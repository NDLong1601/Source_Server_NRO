package nro.models.activity;

import com.google.gson.Gson;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import nro.models.data.LocalManager;
import nro.models.utils.Logger;

/**
 * Polls the published config revision. activity.properties is always available
 * as the fail-closed fallback and emergency kill switch.
 */
public final class ActivityConfigService {

    private static final ActivityConfigService INSTANCE = new ActivityConfigService();
    private static final Path CONFIG_PATH = Paths.get("activity.properties");
    private static final Gson GSON = new Gson();

    private final AtomicReference<ActivityConfig> current = new AtomicReference<>(ActivityConfig.defaults());
    private volatile ActivityConfig fileSnapshot = ActivityConfig.defaults();
    private volatile ActivityConfig databaseSnapshot;
    private volatile long fileModifiedTime = Long.MIN_VALUE;
    private volatile long lastCheckAt;
    private volatile long loadedDatabaseRevision = Long.MIN_VALUE;

    private ActivityConfigService() {
    }

    public static ActivityConfigService gI() {
        return INSTANCE;
    }

    public void load(Connection connection) throws SQLException {
        synchronized (this) {
            reloadFile(true);
            ensureSchema(connection);
            reloadDatabase(connection, true);
        }
    }

    public ActivityConfig getCurrent() {
        long now = System.currentTimeMillis();
        if (now - lastCheckAt < current.get().getPollIntervalMs()) {
            return current.get();
        }
        synchronized (this) {
            if (now - lastCheckAt < current.get().getPollIntervalMs()) {
                return current.get();
            }
            lastCheckAt = now;
            reloadFile(false);
            try (Connection connection = LocalManager.getConnection()) {
                reloadDatabase(connection, false);
            } catch (Exception e) {
                if (fileSnapshot.isFailClosed()) {
                    current.set(fileSnapshot.disabledForFailure());
                }
                Logger.error("Cannot poll Activity Points config; keeping fail-closed snapshot.\n");
            }
            return current.get();
        }
    }

    private void reloadFile(boolean force) {
        try {
            long modified = Files.exists(CONFIG_PATH)
                    ? Files.getLastModifiedTime(CONFIG_PATH).toMillis() : -1L;
            if (!force && modified == fileModifiedTime) {
                return;
            }
            Properties properties = new Properties();
            if (modified >= 0) {
                try (InputStream input = Files.newInputStream(CONFIG_PATH)) {
                    properties.load(input);
                }
            }
            fileSnapshot = ActivityConfig.fromProperties(properties, Math.max(0L, modified));
            fileModifiedTime = modified;
            if (loadedDatabaseRevision == Long.MIN_VALUE) {
                current.set(fileSnapshot);
            }
        } catch (Exception e) {
            current.set(ActivityConfig.defaults().disabledForFailure());
            Logger.error("Cannot read activity.properties; Activity Points are disabled.\n");
        }
    }

