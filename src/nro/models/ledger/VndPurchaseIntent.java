package nro.models.ledger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;

/**
 * SEC-05: Server-owned purchase intent.
 * Bound to the authenticated account, live player, product, policy version, and interaction nonce.
 */
public final class VndPurchaseIntent {

    private final String intentToken;
    private final int accountId;
    private final long playerId;
    private final VndProductType productType;
    private final int presetAmount;
    private final String policyVersion;
    private final long createdAt;
    private final long expiresAt;
    private java.lang.ref.WeakReference<nro.models.network.MySession> session;

    void bindSession(nro.models.network.MySession value) {
        session = new java.lang.ref.WeakReference<>(value);
    }

    boolean matches(nro.models.player.Player player) {
        return session != null && session.get() == player.getSession()
            && player.getSession() != null && player.getSession().player == player
            && accountId == player.getSession().userId && playerId == player.id;
    }

    public static final long DEFAULT_TTL_MS = 5 * 60 * 1000L; // 5 minutes

    public VndPurchaseIntent(int accountId, long playerId, VndProductType productType, int presetAmount, String policyVersion) {
        this(UUID.randomUUID().toString().replace("-", ""), accountId, playerId, productType, presetAmount, policyVersion, System.currentTimeMillis(), System.currentTimeMillis() + DEFAULT_TTL_MS);
    }

    public VndPurchaseIntent(String intentToken, int accountId, long playerId, VndProductType productType, int presetAmount, String policyVersion, long createdAt, long expiresAt) {
        this.intentToken = Objects.requireNonNull(intentToken, "intentToken");
        this.accountId = accountId;
        this.playerId = playerId;
        this.productType = Objects.requireNonNull(productType, "productType");
        this.presetAmount = presetAmount;
        this.policyVersion = Objects.requireNonNull(policyVersion, "policyVersion");
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getIntentToken() {
        return intentToken;
    }

    public int getAccountId() {
        return accountId;
    }

    public long getPlayerId() {
        return playerId;
    }

    public VndProductType getProductType() {
        return productType;
    }

    public int getPresetAmount() {
        return presetAmount;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }

    /**
     * Deterministic purchase identity for the durable ledger and delivery outbox.
     */
    public String getPurchaseKey() {
        return "intent:" + accountId + ":" + playerId + ":" + productType.name() + ":" + intentToken;
    }

    /**
     * Computes the SHA-256 fingerprint of the canonical purchase payload.
     */
    public static String computeFingerprint(VndProductType productType, int amount, String policyVersion) {
        String raw = productType.name() + "|" + amount + "|" + policyVersion;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }
}
