package nro.models.clan;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import nro.models.utils.Logger;

/** Validated server-side economy and timing configuration for the clan tree. */
public final class ClanTreeConfig {

    private static final long MINUTE_MS = 60_000L;
    private static final long HOUR_MS = 60L * MINUTE_MS;
    private static final long DAY_MS = 24L * HOUR_MS;
    private static final int MAX_SUPPORTED_LEVEL = 20;
    private static final int[] DEFAULT_UPGRADE_DAYS = {
        1, 1, 1, 2, 2, 2, 3, 3, 3, 4, 4, 5, 5, 6, 7, 8, 9, 11, 13
    };

    private final int maxLevel;
    private final int waterItemId;
    private final int fertilizerItemId;
    private final int dailyWaterLimit;
    private final int dailyFertilizerLimit;
    private final long actionCooldownMs;
    private final long helpCooldownMs;
    private final long helpDurationMs;
    private final long productionCapMs;
    private final long hourlyGoldPerLevel;
    private final int hourlyCapsulePerFiveLevels;
    private final long growthBase;
    private final double growthExponent;
    private final int waterBase;
    private final int waterPerLevel;
    private final int fertilizerLevelsPerUnit;
    private final int clanLevelsPerTreeLevel;
    private final int waterGrowthBase;
    private final int waterGrowthLevelBonusCap;
    private final int fertilizerGrowthMultiplier;
    private final int[] upgradeDays;

    private ClanTreeConfig(Properties values) {
        // The release currently ships cay_lv_01..20 for every x1-x4 zoom tier.
        maxLevel = integer(values, "max_tree_level", 20, 1, MAX_SUPPORTED_LEVEL);
        waterItemId = integer(values, "water_item_id", 456, 0, Short.MAX_VALUE);
        fertilizerItemId = integer(values, "fertilizer_item_id", 1094, 0, Short.MAX_VALUE);
        dailyWaterLimit = integer(values, "daily_water_limit", 5, 0, 127);
        dailyFertilizerLimit = integer(values, "daily_fertilizer_limit", 2, 0, 127);
        actionCooldownMs = longValue(values, "action_cooldown_ms", 500L, 0L, HOUR_MS);
        helpCooldownMs = minutes(values, "help_cooldown_minutes", 10L, 0L, 30L * DAY_MS);
        helpDurationMs = minutes(values, "help_duration_minutes", 120L, 0L, 30L * DAY_MS);
        productionCapMs = hours(values, "production_cap_hours", 24L, 1L, 365L * DAY_MS);
        hourlyGoldPerLevel = longValue(values, "hourly_gold_per_level", 5_000L, 0L, Long.MAX_VALUE);
        hourlyCapsulePerFiveLevels = integer(values, "hourly_capsule_per_5_levels", 1, 0, 1_000_000);
        growthBase = longValue(values, "growth_base", 100L, 1L, 1_000_000_000L);
        growthExponent = decimal(values, "growth_exponent", 1.40D, 0.10D, 10D);
        waterBase = integer(values, "water_required_base", 20, 0, 1_000_000);
        waterPerLevel = integer(values, "water_required_per_level", 10, 0, 1_000_000);
        fertilizerLevelsPerUnit = integer(values, "fertilizer_levels_per_unit", 3, 1, 127);
        clanLevelsPerTreeLevel = integer(values, "clan_levels_per_tree_level", 2, 1, 127);
        waterGrowthBase = integer(values, "growth_per_water_base", 10, 0, 1_000_000);
        waterGrowthLevelBonusCap = integer(values, "growth_per_water_level_bonus_cap", 10, 0, 1_000_000);
        fertilizerGrowthMultiplier = integer(values, "fertilizer_growth_multiplier", 5, 0, 1_000_000);
        upgradeDays = parseUpgradeDays(values, maxLevel);
    }

