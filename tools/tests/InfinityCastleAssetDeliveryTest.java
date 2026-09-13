package nro.models.data;

import nro.models.network.Message;
import nro.models.network.MySession;
import nro.models.network.Sender;

/** Regression coverage for the large Infinity Castle asset burst. */
public final class InfinityCastleAssetDeliveryTest {

    private static final int EXPECTED_ASSET_PACKETS = 12;

    private InfinityCastleAssetDeliveryTest() {
    }

    public static void main(String[] args) {
        for (int zoom = 1; zoom <= 4; zoom++) {
            RecordingSession session = new RecordingSession((byte) zoom);

            DataGame.preloadInfinityCastleAssets(session);

            assertEquals(0, session.queuedPackets,
                    "Infinity Castle preload must not consume the bounded async queue at x" + zoom);
            assertEquals(EXPECTED_ASSET_PACKETS, session.directPackets,
                    "all Infinity Castle assets must use socket-backed delivery at x" + zoom);
            if (zoom >= 2) {
                assertTrue(session.directPayloadBytes > Sender.getDefaultMaxQueueBytes(),
                        "the x" + zoom + " preload must reproduce a burst larger than the sender queue");
            }
        }
        onDemandLargeBackgroundUsesSocketBackpressure();
        System.out.println("INFINITY_CASTLE_ASSET_DELIVERY_TEST_OK");
    }

    private static void onDemandLargeBackgroundUsesSocketBackpressure() {
        RecordingSession session = new RecordingSession((byte) 4);

        DataGame.sendItemBGTemplate(session, 516);

        assertEquals(0, session.queuedPackets,
                "a background larger than the sender queue must not be enqueued");
        assertEquals(1, session.directPackets,
                "an on-demand large background must use socket-backed delivery");
        assertTrue(session.directPayloadBytes > Sender.getDefaultMaxQueueBytes(),
                "the x4 background must remain larger than the sender queue in this regression");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static final class RecordingSession extends MySession {

        private int queuedPackets;
        private int directPackets;
        private long directPayloadBytes;

        private RecordingSession(byte zoomLevel) {
            this.zoomLevel = zoomLevel;
        }

        @Override
        public boolean isAssetReady() {
            return true;
        }

        @Override
        public void sendMessage(Message message) {
            queuedPackets++;
        }

        @Override
        public void doSendMessage(Message message) {
            directPackets++;
            byte[] payload = message.getData();
            directPayloadBytes += payload == null ? 0L : payload.length;
        }
    }
}
