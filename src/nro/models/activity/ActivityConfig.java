package nro.models.activity;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Immutable runtime snapshot. Database config becomes the source of truth once published. */
public final class ActivityConfig {

    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final boolean enabled;
    private final boolean shadowMode;
    private final boolean rewardsEnabled;
    private final boolean emergencyDisabled;
    private final boolean failClosed;
    private final ZoneId zoneId;
    private final int dailyResetHour;
    private final DayOfWeek weeklyResetDay;
    private final int dailyMax;
    private final int qualifiedDailyPoints;
    private final int diversityCategoryCount;
    private final int diversityBonus;
    private final long pollIntervalMs;
    private final long revision;
    private final List<ActivityTier> dailyTiers;
    private final List<ActivityTier> weeklyTiers;
    private final Map<ActivityType, ActivitySourceRule> sourceRules;

    public ActivityConfig(boolean enabled, boolean shadowMode, boolean rewardsEnabled,
            boolean emergencyDisabled, boolean failClosed, ZoneId zoneId,
            int dailyResetHour, DayOfWeek weeklyResetDay, int dailyMax,
            int qualifiedDailyPoints, int diversityCategoryCount, int diversityBonus,
            long pollIntervalMs, long revision) {
        this(enabled, shadowMode, rewardsEnabled, emergencyDisabled, failClosed, zoneId,
                dailyResetHour, weeklyResetDay, dailyMax, qualifiedDailyPoints,
                diversityCategoryCount, diversityBonus, pollIntervalMs, revision,
                defaultDailyTiers(), defaultWeeklyTiers());
    }

    public ActivityConfig(boolean enabled, boolean shadowMode, boolean rewardsEnabled,
            boolean emergencyDisabled, boolean failClosed, ZoneId zoneId,
            int dailyResetHour, DayOfWeek weeklyResetDay, int dailyMax,
            int qualifiedDailyPoints, int diversityCategoryCount, int diversityBonus,
            long pollIntervalMs, long revision, List<ActivityTier> dailyTiers,
            List<ActivityTier> weeklyTiers) {
        this(enabled, shadowMode, rewardsEnabled, emergencyDisabled, failClosed, zoneId,
                dailyResetHour, weeklyResetDay, dailyMax, qualifiedDailyPoints,
                diversityCategoryCount, diversityBonus, pollIntervalMs, revision,
                dailyTiers, weeklyTiers, defaultSourceRules());
    }

    public ActivityConfig(boolean enabled, boolean shadowMode, boolean rewardsEnabled,
            boolean emergencyDisabled, boolean failClosed, ZoneId zoneId,
            int dailyResetHour, DayOfWeek weeklyResetDay, int dailyMax,
            int qualifiedDailyPoints, int diversityCategoryCount, int diversityBonus,
            long pollIntervalMs, long revision, List<ActivityTier> dailyTiers,
            List<ActivityTier> weeklyTiers, Map<ActivityType, ActivitySourceRule> sourceRules) {
        this.emergencyDisabled = emergencyDisabled;
        this.enabled = enabled && !emergencyDisabled;
        this.shadowMode = shadowMode;
        this.rewardsEnabled = rewardsEnabled && this.enabled && !emergencyDisabled;
        this.failClosed = failClosed;
        this.zoneId = zoneId == null ? DEFAULT_ZONE : zoneId;
        this.dailyResetHour = clamp(dailyResetHour, 0, 23);
        this.weeklyResetDay = weeklyResetDay == null ? DayOfWeek.MONDAY : weeklyResetDay;
        this.dailyMax = clamp(dailyMax, 1, 1_000);
        this.qualifiedDailyPoints = clamp(qualifiedDailyPoints, 1, this.dailyMax);
        this.diversityCategoryCount = clamp(diversityCategoryCount, 1, ActivityCategory.values().length - 1);
        this.diversityBonus = clamp(diversityBonus, 0, this.dailyMax);
        this.pollIntervalMs = clampLong(pollIntervalMs, 1_000L, 60_000L);
        this.revision = Math.max(0L, revision);
        this.dailyTiers = immutableTiers(dailyTiers);
        this.weeklyTiers = immutableTiers(weeklyTiers);
        this.sourceRules = immutableSourceRules(sourceRules);
    }

    public static ActivityConfig defaults() {
        return new ActivityConfig(false, true, false, false, true, DEFAULT_ZONE,
                0, DayOfWeek.MONDAY, 100, 80, 4, 10, 5_000L, 0L);
    }

