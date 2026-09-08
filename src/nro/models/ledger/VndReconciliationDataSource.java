package nro.models.ledger;

import java.util.List;

/**
 * Read-only persistence seam for SEC-07. The JDBC implementation owns the
 * consistency model; the calculator below remains database-independent.
 */
public interface VndReconciliationDataSource {

    ReconciliationSnapshot readVndSnapshot(VndReconciliationRequest request);

    record LedgerMovement(
            long rowId,
            long ownerId,
            String ownerType,
            String currency,
            String operationId,
            String businessKey,
            String transactionType,
            long amount,
            Long requestedAmount,
            Long appliedDelta,
            Long balanceBefore,
            Long balanceAfter,
            int legIndex,
            long durableSequence,
            String payloadFingerprint,
            String schemaVersion,
            String source,
            long committedAtMillis,
            boolean durableEvidence) {
    }

    record OutboxMovement(
            long rowId,
            long ownerId,
            String ownerType,
            String currency,
            String operationId,
            String businessKey,
            String productType,
            long amount,
            String payloadFingerprint,
            String status,
            int retryCount,
            long createdAtMillis,
            long deliveredAtMillis) {
    }

    record OwnerSnapshot(
            long ownerId,
            long persistedBalance,
            List<LedgerMovement> ledger,
            List<OutboxMovement> outbox,
            boolean coverageComplete,
            String unavailableCode,
            VndReconciliationCalculator.OwnerReconciliation evaluated) {

        public OwnerSnapshot {
            ledger = ledger == null ? List.of() : List.copyOf(ledger);
            outbox = outbox == null ? List.of() : List.copyOf(outbox);
        }

        public OwnerSnapshot(long ownerId, long persistedBalance,
                List<LedgerMovement> ledger, List<OutboxMovement> outbox,
                boolean coverageComplete, String unavailableCode) {
            this(ownerId, persistedBalance, ledger, outbox, coverageComplete, unavailableCode, null);
        }

        public static OwnerSnapshot unavailable(long ownerId, String code) {
            return new OwnerSnapshot(ownerId, 0L, List.of(), List.of(), false, code);
        }
    }

    record ReconciliationSnapshot(
            long cutoffLedgerId,
            long cutoffAtMillis,
            List<OwnerSnapshot> owners,
            long nextCursor,
            boolean hasMoreOwners,
            boolean coverageComplete,
            List<Long> unresolvedOwnerIds) {

        public ReconciliationSnapshot {
            owners = owners == null ? List.of() : List.copyOf(owners);
            unresolvedOwnerIds = unresolvedOwnerIds == null ? List.of() : List.copyOf(unresolvedOwnerIds);
        }

        public ReconciliationSnapshot(long cutoffLedgerId, long cutoffAtMillis,
                List<OwnerSnapshot> owners, long nextCursor,
                boolean hasMoreOwners, boolean coverageComplete) {
            this(cutoffLedgerId, cutoffAtMillis, owners, nextCursor,
                    hasMoreOwners, coverageComplete, List.of());
        }
    }
}
