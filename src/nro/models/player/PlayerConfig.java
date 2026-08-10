package nro.models.player;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;

/**
 * Central runtime configuration for player defaults and server-wide limits.
 *
 * Values are reloaded at most once per second. Starting values only affect new
 * characters while limits are used immediately by runtime checks.
 */
public final class PlayerConfig {

    private static final Path CONFIG_PATH = Paths.get("player.properties");
    private static final long RELOAD_INTERVAL_MS = 1_000L;

    private static final long[] DEFAULT_POWER_LIMITS = {
        17_999_999_999L, 19_999_999_999L, 24_999_999_999L, 29_999_999_999L,
        39_999_999_999L, 50_010_000_000L, 60_010_000_000L, 70_010_000_000L,
        80_010_000_000L, 90_010_000_000L
    };
    private static final int[] DEFAULT_HP_MP_LIMITS = {
        220_000, 240_000, 300_000, 350_000, 400_000,
        450_000, 500_000, 525_000, 550_000, 575_000
    };
    private static final int[] DEFAULT_DAMAGE_LIMITS = {
        11_000, 12_000, 15_000, 18_000, 20_000,
        22_000, 24_000, 24_500, 25_000, 26_000
    };
    private static final int[] DEFAULT_DEFENSE_LIMITS = {
        550, 600, 700, 800, 1_000, 1_200, 1_400, 1_500, 1_600, 1_800
    };
    private static final int[] DEFAULT_CRITICAL_LIMITS = {
        1, 2, 3, 4, 5, 6, 7, 8, 9, 10
    };

    private static volatile Properties values = new Properties();
    private static volatile long loadedModifiedTime = Long.MIN_VALUE;
    private static volatile long lastCheckTime;

    private PlayerConfig() {
    }

