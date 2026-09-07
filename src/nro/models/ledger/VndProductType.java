package nro.models.ledger;

/**
 * SEC-05: Authoritative catalog of in-scope VND products and ledger operations.
 */
public enum VndProductType {
    TRADE_GOLD("DEBIT_GOLD_CONVERT", "Quy đổi thỏi vàng"),
    TRADE_GEM("DEBIT_GEM_CONVERT", "Quy đổi ngọc"),
    VIP_1("DEBIT_VIP", "Gói VIP 1"),
    VIP_2("DEBIT_VIP", "Gói VIP 2"),
    VIP_3("DEBIT_VIP", "Gói VIP 3"),
    VIP_4("DEBIT_VIP", "Gói VIP 4"),
    ADMIN_CREDIT("CREDIT_ADMIN", "Điều chỉnh admin"),
    BASELINE_OPENING("BASELINE_OPENING", "Số dư ban đầu");

    private final String ledgerTransactionType;
    private final String displayName;

    VndProductType(String ledgerTransactionType, String displayName) {
        this.ledgerTransactionType = ledgerTransactionType;
        this.displayName = displayName;
    }

    public String getLedgerTransactionType() {
        return ledgerTransactionType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isVip() {
        return this == VIP_1 || this == VIP_2 || this == VIP_3 || this == VIP_4;
    }

    public int getVipLevel() {
        return switch (this) {
            case VIP_1 -> 1;
            case VIP_2 -> 2;
            case VIP_3 -> 3;
            case VIP_4 -> 4;
            default -> 0;
        };
    }

    public int getFixedCost() {
        return switch (this) {
            case VIP_1 -> 50_000;
            case VIP_2 -> 100_000;
            case VIP_3 -> 150_000;
            case VIP_4 -> 200_000;
            default -> -1;
        };
    }
}
