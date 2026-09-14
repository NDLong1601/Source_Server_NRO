package nro.models.social;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import nro.models.network.Message;
import nro.models.network.MySession;
import nro.models.player.Player;

/** Wire-level Phase-3 checks through the server facade, without a live server. */
public final class SocialV2ServerFacadeRegressionTest {

    private SocialV2ServerFacadeRegressionTest() {
    }

    public static void main(String[] args) {
        try {
            verifySearchAndInboxWirePayloads();
            verifyFeatureGateAndActionZeroCapabilityTail();
            verifyRateLimitWireErrorAndGenericMessage();
            verifyDuplicateRequestUsesDuplicateErrorAndGenericMessage();
            verifyWorstCasePagePacketBudget();
            System.out.println("SocialV2ServerFacadeRegressionTest: PASS");
        } catch (Throwable failure) {
            failure.printStackTrace(System.err);
            System.exit(1);
        }
        // Player construction initializes legacy runtime registries that own non-daemon workers.
        // This isolated wire test must not keep the test runner alive after its assertions finish.
        System.exit(0);
    }

    private static void verifySearchAndInboxWirePayloads() throws Exception {
        FakeDirectory directory = new FakeDirectory();
        directory.searchEntries.add(new SocialDirectoryRepository.SearchEntry(summary(2L, "An", (short) 12),
                SocialDirectoryRepository.Relationship.FRIEND));
        directory.searchEntries.add(new SocialDirectoryRepository.SearchEntry(summary(3L, "Anh", (short) 13),
                SocialDirectoryRepository.Relationship.PENDING));
        Instant expiry = Instant.now().plusSeconds(60L);
        directory.inboxEntries.add(new SocialDirectoryRepository.InboxEntry(91L, summary(3L, "Anh", (short) 13),
                expiry));

        CapturingPlayer player = v2Player();
        SocialV2ServerFacade facade = enabledFacade(directory);
        facade.handleV2Action(player, SocialV2Protocol.SEARCH, request(writer -> {
            writer.writeInt(71);
            writer.writeInt(0);
            writer.writeUTF("an");
        }));
        try (DataInputStream wire = wire(player.onlyMessage())) {
            assertEquals("search action", SocialV2Protocol.SEARCH, wire.readUnsignedByte());
            assertEquals("search result", SocialV2Protocol.RESULT_OK, wire.readUnsignedByte());
            assertEquals("search token", 71, wire.readInt());
            assertEquals("search cursor", 0, wire.readInt());
            assertTrue("search hasMore", !wire.readBoolean());
            assertEquals("search count", 2, wire.readUnsignedByte());
            assertEntry(wire, 2L, (short) 12, "An", 0);
            assertEntry(wire, 3L, (short) 13, "Anh", 1);
            assertEquals("search payload fully consumed", 0, wire.available());
        }

        player.clearMessages();
        facade.handleV2Action(player, SocialV2Protocol.INBOX, request(writer -> {
            writer.writeInt(72);
            writer.writeInt(0);
        }));
        try (DataInputStream wire = wire(player.onlyMessage())) {
            assertEquals("inbox action", SocialV2Protocol.INBOX, wire.readUnsignedByte());
            assertEquals("inbox result", SocialV2Protocol.RESULT_OK, wire.readUnsignedByte());
            assertEquals("inbox token", 72, wire.readInt());
            assertEquals("inbox cursor", 0, wire.readInt());
            assertTrue("inbox hasMore", !wire.readBoolean());
            assertEquals("inbox count", 1, wire.readUnsignedByte());
            assertEquals("inbox request", 91L, wire.readLong());
            assertEquals("inbox sender", 3, wire.readInt());
            assertEquals("inbox head", (short) 13, wire.readShort());
            assertEquals("inbox sender name", "Anh", wire.readUTF());
            assertEquals("inbox expiry", expiry.toEpochMilli(), wire.readLong());
            assertEquals("inbox payload fully consumed", 0, wire.available());
        }
    }

