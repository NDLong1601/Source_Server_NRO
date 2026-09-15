package nro.models.social;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded, synchronized per-player rate limiter for the single local server
 * process. It intentionally records no names, text, or account information.
 */
public final class SocialActionRateLimiter {

    public record Policy(int maxSearches, Duration searchWindow, int maxFriendRequests,
            Duration requestWindow, Duration requestCooldown, int maxTrackedPlayers,
            Duration idleEntryTtl) {
        public Policy {
            if (maxSearches <= 0 || maxFriendRequests <= 0 || maxTrackedPlayers <= 0
                    || searchWindow == null || requestWindow == null || requestCooldown == null
                    || idleEntryTtl == null || searchWindow.isZero() || searchWindow.isNegative()
                    || requestWindow.isZero() || requestWindow.isNegative()
                    || requestCooldown.isNegative() || idleEntryTtl.isZero() || idleEntryTtl.isNegative()) {
                throw new IllegalArgumentException("Social rate-limit policy is invalid");
            }
        }

        public static Policy defaults() {
            return new Policy(30, Duration.ofMinutes(1L), 10, Duration.ofMinutes(10L),
                    Duration.ofSeconds(5L), 10_000, Duration.ofMinutes(15L));
        }
    }

    private final Policy policy;
    private final LinkedHashMap<Long, PlayerWindow> windows = new LinkedHashMap<>(16, 0.75F, true);

    public SocialActionRateLimiter() {
        this(Policy.defaults());
    }

    public SocialActionRateLimiter(Policy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("Social rate-limit policy is required");
        }
        this.policy = policy;
    }

    public synchronized boolean allowSearch(long playerId, Instant now) {
        PlayerWindow window = windowFor(playerId, now);
        Instant effectiveNow = effectiveNow(window, now);
        discardExpired(window.searches, effectiveNow, policy.searchWindow());
        if (window.searches.size() >= policy.maxSearches()) {
            return false;
        }
        window.searches.addLast(effectiveNow);
        return true;
    }

    public synchronized boolean allowFriendRequest(long playerId, Instant now) {
        PlayerWindow window = windowFor(playerId, now);
        Instant effectiveNow = effectiveNow(window, now);
        discardExpired(window.friendRequests, effectiveNow, policy.requestWindow());
        if (window.lastFriendRequestAt != null
                && effectiveNow.isBefore(window.lastFriendRequestAt.plus(policy.requestCooldown()))) {
            return false;
        }
        if (window.friendRequests.size() >= policy.maxFriendRequests()) {
            return false;
        }
        window.friendRequests.addLast(effectiveNow);
        window.lastFriendRequestAt = effectiveNow;
        return true;
    }

    /** Visible to package-local regression checks only; it never exposes identities. */
    synchronized int trackedPlayerCount() {
        return windows.size();
    }

    private PlayerWindow windowFor(long playerId, Instant now) {
        if (playerId <= 0L || now == null) {
            throw new IllegalArgumentException("Player id and current time are required for social rate limiting");
        }
        evictIdle(now);
        PlayerWindow existing = windows.get(playerId);
        if (existing != null) {
            return existing;
        }
        while (windows.size() >= policy.maxTrackedPlayers()) {
            Iterator<Map.Entry<Long, PlayerWindow>> entries = windows.entrySet().iterator();
            if (!entries.hasNext()) {
                break;
            }
            entries.next();
            entries.remove();
        }
        PlayerWindow created = new PlayerWindow(now);
        windows.put(playerId, created);
        return created;
    }

    private Instant effectiveNow(PlayerWindow window, Instant requestedNow) {
        Instant effective = requestedNow.isBefore(window.lastSeenAt) ? window.lastSeenAt : requestedNow;
        window.lastSeenAt = effective;
        return effective;
    }

    private void evictIdle(Instant now) {
        Instant cutoff = now.minus(policy.idleEntryTtl());
        Iterator<Map.Entry<Long, PlayerWindow>> entries = windows.entrySet().iterator();
        while (entries.hasNext()) {
            PlayerWindow window = entries.next().getValue();
            if (!window.lastSeenAt.isAfter(cutoff)) {
                entries.remove();
            }
        }
    }

    private static void discardExpired(Deque<Instant> attempts, Instant now, Duration window) {
        Instant cutoff = now.minus(window);
        while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(cutoff)) {
            attempts.removeFirst();
        }
    }

    private static final class PlayerWindow {
        private final Deque<Instant> searches = new ArrayDeque<>();
        private final Deque<Instant> friendRequests = new ArrayDeque<>();
        private Instant lastSeenAt;
        private Instant lastFriendRequestAt;

        private PlayerWindow(Instant createdAt) {
            this.lastSeenAt = createdAt;
        }
    }
}
