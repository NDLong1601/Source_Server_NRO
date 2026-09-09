package nro.models.clan;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Pattern;
import nro.models.utils.Logger;

/** Validated cosmetic milestones for phase 5D. */
public final class ClanAppearanceConfig {

    private static final Path DEFAULT_PATH = Path.of("data", "clan_appearance.properties");
    private static final int MAX_TIERS = 4;
    private static final int MAX_RESOURCE_LEVEL = 20;
    private static final Pattern SAFE_RESOURCE_PREFIX = Pattern.compile("[a-z0-9_]{1,32}");
    private static final String[] DEFAULT_NAMES = {
        "Mầm xanh", "Cổ thụ", "Linh thụ", "Thần mộc"
    };
    private static final String[] DEFAULT_TITLES = {
        "Khởi nguyên", "Bền vững", "Phồn thịnh", "Huyền thoại"
    };
    private static final long[] DEFAULT_VALUES = {0L, 30_000L, 75_000L, 120_000L};
    private static final int[] DEFAULT_CLAN_LEVELS = {1, 20, 40, 60};
    private static final int[] DEFAULT_TREE_LEVELS = {1, 10, 15, 20};
    private static final int[] DEFAULT_ACCENTS = {0x39B96E, 0xD7A83A, 0x55A8FF, 0xC56CFF};
    private static final int[] DEFAULT_AURAS = {0, 1, 2, 3};

    private final int appearanceVersion;
    private final String resourcePrefix;
    private final int maxResourceLevel;
    private final List<Tier> tiers;

    private ClanAppearanceConfig(int appearanceVersion, String resourcePrefix,
            int maxResourceLevel, List<Tier> tiers) {
        this.appearanceVersion = appearanceVersion;
        this.resourcePrefix = resourcePrefix;
        this.maxResourceLevel = maxResourceLevel;
        this.tiers = List.copyOf(tiers);
    }

    public static ClanAppearanceConfig load() {
        return load(DEFAULT_PATH);
    }

    public static ClanAppearanceConfig load(Path path) {
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            values.load(input);
        } catch (IOException error) {
            Logger.warning("Không đọc được " + path + ", dùng cấu hình ngoại hình Cây bang mặc định.\n");
        }
        return from(values);
    }

    public static ClanAppearanceConfig from(Properties values) {
        Properties safe = values == null ? new Properties() : values;
        int version = boundedInt(safe, "appearance_version", 1, 1, 255);
        int tierCount = boundedInt(safe, "tier_count", MAX_TIERS, 1, MAX_TIERS);
        int resourceLevel = boundedInt(safe, "max_resource_level", MAX_RESOURCE_LEVEL,
                1, MAX_RESOURCE_LEVEL);
        String prefix = safe.getProperty("resource_prefix", "cay_lv_").trim().toLowerCase(Locale.ROOT);
        if (!SAFE_RESOURCE_PREFIX.matcher(prefix).matches()) {
            prefix = "cay_lv_";
        }

        ArrayList<Tier> tiers = new ArrayList<>(tierCount);
        long previousValue = 0L;
        int previousClanLevel = 0;
        int previousTreeLevel = 0;
        for (int index = 0; index < tierCount; index++) {
            long minimumValue = Math.max(previousValue, boundedLong(safe,
                    "tier_" + index + "_value", DEFAULT_VALUES[index], 0L, Long.MAX_VALUE));
            int minimumClanLevel = Math.max(previousClanLevel, boundedInt(safe,
                    "tier_" + index + "_clan_level", DEFAULT_CLAN_LEVELS[index],
                    0, Clan.TECHNICAL_MAX_LEVEL));
            int minimumTreeLevel = Math.max(previousTreeLevel, boundedInt(safe,
                    "tier_" + index + "_tree_level", DEFAULT_TREE_LEVELS[index],
                    0, MAX_RESOURCE_LEVEL));
            String name = safeText(safe.getProperty("tier_" + index + "_name",
                    DEFAULT_NAMES[index]), DEFAULT_NAMES[index]);
            String title = safeText(safe.getProperty("tier_" + index + "_title",
                    DEFAULT_TITLES[index]), DEFAULT_TITLES[index]);
            int accentRgb = (int) boundedLong(safe, "tier_" + index + "_accent_rgb",
                    DEFAULT_ACCENTS[index], 0L, 0xFFFFFFL);
            int auraStyle = boundedInt(safe, "tier_" + index + "_aura_style",
                    DEFAULT_AURAS[index], 0, 15);
            tiers.add(new Tier(index, name, title, minimumValue, minimumClanLevel,
                    minimumTreeLevel, accentRgb, auraStyle));
            previousValue = minimumValue;
            previousClanLevel = minimumClanLevel;
            previousTreeLevel = minimumTreeLevel;
        }
        return new ClanAppearanceConfig(version, prefix, resourceLevel, tiers);
    }

    public int appearanceVersion() {
        return appearanceVersion;
    }

    public int tierCount() {
        return tiers.size();
    }

    public Tier tier(int index) {
        return tiers.get(Math.max(0, Math.min(tiers.size() - 1, index)));
    }

    public String resourceName(int treeLevel) {
        int level = Math.max(1, Math.min(maxResourceLevel, treeLevel));
        return resourcePrefix + String.format(Locale.ROOT, "%02d", level);
    }

    private static String safeText(String value, String fallback) {
        String source = value == null ? fallback : value;
        StringBuilder result = new StringBuilder(Math.min(64, source.length()));
        for (int index = 0; index < source.length() && result.length() < 64; index++) {
            char character = source.charAt(index);
            result.append(Character.isISOControl(character) ? ' ' : character);
        }
        String cleaned = result.toString().trim().replaceAll(" +", " ");
        return cleaned.isEmpty() ? fallback : cleaned;
    }

    private static int boundedInt(Properties values, String key, int fallback, int min, int max) {
        return (int) boundedLong(values, key, fallback, min, max);
    }

    private static long boundedLong(Properties values, String key, long fallback, long min, long max) {
        String raw = values.getProperty(key);
        if (raw == null) {
            return Math.max(min, Math.min(max, fallback));
        }
        try {
            long parsed = Long.decode(raw.trim());
            return Math.max(min, Math.min(max, parsed));
        } catch (RuntimeException ignored) {
            return Math.max(min, Math.min(max, fallback));
        }
    }

    public record Tier(int id, String name, String title, long minimumClanValue,
            int minimumClanLevel, int minimumTreeLevel, int accentRgb, int auraStyle) {
    }
}
