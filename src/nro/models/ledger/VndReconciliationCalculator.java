package nro.models.ledger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure SEC-07 invariant checker. It never repairs balances or changes a
 * ledger/outbox row.
 */
public final class VndReconciliationCalculator {

    private static final int MAX_FINDINGS_PER_OWNER = 2_048;
    private static final String ACCOUNT = "ACCOUNT";
    private static final String VND = "VND";
    private static final long MAX_VND = Integer.MAX_VALUE;
    private static final Set<String> KNOWN_TYPES = Set.of(
            "BASELINE_OPENING", "DEBIT_GOLD_CONVERT", "DEBIT_GEM_CONVERT",
            "DEBIT_VIP", "CREDIT_ADMIN");
    private static final Set<String> KNOWN_OUTBOX_STATUSES = Set.of(
            "PENDING_DELIVERY", "DELIVERED", "FAILED_RETRYABLE", "QUARANTINED");

    private VndReconciliationCalculator() {
    }

    public static OwnerReconciliation evaluate(
            VndReconciliationDataSource.OwnerSnapshot owner,
            long nowMillis,
            long stalePendingAfterMillis) {
        if (owner == null) {
            return new OwnerReconciliation(-1L, 0L, null, List.of(VndReconciliationStatus.UNAVAILABLE),
                    List.of(new VndReconciliationFinding(VndReconciliationStatus.UNAVAILABLE,
                            -1L, VND, "", "OWNER_SNAPSHOT_NULL")));
        }

        LinkedHashSet<VndReconciliationStatus> statuses = new LinkedHashSet<>();
        List<VndReconciliationFinding> findings = new ArrayList<>();
        if (owner.unavailableCode() != null && !owner.unavailableCode().isBlank()) {
            add(statuses, findings, VndReconciliationStatus.UNAVAILABLE, owner.ownerId(), "", owner.unavailableCode());
            return new OwnerReconciliation(owner.ownerId(), owner.persistedBalance(), null,
                    orderedStatuses(statuses), findings);
        }
        if (!owner.coverageComplete()) {
            add(statuses, findings, VndReconciliationStatus.INCOMPLETE_COVERAGE,
                    owner.ownerId(), "", "OWNER_BATCH_TRUNCATED");
        }
        if (owner.persistedBalance() < 0L || owner.persistedBalance() > MAX_VND) {
            add(statuses, findings, VndReconciliationStatus.CHAIN_BROKEN,
                    owner.ownerId(), "", "PERSISTED_BALANCE_OUT_OF_RANGE");
        }

        List<VndReconciliationDataSource.LedgerMovement> movements = new ArrayList<>(owner.ledger());
        movements.sort(Comparator.comparingLong(VndReconciliationDataSource.LedgerMovement::durableSequence)
                .thenComparingLong(VndReconciliationDataSource.LedgerMovement::rowId));
        Map<String, VndReconciliationDataSource.LedgerMovement> uniqueLedgerKeys = new HashMap<>();
        List<VndReconciliationDataSource.LedgerMovement> baselines = new ArrayList<>();
        Map<String, VndReconciliationDataSource.OutboxMovement> outboxByOperation = new HashMap<>();
        Set<Long> seenSequences = new HashSet<>();

        for (VndReconciliationDataSource.OutboxMovement outbox : owner.outbox()) {
            String operationId = clean(outbox.operationId());
            String outboxStatus = clean(outbox.status()).toUpperCase(java.util.Locale.ROOT);
            if (operationId.isEmpty() || outbox.ownerId() != owner.ownerId()
                    || !ACCOUNT.equalsIgnoreCase(clean(outbox.ownerType()))
                    || !VND.equalsIgnoreCase(clean(outbox.currency()))) {
                add(statuses, findings, VndReconciliationStatus.ORPHAN_OUTBOX,
                        owner.ownerId(), operationId, "OUTBOX_OWNER_OR_CURRENCY_MISMATCH");
            }
            if (operationId.isEmpty() || outboxByOperation.putIfAbsent(operationId, outbox) != null) {
                add(statuses, findings, VndReconciliationStatus.ORPHAN_OUTBOX,
                        owner.ownerId(), operationId, "DUPLICATE_OUTBOX_OPERATION");
            }
            if (!KNOWN_OUTBOX_STATUSES.contains(outboxStatus)) {
                add(statuses, findings, VndReconciliationStatus.UNKNOWN_TRANSACTION_TYPE,
                        owner.ownerId(), operationId, "UNKNOWN_OUTBOX_STATUS");
            }
            if ("QUARANTINED".equalsIgnoreCase(outboxStatus)) {
                add(statuses, findings, VndReconciliationStatus.DELIVERY_QUARANTINED,
                        owner.ownerId(), operationId, "QUARANTINED_REQUIRES_MANUAL_INTERVENTION");
            }
            if ("FAILED_RETRYABLE".equalsIgnoreCase(outboxStatus)) {
                add(statuses, findings, VndReconciliationStatus.DELIVERY_RETRYABLE,
                        owner.ownerId(), operationId, "FAILED_RETRYABLE_NOT_DELIVERED");
                if (outbox.createdAtMillis() > 0L
                        && nowMillis - outbox.createdAtMillis() > stalePendingAfterMillis) {
                    add(statuses, findings, VndReconciliationStatus.STALE_PENDING,
                            owner.ownerId(), operationId, "FAILED_RETRYABLE_EXPIRED");
                }
            }
            if (!isSha256(outbox.payloadFingerprint())) {
                add(statuses, findings, VndReconciliationStatus.ORPHAN_OUTBOX,
                        owner.ownerId(), operationId, "OUTBOX_FINGERPRINT_INVALID_OR_MISSING");
            }
            if ("DELIVERED".equalsIgnoreCase(outboxStatus) && outbox.deliveredAtMillis() <= 0L) {
                add(statuses, findings, VndReconciliationStatus.ORPHAN_OUTBOX,
                        owner.ownerId(), operationId, "DELIVERED_TIMESTAMP_MISSING");
            }
            if (!"DELIVERED".equalsIgnoreCase(outboxStatus) && outbox.deliveredAtMillis() > 0L) {
                add(statuses, findings, VndReconciliationStatus.ORPHAN_OUTBOX,
                        owner.ownerId(), operationId, "NON_DELIVERED_HAS_DELIVERED_TIMESTAMP");
            }
            if ("PENDING_DELIVERY".equalsIgnoreCase(outboxStatus)
                    && outbox.createdAtMillis() > 0L
                    && nowMillis - outbox.createdAtMillis() > stalePendingAfterMillis) {
                add(statuses, findings, VndReconciliationStatus.STALE_PENDING,
                        owner.ownerId(), operationId, "PENDING_DELIVERY_EXPIRED");
            }
            if ("PENDING_DELIVERY".equalsIgnoreCase(outboxStatus) && outbox.createdAtMillis() <= 0L) {
                add(statuses, findings, VndReconciliationStatus.INCOMPLETE_COVERAGE,
                        owner.ownerId(), operationId, "PENDING_TIMESTAMP_MISSING");
            }
        }

        for (VndReconciliationDataSource.LedgerMovement movement : movements) {
            String operationId = clean(movement.operationId());
            String type = clean(movement.transactionType());
            if (!KNOWN_TYPES.contains(type)) {
                add(statuses, findings, VndReconciliationStatus.UNKNOWN_TRANSACTION_TYPE,
                        owner.ownerId(), operationId, "UNKNOWN_LEDGER_TYPE");
                continue;
            }
            if (movement.ownerId() != owner.ownerId()
                    || !ACCOUNT.equalsIgnoreCase(clean(movement.ownerType()))
                    || !VND.equalsIgnoreCase(clean(movement.currency()))) {
                add(statuses, findings, VndReconciliationStatus.ORPHAN_LEDGER,
                        owner.ownerId(), operationId, "LEDGER_OWNER_OR_CURRENCY_MISMATCH");
            }
            if (operationId.isEmpty() || clean(movement.businessKey()).isEmpty()) {
                add(statuses, findings, VndReconciliationStatus.INCOMPLETE_COVERAGE,
                        owner.ownerId(), operationId, "MISSING_OPERATION_OR_BUSINESS_KEY");
            }
            String uniqueKey = operationId + "#" + movement.legIndex();
            if (operationId.isEmpty() || uniqueLedgerKeys.putIfAbsent(uniqueKey, movement) != null) {
                add(statuses, findings, VndReconciliationStatus.ORPHAN_LEDGER,
                        owner.ownerId(), operationId, "DUPLICATE_OPERATION_LEG");
            }
            if (movement.legIndex() != 0) {
                add(statuses, findings, VndReconciliationStatus.ORPHAN_LEDGER,
                        owner.ownerId(), operationId, "UNEXPECTED_VND_LEG_INDEX");
            }
            if (movement.durableSequence() <= 0L || !movement.durableEvidence()) {
                add(statuses, findings, VndReconciliationStatus.INCOMPLETE_COVERAGE,
                        owner.ownerId(), operationId, "LEGACY_LEDGER_DURABLE_EVIDENCE");
            }
            if (clean(movement.source()).isEmpty() || movement.committedAtMillis() <= 0L) {
                add(statuses, findings, VndReconciliationStatus.INCOMPLETE_COVERAGE,
                        owner.ownerId(), operationId, "LEDGER_COMMIT_METADATA_MISSING");
            }
            if (!seenSequences.add(movement.durableSequence())) {
                add(statuses, findings, VndReconciliationStatus.CHAIN_BROKEN,
                        owner.ownerId(), operationId, "DUPLICATE_DURABLE_SEQUENCE");
            }

            if ("BASELINE_OPENING".equals(type)) {
                if (isSec07Baseline(movement)) {
                    baselines.add(movement);
                    validateBaseline(owner.ownerId(), movement, statuses, findings);
                } else {
                    add(statuses, findings, VndReconciliationStatus.INCOMPLETE_COVERAGE,
                            owner.ownerId(), operationId, "LEGACY_BASELINE_OUTSIDE_SEC07_COVERAGE");
                }
                continue;
            }

            Long delta = movement.appliedDelta();
            if (delta == null) {
                Long requested = movement.requestedAmount();
                long amount = requested != null ? requested : movement.amount();
                if (amount <= 0L) {
                    add(statuses, findings, VndReconciliationStatus.CHAIN_BROKEN,
                            owner.ownerId(), operationId, "NON_POSITIVE_MOVEMENT_AMOUNT");
                    continue;
                }
                delta = type.startsWith("DEBIT_") ? -amount : amount;
                add(statuses, findings, VndReconciliationStatus.INCOMPLETE_COVERAGE,
                        owner.ownerId(), operationId, "LEGACY_SIGNED_DELTA_DERIVED");
            }
            validateMovement(owner.ownerId(), movement, delta, statuses, findings);
            if (type.startsWith("DEBIT_")) {
                VndReconciliationDataSource.OutboxMovement outbox = outboxByOperation.get(operationId);
                if (outbox == null) {
                    add(statuses, findings, VndReconciliationStatus.ORPHAN_LEDGER,
                            owner.ownerId(), operationId, "DEBIT_MISSING_OUTBOX");
                } else {
                    validateOutbox(owner.ownerId(), movement, outbox, type, statuses, findings);
                }
            }
        }

        for (VndReconciliationDataSource.OutboxMovement outbox : owner.outbox()) {
            String operationId = clean(outbox.operationId());
            if (operationId.isEmpty() || !uniqueLedgerKeys.containsKey(operationId + "#0")) {
                add(statuses, findings, VndReconciliationStatus.ORPHAN_OUTBOX,
                        owner.ownerId(), operationId, "OUTBOX_MISSING_LEDGER");
            }
        }

        baselines.sort(Comparator.comparingLong(VndReconciliationDataSource.LedgerMovement::durableSequence)
                .thenComparingLong(VndReconciliationDataSource.LedgerMovement::rowId));
        if (baselines.isEmpty()) {
            add(statuses, findings, VndReconciliationStatus.MISSING_BASELINE,
                    owner.ownerId(), "", "NO_OPENING_BASELINE");
        } else if (baselines.size() > 1) {
            add(statuses, findings, VndReconciliationStatus.DUPLICATE_BASELINE,
                    owner.ownerId(), baselines.get(1).operationId(), "MORE_THAN_ONE_OPENING_BASELINE");
        }

        Long expected = null;
        if (baselines.size() == 1) {
            VndReconciliationDataSource.LedgerMovement baseline = baselines.get(0);
            expected = baseline.balanceAfter();
            if (expected == null) {
                expected = baseline.appliedDelta();
            }
            if (expected == null) {
                expected = baseline.amount();
                add(statuses, findings, VndReconciliationStatus.INCOMPLETE_COVERAGE,
                        owner.ownerId(), clean(baseline.operationId()), "LEGACY_BASELINE_VALUE_DERIVED");
            }
            try {
                for (VndReconciliationDataSource.LedgerMovement movement : movements) {
                    if ("BASELINE_OPENING".equals(clean(movement.transactionType()))) {
                        continue;
                    }
                    if (movement.durableSequence() < baseline.durableSequence()) {
                        add(statuses, findings, VndReconciliationStatus.INCOMPLETE_COVERAGE,
                                owner.ownerId(), clean(movement.operationId()), "PRE_CUTOVER_MOVEMENT_OUTSIDE_COVERAGE");
                        continue;
                    }
                    Long delta = signedDelta(movement);
                    if (delta != null) {
                        expected = Math.addExact(expected, delta);
                    }
                }
            } catch (ArithmeticException overflow) {
                add(statuses, findings, VndReconciliationStatus.UNAVAILABLE,
                        owner.ownerId(), "", "RECONCILIATION_SUM_OVERFLOW");
                expected = null;
            }

            Long previousAfter = baseline.balanceAfter();
            long previousSequence = baseline.durableSequence();
            for (VndReconciliationDataSource.LedgerMovement movement : movements) {
                if ("BASELINE_OPENING".equals(clean(movement.transactionType()))
                        || movement.durableSequence() < baseline.durableSequence()) {
                    continue;
                }
                if (movement.durableSequence() == previousSequence) {
                    add(statuses, findings, VndReconciliationStatus.CHAIN_BROKEN,
                            owner.ownerId(), clean(movement.operationId()), "NON_INCREASING_DURABLE_ORDER");
                }
                if (previousAfter != null && movement.balanceBefore() != null
                        && !previousAfter.equals(movement.balanceBefore())) {
                    add(statuses, findings, VndReconciliationStatus.CHAIN_BROKEN,
                            owner.ownerId(), clean(movement.operationId()), "BEFORE_AFTER_CHAIN_GAP");
                }
                if (movement.balanceAfter() != null) {
                    previousAfter = movement.balanceAfter();
                }
                previousSequence = movement.durableSequence();
            }
        }

        if (expected != null && owner.persistedBalance() != expected) {
            add(statuses, findings, VndReconciliationStatus.BALANCE_DRIFT,
                    owner.ownerId(), "", "PERSISTED_BALANCE_DIFFERS_FROM_EXPECTED");
        }
        if (statuses.isEmpty()) {
            statuses.add(VndReconciliationStatus.OK);
        }
        return new OwnerReconciliation(owner.ownerId(), owner.persistedBalance(), expected,
                orderedStatuses(statuses), findings);
    }

