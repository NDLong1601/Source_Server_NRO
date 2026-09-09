package nro.models.player;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

/** Versioned persisted event state with tolerant legacy-array migration. */
public final class PlayerEventState {

    public static final int SCHEMA_VERSION = 1;
    private final int[] eventPoints = new int[6];
    private final boolean[] rewardClaims = new boolean[4];
    private boolean freeGemClaimAvailable = true;
    private boolean clanCapsuleClaimAvailable = true;
    private LocalDateTime lastCheckIn;

    public static PlayerEventState fromJson(String json) {
        PlayerEventState state = new PlayerEventState();
        if (json == null || json.isBlank()) {
            return state;
        }
        Object parsed = JSONValue.parse(json);
        if (parsed instanceof JSONArray legacy) {
            state.readLegacyEvent(legacy);
            return state;
        }
        if (!(parsed instanceof JSONObject object)) {
            return state;
        }
        int version = intValue(object.get("schemaVersion"), 0);
        if (version > SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported player event schemaVersion " + version);
        }
        Object points = object.get("eventPoints");
        if (points instanceof JSONArray values) {
            for (int index = 0; index < state.eventPoints.length && index < values.size(); index++) {
                state.eventPoints[index] = intValue(values.get(index), 0);
            }
        }
        Object claims = object.get("rewardClaims");
        if (claims instanceof JSONArray values) {
            for (int index = 0; index < state.rewardClaims.length && index < values.size(); index++) {
                state.rewardClaims[index] = booleanValue(values.get(index), false);
            }
        }
        state.freeGemClaimAvailable = booleanValue(object.get("freeGemClaimAvailable"), true);
        state.clanCapsuleClaimAvailable = booleanValue(object.get("clanCapsuleClaimAvailable"), true);
        Object checkIn = object.get("lastCheckIn");
        if (checkIn != null && !String.valueOf(checkIn).isBlank()) {
            state.lastCheckIn = LocalDateTime.parse(String.valueOf(checkIn));
        }
        return state;
    }

    public void applyDailyClaimJson(String json) {
        if (json == null || json.isBlank()) {
            return;
        }
        Object parsed = JSONValue.parse(json);
        if (parsed instanceof JSONArray legacy) {
            freeGemClaimAvailable = intValue(valueAt(legacy, 0), 1) != 0;
            clanCapsuleClaimAvailable = intValue(valueAt(legacy, 1), 1) != 0;
            Object checkIn = valueAt(legacy, 2);
            lastCheckIn = checkIn == null ? null : LocalDateTime.parse(String.valueOf(checkIn));
        } else if (parsed instanceof JSONObject object) {
            int version = intValue(object.get("schemaVersion"), 0);
            if (version > SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported daily claim schemaVersion " + version);
            }
            freeGemClaimAvailable = booleanValue(object.get("freeGemClaimAvailable"), true);
            clanCapsuleClaimAvailable = booleanValue(object.get("clanCapsuleClaimAvailable"), true);
            Object checkIn = object.get("lastCheckIn");
            lastCheckIn = checkIn == null ? null : LocalDateTime.parse(String.valueOf(checkIn));
        }
    }

    @SuppressWarnings("unchecked")
    public String toJson() {
        JSONObject object = new JSONObject();
        object.put("schemaVersion", SCHEMA_VERSION);
        object.put("eventPoints", intArray(eventPoints));
        object.put("rewardClaims", booleanArray(rewardClaims));
        object.put("freeGemClaimAvailable", freeGemClaimAvailable);
        object.put("clanCapsuleClaimAvailable", clanCapsuleClaimAvailable);
        object.put("lastCheckIn", lastCheckIn == null ? null : lastCheckIn.toString());
        return object.toJSONString();
    }

    @SuppressWarnings("unchecked")
    public String toEventJson() {
        JSONObject object = new JSONObject();
        object.put("schemaVersion", SCHEMA_VERSION);
        object.put("eventPoints", intArray(eventPoints));
        object.put("rewardClaims", booleanArray(rewardClaims));
        return object.toJSONString();
    }

    @SuppressWarnings("unchecked")
    public String toDailyClaimJson() {
        JSONObject object = new JSONObject();
        object.put("schemaVersion", SCHEMA_VERSION);
        object.put("freeGemClaimAvailable", freeGemClaimAvailable);
        object.put("clanCapsuleClaimAvailable", clanCapsuleClaimAvailable);
        object.put("lastCheckIn", lastCheckIn == null ? null : lastCheckIn.toString());
        return object.toJSONString();
    }

    public int eventPoint(int index) {
        return eventPoints[checked(index, eventPoints.length)];
    }

    public void setEventPoint(int index, int value) {
        eventPoints[checked(index, eventPoints.length)] = value;
    }

    public boolean dailyRewardClaimed() {
        return rewardClaims[0];
    }

    public boolean topRewardClaimed(int index) {
        return rewardClaims[checked(index + 1, rewardClaims.length)];
    }

    public void setRewardClaimed(int index, boolean value) {
        rewardClaims[checked(index, rewardClaims.length)] = value;
    }

    public boolean isFreeGemClaimAvailable() { return freeGemClaimAvailable; }
    public void setFreeGemClaimAvailable(boolean value) { freeGemClaimAvailable = value; }
    public boolean isClanCapsuleClaimAvailable() { return clanCapsuleClaimAvailable; }
    public void setClanCapsuleClaimAvailable(boolean value) { clanCapsuleClaimAvailable = value; }
    public LocalDateTime lastCheckIn() { return lastCheckIn; }
    public void setLastCheckIn(LocalDateTime value) { lastCheckIn = value; }

    private void readLegacyEvent(JSONArray values) {
        for (int index = 0; index < eventPoints.length && index < values.size(); index++) {
            eventPoints[index] = intValue(values.get(index), 0);
        }
        for (int index = 0; index < rewardClaims.length && index + 6 < values.size(); index++) {
            rewardClaims[index] = booleanValue(values.get(index + 6), false);
        }
    }

    @SuppressWarnings("unchecked")
    private static JSONArray intArray(int[] values) {
        JSONArray result = new JSONArray();
        for (int value : values) result.add(value);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static JSONArray booleanArray(boolean[] values) {
        JSONArray result = new JSONArray();
        for (boolean value : values) result.add(value);
        return result;
    }

    private static Object valueAt(JSONArray values, int index) {
        return index < values.size() ? values.get(index) : null;
    }

    private static int intValue(Object value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private static int checked(int index, int length) {
        if (index < 0 || index >= length) throw new IndexOutOfBoundsException(index);
        return index;
    }

    @Override
    public boolean equals(Object value) {
        if (!(value instanceof PlayerEventState other)) return false;
        return Arrays.equals(eventPoints, other.eventPoints)
                && Arrays.equals(rewardClaims, other.rewardClaims)
                && freeGemClaimAvailable == other.freeGemClaimAvailable
                && clanCapsuleClaimAvailable == other.clanCapsuleClaimAvailable
                && Objects.equals(lastCheckIn, other.lastCheckIn);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(eventPoints);
        result = 31 * result + Arrays.hashCode(rewardClaims);
        result = 31 * result + Boolean.hashCode(freeGemClaimAvailable);
        result = 31 * result + Boolean.hashCode(clanCapsuleClaimAvailable);
        return 31 * result + Objects.hashCode(lastCheckIn);
    }
}
