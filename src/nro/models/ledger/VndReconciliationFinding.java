package nro.models.ledger;

/**
 * Safe, structured reconciliation finding. It contains identifiers and codes,
 * never entitlement JSON, SQL, credentials, or a full player snapshot.
 */
public record VndReconciliationFinding(
        VndReconciliationStatus status,
        long ownerId,
        String currency,
        String operationId,
        String detailCode) {

    public VndReconciliationFinding {
        status = status == null ? VndReconciliationStatus.UNAVAILABLE : status;
        currency = currency == null ? "VND" : currency;
        operationId = operationId == null ? "" : operationId;
        detailCode = detailCode == null ? "UNSPECIFIED" : detailCode;
    }
}
