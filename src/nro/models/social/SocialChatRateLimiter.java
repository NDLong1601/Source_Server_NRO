package nro.models.social;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded per-player token bucket for private chat. It stores only an opaque
 * player id and timing credit—never message text, target ids, or account data.
 */
public final class SocialChatRateLimiter {

    public record Policy(int capacity, Duration refillPeriod, int maxTrackedPlayers,
            Duration idleEntryTtl) {
        public Policy {
            if (capacity <= 0 || maxTrackedPlayers <= 0 || refillPeriod == null || idleEntryTtl == null
                    || refillPeriod.isZero() || refillPeriod.isNegative()
                    || idleEntryTtl.isZero() || idleEntryTtl.isNegative()) {
                throw new IllegalArgumentException("Social chat rate-limit policy is invalid");
            }
            try {
                Math.multiplyExact((long) capacity, refillPeriod.toNanos());
            } catch (ArithmeticException overflow) {
                throw new IllegalArgumentException("Social chat token bucket overflows", overflow);
            }
        }

        public static Policy phase4Defaults() {
            return new Policy(3, Duration.ofSeconds(1L), 10_000, Duration.ofMinutes(15L));
        }
    }

    private final Policy policy;
    private final long refillPeriodNanos;
    private final long maximumCreditNanos;
    private final LinkedHashMap<Long, Bucket> buckets = new LinkedHashMap<>(16, 0.75F, true);

    public SocialChatRateLimiter() {
        this(Policy.phase4Defaults());
    }

    public SocialChatRateLimiter(Policy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("Social chat rate-limit policy is required");
        }
        this.policy = policy;
        this.refillPeriodNanos = policy.refillPeriod().toNanos();
        this.maximumCreditNanos = Math.multiplyExact((long) policy.capacity(), this.refillPeriodNanos);
    }

    /** Consumes one message token, refilling continuously at capacity per configured period. */
    public synchronized boolean tryConsume(long playerId, Instant now) {
        if (playerId <= 0L || now == null) {
            throw new IllegalArgumentException("Player id and current time are required for chat rate limiting");
        }
        evictIdle(now);
        Bucket bucket = bucketFor(playerId, now);
        Instant effectiveNow = now.isBefore(bucket.lastSeenAt) ? bucket.lastSeenAt : now;
        long elapsedNanos = safeElapsedNanos(bucket.lastRefillAt, effectiveNow);
        // The bucket holds `capacity` tokens and fully refills during one
        // configured period, i.e. the phase-4 policy is three messages/sec.
        bucket.creditNanos = Math.min(this.maximumCreditNanos,
                saturatedAdd(bucket.creditNanos, saturatedMultiply(elapsedNanos, policy.capacity())));
        bucket.lastRefillAt = effectiveNow;
        bucket.lastSeenAt = effectiveNow;
        if (bucket.creditNanos < this.refillPeriodNanos) {
            return false;
        }
        bucket.creditNanos -= this.refillPeriodNanos;
        return true;
    }

    synchronized int trackedPlayerCount() {
        return buckets.size();
    }

    private Bucket bucketFor(long playerId, Instant now) {
        Bucket existing = buckets.get(playerId);
        if (existing != null) {
            return existing;
        }
        while (buckets.size() >= policy.maxTrackedPlayers()) {
            Iterator<Map.Entry<Long, Bucket>> entries = buckets.entrySet().iterator();
            if (!entries.hasNext()) {
                break;
            }
            entries.next();
            entries.remove();
        }
        Bucket created = new Bucket(this.maximumCreditNanos, now);
        buckets.put(playerId, created);
        return created;
    }

    private void evictIdle(Instant now) {
        Instant cutoff = now.minus(policy.idleEntryTtl());
        Iterator<Map.Entry<Long, Bucket>> entries = buckets.entrySet().iterator();
        while (entries.hasNext()) {
            if (!entries.next().getValue().lastSeenAt.isAfter(cutoff)) {
                entries.remove();
            }
        }
    }

    private static long safeElapsedNanos(Instant earlier, Instant later) {
        if (earlier == null || later == null || !later.isAfter(earlier)) {
            return 0L;
        }
        try {
            return Duration.between(earlier, later).toNanos();
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static long saturatedMultiply(long value, int multiplier) {
        if (value <= 0L || multiplier <= 0) {
            return 0L;
        }
        return value > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : value * multiplier;
    }

    private static final class Bucket {
        private long creditNanos;
        private Instant lastRefillAt;
        private Instant lastSeenAt;

        private Bucket(long creditNanos, Instant now) {
            this.creditNanos = creditNanos;
            this.lastRefillAt = now;
            this.lastSeenAt = now;
        }
    }
}
