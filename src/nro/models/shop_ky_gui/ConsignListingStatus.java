package nro.models.shop_ky_gui;

/**
 * SEC-04: Authoritative lifecycle status for consignment shop listings.
 * Valid state transitions:
 *   ACTIVE -> SOLD (successful purchase)
 *   ACTIVE -> CANCELLED (seller cancellation)
 *   SOLD   -> CLAIMED (seller claiming proceeds)
 */
public enum ConsignListingStatus {
    ACTIVE,
    SOLD,
    CLAIMED,
    CANCELLED;

    public static ConsignListingStatus fromString(String val) {
        if (val == null || val.trim().isEmpty()) {
            return CANCELLED;
        }
        try {
            return ConsignListingStatus.valueOf(val.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return CANCELLED;
        }
    }
}
