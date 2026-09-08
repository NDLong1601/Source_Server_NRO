package nro.models.player;

/**
 * Standard business reason categories for all player wallet mutations.
 */
public enum WalletReason {
    SHOP_PURCHASE,
    ITEM_SELL,
    ITEM_PICKUP,
    COMBINE_FEE,
    LUCKY_ROUND,
    TRADE,
    CONSIGN_PURCHASE,
    CONSIGN_PROCEEDS,
    CLAN_DEPOSIT,
    CLAN_REWARD,
    ACTIVITY_REWARD,
    ACHIEVEMENT_REWARD,
    TASK_REWARD,
    ADMIN_ADJUSTMENT,
    GIFT_TRANSFER,
    PVP_WAGER,
    VND_ENTITLEMENT,
    LOAD,       // Restricted internal hydration
    RECOVERY,   // Restricted internal recovery
    OTHER;

    public boolean isRestricted() {
        return this == LOAD || this == RECOVERY;
    }
}
