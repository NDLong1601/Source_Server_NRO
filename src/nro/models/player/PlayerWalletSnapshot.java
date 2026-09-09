package nro.models.player;

public record PlayerWalletSnapshot(long gold, int gem, int ruby, int coupon) {

    public PlayerWalletSnapshot {
        if (gold < 0L || gold > PlayerConfig.getMaxGold()
                || gem < 0 || gem > PlayerConfig.getMaxGem()
                || ruby < 0 || ruby > PlayerConfig.getMaxRuby()
                || coupon < 0 || coupon > PlayerConfig.getMaxCoupon()) {
            throw new IllegalArgumentException("Wallet snapshot contains an invalid balance");
        }
    }
}
