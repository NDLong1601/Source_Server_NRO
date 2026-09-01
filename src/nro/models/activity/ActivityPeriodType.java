package nro.models.activity;

/** The two independent Activity Points claim periods. */
public enum ActivityPeriodType {
    DAILY("ngày"),
    WEEKLY("tuần");

    private final String vietnameseName;

    ActivityPeriodType(String vietnameseName) {
        this.vietnameseName = vietnameseName;
    }

    public String getVietnameseName() {
        return vietnameseName;
    }
}
