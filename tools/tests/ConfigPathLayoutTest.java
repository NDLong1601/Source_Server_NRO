import java.nio.file.Path;
import nro.config.ConfigPaths;

/** Regression checks for the single on-disk configuration layout. */
public final class ConfigPathLayoutTest {

    public static void main(String[] args) {
        assertPath("server configuration", "config/Config.properties", ConfigPaths.server());
        assertPath("player configuration", "config/player.properties", ConfigPaths.player());
        assertPath("pet configuration", "config/pet.properties", ConfigPaths.pet());
        assertPath("task configuration", "config/task.properties", ConfigPaths.task());
        assertPath("combine configuration", "config/combine.properties", ConfigPaths.combine());
        assertPath("activity configuration", "config/activity.properties", ConfigPaths.activity());
        assertPath("skill mastery configuration", "config/skill_mastery.properties", ConfigPaths.skillMastery());
        assertPath("clan configuration", "config/clan/clan_buff.properties",
                ConfigPaths.clan("clan_buff.properties"));
        assertRejectedClanFile("../Config.properties");
        assertRejectedClanFile("clan_buff.properties.bak");
        System.out.println("ConfigPathLayoutTest: PASS");
    }

    private static void assertPath(String label, String expected, Path actual) {
        String normalized = actual.toString().replace('\\', '/');
        if (!expected.equals(normalized)) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + normalized);
        }
    }

    private static void assertRejectedClanFile(String filename) {
        try {
            ConfigPaths.clan(filename);
            throw new AssertionError("Expected unsafe clan file to be rejected: " + filename);
        } catch (IllegalArgumentException expected) {
            // Expected: clan configuration filenames are a closed allowlist.
        }
    }
}