    private static void validateBaseline(long ownerId,
            VndReconciliationDataSource.LedgerMovement movement,
            Set<VndReconciliationStatus> statuses,
            List<VndReconciliationFinding> findings) {
        if (movement.balanceBefore() != null && movement.balanceBefore() != 0L) {
            add(statuses, findings, VndReconciliationStatus.CHAIN_BROKEN,
                    ownerId, clean(movement.operationId()), "BASELINE_MUST_START_AT_ZERO");
        }
        Long after = movement.balanceAfter();
        long amount = movement.amount();
        if (amount < 0L || movement.requestedAmount() != null && movement.requestedAmount() < 0L) {
            add(statuses, findings, VndReconciliationStatus.CHAIN_BROKEN,
                    ownerId, clean(movement.operationId()), "NEGATIVE_BASELINE_AMOUNT");
        }
        if (movement.requestedAmount() != null && movement.requestedAmount() != amount) {
            add(statuses, findings, VndReconciliationStatus.CHAIN_BROKEN,
                    ownerId, clean(movement.operationId()), "BASELINE_REQUESTED_AMOUNT_MISMATCH");
        }
        if (after != null && after < 0L) {
            add(statuses, findings, VndReconciliationStatus.CHAIN_BROKEN,
                    ownerId, clean(movement.operationId()), "NEGATIVE_BASELINE");
        }
        if (after != null && movement.appliedDelta() != null && !after.equals(movement.appliedDelta())) {
            add(statuses, findings, VndReconciliationStatus.CHAIN_BROKEN,
                    ownerId, clean(movement.operationId()), "BASELINE_DELTA_AFTER_MISMATCH");
        }
        if (after != null && movement.appliedDelta() == null && amount != after) {
            add(statuses, findings, VndReconciliationStatus.CHAIN_BROKEN,
                    ownerId, clean(movement.operationId()), "BASELINE_AMOUNT_AFTER_MISMATCH");
        }
    }

