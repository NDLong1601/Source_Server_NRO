package nro.models.activity;

/**
 * Stable source keys for Activity Points. Gameplay hooks are added in Phase 2;
 * the defaults here let the core enforce caps and unique events consistently.
 */
public enum ActivityType {
    DAILY_LOGIN(ActivityCategory.LOGIN, 5, 5, null, 0, true),
    DAILY_CHECKIN(ActivityCategory.LOGIN, 5, 5, null, 0, true),
    PEA_HARVEST(ActivityCategory.LIFE, 5, 5, null, 0, false),
    SIDE_TASK_EASY(ActivityCategory.PVE, 5, 30, "SIDE_TASK", 30, false),
    SIDE_TASK_NORMAL(ActivityCategory.PVE, 8, 30, "SIDE_TASK", 30, false),
    SIDE_TASK_HARD(ActivityCategory.PVE, 12, 30, "SIDE_TASK", 30, false),
    SIDE_TASK_VERY_HARD(ActivityCategory.PVE, 15, 30, "SIDE_TASK", 30, false),
    SIDE_TASK_SPECIAL(ActivityCategory.PVE, 20, 30, "SIDE_TASK", 30, false),
    CLAN_CHECKIN(ActivityCategory.SOCIAL, 5, 5, null, 0, true),
    FISH_CATCH(ActivityCategory.LIFE, 2, 10, null, 0, false),
    PVP_WIN(ActivityCategory.PVP, 5, 10, null, 0, true),
    DUNGEON_CLEAR(ActivityCategory.PVE, 10, 20, null, 0, true),
    BOSS_KILL(ActivityCategory.PVE, 5, 10, null, 0, true),
    DIVERSITY_BONUS(ActivityCategory.BONUS, 10, 10, null, 0, true);

    private final ActivityCategory category;
    private final int defaultPoints;
    private final int dailyCap;
    private final String groupKey;
    private final int groupDailyCap;
    private final boolean requiresDedupeKey;

    ActivityType(ActivityCategory category, int defaultPoints, int dailyCap,
            String groupKey, int groupDailyCap, boolean requiresDedupeKey) {
        this.category = category;
        this.defaultPoints = defaultPoints;
        this.dailyCap = dailyCap;
        this.groupKey = groupKey;
        this.groupDailyCap = groupDailyCap;
        this.requiresDedupeKey = requiresDedupeKey;
    }

    public ActivityCategory getCategory() {
        return category;
    }

    public int getDefaultPoints() {
        return defaultPoints;
    }

    public int getDailyCap() {
        return dailyCap;
    }

    public String getGroupKey() {
        return groupKey;
    }

    public int getGroupDailyCap() {
        return groupDailyCap;
    }

    public boolean requiresDedupeKey() {
        return requiresDedupeKey;
    }
}
