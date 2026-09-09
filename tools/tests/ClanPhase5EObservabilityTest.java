import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import nro.models.clan.ClanEconomyConfig;
import nro.models.clan.ClanEconomyMetricAccumulator;
import nro.models.clan.ClanEconomyMetricsService;
import nro.models.clan.ClanFeatureFlags;

/** Pure contract checks for phase 5E clan economy observability. */
public final class ClanPhase5EObservabilityTest {

    public static void main(String[] args) throws Exception {
        verifyMetricAccumulator();
        verifyBoundedSignalCatalog();
        verifyConfigBounds();
        verifyDeployedContract();
        System.out.println("ClanPhase5EObservabilityTest: PASS");
    }

    private static void verifyMetricAccumulator() {
        ClanEconomyMetricAccumulator value = new ClanEconomyMetricAccumulator();
        value.add(2L, 50L);
        value.add(3L, 70L);
        assertEquals("event sum", 5L, value.snapshot().eventCount());
        assertEquals("amount sum", 120L, value.snapshot().amountTotal());

        value.add(Long.MAX_VALUE, Long.MAX_VALUE);
        assertEquals("event saturation", Long.MAX_VALUE, value.snapshot().eventCount());
        assertEquals("amount saturation", Long.MAX_VALUE, value.snapshot().amountTotal());

        boolean rejected = false;
        try {
            value.add(-1L, 0L);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        assertTrue("negative metric delta rejected", rejected);
    }

    private static void verifyBoundedSignalCatalog() {
        Set<String> keys = new HashSet<>();
        for (ClanEconomyMetricsService.Signal signal : ClanEconomyMetricsService.Signal.values()) {
            assertTrue("bounded SQL signal key", signal.name().matches("[A-Z_]{3,48}"));
            assertTrue("unique SQL signal key", keys.add(signal.name()));
        }
        assertTrue("tree water signal", keys.contains("TREE_WATER"));
        assertTrue("gift blocked signal", keys.contains("GIFT_BLOCKED_POLICY"));
        assertTrue("rollback signal", keys.contains("TRANSACTION_ROLLBACK"));
        assertTrue("territory lifetime signal", keys.contains("TERRITORY_DISPOSED"));
    }

    private static void verifyConfigBounds() {
        Properties values = new Properties();
        values.setProperty("lookback_default_days", "999");
        values.setProperty("lookback_max_days", "30");
        values.setProperty("flush_interval_seconds", "0");
        values.setProperty("retention_days", "99999");
        ClanEconomyConfig config = ClanEconomyConfig.from(values);
        assertEquals("default lookback capped by max", 30L, config.defaultLookbackDays());
        assertEquals("max lookback", 30L, config.maxLookbackDays());
        assertEquals("flush lower bound", 5L, config.flushIntervalSeconds());
        assertEquals("retention upper bound", 730L, config.retentionDays());
    }

    private static void verifyDeployedContract() throws Exception {
        ClanEconomyConfig config = ClanEconomyConfig.load(Path.of("data", "clan_economy.properties"));
        assertTrue("lookback choices include 28 days", config.maxLookbackDays() >= 28);
        assertTrue("retention covers lookback", config.retentionDays() >= config.maxLookbackDays());

        ClanFeatureFlags flags = ClanFeatureFlags.load(Path.of("data", "clan_features.properties"));
        assertTrue("5E metrics enabled", flags.isEnabled(ClanFeatureFlags.Feature.ECONOMY_METRICS));
        assertFalse("5E has no client mutation surface",
                flags.canMutate(ClanFeatureFlags.Feature.ECONOMY_METRICS));

        String migration = Files.readString(Path.of("sql", "migrations",
                "20260909_add_clan_economy_metrics.sql")).toLowerCase();
        assertTrue("daily metric table", migration.contains("clan_economy_metric"));
        assertTrue("active clan-day table", migration.contains("clan_economy_active_clan"));
        assertFalse("no player identifiers in metrics", migration.contains("player_id"));
        assertFalse("no actor identifiers in metrics", migration.contains("actor_id"));
        assertFalse("no request identifiers in metrics", migration.contains("request_id"));
    }

    private static void assertEquals(String label, long expected, long actual) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertTrue(String label, boolean value) {
        if (!value) {
            throw new AssertionError(label);
        }
    }

    private static void assertFalse(String label, boolean value) {
        assertTrue(label, !value);
    }
}
