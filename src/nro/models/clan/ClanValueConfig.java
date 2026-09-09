package nro.models.clan;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import nro.models.utils.Logger;

/** Immutable, overflow-safe scoring rules for phase-5B Clan Value. */
public final class ClanValueConfig {

    private static final Path DEFAULT_PATH = Path.of("data", "clan_value.properties");

    private final int formulaVersion;
    private final long clanLevelWeight;
    private final long spentPotentialWeight;
    private final long treeLevelWeight;
    private final long weeklyActivityWeight;
    private final int clanLevelCap;
    private final int spentPotentialCap;
    private final int treeLevelCap;
    private final int weeklyActivityCap;
    private final long achievementScoreCap;

    private ClanValueConfig(Properties values) {
        formulaVersion = (int) bounded(values, "formula_version", 1L, 1L, 255L);
        clanLevelWeight = bounded(values, "clan_level_weight", 1_000L, 0L, Long.MAX_VALUE);
        spentPotentialWeight = bounded(values, "spent_potential_weight", 100L, 0L, Long.MAX_VALUE);
        treeLevelWeight = bounded(values, "tree_level_weight", 500L, 0L, Long.MAX_VALUE);
        weeklyActivityWeight = bounded(values, "weekly_activity_weight", 1L, 0L, Long.MAX_VALUE);
        clanLevelCap = (int) bounded(values, "clan_level_cap", Clan.TECHNICAL_MAX_LEVEL,
                0L, Integer.MAX_VALUE);
        spentPotentialCap = (int) bounded(values, "spent_potential_cap", 10_000L,
                0L, Integer.MAX_VALUE);
        treeLevelCap = (int) bounded(values, "tree_level_cap", 1_000L,
                0L, Integer.MAX_VALUE);
        weeklyActivityCap = (int) bounded(values, "weekly_activity_cap", 1_000L,
                0L, Integer.MAX_VALUE);
        achievementScoreCap = bounded(values, "achievement_score_cap", 1_000_000L,
                0L, Long.MAX_VALUE);
    }

    public static ClanValueConfig load() {
        return load(DEFAULT_PATH);
    }

    public static ClanValueConfig load(Path path) {
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            values.load(input);
        } catch (IOException error) {
            Logger.warning("Không đọc được " + path + ", dùng công thức Clan Value mặc định.\n");
        }
        return from(values);
    }

    public static ClanValueConfig from(Properties values) {
        return new ClanValueConfig(values == null ? new Properties() : values);
    }

    /**
     * Computes activity value only. Current treasury balances are deliberately
     * absent so depositing wealth cannot manufacture rank value.
     */
    public Score score(int clanLevel, int spentPotentialPoints, int treeLevel,
            long achievementScore, int weeklyActivityProgress, int weeklyActivityTarget) {
        long levelUnits = boundedNonNegative(clanLevel, clanLevelCap);
        long potentialUnits = boundedNonNegative(spentPotentialPoints, spentPotentialCap);
        long treeUnits = boundedNonNegative(treeLevel, treeLevelCap);
        long weeklyUnits = Math.min(
                boundedNonNegative(weeklyActivityProgress, weeklyActivityCap),
                boundedNonNegative(weeklyActivityTarget, weeklyActivityCap));

        long levelScore = saturatedMultiply(levelUnits, clanLevelWeight);
        long potentialScore = saturatedMultiply(potentialUnits, spentPotentialWeight);
        long treeScore = saturatedMultiply(treeUnits, treeLevelWeight);
        long safeAchievementScore = Math.min(Math.max(0L, achievementScore), achievementScoreCap);
        long weeklyScore = saturatedMultiply(weeklyUnits, weeklyActivityWeight);
        long total = saturatedAdd(levelScore, potentialScore);
        total = saturatedAdd(total, treeScore);
        total = saturatedAdd(total, safeAchievementScore);
        total = saturatedAdd(total, weeklyScore);
        return new Score(formulaVersion, total, levelScore, potentialScore, treeScore,
                safeAchievementScore, weeklyScore);
    }

    private static long boundedNonNegative(int value, int cap) {
        return Math.min(Math.max(0, value), cap);
    }

    private static long saturatedMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) {
            return 0L;
        }
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    private static long saturatedAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static long bounded(Properties values, String key, long fallback, long min, long max) {
        try {
            long value = Long.parseLong(values.getProperty(key, String.valueOf(fallback)).trim());
            return Math.max(min, Math.min(max, value));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public record Score(int formulaVersion, long totalValue, long clanLevelScore,
            long spentPotentialScore, long treeLevelScore, long achievementScore,
            long weeklyActivityScore) {
    }
}
