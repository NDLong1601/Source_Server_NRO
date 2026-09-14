package nro.models.social;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded server-side record of successfully delivered location shares. */
public final class SocialLocationCooldown {

    private static final int MAX_TRACKED_PLAYERS = 10_000;
    private static final Duration IDLE_TTL = Duration.ofMinutes(15L);
    private final LinkedHashMap<Long, Instant> lastSharedAt = new LinkedHashMap<>(16, 0.75F, true);

    public synchronized Instant lastSharedAt(long playerId, Instant now) {
        if (playerId <= 0L || now == null) {
            throw new IllegalArgumentException("Player id and current time are required for location cooldown");
        }
        evictIdle(now);
        return lastSharedAt.get(playerId);
    }

    public synchronized void markDelivered(long playerId, Instant now) {
        if (playerId <= 0L || now == null) {
            throw new IllegalArgumentException("Player id and current time are required for location cooldown");
        }
        evictIdle(now);
        while (lastSharedAt.size() >= MAX_TRACKED_PLAYERS && !lastSharedAt.containsKey(playerId)) {
            Iterator<Map.Entry<Long, Instant>> entries = lastSharedAt.entrySet().iterator();
            if (!entries.hasNext()) {
                break;
            }
            entries.next();
            entries.remove();
        }
        Instant existing = lastSharedAt.get(playerId);
        lastSharedAt.put(playerId, existing != null && existing.isAfter(now) ? existing : now);
    }

    private void evictIdle(Instant now) {
        Instant cutoff = now.minus(IDLE_TTL);
        Iterator<Map.Entry<Long, Instant>> entries = lastSharedAt.entrySet().iterator();
        while (entries.hasNext()) {
            if (!entries.next().getValue().isAfter(cutoff)) {
                entries.remove();
            }
        }
    }
}
