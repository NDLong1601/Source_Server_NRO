package nro.models.server;

import java.util.concurrent.atomic.AtomicInteger;
import nro.models.player.Achievement;
import nro.models.player.Player;

/** Regression coverage for the production disconnect lifecycle wrapper. */
public final class ClientRemovalLifecycleTest {

    private ClientRemovalLifecycleTest() {
    }

    public static void main(String[] args) {
        Player player = new Player();
        player.id = 100_002;
        Achievement achievement = player.achievement;
        AtomicInteger cleanupAttempts = new AtomicInteger();
        AtomicInteger saveAttempts = new AtomicInteger();

        Client.executeRemovalLifecycle(player, () -> {
            cleanupAttempts.incrementAndGet();
            assertFalse(achievement.isAcceptingClaims(),
                    "claims must be closed before registry/teardown cleanup");
            throw new RuntimeException("simulated registry cleanup failure");
        }, () -> {
            saveAttempts.incrementAndGet();
            assertFalse(achievement.isAcceptingClaims(),
                    "claims must remain closed when persistence starts");
        });

        assertEquals(1, cleanupAttempts.get(), "cleanup must run exactly once");
        assertEquals(1, saveAttempts.get(), "save must be attempted exactly once");
        assertFalse(achievement.isAcceptingClaims(), "achievement must remain closed");

        AtomicInteger nullActions = new AtomicInteger();
        Client.executeRemovalLifecycle(null, nullActions::incrementAndGet, nullActions::incrementAndGet);
        assertEquals(0, nullActions.get(), "null player must not run cleanup or save");

        System.out.println("CLIENT_REMOVAL_LIFECYCLE_TEST_OK");
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
}
