import java.nio.file.Path;
import java.util.Properties;
import nro.models.clan.ClanFeatureFlags;
import nro.models.clan.ClanValueConfig;

/** Pure regression checks for the phase-5B Clan Value formula and rollout. */
public final class ClanPhase5BValueTest {

    public static void main(String[] args) {
        verifyFormulaBreakdown();
        verifyActivityAndAchievementCaps();
        verifyOverflowSaturation();
        verifyDeployedConfiguration();
        verifyDeployedRollout();
        System.out.println("ClanPhase5BValueTest: PASS");
    }

    private static void verifyFormulaBreakdown() {
        ClanValueConfig config = ClanValueConfig.from(new Properties());
        ClanValueConfig.Score score = config.score(10, 15, 6, 2_000L, 500, 1_000);

        assertEquals("formula version", 1, score.formulaVersion());
        assertEquals("clan level score", 10_000L, score.clanLevelScore());
        assertEquals("spent potential score", 1_500L, score.spentPotentialScore());
        assertEquals("tree level score", 3_000L, score.treeLevelScore());
        assertEquals("achievement score", 2_000L, score.achievementScore());
        assertEquals("weekly activity score", 500L, score.weeklyActivityScore());
        assertEquals("total value", 17_000L, score.totalValue());
    }

    private static void verifyActivityAndAchievementCaps() {
        Properties values = new Properties();
        values.setProperty("weekly_activity_cap", "300");
        values.setProperty("weekly_activity_weight", "2");
        values.setProperty("achievement_score_cap", "700");
        ClanValueConfig config = ClanValueConfig.from(values);

        ClanValueConfig.Score score = config.score(0, 0, 0, 900L, 800, 500);
        assertEquals("achievement cap", 700L, score.achievementScore());
        assertEquals("weekly target and config cap", 600L, score.weeklyActivityScore());
        assertEquals("capped total", 1_300L, score.totalValue());

        ClanValueConfig.Score invalid = config.score(-1, -1, -1, -1L, -1, -1);
        assertEquals("negative inputs", 0L, invalid.totalValue());
    }

    private static void verifyOverflowSaturation() {
        Properties values = new Properties();
        values.setProperty("clan_level_weight", String.valueOf(Long.MAX_VALUE));
        values.setProperty("spent_potential_weight", String.valueOf(Long.MAX_VALUE));
        values.setProperty("tree_level_weight", String.valueOf(Long.MAX_VALUE));
        values.setProperty("weekly_activity_weight", String.valueOf(Long.MAX_VALUE));
        values.setProperty("achievement_score_cap", String.valueOf(Long.MAX_VALUE));
        ClanValueConfig.Score score = ClanValueConfig.from(values)
                .score(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
                        Long.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals("overflow saturates", Long.MAX_VALUE, score.totalValue());
    }

    private static void verifyDeployedConfiguration() {
        ClanValueConfig.Score score = ClanValueConfig.load(Path.of("data", "clan_value.properties"))
                .score(10, 15, 6, 2_000L, 500, 1_000);
        assertEquals("deployed formula", 17_000L, score.totalValue());
    }

    private static void verifyDeployedRollout() {
        ClanFeatureFlags flags = ClanFeatureFlags.load(Path.of("data", "clan_features.properties"));
        assertTrue("phase 5B value enabled", flags.isEnabled(ClanFeatureFlags.Feature.VALUE));
        assertTrue("phase 5B value refresh enabled", flags.canMutate(ClanFeatureFlags.Feature.VALUE));
        assertTrue("phase 5C ranking enabled after rollout", flags.isEnabled(ClanFeatureFlags.Feature.RANKING));
        assertFalse("phase 5C ranking remains read-only", flags.canMutate(ClanFeatureFlags.Feature.RANKING));
        assertTrue("phase 5D appearance enabled after rollout", flags.isEnabled(ClanFeatureFlags.Feature.APPEARANCE));
        assertFalse("phase 5D appearance remains automatic/read-only",
                flags.canMutate(ClanFeatureFlags.Feature.APPEARANCE));
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
