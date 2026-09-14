package nro.models.social;

import java.time.Instant;
import java.util.List;

/**
 * RED specification for the future persistence/service phases. Phase 1 must
 * leave these assertions failing because no social behavior exists yet.
 */
public final class SocialV2BehaviorRegressionTest {

    private SocialV2BehaviorRegressionTest() {
    }

    public static void main(String[] args) {
        SocialFriendPolicy policy = new SocialFriendPolicy();
        int failed = 0;
        failed += expect("canonical pair", () -> {
            SocialFriendPolicy.FriendshipPair pair = policy.normalizePair(90L, 12L);
            check(pair.lowPlayerId() == 12L && pair.highPlayerId() == 90L,
                    "pair must be stored low-to-high");
        });
        failed += expect("self friendship", () -> expectFailure(() -> policy.normalizePair(12L, 12L)));
        failed += expect("friend limit", () -> {
            check(policy.canCreateFriendship(99), "99 friends must be permitted");
            check(!policy.canCreateFriendship(100), "100 friends must be rejected");
        });
        failed += expect("cross request", () -> check(
                policy.resolveRequest(true, 44L, 90L) == SocialFriendPolicy.RequestResolution.AUTO_ACCEPT,
                "opposite pending request must auto-accept"));
        failed += expect("expiry", () -> check(
                policy.isRequestExpired(Instant.parse("2026-10-14T00:00:00Z"),
                        Instant.parse("2026-10-14T00:00:00Z")),
                "request expires at its expiry instant"));
        failed += expect("search ranking", () -> {
            List<SocialFriendPolicy.SearchCandidate> ranked = policy.rankSearch("an", List.of(
                    new SocialFriendPolicy.SearchCandidate(7L, "Banan", false),
                    new SocialFriendPolicy.SearchCandidate(8L, "An", false),
                    new SocialFriendPolicy.SearchCandidate(9L, "Anh", false)));
            check(ranked.get(0).playerId() == 8L && ranked.get(1).playerId() == 9L,
                    "exact name must rank before prefix, then contains");
        });
        failed += expect("chat authorization", () -> {
            check(policy.authorizeChat(true, true, "xin chào")
                    == SocialFriendPolicy.Authorization.ALLOWED, "online friends may chat");
            check(policy.authorizeChat(false, true, "xin chào")
                    == SocialFriendPolicy.Authorization.NOT_FRIENDS, "non-friends may not chat");
            check(policy.authorizeChat(true, false, "xin chào")
                    == SocialFriendPolicy.Authorization.OFFLINE, "offline friends may not chat");
        });
        failed += expect("location authorization", () -> {
            Instant now = Instant.parse("2026-09-14T00:00:00Z");
            check(policy.authorizeLocation(true, true, now.minusSeconds(120L), now)
                    == SocialFriendPolicy.Authorization.ALLOWED, "location is allowed after cooldown");
            check(policy.authorizeLocation(true, true, now.minusSeconds(119L), now)
                    == SocialFriendPolicy.Authorization.COOLDOWN, "location cooldown is two minutes");
        });
        if (failed != 0) {
            throw new AssertionError("Social V2 RED specifications still failing: " + failed);
        }
        System.out.println("SocialV2BehaviorRegressionTest: PASS");
    }

    private static int expect(String name, Runnable assertion) {
        try {
            assertion.run();
            return 0;
        } catch (Throwable error) {
            System.err.println("RED " + name + ": " + error.getMessage());
            return 1;
        }
    }

    private static void expectFailure(Runnable action) {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "input must be rejected");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
