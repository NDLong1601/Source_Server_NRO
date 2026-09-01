package nro.models.activity;

/** Immutable option range declared in a published Activity reward. */
public final class ActivityRewardOption {

    private final int optionId;
    private final int paramMin;
    private final int paramMax;

    public ActivityRewardOption(int optionId, int paramMin, int paramMax) {
        this.optionId = optionId;
        this.paramMin = paramMin;
        this.paramMax = paramMax;
    }

    public int getOptionId() {
        return optionId;
    }

    public int getParamMin() {
        return paramMin;
    }

    public int getParamMax() {
        return paramMax;
    }
}
