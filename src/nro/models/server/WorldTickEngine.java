package nro.models.server;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import nro.models.activity.ActivityConfigService;
import nro.models.map.Zone;
import nro.models.utils.Logger;

/** Owns world scheduling, parallel zone ticks, health, and deterministic shutdown. */
public final class WorldTickEngine {

    private final WorldRegistry worlds;
    private final Runnable testTick;
    private final long intervalMillis;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean tickInProgress = new AtomicBoolean();
    private final AtomicLong lastTickAt = new AtomicLong();
    private final AtomicLong lastDurationMillis = new AtomicLong();
    private final LongAdder completedTicks = new LongAdder();
    private final LongAdder failedTicks = new LongAdder();
    private final LongAdder rejectedOverlaps = new LongAdder();
    private ScheduledExecutorService scheduler;
    private ExecutorService workers;

    public WorldTickEngine(WorldRegistry worlds) {
        this(worlds, null, 1_000);
    }

    private WorldTickEngine(WorldRegistry worlds, Runnable testTick, long intervalMillis) {
        this.worlds = worlds;
        this.testTick = testTick;
        this.intervalMillis = intervalMillis;
    }

    static WorldTickEngine forTest(Runnable tick, long intervalMillis) {
        return new WorldTickEngine(null, tick, intervalMillis);
    }

    public synchronized void start() {
        if (!running.compareAndSet(false, true)) return;
        int processors = Math.max(1, Runtime.getRuntime().availableProcessors());
        scheduler = Executors.newScheduledThreadPool(testTick == null ? 2 : 1,
                new NamedThreadFactory("WorldScheduler", false));
        workers = Executors.newFixedThreadPool(processors, new NamedThreadFactory("ZoneWorker", false));
        if (testTick == null) {
            scheduler.scheduleAtFixedRate(this::refreshActivityConfig, 1, 1, TimeUnit.SECONDS);
        }
        scheduler.scheduleWithFixedDelay(this::guardedTick, 0, intervalMillis, TimeUnit.MILLISECONDS);
    }

    private void refreshActivityConfig() {
        try {
            ActivityConfigService.gI().getCurrent();
        } catch (Exception error) {
            Logger.error("Cannot refresh Activity Points runtime config.\n");
        }
    }

    private void guardedTick() {
        if (!running.get()) return;
        if (!tickInProgress.compareAndSet(false, true)) {
            rejectedOverlaps.increment();
            ServerRuntimeMetrics.gI().recordRejectedOverlap();
            return;
        }
        long startedAt = System.currentTimeMillis();
        try {
            if (testTick != null) testTick.run(); else tickWorlds();
            completedTicks.increment();
        } catch (Throwable error) {
            failedTicks.increment();
            ServerRuntimeMetrics.gI().recordTickException();
            Logger.error("[TICK] event=world_tick_failed errorType=" + error.getClass().getSimpleName() + "\n");
        } finally {
            long duration = System.currentTimeMillis() - startedAt;
            lastTickAt.set(System.currentTimeMillis());
            lastDurationMillis.set(duration);
            ServerRuntimeMetrics.gI().recordTickDuration(duration);
            if (duration > intervalMillis) ServerRuntimeMetrics.gI().recordDeadlineOverrun();
            tickInProgress.set(false);
        }
    }

    private void tickWorlds() throws InterruptedException {
        List<Callable<Void>> tasks = new ArrayList<>();
        for (nro.models.map.Map map : worlds.snapshot()) {
            List<Zone> batch = new ArrayList<>(10);
            for (Zone zone : map.zones) {
                batch.add(zone);
                if (batch.size() == 10) {
                    addBatch(tasks, batch);
                    batch = new ArrayList<>(10);
                }
            }
            if (!batch.isEmpty()) addBatch(tasks, batch);
        }
        CompletionService<Void> completion = new ExecutorCompletionService<>(workers);
        for (Callable<Void> task : tasks) completion.submit(task);
        for (int i = 0; i < tasks.size(); i++) {
            try {
                completion.take().get();
            } catch (java.util.concurrent.ExecutionException error) {
                failedTicks.increment();
                ServerRuntimeMetrics.gI().recordTickException();
                Logger.error("[TICK] event=batch_failed errorType="
                        + error.getCause().getClass().getSimpleName() + "\n");
            }
        }
    }

    private static void addBatch(List<Callable<Void>> tasks, List<Zone> batch) {
        List<Zone> immutableBatch = List.copyOf(batch);
        tasks.add(() -> {
            updateZoneBatch(immutableBatch);
            return null;
        });
    }

    static int updateZoneBatch(List<Zone> zones) {
        int failures = 0;
        for (Zone zone : zones) {
            try {
                zone.update();
            } catch (Throwable error) {
                failures++;
                ServerRuntimeMetrics.gI().recordTickException();
                int mapId = zone.map == null ? -1 : zone.map.mapId;
                Logger.error("[TICK] event=zone_tick_failed mapId=" + mapId
                        + " zoneId=" + zone.zoneId + " errorType="
                        + error.getClass().getSimpleName() + "\n");
            }
        }
        return failures;
    }

    public synchronized void shutdown() {
        if (!running.compareAndSet(true, false)) return;
        if (scheduler != null) scheduler.shutdownNow();
        if (workers != null) workers.shutdownNow();
        awaitTermination(scheduler);
        awaitTermination(workers);
    }

    private static void awaitTermination(java.util.concurrent.ExecutorService service) {
        if (service == null) return;
        try {
            service.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    public Health health() {
        return new Health(running.get(), tickInProgress.get(), lastTickAt.get(),
                lastDurationMillis.get(), completedTicks.sum(), failedTicks.sum(), rejectedOverlaps.sum());
    }

    public record Health(boolean running, boolean tickInProgress, long lastTickAt,
            long lastDurationMillis, long completedTicks, long failedTicks, long rejectedOverlaps) {
    }
}
