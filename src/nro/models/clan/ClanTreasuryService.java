package nro.models.clan;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import nro.models.data.LocalManager;
import nro.models.network.Message;
import nro.models.player.Player;
import nro.models.services.Service;
import nro.models.utils.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONValue;

/**
 * Persistent currency-only clan treasury. This deliberately has no withdraw
 * endpoint: all deposits are irreversible and only create a ledger record.
 */
public final class ClanTreasuryService {

    public static final byte REQUEST_VIEW = 100;
    public static final byte REQUEST_DEPOSIT_GOLD = 101;
    public static final byte REQUEST_DEPOSIT_GEM = 102;
    public static final byte REQUEST_LEDGER_PAGE = 103;

    public static final byte RESPONSE_SNAPSHOT = 110;
    public static final byte RESPONSE_LEDGER_PAGE = 111;

    public static final byte CURRENCY_GOLD = 1;
    public static final byte CURRENCY_GEM = 2;

    private static final int LEDGER_PAGE_SIZE = 50;
    private static final int REQUEST_ID_MAX_LENGTH = 80;
    private static final long GEM_CONTRIBUTION_SCORE = 1_000_000L;
    private static final ClanTreasuryService INSTANCE = new ClanTreasuryService();

    private volatile boolean schemaReady;

    private ClanTreasuryService() {
    }

    public static ClanTreasuryService gI() {
        return INSTANCE;
    }

