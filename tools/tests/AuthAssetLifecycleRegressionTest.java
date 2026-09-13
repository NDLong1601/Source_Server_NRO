package nro.models.server.dispatch;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import nro.models.network.Message;
import nro.models.network.MySession;
import nro.models.network.Sender;
import nro.models.network.Session;
import nro.models.network.SessionCloseCause;
import nro.models.player.Player;
import nro.models.server.GameRuntime;

/** Regression coverage for initial-map teardown and optional asset backpressure. */
public final class AuthAssetLifecycleRegressionTest {

    private AuthAssetLifecycleRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        initialMapLoadDefersPlayerDisposalUntilTheLifecycleStepFinishes();
        optionalAssetBackpressureKeepsTheSessionAlive();
        System.out.println("AUTH_ASSET_LIFECYCLE_REGRESSION_TEST_OK");
    }

    private static void initialMapLoadDefersPlayerDisposalUntilTheLifecycleStepFinishes() {
        GameRuntime.installTemplatesForTesting(List.of(), List.of(), List.of());
        TestSession session = new TestSession();
        Player player = new Player();
        player.id = 100_003;
        assertTrue(session.bind(player), "test player must be published to the session");

        AtomicBoolean playerWasIntactAfterClose = new AtomicBoolean(false);
        boolean remainedActive = AuthAssetCommandHandler.runInitialMapLoad(
                session, player, () -> {
                    session.close(SessionCloseCause.SLOW_CONSUMER);
                    playerWasIntactAfterClose.set(
                            player.location != null && player.getSession() == session);
                });

        assertTrue(playerWasIntactAfterClose.get(),
                "player fields must stay alive until the initial-map step exits");
        assertFalse(remainedActive, "closed session must not report an active lifecycle step");
        assertEquals(1, session.removalCount.get(), "deferred teardown must run once");
        assertTrue(player.location == null, "player must be disposed after the guarded step");
        assertTrue(player.getSession() == null, "disposed player must release its session");
    }

    private static void optionalAssetBackpressureKeepsTheSessionAlive() throws Exception {
        int originalMessages = Sender.getDefaultMaxQueueMessages();
        long originalBytes = Sender.getDefaultMaxQueueBytes();
        Sender.configureDefaultLimits(4, 32);
        Session session = new Session();
        try {
            Message first = messageWithPayload(16);
            assertTrue(session.trySendMessage(first, 8),
                    "first optional frame should fit while preserving queue headroom");
            first.cleanup();

            Message optionalOverflow = messageWithPayload(16);
            assertFalse(session.trySendMessage(optionalOverflow, 8),
                    "optional frame must be rejected before consuming reserved headroom");
            optionalOverflow.cleanup();
            assertTrue(session.isConnected(),
                    "rejecting an optional preload must not disconnect the client");
            assertEquals(1, session.getNumMessages(),
                    "rejected optional frame must not change queue accounting");

            Message requiredOverflow = messageWithPayload(16);
            session.sendMessage(requiredOverflow);
            requiredOverflow.cleanup();
            assertTrue(session.isClosed(),
                    "required packet overflow must retain the fail-closed policy");
            assertTrue(session.getCloseCause() == SessionCloseCause.SLOW_CONSUMER,
                    "required packet overflow must preserve the close cause");
        } finally {
            session.close(SessionCloseCause.CLIENT_DISCONNECT);
            Sender.configureDefaultLimits(originalMessages, originalBytes);
        }
    }

    private static Message messageWithPayload(int size) throws Exception {
        Message message = new Message(1);
        for (int i = 0; i < size; i++) {
            message.writer().writeByte(i);
        }
        return message;
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static final class TestSession extends MySession {

        private final AtomicInteger removalCount = new AtomicInteger();

        private boolean bind(Player player) {
            return publishPlayerIfActive(player, () -> {
            });
        }

        @Override
        protected void removePlayerOnClose(Player closingPlayer) {
            removalCount.incrementAndGet();
            closingPlayer.dispose();
        }
    }
}
