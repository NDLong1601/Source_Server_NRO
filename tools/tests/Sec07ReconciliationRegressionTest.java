package tools.tests;

import java.util.List;
import java.util.Map;
import nro.models.ledger.VndReconciliationCalculator;
import nro.models.ledger.VndReconciliationDataSource;
import nro.models.ledger.VndReconciliationFinding;
import nro.models.ledger.VndReconciliationRequest;
import nro.models.ledger.VndReconciliationRunReport;
import nro.models.ledger.VndReconciliationStatus;

/**
 * SEC-07 pure-domain regression tests. JDBC atomicity and lock behavior belong
 * to an approved MySQL integration fixture and are intentionally not faked here.
 */
public final class Sec07ReconciliationRegressionTest {

    private static int assertions;

    private Sec07ReconciliationRegressionTest() {
    }

    public static void main(String[] args) {
        testZeroBaselineAndSignedChain();
        testMissingAndDuplicateBaseline();
        testUnknownTypeAndChainGap();
        testOrphanAndStaleOutbox();
        testLegacyEvidenceAndOverflowAreNotOk();
        testOneOwnerFailureDoesNotHideAnother();
        testStreamingAccumulatorAcrossPageBoundary();
        testIncompleteLedgerDoesNotInventDrift();
        testReportExitCodes();
        testReportBoundaryAndFormulaInjection();
        System.out.println("SEC-07 RECONCILIATION TESTS PASSED; assertions=" + assertions);
    }

    private static void testZeroBaselineAndSignedChain() {
        String fingerprint = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        VndReconciliationDataSource.OwnerSnapshot owner = owner(42L, 45L,
                List.of(
                        movement(1, 42, "baseline-42", "BASELINE_OPENING", 0L, 0L, 0L, 0L, 1L, true, "SEC-07-BASELINE"),
                        movement(2, 42, "op-1", "CREDIT_ADMIN", 100L, 100L, 0L, 100L, 2L, true, "SEC-07-V1"),
                        movement(3, 42, "op-2", "DEBIT_GOLD_CONVERT", 55L, -55L, 100L, 45L, 3L, true, "SEC-07-V1")),
                List.of(new VndReconciliationDataSource.OutboxMovement(
                        7, 42, "ACCOUNT", "VND", "op-2", "op-2", "TRADE_GOLD", 55,
                        fingerprint, "DELIVERED", 0, 10, 20)), true, null);
        VndReconciliationCalculator.OwnerReconciliation result =
                VndReconciliationCalculator.evaluate(owner, 1_000, 100);
        check(result.isOk(), "zero baseline plus signed credit/debit chain should be OK: " + result.statuses());
        equalsLong(45, result.expectedBalance(), "expected signed balance");
    }

    private static void testMissingAndDuplicateBaseline() {
        VndReconciliationCalculator.OwnerReconciliation missing = evaluate(owner(1, 0, List.of(), List.of(), true, null));
        has(missing, VndReconciliationStatus.MISSING_BASELINE, "missing baseline");

        VndReconciliationDataSource.LedgerMovement first = movement(1, 2, "base-1", "BASELINE_OPENING", 10L, 10L, 0L, 10L, 0L, true, "SEC-07-BASELINE");
        VndReconciliationDataSource.LedgerMovement second = movement(2, 2, "base-2", "BASELINE_OPENING", 10L, 10L, 0L, 10L, 0L, true, "SEC-07-BASELINE");
        VndReconciliationCalculator.OwnerReconciliation duplicate = evaluate(owner(2, 10, List.of(first, second), List.of(), true, null));
        has(duplicate, VndReconciliationStatus.DUPLICATE_BASELINE, "duplicate baseline");
    }

    private static void testUnknownTypeAndChainGap() {
        VndReconciliationDataSource.LedgerMovement base = movement(1, 3, "base-3", "BASELINE_OPENING", 10L, 10L, 0L, 10L, 0L, true, "SEC-07-BASELINE");
        VndReconciliationDataSource.LedgerMovement unknown = movement(2, 3, "unknown", "MYSTERY", 5L, 5L, 10L, 15L, 1L, true, "SEC-07-V1");
        VndReconciliationDataSource.LedgerMovement gap = movement(3, 3, "gap", "CREDIT_ADMIN", 2L, 2L, 99L, 101L, 2L, true, "SEC-07-V1");
        VndReconciliationCalculator.OwnerReconciliation result = evaluate(owner(3, 12,
                List.of(base, unknown, gap), List.of(), true, null));
        has(result, VndReconciliationStatus.UNKNOWN_TRANSACTION_TYPE, "unknown transaction type");
        has(result, VndReconciliationStatus.CHAIN_BROKEN, "before/after chain gap");
    }

