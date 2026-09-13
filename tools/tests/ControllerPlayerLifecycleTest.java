package nro.models.server;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import nro.models.network.MySession;
import nro.models.network.SessionCloseCause;
import nro.models.player.Player;

/** Regression coverage for player ownership during command dispatch. */
public final class ControllerPlayerLifecycleTest {

    private ControllerPlayerLifecycleTest() {
    }

    public static void main(String[] args) throws Exception {
        GameRuntime.installTemplatesForTesting(List.of(), List.of(), List.of());
        TestSession session = new TestSession();
        Player player = new Player();
        player.id = 100_004;
        assertTrue(session.bind(player), "test player must be published to the session");

        AtomicBoolean intactInsideCommand = new AtomicBoolean(false);
        boolean remainedActive = Controller.runCommandIfPlayerOwned(session, player, () -> {
            session.close(SessionCloseCause.SLOW_CONSUMER);
            intactInsideCommand.set(player.location != null
                    && player.idMark != null
                    && player.getSession() == session);
        });

        assertTrue(intactInsideCommand.get(),
                "session close must defer player disposal until command dispatch exits");
        assertFalse(remainedActive, "closed session must not remain active after the command");
        assertEquals(1, session.removalCount.get(), "deferred teardown must run exactly once");
        assertTrue(player.location == null, "player must be disposed after the command exits");
        assertTrue(player.idMark == null, "disposed player must release idMark after the command exits");
        System.out.println("CONTROLLER_PLAYER_LIFECYCLE_TEST_OK");
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