    private static void validateMovement(long ownerId,
            VndReconciliationDataSource.LedgerMovement movement,
            long delta,
            Set<VndReconciliationStatus> statuses,
            List<VndReconciliationFinding> findings) {
        String type = clean(movement.transactionType());
        if (movement.requestedAmount() != null && movement.requestedAmount() <= 0L) {
            add(statuses, findings, VndReconciliationStatus.CHAIN_BROKEN,
                    ownerId, clean(movement.operationId()), "NON_POSITIVE_REQUESTED_AMOUNT");
        }
        if (movement.amount() <= 0L) {
            add(statuses, findings, VndReconciliationStatus.CHAIN_BROKEN,
                    ownerId, clean(movement.operationId()), "NON_POSITIVE_LEDGER_AMOUNT");
        }
        if (type.startsWith("DEBIT_") && delta >= 0L || type.startsWith("CREDIT_") && delta <= 0L) {
            add(statuses, findings, VndReconciliationStatus.CHAIN_BROKEN,
                    ownerId, clean(movement.operationId()), "SIGNED_DELTA_DIRECTION_INVALID");
        }
        if (type.startsWith("DEBIT_") && movement.requestedAmount() != null
                && delta != -movement.requestedAmount()) {
            add(statuses, findings, VndReconciliationStatus.CHAIN_BROKEN,
                    ownerId, clean(movement.operationId()), "DEBIT_DELTA_REQUESTED_AMOUNT_MISMATCH");
        }
        if (type.startsWith("CREDIT_") && movement.requestedAmount() != null
                && (delta > movement.requestedAmount())) {
            add(statuses, findings, VndReconciliationStatus.CHAIN_BROKEN,
                    ownerId, clean(movement.operationId()), "CREDIT_APPLIED_EXCEEDS_REQUESTED_AMOUNT");
        }
        if (movement.balanceBefore() != null && movement.balanceAfter() != null) {
            try {
                if (Math.addExact(movement.balanceBefore(), delta) != movement.balanceAfter()) {
                    add(statuses, findings, VndReconciliationStatus.CHAIN_BROKEN,
                            ownerId, clean(movement.operationId()), "LEG_BEFORE_DELTA_AFTER_MISMATCH");
                }
            } catch (ArithmeticException overflow) {
                add(statuses, findings, VndReconciliationStatus.UNAVAILABLE,
                        ownerId, clean(movement.operationId()), "LEG_ARITHMETIC_OVERFLOW");
            }
        } else {
            add(statuses, findings, VndReconciliationStatus.INCOMPLETE_COVERAGE,
                    ownerId, clean(movement.operationId()), "LEG_BALANCE_EVIDENCE_MISSING");
        }
    }

