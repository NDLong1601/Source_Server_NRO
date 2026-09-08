package nro.models.player;

/**
 * Result status for PlayerWallet mutation requests.
 */
public enum WalletStatus {
    SUCCESS,
    INVALID_AMOUNT,
    INVALID_BALANCE,
    INSUFFICIENT_FUNDS,
    LIMIT_EXCEEDED,
    INACTIVE,
    STATE_CHANGED,
    UNAUTHORIZED_PLAN
}