    private static void verifyFeatureGateAndActionZeroCapabilityTail() throws Exception {
        FakeDirectory directory = new FakeDirectory();
        directory.pendingCount = 3;
        CapturingPlayer player = v2Player();
        SocialV2ServerFacade disabled = facade(directory, false);
        disabled.handleV2Action(player, SocialV2Protocol.SEARCH, request(writer -> {
            writer.writeInt(1);
            writer.writeInt(0);
            writer.writeUTF("an");
        }));
        try (DataInputStream wire = wire(player.onlyMessage())) {
            assertEquals("disabled action", SocialV2Protocol.SEARCH, wire.readUnsignedByte());
            assertEquals("disabled result", SocialV2Protocol.RESULT_ERROR, wire.readUnsignedByte());
            assertEquals("disabled error", SocialV2Protocol.ErrorCode.FEATURE_DISABLED.wireValue(),
                    wire.readUnsignedByte());
        }

        player.clearMessages();
        SocialV2ServerFacade enabled = enabledFacade(directory);
        enabled.handleV2Action(player, SocialV2Protocol.SEARCH, request(writer -> {
            writer.writeInt(2);
            writer.writeInt(0);
            writer.writeUTF("an");
            writer.writeByte(1);
        }));
        try (DataInputStream wire = wire(player.onlyMessage())) {
            assertEquals("trailing-data action", SocialV2Protocol.SEARCH, wire.readUnsignedByte());
            assertEquals("trailing-data result", SocialV2Protocol.RESULT_ERROR, wire.readUnsignedByte());
            assertEquals("trailing-data error", SocialV2Protocol.ErrorCode.MALFORMED.wireValue(),
                    wire.readUnsignedByte());
        }

        player.clearMessages();
        enabled.sendFriendList(player);
        try (DataInputStream wire = wire(player.onlyMessage())) {
            assertEquals("list action", SocialV2Protocol.OPEN_LIST, wire.readUnsignedByte());
            assertEquals("empty list count", 0, wire.readUnsignedByte());
            assertEquals("protocol version", SocialV2Protocol.PROTOCOL_VERSION, wire.readUnsignedByte());
            assertEquals("capability", SocialV2Protocol.CAPABILITY_SOCIAL_V2, wire.readInt());
            assertEquals("friend limit", SocialV2Protocol.MAX_FRIENDS, wire.readUnsignedByte());
            assertEquals("online count", 0, wire.readUnsignedByte());
            assertEquals("pending count", 3, wire.readUnsignedShort());
            assertEquals("list payload fully consumed", 0, wire.available());
        }
    }

    private static void verifyWorstCasePagePacketBudget() throws Exception {
        FakeDirectory directory = new FakeDirectory();
        // player.name is VARCHAR(20); a supplementary scalar occupies two UTF-16 chars and six modified-UTF bytes.
        String maximalName = "\uD83D\uDE00".repeat(20);
        for (long id = 2L; id <= SocialV2Protocol.PAGE_SIZE + 1L; id++) {
            directory.searchEntries.add(new SocialDirectoryRepository.SearchEntry(summary(id, maximalName, (short) id),
                    SocialDirectoryRepository.Relationship.CAN_ADD));
        }
        CapturingPlayer player = v2Player();
        enabledFacade(directory).handleV2Action(player, SocialV2Protocol.SEARCH, request(writer -> {
            writer.writeInt(88);
            writer.writeInt(0);
            writer.writeUTF("an");
        }));
        byte[] searchPage = player.onlyMessage();
        assertTrue("page has data", searchPage.length > 0);
        SocialV2Protocol.requirePacketPayloadLength(searchPage.length);
        int maximalLegacyEntry = Integer.BYTES + (Short.BYTES * 4) + 1
                + Short.BYTES + SocialV2Protocol.modifiedUtfLength(maximalName)
                + 1 + Short.BYTES + SocialV2Protocol.modifiedUtfLength("9".repeat(25));
        int worstCaseFriendList = 2 + (SocialV2Protocol.MAX_FRIENDS * maximalLegacyEntry) + 9;
        assertTrue("100 friend rows with max valid UTF names fit the social packet cap",
                worstCaseFriendList <= SocialV2Protocol.MAX_PACKET_BYTES);
    }

