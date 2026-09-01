package nro.models.activity;

/** Immutable runtime override for one stable ActivityType source key. */
public final class ActivitySourceRule {

    private final boolean enabled;
    private final ActivityCategory category;
    private final int points;
    private final int dailyCap;
    private final String groupKey;
    private final int groupDailyCap;

    public ActivitySourceRule(boolean enabled, ActivityCategory category, int points,
            int dailyCap, String groupKey, int groupDailyCap) {
        this.enabled = enabled;
        this.category = category;
        this.points = points;
        this.dailyCap = dailyCap;
        this.groupKey = groupKey;
        this.groupDailyCap = groupDailyCap;
    }

    public static ActivitySourceRule defaults(ActivityType type) {
        return new ActivitySourceRule(true, type.getCategory(), type.getDefaultPoints(),
                type.getDailyCap(), type.getGroupKey(), type.getGroupDailyCap());
    }

    public boolean isEnabled() { return enabled; }
    public ActivityCategory getCategory() { return category; }
    public int getPoints() { return points; }
    public int getDailyCap() { return dailyCap; }
    public String getGroupKey() { return groupKey; }
    public int getGroupDailyCap() { return groupDailyCap; }
}