    public static ClanTreeConfig load(Path path) {
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            values.load(input);
        } catch (IOException error) {
            Logger.warning("Không đọc được " + path + ", dùng cấu hình Cây bang mặc định.\n");
        }
        return from(values);
    }

    public static ClanTreeConfig from(Properties values) {
        return new ClanTreeConfig(values == null ? new Properties() : values);
    }

    public int maxLevel() { return maxLevel; }
    public int waterItemId() { return waterItemId; }
    public int fertilizerItemId() { return fertilizerItemId; }
    public int dailyWaterLimit() { return dailyWaterLimit; }
    public int dailyFertilizerLimit() { return dailyFertilizerLimit; }
    public long actionCooldownMs() { return actionCooldownMs; }
    public long helpCooldownMs() { return helpCooldownMs; }
    public long helpDurationMs() { return helpDurationMs; }
    public long productionCapMs() { return productionCapMs; }

    public long growthRequired(int level) {
        double scaled = growthBase * Math.pow(normalizeLevel(level), growthExponent);
        return !Double.isFinite(scaled) || scaled >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(1L, Math.round(scaled));
    }

    public int waterRequired(int level) {
        return saturatedInt(BigInteger.valueOf(waterBase)
                .add(BigInteger.valueOf(waterPerLevel).multiply(BigInteger.valueOf(normalizeLevel(level)))));
    }

    public int fertilizerRequired(int level) {
        return Math.max(0, (normalizeLevel(level) + 1) / fertilizerLevelsPerUnit);
    }

    public int minimumClanLevel(int level) {
        return Math.max(1, (normalizeLevel(level) + 1) / clanLevelsPerTreeLevel);
    }

    public int growthFromWater(int level) {
        return saturatedInt(BigInteger.valueOf(waterGrowthBase)
                .add(BigInteger.valueOf(Math.min(waterGrowthLevelBonusCap,
                        Math.max(0, normalizeLevel(level) - 1)))));
    }

    public int growthFromFertilizer(int level) {
        return saturatedInt(BigInteger.valueOf(growthFromWater(level))
                .multiply(BigInteger.valueOf(fertilizerGrowthMultiplier)));
    }

    public long upgradeDurationMs(int targetLevel) {
        return saturatedLong(BigInteger.valueOf(upgradeDaysForTargetLevel(targetLevel))
                .multiply(BigInteger.valueOf(DAY_MS)));
    }

    public int upgradeDaysForTargetLevel(int targetLevel) {
        if (upgradeDays.length == 0) {
            return 0;
        }
        int index = Math.max(0, Math.min(upgradeDays.length - 1, targetLevel - 2));
        return upgradeDays[index];
    }

    public ProductionYield productionYield(int level, long hours, int vitalityPercent, int yieldBasisPoints) {
        long safeHours = Math.max(0L, hours);
        int safeVitality = Math.max(0, Math.min(100, vitalityPercent));
        int safeBasisPoints = Math.max(0, Math.min(1_000_000, yieldBasisPoints));
        BigInteger bonus = BigInteger.valueOf(10_000L + safeBasisPoints);
        BigInteger gold = BigInteger.valueOf(hourlyGoldPerLevel)
                .multiply(BigInteger.valueOf(normalizeLevel(level)))
                .multiply(BigInteger.valueOf(safeHours))
                .multiply(BigInteger.valueOf(safeVitality))
                .divide(BigInteger.valueOf(100L))
                .multiply(bonus)
                .divide(BigInteger.valueOf(10_000L));
        BigInteger capsule = BigInteger.valueOf(safeHours)
                .multiply(BigInteger.valueOf(normalizeLevel(level) / 5L))
                .multiply(BigInteger.valueOf(hourlyCapsulePerFiveLevels))
                .multiply(bonus)
                .divide(BigInteger.valueOf(10_000L));
        return new ProductionYield(saturatedLong(gold), saturatedInt(capsule));
    }

    private int normalizeLevel(int level) {
        return Math.max(1, Math.min(maxLevel, level));
    }

    private static int[] parseUpgradeDays(Properties values, int maxLevel) {
        int[] fallback = defaultUpgradeDays(maxLevel);
        if (maxLevel <= 1) {
            return fallback;
        }
        String dynamicKey = "upgrade_days_to_levels_2_" + maxLevel;
        String raw = values.getProperty(dynamicKey);
        if ((raw == null || raw.isBlank()) && maxLevel == 20) {
            raw = values.getProperty("upgrade_days_to_levels_2_20");
        }
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String[] parts = raw.split(",");
        if (parts.length != maxLevel - 1) {
            return fallback;
        }
        int[] result = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                result[i] = Math.max(0, Math.min(3_650, Integer.parseInt(parts[i].trim())));
            }
            return result;
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    private static int[] defaultUpgradeDays(int maxLevel) {
        int[] result = new int[Math.max(0, maxLevel - 1)];
        for (int i = 0; i < result.length; i++) {
            result[i] = i < DEFAULT_UPGRADE_DAYS.length
                    ? DEFAULT_UPGRADE_DAYS[i] : DEFAULT_UPGRADE_DAYS[DEFAULT_UPGRADE_DAYS.length - 1];
        }
        return result;
    }

    private static long minutes(Properties values, String key, long fallback, long min, long maxMs) {
        return unitMillis(values, key, fallback, min, maxMs, MINUTE_MS);
    }

    private static long hours(Properties values, String key, long fallback, long min, long maxMs) {
        return unitMillis(values, key, fallback, min, maxMs, HOUR_MS);
    }

    private static long unitMillis(Properties values, String key, long fallback, long min, long maxMs, long unit) {
        long amount = longValue(values, key, fallback, min, maxMs / unit);
        return amount > maxMs / unit ? maxMs : amount * unit;
    }

    private static int integer(Properties values, String key, int fallback, int min, int max) {
        return (int) longValue(values, key, fallback, min, max);
    }

    private static long longValue(Properties values, String key, long fallback, long min, long max) {
        try {
            long value = Long.parseLong(values.getProperty(key, String.valueOf(fallback)).trim());
            return Math.max(min, Math.min(max, value));
        } catch (RuntimeException error) {
            return fallback;
        }
    }

    private static double decimal(Properties values, String key, double fallback, double min, double max) {
        try {
            BigDecimal value = new BigDecimal(values.getProperty(key, String.valueOf(fallback)).trim());
            return value.max(BigDecimal.valueOf(min)).min(BigDecimal.valueOf(max))
                    .setScale(6, RoundingMode.HALF_UP).doubleValue();
        } catch (RuntimeException error) {
            return fallback;
        }
    }

    private static int saturatedInt(BigInteger value) {
        return value.signum() <= 0 ? 0 : value.min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue();
    }

    private static long saturatedLong(BigInteger value) {
        return value.signum() <= 0 ? 0L : value.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }

    public record ProductionYield(long gold, int capsule) {
    }
}
