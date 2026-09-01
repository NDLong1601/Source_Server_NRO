package nro.models.activity;

/** Result returned by a tier claim attempt; no exception is exposed to a player. */
public final class ActivityClaimResult {

    public enum Reason {
        SUCCESS,
        FEATURE_DISABLED,
        REWARDS_DISABLED,
        TIER_NOT_FOUND,
        TIER_DISABLED,
        NOT_ELIGIBLE,
        ALREADY_CLAIMED,
        INVENTORY_FULL,
        INVALID_REWARD,
        GENDER_MISMATCH,
        DELIVERY_FAILED
    }

    private final Reason reason;
    private final String message;

    private ActivityClaimResult(Reason reason, String message) {
        this.reason = reason;
        this.message = message;
    }

    public static ActivityClaimResult of(Reason reason, String message) {
        return new ActivityClaimResult(reason, message);
    }

    public Reason getReason() { return reason; }
    public String getMessage() { return message; }
    public boolean isSuccess() { return reason == Reason.SUCCESS; }
}
