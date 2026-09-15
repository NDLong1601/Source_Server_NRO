package nro.models.social;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable Social V2 wire and limit contract. */
public final class SocialV2Protocol {

    public static final int COMMAND_SOCIAL = -80;
    public static final int COMMAND_PRIVATE_CHAT_REQUEST = -72;
    public static final int COMMAND_PRIVATE_CHAT_EVENT = 92;

    /** First client version allowed to parse the additive action-0 capability tail. */
    public static final int SOCIAL_V2_CLIENT_VERSION = 223;
    public static final int PROTOCOL_VERSION = 1;
    public static final int CAPABILITY_SOCIAL_V2 = 0x0000_0001;
    public static final int RESULT_OK = 0;
    public static final int RESULT_ERROR = 1;

    public static final int OPEN_LIST = 0;
    public static final int MAKE_FRIEND = 1;
    public static final int REMOVE_FRIEND = 2;
    public static final int PRESENCE = 3;
    public static final int SEARCH = 4;
    public static final int SEND_REQUEST = 5;
    public static final int INBOX = 6;
    public static final int ACCEPT_REQUEST = 7;
    public static final int REJECT_REQUEST = 8;
    public static final int PROFILE = 9;
    public static final int SHARE_LOCATION = 10;
    public static final int LOCATION_EVENT = 11;
    public static final int PENDING_COUNT = 12;

    public static final int PAGE_SIZE = 20;
    public static final int MAX_FRIENDS = 100;
    public static final int REQUEST_EXPIRY_DAYS = 30;
    public static final int SEARCH_MIN_CODE_POINTS = 2;
    public static final int SEARCH_MAX_CODE_POINTS = 32;
    public static final int MAX_CHAT_CODE_POINTS = 80;
    public static final long LOCATION_COOLDOWN_MILLIS = 120_000L;
    public static final int MAX_PACKET_BYTES = 65_535;
    public static final int MAX_MODIFIED_UTF_BYTES = 65_535;

    private static final Map<Integer, String> ACTION_NAMES = buildActionNames();

    private SocialV2Protocol() {
    }

    public static boolean isV2ClientVersion(int clientVersion) {
        return clientVersion >= SOCIAL_V2_CLIENT_VERSION;
    }

    public static boolean isLegacyFriendAction(int action) {
        return action == OPEN_LIST || action == MAKE_FRIEND || action == REMOVE_FRIEND;
    }

    public static boolean isV2Action(int action) {
        return action >= PRESENCE && action <= PENDING_COUNT;
    }

    public enum ErrorCode {
        FEATURE_DISABLED(1),
        CLIENT_TOO_OLD(2),
        MALFORMED(3),
        UNSUPPORTED_ACTION(4),
        NOT_FRIENDS(5),
        NOT_FOUND(6),
        SELF_TARGET(7),
        FRIEND_LIMIT(8),
        DUPLICATE(9),
        EXPIRED(10),
        OFFLINE(11),
        RATE_LIMITED(12),
        INVALID_TEXT(13),
        LOCATION_COOLDOWN(14),
        PACKET_TOO_LARGE(15);

        private final int wireValue;

        ErrorCode(int wireValue) {
            this.wireValue = wireValue;
        }

        public int wireValue() {
            return wireValue;
        }

        public static ErrorCode fromWireValue(int wireValue) {
            for (ErrorCode value : values()) {
                if (value.wireValue == wireValue) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Unsupported social-v2 error code: " + wireValue);
        }
    }

    public static String actionName(int action) {
        return ACTION_NAMES.get(action);
    }

    public static Map<Integer, String> actionNames() {
        return ACTION_NAMES;
    }

    /** Mirrors DataOutputStream.writeUTF's modified-UTF payload length (without its u16 prefix). */
    public static int modifiedUtfLength(String value) {
        if (value == null) {
            throw new IllegalArgumentException("UTF value must not be null");
        }
        long length = 0L;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            length += character >= 0x0001 && character <= 0x007F ? 1L
                    : character <= 0x07FF ? 2L : 3L;
            if (length > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Modified UTF length overflows int");
            }
        }
        return (int) length;
    }

    public static boolean fitsModifiedUtf(String value, int maxCodePoints) {
        if (value == null || maxCodePoints < 0) {
            return false;
        }
        return value.codePointCount(0, value.length()) <= maxCodePoints
                && modifiedUtfLength(value) <= MAX_MODIFIED_UTF_BYTES;
    }

    /** Validates Message.getData() bytes; the outer command/frame header is not included. */
    public static void requirePacketPayloadLength(long payloadLength) {
        if (payloadLength < 0L || payloadLength > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("Social packet payload must be within 0.."
                    + MAX_PACKET_BYTES + " bytes");
        }
    }

    private static Map<Integer, String> buildActionNames() {
        LinkedHashMap<Integer, String> actions = new LinkedHashMap<>();
        actions.put(OPEN_LIST, "OPEN_LIST");
        actions.put(MAKE_FRIEND, "MAKE_FRIEND");
        actions.put(REMOVE_FRIEND, "REMOVE_FRIEND");
        actions.put(PRESENCE, "PRESENCE");
        actions.put(SEARCH, "SEARCH");
        actions.put(SEND_REQUEST, "SEND_REQUEST");
        actions.put(INBOX, "INBOX");
        actions.put(ACCEPT_REQUEST, "ACCEPT_REQUEST");
        actions.put(REJECT_REQUEST, "REJECT_REQUEST");
        actions.put(PROFILE, "PROFILE");
        actions.put(SHARE_LOCATION, "SHARE_LOCATION");
        actions.put(LOCATION_EVENT, "LOCATION_EVENT");
        actions.put(PENDING_COUNT, "PENDING_COUNT");
        return Collections.unmodifiableMap(actions);
    }
}
