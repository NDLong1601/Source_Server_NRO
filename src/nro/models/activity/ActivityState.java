package nro.models.activity;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Persisted player progress for daily and weekly Activity Points. */
public class ActivityState {

    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_UNIQUE_KEYS_PER_SOURCE = 128;

    public int schemaVersion = SCHEMA_VERSION;
    public String dayKey = "";
    public int dailyPoints;
    public long dailyClaimMask;
    public Map<String, Integer> dailyCounters = new LinkedHashMap<>();
    public Set<String> dailyCategories = new LinkedHashSet<>();
    public Map<String, Set<String>> dailyUnique = new LinkedHashMap<>();
    public String weekKey = "";
    public int weeklyPoints;
    public int qualifiedDays;
    public Set<String> qualifiedDayKeys = new LinkedHashSet<>();
    public long weeklyClaimMask;
    public long lastAwardAt;

    /** Runtime-only throttle; Gson deliberately excludes it from player data. */
    public transient long lastPeriodCheckAt;

    public void normalize() {
        schemaVersion = SCHEMA_VERSION;
        dayKey = dayKey == null ? "" : dayKey;
        weekKey = weekKey == null ? "" : weekKey;
        dailyPoints = Math.max(0, dailyPoints);
        weeklyPoints = Math.max(0, weeklyPoints);
        qualifiedDays = Math.max(0, qualifiedDays);
        if (dailyCounters == null) {
            dailyCounters = new LinkedHashMap<>();
        }
        if (dailyCategories == null) {
            dailyCategories = new LinkedHashSet<>();
        }
        if (dailyUnique == null) {
            dailyUnique = new LinkedHashMap<>();
        }
        if (qualifiedDayKeys == null) {
            qualifiedDayKeys = new LinkedHashSet<>();
        }
        dailyCounters.entrySet().removeIf(entry -> entry.getKey() == null
                || entry.getValue() == null || entry.getValue() < 0);
        dailyCategories.removeIf(value -> value == null || value.isBlank());
        dailyUnique.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
        for (Set<String> keys : dailyUnique.values()) {
            keys.removeIf(value -> value == null || value.isBlank());
            while (keys.size() > MAX_UNIQUE_KEYS_PER_SOURCE) {
                keys.remove(keys.iterator().next());
            }
        }
        qualifiedDayKeys.removeIf(value -> value == null || value.isBlank());
        qualifiedDays = qualifiedDayKeys.size();
    }

    public void resetDaily(String nextDayKey) {
        dayKey = nextDayKey;
        dailyPoints = 0;
        dailyClaimMask = 0L;
        dailyCounters.clear();
        dailyCategories.clear();
        dailyUnique.clear();
        lastAwardAt = 0L;
    }

    public void resetWeekly(String nextWeekKey) {
        weekKey = nextWeekKey;
        weeklyPoints = 0;
        qualifiedDays = 0;
        qualifiedDayKeys.clear();
        weeklyClaimMask = 0L;
    }

    public int counter(String key) {
        return dailyCounters.getOrDefault(key, 0);
    }

    public void increaseCounter(String key, int amount) {
        if (key == null || key.isBlank() || amount <= 0) {
            return;
        }
        dailyCounters.put(key, Math.max(0, counter(key) + amount));
    }
}
