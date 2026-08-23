package nro.models.skill;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Cấu hình nóng cho hệ thống thành thạo kỹ năng. File được kiểm tra thay đổi
 * tối đa một lần mỗi giây để có thể cân bằng mà không cần build lại server.
 */
public final class SkillMasteryConfig {

    private static final Path CONFIG_PATH = Paths.get("skill_mastery.properties");
    private static final long RELOAD_INTERVAL_MS = 1_000L;
    private static final int BASE_LEVEL = 7;

    private static volatile Properties values = new Properties();
    private static volatile long loadedModifiedTime = Long.MIN_VALUE;
    private static volatile long lastCheckTime;

    private SkillMasteryConfig() {
    }

    private static Properties values() {
        long now = System.currentTimeMillis();
        if (now - lastCheckTime < RELOAD_INTERVAL_MS) {
            return values;
        }
        synchronized (SkillMasteryConfig.class) {
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
                System.err.println("Khong the tai skill_mastery.properties: " + e.getMessage());
            }
            return values;
        }
    }

    public static boolean isEnabled() {
        return getBoolean("mastery.enabled", true);
    }

    public static int getBaseLevel() {
        return BASE_LEVEL;
    }

    /** Giá trị 0 trong file có nghĩa là không đặt trần cấp. */
    public static int getMaxLevel(int skillTemplateId) {
        int configured = getSkillInt(skillTemplateId, "maxLevel", getInt("mastery.maxLevel", 0, 0, Integer.MAX_VALUE), 0, Integer.MAX_VALUE);
        return configured == 0 ? Integer.MAX_VALUE : Math.max(BASE_LEVEL, configured);
    }

    public static long getRequiredUses(int skillTemplateId, int currentLevel) {
        Long exact = getOptionalLevelLong(skillTemplateId, currentLevel, "requiredUses", 1L, Long.MAX_VALUE);
        if (exact != null) {
            return exact;
        }
        long base = getSkillLong(skillTemplateId, "requiredUses",
                getLong("mastery.requiredUses", 1_000L, 1L, Long.MAX_VALUE), 1L, Long.MAX_VALUE);
        int growth = getSkillInt(skillTemplateId, "requiredGrowthPercent",
                getInt("mastery.requiredGrowthPercent", 20, 0, 10_000), 0, 10_000);
        long maximum = getSkillLong(skillTemplateId, "requiredUsesMax",
                getLong("mastery.requiredUsesMax", 1_000_000L, 1L, Long.MAX_VALUE), 1L, Long.MAX_VALUE);
        if (growth == 0) {
            return Math.min(base, maximum);
        }
        int steps = Math.max(0, currentLevel - BASE_LEVEL);
        long required = base;
        for (int i = 0; i < steps && required < maximum; i++) {
            long increase = multiplyDivide(required, growth, 100L);
            required = safeAdd(required, Math.max(1L, increase));
            if (required >= maximum) {
                return maximum;
            }
        }
        return Math.max(1L, Math.min(required, maximum));
    }

    public static long getGainPerUse(int skillTemplateId, int currentLevel) {
        Long exact = getOptionalLevelLong(skillTemplateId, currentLevel, "gainPerUse", 1L, Long.MAX_VALUE);
        if (exact != null) {
            return exact;
        }
        return getSkillLong(skillTemplateId, "gainPerUse",
                getLong("mastery.gainPerUse", 1L, 1L, Long.MAX_VALUE), 1L, Long.MAX_VALUE);
    }

    public static int getMinGainIntervalMs(int skillTemplateId) {
        return getSkillInt(skillTemplateId, "minGainIntervalMs",
                getInt("mastery.minGainIntervalMs", 250, 0, Integer.MAX_VALUE), 0, Integer.MAX_VALUE);
    }

    public static int getClientProgressStep() {
        return getInt("mastery.clientProgressStep", 10, 1, 1_000);
    }

    public static boolean sendProgressForAllSkills() {
        return getBoolean("mastery.clientProgressAllSkills", true);
    }

    public static boolean isMapExcluded(int mapId) {
        String raw = values().getProperty("mastery.excludedMapIds", "").trim();
        if (raw.isEmpty()) {
            return false;
        }
        for (String value : raw.split(",")) {
            try {
                if (Integer.parseInt(value.trim()) == mapId) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return false;
    }

    public static int applyDamage(Skill skill, int baseValue) {
        Integer exact = getOptionalLevelInt(skill, "damage", 0, Integer.MAX_VALUE);
        if (exact != null) {
            return exact;
        }
        return applyDamageIncrease(skill, baseValue);
    }

    /**
     * Damage động (tự sát, dịch chuyển, quả cầu...) có baseValue là HP/damage
     * runtime, không phải cột damage của skill. Vì vậy chỉ dùng phần trăm chính
     * xác theo cấp, tuyệt đối không thay nó bằng skill.level.N.damage.
     */
    public static int applyDamageStat(Skill skill, int baseValue) {
        Integer exactPercent = getOptionalLevelInt(skill, "damagePercent", 0, 100_000);
        if (exactPercent != null) {
            return clampInt(multiplyDivide(baseValue, exactPercent, 100L));
        }
        return applyDamageIncrease(skill, baseValue);
    }

    private static int applyDamageIncrease(Skill skill, int baseValue) {
        return applyIncrease(skill, baseValue, "damagePercentPerLevel", "damageFlatPerLevel",
                getInt("mastery.damagePercentPerLevel", 5, 0, 10_000),
                getInt("mastery.damageFlatPerLevel", 0, 0, Integer.MAX_VALUE));
    }

    public static int applyRange(Skill skill, int baseValue) {
        Integer exactPercent = getOptionalLevelInt(skill, "rangePercent", 0, 100_000);
        if (exactPercent != null) {
            return clampInt(multiplyDivide(baseValue, exactPercent, 100L));
        }
        return applyIncrease(skill, baseValue, "rangePercentPerLevel", "rangeFlatPerLevel",
                getInt("mastery.rangePercentPerLevel", 0, 0, 10_000),
                getInt("mastery.rangeFlatPerLevel", 0, 0, Integer.MAX_VALUE));
    }

    public static int applyRangeX(Skill skill, int baseValue) {
        Integer exact = getOptionalLevelInt(skill, "dx", 0, Integer.MAX_VALUE);
        return exact != null ? exact : applyRange(skill, baseValue);
    }

    public static int applyRangeY(Skill skill, int baseValue) {
        Integer exact = getOptionalLevelInt(skill, "dy", 0, Integer.MAX_VALUE);
        return exact != null ? exact : applyRange(skill, baseValue);
    }

    public static int applyEffect(Skill skill, int baseValue) {
        Integer exactPercent = getOptionalLevelInt(skill, "effectPercent", 0, 100_000);
        if (exactPercent != null) {
            return clampInt(multiplyDivide(baseValue, exactPercent, 100L));
        }
        return applyIncrease(skill, baseValue, "effectPercentPerLevel", "effectFlatPerLevel",
                getInt("mastery.effectPercentPerLevel", 0, 0, 10_000),
                getInt("mastery.effectFlatPerLevel", 0, 0, Integer.MAX_VALUE));
    }

    public static int applyMana(Skill skill, int baseValue) {
        Integer exact = getOptionalLevelInt(skill, "manaUse", 0, Integer.MAX_VALUE);
        if (exact != null) {
            return exact;
        }
        int percent = getSkillInt(skill.template.id, "manaReductionPercentPerLevel",
                getInt("mastery.manaReductionPercentPerLevel", 0, 0, 100), 0, 100);
        int flat = getSkillInt(skill.template.id, "manaReductionFlatPerLevel",
                getInt("mastery.manaReductionFlatPerLevel", 0, 0, Integer.MAX_VALUE), 0, Integer.MAX_VALUE);
        int minimumPercent = getSkillInt(skill.template.id, "minimumManaPercent",
                getInt("mastery.minimumManaPercent", 50, 0, 100), 0, 100);
        return applyReduction(skill, baseValue, percent, flat, minimumPercent);
    }

    public static int applyCooldown(Skill skill, int baseValue) {
        Integer exact = getOptionalLevelInt(skill, "coolDown", 0, Integer.MAX_VALUE);
        if (exact != null) {
            return exact;
        }
        int percent = getSkillInt(skill.template.id, "cooldownReductionPercentPerLevel",
                getInt("mastery.cooldownReductionPercentPerLevel", 0, 0, 100), 0, 100);
        int flat = getSkillInt(skill.template.id, "cooldownReductionMsPerLevel",
                getInt("mastery.cooldownReductionMsPerLevel", 0, 0, Integer.MAX_VALUE), 0, Integer.MAX_VALUE);
        int minimumPercent = getSkillInt(skill.template.id, "minimumCooldownPercent",
                getInt("mastery.minimumCooldownPercent", 50, 0, 100), 0, 100);
        return applyReduction(skill, baseValue, percent, flat, minimumPercent);
    }

    public static int applyMaxFight(Skill skill, int baseValue) {
        Integer exact = getOptionalLevelInt(skill, "maxFight", 1, Integer.MAX_VALUE);
        if (exact != null) {
            return exact;
        }
        return applyIncrease(skill, baseValue, "maxFightPercentPerLevel", "maxFightFlatPerLevel",
                getInt("mastery.maxFightPercentPerLevel", 0, 0, 10_000),
                getInt("mastery.maxFightFlatPerLevel", 0, 0, Integer.MAX_VALUE));
    }

    private static int applyIncrease(Skill skill, int baseValue, String percentName, String flatName,
            int defaultPercent, int defaultFlat) {
        if (skill == null || skill.template == null || skill.getActualLevel() <= BASE_LEVEL || baseValue <= 0) {
            return baseValue;
        }
        int levels = skill.getActualLevel() - BASE_LEVEL;
        int percent = getSkillInt(skill.template.id, percentName, defaultPercent, 0, 10_000);
        int flat = getSkillInt(skill.template.id, flatName, defaultFlat, 0, Integer.MAX_VALUE);
        long result = baseValue;
        result = safeAdd(result, multiplyDivide(baseValue, (long) percent * levels, 100L));
        result = safeAdd(result, (long) flat * levels);
        return clampInt(result);
    }

    private static int applyReduction(Skill skill, int baseValue, int percentPerLevel, int flatPerLevel, int minimumPercent) {
        if (skill == null || skill.getActualLevel() <= BASE_LEVEL || baseValue <= 0) {
            return baseValue;
        }
        int levels = skill.getActualLevel() - BASE_LEVEL;
        long reduced = baseValue - multiplyDivide(baseValue, (long) percentPerLevel * levels, 100L)
                - (long) flatPerLevel * levels;
        long minimum = multiplyDivide(baseValue, minimumPercent, 100L);
        return clampInt(Math.max(minimum, reduced));
    }

    private static String skillKey(int skillTemplateId, String field) {
        return "skill." + skillTemplateId + "." + field;
    }

    private static String levelKey(int skillTemplateId, int level, String field) {
        return skillKey(skillTemplateId, "level." + level + "." + field);
    }

    private static Integer getOptionalLevelInt(Skill skill, String field, int min, int max) {
        if (skill == null || skill.template == null || skill.getActualLevel() <= BASE_LEVEL) {
            return null;
        }
        return getOptionalLevelInt(skill.template.id, skill.getActualLevel(), field, min, max);
    }

    private static Integer getOptionalLevelInt(int skillTemplateId, int level, String field, int min, int max) {
        String raw = values().getProperty(levelKey(skillTemplateId, level, field));
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(raw.trim())));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long getOptionalLevelLong(int skillTemplateId, int level, String field, long min, long max) {
        String raw = values().getProperty(levelKey(skillTemplateId, level, field));
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return Math.max(min, Math.min(max, Long.parseLong(raw.trim())));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int getSkillInt(int skillTemplateId, String field, int defaultValue, int min, int max) {
        return getInt(skillKey(skillTemplateId, field), defaultValue, min, max);
    }

    private static long getSkillLong(int skillTemplateId, String field, long defaultValue, long min, long max) {
        return getLong(skillKey(skillTemplateId, field), defaultValue, min, max);
    }

    private static boolean getBoolean(String key, boolean defaultValue) {
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

    private static int getInt(String key, int defaultValue, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(values().getProperty(key, "").trim())));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static long getLong(String key, long defaultValue, long min, long max) {
        try {
            return Math.max(min, Math.min(max, Long.parseLong(values().getProperty(key, "").trim())));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static long multiplyDivide(long value, long multiplier, long divisor) {
        if (value <= 0 || multiplier <= 0) {
            return 0L;
        }
        if (value > Long.MAX_VALUE / multiplier) {
            return Long.MAX_VALUE / divisor;
        }
        return value * multiplier / divisor;
    }

    private static long safeAdd(long first, long second) {
        if (second > 0 && first > Long.MAX_VALUE - second) {
            return Long.MAX_VALUE;
        }
        return first + second;
    }

    private static int clampInt(long value) {
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, value));
    }
}