    private static void testOrphanAndStaleOutbox() {
        VndReconciliationDataSource.LedgerMovement base = movement(1, 4, "base-4", "BASELINE_OPENING", 20L, 20L, 0L, 20L, 1L, true, "SEC-07-BASELINE");
        VndReconciliationDataSource.LedgerMovement freshDebit = movement(2, 4, "fresh-debit-4",
                "DEBIT_GOLD_CONVERT", 10L, -10L, 20L, 10L, 2L, true, "SEC-07-V1");
        VndReconciliationDataSource.OutboxMovement freshPending = new VndReconciliationDataSource.OutboxMovement(
                7, 4, "ACCOUNT", "VND", "fresh-debit-4", "fresh-debit-4", "TRADE_GOLD", 10,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "PENDING_DELIVERY", 0, 950, 0);
        VndReconciliationCalculator.OwnerReconciliation freshPendingResult = evaluate(owner(4, 10,
                List.of(base, freshDebit), List.of(freshPending), true, null));
        check(freshPendingResult.isOk(), "fresh pending delivery remains within the allowed window");
        check(!freshPendingResult.statuses().contains(VndReconciliationStatus.STALE_PENDING),
                "fresh pending delivery is not incorrectly marked stale");

        VndReconciliationDataSource.OutboxMovement orphan = new VndReconciliationDataSource.OutboxMovement(
                8, 4, "ACCOUNT", "VND", "missing", "missing", "TRADE_GOLD", 10,
                "", "PENDING_DELIVERY", 0, 1, 0);
        VndReconciliationCalculator.OwnerReconciliation result = evaluate(owner(4, 20,
                List.of(base), List.of(orphan), true, null));
        has(result, VndReconciliationStatus.ORPHAN_OUTBOX, "outbox without ledger");
        has(result, VndReconciliationStatus.STALE_PENDING, "old pending outbox");

        VndReconciliationDataSource.LedgerMovement debit = movement(2, 4, "debit-4",
                "DEBIT_GOLD_CONVERT", 10L, -10L, 0L, 0L, 2L, true, "SEC-07-V1");
        VndReconciliationDataSource.OutboxMovement quarantined = new VndReconciliationDataSource.OutboxMovement(
                9, 4, "ACCOUNT", "VND", "debit-4", "debit-4", "TRADE_GOLD", 10,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "QUARANTINED", 9, 1, 0);
        VndReconciliationCalculator.OwnerReconciliation quarantinedResult = evaluate(owner(4, 10,
                List.of(base, debit), List.of(quarantined), true, null));
        has(quarantinedResult, VndReconciliationStatus.DELIVERY_QUARANTINED,
                "quarantined delivery requires intervention");
        check(!quarantinedResult.isOk(), "quarantined delivery cannot be OK");

        VndReconciliationDataSource.LedgerMovement retryDebit = movement(3, 4, "debit-5",
                "DEBIT_GOLD_CONVERT", 10L, -10L, 0L, 0L, 3L, true, "SEC-07-V1");
        VndReconciliationDataSource.OutboxMovement retryable = new VndReconciliationDataSource.OutboxMovement(
                10, 4, "ACCOUNT", "VND", "debit-5", "debit-5", "TRADE_GOLD", 10,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "FAILED_RETRYABLE", 9, 1, 0);
        VndReconciliationCalculator.OwnerReconciliation retryResult = evaluate(owner(4, 10,
                List.of(base, retryDebit), List.of(retryable), true, null));
        has(retryResult, VndReconciliationStatus.DELIVERY_RETRYABLE,
                "retryable delivery is not silently healthy");
        has(retryResult, VndReconciliationStatus.STALE_PENDING,
                "expired retryable delivery is stale");

        String[] invalidFingerprints = {null, "", " ", "a".repeat(63), "g".repeat(64)};
        for (String invalidFingerprint : invalidFingerprints) {
            VndReconciliationDataSource.OutboxMovement invalidOutbox = new VndReconciliationDataSource.OutboxMovement(
                    11, 4, "ACCOUNT", "VND", "debit-4", "debit-4", "TRADE_GOLD", 10,
                    invalidFingerprint, "DELIVERED", 0, 10, 20);
            VndReconciliationCalculator.OwnerReconciliation fingerprintResult = evaluate(owner(4, 10,
                    List.of(base, debit), List.of(invalidOutbox), true, null));
            has(fingerprintResult, VndReconciliationStatus.ORPHAN_OUTBOX,
                    "invalid debit fingerprint is not accepted: " + invalidFingerprint);
        }
        VndReconciliationDataSource.OutboxMovement missingDeliveryTimestamp = new VndReconciliationDataSource.OutboxMovement(
                12, 4, "ACCOUNT", "VND", "debit-4", "debit-4", "TRADE_GOLD", 10,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "DELIVERED", 0, 10, 0);
        has(evaluate(owner(4, 10, List.of(base, debit), List.of(missingDeliveryTimestamp), true, null)),
                VndReconciliationStatus.ORPHAN_OUTBOX,
                "delivered outbox without delivery timestamp is not accepted");
    }

