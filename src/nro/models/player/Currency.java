package nro.models.player;

/**
 * Spendable currencies supported by the PlayerWallet domain boundary.
 */
public enum Currency {
    GOLD("Vàng"),
    GEM("Ngọc xanh"),
    RUBY("Hồng ngọc"),
    COUPON("Điểm");

    private final String displayName;

    Currency(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public long getMaxLimit() {
        return switch (this) {
            case GOLD -> PlayerConfig.getMaxGold();
            case GEM -> (long) PlayerConfig.getMaxGem();
            case RUBY -> (long) PlayerConfig.getMaxRuby();
            case COUPON -> (long) PlayerConfig.getMaxCoupon();
        };
    }
}
