package nro.models.clan;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import nro.models.data.LocalManager;
import nro.models.utils.Logger;
import nro.models.utils.TimeUtil;

/**
 * Low-cardinality phase 5E metrics. Gameplay threads only update memory; a
 * daemon batches global daily counters and clan-day activity markers to JDBC.
 */
public final class ClanEconomyMetricsService {

    public enum Signal {
        TREE_WATER,
        TREE_FERTILIZE,
        TREE_VITALITY_REACHED,
        TREE_HARVEST,
        TREE_LEVEL_UP,
        CLAN_LEVEL_UP,
        GIFT_SENT,
        GIFT_BLOCKED_POLICY,
        PENDING_GIFT_DELIVERED,
        DUPLICATE_REQUEST,
        TRANSACTION_ROLLBACK,
        TERRITORY_CREATED,
        TERRITORY_DISPOSED
    }

    private static final ClanEconomyMetricsService INSTANCE = new ClanEconomyMetricsService();

    private final Object pendingLock = new Object();
    private final Map<MetricKey, ClanEconomyMetricAccumulator> pending = new HashMap<>();
    private final Set<ActiveClanKey> pendingActiveClans = new HashSet<>();
    private final AtomicBoolean started = new AtomicBoolean();
    private final ScheduledExecutorService executor;
    private final ClanEconomyConfig config = ClanEconomyConfig.load();
    private volatile long lastFailureLogAt;