    private void ensureSchema(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("CREATE TABLE IF NOT EXISTS activity_config_version ("
                + "id BIGINT NOT NULL AUTO_INCREMENT,"
                + "version_no BIGINT NOT NULL,"
                + "status VARCHAR(16) NOT NULL,"
                + "schema_version INT NOT NULL,"
                + "config_json LONGTEXT NOT NULL,"
                + "note VARCHAR(500) NULL,"
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "published_at TIMESTAMP NULL DEFAULT NULL,"
                + "PRIMARY KEY (id), UNIQUE KEY uk_activity_config_version (version_no)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci")) {
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement("CREATE TABLE IF NOT EXISTS activity_runtime_config ("
                + "id TINYINT NOT NULL,"
                + "published_config_id BIGINT NULL,"
                + "revision BIGINT NOT NULL DEFAULT 0,"
                + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                + "PRIMARY KEY (id)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci")) {
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement("INSERT IGNORE INTO activity_config_version "
                + "(version_no,status,schema_version,config_json,note,published_at) VALUES (1,'PUBLISHED',1,?,'Phase 1 baseline',NOW())")) {
            ps.setString(1, defaultConfigJson());
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement("INSERT IGNORE INTO activity_runtime_config "
                + "(id,published_config_id,revision) SELECT 1,id,1 FROM activity_config_version WHERE version_no=1")) {
            ps.executeUpdate();
        }
    }

    private void reloadDatabase(Connection connection, boolean force) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT r.revision,v.config_json "
                + "FROM activity_runtime_config r LEFT JOIN activity_config_version v ON v.id=r.published_config_id "
                + "WHERE r.id=1 LIMIT 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    loadedDatabaseRevision = Long.MIN_VALUE;
                    current.set(fileSnapshot);
                    return;
                }
                long revision = rs.getLong("revision");
                if (!force && revision == loadedDatabaseRevision) {
                    current.set(applyEmergency(databaseSnapshot == null ? fileSnapshot : databaseSnapshot));
                    return;
                }
                String raw = rs.getString("config_json");
                StoredConfig stored = raw == null || raw.isBlank() ? null : GSON.fromJson(raw, StoredConfig.class);
                if (stored == null || stored.global == null) {
                    throw new SQLException("Published activity config is empty or invalid.");
                }
                ActivityGlobal global = stored.global;
                ActivityConfig loaded = fileSnapshot.withDatabaseValues(global.enabled, global.shadowMode,
                        global.rewardEnabled, global.timezone, global.dailyResetHour, global.weeklyResetDay,
                        global.dailyMax, global.qualifiedDailyPoints, global.diversityCategoryCount,
                        global.diversityBonus, revision,
                        parseTiers(stored.dailyTiers, ActivityPeriodType.DAILY,
                                global.dailyMax == null ? fileSnapshot.getDailyMax() : global.dailyMax),
                        parseTiers(stored.weeklyTiers, ActivityPeriodType.WEEKLY,
                                global.dailyMax == null ? fileSnapshot.getDailyMax() : global.dailyMax),
                        parseSources(stored.sources,
                                global.dailyMax == null ? fileSnapshot.getDailyMax() : global.dailyMax));
                databaseSnapshot = loaded;
                current.set(applyEmergency(databaseSnapshot));
                loadedDatabaseRevision = revision;
                Logger.success(Logger.PURPLE + "Loaded Activity Points config revision " + revision + "\n");
            }
        }
    }

    private ActivityConfig applyEmergency(ActivityConfig config) {
        if (!fileSnapshot.isEmergencyDisabled()) {
            return config;
        }
        return new ActivityConfig(false, true, false, true, fileSnapshot.isFailClosed(),
                config.getZoneId(), config.getDailyResetHour(), config.getWeeklyResetDay(),
                config.getDailyMax(), config.getQualifiedDailyPoints(), config.getDiversityCategoryCount(),
                config.getDiversityBonus(), fileSnapshot.getPollIntervalMs(), config.getRevision(),
                config.getDailyTiers(), config.getWeeklyTiers(), config.getSourceRules());
    }

    private String defaultConfigJson() {
        StoredConfig config = new StoredConfig();
        config.schemaVersion = ActivityState.SCHEMA_VERSION;
        config.global = new ActivityGlobal();
        config.global.enabled = fileSnapshot.isEnabled();
        config.global.shadowMode = fileSnapshot.isShadowMode();
        config.global.rewardEnabled = fileSnapshot.isRewardsEnabled();
        config.global.timezone = fileSnapshot.getZoneId().getId();
        config.global.dailyResetHour = fileSnapshot.getDailyResetHour();
        config.global.weeklyResetDay = fileSnapshot.getWeeklyResetDay().name();
        config.global.dailyMax = fileSnapshot.getDailyMax();
        config.global.qualifiedDailyPoints = fileSnapshot.getQualifiedDailyPoints();
        config.global.diversityCategoryCount = fileSnapshot.getDiversityCategoryCount();
        config.global.diversityBonus = fileSnapshot.getDiversityBonus();
        config.dailyTiers = toStoredTiers(fileSnapshot.getDailyTiers());
        config.weeklyTiers = toStoredTiers(fileSnapshot.getWeeklyTiers());
        config.sources = toStoredSources(fileSnapshot.getSourceRules());
        return GSON.toJson(config);
    }

    private Map<ActivityType, ActivitySourceRule> parseSources(List<StoredSource> storedSources,
            int dailyMax) throws SQLException {
        if (storedSources == null) {
            return ActivityConfig.defaultSourceRules();
        }
        if (storedSources.size() != ActivityType.values().length) {
            throw new SQLException("Activity source config must include every stable source key.");
        }
        Map<ActivityType, ActivitySourceRule> rules = new EnumMap<>(ActivityType.class);
        for (StoredSource raw : storedSources) {
            if (raw == null || raw.key == null) {
                throw new SQLException("Invalid Activity source config.");
            }
            ActivityType type;
            ActivityCategory category;
            try {
                type = ActivityType.valueOf(raw.key.trim().toUpperCase());
                category = raw.category == null ? type.getCategory()
                        : ActivityCategory.valueOf(raw.category.trim().toUpperCase());
            } catch (Exception e) {
                throw new SQLException("Unknown Activity source key or category.", e);
            }
            if (rules.containsKey(type)) {
                throw new SQLException("Duplicate Activity source " + type.name() + ".");
            }
            int points = raw.points == null ? type.getDefaultPoints() : raw.points;
            int dailyCap = raw.dailyCap == null ? type.getDailyCap() : raw.dailyCap;
            String groupKey = raw.groupKey == null ? type.getGroupKey() : raw.groupKey.trim();
            int groupDailyCap = raw.groupDailyCap == null ? type.getGroupDailyCap() : raw.groupDailyCap;
            boolean enabled = raw.enabled == null || raw.enabled;
            if (groupKey == null) {
                groupKey = "";
            }
            if (points < 0 || points > dailyMax || dailyCap < 0 || dailyCap > dailyMax
                    || (enabled && (points <= 0 || dailyCap < points))) {
                throw new SQLException("Invalid points/cap for Activity source " + type.name() + ".");
            }
            if (!groupKey.isBlank()) {
                if (!groupKey.matches("[A-Z][A-Z0-9_]{1,31}") || groupDailyCap < points || groupDailyCap > dailyMax) {
                    throw new SQLException("Invalid group cap for Activity source " + type.name() + ".");
                }
            } else {
                groupKey = null;
                groupDailyCap = 0;
            }
            rules.put(type, new ActivitySourceRule(enabled, category,
                    points, dailyCap, groupKey, groupDailyCap));
        }
        if (rules.size() != ActivityType.values().length) {
            throw new SQLException("Activity source config is missing a stable source key.");
        }
        return rules;
    }

    private List<ActivityTier> parseTiers(List<StoredTier> storedTiers, ActivityPeriodType period,
            int dailyMax) throws SQLException {
        if (storedTiers == null) {
            return period == ActivityPeriodType.DAILY
                    ? ActivityConfig.defaultDailyTiers() : ActivityConfig.defaultWeeklyTiers();
        }
        if (storedTiers.size() > 50) {
            throw new SQLException("Too many Activity " + period + " tiers.");
        }
        List<ActivityTier> tiers = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<Integer> bits = new HashSet<>();
        Set<Integer> thresholds = new HashSet<>();
        int maxThreshold = Math.max(1, Math.min(1_000, dailyMax))
                * (period == ActivityPeriodType.DAILY ? 1 : 7);
        for (StoredTier raw : storedTiers) {
            if (raw == null || raw.id == null || !raw.id.matches("[A-Z][A-Z0-9_]{2,63}")) {
                throw new SQLException("Invalid Activity " + period + " tier id.");
            }
            if (!ids.add(raw.id) || raw.claimBit == null || raw.claimBit < 0 || raw.claimBit > 62
                    || !bits.add(raw.claimBit) || raw.threshold == null || raw.threshold <= 0
                    || raw.threshold > maxThreshold || !thresholds.add(raw.threshold)) {
                throw new SQLException("Invalid or duplicate Activity " + period + " tier " + raw.id + ".");
            }
            int minQualifiedDays = raw.minQualifiedDays == null ? 0 : raw.minQualifiedDays;
            if (minQualifiedDays < 0 || minQualifiedDays > 7) {
                throw new SQLException("Invalid qualified day requirement for Activity tier " + raw.id + ".");
            }
            boolean enabled = raw.enabled == null || raw.enabled;
            List<ActivityReward> rewards = parseRewards(raw.rewards, raw.id);
            if (enabled && rewards.isEmpty()) {
                throw new SQLException("Enabled Activity tier " + raw.id + " has no rewards.");
            }
            tiers.add(new ActivityTier(raw.id, raw.claimBit, enabled, raw.threshold,
                    minQualifiedDays, rewards));
        }
        return tiers;
    }

    private List<ActivityReward> parseRewards(List<StoredReward> storedRewards, String tierId) throws SQLException {
        List<ActivityReward> rewards = new ArrayList<>();
        if (storedRewards == null) {
            return rewards;
        }
        for (StoredReward raw : storedRewards) {
            if (raw == null || raw.kind == null) {
                throw new SQLException("Invalid reward in Activity tier " + tierId + ".");
            }
            ActivityReward.Kind kind;
            try {
                kind = ActivityReward.Kind.valueOf(raw.kind.trim().toUpperCase());
            } catch (Exception e) {
                throw new SQLException("Unknown reward kind in Activity tier " + tierId + ".", e);
            }
            int min = raw.quantityMin == null ? 0 : raw.quantityMin;
            int max = raw.quantityMax == null ? min : raw.quantityMax;
            if (min <= 0 || max < min || max > 100_000_000) {
                throw new SQLException("Invalid reward quantity in Activity tier " + tierId + ".");
            }
            int itemId = raw.itemId == null ? 0 : raw.itemId;
            if (kind == ActivityReward.Kind.ITEM && (itemId <= 0 || itemId > Short.MAX_VALUE)) {
                throw new SQLException("Invalid item id in Activity tier " + tierId + ".");
            }
            int gender = raw.gender == null ? 3 : raw.gender;
            if (gender < -1 || gender > 3) {
                throw new SQLException("Invalid reward gender in Activity tier " + tierId + ".");
            }
            List<ActivityRewardOption> options = new ArrayList<>();
            Set<Integer> optionIds = new HashSet<>();
            if (raw.options != null) {
                for (StoredRewardOption option : raw.options) {
                    if (option == null || option.id == null || option.id < 0 || option.id > Short.MAX_VALUE
                            || !optionIds.add(option.id)) {
                        throw new SQLException("Invalid reward option in Activity tier " + tierId + ".");
                    }
                    int paramMin = option.paramMin == null ? 0 : option.paramMin;
                    int paramMax = option.paramMax == null ? paramMin : option.paramMax;
                    if (paramMin < Short.MIN_VALUE || paramMax < paramMin || paramMax > Short.MAX_VALUE) {
                        throw new SQLException("Invalid reward option range in Activity tier " + tierId + ".");
                    }
                    options.add(new ActivityRewardOption(option.id, paramMin, paramMax));
                }
            }
            int expiryMin = raw.expiryDaysMin == null ? 0 : raw.expiryDaysMin;
            int expiryMax = raw.expiryDaysMax == null ? expiryMin : raw.expiryDaysMax;
            if (raw.expiry != null) {
                for (StoredExpiry expiry : raw.expiry) {
                    if (expiry != null && "days".equalsIgnoreCase(expiry.mode)) {
                        expiryMin = expiry.daysMin == null ? 0 : expiry.daysMin;
                        expiryMax = expiry.daysMax == null ? expiryMin : expiry.daysMax;
                        break;
                    }
                }
            }
            if (expiryMin < 0 || expiryMax < expiryMin || expiryMax > 36_500) {
                throw new SQLException("Invalid expiry in Activity tier " + tierId + ".");
            }
            boolean bound = raw.bound != null ? raw.bound
                    : raw.bindMode == null || !"UNBOUND".equalsIgnoreCase(raw.bindMode);
            rewards.add(new ActivityReward(kind, itemId, min, max, gender, bound,
                    Boolean.TRUE.equals(raw.initBaseOptions), Boolean.TRUE.equals(raw.useDefaultOptions),
                    options, expiryMin, expiryMax));
        }
        return rewards;
    }

    private List<StoredTier> toStoredTiers(List<ActivityTier> tiers) {
        List<StoredTier> serialized = new ArrayList<>();
        for (ActivityTier tier : tiers) {
            StoredTier target = new StoredTier();
            target.id = tier.getId();
            target.claimBit = tier.getClaimBit();
            target.enabled = tier.isEnabled();
            target.threshold = tier.getThreshold();
            target.minQualifiedDays = tier.getMinQualifiedDays();
            target.rewards = new ArrayList<>();
            for (ActivityReward reward : tier.getRewards()) {
                StoredReward output = new StoredReward();
                output.kind = reward.getKind().name();
                output.itemId = reward.getItemId();
                output.quantityMin = reward.getQuantityMin();
                output.quantityMax = reward.getQuantityMax();
                output.gender = reward.getGender();
                output.bindMode = reward.isBound() ? "BOUND" : "UNBOUND";
                output.initBaseOptions = reward.isInitBaseOptions();
                output.useDefaultOptions = reward.isUseDefaultOptions();
                output.expiryDaysMin = reward.getExpiryDaysMin();
                output.expiryDaysMax = reward.getExpiryDaysMax();
                output.options = new ArrayList<>();
                for (ActivityRewardOption option : reward.getOptions()) {
                    StoredRewardOption optionOutput = new StoredRewardOption();
                    optionOutput.id = option.getOptionId();
                    optionOutput.paramMin = option.getParamMin();
                    optionOutput.paramMax = option.getParamMax();
                    output.options.add(optionOutput);
                }
                target.rewards.add(output);
            }
            serialized.add(target);
        }
        return serialized;
    }

    private List<StoredSource> toStoredSources(Map<ActivityType, ActivitySourceRule> rules) {
        List<StoredSource> serialized = new ArrayList<>();
        for (ActivityType type : ActivityType.values()) {
            ActivitySourceRule rule = rules.get(type);
            StoredSource target = new StoredSource();
            target.key = type.name();
            target.enabled = rule.isEnabled();
            target.category = rule.getCategory().name();
            target.points = rule.getPoints();
            target.dailyCap = rule.getDailyCap();
            target.groupKey = rule.getGroupKey();
            target.groupDailyCap = rule.getGroupDailyCap();
            serialized.add(target);
        }
        return serialized;
    }

    private static final class StoredConfig {
        int schemaVersion;
        ActivityGlobal global;
        List<StoredTier> dailyTiers;
        List<StoredTier> weeklyTiers;
        List<StoredSource> sources;
    }

    private static final class ActivityGlobal {
        Boolean enabled;
        Boolean shadowMode;
        Boolean rewardEnabled;
        String timezone;
        Integer dailyResetHour;
        String weeklyResetDay;
        Integer dailyMax;
        Integer qualifiedDailyPoints;
        Integer diversityCategoryCount;
        Integer diversityBonus;
    }

    private static final class StoredTier {
        String id;
        Integer claimBit;
        Boolean enabled;
        Integer threshold;
        Integer minQualifiedDays;
        List<StoredReward> rewards;
    }

    private static final class StoredReward {
        String kind;
        Integer itemId;
        Integer quantityMin;
        Integer quantityMax;
        Integer gender;
        String bindMode;
        Boolean bound;
        Boolean initBaseOptions;
        Boolean useDefaultOptions;
        List<StoredRewardOption> options;
        Integer expiryDaysMin;
        Integer expiryDaysMax;
        List<StoredExpiry> expiry;
    }

    private static final class StoredRewardOption {
        Integer id;
        Integer paramMin;
        Integer paramMax;
    }

    private static final class StoredExpiry {
        String mode;
        Integer daysMin;
        Integer daysMax;
    }

    private static final class StoredSource {
        String key;
        Boolean enabled;
        String category;
        Integer points;
        Integer dailyCap;
        String groupKey;
        Integer groupDailyCap;
    }
}
