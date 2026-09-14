package nro.models.social;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import nro.config.ConfigPaths;
import nro.models.utils.Logger;

/** Fail-closed rollout switches for social v2. */
public final class SocialV2FeatureFlags {

    public static final String ENABLED_KEY = "social_v2.enabled";
    public static final String MIGRATION_ENABLED_KEY = "social_v2.migration_enabled";

    private static final Path DEFAULT_PATH = ConfigPaths.social("social_features.properties");
    private static final SocialV2FeatureFlags INSTANCE = load(DEFAULT_PATH);

    private final boolean enabled;
    private final boolean migrationEnabled;

    private SocialV2FeatureFlags(boolean enabled, boolean migrationEnabled) {
        this.enabled = enabled;
        this.migrationEnabled = enabled && migrationEnabled;
    }

    public static SocialV2FeatureFlags gI() {
        return INSTANCE;
    }

    public static SocialV2FeatureFlags load(Path path) {
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            values.load(input);
        } catch (IOException error) {
            Logger.warning("Không đọc được " + path + ", social v2 được tắt.\n");
        }
        return from(values);
    }

    public static SocialV2FeatureFlags from(Properties values) {
        Properties safeValues = values == null ? new Properties() : values;
        boolean enabled = booleanValue(safeValues, ENABLED_KEY, false);
        boolean migrationEnabled = booleanValue(safeValues, MIGRATION_ENABLED_KEY, false);
        return new SocialV2FeatureFlags(enabled, migrationEnabled);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isMigrationEnabled() {
        return migrationEnabled;
    }

    public boolean allowsClientVersion(int clientVersion) {
        return enabled && SocialV2Protocol.isV2ClientVersion(clientVersion);
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
}