    private static void verifyRateLimitWireErrorAndGenericMessage() throws Exception {
        FakeDirectory directory = new FakeDirectory();
        CapturingPlayer player = v2Player();
        SocialActionRateLimiter limiter = new SocialActionRateLimiter(new SocialActionRateLimiter.Policy(
                1, Duration.ofMinutes(1L), 1, Duration.ofMinutes(1L), Duration.ZERO, 10, Duration.ofMinutes(1L)));
        SocialV2ServerFacade facade = facade(directory, true, limiter);
        facade.handleV2Action(player, SocialV2Protocol.SEARCH, request(writer -> {
            writer.writeInt(90);
            writer.writeInt(0);
            writer.writeUTF("an");
        }));
        player.clearMessages();
        facade.handleV2Action(player, SocialV2Protocol.SEARCH, request(writer -> {
            writer.writeInt(91);
            writer.writeInt(0);
            writer.writeUTF("an");
        }));
        try (DataInputStream wire = wire(player.onlySocialMessage())) {
            assertEquals("rate limited action", SocialV2Protocol.SEARCH, wire.readUnsignedByte());
            assertEquals("rate limited result", SocialV2Protocol.RESULT_ERROR, wire.readUnsignedByte());
            assertEquals("rate limited error", SocialV2Protocol.ErrorCode.RATE_LIMITED.wireValue(),
                    wire.readUnsignedByte());
        }
        assertTrue("rate limited player receives generic notification", player.hasCommand(-25));
    }

    private static void verifyDuplicateRequestUsesDuplicateErrorAndGenericMessage() throws Exception {
        CapturingPlayer player = v2Player();
        SocialV2ServerFacade facade = facade(new FakeDirectory(), true,
                new SocialRelationshipService(new AlreadyFriendsRepository(), new SocialFriendPolicy()),
                new SocialActionRateLimiter());
        facade.handleV2Action(player, SocialV2Protocol.SEND_REQUEST, request(writer -> writer.writeInt(2)));
        try (DataInputStream wire = wire(player.onlySocialMessage())) {
            assertEquals("duplicate action", SocialV2Protocol.SEND_REQUEST, wire.readUnsignedByte());
            assertEquals("duplicate result", SocialV2Protocol.RESULT_ERROR, wire.readUnsignedByte());
            assertEquals("duplicate error", SocialV2Protocol.ErrorCode.DUPLICATE.wireValue(),
                    wire.readUnsignedByte());
        }
        assertTrue("duplicate player receives generic notification", player.hasCommand(-25));
    }

    private static SocialV2ServerFacade enabledFacade(FakeDirectory directory) {
        return facade(directory, true);
    }

    private static SocialV2ServerFacade facade(FakeDirectory directory, boolean enabled) {
        return facade(directory, enabled, new SocialActionRateLimiter());
    }

    private static SocialV2ServerFacade facade(FakeDirectory directory, boolean enabled,
            SocialActionRateLimiter rateLimiter) {
        return facade(directory, enabled, unusedRelationshipService(), rateLimiter);
    }

    private static SocialV2ServerFacade facade(FakeDirectory directory, boolean enabled,
            SocialRelationshipService relationships, SocialActionRateLimiter rateLimiter) {
        Properties values = new Properties();
        values.setProperty(SocialV2FeatureFlags.ENABLED_KEY, Boolean.toString(enabled));
        return new SocialV2ServerFacade(relationships, new SocialDirectoryService(directory, playerId -> false),
                SocialV2FeatureFlags.from(values), rateLimiter);
    }

    private static SocialRelationshipService unusedRelationshipService() {
        SocialRelationshipRepository unusedRelationships = new SocialRelationshipRepository() {
            @Override
            public <T> T inTransaction(TransactionWork<T> work) {
                throw new AssertionError("Directory wire test must not change relationships");
            }
        };
        return new SocialRelationshipService(unusedRelationships, new SocialFriendPolicy());
    }

