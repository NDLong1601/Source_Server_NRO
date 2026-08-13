package nro.models.task;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;
import nro.models.consts.ConstTask;

public final class TaskConfig {

    private static final Path CONFIG_PATH = Paths.get("task.properties");
    private static final long RELOAD_INTERVAL_MS = 1_000L;
    private static volatile Properties values = new Properties();
    private static volatile long loadedModifiedTime = Long.MIN_VALUE;
    private static volatile long lastCheckTime;

    private TaskConfig() {
    }

    private static Properties values() {
        long now = System.currentTimeMillis();
        if (now - lastCheckTime < RELOAD_INTERVAL_MS) {
            return values;
        }
        synchronized (TaskConfig.class) {
            if (now - lastCheckTime < RELOAD_INTERVAL_MS) {
                return values;
            }
            lastCheckTime = now;
            try {
                long modifiedTime = Files.exists(CONFIG_PATH)
                        ? Files.getLastModifiedTime(CONFIG_PATH).toMillis() : -1L;
                if (modifiedTime != loadedModifiedTime) {
                    Properties loaded = new Properties();
                    if (modifiedTime >= 0) {
                        try (InputStream input = Files.newInputStream(CONFIG_PATH)) {
                            loaded.load(input);
                        }
                    }
                    values = loaded;
                    loadedModifiedTime = modifiedTime;
                }
            } catch (IOException e) {
                System.err.println("Khong the tai task.properties: " + e.getMessage());
            }
            return values;
        }
    }

    public static int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(values().getProperty(key, "").trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static int getInt(String key, int defaultValue, int min, int max) {
        return Math.max(min, Math.min(max, getInt(key, defaultValue)));
    }

    public static int[] getIntArray(String key, int... defaultValues) {
        String raw = values().getProperty(key, "").trim();
        if (raw.isEmpty()) {
            return Arrays.copyOf(defaultValues, defaultValues.length);
        }
        String[] parts = raw.split(",");
        int[] result = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                result[i] = Integer.parseInt(parts[i].trim());
            }
            return result.length == 0 ? Arrays.copyOf(defaultValues, defaultValues.length) : result;
        } catch (NumberFormatException e) {
            return Arrays.copyOf(defaultValues, defaultValues.length);
        }
    }

    public static int getMaxSideTask() {
        return getInt("task.side.maxPerDay", ConstTask.MAX_SIDE_TASK, 0, 127);
    }

    public static int getMaxClanTask() {
        return getInt("task.clan.maxPerDay", ConstTask.MAX_CLAN_TASK, 0, 127);
    }

    public static int getSideGoldReward(int level) {
        return getLevelInt("task.side.goldRewards", level,
                ConstTask.GOLD_EASY,
                ConstTask.GOLD_NORMAL,
                ConstTask.GOLD_HARD,
                ConstTask.GOLD_VERY_HARD,
                ConstTask.GOLD_HELL);
    }

    public static int getSideItemReward(int level) {
        return getLevelInt("task.side.itemRewards", level, 708, 707, 706, 705, 704);
    }

    public static int getSideHellTreeItemId() {
        return getInt("task.side.hellTreeItemId", 822, -1, Short.MAX_VALUE);
    }

    public static int getSideHellTreeLeftThreshold() {
        return getInt("task.side.hellTreeLeftThreshold", 15, 0, 127);
    }

    public static int getClanCapsuleReward(int level) {
        int perLevel = getInt("task.clan.capsulePerLevel", 10, 0, Integer.MAX_VALUE);
        return Math.max(0, level + 1) * perLevel;
    }

    private static String rewardKey(String type, int id, String field) {
        return "task.reward." + type + "." + id + "." + field;
    }

    public static boolean isCustomRewardEnabled(String type, int id) {
        return "1".equals(values().getProperty(rewardKey(type, id, "enabled"), "").trim())
                || "true".equalsIgnoreCase(values().getProperty(rewardKey(type, id, "enabled"), "").trim());
    }

    public static long getTaskRewardPotential(String type, int id) {
        return getLong(rewardKey(type, id, "potential"), 0L, 0L, Integer.MAX_VALUE);
    }

    public static long getTaskRewardGold(String type, int id) {
        return getLong(rewardKey(type, id, "gold"), 0L, 0L, Integer.MAX_VALUE);
    }

    public static int getTaskRewardGem(String type, int id) {
        return getInt(rewardKey(type, id, "gem"), 0, 0, Integer.MAX_VALUE);
    }

    public static int[] getTaskRewardItems(String type, int id) {
        String raw = values().getProperty(rewardKey(type, id, "items"), "").trim();
        if (raw.isEmpty()) {
            return new int[0];
        }
        String[] parts = raw.split(",");
        int[] parsed = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                parsed[i] = Integer.parseInt(parts[i].trim());
                if (parsed[i] < 0 || parsed[i] > Short.MAX_VALUE) {
                    return new int[0];
                }
            }
            return parsed;
        } catch (NumberFormatException e) {
            return new int[0];
        }
    }

    private static long getLong(String key, long defaultValue, long min, long max) {
        try {
            return Math.max(min, Math.min(max, Long.parseLong(values().getProperty(key, "").trim())));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static int getLevelInt(String key, int level, int... defaultValues) {
        int[] configured = getIntArray(key, defaultValues);
        if (level < 0) {
            return 0;
        }
        if (level < configured.length) {
            return configured[level];
        }
        return level < defaultValues.length ? defaultValues[level] : 0;
    }
}