    private static void testLegacyEvidenceAndOverflowAreNotOk() {
        VndReconciliationDataSource.LedgerMovement legacyBase = movement(1, 5, "base-5", "BASELINE_OPENING", 20L, 20L, 0L, 20L, 0L, false, "SEC-05-BASELINE");
        VndReconciliationDataSource.LedgerMovement legacyCredit = movement(2, 5, "legacy", "CREDIT_ADMIN", 5L, null, 20L, 25L, 1L, false, "SEC-05-LEGACY");
        VndReconciliationCalculator.OwnerReconciliation legacy = evaluate(owner(5, 25,
                List.of(legacyBase, legacyCredit), List.of(), true, null));
        has(legacy, VndReconciliationStatus.INCOMPLETE_COVERAGE, "legacy evidence is not OK");

        VndReconciliationDataSource.LedgerMovement maxBase = movement(1, 6, "base-6", "BASELINE_OPENING",
                Long.MAX_VALUE, Long.MAX_VALUE, 0, Long.MAX_VALUE, 0, true, "SEC-07-BASELINE");
        VndReconciliationDataSource.LedgerMovement overflow = movement(2, 6, "overflow", "CREDIT_ADMIN",
                1L, 1L, Long.MAX_VALUE, null, 1L, true, "SEC-07-V1");
        VndReconciliationCalculator.OwnerReconciliation result = evaluate(owner(6, Long.MAX_VALUE,
                List.of(maxBase, overflow), List.of(), true, null));
        has(result, VndReconciliationStatus.UNAVAILABLE, "sum overflow is unavailable");
    }

    private static void testOneOwnerFailureDoesNotHideAnother() {
        VndReconciliationCalculator.OwnerReconciliation failed = evaluate(
                VndReconciliationDataSource.OwnerSnapshot.unavailable(7, "OWNER_QUERY_FAILED"));
        VndReconciliationDataSource.OwnerSnapshot goodOwner = owner(8, 0,
                List.of(movement(1, 8, "base-8", "BASELINE_OPENING", 0L, 0L, 0L, 0L, 1L, true, "SEC-07-BASELINE")),
                List.of(), true, null);
        VndReconciliationCalculator.OwnerReconciliation good = evaluate(goodOwner);
        has(failed, VndReconciliationStatus.UNAVAILABLE, "failed owner is retained");
        check(good.isOk(), "healthy owner remains independently auditable");
    }

    private static void testStreamingAccumulatorAcrossPageBoundary() {
        String fingerprint = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        VndReconciliationCalculator.StreamingAccumulator accumulator =
                new VndReconciliationCalculator.StreamingAccumulator(20L, 9_991L, 1_000L, 100L);
        accumulator.acceptLedger(movement(1, 20, "base-20", "BASELINE_OPENING",
                0L, 0L, 0L, 0L, 1L, true, "SEC-07-BASELINE"), null);
        for (int i = 1; i <= 9_999; i++) {
            accumulator.acceptLedger(movement(i + 1L, 20, "credit-" + i, "CREDIT_ADMIN",
                    1L, 1L, i - 1L, (long) i, i + 1L, true, "SEC-07-V1"), null);
        }
        VndReconciliationDataSource.LedgerMovement debit = movement(10_001L, 20, "debit-20",
                "DEBIT_GOLD_CONVERT", 10L, -10L, 9_999L, 9_989L, 10_001L, true, "SEC-07-V1");
        VndReconciliationDataSource.OutboxMovement outbox = new VndReconciliationDataSource.OutboxMovement(
                20L, 20L, "ACCOUNT", "VND", "debit-20", "debit-20", "TRADE_GOLD", 10L,
                fingerprint, "DELIVERED", 0, 10L, 20L);
        accumulator.acceptLedger(debit, outbox);
        accumulator.acceptLedger(movement(10_002L, 20, "credit-10000", "CREDIT_ADMIN",
                1L, 1L, 9_989L, 9_990L, 10_002L, true, "SEC-07-V1"), null);
        accumulator.acceptLedger(movement(10_003L, 20, "credit-10001", "CREDIT_ADMIN",
                1L, 1L, 9_990L, 9_991L, 10_003L, true, "SEC-07-V1"), null);
        accumulator.acceptOutbox(outbox, true);
        check(accumulator.finish().isOk(),
                "streaming accumulator preserves chain and outbox matching across a >10k page boundary");

        VndReconciliationCalculator.StreamingAccumulator outboxAccumulator =
                new VndReconciliationCalculator.StreamingAccumulator(21L, 0L, 1_000L, 100L);
        outboxAccumulator.acceptLedger(movement(1L, 21, "base-21", "BASELINE_OPENING",
                0L, 0L, 0L, 0L, 1L, true, "SEC-07-BASELINE"), null);
        for (int i = 1; i <= 10_001; i++) {
            outboxAccumulator.acceptOutbox(new VndReconciliationDataSource.OutboxMovement(
                    i, 21L, "ACCOUNT", "VND", "outbox-" + i, "outbox-" + i,
                    "TRADE_GOLD", 10L,
                    fingerprint, "DELIVERED", 0, 10L, 20L), true);
        }
        check(outboxAccumulator.finish().isOk(),
                "streaming accumulator processes more than 10k outbox rows without an implicit truncation");
    }

