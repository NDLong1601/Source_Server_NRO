package nro.models.activity;

import com.google.gson.Gson;
import nro.models.utils.Logger;

/** JSON bridge for player.data_activity. */
public final class ActivityRepository {

    private static final Gson GSON = new Gson();

    private ActivityRepository() {
    }

    public static ActivityState read(String raw, int legacyDailyPoints) {
        ActivityState state = null;
        if (raw != null && !raw.isBlank()) {
            try {
                state = GSON.fromJson(raw, ActivityState.class);
            } catch (Exception e) {
                Logger.error("Cannot parse player data_activity; using a safe ActivityState default.\n");
            }
        }
        if (state == null) {
            state = new ActivityState();
            state.dailyPoints = Math.max(0, legacyDailyPoints);
        }
        state.normalize();
        return state;
    }

    public static String write(ActivityState state) {
        ActivityState safe = state == null ? new ActivityState() : state;
        safe.normalize();
        return GSON.toJson(safe);
    }
}