    private static CapturingPlayer v2Player() {
        CapturingPlayer player = new CapturingPlayer();
        player.id = 1L;
        player.name = "Viewer";
        MySession session = new MySession();
        session.version = SocialV2Protocol.SOCIAL_V2_CLIENT_VERSION;
        player.setSession(session);
        return player;
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

    private static DataInputStream wire(byte[] payload) {
        return new DataInputStream(new ByteArrayInputStream(payload));
    }

    private static SocialDirectoryRepository.PlayerSummary summary(long id, String name, short head) {
        return new SocialDirectoryRepository.PlayerSummary(id, name, head);
    }

    private static void assertEntry(DataInputStream wire, long id, short head, String name, int relationship)
            throws IOException {
        assertEquals("entry id", (int) id, wire.readInt());
        assertEquals("entry head", head, wire.readShort());
        assertEquals("entry name", name, wire.readUTF());
        assertEquals("entry relationship", relationship, wire.readUnsignedByte());
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

        private byte[] onlyMessage() {
            return onlySocialMessage();
        }

        private byte[] onlySocialMessage() {
            List<CapturedMessage> socialMessages = messages.stream()
                    .filter(message -> message.command() == (byte) SocialV2Protocol.COMMAND_SOCIAL).toList();
            if (socialMessages.size() != 1) {
                throw new AssertionError("expected exactly one social packet but got " + socialMessages.size());
            }
            return socialMessages.get(0).payload();
        }

        private boolean hasCommand(int command) {
            return messages.stream().anyMatch(message -> message.command() == (byte) command);
        }

        private void clearMessages() {
            messages.clear();
        }

        private record CapturedMessage(byte command, byte[] payload) {
        }
    }

    private static final class FakeDirectory implements SocialDirectoryRepository {
        private final List<SearchEntry> searchEntries = new ArrayList<>();
        private final List<InboxEntry> inboxEntries = new ArrayList<>();
        private int pendingCount;

        @Override
        public List<SearchEntry> search(long viewerId, String query, int offset, int limit) {
            return slice(searchEntries, offset, limit);
        }

        @Override
        public List<InboxEntry> inbox(long receiverId, int offset, int limit, Instant now) {
            return slice(inboxEntries, offset, limit);
        }

        @Override
        public List<PlayerSummary> friends(long playerId) {
            return List.of();
        }

        @Override
        public int pendingCount(long receiverId, Instant now) {
            return pendingCount;
        }

        private static <T> List<T> slice(List<T> rows, int offset, int limit) {
            if (offset >= rows.size()) {
                return List.of();
            }
            return List.copyOf(rows.subList(offset, Math.min(rows.size(), offset + limit)));
        }
    }

    private static final class AlreadyFriendsRepository implements SocialRelationshipRepository {
        @Override
        public <T> T inTransaction(TransactionWork<T> work) throws SQLException {
            return work.execute(new Transaction() {
                @Override
                public boolean lockExistingPlayers(SocialFriendPolicy.FriendshipPair pair) {
                    return true;
                }

                @Override
                public boolean hasFriendship(SocialFriendPolicy.FriendshipPair pair) {
                    return true;
                }

                @Override
                public int friendshipCount(long playerId) {
                    return 1;
                }

                @Override
                public void addFriendship(SocialFriendPolicy.FriendshipPair pair, Instant createdAt) {
                    throw new AssertionError("duplicate request must not create friendship");
                }

                @Override
                public boolean removeFriendship(SocialFriendPolicy.FriendshipPair pair) {
                    throw new AssertionError("not used");
                }

                @Override
                public PendingFriendRequest findPendingByPairForUpdate(SocialFriendPolicy.FriendshipPair pair) {
                    throw new AssertionError("not used");
                }

                @Override
                public PendingFriendRequest findPendingById(long requestId) {
                    throw new AssertionError("not used");
                }

                @Override
                public PendingFriendRequest findPendingByIdForUpdate(long requestId) {
                    throw new AssertionError("not used");
                }

                @Override
                public long insertPending(SocialFriendPolicy.FriendshipPair pair, long senderId, long receiverId,
                        Instant createdAt, Instant expiresAt) {
                    throw new AssertionError("duplicate request must not create pending state");
                }

                @Override
                public boolean deletePending(long requestId) {
                    throw new AssertionError("not used");
                }

                @Override
                public int deleteExpiredForPair(SocialFriendPolicy.FriendshipPair pair, Instant now) {
                    return 0;
                }

                @Override
                public int deleteExpired(Instant now) {
                    throw new AssertionError("not used");
                }
            });
        }
    }
}
