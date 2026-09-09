package nro.models.clan;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import nro.models.utils.Logger;

/** Validated retention and balancing thresholds for phase 5E. */
public final class ClanEconomyConfig {

    private static final Path DEFAULT_PATH = Path.of("data", "clan_economy.properties");

    private final int defaultLookbackDays;
    private final int maxLookbackDays;
    private final int retentionDays;
    private final int flushIntervalSeconds;
    private final int goldSinkMinimumPercent;
    private final int goldSinkMaximumPercent;
    private final int gemSinkMinimumPercent;
    private final int gemSinkMaximumPercent;
    private final int capsuleSinkMinimumPercent;
    private final int capsuleSinkMaximumPercent;

    private ClanEconomyConfig(Properties values) {
        maxLookbackDays = integer(values, "lookback_max_days", 90, 7, 365);
        defaultLookbackDays = integer(values, "lookback_default_days", 14, 1, maxLookbackDays);
        retentionDays = integer(values, "retention_days", 180, maxLookbackDays, 730);
        flushIntervalSeconds = integer(values, "flush_interval_seconds", 15, 5, 300);
        goldSinkMinimumPercent = integer(values, "gold_sink_ratio_min_percent", 70, 0, 1_000);
        goldSinkMaximumPercent = orderedMaximum(values, "gold_sink_ratio_max_percent", 110,
                goldSinkMinimumPercent);
        gemSinkMinimumPercent = integer(values, "gem_sink_ratio_min_percent", 50, 0, 1_000);
        gemSinkMaximumPercent = orderedMaximum(values, "gem_sink_ratio_max_percent", 150,
                gemSinkMinimumPercent);
        capsuleSinkMinimumPercent = integer(values, "capsule_sink_ratio_min_percent", 70, 0, 1_000);
        capsuleSinkMaximumPercent = orderedMaximum(values, "capsule_sink_ratio_max_percent", 130,
                capsuleSinkMinimumPercent);
    }

    public static ClanEconomyConfig load() {
        return load(DEFAULT_PATH);
    }

    public static ClanEconomyConfig load(Path path) {
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            values.load(input);
        } catch (IOException error) {
            Logger.warning("Không đọc được " + path + ", dùng cấu hình kinh tế bang mặc định.\n");
        }
        return from(values);
    }

    public static ClanEconomyConfig from(Properties values) {
        return new ClanEconomyConfig(values == null ? new Properties() : values);
    }

    public int defaultLookbackDays() { return defaultLookbackDays; }
    public int maxLookbackDays() { return maxLookbackDays; }
    public int retentionDays() { return retentionDays; }
    public int flushIntervalSeconds() { return flushIntervalSeconds; }
    public int goldSinkMinimumPercent() { return goldSinkMinimumPercent; }
    public int goldSinkMaximumPercent() { return goldSinkMaximumPercent; }
    public int gemSinkMinimumPercent() { return gemSinkMinimumPercent; }
    public int gemSinkMaximumPercent() { return gemSinkMaximumPercent; }
    public int capsuleSinkMinimumPercent() { return capsuleSinkMinimumPercent; }
    public int capsuleSinkMaximumPercent() { return capsuleSinkMaximumPercent; }

    private static int orderedMaximum(Properties values, String key, int fallback, int minimum) {
        return integer(values, key, fallback, minimum, 1_000);
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