    private static void validateOutbox(long ownerId,
            VndReconciliationDataSource.LedgerMovement movement,
            VndReconciliationDataSource.OutboxMovement outbox,
            String transactionType,
            Set<VndReconciliationStatus> statuses,
            List<VndReconciliationFinding> findings) {
        String operationId = clean(movement.operationId());
        if (!operationId.equals(clean(outbox.operationId()))
                || !operationId.equals(clean(outbox.businessKey()))
                || outbox.ownerId() != ownerId
                || outbox.amount() != positiveRequestedAmount(movement)
                || !compatibleProduct(transactionType, outbox.productType())) {
            add(statuses, findings, VndReconciliationStatus.ORPHAN_OUTBOX,
                    ownerId, operationId, "OUTBOX_LEDGER_FIELDS_MISMATCH");
        }
        String ledgerFingerprint = clean(movement.payloadFingerprint());
        String outboxFingerprint = clean(outbox.payloadFingerprint());
        if (!isSha256(ledgerFingerprint) || !isSha256(outboxFingerprint)) {
            add(statuses, findings, VndReconciliationStatus.ORPHAN_OUTBOX,
                    ownerId, operationId, "DEBIT_FINGERPRINT_INVALID_OR_MISSING");
        } else if (!ledgerFingerprint.equalsIgnoreCase(outboxFingerprint)) {
            add(statuses, findings, VndReconciliationStatus.ORPHAN_OUTBOX,
                    ownerId, operationId, "PAYLOAD_FINGERPRINT_MISMATCH");
        }
    }

