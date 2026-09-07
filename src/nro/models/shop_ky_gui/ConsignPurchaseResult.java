package nro.models.shop_ky_gui;

/**
 * SEC-04: Explicit result outcomes for every consignment lifecycle operation.
 */
public final class ConsignPurchaseResult {

    public enum Outcome {
        SUCCESS,
        LISTING_NOT_FOUND,
        ALREADY_SOLD,
        ALREADY_CLAIMED,
        ALREADY_CANCELLED,
        NOT_OWNER,
        SELF_PURCHASE,
        INVALID_LISTING,
        INSUFFICIENT_GOLD,
        INSUFFICIENT_GEM,
        BAG_FULL,
        CONFLICT,
        DATABASE_FAILURE,
        PERSISTENCE_UNKNOWN,
        INVENTORY_CHANGED,
        PLAYER_UNAVAILABLE,
        PRICE_MISMATCH,
        POWER_TOO_LOW,
        INTERNAL_ERROR
    }

    private final Outcome outcome;
    private final String message;
    private final ConsignItem listingSnapshot;

    public ConsignPurchaseResult(Outcome outcome, String message, ConsignItem listingSnapshot) {
        this.outcome = outcome;
        this.message = message;
        this.listingSnapshot = listingSnapshot;
    }

    public static ConsignPurchaseResult success(String message, ConsignItem snapshot) {
        return new ConsignPurchaseResult(Outcome.SUCCESS, message, snapshot);
    }

    public static ConsignPurchaseResult fail(Outcome outcome, String message) {
        return new ConsignPurchaseResult(outcome, message, null);
    }

    public static ConsignPurchaseResult fail(Outcome outcome, String message, ConsignItem snapshot) {
        return new ConsignPurchaseResult(outcome, message, snapshot);
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public boolean isSuccess() {
        return outcome == Outcome.SUCCESS;
    }

    public String getMessage() {
        return message;
    }

    public ConsignItem getListingSnapshot() {
        return listingSnapshot;
    }

    @Override
    public String toString() {
        return "ConsignPurchaseResult{" + "outcome=" + outcome + ", message='" + message + '\'' + '}';
    }
}
