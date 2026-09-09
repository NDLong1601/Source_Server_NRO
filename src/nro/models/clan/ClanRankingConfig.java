package nro.models.clan;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import nro.models.utils.Logger;

/** Bounded phase-5C ranking configuration. */
public final class ClanRankingConfig {

    private static final Path DEFAULT_PATH = Path.of("data", "clan_ranking.properties");
    private static final int PROTOCOL_MAX_PAGE_SIZE = 50;
    private static final int PROTOCOL_MAX_PAGE = 65_535;

    private final int defaultPageSize;
    private final int maxPageSize;
    private final int maxPage;
    private final int refreshSeconds;
    private final int legacyTopSize;

    private ClanRankingConfig(int defaultPageSize, int maxPageSize, int maxPage,
            int refreshSeconds, int legacyTopSize) {
        this.defaultPageSize = defaultPageSize;
        this.maxPageSize = maxPageSize;
        this.maxPage = maxPage;
        this.refreshSeconds = refreshSeconds;
        this.legacyTopSize = legacyTopSize;
    }

    public static ClanRankingConfig load() {
        return load(DEFAULT_PATH);
    }

    public static ClanRankingConfig load(Path path) {
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            values.load(input);
        } catch (IOException error) {
            Logger.warning("Không đọc được " + path + ", dùng cấu hình xếp hạng bang mặc định.\n");
        }
        return from(values);
    }

    public static ClanRankingConfig from(Properties values) {
        Properties safe = values == null ? new Properties() : values;
        int maxSize = boundedInt(safe, "max_page_size", 50, 1, PROTOCOL_MAX_PAGE_SIZE);
        int defaultSize = boundedInt(safe, "default_page_size", 20, 1, maxSize);
        int pageLimit = boundedInt(safe, "max_page", 1_000, 0, PROTOCOL_MAX_PAGE);
        int refresh = boundedInt(safe, "refresh_seconds", 30, 1, 3_600);
        int legacySize = boundedInt(safe, "legacy_top_size", 50, 1, maxSize);
        return new ClanRankingConfig(defaultSize, maxSize, pageLimit, refresh, legacySize);
    }

    public int normalizePage(int page) {
        return Math.max(0, Math.min(maxPage, page));
    }

    public int normalizePageSize(int pageSize) {
        return pageSize <= 0 ? defaultPageSize : Math.min(maxPageSize, pageSize);
    }

    public long refreshMillis() {
        return refreshSeconds * 1_000L;
    }

    public int legacyTopSize() {
        return legacyTopSize;
    }

    private static int boundedInt(Properties values, String key, int fallback, int min, int max) {
        String raw = values.getProperty(key);
        if (raw == null) {
            return Math.max(min, Math.min(max, fallback));
        }
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(raw.trim())));
        } catch (NumberFormatException ignored) {
            return Math.max(min, Math.min(max, fallback));
        }
    }
}
