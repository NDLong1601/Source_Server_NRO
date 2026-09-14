package nro.models.social;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import nro.models.player.Player;

/**
 * Phase-4 regression specification for the server-only social realtime boundary.
 * It deliberately uses in-memory collaborators: no production database, network,
 * client project, or server process is needed to prove these rules.
 */
public final class SocialV2Phase4RegressionTest {

    private SocialV2Phase4RegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        verifyChatAndLocationPolicy();
        verifyChatTokenBucket();
        verifyPresenceUsesOnlyOnlineFriendsAndUnpublishesBeforeDelivery();
        verifyOfflineProfileIsMinimalAndHasStableActivityLabels();
        System.out.println("SocialV2Phase4RegressionTest: PASS");
        // Player construction initializes legacy non-daemon runtime workers.
        // This isolated regression process must not keep the suite alive.
        System.exit(0);
    }

    private static void verifyChatAndLocationPolicy() {
        SocialFriendPolicy policy = new SocialFriendPolicy();
        Instant now = Instant.parse("2026-09-14T00:00:00Z");

        assertEquals("online mutual friends may chat", SocialFriendPolicy.Authorization.ALLOWED,
                policy.authorizeChat(true, true, "  xin chào  "));
        assertEquals("non-friends are rejected before presence is disclosed",
                SocialFriendPolicy.Authorization.NOT_FRIENDS,
                policy.authorizeChat(false, false, "xin chào"));
        assertEquals("offline friends may not chat", SocialFriendPolicy.Authorization.OFFLINE,
                policy.authorizeChat(true, false, "xin chào"));
        assertEquals("blank chat is rejected", SocialFriendPolicy.Authorization.INVALID_TEXT,
                policy.authorizeChat(true, true, " \t "));
        assertEquals("control characters are rejected", SocialFriendPolicy.Authorization.INVALID_TEXT,
                policy.authorizeChat(true, true, "hello\nworld"));
        assertEquals("80 Unicode code points are allowed", SocialFriendPolicy.Authorization.ALLOWED,
                policy.authorizeChat(true, true, "x".repeat(80)));
        assertEquals("81 Unicode code points are rejected", SocialFriendPolicy.Authorization.INVALID_TEXT,
                policy.authorizeChat(true, true, "x".repeat(81)));

        assertEquals("location from an online friend is allowed", SocialFriendPolicy.Authorization.ALLOWED,
                policy.authorizeLocation(true, true, now.minusMillis(SocialV2Protocol.LOCATION_COOLDOWN_MILLIS), now));
        assertEquals("location cooldown remains server-side", SocialFriendPolicy.Authorization.COOLDOWN,
                policy.authorizeLocation(true, true, now.minusMillis(SocialV2Protocol.LOCATION_COOLDOWN_MILLIS - 1L), now));
    }

    private static void verifyChatTokenBucket() {
        SocialChatRateLimiter limiter = new SocialChatRateLimiter(new SocialChatRateLimiter.Policy(
                3, Duration.ofSeconds(1L), 10, Duration.ofMinutes(15L)));
        Instant now = Instant.parse("2026-09-14T00:00:00Z");
        assertTrue("first chat token", limiter.tryConsume(1L, now));
        assertTrue("second chat token", limiter.tryConsume(1L, now));
        assertTrue("third chat token", limiter.tryConsume(1L, now));
        assertTrue("fourth chat token is rejected", !limiter.tryConsume(1L, now));
        assertTrue("one token refills after one third of the period", limiter.tryConsume(1L, now.plusMillis(334L)));
        assertTrue("different players have independent buckets", limiter.tryConsume(2L, now));
    }

    private static void verifyPresenceUsesOnlyOnlineFriendsAndUnpublishesBeforeDelivery() throws Exception {
        List<PresenceEvent> events = new ArrayList<>();
        Map<Long, List<Long>> friendships = Map.of(1L, List.of(2L), 2L, List.of(1L), 3L, List.of());
        SocialPresenceService presence = new SocialPresenceService(
                playerId -> friendships.getOrDefault(playerId, List.of()),
                (recipient, friendId, online) -> events.add(new PresenceEvent(recipient.id, friendId, online)));
        Player alice = player(1L, "Alice");
        Player bob = player(2L, "Bob");
        Player stranger = player(3L, "Stranger");

        presence.publish(bob);
        presence.publish(stranger);
        presence.publish(alice);
        assertEquals("only Bob receives Alice online", List.of(new PresenceEvent(2L, 1L, true)), events);
        assertTrue("published player is online", presence.isOnline(1L));
        assertTrue("stranger is never subscribed", events.stream().noneMatch(event -> event.recipientId() == 3L));

        events.clear();
        presence.publish(alice);
        assertEquals("duplicate publication is idempotent", List.of(), events);

        Player reconnectedAlice = player(1L, "Alice");
        presence.publish(reconnectedAlice);
        assertEquals("replacement session receives ordered offline then online transitions", List.of(
                new PresenceEvent(2L, 1L, false), new PresenceEvent(2L, 1L, true)), events);
        events.clear();
        presence.unpublish(alice);
        assertTrue("old teardown cannot unpublish replacement", presence.isOnline(1L));
        presence.unpublish(reconnectedAlice);
        assertTrue("unpublish blocks future delivery", !presence.isOnline(1L));
        assertEquals("only Bob receives Alice offline", List.of(new PresenceEvent(2L, 1L, false)), events);
    }

    private static void verifyOfflineProfileIsMinimalAndHasStableActivityLabels() throws Exception {
        Instant now = Instant.parse("2026-09-14T00:00:00Z");
        SocialProfileRepository repository = playerId -> playerId == 9L
                ? new SocialProfileRepository.StoredProfile(9L, "Offline", (short) 44, "Clan", 123_456L,
                        now.minus(Duration.ofDays(8L)))
                : null;
        SocialProfileService profiles = new SocialProfileService(repository);
        SocialProfileService.Profile profile = profiles.loadOffline(9L, now);

        assertEquals("profile keeps only requested player id", 9L, profile.playerId());
        assertEquals("offline profile carries no online Player", false, profile.online());
        assertEquals("eight-day activity label", "Gần đây", profile.activityLabel());
        assertEquals("raw power remains numeric", 123_456L, profile.rawPower());
        assertTrue("formatted power is present", !profile.formattedPower().isBlank());
        assertEquals("unknown profile stays absent", null, profiles.loadOffline(10L, now));
    }

    private static Player player(long id, String name) {
        Player player = new Player();
        player.id = id;
        player.name = name;
        return player;
    }

    private static void assertTrue(String label, boolean condition) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }

    private static void assertEquals(String label, Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(label + " expected=" + expected + " actual=" + actual);
        }
    }

    private record PresenceEvent(long recipientId, long friendId, boolean online) {
    }
}
