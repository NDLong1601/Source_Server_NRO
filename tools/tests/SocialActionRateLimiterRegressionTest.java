package nro.models.social;

import java.time.Duration;
import java.time.Instant;

/** Specifies the bounded per-player anti-enumeration and anti-spam policy for Phase 3. */
public final class SocialActionRateLimiterRegressionTest {

    private static final Instant START = Instant.parse("2026-09-14T00:00:00Z");

    private SocialActionRateLimiterRegressionTest() {
    }

    public static void main(String[] args) {
        verifySearchWindowIsPerPlayerAndResets();
        verifyRequestCooldownAndWindowAreBothEnforced();
        verifyLimiterTrackingRemainsBounded();
        System.out.println("SocialActionRateLimiterRegressionTest: PASS");
    }

    private static void verifySearchWindowIsPerPlayerAndResets() {
        SocialActionRateLimiter limiter = new SocialActionRateLimiter(policy());
        for (int attempt = 0; attempt < 30; attempt++) {
            check(limiter.allowSearch(1L, START.plusSeconds(attempt)), "first 30 searches must be allowed");
        }
        check(!limiter.allowSearch(1L, START.plusSeconds(30L)), "31st search in one minute must be limited");
        check(limiter.allowSearch(2L, START.plusSeconds(30L)), "one player's search limit must not throttle another");
        check(limiter.allowSearch(1L, START.plusSeconds(60L)), "rolling search window must release its oldest request");
    }

    private static void verifyRequestCooldownAndWindowAreBothEnforced() {
        SocialActionRateLimiter limiter = new SocialActionRateLimiter(policy());
        check(limiter.allowFriendRequest(1L, START), "first friend request must be allowed");
        check(!limiter.allowFriendRequest(1L, START.plusSeconds(4L)), "five-second request cooldown must apply");
        for (int request = 1; request < 10; request++) {
            check(limiter.allowFriendRequest(1L, START.plusSeconds(request * 5L)),
                    "request cooldown should release each five seconds");
        }
        check(!limiter.allowFriendRequest(1L, START.plusSeconds(50L)), "11th request in ten minutes must be limited");
        check(limiter.allowFriendRequest(1L, START.plusSeconds(600L)),
                "request window must release its oldest accepted request");
    }

    private static void verifyLimiterTrackingRemainsBounded() {
        SocialActionRateLimiter limiter = new SocialActionRateLimiter(new SocialActionRateLimiter.Policy(
                1, Duration.ofMinutes(1L), 1, Duration.ofMinutes(1L), Duration.ZERO, 2, Duration.ofMinutes(1L)));
        check(limiter.allowSearch(1L, START), "first tracked player must be allowed");
        check(limiter.allowSearch(2L, START), "second tracked player must be allowed");
        check(limiter.allowSearch(3L, START), "new player may evict least-recent tracking state");
        check(limiter.trackedPlayerCount() <= 2, "limiter must not grow without bound");
    }

    private static SocialActionRateLimiter.Policy policy() {
        return SocialActionRateLimiter.Policy.phase3Defaults();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