    private static long positiveRequestedAmount(VndReconciliationDataSource.LedgerMovement movement) {
        return movement.requestedAmount() != null ? movement.requestedAmount() : movement.amount();
    }

    private static boolean compatibleProduct(String transactionType, String productType) {
        String product = clean(productType);
        return switch (transactionType) {
            case "DEBIT_GOLD_CONVERT" -> "TRADE_GOLD".equals(product);
            case "DEBIT_GEM_CONVERT" -> "TRADE_GEM".equals(product);
            case "DEBIT_VIP" -> product.startsWith("VIP_");
            default -> false;
        };
    }

    private static boolean isSec07Baseline(VndReconciliationDataSource.LedgerMovement movement) {
        return "BASELINE_OPENING".equals(clean(movement.transactionType()))
                && "SEC-07-BASELINE".equals(clean(movement.schemaVersion()))
                && ACCOUNT.equalsIgnoreCase(clean(movement.ownerType()))
                && VND.equalsIgnoreCase(clean(movement.currency()))
                && movement.durableEvidence();
    }

    private static boolean isSha256(String value) {
        return value != null && value.trim().matches("[0-9a-fA-F]{64}");
    }

    /**
     * Bounded-memory evaluator for JDBC page readers. Ledger pages must be
     * delivered in durable sequence order; the matching outbox row is supplied
     * by the page query's left join. It intentionally keeps no movement list.
     */
    public static final class StreamingAccumulator {
        private final long ownerId;
        private final long persistedBalance;
        private final long nowMillis;
        private final long stalePendingAfterMillis;
        private final LinkedHashSet<VndReconciliationStatus> statuses = new LinkedHashSet<>();
        private final List<VndReconciliationFinding> findings = new ArrayList<>();
        private VndReconciliationDataSource.LedgerMovement baseline;
        private Long expectedBalance;
        private Long previousAfter;
        private long previousSequence = Long.MIN_VALUE;
        private long movementsBeforeBaseline;
        private String firstMovementBeforeBaseline = "";
        private boolean ledgerComplete = true;

