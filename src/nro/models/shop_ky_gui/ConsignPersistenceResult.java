package nro.models.shop_ky_gui;

/** Result of one durable consignment transaction. */
public final class ConsignPersistenceResult {

    public enum Status {
        COMMITTED,
        CONFLICT,
        ID_EXHAUSTED,
        FAILED,
        UNKNOWN
    }

    private final Status status;
    private final int listingId;

    private ConsignPersistenceResult(Status status, int listingId) {
        this.status = status;
        this.listingId = listingId;
    }

    public static ConsignPersistenceResult committed(int listingId) {
        return new ConsignPersistenceResult(Status.COMMITTED, listingId);
    }

    public static ConsignPersistenceResult of(Status status) {
        return new ConsignPersistenceResult(status, -1);
    }

    public Status getStatus() {
        return status;
    }

    public int getListingId() {
        return listingId;
    }

    public boolean isCommitted() {
        return status == Status.COMMITTED;
    }
}
