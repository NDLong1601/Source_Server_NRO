package nro.models.clan;

/** Thread-safe saturating counter used before a metrics batch is persisted. */
public final class ClanEconomyMetricAccumulator {

    private long eventCount;
    private long amountTotal;

    public synchronized void add(long events, long amount) {
        if (events < 0L || amount < 0L) {
            throw new IllegalArgumentException("Metric deltas must be non-negative");
        }
        eventCount = saturatedAdd(eventCount, events);
        amountTotal = saturatedAdd(amountTotal, amount);
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(eventCount, amountTotal);
    }

    private static long saturatedAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    public record Snapshot(long eventCount, long amountTotal) {
    }
}