    private static void testIncompleteLedgerDoesNotInventDrift() {
        var partial = new VndReconciliationCalculator.StreamingAccumulator(1, 90, 1000, 100);
        partial.acceptLedger(movement(1, 1, "base", "BASELINE_OPENING",
                100L, 100L, 0L, 100L, 1L, true, "SEC-07-BASELINE"), null);
        partial.markWorkLimitReached();
        var result = partial.finish();
        has(result, VndReconciliationStatus.INCOMPLETE_COVERAGE, "partial ledger stays incomplete");
        check(result.expectedBalance() == null, "partial sum is not a final expected balance");
        check(!result.statuses().contains(VndReconciliationStatus.BALANCE_DRIFT), "unread debit must not invent drift");
        var unread = new VndReconciliationCalculator.StreamingAccumulator(1, 90, 1000, 100);
        unread.markWorkLimitReached();
        check(!unread.finish().statuses().contains(VndReconciliationStatus.MISSING_BASELINE),
                "unread baseline must not be declared missing");
        var outboxOnly = new VndReconciliationCalculator.StreamingAccumulator(1, 90, 1000, 100);
        outboxOnly.acceptLedger(movement(1, 1, "base", "BASELINE_OPENING",
                100L, 100L, 0L, 100L, 1L, true, "SEC-07-BASELINE"), null);
        outboxOnly.markWorkLimitReached(true);
        has(outboxOnly.finish(), VndReconciliationStatus.BALANCE_DRIFT,
                "complete ledger still proves drift when only outbox scan is incomplete");
    }

    private static void testReportExitCodes() {
        VndReconciliationCalculator.OwnerReconciliation cleanOwner = evaluate(owner(10, 0,
                List.of(movement(1, 10, "base-10", "BASELINE_OPENING", 0L, 0L, 0L, 0L, 1L, true,
                        "SEC-07-BASELINE")), List.of(), true, null));
        VndReconciliationRunReport clean = new VndReconciliationRunReport(
                "run-clean", 1L, 2L, 1L, 1L, "REPEATABLE_READ_OWNER_SNAPSHOT", 0L, 10L,
                false, true, List.of(cleanOwner), Map.of(VndReconciliationStatus.OK, 1), "");
        equalsInt(0, clean.exitCode(), "clean report exit code");
        check(!VndReconciliationRunReport.unavailable(VndReconciliationRequest.defaults(1), 1L, "x")
                .isSuccessful(), "unavailable report is not successful");
        equalsInt(3, VndReconciliationRunReport.unavailable(VndReconciliationRequest.defaults(1), 1L, "x").exitCode(),
                "unavailable report exit code");
        VndReconciliationRunReport mixed = new VndReconciliationRunReport(
                "run-mixed", 1L, 2L, 1L, 1L, "UNAVAILABLE", 0L, 0L, false, false,
                List.of(
                        new VndReconciliationCalculator.OwnerReconciliation(11L, 1L, 0L,
                                List.of(VndReconciliationStatus.BALANCE_DRIFT), List.of()),
                        new VndReconciliationCalculator.OwnerReconciliation(12L, 1L, null,
                                List.of(VndReconciliationStatus.UNAVAILABLE), List.of())),
                Map.of(VndReconciliationStatus.BALANCE_DRIFT, 1,
                        VndReconciliationStatus.UNAVAILABLE, 1), "");
        equalsInt(3, mixed.exitCode(), "mixed unavailable report exit code");
        equalsInt(2, new VndReconciliationRunReport(
                "run-finding", 1L, 2L, 1L, 1L, "REPEATABLE_READ_OWNER_SNAPSHOT", 0L, 0L,
                false, true, List.of(new VndReconciliationCalculator.OwnerReconciliation(13L, 1L, 0L,
                        List.of(VndReconciliationStatus.BALANCE_DRIFT), List.of())),
                Map.of(VndReconciliationStatus.BALANCE_DRIFT, 1), "").exitCode(),
                "finding report exit code");
        VndReconciliationRunReport unresolved = new VndReconciliationRunReport(
                "run-unresolved", 1L, 2L, 1L, 1L, "REPEATABLE_READ_OWNER_SNAPSHOT", 0L, 10L,
                false, true, List.of(cleanOwner), Map.of(VndReconciliationStatus.OK, 1), "",
                List.of(99L));
        check(!unresolved.isSuccessful(), "unresolved owner prevents a clean report");
        equalsInt(2, unresolved.exitCode(), "unresolved owner report exit code");
        check(unresolved.overallStatus() == VndReconciliationStatus.INCOMPLETE_COVERAGE,
                "unresolved owner is reflected in the report status");
        check(unresolved.toJson().contains("\"unresolvedOwnerIds\":[99]"),
                "JSON exposes unresolved owners");
        check(unresolved.toCsv().contains("\"[99]\""),
                "CSV exposes unresolved owners");
    }

