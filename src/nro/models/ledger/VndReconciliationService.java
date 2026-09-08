package nro.models.ledger;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import nro.models.utils.Logger;

/** Bounded, read-only SEC-07 reconciliation service. */
public class VndReconciliationService {

    private final MoneyLedgerRepository repository;

    public VndReconciliationService(MoneyLedgerRepository repository) {
        this.repository = repository;
    }

    public static VndReconciliationService createDefault() {
        return new VndReconciliationService(MoneyLedgerService.gI().getRepository());
    }

    /**
     * Runs the SEC-07 audit against a bounded, owner-consistent snapshot. A
     * finding is data, not an exception: one bad owner is retained while other
     * owners in the batch are still evaluated.
     */
    public VndReconciliationRunReport runSec07(VndReconciliationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("SEC-07 request is required");
        }
        long startedAt = System.currentTimeMillis();
        if (!(repository instanceof VndReconciliationDataSource source)) {
            return VndReconciliationRunReport.unavailable(request, startedAt,
                    "REPOSITORY_DOES_NOT_SUPPORT_SEC07");
        }

        final VndReconciliationDataSource.ReconciliationSnapshot snapshot;
        try {
            snapshot = source.readVndSnapshot(request);
        } catch (RuntimeException unavailable) {
            Logger.error("[SEC-07] Reconciliation unavailable run=" + request.runId());
            return VndReconciliationRunReport.unavailable(request, startedAt,
                    "SNAPSHOT_UNAVAILABLE");
        }

        List<VndReconciliationCalculator.OwnerReconciliation> owners = new ArrayList<>();
        EnumMap<VndReconciliationStatus, Integer> counts = new EnumMap<>(VndReconciliationStatus.class);
        for (VndReconciliationDataSource.OwnerSnapshot owner : snapshot.owners()) {
            VndReconciliationCalculator.OwnerReconciliation result;
            try {
                result = owner.evaluated() != null
                        ? owner.evaluated()
                        : VndReconciliationCalculator.evaluate(owner, System.currentTimeMillis(),
                                request.stalePendingAfterMillis());
            } catch (RuntimeException invalidSnapshot) {
                result = new VndReconciliationCalculator.OwnerReconciliation(
                        owner == null ? -1L : owner.ownerId(),
                        owner == null ? 0L : owner.persistedBalance(),
                        null,
                        List.of(VndReconciliationStatus.UNAVAILABLE),
                        List.of(new VndReconciliationFinding(VndReconciliationStatus.UNAVAILABLE,
                                owner == null ? -1L : owner.ownerId(), "VND", "", "OWNER_EVALUATION_FAILED")));
            }
            owners.add(result);
            for (VndReconciliationStatus status : result.statuses()) {
                counts.merge(status, 1, Integer::sum);
                if (status != VndReconciliationStatus.OK) {
                    Logger.error("[SEC-07] finding run=" + request.runId()
                            + " owner=" + result.ownerId() + " code=" + status.name());
                }
            }
        }

        boolean coverageComplete = snapshot.coverageComplete()
                && !snapshot.hasMoreOwners()
                && snapshot.unresolvedOwnerIds().isEmpty()
                && owners.stream().noneMatch(owner -> owner.statuses().contains(VndReconciliationStatus.UNAVAILABLE));
        if (!coverageComplete) {
            counts.merge(VndReconciliationStatus.INCOMPLETE_COVERAGE, 1, Integer::sum);
        }
        return new VndReconciliationRunReport(
                request.runId(), startedAt, System.currentTimeMillis(),
                snapshot.cutoffLedgerId(), snapshot.cutoffAtMillis(),
                "REPEATABLE_READ_OWNER_SNAPSHOT",
                request.afterOwnerId(), snapshot.nextCursor(), snapshot.hasMoreOwners(),
                coverageComplete, owners, counts, "", snapshot.unresolvedOwnerIds());
    }
}
