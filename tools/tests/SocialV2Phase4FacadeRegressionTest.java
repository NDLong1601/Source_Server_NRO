package nro.models.social;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import nro.models.map.Zone;
import nro.models.network.Message;
import nro.models.network.MySession;
import nro.models.player.Location;
import nro.models.player.Player;

/** Wire and authorization checks for phase-4 facade integration without a live server. */
public final class SocialV2Phase4FacadeRegressionTest {

    private SocialV2Phase4FacadeRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        MutableFriendships friendships = new MutableFriendships();
        SocialPresenceService presence = new SocialPresenceService(playerId -> List.of(),
                (recipient, friendId, online) -> { });
        SocialProfileService profiles = new SocialProfileService(playerId -> playerId == 2L
                ? new SocialProfileRepository.StoredProfile(2L, "Offline", (short) 22, "Clan", 987_654L,
                        Instant.parse("2026-09-06T00:00:00Z"))
                : null);
        SocialV2ServerFacade facade = facade(friendships, presence, profiles);
        CapturingPlayer sender = player(1L, "Sender");
        CapturingPlayer target = player(2L, "Target");

        verifyOfflineProfileWire(facade, sender);
        presence.publish(sender);
        presence.publish(target);
        configureAuthoritativeLocation(sender);
        verifyLocationWireUsesServerCoordinates(facade, sender, target);
        verifyPrivateChatAndRemoveRace(facade, friendships, sender, target);
        System.out.println("SocialV2Phase4FacadeRegressionTest: PASS");
        // Player/Map construction starts legacy non-daemon workers in this server.
        System.exit(0);
    }

    private static void verifyOfflineProfileWire(SocialV2ServerFacade facade, CapturingPlayer sender)
            throws Exception {
        facade.handleV2Action(sender, SocialV2Protocol.PROFILE, request(writer -> writer.writeInt(2)));
        try (DataInputStream wire = wire(sender.onlySocialAction(SocialV2Protocol.PROFILE))) {
            assertEquals("profile action", SocialV2Protocol.PROFILE, wire.readUnsignedByte());
            assertEquals("profile result", SocialV2Protocol.RESULT_OK, wire.readUnsignedByte());
            assertEquals("profile id", 2, wire.readInt());
            assertEquals("profile head", (short) 22, wire.readShort());
            assertEquals("profile name", "Offline", wire.readUTF());
            assertEquals("profile clan", "Clan", wire.readUTF());
            assertEquals("profile activity", SocialProfileService.ACTIVITY_RECENT, wire.readUTF());
            assertEquals("profile raw power", 987_654L, wire.readLong());
            assertTrue("profile formatted power", !wire.readUTF().isBlank());
            assertTrue("offline profile flag", !wire.readBoolean());
            assertEquals("profile fully consumed", 0, wire.available());
        }
        sender.clearMessages();
    }

    private static void verifyLocationWireUsesServerCoordinates(SocialV2ServerFacade facade,
            CapturingPlayer sender, CapturingPlayer target) throws Exception {
        facade.handleV2Action(sender, SocialV2Protocol.SHARE_LOCATION, request(writer -> writer.writeInt(2)));
        assertLocationEvent("sender echo", sender.onlySocialAction(SocialV2Protocol.LOCATION_EVENT), 1, 150, 4, 321, 654);
        assertLocationEvent("target event", target.onlySocialAction(SocialV2Protocol.LOCATION_EVENT), 1, 150, 4, 321, 654);
        try (DataInputStream wire = wire(sender.onlySocialAction(SocialV2Protocol.SHARE_LOCATION))) {
            assertEquals("location acknowledgement action", SocialV2Protocol.SHARE_LOCATION, wire.readUnsignedByte());
            assertEquals("location acknowledgement result", SocialV2Protocol.RESULT_OK, wire.readUnsignedByte());
            assertEquals("location acknowledgement fully consumed", 0, wire.available());
        }
        sender.clearMessages();
        target.clearMessages();
    }

    private static void verifyPrivateChatAndRemoveRace(SocialV2ServerFacade facade, MutableFriendships friendships,
            CapturingPlayer sender, CapturingPlayer target) {
        Instant now = Instant.parse("2026-09-14T00:00:00Z");
        facade.handlePrivateChat(sender, 2, "  hello  ", now);
        assertTrue("sender receives server chat echo", sender.hasCommand(SocialV2Protocol.COMMAND_PRIVATE_CHAT_EVENT));
        assertTrue("target receives forwarded chat", target.hasCommand(SocialV2Protocol.COMMAND_PRIVATE_CHAT_EVENT));

        sender.clearMessages();
        target.clearMessages();
        facade.handlePrivateChat(sender, 2, "blocked-word-fuck", now.plusMillis(400L));
        assertTrue("filtered text never receives a sender echo",
                !sender.hasCommand(SocialV2Protocol.COMMAND_PRIVATE_CHAT_EVENT));
        assertTrue("filtered text never reaches the target",
                !target.hasCommand(SocialV2Protocol.COMMAND_PRIVATE_CHAT_EVENT));

        sender.clearMessages();
        target.clearMessages();
        facade.removeFriend(sender, 2);
        assertTrue("remove changes the authoritative relationship", !friendships.friends);
        sender.clearMessages();
        facade.handlePrivateChat(sender, 2, "after-remove", now.plusSeconds(1L));
        assertTrue("remove blocks a later sender echo", !sender.hasCommand(SocialV2Protocol.COMMAND_PRIVATE_CHAT_EVENT));
        assertTrue("remove blocks a later target delivery", !target.hasCommand(SocialV2Protocol.COMMAND_PRIVATE_CHAT_EVENT));
    }

    private static SocialV2ServerFacade facade(MutableFriendships friendships, SocialPresenceService presence,
            SocialProfileService profiles) {
        Properties properties = new Properties();
        properties.setProperty(SocialV2FeatureFlags.ENABLED_KEY, "true");
        SocialFriendPolicy policy = new SocialFriendPolicy();
        SocialRelationshipService relationships = new SocialRelationshipService(friendships, policy);
        SocialDirectoryService directory = new SocialDirectoryService(new EmptyDirectory(), playerId -> false);
        return new SocialV2ServerFacade(relationships, directory, SocialV2FeatureFlags.from(properties),
                new SocialActionRateLimiter(), policy, presence, profiles,
                new SocialChatRateLimiter(new SocialChatRateLimiter.Policy(
                        3, Duration.ofSeconds(1L), 10, Duration.ofMinutes(15L))), new SocialLocationCooldown());
    }

    private static CapturingPlayer player(long id, String name) {
        CapturingPlayer player = new CapturingPlayer();
        player.id = id;
        player.name = name;
        MySession session = new MySession();
        session.version = SocialV2Protocol.SOCIAL_V2_CLIENT_VERSION;
        player.setSession(session);
        return player;
    }

    private static void configureAuthoritativeLocation(Player player) {
        nro.models.map.Map map = new nro.models.map.Map(150, "Test", (byte) 0, (byte) 0, (byte) 0,
                (byte) 0, (byte) 0, new int[][] { { 0 } }, new int[] { 0 }, 0, 1, List.of());
        player.zone = new Zone(map, 4, 7);
        player.location = new Location();
        player.location.x = 321;
        player.location.y = 654;
    }

    private static Message request(MessageWriter writer) throws IOException {
        Message outgoing = new Message(SocialV2Protocol.COMMAND_SOCIAL);
        try {
            writer.write(outgoing.writer());
            return new Message((byte) SocialV2Protocol.COMMAND_SOCIAL, outgoing.getData());
        } finally {
            outgoing.cleanup();
        }
    }

    private static void assertLocationEvent(String label, byte[] payload, int senderId, int mapId, int zoneId,
            int x, int y) throws IOException {
        try (DataInputStream wire = wire(payload)) {
            assertEquals(label + " action", SocialV2Protocol.LOCATION_EVENT, wire.readUnsignedByte());
            assertEquals(label + " sender", senderId, wire.readInt());
            assertEquals(label + " map", (short) mapId, wire.readShort());
            assertEquals(label + " zone", (short) zoneId, wire.readShort());
            assertEquals(label + " x", (short) x, wire.readShort());
            assertEquals(label + " y", (short) y, wire.readShort());
            assertEquals(label + " fully consumed", 0, wire.available());
        }
    }

    private static DataInputStream wire(byte[] payload) {
        return new DataInputStream(new ByteArrayInputStream(payload));
    }

    private static void assertTrue(String label, boolean value) {
        if (!value) {
            throw new AssertionError(label);
        }
    }

    private static void assertEquals(String label, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + " expected=" + expected + " actual=" + actual);
        }
    }

    @FunctionalInterface
    private interface MessageWriter {
        void write(java.io.DataOutputStream writer) throws IOException;
    }

    private static final class CapturingPlayer extends Player {
        private final List<CapturedMessage> messages = new ArrayList<>();

        @Override
        public void sendMessage(Message message) {
            messages.add(new CapturedMessage(message.command, message.getData()));
        }

        private boolean hasCommand(int command) {
            return messages.stream().anyMatch(message -> message.command() == (byte) command);
        }

        private byte[] onlySocialAction(int action) {
            List<CapturedMessage> matches = messages.stream()
                    .filter(message -> message.command() == (byte) SocialV2Protocol.COMMAND_SOCIAL
                    && message.payload().length > 0 && Byte.toUnsignedInt(message.payload()[0]) == action)
                    .toList();
            if (matches.size() != 1) {
                throw new AssertionError("expected one social action " + action + " but got " + matches.size());
            }
            return matches.get(0).payload();
        }

        private void clearMessages() {
            messages.clear();
        }

        private record CapturedMessage(byte command, byte[] payload) {
        }
    }

    private static final class EmptyDirectory implements SocialDirectoryRepository {
        @Override
        public List<SearchEntry> search(long viewerId, String query, int offset, int limit) {
            return List.of();
        }

        @Override
        public List<InboxEntry> inbox(long receiverId, int offset, int limit, Instant now) {
            return List.of();
        }

        @Override
        public List<PlayerSummary> friends(long playerId) {
            return List.of();
        }

        @Override
        public int pendingCount(long receiverId, Instant now) {
            return 0;
        }
    }

    private static final class MutableFriendships implements SocialRelationshipRepository {
        private boolean friends = true;

        @Override
        public <T> T inTransaction(TransactionWork<T> work) throws java.sql.SQLException {
            return work.execute(new Transaction() {
                @Override
                public boolean lockExistingPlayers(SocialFriendPolicy.FriendshipPair pair) {
                    return true;
                }

                @Override
                public boolean hasFriendship(SocialFriendPolicy.FriendshipPair pair) {
                    return friends;
                }

                @Override
                public int friendshipCount(long playerId) {
                    return friends ? 1 : 0;
                }

                @Override
                public void addFriendship(SocialFriendPolicy.FriendshipPair pair, Instant createdAt) {
                    friends = true;
                }

                @Override
                public boolean removeFriendship(SocialFriendPolicy.FriendshipPair pair) {
                    boolean existed = friends;
                    friends = false;
                    return existed;
                }

                @Override
                public PendingFriendRequest findPendingByPairForUpdate(SocialFriendPolicy.FriendshipPair pair) {
                    return null;
                }

                @Override
                public PendingFriendRequest findPendingById(long requestId) {
                    return null;
                }

                @Override
                public PendingFriendRequest findPendingByIdForUpdate(long requestId) {
                    return null;
                }

                @Override
                public long insertPending(SocialFriendPolicy.FriendshipPair pair, long senderId, long receiverId,
                        Instant createdAt, Instant expiresAt) {
                    return 1L;
                }

                @Override
                public boolean deletePending(long requestId) {
                    return true;
                }

                @Override
                public int deleteExpiredForPair(SocialFriendPolicy.FriendshipPair pair, Instant now) {
                    return 0;
                }

                @Override
                public int deleteExpired(Instant now) {
                    return 0;
                }
            });
        }
    }
}
