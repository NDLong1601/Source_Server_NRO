package nro.models.player;

/**
 * Immutable outcome of a PlayerWallet mutation.
 */
public final class WalletResult {

    private final WalletStatus status;
    private final Currency currency;
    private final long amountRequested;
    private final long amountApplied;
    private final long balanceBefore;
    private final long balanceAfter;
    private final WalletMutationContext context;
    private final String message;

    public WalletResult(WalletStatus status, Currency currency, long amountRequested,
                        long amountApplied, long balanceBefore, long balanceAfter,
                        WalletMutationContext context, String message) {
        this.status = status;
        this.currency = currency;
        this.amountRequested = amountRequested;
        this.amountApplied = amountApplied;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
        this.context = context;
        this.message = message;
    }

    public static WalletResult success(Currency currency, long requested, long applied,
                                       long before, long after, WalletMutationContext ctx) {
        return new WalletResult(WalletStatus.SUCCESS, currency, requested, applied,
                before, after, ctx, "Thành công");
    }

    public static WalletResult fail(WalletStatus status, Currency currency, long requested,
                                    long currentBalance, WalletMutationContext ctx, String message) {
        return new WalletResult(status, currency, requested, 0L,
                currentBalance, currentBalance, ctx, message);
    }

    public boolean isSuccess() {
        return status == WalletStatus.SUCCESS;
    }

    /**
     * Explicit fail-closed bridge for legacy void workflows. New code should
     * normally branch on {@link #isSuccess()} and return a domain-specific error.
     */
    public WalletResult requireSuccess() {
        if (!isSuccess()) {
            String reason = context != null && context.getReason() != null
                    ? context.getReason().name() : "UNKNOWN";
            throw new IllegalStateException("Wallet mutation rejected: status=" + status
                    + ", currency=" + currency + ", reason=" + reason);
        }
        return this;
    }

    public WalletStatus getStatus() {
        return status;
    }

    public Currency getCurrency() {
        return currency;
    }

    public long getAmountRequested() {
        return amountRequested;
    }

    public long getAmountApplied() {
        return amountApplied;
    }

    public long getBalanceBefore() {
        return balanceBefore;
    }

    public long getBalanceAfter() {
        return balanceAfter;
    }

    public WalletMutationContext getContext() {
        return context;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "WalletResult{"
                + "status=" + status
                + ", currency=" + currency
                + ", requested=" + amountRequested
                + ", applied=" + amountApplied
                + ", before=" + balanceBefore
                + ", after=" + balanceAfter
                + (message != null ? ", msg='" + message + '\'' : "")
                + '}';
    }
}