        public StreamingAccumulator(long ownerId, long persistedBalance,
                long nowMillis, long stalePendingAfterMillis) {
            this.ownerId = ownerId;
            this.persistedBalance = persistedBalance;
            this.nowMillis = nowMillis;
            this.stalePendingAfterMillis = stalePendingAfterMillis;
        }

        public void acceptLedger(VndReconciliationDataSource.LedgerMovement movement,
                VndReconciliationDataSource.OutboxMovement matchingOutbox) {
            if (movement == null) {
                add(statuses, findings, VndReconciliationStatus.UNAVAILABLE,
                        ownerId, "", "LEDGER_PAGE_ROW_NULL");
                return;
            }
            String operationId = clean(movement.operationId());
            String type = clean(movement.transactionType());
            if (previousSequence != Long.MIN_VALUE && movement.durableSequence() <= previousSequence) {
                add(statuses, findings, VndReconciliationStatus.CHAIN_BROKEN,
                        ownerId, operationId, movement.durableSequence() == previousSequence
                                ? "DUPLICATE_DURABLE_SEQUENCE" : "NON_INCREASING_DURABLE_ORDER");
            }
            previousSequence = Math.max(previousSequence, movement.durableSequence());
            if (!KNOWN_TYPES.contains(type)) {
                add(statuses, findings, VndReconciliationStatus.UNKNOWN_TRANSACTION_TYPE,
                        ownerId, operationId, "UNKNOWN_LEDGER_TYPE");
                movementsBeforeBaseline++;
                if (firstMovementBeforeBaseline.isEmpty()) firstMovementBeforeBaseline = operationId;
                return;
            }
            if (movement.ownerId() != ownerId
                    || !ACCOUNT.equalsIgnoreCase(clean(movement.ownerType()))
                    || !VND.equalsIgnoreCase(clean(movement.currency()))) {
                add(statuses, findings, VndReconciliationStatus.ORPHAN_LEDGER,
                        ownerId, operationId, "LEDGER_OWNER_OR_CURRENCY_MISMATCH");
            }
            if (operationId.isEmpty() || clean(movement.businessKey()).isEmpty()) {
                add(statuses, findings, VndReconciliationStatus.INCOMPLETE_COVERAGE,
                        ownerId, operationId, "MISSING_OPERATION_OR_BUSINESS_KEY");
            }
            if (movement.legIndex() != 0) {
                add(statuses, findings, VndReconciliationStatus.ORPHAN_LEDGER,
                        ownerId, operationId, "UNEXPECTED_VND_LEG_INDEX");
            }
            if (movement.durableSequence() <= 0L || !movement.durableEvidence()) {
                add(statuses, findings, VndReconciliationStatus.INCOMPLETE_COVERAGE,
                        ownerId, operationId, "LEGACY_LEDGER_DURABLE_EVIDENCE");
            }
            if (clean(movement.source()).isEmpty() || movement.committedAtMillis() <= 0L) {
                add(statuses, findings, VndReconciliationStatus.INCOMPLETE_COVERAGE,
                        ownerId, operationId, "LEDGER_COMMIT_METADATA_MISSING");
            }

            if ("BASELINE_OPENING".equals(type)) {
                if (isSec07Baseline(movement)) {
                    if (baseline == null) {
                        baseline = movement;
                        expectedBalance = baselineValue(movement);
                        previousAfter = movement.balanceAfter();
                    } else {
                        add(statuses, findings, VndReconciliationStatus.DUPLICATE_BASELINE,
                                ownerId, operationId, "MORE_THAN_ONE_OPENING_BASELINE");
                        validateBaseline(ownerId, movement, statuses, findings);
                    }
                } else {
                    add(statuses, findings, VndReconciliationStatus.INCOMPLETE_COVERAGE,
                            ownerId, operationId, "LEGACY_BASELINE_OUTSIDE_SEC07_COVERAGE");
                    movementsBeforeBaseline++;
                    if (firstMovementBeforeBaseline.isEmpty()) firstMovementBeforeBaseline = operationId;
                }
                if (baseline == movement) {
                    validateBaseline(ownerId, movement, statuses, findings);
                }
                return;
            }

            Long delta = movement.appliedDelta();
            if (delta == null) {
                long amount = movement.requestedAmount() != null
                        ? movement.requestedAmount() : movement.amount();
                if (amount <= 0L) {
                    add(statuses, findings, VndReconciliationStatus.CHAIN_BROKEN,
                            ownerId, operationId, "NON_POSITIVE_MOVEMENT_AMOUNT");
                } else {
                    delta = type.startsWith("DEBIT_") ? -amount : amount;
                    add(statuses, findings, VndReconciliationStatus.INCOMPLETE_COVERAGE,
                            ownerId, operationId, "LEGACY_SIGNED_DELTA_DERIVED");
                }
            }
            if (delta != null) {
                validateMovement(ownerId, movement, delta, statuses, findings);
            }
            if (type.startsWith("DEBIT_")) {
                if (matchingOutbox == null) {
                    add(statuses, findings, VndReconciliationStatus.ORPHAN_LEDGER,
                            ownerId, operationId, "DEBIT_MISSING_OUTBOX");
                } else {
                    validateOutbox(ownerId, movement, matchingOutbox, type, statuses, findings);
                }
            }

            if (baseline == null || movement.durableSequence() < baseline.durableSequence()) {
                movementsBeforeBaseline++;
                if (firstMovementBeforeBaseline.isEmpty()) firstMovementBeforeBaseline = operationId;
                return;
            }
            if (expectedBalance != null && delta != null) {
                try {
                    expectedBalance = Math.addExact(expectedBalance, delta);
                } catch (ArithmeticException overflow) {
                    add(statuses, findings, VndReconciliationStatus.UNAVAILABLE,
                            ownerId, operationId, "RECONCILIATION_SUM_OVERFLOW");
                    expectedBalance = null;
                }
            }
            if (previousAfter != null && movement.balanceBefore() != null
                    && !previousAfter.equals(movement.balanceBefore())) {
                add(statuses, findings, VndReconciliationStatus.CHAIN_BROKEN,
                        ownerId, operationId, "BEFORE_AFTER_CHAIN_GAP");
            }
            if (movement.balanceAfter() != null) previousAfter = movement.balanceAfter();
        }