    /** Creates the phase-1 schema without requiring a manual SQL deployment. */
    public synchronized void ensureSchema(Connection connection) throws SQLException {
        if (schemaReady) {
            return;
        }
        ensureClanColumn(connection, "clan_gold", "BIGINT NOT NULL DEFAULT 0");
        ensureClanColumn(connection, "clan_gem", "BIGINT NOT NULL DEFAULT 0");
        ensureClanColumn(connection, "treasury_version", "BIGINT NOT NULL DEFAULT 0");
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS clan_member_contribution ("
                    + "clan_id INT NOT NULL, player_id BIGINT NOT NULL, "
                    + "lifetime_score BIGINT NOT NULL DEFAULT 0, "
                    + "donated_gold BIGINT NOT NULL DEFAULT 0, "
                    + "donated_gem BIGINT NOT NULL DEFAULT 0, "
                    + "last_contribution_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP "
                    + "ON UPDATE CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY (clan_id, player_id), KEY idx_contribution_player (player_id)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS clan_ledger ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT, clan_id INT NOT NULL, actor_id BIGINT NOT NULL, "
                    + "actor_name VARCHAR(64) NOT NULL, action_type VARCHAR(32) NOT NULL, "
                    + "currency_type TINYINT NOT NULL, amount BIGINT NOT NULL, balance_after BIGINT NOT NULL, "
                    + "target_id BIGINT NULL, metadata_json TEXT NULL, request_id VARCHAR(80) NOT NULL, "
                    + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY (id), UNIQUE KEY uk_clan_ledger_request (clan_id, request_id), "
                    + "KEY idx_clan_ledger_page (clan_id, id), KEY idx_clan_ledger_actor (actor_id, created_at)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
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

    public void handleRequest(Player player, byte action, Message message) throws IOException {
        switch (action) {
            case REQUEST_VIEW:
                sendTreasuryView(player);
                break;
            case REQUEST_DEPOSIT_GOLD:
                deposit(player, CURRENCY_GOLD, message.reader().readLong(), message.reader().readUTF());
                break;
            case REQUEST_DEPOSIT_GEM:
                deposit(player, CURRENCY_GEM, message.reader().readLong(), message.reader().readUTF());
                break;
            case REQUEST_LEDGER_PAGE:
                sendLedgerPage(player, message.reader().readLong());
                break;
            default:
                break;
        }
    }

    public boolean isTreasuryAction(byte action) {
        return action >= REQUEST_VIEW && action <= REQUEST_LEDGER_PAGE;
    }

    private void sendTreasuryView(Player player) {
        if (!requireClan(player)) {
            return;
        }
        sendSnapshot(player);
    }

    private void deposit(Player player, byte currency, long amount, String requestId) {
        if (!requireClan(player)) {
            return;
        }
        if (amount <= 0) {
            Service.gI().sendThongBao(player, "Số lượng đóng góp phải lớn hơn 0.");
            return;
        }
        if (requestId == null || requestId.isBlank() || requestId.length() > REQUEST_ID_MAX_LENGTH) {
            Service.gI().sendThongBao(player, "Yêu cầu đóng góp không hợp lệ. Vui lòng thử lại.");
            return;
        }

        Clan clan = player.clan;
        DepositResult result;
        synchronized (player) {
            synchronized (clan) {
                if (player.clan != clan) {
                    Service.gI().sendThongBao(player, "Bạn không còn thuộc bang hội này.");
                    return;
                }
                result = persistDeposit(player, clan, currency, amount, requestId);
                if (result.success) {
                    player.inventory.gold = result.playerGold;
                    player.inventory.gem = result.playerGem;
                    clan.clanGold = result.clanGold;
                    clan.clanGem = result.clanGem;
                    clan.treasuryVersion = result.treasuryVersion;
                }
            }
        }

        if (!result.success) {
            Service.gI().sendThongBao(player, result.message);
            return;
        }
        Service.gI().sendMoney(player);
        Service.gI().sendThongBao(player, result.message);
        refreshOnlineClanSnapshots(clan, player);
        sendLedgerPage(player, 0L);
    }

    /** Refreshes the balance immediately for every online member viewing the same clan treasury. */
    private void refreshOnlineClanSnapshots(Clan clan, Player requester) {
        boolean requesterRefreshed = false;
        for (Player member : new ArrayList<>(clan.membersInGame)) {
            if (member != null && member.clan == clan) {
                sendSnapshot(member);
                requesterRefreshed |= member == requester;
            }
        }
        if (!requesterRefreshed && requester != null && requester.clan == clan) {
            sendSnapshot(requester);
        }
    }

    private DepositResult persistDeposit(Player player, Clan clan, byte currency, long amount, String requestId) {
        try (Connection connection = LocalManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                TreasuryRow treasury = lockTreasury(connection, clan.id);
                if (treasury == null) {
                    connection.rollback();
                    return DepositResult.failure("Bang hội không tồn tại.");
                }
                if (isRequestProcessed(connection, clan.id, requestId)) {
                    connection.rollback();
                    return DepositResult.failure("Yêu cầu này đã được xử lý trước đó.");
                }

                PlayerMoney playerMoney = lockPlayerMoney(connection, player, clan.id);
                if (playerMoney == null) {
                    connection.rollback();
                    return DepositResult.failure("Dữ liệu nhân vật hoặc bang hội không hợp lệ.");
                }
                if (currency == CURRENCY_GOLD && playerMoney.gold < amount) {
                    connection.rollback();
                    return DepositResult.failure("Bạn không đủ vàng để đóng góp.");
                }
                if (currency == CURRENCY_GEM && playerMoney.gem < amount) {
                    connection.rollback();
                    return DepositResult.failure("Bạn không đủ ngọc để đóng góp.");
                }

                long nextGold = currency == CURRENCY_GOLD ? checkedAdd(treasury.gold, amount) : treasury.gold;
                long nextGem = currency == CURRENCY_GEM ? checkedAdd(treasury.gem, amount) : treasury.gem;
                long nextVersion = checkedAdd(treasury.version, 1L);
                long scoreDelta = currency == CURRENCY_GOLD ? amount : checkedMultiply(amount, GEM_CONTRIBUTION_SCORE);
                ContributionRow contribution = lockContribution(connection, clan.id, player.id);
                long nextScore = checkedAdd(contribution.score, scoreDelta);
                long nextDonatedGold = checkedAdd(contribution.donatedGold,
                        currency == CURRENCY_GOLD ? amount : 0L);
                long nextDonatedGem = checkedAdd(contribution.donatedGem,
                        currency == CURRENCY_GEM ? amount : 0L);

                long remainingGold = currency == CURRENCY_GOLD ? playerMoney.gold - amount : playerMoney.gold;
                long remainingGem = currency == CURRENCY_GEM ? playerMoney.gem - amount : playerMoney.gem;
                updatePlayerMoney(connection, player.id, playerMoney.inventory, remainingGold, remainingGem);
                updateTreasury(connection, clan.id, nextGold, nextGem, nextVersion);
                saveContribution(connection, clan.id, player.id, contribution.exists,
                        nextScore, nextDonatedGold, nextDonatedGem);
                insertLedger(connection, clan.id, player, currency, amount,
                        currency == CURRENCY_GOLD ? nextGold : nextGem, requestId);
                connection.commit();
                return DepositResult.success(remainingGold, (int) remainingGem, nextGold, nextGem, nextVersion,
                        "Đã đóng góp " + amount + (currency == CURRENCY_GOLD ? " vàng" : " ngọc")
                        + " vào quỹ bang. Khoản đóng góp không thể rút lại.");
            } catch (Exception e) {
                connection.rollback();
                Logger.logException(ClanTreasuryService.class, e, "Lỗi transaction đóng góp kho bang");
                return DepositResult.failure("Không thể đóng góp lúc này. Tài sản của bạn không bị trừ.");
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception e) {
            Logger.logException(ClanTreasuryService.class, e, "Không mở được transaction kho bang");
            return DepositResult.failure("Không thể kết nối kho bang. Vui lòng thử lại.");
        }
    }

    private TreasuryRow lockTreasury(Connection connection, int clanId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT clan_gold, clan_gem, treasury_version FROM clan WHERE id=? FOR UPDATE")) {
            ps.setInt(1, clanId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new TreasuryRow(rs.getLong(1), rs.getLong(2), rs.getLong(3)) : null;
            }
        }
    }

    private boolean isRequestProcessed(Connection connection, int clanId, String requestId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM clan_ledger WHERE clan_id=? AND request_id=? LIMIT 1")) {
            ps.setInt(1, clanId);
            ps.setString(2, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private PlayerMoney lockPlayerMoney(Connection connection, Player player, int clanId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT clan_id, data_inventory FROM player WHERE id=? FOR UPDATE")) {
            ps.setLong(1, player.id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getInt("clan_id") != clanId) {
                    return null;
                }
                Object parsed = JSONValue.parse(rs.getString("data_inventory"));
                if (!(parsed instanceof JSONArray inventory) || inventory.size() < 2) {
                    return null;
                }
                long gold = parseCurrency(inventory.get(0));
                long gem = parseCurrency(inventory.get(1));
                if (gold < 0 || gem < 0 || gem > Integer.MAX_VALUE) {
                    return null;
                }
                return new PlayerMoney(inventory, gold, gem);
            }
        }
    }

    private ContributionRow lockContribution(Connection connection, int clanId, long playerId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT lifetime_score, donated_gold, donated_gem FROM clan_member_contribution "
                + "WHERE clan_id=? AND player_id=? FOR UPDATE")) {
            ps.setInt(1, clanId);
            ps.setLong(2, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return new ContributionRow(false, 0L, 0L, 0L);
                }
                return new ContributionRow(true, rs.getLong(1), rs.getLong(2), rs.getLong(3));
            }
        }
    }

    private void updatePlayerMoney(Connection connection, long playerId, JSONArray inventory,
            long gold, long gem) throws SQLException {
        inventory.set(0, gold);
        inventory.set(1, gem);
        try (PreparedStatement ps = connection.prepareStatement("UPDATE player SET data_inventory=? WHERE id=?")) {
            ps.setString(1, inventory.toJSONString());
            ps.setLong(2, playerId);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Không thể cập nhật tài sản nhân vật");
            }
        }
    }

    private void updateTreasury(Connection connection, int clanId, long gold, long gem, long version) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE clan SET clan_gold=?, clan_gem=?, treasury_version=? WHERE id=?")) {
            ps.setLong(1, gold);
            ps.setLong(2, gem);
            ps.setLong(3, version);
            ps.setInt(4, clanId);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Không thể cập nhật quỹ bang");
            }
        }
    }

    private void saveContribution(Connection connection, int clanId, long playerId, boolean exists,
            long score, long donatedGold, long donatedGem) throws SQLException {
        String sql = exists
                ? "UPDATE clan_member_contribution SET lifetime_score=?, donated_gold=?, donated_gem=? "
                + "WHERE clan_id=? AND player_id=?"
                : "INSERT INTO clan_member_contribution (lifetime_score, donated_gold, donated_gem, clan_id, player_id) "
                + "VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, score);
            ps.setLong(2, donatedGold);
            ps.setLong(3, donatedGem);
            ps.setInt(4, clanId);
            ps.setLong(5, playerId);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Không thể lưu điểm cống hiến");
            }
        }
    }

    private void insertLedger(Connection connection, int clanId, Player player, byte currency,
            long amount, long balanceAfter, String requestId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO clan_ledger "
                + "(clan_id, actor_id, actor_name, action_type, currency_type, amount, balance_after, "
                + "target_id, metadata_json, request_id) VALUES (?,?,?,?,?,?,?,?,?,?)")) {
            ps.setInt(1, clanId);
            ps.setLong(2, player.id);
            ps.setString(3, player.name == null ? "" : player.name);
            ps.setString(4, "DEPOSIT");
            ps.setByte(5, currency);
            ps.setLong(6, amount);
            ps.setLong(7, balanceAfter);
            ps.setNull(8, java.sql.Types.BIGINT);
            ps.setString(9, "{}");
            ps.setString(10, requestId);
            ps.executeUpdate();
        }
    }

    public void sendSnapshot(Player player) {
        if (!requireClan(player)) {
            return;
        }
        Clan clan = player.clan;
        long contribution = getContributionForPlayer(clan.id, player.id);
        try {
            Message message = new Message(127);
            message.writer().writeByte(RESPONSE_SNAPSHOT);
            message.writer().writeInt(clan.id);
            message.writer().writeLong((long) clan.capsuleClan);
            message.writer().writeLong(clan.clanGold);
            message.writer().writeLong(clan.clanGem);
            message.writer().writeLong(contribution);
            message.writer().writeLong(clan.treasuryVersion);
            player.sendMessage(message);
            message.cleanup();
        } catch (Exception e) {
            Logger.logException(ClanTreasuryService.class, e, "Không gửi được snapshot kho bang");
        }
    }

    public void sendLedgerPage(Player player, long beforeId) {
        if (!requireClan(player)) {
            return;
        }
        List<LedgerEntry> entries = new ArrayList<>();
        try (Connection connection = LocalManager.getConnection();
                PreparedStatement ps = connection.prepareStatement(beforeId > 0
                        ? "SELECT id, actor_id, actor_name, action_type, currency_type, amount, balance_after, "
                        + "UNIX_TIMESTAMP(created_at) AS created_at FROM clan_ledger WHERE clan_id=? AND id<? "
                        + "ORDER BY id DESC LIMIT ?"
                        : "SELECT id, actor_id, actor_name, action_type, currency_type, amount, balance_after, "
                        + "UNIX_TIMESTAMP(created_at) AS created_at FROM clan_ledger WHERE clan_id=? "
                        + "ORDER BY id DESC LIMIT ?")) {
            ps.setInt(1, player.clan.id);
            int index = 2;
            if (beforeId > 0) {
                ps.setLong(index++, beforeId);
            }
            ps.setInt(index, LEDGER_PAGE_SIZE + 1);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(new LedgerEntry(rs.getLong("id"), rs.getLong("actor_id"), rs.getString("actor_name"),
                            rs.getString("action_type"), rs.getByte("currency_type"), rs.getLong("amount"),
                            rs.getLong("balance_after"), rs.getLong("created_at")));
                }
            }
        } catch (Exception e) {
            Logger.logException(ClanTreasuryService.class, e, "Không tải lịch sử kho bang");
            Service.gI().sendThongBao(player, "Không tải được lịch sử kho bang.");
            return;
        }
        boolean hasMore = entries.size() > LEDGER_PAGE_SIZE;
        if (hasMore) {
            entries.remove(entries.size() - 1);
        }
        long nextCursor = hasMore && !entries.isEmpty() ? entries.get(entries.size() - 1).id : 0L;
        try {
            Message message = new Message(127);
            message.writer().writeByte(RESPONSE_LEDGER_PAGE);
            message.writer().writeInt(player.clan.id);
            message.writer().writeLong(beforeId);
            message.writer().writeByte(hasMore ? 1 : 0);
            message.writer().writeLong(nextCursor);
            message.writer().writeByte(entries.size());
            for (LedgerEntry entry : entries) {
                message.writer().writeLong(entry.id);
                message.writer().writeLong(entry.actorId);
                message.writer().writeUTF(entry.actorName == null ? "" : entry.actorName);
                message.writer().writeUTF(entry.actionType);
                message.writer().writeByte(entry.currencyType);
                message.writer().writeLong(entry.amount);
                message.writer().writeLong(entry.balanceAfter);
                message.writer().writeLong(entry.createdAtSeconds);
            }
            player.sendMessage(message);
            message.cleanup();
        } catch (Exception e) {
            Logger.logException(ClanTreasuryService.class, e, "Không gửi được lịch sử kho bang");
        }
    }

    public List<LedgerEntry> getRecentLedgerEntries(int clanId, int limit) {
        int safeLimit = Math.max(1, Math.min(50, limit));
        List<LedgerEntry> entries = new ArrayList<>();
        try (Connection connection = LocalManager.getConnection();
                PreparedStatement ps = connection.prepareStatement(
                        "SELECT id, actor_id, actor_name, action_type, currency_type, amount, balance_after, "
                        + "UNIX_TIMESTAMP(created_at) AS created_at FROM clan_ledger WHERE clan_id=? "
                        + "ORDER BY id DESC LIMIT ?")) {
            ps.setInt(1, clanId);
            ps.setInt(2, safeLimit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(new LedgerEntry(rs.getLong("id"), rs.getLong("actor_id"),
                            rs.getString("actor_name"), rs.getString("action_type"),
                            rs.getByte("currency_type"), rs.getLong("amount"),
                            rs.getLong("balance_after"), rs.getLong("created_at")));
                }
            }
        } catch (Exception e) {
            Logger.logException(ClanTreasuryService.class, e, "Không tải được lịch sử gần nhất của kho bang");
        }
        return entries;
    }

    public long getContributionForPlayer(int clanId, long playerId) {
        try (Connection connection = LocalManager.getConnection();
                PreparedStatement ps = connection.prepareStatement(
                        "SELECT lifetime_score FROM clan_member_contribution WHERE clan_id=? AND player_id=?")) {
            ps.setInt(1, clanId);
            ps.setLong(2, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (Exception e) {
            Logger.logException(ClanTreasuryService.class, e, "Không tải được cống hiến thành viên");
            return 0L;
        }
    }

    /** Cống hiến từ hoạt động bang, tách biệt hoàn toàn với khoản đóng góp quỹ. */
    public void addActivityContribution(int clanId, long playerId, long amount) {
        if (clanId < 0 || playerId <= 0 || amount <= 0) {
            return;
        }
        try (Connection connection = LocalManager.getConnection()) {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO clan_member_contribution (clan_id,player_id,lifetime_score,donated_gold,donated_gem) "
                    + "VALUES (?,?,?,0,0) ON DUPLICATE KEY UPDATE lifetime_score=lifetime_score+VALUES(lifetime_score)")) {
                ps.setInt(1, clanId);
                ps.setLong(2, playerId);
                ps.setLong(3, amount);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            Logger.logException(ClanTreasuryService.class, e, "Không cộng được cống hiến hoạt động bang");
        }
    }

    private boolean requireClan(Player player) {
        if (player != null && player.clan != null) {
            return true;
        }
        if (player != null) {
            Service.gI().sendThongBao(player, "Bạn cần có bang hội để dùng Kho bang.");
        }
        return false;
    }

    private static long parseCurrency(Object value) throws SQLException {
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            throw new SQLException("Dữ liệu tiền tệ nhân vật không hợp lệ", e);
        }
    }

    private static long checkedAdd(long left, long right) throws SQLException {
        if (right > 0 && left > Long.MAX_VALUE - right) {
            throw new SQLException("Số dư kho bang vượt giới hạn");
        }
        return left + right;
    }

    private static long checkedMultiply(long left, long right) throws SQLException {
        if (left > 0 && right > Long.MAX_VALUE / left) {
            throw new SQLException("Điểm cống hiến vượt giới hạn");
        }
        return left * right;
    }

    private record TreasuryRow(long gold, long gem, long version) {
    }

    private record PlayerMoney(JSONArray inventory, long gold, long gem) {
    }

    private record ContributionRow(boolean exists, long score, long donatedGold, long donatedGem) {
    }

    public static record LedgerEntry(long id, long actorId, String actorName, String actionType,
            byte currencyType, long amount, long balanceAfter, long createdAtSeconds) {
    }

    private static final class DepositResult {

        private final boolean success;
        private final long playerGold;
        private final int playerGem;
        private final long clanGold;
        private final long clanGem;
        private final long treasuryVersion;
        private final String message;

        private DepositResult(boolean success, long playerGold, int playerGem, long clanGold,
                long clanGem, long treasuryVersion, String message) {
            this.success = success;
            this.playerGold = playerGold;
            this.playerGem = playerGem;
            this.clanGold = clanGold;
            this.clanGem = clanGem;
            this.treasuryVersion = treasuryVersion;
            this.message = message;
        }

        private static DepositResult success(long playerGold, int playerGem, long clanGold,
                long clanGem, long treasuryVersion, String message) {
            return new DepositResult(true, playerGold, playerGem, clanGold, clanGem, treasuryVersion, message);
        }

        private static DepositResult failure(String message) {
            return new DepositResult(false, 0L, 0, 0L, 0L, 0L, message);
        }
    }
}
