package nro.models.ledger;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Serializable SEC-07 run result. JSON/CSV are deliberately implemented here
 * so the read-only CLI does not introduce another dependency or log payloads.
 */
public final class VndReconciliationRunReport {

    private final String runId;
    private final long startedAtMillis;
    private final long finishedAtMillis;
    private final long cutoffLedgerId;
    private final long cutoffAtMillis;
    private final String consistencyModel;
    private final long afterOwnerId;
    private final long nextCursor;
    private final boolean hasMoreOwners;
    private final boolean coverageComplete;
    private final List<VndReconciliationCalculator.OwnerReconciliation> owners;
    private final Map<VndReconciliationStatus, Integer> counts;
    private final String unavailableDetail;
    private final List<Long> unresolvedOwnerIds;

    public VndReconciliationRunReport(String runId, long startedAtMillis, long finishedAtMillis,
            long cutoffLedgerId, long cutoffAtMillis, String consistencyModel,
            long afterOwnerId, long nextCursor, boolean hasMoreOwners, boolean coverageComplete,
            List<VndReconciliationCalculator.OwnerReconciliation> owners,
            Map<VndReconciliationStatus, Integer> counts, String unavailableDetail,
            List<Long> unresolvedOwnerIds) {
        this.runId = runId;
        this.startedAtMillis = startedAtMillis;
        this.finishedAtMillis = finishedAtMillis;
        this.cutoffLedgerId = cutoffLedgerId;
        this.cutoffAtMillis = cutoffAtMillis;
        this.consistencyModel = consistencyModel == null ? "UNAVAILABLE" : consistencyModel;
        this.afterOwnerId = afterOwnerId;
        this.nextCursor = nextCursor;
        this.hasMoreOwners = hasMoreOwners;
        this.coverageComplete = coverageComplete;
        this.owners = owners == null ? List.of() : List.copyOf(owners);
        this.counts = new EnumMap<>(VndReconciliationStatus.class);
        if (counts != null) {
            this.counts.putAll(counts);
        }
        this.unavailableDetail = unavailableDetail == null ? "" : unavailableDetail;
        this.unresolvedOwnerIds = unresolvedOwnerIds == null ? List.of() : List.copyOf(unresolvedOwnerIds);
    }

    public VndReconciliationRunReport(String runId, long startedAtMillis, long finishedAtMillis,
            long cutoffLedgerId, long cutoffAtMillis, String consistencyModel,
            long afterOwnerId, long nextCursor, boolean hasMoreOwners, boolean coverageComplete,
            List<VndReconciliationCalculator.OwnerReconciliation> owners,
            Map<VndReconciliationStatus, Integer> counts, String unavailableDetail) {
        this(runId, startedAtMillis, finishedAtMillis, cutoffLedgerId, cutoffAtMillis,
                consistencyModel, afterOwnerId, nextCursor, hasMoreOwners, coverageComplete,
                owners, counts, unavailableDetail, List.of());
    }

    public static VndReconciliationRunReport unavailable(VndReconciliationRequest request,
            long startedAtMillis, String detail) {
        VndReconciliationCalculator.OwnerReconciliation owner =
                new VndReconciliationCalculator.OwnerReconciliation(
                        request.ownerId(), 0L, null,
                        List.of(VndReconciliationStatus.UNAVAILABLE),
                        List.of(new VndReconciliationFinding(VndReconciliationStatus.UNAVAILABLE,
                                request.ownerId(), "VND", "", detail)));
        EnumMap<VndReconciliationStatus, Integer> counts = new EnumMap<>(VndReconciliationStatus.class);
        counts.put(VndReconciliationStatus.UNAVAILABLE, 1);
        return new VndReconciliationRunReport(request.runId(), startedAtMillis, System.currentTimeMillis(),
                0L, 0L, "UNAVAILABLE", request.afterOwnerId(), request.afterOwnerId(), false,
                false, List.of(owner), counts, detail);
    }