    private static Properties values() {
        long now = System.currentTimeMillis();
        if (now - lastCheckTime < RELOAD_INTERVAL_MS) {
            return values;
        }
        synchronized (PlayerConfig.class) {
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
                System.err.println("Khong the tai player.properties: " + e.getMessage());
            }
            return values;
        }
    }

    public static int getInt(String key, int defaultValue, int min, int max) {
        try {
            return Math.max(min, Math.min(max,
                    Integer.parseInt(values().getProperty(key, "").trim())));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static long getLong(String key, long defaultValue, long min, long max) {
        try {
            return Math.max(min, Math.min(max,
                    Long.parseLong(values().getProperty(key, "").trim())));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static int[] getIntArray(String key, int[] defaults) {
        String raw = values().getProperty(key, "").trim();
        if (raw.isEmpty()) {
            return Arrays.copyOf(defaults, defaults.length);
        }
        String[] parts = raw.split(",");
        if (parts.length != defaults.length) {
            return Arrays.copyOf(defaults, defaults.length);
        }
        int[] result = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                result[i] = Integer.parseInt(parts[i].trim());
            }
            return result;
        } catch (NumberFormatException e) {
            return Arrays.copyOf(defaults, defaults.length);
        }
    }

    private static long[] getLongArray(String key, long[] defaults) {
        String raw = values().getProperty(key, "").trim();
        if (raw.isEmpty()) {
            return Arrays.copyOf(defaults, defaults.length);
        }
        String[] parts = raw.split(",");
        if (parts.length != defaults.length) {
            return Arrays.copyOf(defaults, defaults.length);
        }
        long[] result = new long[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                result[i] = Long.parseLong(parts[i].trim());
            }
            return result;
        } catch (NumberFormatException e) {
            return Arrays.copyOf(defaults, defaults.length);
        }
    }

    private static int raceValue(String suffix, int gender, int earth, int namek, int saiyan,
            int min, int max) {
        String race = gender == 0 ? "earth" : gender == 1 ? "namek" : "saiyan";
        int fallback = gender == 0 ? earth : gender == 1 ? namek : saiyan;
        return getInt("player.start." + race + "." + suffix, fallback, min, max);
    }

    public static long getStartGold() {
        return getLong("player.start.gold", 2_000L, 0L, getMaxGold());
    }

    public static int getStartGem(boolean testServer) {
        return testServer
                ? getInt("player.start.testGem", 1_000_000_000, 0, getMaxGem())
                : getInt("player.start.gem", 10_000, 0, getMaxGem());
    }

    public static int getStartRuby() {
        return getInt("player.start.ruby", 0, 0, getMaxRuby());
    }

    public static int getStartCoupon() {
        return getInt("player.start.coupon", 0, 0, getMaxCoupon());
    }

    public static int getStartBagSlots() {
        return getInt("player.start.bagSlots", 30, 1, getMaxBagSlots());
    }

    public static int getStartBoxSlots() {
        return getInt("player.start.boxSlots", 30, 1, getMaxBoxSlots());
    }

    public static int getStartStamina() {
        return getInt("player.start.stamina", 1_000, 1, Short.MAX_VALUE);
    }

    public static int getStartLimitPower() {
        return getInt("player.start.limitPower", 0, 0, NPoint.MAX_LIMIT);
    }

    public static long getStartPower() {
        return getLong("player.start.power", 2_000L, 0L, getPowerLimit(getStartLimitPower()));
    }

    public static long getStartPotential() {
        return getLong("player.start.potential", 2_000L, 0L, Long.MAX_VALUE);
    }

    public static int getStartCriticalDragon() {
        return getInt("player.start.criticalDragon", 0, 0, Byte.MAX_VALUE);
    }

    public static int getStartHp(int gender) {
        return raceValue("hp", gender, 200, 100, 100, 1, Integer.MAX_VALUE);
    }

    public static int getStartMp(int gender) {
        return raceValue("mp", gender, 100, 200, 100, 1, Integer.MAX_VALUE);
    }

    public static int getStartDamage(int gender) {
        return raceValue("damage", gender, 10, 10, 15, 1, Integer.MAX_VALUE);
    }

    public static int getStartDefense(int gender) {
        return raceValue("defense", gender, 0, 0, 0, 0, Integer.MAX_VALUE);
    }

    public static int getStartCritical(int gender) {
        return raceValue("critical", gender, 0, 0, 0, 0, Byte.MAX_VALUE);
    }

    public static long getMaxGold() {
        return getLong("player.max.gold", 200_000_000_000L, 1L, 9_000_000_000_000_000L);
    }

    public static int getMaxGem() {
        return getInt("player.max.gem", Integer.MAX_VALUE, 1, Integer.MAX_VALUE);
    }

    public static int getMaxRuby() {
        return getInt("player.max.ruby", Integer.MAX_VALUE, 1, Integer.MAX_VALUE);
    }

    public static int getMaxCoupon() {
        return getInt("player.max.coupon", Integer.MAX_VALUE, 1, Integer.MAX_VALUE);
    }

    public static int getMaxBagSlots() {
        return getInt("player.max.bagSlots", 80, 1, 127);
    }

    public static int getMaxBoxSlots() {
        return getInt("player.max.boxSlots", 100, 1, 127);
    }

    public static long getPowerLimit(int level) {
        long[] configured = getLongArray("player.limit.power", DEFAULT_POWER_LIMITS);
        return level >= 0 && level < configured.length ? configured[level] : 0L;
    }

    public static int getHpMpLimit(int level) {
        int[] configured = getIntArray("player.limit.hpMp", DEFAULT_HP_MP_LIMITS);
        return level >= 0 && level < configured.length ? configured[level] : 0;
    }

    public static int getDamageLimit(int level) {
        int[] configured = getIntArray("player.limit.damage", DEFAULT_DAMAGE_LIMITS);
        return level >= 0 && level < configured.length ? configured[level] : 0;
    }

    public static int getDefenseLimit(int level) {
        int[] configured = getIntArray("player.limit.defense", DEFAULT_DEFENSE_LIMITS);
        return level >= 0 && level < configured.length ? configured[level] : 0;
    }

    public static int getCriticalLimit(int level) {
        int[] configured = getIntArray("player.limit.critical", DEFAULT_CRITICAL_LIMITS);
        return level >= 0 && level < configured.length ? configured[level] : 0;
    }
}