    private static void testReportBoundaryAndFormulaInjection() {
        VndReconciliationCalculator.OwnerReconciliation owner =
                new VndReconciliationCalculator.OwnerReconciliation(
                        9L, 0L, 0L,
                        List.of(VndReconciliationStatus.ORPHAN_OUTBOX),
                        List.of(new VndReconciliationFinding(VndReconciliationStatus.ORPHAN_OUTBOX,
                                9L, "VND", "@formula", "REPORT_BOUNDARY")));
        VndReconciliationRunReport report = new VndReconciliationRunReport(
                "run-report", 1L, 2L, 0L, 0L, "UNAVAILABLE", 0L, 0L,
                false, false, List.of(owner),
                Map.of(VndReconciliationStatus.ORPHAN_OUTBOX, 1), "test");
        check(report.overallStatus() == VndReconciliationStatus.INCOMPLETE_COVERAGE,
                "incomplete report has an unsuccessful overall status");
        check(report.toJson().contains("\"cutoffAt\":null"),
                "unavailable report does not serialize epoch as a real cutoff");
        check(report.toCsv().contains("\"'@formula\""),
                "CSV formula-like operation IDs are prefixed safely");
        VndReconciliationRunReport unavailable = VndReconciliationRunReport.unavailable(
                VndReconciliationRequest.defaults(1), 1L, "test");
        check(unavailable.overallStatus() == VndReconciliationStatus.UNAVAILABLE,
                "unavailable report keeps UNAVAILABLE as the primary status");
    }

    private static VndReconciliationCalculator.OwnerReconciliation evaluate(
            VndReconciliationDataSource.OwnerSnapshot owner) {
        return VndReconciliationCalculator.evaluate(owner, 1_000, 100);
    }

    private static VndReconciliationDataSource.OwnerSnapshot owner(long id, long current,
            List<VndReconciliationDataSource.LedgerMovement> ledger,
            List<VndReconciliationDataSource.OutboxMovement> outbox,
            boolean complete, String unavailable) {
        return new VndReconciliationDataSource.OwnerSnapshot(id, current, ledger, outbox, complete, unavailable);
    }

    private static VndReconciliationDataSource.LedgerMovement movement(long rowId, long ownerId,
            String operationId, String type, long amount, Long delta, long before, Long after,
            long sequence, boolean evidence, String schemaVersion) {
        return new VndReconciliationDataSource.LedgerMovement(rowId, ownerId, "ACCOUNT", "VND",
                operationId, operationId, type, amount, amount, delta, before, after, 0,
                sequence, evidence ? "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef" : null,
                schemaVersion, type, evidence ? 1_000L : 0L, evidence);
    }

    private static void has(VndReconciliationCalculator.OwnerReconciliation result,
            VndReconciliationStatus status, String message) {
        check(result.statuses().contains(status), message + " should emit " + status);
    }

    private static void equalsLong(long expected, Long actual, String message) {
        check(actual != null && expected == actual, message + " expected=" + expected + " actual=" + actual);
    }

    private static void equalsInt(int expected, int actual, String message) {
        check(expected == actual, message + " expected=" + expected + " actual=" + actual);
    }

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError("FAILED: " + message);
    }
}
