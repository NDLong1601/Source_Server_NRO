package nro.models.clan;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import nro.models.data.LocalManager;
import nro.models.network.Message;
import nro.models.player.Player;
import nro.models.services.Service;
import nro.models.utils.Logger;

/** Server-authoritative phase-5B Clan Value calculation and materialization. */
public final class ClanValueService {

    public static final byte REQUEST_VIEW = 126;
    public static final byte RESPONSE_SNAPSHOT = 126;

    private static final ClanValueService INSTANCE = new ClanValueService();

    private final ClanValueConfig config = ClanValueConfig.load();
    private final Map<Integer, CacheState> cache = new ConcurrentHashMap<>();
    private final AtomicLong revision = new AtomicLong();
    private volatile boolean schemaReady;

    private ClanValueService() {
    }

    public static ClanValueService gI() {
        return INSTANCE;
    }

    public synchronized void ensureSchema(Connection connection) throws SQLException {
        if (schemaReady) {
            return;
        }
        ensureClanColumn(connection, "clan_value", "BIGINT NOT NULL DEFAULT 0");
        ensureClanColumn(connection, "clan_value_version", "BIGINT NOT NULL DEFAULT 0");
        ensureClanColumn(connection, "clan_value_formula_version", "INT NOT NULL DEFAULT 0");
        ensureClanColumn(connection, "clan_achievement_score", "BIGINT NOT NULL DEFAULT 0");
        boolean hasIndex;
        try (PreparedStatement ps = connection.prepareStatement(
                "SHOW INDEX FROM clan WHERE Key_name=?")) {
            ps.setString(1, "idx_clan_value");
            try (ResultSet rs = ps.executeQuery()) {
                hasIndex = rs.next();
            }
        }
        if (!hasIndex) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE clan ADD INDEX idx_clan_value (clan_value, id)");
            }
        }
        schemaReady = true;
    }

    private void ensureClanColumn(Connection connection, String columnName, String definition) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SHOW COLUMNS FROM clan LIKE ?")) {
            ps.setString(1, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE clan ADD COLUMN " + columnName + " " + definition);
        }
    }

    /** Startup load uses the caller's connection so a one-connection pool cannot deadlock. */
    public void loadForClan(Connection connection, Clan clan) throws SQLException {
        if (connection == null || clan == null || clan.id < 0) {
            return;
        }
        ensureSchema(connection);
        synchronized (clan) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT clan_value,clan_value_version,clan_value_formula_version,"
                    + "clan_achievement_score FROM clan WHERE id=?")) {
                ps.setInt(1, clan.id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        clan.clanValue = Math.max(0L, rs.getLong(1));
                        clan.clanValueVersion = Math.max(0L, rs.getLong(2));
                        clan.clanValueFormulaVersion = Math.max(0, rs.getInt(3));
                        clan.clanAchievementScore = Math.max(0L, rs.getLong(4));
                    }
                }
            }
            ClanTreeService.ValueState tree = readTreeValue(connection, clan.id);
            CacheState calculated = calculate(clan, tree);
            if (ClanFeatureFlags.gI().canMutate(ClanFeatureFlags.Feature.VALUE)
                    && needsPersistence(clan, calculated.score)) {
                persist(connection, clan, calculated.score);
                calculated = calculated.withVersion(clan.clanValueVersion);
            } else {
                calculated = calculated.withVersion(clan.clanValueVersion);
            }
            cache.put(clan.id, calculated);
        }
    }

    public void initializeClan(Clan clan) {
        if (clan == null || clan.id < 0) {
            return;
        }
        try (Connection connection = LocalManager.getConnection()) {
            loadForClan(connection, clan);
        } catch (Exception error) {
            Logger.logException(ClanValueService.class, error, "Không khởi tạo được Clan Value");
        }
    }

    public void disposeClan(int clanId) {
        if (clanId >= 0 && cache.remove(clanId) != null) {
            revision.incrementAndGet();
        }
    }

    /** Monotonic invalidation token consumed by the phase-5C leaderboard cache. */
    public long revision() {
        return revision.get();
    }

    public Snapshot snapshot(Clan clan) {
        if (clan == null || clan.id < 0) {
            return disabledSnapshot();
        }
        boolean enabled = ClanFeatureFlags.gI().isEnabled(ClanFeatureFlags.Feature.VALUE);
        ClanTreeService.ValueState tree = ClanTreeService.gI().valueState(clan.id);
        synchronized (clan) {
            CacheState calculated = calculate(clan, tree);
            CacheState previous = cache.get(clan.id);
            if (previous != null && previous.sameSourcesAndScore(calculated)) {
                return new Snapshot(enabled, calculated.score, previous.version);
            }
            long version = clan.clanValueVersion;
            boolean scoreChanged = previous != null && !previous.score.equals(calculated.score);
            if (enabled && ClanFeatureFlags.gI().canMutate(ClanFeatureFlags.Feature.VALUE)
                    && (needsPersistence(clan, calculated.score) || scoreChanged)) {
                try (Connection connection = LocalManager.getConnection()) {
                    ensureSchema(connection);
                    persist(connection, clan, calculated.score);
                    version = clan.clanValueVersion;
                } catch (Exception error) {
                    Logger.logException(ClanValueService.class, error, "Không làm mới được Clan Value");
                    // Do not cache a failed materialization; the next snapshot retries persistence.
                    return new Snapshot(enabled, calculated.score, version);
                }
            }
            CacheState refreshed = calculated.withVersion(version);
            cache.put(clan.id, refreshed);
            return new Snapshot(enabled, refreshed.score, refreshed.version);
        }
    }

    public void handleRequest(Player player, byte action) {
        if (action != REQUEST_VIEW) {
            return;
        }
        if (!ClanFeatureFlags.gI().isEnabled(ClanFeatureFlags.Feature.VALUE)) {
            notify(player, "Giá trị bang đang tạm khóa.");
            return;
        }
        if (player == null || player.clan == null) {
            notify(player, "Bạn chưa có bang hội.");
            return;
        }
        sendSnapshot(player);
    }

    public void sendSnapshot(Player player) {
        if (player == null || player.clan == null
                || !ClanFeatureFlags.gI().isEnabled(ClanFeatureFlags.Feature.VALUE)) {
            return;
        }
        Clan clan = player.clan;
        Snapshot snapshot = snapshot(clan);
        Message message = null;
        try {
            ClanValueConfig.Score score = snapshot.score;
            message = new Message(127);
            message.writer().writeByte(RESPONSE_SNAPSHOT);
            message.writer().writeInt(clan.id);
            message.writer().writeByte(1);
            message.writer().writeByte(score.formulaVersion());
            message.writer().writeLong(score.totalValue());
            message.writer().writeLong(score.clanLevelScore());
            message.writer().writeLong(score.spentPotentialScore());
            message.writer().writeLong(score.treeLevelScore());
            message.writer().writeLong(score.achievementScore());
            message.writer().writeLong(score.weeklyActivityScore());
            message.writer().writeLong(snapshot.version);
            player.sendMessage(message);
        } catch (Exception error) {
            Logger.logException(ClanValueService.class, error, "Không gửi được Clan Value");
        } finally {
            if (message != null) {
                message.cleanup();
            }
        }
    }

    private CacheState calculate(Clan clan, ClanTreeService.ValueState tree) {
        clan.ensureWeeklyContract();
        int spentPotential = Math.max(0, clan.potentialTotal - clan.potentialUnspent);
        ClanValueConfig.Score score = config.score(clan.level, spentPotential, tree.level(),
                clan.clanAchievementScore, clan.weeklyContractProgress, clan.weeklyContractTarget);
        return new CacheState(score, clan.clanValueVersion, tree.version(), clan.progressionVersion,
                clan.weeklyContractWeek, clan.weeklyContractProgress, clan.weeklyContractTarget);
    }

    private ClanTreeService.ValueState readTreeValue(Connection connection, int clanId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT level,version FROM clan_tree WHERE clan_id=?")) {
            ps.setInt(1, clanId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new ClanTreeService.ValueState(Math.max(0, rs.getInt(1)), Math.max(0L, rs.getLong(2)));
                }
            }
        }
        return new ClanTreeService.ValueState(0, 0L);
    }

    private boolean needsPersistence(Clan clan, ClanValueConfig.Score score) {
        return clan.clanValue != score.totalValue()
                || clan.clanValueFormulaVersion != score.formulaVersion();
    }

    private void persist(Connection connection, Clan clan, ClanValueConfig.Score score) throws SQLException {
        long nextVersion = clan.clanValueVersion == Long.MAX_VALUE
                ? Long.MAX_VALUE : clan.clanValueVersion + 1L;
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE clan SET clan_value=?,clan_value_version=?,clan_value_formula_version=? "
                + "WHERE id=?")) {
            ps.setLong(1, score.totalValue());
            ps.setLong(2, nextVersion);
            ps.setInt(3, score.formulaVersion());
            ps.setInt(4, clan.id);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Không tìm thấy bang để lưu Clan Value");
            }
        }
        clan.clanValue = score.totalValue();
        clan.clanValueVersion = nextVersion;
        clan.clanValueFormulaVersion = score.formulaVersion();
        revision.incrementAndGet();
    }

    private Snapshot disabledSnapshot() {
        return new Snapshot(false, config.score(0, 0, 0, 0L, 0, 0), 0L);
    }

    private static void notify(Player player, String text) {
        if (player != null) {
            Service.gI().sendThongBao(player, text);
        }
    }

    public record Snapshot(boolean enabled, ClanValueConfig.Score score, long version) {
    }

    private record CacheState(ClanValueConfig.Score score, long version, long treeVersion,
            long progressionVersion, long weeklyWeek, int weeklyProgress, int weeklyTarget) {

        CacheState withVersion(long nextVersion) {
            return new CacheState(score, nextVersion, treeVersion, progressionVersion,
                    weeklyWeek, weeklyProgress, weeklyTarget);
        }

        boolean sameSourcesAndScore(CacheState other) {
            return other != null && treeVersion == other.treeVersion
                    && progressionVersion == other.progressionVersion
                    && weeklyWeek == other.weeklyWeek
                    && weeklyProgress == other.weeklyProgress
                    && weeklyTarget == other.weeklyTarget
                    && score.equals(other.score);
        }
    }
}