        public void acceptOutbox(VndReconciliationDataSource.OutboxMovement outbox,
                boolean hasMatchingLedger) {
            if (outbox == null) {
                add(statuses, findings, VndReconciliationStatus.UNAVAILABLE,
                        ownerId, "", "OUTBOX_PAGE_ROW_NULL");
                return;
            }
            String operationId = clean(outbox.operationId());
            String status = clean(outbox.status()).toUpperCase(java.util.Locale.ROOT);
            if (operationId.isEmpty() || outbox.ownerId() != ownerId
                    || !ACCOUNT.equalsIgnoreCase(clean(outbox.ownerType()))
                    || !VND.equalsIgnoreCase(clean(outbox.currency()))) {
                add(statuses, findings, VndReconciliationStatus.ORPHAN_OUTBOX,
                        ownerId, operationId, "OUTBOX_OWNER_OR_CURRENCY_MISMATCH");
            }
            if (!hasMatchingLedger) {
                add(statuses, findings, VndReconciliationStatus.ORPHAN_OUTBOX,
                        ownerId, operationId, "OUTBOX_MISSING_LEDGER");
            }
            if (!KNOWN_OUTBOX_STATUSES.contains(status)) {
                add(statuses, findings, VndReconciliationStatus.UNKNOWN_TRANSACTION_TYPE,
                        ownerId, operationId, "UNKNOWN_OUTBOX_STATUS");
            }
            if ("QUARANTINED".equals(status)) {
                add(statuses, findings, VndReconciliationStatus.DELIVERY_QUARANTINED,
                        ownerId, operationId, "QUARANTINED_REQUIRES_MANUAL_INTERVENTION");
            }
            if ("FAILED_RETRYABLE".equals(status)) {
                add(statuses, findings, VndReconciliationStatus.DELIVERY_RETRYABLE,
                        ownerId, operationId, "FAILED_RETRYABLE_NOT_DELIVERED");
                if (outbox.createdAtMillis() > 0L
                        && nowMillis - outbox.createdAtMillis() > stalePendingAfterMillis) {
                    add(statuses, findings, VndReconciliationStatus.STALE_PENDING,
                            ownerId, operationId, "FAILED_RETRYABLE_EXPIRED");
                }
            }
            if (!isSha256(outbox.payloadFingerprint())) {
                add(statuses, findings, VndReconciliationStatus.ORPHAN_OUTBOX,
                        ownerId, operationId, "OUTBOX_FINGERPRINT_INVALID_OR_MISSING");
            }
            if ("DELIVERED".equals(status) && outbox.deliveredAtMillis() <= 0L) {
                add(statuses, findings, VndReconciliationStatus.ORPHAN_OUTBOX,
                        ownerId, operationId, "DELIVERED_TIMESTAMP_MISSING");
            }
            if (!"DELIVERED".equals(status) && outbox.deliveredAtMillis() > 0L) {
                add(statuses, findings, VndReconciliationStatus.ORPHAN_OUTBOX,
                        ownerId, operationId, "NON_DELIVERED_HAS_DELIVERED_TIMESTAMP");
            }
            if ("PENDING_DELIVERY".equals(status) && outbox.createdAtMillis() <= 0L) {
                add(statuses, findings, VndReconciliationStatus.INCOMPLETE_COVERAGE,
                        ownerId, operationId, "PENDING_TIMESTAMP_MISSING");
            }
            if ("PENDING_DELIVERY".equals(status) && outbox.createdAtMillis() > 0L
                    && nowMillis - outbox.createdAtMillis() > stalePendingAfterMillis) {
                add(statuses, findings, VndReconciliationStatus.STALE_PENDING,
                        ownerId, operationId, "PENDING_DELIVERY_EXPIRED");
            }
        }

