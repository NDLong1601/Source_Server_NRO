package nro.models.network;

import java.util.Arrays;

public final class QueuedFrame {

    public static final int MAX_STANDARD_PAYLOAD_LENGTH = 0xFFFF;
    public static final int MAX_EXTENDED_PAYLOAD_LENGTH = 0xFFFFFF;

    private final byte command;
    private final byte[] payload;
    private final int wireSize;

    public QueuedFrame(byte command, byte[] data) {
        this.command = command;
        if (data != null && data.length > 0) {
            this.payload = Arrays.copyOf(data, data.length);
        } else {
            this.payload = new byte[0];
        }
        int headerSize = isExtendedCommand(command) ? 3 : 2;
        this.wireSize = 1 + headerSize + this.payload.length;
    }

    public static boolean isExtendedCommand(byte command) {
        return command == -32 || command == -66 || command == -74 || command == 11
                || command == -67 || command == -87 || command == 66;
    }

    public static int getMaxPayloadLength(byte command) {
        return isExtendedCommand(command)
                ? MAX_EXTENDED_PAYLOAD_LENGTH
                : MAX_STANDARD_PAYLOAD_LENGTH;
    }

    public byte getCommand() {
        return command;
    }

    public byte[] getPayload() {
        return Arrays.copyOf(payload, payload.length);
    }

    public int getPayloadLength() {
        return payload.length;
    }

    public int getWireSize() {
        return wireSize;
    }

    public Message toMessage() {
        return new Message(command, payload);
    }
}
