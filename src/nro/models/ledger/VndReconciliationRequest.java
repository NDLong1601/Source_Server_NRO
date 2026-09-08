package nro.models.ledger;

import java.util.UUID;

/**
 * Bounded, read-only SEC-07 reconciliation request.
 */
public record VndReconciliationRequest(
        String runId,
        long afterOwnerId,
        long ownerId,
        String currency,
        int ownerBatchSize,
        int queryTimeoutSeconds,
        long stalePendingAfterMillis,
        boolean fullOwnerScan,
        int runTimeoutSeconds) {

    public VndReconciliationRequest(String runId, long afterOwnerId, long ownerId,
            String currency, int ownerBatchSize, int queryTimeoutSeconds, long stalePendingAfterMillis) {
        this(runId, afterOwnerId, ownerId, currency, ownerBatchSize, queryTimeoutSeconds,
                stalePendingAfterMillis, false, 300);
    }

    public static final int MAX_OWNER_BATCH_SIZE = 1_000;
    public static final int MAX_QUERY_TIMEOUT_SECONDS = 120;
    public static final long MAX_STALE_PENDING_MILLIS = 90L * 24L * 60L * 60L * 1_000L;

    public VndReconciliationRequest {
        if (runId == null || !runId.matches("[A-Za-z0-9._:-]{1,64}")) {
            throw new IllegalArgumentException("runId must be 1..64 ASCII identifier characters");
        }
        if (afterOwnerId < 0L || ownerId < -1L || ownerId == 0L) {
            throw new IllegalArgumentException("owner cursor/filter is invalid");
        }
        if (currency == null || !"VND".equalsIgnoreCase(currency)) {
            throw new IllegalArgumentException("SEC-07 currently reconciles VND only");
        }
        currency = "VND";
        if (fullOwnerScan && (ownerId <= 0L || afterOwnerId != 0L)) {
            throw new IllegalArgumentException("full-owner requires --account-id and no owner cursor");
        }
        if (runTimeoutSeconds < 1 || runTimeoutSeconds > 3600) {
            throw new IllegalArgumentException("run timeout must be 1..3600 seconds");
        }
        if (ownerBatchSize < 1 || ownerBatchSize > MAX_OWNER_BATCH_SIZE) {
            throw new IllegalArgumentException("owner batch size must be 1..1000");
        }
        if (queryTimeoutSeconds < 1 || queryTimeoutSeconds > MAX_QUERY_TIMEOUT_SECONDS) {
            throw new IllegalArgumentException("query timeout must be 1..120 seconds");
        }
        if (stalePendingAfterMillis < 1L || stalePendingAfterMillis > MAX_STALE_PENDING_MILLIS) {
            throw new IllegalArgumentException("stale pending age is outside the supported bound");
        }
    }

    public static VndReconciliationRequest defaults(int ownerBatchSize) {
        return new VndReconciliationRequest(
                UUID.randomUUID().toString().replace("-", ""),
                0L,
                -1L,
                "VND",
                ownerBatchSize,
                30,
                24L * 60L * 60L * 1_000L);
    }

    public VndReconciliationRequest withAfterOwnerId(long cursor) {
        return new VndReconciliationRequest(runId, cursor, ownerId, currency,
                ownerBatchSize, queryTimeoutSeconds, stalePendingAfterMillis, fullOwnerScan, runTimeoutSeconds);
    }
}
