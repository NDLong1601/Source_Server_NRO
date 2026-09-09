package nro.models.clan;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import nro.models.utils.Logger;

/** Independent rollout switches for clan subsystems. */
public final class ClanFeatureFlags {

    private static final Path DEFAULT_PATH = Path.of("data", "clan_features.properties");
    private static final ClanFeatureFlags INSTANCE = load(DEFAULT_PATH);

    public enum Feature {
        TERRITORY("territory", true),
        TREASURY("treasury", true),
        TREE("tree", true),
        PROGRESSION("progression", true),
        SHOP("shop", true),
        GIFT("gift", true),
        ITEM_STORAGE("item_storage", true),
        BUFF("buff", true),
        VALUE("value", false),
        RANKING("ranking", false),
        APPEARANCE("appearance", false),
        ECONOMY_METRICS("economy_metrics", false);

        private final String key;
        private final boolean defaultEnabled;

        Feature(String key, boolean defaultEnabled) {
            this.key = key;
            this.defaultEnabled = defaultEnabled;
        }
    }

    private final Map<Feature, State> states;

    private ClanFeatureFlags(Map<Feature, State> states) {
        this.states = new EnumMap<>(states);
    }

    public static ClanFeatureFlags gI() {
        return INSTANCE;
    }

    public static ClanFeatureFlags load(Path path) {
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            values.load(input);
        } catch (IOException error) {
            Logger.warning("Không đọc được " + path + ", dùng feature flag bang mặc định.\n");
        }
        return from(values);
    }

    public static ClanFeatureFlags from(Properties values) {
        Properties safeValues = values == null ? new Properties() : values;
        EnumMap<Feature, State> result = new EnumMap<>(Feature.class);
        for (Feature feature : Feature.values()) {
            boolean enabled = booleanValue(safeValues, feature.key + ".enabled", feature.defaultEnabled);
            boolean mutations = booleanValue(safeValues, feature.key + ".mutations_enabled", enabled);
            result.put(feature, new State(enabled, enabled && mutations));
        }
        return new ClanFeatureFlags(result);
    }

    public boolean isEnabled(Feature feature) {
        State state = states.get(feature);
        return state != null && state.enabled;
    }

    public boolean canMutate(Feature feature) {
        State state = states.get(feature);
        return state != null && state.mutationsEnabled;
    }

    private static boolean booleanValue(Properties values, String key, boolean fallback) {
        String raw = values.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes", "on" -> true;
            case "false", "0", "no", "off" -> false;
            default -> fallback;
        };
    }

    private record State(boolean enabled, boolean mutationsEnabled) {
    }
}
