package nro.models.network;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public final class IpConnectionRegistry {

    private final ConcurrentHashMap<String, AtomicInteger> ipCounts = new ConcurrentHashMap<>();
    private final LongAdder totalAccepted = new LongAdder();
    private final LongAdder totalRejected = new LongAdder();
    private final LongAdder activeLeases = new LongAdder();

    public IpLease acquire(String rawIp, int maxPerIp) {
        if (rawIp == null || rawIp.isBlank()) {
            return null;
        }
        AtomicBoolean acquired = new AtomicBoolean(false);
        ipCounts.compute(rawIp, (k, current) -> {
            int val = (current == null) ? 0 : current.get();
            if (val < maxPerIp) {
                acquired.set(true);
                return new AtomicInteger(val + 1);
            }
            return current;
        });

        if (acquired.get()) {
            activeLeases.increment();
            totalAccepted.increment();
            return new IpLease(this, rawIp);
        } else {
            totalRejected.increment();
            return null;
        }
    }

    void release(String rawIp) {
        if (rawIp == null || rawIp.isBlank()) {
            return;
        }
        AtomicBoolean wasActive = new AtomicBoolean(false);
        ipCounts.compute(rawIp, (k, current) -> {
            if (current == null) {
                return null;
            }
            wasActive.set(true);
            int val = current.decrementAndGet();
            if (val <= 0) {
                return null;
            }
            return current;
        });
        if (wasActive.get()) {
            activeLeases.decrement();
        }
    }

    public int getCount(String rawIp) {
        if (rawIp == null) {
            return 0;
        }
        AtomicInteger counter = ipCounts.get(rawIp);
        return counter == null ? 0 : Math.max(0, counter.get());
    }

    public boolean hasEntry(String rawIp) {
        return rawIp != null && ipCounts.containsKey(rawIp);
    }

    public long getActiveLeases() {
        return activeLeases.sum();
    }

    public long getTotalAccepted() {
        return totalAccepted.sum();
    }

    public long getTotalRejected() {
        return totalRejected.sum();
    }

    public void clear() {
        ipCounts.clear();
        activeLeases.reset();
        totalAccepted.reset();
        totalRejected.reset();
    }
}
