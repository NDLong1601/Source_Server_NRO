package nro.models.activity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import nro.models.data.LocalManager;
import nro.models.utils.Logger;

/**
 * Asynchronous, aggregated telemetry for Activity Points. Gameplay threads
 * never wait for database I/O; at most one short metric window can be lost on
 * an ungraceful process stop.
 */
public final class ActivityMetricsService {

    private static final ActivityMetricsService INSTANCE = new ActivityMetricsService();
    private static final long FLUSH_INTERVAL_SECONDS = 15L;

    private final Object pendingLock = new Object();
    private final Map<MetricKey, MetricValue> pending = new HashMap<>();
    private final AtomicBoolean started = new AtomicBoolean();
    private final ScheduledExecutorService executor;
    private volatile long lastFailureLogAt;

    private ActivityMetricsService() {
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "activity-metrics-flush");
            thread.setDaemon(true);
            return thread;
        };
        executor = Executors.newSingleThreadScheduledExecutor(factory);
    }

    public static ActivityMetricsService gI() {
        return INSTANCE;
    }

    public void start(Connection connection) throws SQLException {
        ensureSchema(connection);
        if (started.compareAndSet(false, true)) {
            executor.scheduleWithFixedDelay(this::flushSafely,
                    FLUSH_INTERVAL_SECONDS, FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
            Logger.success(Logger.PURPLE + "Activity Points shadow metrics are enabled\n");
        }
    }

    public void record(ActivityConfig config, ActivityState state, ActivityType type,
            int requestedPoints, ActivityResult result) {
        if (!started.get() || config == null || state == null || type == null || result == null) {
            return;
        }
        String dayKey = state.dayKey == null || state.dayKey.isBlank() ? "unknown" : state.dayKey;
        MetricKey key = new MetricKey(dayKey,
                config.isShadowMode() ? "SHADOW" : "LIVE",
                config.getRevision(), type.name(), result.reason.name());
        synchronized (pendingLock) {
            MetricValue value = pending.computeIfAbsent(key, ignored -> new MetricValue());
            value.eventCount++;
            value.requestedPoints += Math.max(0, requestedPoints);
            value.awardedPoints += Math.max(0, result.awarded - result.diversityBonus);
            if (result.diversityBonus > 0) {
                MetricKey bonusKey = new MetricKey(dayKey,
                        config.isShadowMode() ? "SHADOW" : "LIVE",
                        config.getRevision(), ActivityType.DIVERSITY_BONUS.name(), result.reason.name());
                MetricValue bonusValue = pending.computeIfAbsent(bonusKey, ignored -> new MetricValue());
                bonusValue.eventCount++;
                bonusValue.requestedPoints += result.diversityBonus;
                bonusValue.awardedPoints += result.diversityBonus;
            }
        }
    }

    public void flushNow() {
        flushSafely();
    }

    private void ensureSchema(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("CREATE TABLE IF NOT EXISTS activity_source_metric ("
                + "metric_day VARCHAR(10) NOT NULL,"
                + "mode VARCHAR(16) NOT NULL,"
                + "config_revision BIGINT NOT NULL,"
                + "source_key VARCHAR(64) NOT NULL,"
                + "result_code VARCHAR(40) NOT NULL,"
                + "event_count BIGINT NOT NULL DEFAULT 0,"
                + "requested_points BIGINT NOT NULL DEFAULT 0,"
                + "awarded_points BIGINT NOT NULL DEFAULT 0,"
                + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                + "PRIMARY KEY (metric_day, mode, config_revision, source_key, result_code)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci")) {
            ps.executeUpdate();
        }
    }

    private void flushSafely() {
        Map<MetricKey, MetricValue> snapshot;
        synchronized (pendingLock) {
            if (pending.isEmpty()) {
                return;
            }
            snapshot = new HashMap<>(pending);
            pending.clear();
        }
        try (Connection connection = LocalManager.getConnection();
                PreparedStatement ps = connection.prepareStatement("INSERT INTO activity_source_metric "
                        + "(metric_day,mode,config_revision,source_key,result_code,event_count,requested_points,awarded_points) "
                        + "VALUES (?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE "
                        + "event_count=event_count+VALUES(event_count),"
                        + "requested_points=requested_points+VALUES(requested_points),"
                        + "awarded_points=awarded_points+VALUES(awarded_points)")) {
            for (Map.Entry<MetricKey, MetricValue> entry : snapshot.entrySet()) {
                MetricKey key = entry.getKey();
                MetricValue value = entry.getValue();
                ps.setString(1, key.dayKey);
                ps.setString(2, key.mode);
                ps.setLong(3, key.revision);
                ps.setString(4, key.sourceKey);
                ps.setString(5, key.reason);
                ps.setLong(6, value.eventCount);
                ps.setLong(7, value.requestedPoints);
                ps.setLong(8, value.awardedPoints);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (Exception e) {
            mergeBack(snapshot);
            long now = System.currentTimeMillis();
            if (now - lastFailureLogAt >= 60_000L) {
                lastFailureLogAt = now;
                Logger.error("Cannot flush Activity Points metrics; retrying later.\n");
            }
        }
    }

    private void mergeBack(Map<MetricKey, MetricValue> snapshot) {
        synchronized (pendingLock) {
            for (Map.Entry<MetricKey, MetricValue> entry : snapshot.entrySet()) {
                MetricValue target = pending.computeIfAbsent(entry.getKey(), ignored -> new MetricValue());
                target.add(entry.getValue());
            }
        }
    }

    private static final class MetricKey {
        private final String dayKey;
        private final String mode;
        private final long revision;
        private final String sourceKey;
        private final String reason;

        private MetricKey(String dayKey, String mode, long revision, String sourceKey, String reason) {
            this.dayKey = dayKey;
            this.mode = mode;
            this.revision = revision;
            this.sourceKey = sourceKey;
            this.reason = reason;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof MetricKey key)) {
                return false;
            }
            return revision == key.revision && Objects.equals(dayKey, key.dayKey)
                    && Objects.equals(mode, key.mode) && Objects.equals(sourceKey, key.sourceKey)
                    && Objects.equals(reason, key.reason);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dayKey, mode, revision, sourceKey, reason);
        }
    }

    private static final class MetricValue {
        private long eventCount;
        private long requestedPoints;
        private long awardedPoints;

        private void add(MetricValue other) {
            eventCount += other.eventCount;
            requestedPoints += other.requestedPoints;
            awardedPoints += other.awardedPoints;
        }
    }
}
