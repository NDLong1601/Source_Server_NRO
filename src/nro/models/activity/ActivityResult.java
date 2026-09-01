package nro.models.activity;

/** Result code returned by the core before gameplay hooks and rewards exist. */
public final class ActivityResult {

    public enum Reason {
        AWARDED,
        SHADOW_AWARDED,
        FEATURE_DISABLED,
        SOURCE_DISABLED,
        SHADOW_ONLY,
        SOURCE_CAP_REACHED,
        DAILY_CAP_REACHED,
        DUPLICATE_EVENT,
        INVALID_DEDUPE_KEY,
        INVALID_REQUEST,
        NOT_ELIGIBLE
    }

    public final Reason reason;
    public final int awarded;
    public final int dailyPoints;
    /** Portion of awarded that came from the one-time DIVERSITY_BONUS source. */
    public final int diversityBonus;

    private ActivityResult(Reason reason, int awarded, int dailyPoints, int diversityBonus) {
        this.reason = reason;
        this.awarded = awarded;
        this.dailyPoints = dailyPoints;
        this.diversityBonus = diversityBonus;
    }

    public static ActivityResult of(Reason reason, int awarded, int dailyPoints) {
        return of(reason, awarded, dailyPoints, 0);
    }

    public static ActivityResult of(Reason reason, int awarded, int dailyPoints, int diversityBonus) {
        int safeAwarded = Math.max(0, awarded);
        return new ActivityResult(reason, safeAwarded, Math.max(0, dailyPoints),
                Math.max(0, Math.min(safeAwarded, diversityBonus)));
    }

    public boolean isAwarded() {
        return (reason == Reason.AWARDED || reason == Reason.SHADOW_AWARDED) && awarded > 0;
    }
}
