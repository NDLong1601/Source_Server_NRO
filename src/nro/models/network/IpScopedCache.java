package nro.models.network;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Small, bounded cache for short-lived state associated with a remote IP.
 *
 * <p>The cache uses access order for capacity eviction and refreshes the TTL
 * whenever an entry is read. All operations are synchronized because login
 * traffic can reach the same address from multiple session threads.</p>
 */
public final class IpScopedCache<V> {

    private final int maxEntries;
    private final long ttlMillis;
    private final LongSupplier clock;
    private final LinkedHashMap<String, Entry<V>> entries = new LinkedHashMap<>(16, 0.75f, true);

    public IpScopedCache(int maxEntries, long ttlMillis) {
        this(maxEntries, ttlMillis, System::currentTimeMillis);
    }

    public IpScopedCache(int maxEntries, long ttlMillis, LongSupplier clock) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("ttlMillis must be positive");
        }
        this.maxEntries = maxEntries;
        this.ttlMillis = ttlMillis;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized V get(String ipAddress) {
        if (ipAddress == null) {
            return null;
        }
        long now = clock.getAsLong();
        Entry<V> entry = entries.get(ipAddress);
        if (entry == null) {
            return null;
        }
        if (isExpired(entry, now)) {
            entries.remove(ipAddress);
            return null;
        }
        entry.lastAccessMillis = now;
        return entry.value;
    }

    public synchronized void put(String ipAddress, V value) {
        Objects.requireNonNull(ipAddress, "ipAddress");
        Objects.requireNonNull(value, "value");
        long now = clock.getAsLong();
        cleanupExpired(now);
        ensureCapacityFor(ipAddress);
        entries.put(ipAddress, new Entry<>(value, now));
    }

    public synchronized V computeIfAbsent(String ipAddress, Function<String, ? extends V> factory) {
        Objects.requireNonNull(ipAddress, "ipAddress");
        Objects.requireNonNull(factory, "factory");
        long now = clock.getAsLong();
        Entry<V> current = entries.get(ipAddress);
        if (current != null && !isExpired(current, now)) {
            current.lastAccessMillis = now;
            return current.value;
        }
        if (current != null) {
            entries.remove(ipAddress);
        }
        V value = Objects.requireNonNull(factory.apply(ipAddress), "factory result");
        cleanupExpired(now);
        ensureCapacityFor(ipAddress);
        entries.put(ipAddress, new Entry<>(value, now));
        return value;
    }

    public synchronized void cleanupExpired() {
        cleanupExpired(clock.getAsLong());
    }

    public synchronized int size() {
        cleanupExpired(clock.getAsLong());
        return entries.size();
    }

    private void cleanupExpired(long now) {
        Iterator<Map.Entry<String, Entry<V>>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            if (isExpired(iterator.next().getValue(), now)) {
                iterator.remove();
            }
        }
    }

    private void ensureCapacityFor(String ipAddress) {
        if (entries.containsKey(ipAddress)) {
            return;
        }
        while (entries.size() >= maxEntries) {
            Iterator<String> iterator = entries.keySet().iterator();
            if (!iterator.hasNext()) {
                return;
            }
            iterator.next();
            iterator.remove();
        }
    }

    private boolean isExpired(Entry<V> entry, long now) {
        long elapsed = now - entry.lastAccessMillis;
        return elapsed >= ttlMillis && elapsed >= 0;
    }

    private static final class Entry<V> {

        private final V value;
        private long lastAccessMillis;

        private Entry(V value, long lastAccessMillis) {
            this.value = value;
            this.lastAccessMillis = lastAccessMillis;
        }
    }
}