    public static ActivityConfig fromProperties(Properties properties, long revision) {
        Properties values = properties == null ? new Properties() : properties;
        boolean enabled = bool(values, "activity.enabled", false);
        boolean shadow = bool(values, "activity.shadow.mode", true);
        boolean rewards = bool(values, "activity.rewards.enabled", false);
        boolean emergency = bool(values, "activity.emergency.disable", false);
        boolean failClosed = bool(values, "activity.config.fail.closed", true);
        ZoneId zone = zone(values.getProperty("activity.timezone", DEFAULT_ZONE.getId()));
        DayOfWeek resetDay = day(values.getProperty("activity.weekly.reset.day", "MONDAY"));
        int dailyMax = integer(values, "activity.daily.max", 100, 1, 1_000);
        int qualified = integer(values, "activity.qualified.daily.points", 80, 1, dailyMax);
        int categoryCount = integer(values, "activity.diversity.category.count", 4, 1, ActivityCategory.values().length - 1);
        int bonus = integer(values, "activity.diversity.bonus", 10, 0, dailyMax);
        int pollSeconds = integer(values, "activity.config.poll.seconds", 5, 1, 60);
        return new ActivityConfig(enabled, shadow, rewards, emergency, failClosed, zone,
                integer(values, "activity.daily.reset.hour", 0, 0, 23), resetDay,
                dailyMax, qualified, categoryCount, bonus, pollSeconds * 1_000L, revision);
    }

    public ActivityConfig withDatabaseValues(Boolean databaseEnabled, Boolean databaseShadow,
            Boolean databaseRewards, String databaseTimezone, Integer databaseResetHour,
            String databaseResetDay, Integer databaseDailyMax, Integer databaseQualifiedPoints,
            Integer databaseDiversityCategories, Integer databaseDiversityBonus, long databaseRevision) {
        return withDatabaseValues(databaseEnabled, databaseShadow, databaseRewards, databaseTimezone,
                databaseResetHour, databaseResetDay, databaseDailyMax, databaseQualifiedPoints,
                databaseDiversityCategories, databaseDiversityBonus, databaseRevision,
                dailyTiers, weeklyTiers);
    }

    public ActivityConfig withDatabaseValues(Boolean databaseEnabled, Boolean databaseShadow,
            Boolean databaseRewards, String databaseTimezone, Integer databaseResetHour,
            String databaseResetDay, Integer databaseDailyMax, Integer databaseQualifiedPoints,
            Integer databaseDiversityCategories, Integer databaseDiversityBonus, long databaseRevision,
            List<ActivityTier> databaseDailyTiers, List<ActivityTier> databaseWeeklyTiers) {
        return withDatabaseValues(databaseEnabled, databaseShadow, databaseRewards, databaseTimezone,
                databaseResetHour, databaseResetDay, databaseDailyMax, databaseQualifiedPoints,
                databaseDiversityCategories, databaseDiversityBonus, databaseRevision,
                databaseDailyTiers, databaseWeeklyTiers, sourceRules);
    }

    public ActivityConfig withDatabaseValues(Boolean databaseEnabled, Boolean databaseShadow,
            Boolean databaseRewards, String databaseTimezone, Integer databaseResetHour,
            String databaseResetDay, Integer databaseDailyMax, Integer databaseQualifiedPoints,
            Integer databaseDiversityCategories, Integer databaseDiversityBonus, long databaseRevision,
            List<ActivityTier> databaseDailyTiers, List<ActivityTier> databaseWeeklyTiers,
            Map<ActivityType, ActivitySourceRule> databaseSourceRules) {
        int max = databaseDailyMax == null ? dailyMax : clamp(databaseDailyMax, 1, 1_000);
        return new ActivityConfig(
                databaseEnabled == null ? enabled : databaseEnabled,
                databaseShadow == null ? shadowMode : databaseShadow,
                databaseRewards == null ? rewardsEnabled : databaseRewards,
                emergencyDisabled,
                failClosed,
                databaseTimezone == null ? zoneId : zone(databaseTimezone),
                databaseResetHour == null ? dailyResetHour : databaseResetHour,
                databaseResetDay == null ? weeklyResetDay : day(databaseResetDay),
                max,
                databaseQualifiedPoints == null ? qualifiedDailyPoints : databaseQualifiedPoints,
                databaseDiversityCategories == null ? diversityCategoryCount : databaseDiversityCategories,
                databaseDiversityBonus == null ? diversityBonus : databaseDiversityBonus,
                pollIntervalMs,
                databaseRevision,
                databaseDailyTiers == null ? dailyTiers : databaseDailyTiers,
                databaseWeeklyTiers == null ? weeklyTiers : databaseWeeklyTiers,
                databaseSourceRules == null ? sourceRules : databaseSourceRules
        );
    }

    public ActivityConfig disabledForFailure() {
        return new ActivityConfig(false, true, false, emergencyDisabled, failClosed, zoneId,
                dailyResetHour, weeklyResetDay, dailyMax, qualifiedDailyPoints,
                diversityCategoryCount, diversityBonus, pollIntervalMs, revision, dailyTiers, weeklyTiers, sourceRules);
    }

