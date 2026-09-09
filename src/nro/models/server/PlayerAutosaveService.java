package nro.models.server;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import nro.models.database.PlayerDAO;
import nro.models.database.PlayerRepository;
import nro.models.player.Player;
import nro.models.player.PlayerAutosavePolicy;
import nro.models.player.PlayerSnapshot;
import nro.models.utils.Logger;

/**
 * Staggered dirty-player autosave. Snapshots are produced in the player's zone
 * mailbox; only immutable snapshots cross to this JDBC worker.
 */
public final class PlayerAutosaveService {

    private static final int MAX_PENDING_SNAPSHOTS = 2_048;

    private final Supplier<List<Player>> players;
    private final PlayerAutosavePolicy policy;
    private final PlayerRepository repository;
    private final Map<Long, ScheduleState> schedules = new ConcurrentHashMap<>();
    private final ArrayBlockingQueue<PendingSnapshot> pending =
            new ArrayBlockingQueue<>(MAX_PENDING_SNAPSHOTS);
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(
            new NamedThreadFactory("PlayerAutosave", true));
    private volatile boolean running;

    public PlayerAutosaveService(Supplier<List<Player>> players, PlayerAutosavePolicy policy) {
        this(players, policy, new PlayerRepository());
    }

    PlayerAutosaveService(Supplier<List<Player>> players, PlayerAutosavePolicy policy,
            PlayerRepository repository) {
        this.players = players;
        this.policy = policy;
        this.repository = repository;
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        long cadence = Math.min(1_000L, Math.max(250L, policy.retryBaseMillis()));
        worker.scheduleWithFixedDelay(this::runCycle, cadence, cadence, TimeUnit.MILLISECONDS);
    }

    private void runCycle() {
        if (!running) {
            return;
        }
        try {
            persistPending();
            scheduleDirtyPlayers();
            ServerRuntimeMetrics.gI().setAutosaveBacklog(pending.size());
        } catch (Throwable error) {
            ServerRuntimeMetrics.gI().recordAutosaveFailure();
            Logger.error("[AUTOSAVE] cycle failed errorType=" + error.getClass().getSimpleName() + "\n");
        }
    }

    private void scheduleDirtyPlayers() {
        long now = System.currentTimeMillis();
        List<Player> snapshot = players.get();
        if (snapshot == null) {
            return;
        }
        Set<Long> onlinePlayerIds = new HashSet<>(snapshot.size());
        for (Player player : snapshot) {
            if (player != null) {
                onlinePlayerIds.add(player.id);
            }
            if (player == null || player.id <= 0L || player.isRemovingOrDisposed()
                    || player.isPersistenceQuarantined() || player.idMark == null
                    || !player.idMark.isLoadedAllDataPlayer()
                    || !player.getPersistenceState().isDirty()) {
                continue;
            }
            ScheduleState schedule = schedules.computeIfAbsent(player.id,
                    ignored -> new ScheduleState(policy.firstDueAt(player.id, now)));
            if (!policy.isDue(now, schedule.nextAttemptAt)) {
                continue;
            }
            schedule.nextAttemptAt = Long.MAX_VALUE;
            boolean accepted = PlayerDAO.captureOnOwnerThread(player,
                    captured -> acceptSnapshot(player, captured));
            if (!accepted) {
                scheduleFailure(player, schedule, "mailbox_rejected");
            }
        }
        schedules.keySet().removeIf(playerId -> !onlinePlayerIds.contains(playerId));
    }

    private void acceptSnapshot(Player player, PlayerSnapshot snapshot) {
        ScheduleState schedule = schedules.computeIfAbsent(player.id,
                ignored -> new ScheduleState(System.currentTimeMillis()));
        if (snapshot == null) {
            schedule.nextAttemptAt = System.currentTimeMillis() + policy.retryBaseMillis();
            return;
        }
        if (!pending.offer(new PendingSnapshot(player, snapshot))) {
            player.getPersistenceState().finishSave();
            scheduleFailure(player, schedule, "queue_full");
        }
    }

    private void persistPending() {
        PendingSnapshot work;
        while ((work = pending.poll()) != null) {
            Player player = work.player;
            ScheduleState schedule = schedules.computeIfAbsent(player.id,
                    ignored -> new ScheduleState(System.currentTimeMillis()));
            PlayerDAO.SaveResult result = PlayerDAO.persistCaptured(player, work.snapshot, repository);
            if (result == PlayerDAO.SaveResult.SAVED || result == PlayerDAO.SaveResult.SKIPPED) {
                schedule.attempts = 0;
                schedule.nextAttemptAt = System.currentTimeMillis() + policy.rpoMillis();
                if (result == PlayerDAO.SaveResult.SAVED) {
                    ServerRuntimeMetrics.gI().recordAutosaveSuccess();
                }
            } else {
                if (result == PlayerDAO.SaveResult.OPTIMISTIC_CONFLICT) {
                    schedule.attempts = policy.maxAttempts() - 1;
                }
                scheduleFailure(player, schedule, result.name().toLowerCase());
            }
        }
    }

    private void scheduleFailure(Player player, ScheduleState schedule, String error) {
        ServerRuntimeMetrics.gI().recordAutosaveFailure();
        int attempt = ++schedule.attempts;
        schedule.nextAttemptAt = System.currentTimeMillis() + policy.retryDelayMillis(attempt);
        if (attempt < policy.maxAttempts()) {
            return;
        }
        ServerRuntimeMetrics.gI().recordAutosaveDeadLetter();
        try {
            repository.recordDeadLetter(player.id, player.name, attempt, error);
        } catch (SQLException persistenceError) {
            Logger.error("[AUTOSAVE] dead-letter write failed playerId=" + player.id
                    + " errorType=" + persistenceError.getClass().getSimpleName() + "\n");
        }
        schedule.attempts = 0;
        schedule.nextAttemptAt = System.currentTimeMillis() + policy.rpoMillis();
    }

    public synchronized void shutdown() {
        running = false;
        worker.shutdown();
        try {
            if (!worker.awaitTermination(5, TimeUnit.SECONDS)) {
                worker.shutdownNow();
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            worker.shutdownNow();
        }
    }

    public boolean isRunning() { return running && !worker.isShutdown(); }
    public int backlog() { return pending.size(); }

    private static final class ScheduleState {
        private volatile long nextAttemptAt;
        private int attempts;

        private ScheduleState(long nextAttemptAt) {
            this.nextAttemptAt = nextAttemptAt;
        }
    }

    private record PendingSnapshot(Player player, PlayerSnapshot snapshot) {
    }
}