    public String runId() { return runId; }
    public long startedAtMillis() { return startedAtMillis; }
    public long finishedAtMillis() { return finishedAtMillis; }
    public long durationMillis() { return Math.max(0L, finishedAtMillis - startedAtMillis); }
    public long cutoffLedgerId() { return cutoffLedgerId; }
    public long cutoffAtMillis() { return cutoffAtMillis; }
    public String consistencyModel() { return consistencyModel; }
    public long afterOwnerId() { return afterOwnerId; }
    public long nextCursor() { return nextCursor; }
    public boolean hasMoreOwners() { return hasMoreOwners; }
    public boolean coverageComplete() { return coverageComplete; }
    public List<VndReconciliationCalculator.OwnerReconciliation> owners() { return owners; }
    public Map<VndReconciliationStatus, Integer> counts() { return Map.copyOf(counts); }
    public String unavailableDetail() { return unavailableDetail; }
    public List<Long> unresolvedOwnerIds() { return unresolvedOwnerIds; }

    public boolean isSuccessful() {
        if (!coverageComplete || hasMoreOwners || !unresolvedOwnerIds.isEmpty()) {
            return false;
        }
        return owners.stream().allMatch(VndReconciliationCalculator.OwnerReconciliation::isOk);
    }

    /** Process contract: 0=clean, 2=actionable finding/partial, 3=unavailable. */
    public int exitCode() {
        if (overallStatus() == VndReconciliationStatus.UNAVAILABLE
                || owners.stream().anyMatch(owner -> owner.statuses().contains(VndReconciliationStatus.UNAVAILABLE))) {
            return 3;
        }
        return isSuccessful() ? 0 : 2;
    }

    public VndReconciliationStatus overallStatus() {
        if (counts.getOrDefault(VndReconciliationStatus.UNAVAILABLE, 0) > 0) {
            return VndReconciliationStatus.UNAVAILABLE;
        }
        if (!coverageComplete || hasMoreOwners || !unresolvedOwnerIds.isEmpty()) {
            return VndReconciliationStatus.INCOMPLETE_COVERAGE;
        }
        return owners.stream().map(VndReconciliationCalculator.OwnerReconciliation::primaryStatus)
                .max(java.util.Comparator.comparingInt(VndReconciliationStatus::severity))
                .orElse(VndReconciliationStatus.OK);
    }

    public String toJson() {
        StringBuilder json = new StringBuilder(512);
        json.append('{')
                .append("\"schemaVersion\":\"SEC-07-V1\",")
                .append("\"runId\":").append(quote(runId)).append(',')
                .append("\"startedAt\":").append(quote(Instant.ofEpochMilli(startedAtMillis).toString())).append(',')
                .append("\"finishedAt\":").append(quote(Instant.ofEpochMilli(finishedAtMillis).toString())).append(',')
                .append("\"durationMillis\":").append(durationMillis()).append(',')
                .append("\"cutoffLedgerId\":").append(cutoffLedgerId).append(',')
                .append("\"cutoffAt\":").append(cutoffAtMillis == 0L
                        ? "null" : quote(Instant.ofEpochMilli(cutoffAtMillis).toString())).append(',')
                .append("\"consistencyModel\":").append(quote(consistencyModel)).append(',')
                .append("\"afterOwnerId\":").append(afterOwnerId).append(',')
                .append("\"nextCursor\":").append(nextCursor).append(',')
                .append("\"hasMoreOwners\":").append(hasMoreOwners).append(',')
                .append("\"unresolvedOwnerIds\":[");
        for (int i = 0; i < unresolvedOwnerIds.size(); i++) {
            if (i > 0) json.append(',');
            json.append(unresolvedOwnerIds.get(i));
        }
        json.append("],")
                .append("\"coverageComplete\":").append(coverageComplete).append(',')
                .append("\"overallStatus\":").append(quote(overallStatus().name())).append(',')
                .append("\"successful\":").append(isSuccessful()).append(',')
                .append("\"counts\":{");
        boolean first = true;
        for (VndReconciliationStatus status : VndReconciliationStatus.values()) {
            Integer count = counts.get(status);
            if (count == null) continue;
            if (!first) json.append(',');
            first = false;
            json.append(quote(status.name())).append(':').append(count);
        }
        json.append("},\"owners\":[");
        for (int i = 0; i < owners.size(); i++) {
            if (i > 0) json.append(',');
            var owner = owners.get(i);
            json.append('{').append("\"ownerId\":").append(owner.ownerId())
                    .append(",\"persistedBalance\":").append(owner.persistedBalance())
                    .append(",\"expectedBalance\":").append(owner.expectedBalance() == null ? "null" : owner.expectedBalance())
                    .append(",\"statuses\":[");
            for (int j = 0; j < owner.statuses().size(); j++) {
                if (j > 0) json.append(',');
                json.append(quote(owner.statuses().get(j).name()));
            }
            json.append("],\"findings\":[");
            for (int j = 0; j < owner.findings().size(); j++) {
                if (j > 0) json.append(',');
                VndReconciliationFinding finding = owner.findings().get(j);
                json.append('{').append("\"code\":").append(quote(finding.status().name()))
                        .append(",\"ownerId\":").append(finding.ownerId())
                        .append(",\"currency\":").append(quote(finding.currency()))
                        .append(",\"operationId\":").append(quote(finding.operationId()))
                        .append(",\"detailCode\":").append(quote(finding.detailCode())).append('}');
            }
            json.append("]}");
        }
        json.append("]}");
        return json.toString();
    }