    private ClanEconomyMetricsService() {
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "clan-economy-metrics-flush");
            thread.setDaemon(true);
            return thread;
        };
        executor = Executors.newSingleThreadScheduledExecutor(factory);
    }

    public static ClanEconomyMetricsService gI() {
        return INSTANCE;
    }

    public void start(Connection connection) throws SQLException {
        ensureSchema(connection);
        cleanupExpired(connection);
        if (!ClanFeatureFlags.gI().isEnabled(ClanFeatureFlags.Feature.ECONOMY_METRICS)) {
            return;
        }
        if (started.compareAndSet(false, true)) {
            executor.scheduleWithFixedDelay(this::flushSafely,
                    config.flushIntervalSeconds(), config.flushIntervalSeconds(), TimeUnit.SECONDS);
            Logger.success(Logger.PURPLE + "Clan economy metrics are enabled\n");
        }
    }

    public synchronized void ensureSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS clan_economy_metric ("
                    + "metric_day DATE NOT NULL, signal_key VARCHAR(48) NOT NULL, "
                    + "event_count BIGINT NOT NULL DEFAULT 0, amount_total BIGINT NOT NULL DEFAULT 0, "
                    + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY (metric_day, signal_key)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS clan_economy_active_clan ("
                    + "metric_day DATE NOT NULL, clan_id INT NOT NULL, "
                    + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY (metric_day, clan_id), KEY idx_clan_economy_active_clan (clan_id, metric_day)) "
                    + "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
        }
        ensureLedgerReportIndex(connection);
    }

    private void ensureLedgerReportIndex(Connection connection) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT column_name FROM information_schema.statistics "
                + "WHERE table_schema=DATABASE() AND table_name='clan_ledger' "
                + "AND index_name='idx_clan_ledger_economy' ORDER BY seq_in_index");
                var rows = statement.executeQuery()) {
            while (rows.next()) {
                columns.add(rows.getString(1).toLowerCase());
            }
        }
        List<String> expected = List.of("created_at", "currency_type", "action_type");
        if (columns.isEmpty()) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE clan_ledger ADD KEY idx_clan_ledger_economy "
                        + "(created_at,currency_type,action_type)");
            }
        } else if (!columns.equals(expected)) {
            throw new SQLException("idx_clan_ledger_economy has unexpected columns: " + columns);
        }
    }

    public void record(Signal signal, int clanId) {
        record(signal, 0L, clanId);
    }

    /** amount is a non-negative signal-specific quantity; event_count always increments once. */
    public void record(Signal signal, long amount, int clanId) {
        if (!started.get() || signal == null || amount < 0L) {
            return;
        }
        LocalDate today = LocalDate.now(TimeUtil.VIETNAM_ZONE);
        synchronized (pendingLock) {
            MetricKey key = new MetricKey(today, signal);
            ClanEconomyMetricAccumulator accumulator = pending.computeIfAbsent(key,
                    ignored -> new ClanEconomyMetricAccumulator());
            accumulator.add(1L, amount);
            if (clanId >= 0) {
                pendingActiveClans.add(new ActiveClanKey(today, clanId));
            }
        }
    }

    public void markActiveClan(int clanId) {
        if (!started.get() || clanId < 0) {
            return;
        }
        synchronized (pendingLock) {
            pendingActiveClans.add(new ActiveClanKey(LocalDate.now(TimeUtil.VIETNAM_ZONE), clanId));
        }
    }

    public void flushNow() {
        flushSafely();
    }

    private void cleanupExpired(Connection connection) throws SQLException {
        Date cutoff = Date.valueOf(LocalDate.now(TimeUtil.VIETNAM_ZONE).minusDays(config.retentionDays()));
        try (PreparedStatement metrics = connection.prepareStatement(
                "DELETE FROM clan_economy_metric WHERE metric_day<?");
                PreparedStatement active = connection.prepareStatement(
                        "DELETE FROM clan_economy_active_clan WHERE metric_day<?")) {
            metrics.setDate(1, cutoff);
            metrics.executeUpdate();
            active.setDate(1, cutoff);
            active.executeUpdate();
        }
    }

    private void flushSafely() {
        Map<MetricKey, ClanEconomyMetricAccumulator.Snapshot> metricSnapshot = new HashMap<>();
        Set<ActiveClanKey> activeSnapshot;
        synchronized (pendingLock) {
            if (pending.isEmpty() && pendingActiveClans.isEmpty()) {
                return;
            }
            for (Map.Entry<MetricKey, ClanEconomyMetricAccumulator> entry : pending.entrySet()) {
                metricSnapshot.put(entry.getKey(), entry.getValue().snapshot());
            }
            activeSnapshot = new HashSet<>(pendingActiveClans);
            pending.clear();
            pendingActiveClans.clear();
        }

        try (Connection connection = LocalManager.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement metrics = connection.prepareStatement(
                    "INSERT INTO clan_economy_metric (metric_day,signal_key,event_count,amount_total) "
                    + "VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE "
                    + "event_count=IF(event_count>9223372036854775807-VALUES(event_count),"
                    + "9223372036854775807,event_count+VALUES(event_count)),"
                    + "amount_total=IF(amount_total>9223372036854775807-VALUES(amount_total),"
                    + "9223372036854775807,amount_total+VALUES(amount_total))");
                    PreparedStatement active = connection.prepareStatement(
                            "INSERT IGNORE INTO clan_economy_active_clan (metric_day,clan_id) VALUES (?,?)")) {
                for (Map.Entry<MetricKey, ClanEconomyMetricAccumulator.Snapshot> entry : metricSnapshot.entrySet()) {
                    metrics.setDate(1, Date.valueOf(entry.getKey().day));
                    metrics.setString(2, entry.getKey().signal.name());
                    metrics.setLong(3, entry.getValue().eventCount());
                    metrics.setLong(4, entry.getValue().amountTotal());
                    metrics.addBatch();
                }
                if (!metricSnapshot.isEmpty()) {
                    metrics.executeBatch();
                }
                for (ActiveClanKey key : activeSnapshot) {
                    active.setDate(1, Date.valueOf(key.day));
                    active.setInt(2, key.clanId);
                    active.addBatch();
                }
                if (!activeSnapshot.isEmpty()) {
                    active.executeBatch();
                }
                connection.commit();
            } catch (Exception error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception error) {
            mergeBack(metricSnapshot, activeSnapshot);
            long now = System.currentTimeMillis();
            if (now - lastFailureLogAt >= 60_000L) {
                lastFailureLogAt = now;
                Logger.error("Cannot flush clan economy metrics; retrying later.\n");
            }
        }
    }

    private void mergeBack(Map<MetricKey, ClanEconomyMetricAccumulator.Snapshot> metrics,
            Set<ActiveClanKey> activeClans) {
        synchronized (pendingLock) {
            for (Map.Entry<MetricKey, ClanEconomyMetricAccumulator.Snapshot> entry : metrics.entrySet()) {
                ClanEconomyMetricAccumulator target = pending.computeIfAbsent(entry.getKey(),
                        ignored -> new ClanEconomyMetricAccumulator());
                target.add(entry.getValue().eventCount(), entry.getValue().amountTotal());
            }
            pendingActiveClans.addAll(activeClans);
        }
    }

    private record MetricKey(LocalDate day, Signal signal) {
    }

    private record ActiveClanKey(LocalDate day, int clanId) {
    }
}
