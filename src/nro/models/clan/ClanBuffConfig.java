package nro.models.clan;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import nro.models.utils.Logger;

/** Validated effect, duration and recovery settings for temporary clan buffs. */
public final class ClanBuffConfig {

    private static final Path DEFAULT_PATH = Path.of("data", "clan_buff.properties");
    private static final int MIN_ITEM_ID = 2252;
    private static final int MAX_ITEM_ID = 2269;

    private final int[] effectPercents;
    private final int shortDurationDays;
    private final int mediumDurationDays;
    private final int longDurationDays;
    private final long recoveryIntervalMillis;

    private ClanBuffConfig(Properties values) {
        effectPercents = new int[] {
            integer(values, "hp_regen_percent", 1, 0, 100),
            integer(values, "ki_regen_percent", 1, 0, 100),
            integer(values, "attack_percent", 10, 0, 100),
            integer(values, "luck_percent", 10, 0, 100),
            integer(values, "power_percent", 15, 0, 100),
            integer(values, "mob_gold_percent", 20, 0, 100)
        };
        shortDurationDays = integer(values, "short_duration_days", 1, 1, 365);
        mediumDurationDays = Math.max(shortDurationDays,
                integer(values, "medium_duration_days", 3, 1, 365));
        longDurationDays = Math.max(mediumDurationDays,
                integer(values, "long_duration_days", 7, 1, 365));
        recoveryIntervalMillis = integer(values, "recovery_interval_seconds", 5, 1, 60) * 1_000L;
    }

    public static ClanBuffConfig load() {
        return load(DEFAULT_PATH);
    }

    public static ClanBuffConfig load(Path path) {
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            values.load(input);
        } catch (IOException error) {
            Logger.warning("Không đọc được " + path + ", dùng cấu hình buff bang mặc định.\n");
        }
        return from(values);
    }

    public static ClanBuffConfig from(Properties values) {
        return new ClanBuffConfig(values == null ? new Properties() : values);
    }

    public int effectPercent(int typeId) {
        return typeId < 0 || typeId >= effectPercents.length ? 0 : effectPercents[typeId];
    }

    public int durationDaysForItem(int itemId) {
        if (itemId < MIN_ITEM_ID || itemId > MAX_ITEM_ID) {
            return 0;
        }
        return switch ((itemId - MIN_ITEM_ID) % 3) {
            case 0 -> shortDurationDays;
            case 1 -> mediumDurationDays;
            default -> longDurationDays;
        };
    }

    public long recoveryIntervalMillis() {
        return recoveryIntervalMillis;
    }

    private static int integer(Properties values, String key, int fallback, int minimum, int maximum) {
        try {
            int value = Integer.parseInt(values.getProperty(key, String.valueOf(fallback)).trim());
            return Math.max(minimum, Math.min(maximum, value));
        } catch (RuntimeException error) {
            return Math.max(minimum, Math.min(maximum, fallback));
        }
    }
}
