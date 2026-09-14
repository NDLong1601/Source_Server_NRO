package nro.config;

import java.nio.file.Path;
import java.util.Set;

/** Canonical, closed configuration layout shared by the server and admin tools. */
public final class ConfigPaths {

    private static final Path ROOT = Path.of("config");
    private static final Set<String> CLAN_FILES = Set.of(
            "clan_appearance.properties",
            "clan_buff.properties",
            "clan_chat.properties",
            "clan_economy.properties",
            "clan_features.properties",
            "clan_gift.properties",
            "clan_progression.properties",
            "clan_ranking.properties",
            "clan_shop.properties",
            "clan_tree.properties",
            "clan_value.properties"
    );
    private static final Set<String> SOCIAL_FILES = Set.of(
            "social_features.properties"
    );

    private ConfigPaths() {
    }

    public static Path server() { return ROOT.resolve("Config.properties"); }
    public static Path player() { return ROOT.resolve("player.properties"); }
    public static Path pet() { return ROOT.resolve("pet.properties"); }
    public static Path task() { return ROOT.resolve("task.properties"); }
    public static Path combine() { return ROOT.resolve("combine.properties"); }
    public static Path activity() { return ROOT.resolve("activity.properties"); }
    public static Path skillMastery() { return ROOT.resolve("skill_mastery.properties"); }

    public static Path clan(String filename) {
        if (!CLAN_FILES.contains(filename)) {
            throw new IllegalArgumentException("Unsupported clan configuration file: " + filename);
        }
        return ROOT.resolve("clan").resolve(filename);
    }

    public static Path social(String filename) {
        if (!SOCIAL_FILES.contains(filename)) {
            throw new IllegalArgumentException("Unsupported social configuration file: " + filename);
        }
        return ROOT.resolve("social").resolve(filename);
    }
}
