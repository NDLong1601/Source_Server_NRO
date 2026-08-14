package nro.models.player;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;

/**
 * Runtime configuration for combat disciples (Player.pet), not cosmetic pets.
 * The properties file is checked at most once per second.
 */
public final class PetConfig {

    private static final Path CONFIG_PATH = Paths.get("pet.properties");
    private static final long RELOAD_INTERVAL_MS = 1_000L;

    private static volatile Properties values = new Properties();
    private static volatile long loadedModifiedTime = Long.MIN_VALUE;
    private static volatile long lastCheckTime;

    private PetConfig() {
    }

    private static Properties values() {
        long now = System.currentTimeMillis();
        if (now - lastCheckTime < RELOAD_INTERVAL_MS) {
            return values;
        }
        synchronized (PetConfig.class) {
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
                System.err.println("Khong the tai pet.properties: " + e.getMessage());
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

    public static boolean getBoolean(String key, boolean defaultValue) {
        String value = values().getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim())) {
            return true;
        }
        if ("false".equalsIgnoreCase(value.trim()) || "0".equals(value.trim())) {
            return false;
        }
        return defaultValue;
    }

    public static int[] getIntArray(String key, int... defaults) {
        String raw = values().getProperty(key, "").trim();
        if (raw.isEmpty()) {
            return Arrays.copyOf(defaults, defaults.length);
        }
        String[] parts = raw.split(",");
        int[] result = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                result[i] = Integer.parseInt(parts[i].trim());
            }
            return result.length == 0 ? Arrays.copyOf(defaults, defaults.length) : result;
        } catch (NumberFormatException e) {
            return Arrays.copyOf(defaults, defaults.length);
        }
    }

    private static String typeKey(int typePet) {
        return switch (typePet) {
            case 1 -> "mabu";
            case 2 -> "uub";
            case 3 -> "kidbeer";
            case 4 -> "jiren";
            default -> "normal";
        };
    }

    private static long defaultPower(int typePet) {
        return switch (typePet) {
            case 1 -> 1_500_000L;
            case 2, 3, 4 -> 40_000_000_000L;
            default -> 2_000L;
        };
    }

    private static int defaultStat(int typePet, String stat, boolean maximum) {
        if (typePet >= 2 && typePet <= 4) {
            return switch (stat) {
                case "hp", "mp" -> 400_000;
                case "damage" -> 20_000;
                case "defense" -> maximum ? 50 : 9;
                case "critical" -> maximum ? 2 : 0;
                default -> 0;
            };
        }
        return switch (stat) {
            case "hp", "mp" -> maximum ? 2_100 : 800;
            case "damage" -> typePet == 1 ? (maximum ? 120 : 50) : (maximum ? 45 : 20);
            case "defense" -> maximum ? 50 : 9;
            case "critical" -> maximum ? 2 : 0;
            default -> 0;
        };
    }

    public static long getStartPower(int typePet) {
        return getLong("pet.start." + typeKey(typePet) + ".power", defaultPower(typePet), 0L, Long.MAX_VALUE);
    }

    public static int getStartStatMin(int typePet, String stat) {
        int fallback = defaultStat(typePet, stat, false);
        return getInt("pet.start." + typeKey(typePet) + "." + stat + "Min", fallback, 0, Integer.MAX_VALUE);
    }

    public static int getStartStatMax(int typePet, String stat) {
        int minimum = getStartStatMin(typePet, stat);
        int fallback = Math.max(minimum, defaultStat(typePet, stat, true));
        return getInt("pet.start." + typeKey(typePet) + "." + stat + "Max", fallback, minimum, Integer.MAX_VALUE);
    }

    public static int getStartStamina() {
        return getInt("pet.start.stamina", 1_000, 1, Short.MAX_VALUE);
    }

    public static int getStartLimitPower() {
        return getInt("pet.start.limitPower", 0, 0, NPoint.MAX_LIMIT);
    }

    public static long getSkillUnlockPower(int skillSlot) {
        long[] defaults = {150_000_000L, 1_500_000_000L, 20_000_000_000L, 40_000_000_000L};
        String raw = values().getProperty("pet.skill.unlockPower", "").trim();
        if (!raw.isEmpty()) {
            String[] parts = raw.split(",");
            if (parts.length == defaults.length) {
                try {
                    int index = skillSlot - 2;
                    return index >= 0 && index < parts.length ? Long.parseLong(parts[index].trim()) : Long.MAX_VALUE;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        int index = skillSlot - 2;
        return index >= 0 && index < defaults.length ? defaults[index] : Long.MAX_VALUE;
    }

    public static int[] getSkillPool(int skillSlot) {
        int[] defaults = switch (skillSlot) {
            case 1 -> new int[]{0, 2, 4};
            case 2 -> new int[]{1, 3, 5};
            case 3 -> new int[]{6, 8, 9};
            case 4 -> new int[]{13, 12, 19};
            case 5 -> new int[]{24, 26, 25};
            default -> new int[0];
        };
        int[] configured = getIntArray("pet.skill.slot" + skillSlot + "Pool", defaults);
        int[] filtered = Arrays.stream(configured)
                .filter(PetConfig::isKnownSkill)
                .distinct()
                .toArray();
        return filtered.length == 0 ? defaults : filtered;
    }

    private static boolean isKnownSkill(int skillId) {
        return skillId >= 0 && skillId <= 26 && skillId != 15 && skillId != 16;
    }

    public static int getSkillMaxLevel() {
        return getInt("pet.skill.maxLevel", 7, 1, 7);
    }

    public static int getAttackSkillCooldownMs() {
        return getInt("pet.skill.attackCooldownMs", 1_000, 0, Integer.MAX_VALUE);
    }

    public static boolean isAutoUnlockSkill() {
        return getBoolean("pet.skill.autoUnlock", true);
    }

    public static boolean isTypeAllowed(String key, int typePet, int... defaults) {
        for (int value : getIntArray(key, defaults)) {
            if (value == typePet) {
                return true;
            }
        }
        return false;
    }

    public static int getFusionTypeBonus(int typePet, String stat) {
        int fallback = typePet >= 2 && typePet <= 4 ? 20 : 0;
        return getInt("pet.fusion." + typeKey(typePet) + "." + stat + "BonusPercent", fallback, 0, 10_000);
    }

    public static int getFusionContributionPercent() {
        return getInt("pet.fusion.contributionPercent", 100, 0, 10_000);
    }

    public static int getFusionDurationMs() {
        return getInt("pet.fusion.durationMs", 600_000, 1_000, Integer.MAX_VALUE);
    }

    public static int getUnfusionCooldownMs() {
        return getInt("pet.fusion.unfusionCooldownMs", 5_000, 0, Integer.MAX_VALUE);
    }
}