        public void markDuplicateLedgerOperation() {
            add(statuses, findings, VndReconciliationStatus.ORPHAN_LEDGER,
                    ownerId, "", "DUPLICATE_OPERATION_LEG");
        }

        public void markDuplicateOutboxOperation() {
            add(statuses, findings, VndReconciliationStatus.ORPHAN_OUTBOX,
                    ownerId, "", "DUPLICATE_OUTBOX_OPERATION");
        }

        public void markWorkLimitReached() {
            markWorkLimitReached(false);
        }

        /** Outbox exhaustion does not invalidate an already complete balance sum. */
        public void markWorkLimitReached(boolean ledgerWasFullyRead) {
            ledgerComplete = ledgerWasFullyRead;
            add(statuses, findings, VndReconciliationStatus.INCOMPLETE_COVERAGE,
                    ownerId, "", "OWNER_WORK_LIMIT_REACHED");
        }

        public OwnerReconciliation finish() {
            if (ledgerComplete && baseline == null) {
                add(statuses, findings, VndReconciliationStatus.MISSING_BASELINE,
                        ownerId, "", "NO_OPENING_BASELINE");
            }
            if (movementsBeforeBaseline > 0L && baseline != null) {
                add(statuses, findings, VndReconciliationStatus.INCOMPLETE_COVERAGE,
                        ownerId, firstMovementBeforeBaseline, "PRE_CUTOVER_MOVEMENT_OUTSIDE_COVERAGE");
            }
            if (!ledgerComplete) expectedBalance = null;
            if (expectedBalance != null && persistedBalance != expectedBalance) {
                add(statuses, findings, VndReconciliationStatus.BALANCE_DRIFT,
                        ownerId, "", "PERSISTED_BALANCE_DIFFERS_FROM_EXPECTED");
            }
            if (statuses.isEmpty()) statuses.add(VndReconciliationStatus.OK);
            return new OwnerReconciliation(ownerId, persistedBalance, expectedBalance,
                    orderedStatuses(statuses), findings);
        }

        private Long baselineValue(VndReconciliationDataSource.LedgerMovement movement) {
            Long value = movement.balanceAfter();
            if (value == null) value = movement.appliedDelta();
            if (value == null) {
                value = movement.amount();
                add(statuses, findings, VndReconciliationStatus.INCOMPLETE_COVERAGE,
                        ownerId, clean(movement.operationId()), "LEGACY_BASELINE_VALUE_DERIVED");
            }
            return value;
        }
    }

    private static Long signedDelta(VndReconciliationDataSource.LedgerMovement movement) {
        if (movement.appliedDelta() != null) {
            return movement.appliedDelta();
        }
        long amount = movement.requestedAmount() != null ? movement.requestedAmount() : movement.amount();
        if (amount <= 0L) {
            return null;
        }
        return clean(movement.transactionType()).startsWith("DEBIT_") ? -amount : amount;
    }

    private static void add(Set<VndReconciliationStatus> statuses,
            List<VndReconciliationFinding> findings,
            VndReconciliationStatus status, long ownerId, String operationId, String detailCode) {
        statuses.add(status);
        if (findings.size() < MAX_FINDINGS_PER_OWNER) {
            findings.add(new VndReconciliationFinding(status, ownerId, VND, operationId, detailCode));
        }
    }

    private static List<VndReconciliationStatus> orderedStatuses(Set<VndReconciliationStatus> statuses) {
        return statuses.stream().sorted(Comparator.comparingInt(VndReconciliationStatus::severity).reversed()).toList();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public record OwnerReconciliation(
            long ownerId,
            long persistedBalance,
            Long expectedBalance,
            List<VndReconciliationStatus> statuses,
            List<VndReconciliationFinding> findings) {

        public OwnerReconciliation {
            statuses = statuses == null || statuses.isEmpty()
                    ? List.of(VndReconciliationStatus.UNAVAILABLE) : List.copyOf(statuses);
            findings = findings == null ? List.of() : List.copyOf(findings);
        }

        public boolean isOk() {
            return statuses.size() == 1 && statuses.get(0) == VndReconciliationStatus.OK;
        }

        public VndReconciliationStatus primaryStatus() {
            return statuses.stream().max(Comparator.comparingInt(VndReconciliationStatus::severity))
                    .orElse(VndReconciliationStatus.UNAVAILABLE);
        }
    }
}