    public String toCsv() {
        StringBuilder csv = new StringBuilder(
                "run_id,duration_ms,overall_status,successful,coverage_complete,has_more_owners,next_cursor,"
                + "unresolved_owner_ids,owner_id,currency,status,detail_code,operation_id,persisted_balance,expected_balance\n");
        for (var owner : owners) {
            if (owner.findings().isEmpty()) {
                csv.append(row(runId, durationMillis(), overallStatus().name(), isSuccessful(), coverageComplete,
                        hasMoreOwners, nextCursor, unresolvedOwnerIds, owner.ownerId(), "VND", "OK", "", "",
                        owner.persistedBalance(), owner.expectedBalance()));
            } else {
                for (var finding : owner.findings()) {
                    csv.append(row(runId, durationMillis(), overallStatus().name(), isSuccessful(), coverageComplete,
                            hasMoreOwners, nextCursor, unresolvedOwnerIds, owner.ownerId(), finding.currency(), finding.status().name(),
                            finding.detailCode(), finding.operationId(), owner.persistedBalance(), owner.expectedBalance()));
                }
            }
        }
        return csv.toString();
    }

    public String summaryLine() {
        return "SEC-07 run=" + runId + " status=" + overallStatus().name()
                + " owners=" + owners.size() + " durationMs=" + durationMillis()
                + " coverageComplete=" + coverageComplete + " unresolved=" + unresolvedOwnerIds.size()
                + " nextCursor=" + nextCursor + " hasMore=" + hasMoreOwners;
    }

    private static String row(String runId, long durationMillis, String overallStatus, boolean successful,
            boolean coverageComplete, boolean hasMoreOwners, long nextCursor, List<Long> unresolvedOwnerIds,
            long ownerId, String currency, String status,
            String detailCode, String operationId, long persisted, Long expected) {
        return csv(runId) + ',' + durationMillis + ',' + csv(overallStatus) + ',' + successful + ','
                + coverageComplete + ',' + hasMoreOwners + ',' + nextCursor + ','
                + csv(unresolvedOwnerIds.toString()) + ',' + ownerId + ',' + csv(currency)
                + ',' + csv(status) + ','
                + csv(detailCode) + ',' + csv(operationId) + ',' + persisted + ','
                + (expected == null ? "" : expected) + '\n';
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value;
        if (safe.startsWith("=") || safe.startsWith("+") || safe.startsWith("-") || safe.startsWith("@")) {
            safe = "'" + safe;
        }
        return '"' + safe.replace("\"", "\"\"") + '"';
    }

    private static String quote(String value) {
        if (value == null) return "null";
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n") + '"';
    }
}
