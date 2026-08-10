package nro.models.combine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;
import nro.models.utils.Util;

/**
 * Runtime configuration for combine features.
 *
 * The file is checked at most once per second, so changes made from the admin
 * menu affect new combine attempts without restarting the server.
 */
public final class CombineConfig {

    private static final Path CONFIG_PATH = Paths.get("combine.properties");
    private static final long RELOAD_INTERVAL_MS = 1_000L;
    private static volatile Properties values = new Properties();
    private static volatile long loadedModifiedTime = Long.MIN_VALUE;
    private static volatile long lastCheckTime;

    private CombineConfig() {
    }

    private static Properties values() {
        long now = System.currentTimeMillis();
        if (now - lastCheckTime < RELOAD_INTERVAL_MS) {
            return values;
        }
        synchronized (CombineConfig.class) {
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
                System.err.println("Khong the tai combine.properties: " + e.getMessage());
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

    public static double getDouble(String key, double defaultValue) {
        try {
            return Double.parseDouble(values().getProperty(key, "").trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static double getRate(String key, double defaultValue) {
        return Math.max(0D, Math.min(100D, getDouble(key, defaultValue)));
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        String value = values().getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        value = value.trim();
        if ("true".equalsIgnoreCase(value) || "1".equals(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value) || "0".equals(value)) {
            return false;
        }
        return defaultValue;
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

    public static double[] getDoubleArray(String key, double... defaultValues) {
        String raw = values().getProperty(key, "").trim();
        if (raw.isEmpty()) {
            return Arrays.copyOf(defaultValues, defaultValues.length);
        }
        String[] parts = raw.split(",");
        double[] result = new double[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                result[i] = Double.parseDouble(parts[i].trim());
            }
            return result.length == 0 ? Arrays.copyOf(defaultValues, defaultValues.length) : result;
        } catch (NumberFormatException e) {
            return Arrays.copyOf(defaultValues, defaultValues.length);
        }
    }

    public static double getLevelValue(String key, int level, double... defaultValues) {
        double[] configured = getDoubleArray(key, defaultValues);
        if (level < 0) {
            return 0D;
        }
        if (level < configured.length) {
            return configured[level];
        }
        return level < defaultValues.length ? defaultValues[level] : 0D;
    }

    public static double getLevelRate(String key, int level, double... defaultValues) {
        return Math.max(0D, Math.min(100D, getLevelValue(key, level, defaultValues)));
    }

    public static int getLevelInt(String key, int level, int... defaultValues) {
        int[] configured = getIntArray(key, defaultValues);
        if (level < 0) {
            return 0;
        }
        if (level < configured.length) {
            return configured[level];
        }
        return level < defaultValues.length ? defaultValues[level] : 0;
    }

    public static boolean roll(String key, double defaultRate) {
        return Util.isTrue((float) getRate(key, defaultRate), 100);
    }
}