    public boolean isEnabled() { return enabled; }
    public boolean isShadowMode() { return shadowMode; }
    public boolean isRewardsEnabled() { return rewardsEnabled; }
    public boolean isEmergencyDisabled() { return emergencyDisabled; }
    public boolean isFailClosed() { return failClosed; }
    public ZoneId getZoneId() { return zoneId; }
    public int getDailyResetHour() { return dailyResetHour; }
    public DayOfWeek getWeeklyResetDay() { return weeklyResetDay; }
    public int getDailyMax() { return dailyMax; }
    public int getQualifiedDailyPoints() { return qualifiedDailyPoints; }
    public int getDiversityCategoryCount() { return diversityCategoryCount; }
    public int getDiversityBonus() { return diversityBonus; }
    public long getPollIntervalMs() { return pollIntervalMs; }
    public long getRevision() { return revision; }
    public List<ActivityTier> getDailyTiers() { return dailyTiers; }
    public List<ActivityTier> getWeeklyTiers() { return weeklyTiers; }
    public ActivitySourceRule getSourceRule(ActivityType type) {
        ActivitySourceRule rule = sourceRules.get(type);
        return rule == null ? ActivitySourceRule.defaults(type) : rule;
    }
    public Map<ActivityType, ActivitySourceRule> getSourceRules() { return sourceRules; }

    private static List<ActivityTier> immutableTiers(List<ActivityTier> tiers) {
        return Collections.unmodifiableList(new ArrayList<>(tiers == null ? List.of() : tiers));
    }

    private static Map<ActivityType, ActivitySourceRule> immutableSourceRules(
            Map<ActivityType, ActivitySourceRule> rules) {
        EnumMap<ActivityType, ActivitySourceRule> values = new EnumMap<>(ActivityType.class);
        for (ActivityType type : ActivityType.values()) {
            ActivitySourceRule rule = rules == null ? null : rules.get(type);
            values.put(type, rule == null ? ActivitySourceRule.defaults(type) : rule);
        }
        return Collections.unmodifiableMap(values);
    }

    public static Map<ActivityType, ActivitySourceRule> defaultSourceRules() {
        EnumMap<ActivityType, ActivitySourceRule> values = new EnumMap<>(ActivityType.class);
        for (ActivityType type : ActivityType.values()) {
            values.put(type, ActivitySourceRule.defaults(type));
        }
        return values;
    }

    /**
     * Safe starter reward values. They are only a template: runtime reward
     * delivery remains disabled until a later live configuration sets it on.
     */
    public static List<ActivityTier> defaultDailyTiers() {
        return List.of(
                currencyTier("DAILY_20", 0, 20, 10_000),
                currencyTier("DAILY_40", 1, 40, 25_000),
                currencyTier("DAILY_60", 2, 60, 50_000),
                currencyTier("DAILY_80", 3, 80, 100_000),
                currencyTier("DAILY_100", 4, 100, 200_000));
    }

    public static List<ActivityTier> defaultWeeklyTiers() {
        return List.of(
                new ActivityTier("WEEKLY_300", 0, true, 300, 0,
                        List.of(currency(ActivityReward.Kind.GOLD, 300_000))),
                new ActivityTier("WEEKLY_500", 1, true, 500, 5,
                        List.of(currency(ActivityReward.Kind.GOLD, 750_000))));
    }

    private static ActivityTier currencyTier(String id, int claimBit, int threshold, int amount) {
        return new ActivityTier(id, claimBit, true, threshold, 0,
                List.of(currency(ActivityReward.Kind.GOLD, amount)));
    }

    private static ActivityReward currency(ActivityReward.Kind kind, int amount) {
        return new ActivityReward(kind, 0, amount, amount, 3,
                false, false, false, List.of(), 0, 0);
    }

    private static boolean bool(Properties properties, String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) return defaultValue;
        value = value.trim();
        if ("true".equalsIgnoreCase(value) || "1".equals(value)) return true;
        if ("false".equalsIgnoreCase(value) || "0".equals(value)) return false;
        return defaultValue;
    }

    private static int integer(Properties properties, String key, int defaultValue, int min, int max) {
        try {
            return clamp(Integer.parseInt(properties.getProperty(key, "").trim()), min, max);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static ZoneId zone(String raw) {
        try {
            return ZoneId.of(raw == null ? DEFAULT_ZONE.getId() : raw.trim());
        } catch (Exception ignored) {
            return DEFAULT_ZONE;
        }
    }

    private static DayOfWeek day(String raw) {
        try {
            return DayOfWeek.valueOf(raw == null ? "MONDAY" : raw.trim().toUpperCase());
        } catch (Exception ignored) {
            return DayOfWeek.MONDAY;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long clampLong(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
}
