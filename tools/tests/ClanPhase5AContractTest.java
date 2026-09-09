import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import nro.models.clan.ClanDissolutionPolicy;
import nro.models.clan.ClanFeatureFlags;
import nro.models.clan.ClanTreasuryService;
import nro.models.clan.ClanTreeConfig;
import nro.models.player.PlayerPersistenceComponent;
import nro.models.player.PlayerPersistenceState;

/** Pure regression checks for the phase-5A safety and rollout contracts. */
public final class ClanPhase5AContractTest {

    public static void main(String[] args) {
        verifyTreeConfiguration();
        verifyFeatureFlags();
        verifyDeployedConfiguration();
        verifyLedgerRequestIds();
        verifyDissolutionPolicy();
        verifyExternalInventoryCommitAcknowledgement();
        System.out.println("ClanPhase5AContractTest: PASS");
    }

    private static void verifyTreeConfiguration() {
        Properties values = new Properties();
        values.setProperty("max_tree_level", "18");
        values.setProperty("water_item_id", "500");
        values.setProperty("daily_water_limit", "7");
        values.setProperty("hourly_gold_per_level", "6000");
        values.setProperty("growth_base", "120");
        values.setProperty("growth_exponent", "1.5");
        values.setProperty("upgrade_days_to_levels_2_18",
                "1,1,1,2,2,2,3,3,3,4,4,5,5,6,7,8,9");
        ClanTreeConfig config = ClanTreeConfig.from(values);
        assertEquals("tree max level", 18, config.maxLevel());
        assertEquals("water item", 500, config.waterItemId());
        assertEquals("daily water", 7, config.dailyWaterLimit());
        assertEquals("growth level 4", 960L, config.growthRequired(4));
        assertEquals("upgrade level 18", 9, config.upgradeDaysForTargetLevel(18));
        ClanTreeConfig.ProductionYield yield = config.productionYield(5, 2, 100, 0);
        assertEquals("configured gold yield", 60_000L, yield.gold());
        assertEquals("configured capsule yield", 2, yield.capsule());
    }

    private static void verifyFeatureFlags() {
        Properties values = new Properties();
        values.setProperty("tree.enabled", "true");
        values.setProperty("tree.mutations_enabled", "false");
        values.setProperty("gift.enabled", "false");
        ClanFeatureFlags flags = ClanFeatureFlags.from(values);
        assertTrue("tree remains readable", flags.isEnabled(ClanFeatureFlags.Feature.TREE));
        assertFalse("tree mutations disabled", flags.canMutate(ClanFeatureFlags.Feature.TREE));
        assertFalse("gift disabled", flags.isEnabled(ClanFeatureFlags.Feature.GIFT));
        assertTrue("treasury defaults on", flags.canMutate(ClanFeatureFlags.Feature.TREASURY));
    }

    private static void verifyDeployedConfiguration() {
        ClanTreeConfig tree = ClanTreeConfig.load(Path.of("data", "clan_tree.properties"));
        ClanFeatureFlags flags = ClanFeatureFlags.load(Path.of("data", "clan_features.properties"));
        assertEquals("deployed tree max", 20, tree.maxLevel());
        assertEquals("deployed production cap", 24L * 60L * 60L * 1_000L, tree.productionCapMs());
        assertTrue("deployed tree mutations", flags.canMutate(ClanFeatureFlags.Feature.TREE));
        assertTrue("deployed treasury mutations", flags.canMutate(ClanFeatureFlags.Feature.TREASURY));
        assertTrue("phase 5B value enabled after rollout", flags.isEnabled(ClanFeatureFlags.Feature.VALUE));
        assertTrue("phase 5C ranking enabled after rollout", flags.isEnabled(ClanFeatureFlags.Feature.RANKING));
        assertFalse("phase 5C ranking remains read-only", flags.canMutate(ClanFeatureFlags.Feature.RANKING));
        assertTrue("phase 5D appearance enabled after rollout", flags.isEnabled(ClanFeatureFlags.Feature.APPEARANCE));
        assertFalse("phase 5D appearance remains automatic/read-only",
                flags.canMutate(ClanFeatureFlags.Feature.APPEARANCE));
    }

    private static void verifyLedgerRequestIds() {
        String gold = ClanTreasuryService.ledgerRequestId("TREE_HARVEST", "42:9",
                ClanTreasuryService.CURRENCY_GOLD);
        String repeated = ClanTreasuryService.ledgerRequestId("TREE_HARVEST", "42:9",
                ClanTreasuryService.CURRENCY_GOLD);
        String capsule = ClanTreasuryService.ledgerRequestId("TREE_HARVEST", "42:9",
                ClanTreasuryService.CURRENCY_CAPSULE);
        assertEquals("ledger id deterministic", gold, repeated);
        assertFalse("currencies get distinct ids", gold.equals(capsule));
        assertTrue("ledger id fits schema", gold.length() <= 80);
    }

    private static void verifyDissolutionPolicy() {
        Set<String> active = ClanDissolutionPolicy.activeStateTables();
        assertTrue("tree state removed", active.contains("clan_tree"));
        assertTrue("storage state removed", active.contains("clan_item_storage"));
        assertTrue("buff state removed", active.contains("clan_active_buff"));
        assertFalse("ledger retained", active.contains("clan_ledger"));
        assertFalse("pending entitlements retained", active.contains("clan_pending_reward"));
    }

    private static void verifyExternalInventoryCommitAcknowledgement() {
        PlayerPersistenceState state = new PlayerPersistenceState();
        state.initializeLoaded(7L);
        state.markDirty(PlayerPersistenceComponent.INVENTORY);
        state.markDirty(PlayerPersistenceComponent.SOCIAL);
        state.acknowledgeExternalCommit(7L, 8L, PlayerPersistenceComponent.INVENTORY);
        assertEquals("external commit revision", 8L, state.saveVersion());
        assertFalse("inventory acknowledged", state.dirtyComponents().contains(PlayerPersistenceComponent.INVENTORY));
        assertTrue("unrelated dirty state preserved", state.dirtyComponents().contains(PlayerPersistenceComponent.SOCIAL));
    }

    private static void assertEquals(String label, long expected, long actual) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertEquals(String label, String expected, String actual) {
        if (!expected.equals(actual)) {
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
