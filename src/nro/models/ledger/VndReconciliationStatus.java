package nro.models.ledger;

/**
 * Finding codes emitted by the SEC-07 VND reconciliation contract.
 *
 * These values are intentionally stable: operators and tests must branch on a
 * code, not on a localized log message.
 */
public enum VndReconciliationStatus {
    OK(0),
    BALANCE_DRIFT(70),
    MISSING_BASELINE(80),
    DUPLICATE_BASELINE(90),
    CHAIN_BROKEN(100),
    ORPHAN_LEDGER(110),
    ORPHAN_OUTBOX(120),
    DELIVERY_RETRYABLE(125),
    STALE_PENDING(130),
    UNKNOWN_TRANSACTION_TYPE(140),
    INCOMPLETE_COVERAGE(150),
    DELIVERY_QUARANTINED(180),
    UNAVAILABLE(200);

    private final int severity;

    VndReconciliationStatus(int severity) {
        this.severity = severity;
    }

    public int severity() {
        return severity;
    }
}
