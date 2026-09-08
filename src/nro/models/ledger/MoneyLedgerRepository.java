package nro.models.ledger;

import java.util.List;

/**
 * SEC-05: Persistence SPI for durable VND transactions and recoverable delivery outbox.
 */
public interface MoneyLedgerRepository {

    enum DebitStatus {
        SUCCESS,
        INSUFFICIENT_BALANCE,
        ALREADY_COMMITTED,
        ALREADY_PENDING,
        CONFLICTING_PAYLOAD,
        VIP_LIMIT_REACHED,
        UNKNOWN,
        FAILED,
        BASELINE_MISSING
    }

    record DebitResult(
        DebitStatus status,
        int balanceBefore,
        int balanceAfter,
        String purchaseKey,
        String existingStatus
    ) {}

    record OutboxRecord(
        long id,
        String purchaseKey,
        int accountId,
        long playerId,
        VndProductType productType,
        int amount,
        String payloadFingerprint,
        String frozenEntitlementJson,
        String status,
        String errorCode,
        int retryCount,
        long createdAt
    ) {}

    /**
     * Atomically debits account.vnd conditionally and inserts immutable ledger and pending outbox records.
     */
    DebitResult recordDebitAndPendingOutbox(
        String purchaseKey,
        int accountId,
        long playerId,
        VndProductType productType,
        int amount,
        String payloadFingerprint,
        String frozenPayloadJson,
        String policyVersion
    );

    OutboxRecord findOutboxRecord(String purchaseKey);

    int readBalance(int accountId);

    List<OutboxRecord> findPendingDeliveriesForPlayer(long playerId);

    /**
     * Atomically updates player persisted fields and marks the outbox entry DELIVERED.
     */
    boolean markDeliveredWithPlayerState(
        String purchaseKey,
        long playerId,
        String itemsBagJson,
        String dataInventoryJson,
        String dataVipJson,
        String petJson,
        String dataTaskBadgesJson,
        int eventPoint,
        String dataEventJson
    );

    void recordOutboxFailure(String purchaseKey, String errorCode);

}
