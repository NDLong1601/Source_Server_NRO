package nro.models.player;

/**
 * Metadata and tracking context attached to a player wallet mutation.
 */
public final class WalletMutationContext {

    private final WalletReason reason;
    private final String transactionId;
    private final String description;

    public WalletMutationContext(WalletReason reason, String transactionId, String description) {
        this.reason = reason != null ? reason : WalletReason.OTHER;
        this.transactionId = transactionId;
        this.description = description;
    }

    public static WalletMutationContext of(WalletReason reason) {
        return new WalletMutationContext(reason, null, null);
    }

    /**
     * Creates descriptive metadata for a mutation that has no durable request ID.
     * Durable/idempotent workflows must use the three-argument overload.
     */
    public static WalletMutationContext of(WalletReason reason, String description) {
        return new WalletMutationContext(reason, null, description);
    }

    public static WalletMutationContext of(WalletReason reason, String transactionId, String description) {
        return new WalletMutationContext(reason, transactionId, description);
    }

    public WalletReason getReason() {
        return reason;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return "WalletMutationContext{"
                + "reason=" + reason
                + (transactionId != null ? ", txId='" + transactionId + '\'' : "")
                + (description != null ? ", desc='" + description + '\'' : "")
                + '}';
    }
}
