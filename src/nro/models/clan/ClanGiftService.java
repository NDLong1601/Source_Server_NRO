package nro.models.clan;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.io.FileInputStream;
import nro.models.data.LocalManager;
import nro.models.item.Item;
import nro.models.network.Message;
import nro.models.player.Player;
import nro.models.player.PlayerConfig;
import nro.models.services.InventoryService;
import nro.models.services.Service;
import nro.models.utils.Logger;
import nro.models.utils.Util;
import org.json.simple.JSONArray;

/** Server-authoritative, idempotent gifts between members of one clan. */
public final class ClanGiftService {

    public static final byte REQUEST_SEND_GIFT = 120;
    private static final ClanGiftService INSTANCE = new ClanGiftService();
    private static final int REQUEST_ID_MAX = 80;
    private volatile boolean enabled = true;
    private volatile boolean schemaReady;
    private volatile int sentPerDay;
    private volatile int receivedPerDay;
    private volatile int minJoinHours;
    private volatile long minContribution = 0L;
    private volatile int boundGemChancePercent = 15;
    private volatile long goldBase = 100_000L;

    private ClanGiftService() { loadConfig(); }
    public static ClanGiftService gI() { return INSTANCE; }

    public synchronized void loadConfig() {
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream("data/clan_gift.properties")) { properties.load(input); }
        catch (Exception ignored) { }
        enabled = Boolean.parseBoolean(properties.getProperty("enabled", "true"));
        sentPerDay = nonNegative(properties, "sent_per_day", 0);
        receivedPerDay = nonNegative(properties, "received_per_day", 0);
        minJoinHours = nonNegative(properties, "minimum_join_hours", 0);
        minContribution = nonNegativeLong(properties, "minimum_contribution", 0L);
        boundGemChancePercent = Math.max(0, Math.min(100, positive(properties, "bound_gem_chance_percent", 15)));
        goldBase = positiveLong(properties, "gold_base", 100_000L);
    }

    public synchronized void ensureSchema(Connection connection) throws SQLException {
        if (schemaReady) return;
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS clan_gift_daily ("
                    + "clan_id INT NOT NULL, player_id BIGINT NOT NULL, day_key INT NOT NULL, sent_count INT NOT NULL DEFAULT 0, received_count INT NOT NULL DEFAULT 0, "
                    + "PRIMARY KEY (clan_id,player_id,day_key)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS clan_gift_pair ("
                    + "clan_id INT NOT NULL, sender_id BIGINT NOT NULL, receiver_id BIGINT NOT NULL, day_key INT NOT NULL, "
                    + "PRIMARY KEY (clan_id,sender_id,receiver_id,day_key)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS clan_pending_reward ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT, clan_id INT NOT NULL, player_id BIGINT NOT NULL, source_type VARCHAR(32) NOT NULL, "
                    + "gold_amount BIGINT NOT NULL DEFAULT 0, ruby_amount INT NOT NULL DEFAULT 0, request_id VARCHAR(80) NOT NULL, status TINYINT NOT NULL DEFAULT 0, "
                    + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, claimed_at TIMESTAMP NULL DEFAULT NULL, "
                    + "PRIMARY KEY (id), UNIQUE KEY uk_clan_pending_request (request_id), KEY idx_clan_pending_player (player_id,status,id)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS clan_gift_request ("
                    + "request_id VARCHAR(80) NOT NULL, clan_id INT NOT NULL, sender_id BIGINT NOT NULL, receiver_id BIGINT NOT NULL, "
                    + "gold_amount BIGINT NOT NULL DEFAULT 0, ruby_amount INT NOT NULL DEFAULT 0, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY (request_id), KEY idx_clan_gift_sender_day (clan_id,sender_id,created_at)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
        }
        schemaReady = true;
    }

    public boolean isGiftAction(byte action) { return action == REQUEST_SEND_GIFT; }

    public void handleRequest(Player player, byte action, Message message) throws IOException {
        if (action == REQUEST_SEND_GIFT) sendGift(player, message.reader().readLong(), message.reader().readUTF());
    }

    private void sendGift(Player sender, long receiverId, String requestId) {
        if (!enabled) { notify(sender, "Tặng quà bang đang tạm khóa."); return; }
        if (sender == null || sender.clan == null) { notify(sender, "Bạn cần có bang hội để tặng quà."); return; }
        if (receiverId <= 0 || receiverId == sender.id || !validRequestId(requestId)) {
            notify(sender, "Yêu cầu tặng quà không hợp lệ."); return;
        }
        Clan clan = sender.clan;
        ClanMember receiver = clan.getClanMember((int) receiverId);
        if (receiver == null || sender.clanMember == null) { notify(sender, "Người nhận không còn cùng bang hội."); return; }
        if (minJoinHours > 0 && (!oldEnough(sender.clanMember) || !oldEnough(receiver))) { notify(sender, "Cả hai thành viên cần vào bang ít nhất " + minJoinHours + " giờ."); return; }
        if (sameAccount(sender.id, receiverId)) { notify(sender, "Không thể tặng quà cho nhân vật cùng tài khoản."); return; }
        long contribution = ClanTreasuryService.gI().getContributionForPlayer(clan.id, sender.id);
        if (contribution < minContribution) { notify(sender, "Bạn cần thêm " + (minContribution - contribution) + " điểm cống hiến để tặng quà."); return; }
        Item ticket = InventoryService.gI().findItemBag(sender, ClanShopService.CLAN_GIFT_TICKET_ITEM_ID);
        if (ticket == null || ticket.quantity < 1) {
            notify(sender, "Bạn cần có Phiếu quà bang trong hành trang để tặng quà."); return;
        }

        GiftResult result;
        synchronized (sender) {
            synchronized (clan) {
                if (sender.clan != clan || clan.getClanMember((int) receiverId) == null) {
                    notify(sender, "Người nhận không còn cùng bang hội."); return;
                }
                result = persistGift(clan, sender, receiverId, requestId);
                if (result.success) {
                    InventoryService.gI().subQuantityItemsBag(sender, ticket, 1);
                    InventoryService.gI().sendItemBags(sender);
                }
            }
        }
        if (!result.success) { notify(sender, result.message); return; }
        notify(sender, "Đã tặng quà bang cho " + receiver.name + ": " + result.rewardText());
        Player online = clan.getPlayerOnline((int) receiverId);
        if (online != null && !online.isOffline) deliverPending(online);
    }

    private GiftResult persistGift(Clan clan, Player sender, long receiverId, String requestId) {
        int today = dayKey();
        long gold = 0L;
        int ruby = 0;
        if (Util.isTrue(boundGemChancePercent, 100)) ruby = Util.nextInt(3, 5);
        else gold = safeGoldReward(clan.level, clan.getClanMember((int) receiverId).powerPoint);
        try (Connection connection = LocalManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ensureSchema(connection);
                if (requestProcessed(connection, requestId)) {
                    connection.rollback(); return GiftResult.fail("Yêu cầu tặng quà này đã được xử lý.");
                }
                DailyRow sent = lockDaily(connection, clan.id, sender.id, today);
                DailyRow received = lockDaily(connection, clan.id, receiverId, today);
                if (sentPerDay > 0 && sent.sent >= sentPerDay) { connection.rollback(); return GiftResult.fail("Bạn đã dùng hết lượt tặng quà hôm nay."); }
                if (receivedPerDay > 0 && received.received >= receivedPerDay) { connection.rollback(); return GiftResult.fail("Người nhận đã đủ quà hôm nay."); }
                if (pairExists(connection, clan.id, sender.id, receiverId, today)) { connection.rollback(); return GiftResult.fail("Bạn đã tặng quà cho thành viên này hôm nay."); }
                saveDaily(connection, clan.id, sender.id, today, sent.sent + 1, sent.received);
                saveDaily(connection, clan.id, receiverId, today, received.sent, received.received + 1);
                savePair(connection, clan.id, sender.id, receiverId, today);
                saveRequest(connection, clan.id, sender.id, receiverId, gold, ruby, requestId);
                savePending(connection, clan.id, receiverId, gold, ruby, requestId);
                connection.commit();
                return GiftResult.ok(gold, ruby);
            } catch (Exception e) {
                connection.rollback(); Logger.logException(ClanGiftService.class, e, "Transaction tặng quà bang");
                return GiftResult.fail("Tặng quà thất bại; Phiếu quà chưa bị trừ.");
            } finally { connection.setAutoCommit(true); }
        } catch (Exception e) {
            Logger.logException(ClanGiftService.class, e, "Mở transaction tặng quà bang");
            return GiftResult.fail("Không thể kết nối hệ thống quà bang.");
        }
    }

    /** Called after the login state has been loaded, and immediately for an online recipient. */
    public void deliverPending(Player player) {
        if (player == null) return;
        List<PendingReward> rewards = new ArrayList<>();
        try (Connection connection = LocalManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ensureSchema(connection);
                try (PreparedStatement ps = connection.prepareStatement("SELECT id,gold_amount,ruby_amount FROM clan_pending_reward WHERE player_id=? AND status=0 ORDER BY id FOR UPDATE")) {
                    ps.setLong(1, player.id);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) rewards.add(new PendingReward(rs.getLong(1), rs.getLong(2), rs.getInt(3)));
                    }
                }
                long gold = 0L; long ruby = 0L;
                for (PendingReward reward : rewards) { gold = Math.addExact(gold, reward.gold); ruby = Math.addExact(ruby, reward.ruby); }
                if (gold > PlayerConfig.getMaxGold() - player.inventory.gold || ruby > Integer.MAX_VALUE - (long) player.inventory.ruby) {
                    connection.rollback();
                    notify(player, "Có quà bang chờ nhận nhưng tài sản của bạn đã đạt giới hạn.");
                    return;
                }
                if (!rewards.isEmpty()) {
                    player.inventory.gold += gold;
                    player.inventory.ruby += (int) ruby;
                    JSONArray inventory = new JSONArray();
                    inventory.add(player.inventory.gold);
                    inventory.add(player.inventory.gem);
                    inventory.add(player.inventory.ruby);
                    inventory.add(player.inventory.coupon);
                    inventory.add(player.inventory.event);
                    try (PreparedStatement ps = connection.prepareStatement("UPDATE player SET data_inventory=? WHERE id=?")) {
                        ps.setString(1, inventory.toJSONString());
                        ps.setLong(2, player.id);
                        if (ps.executeUpdate() != 1) throw new SQLException("Không tìm thấy người nhận quà bang.");
                    }
                    try (PreparedStatement ps = connection.prepareStatement("UPDATE clan_pending_reward SET status=1,claimed_at=NOW() WHERE player_id=? AND status=0")) {
                        ps.setLong(1, player.id); ps.executeUpdate();
                    }
                }
                connection.commit();
                if (!rewards.isEmpty()) {
                    Service.gI().sendMoney(player);
                    notify(player, "Bạn đã nhận quà bang: " + rewardText(gold, (int) ruby) + ".");
                }
            } catch (Exception e) {
                connection.rollback(); Logger.logException(ClanGiftService.class, e, "Nhận quà bang chờ");
            } finally { connection.setAutoCommit(true); }
        } catch (Exception e) { Logger.logException(ClanGiftService.class, e, "Mở transaction nhận quà bang chờ"); }
    }

    private boolean oldEnough(ClanMember member) { return member != null && System.currentTimeMillis() / 1000L - member.joinTime >= minJoinHours * 3600L; }
    /** ClanMember has no RPG-level field, so power brackets provide the stable recipient-level component for offline gifts. */
    private long safeGoldReward(int clanLevel, long receiverPower) {
        long powerTier = Math.max(1L, Math.min(10L, 1L + Math.max(0L, receiverPower) / 100_000_000L));
        long combinedTier = Math.max(1L, clanLevel) + powerTier - 1L;
        try { return Math.multiplyExact(goldBase, combinedTier); }
        catch (ArithmeticException e) { return Long.MAX_VALUE / 4; }
    }
    private static int dayKey() { return (int) LocalDate.now(nro.models.utils.TimeUtil.VIETNAM_ZONE).toEpochDay(); }
    private static boolean validRequestId(String value) { return value != null && !value.isBlank() && value.length() <= REQUEST_ID_MAX; }
    private static void notify(Player player, String text) { if (player != null) Service.gI().sendThongBao(player, text); }

    private boolean sameAccount(long senderId, long receiverId) {
        try (Connection connection = LocalManager.getConnection(); PreparedStatement ps = connection.prepareStatement("SELECT account_id FROM player WHERE id IN (?,?)")) {
            ps.setLong(1, senderId); ps.setLong(2, receiverId);
            try (ResultSet rs = ps.executeQuery()) {
                Integer first = null;
                while (rs.next()) { int account = rs.getInt(1); if (first == null) first = account; else return first == account; }
            }
        } catch (Exception e) { Logger.logException(ClanGiftService.class, e, "Kiểm tra cùng tài khoản tặng quà"); return true; }
        return true;
    }

    private DailyRow lockDaily(Connection c, int clanId, long playerId, int day) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT sent_count,received_count FROM clan_gift_daily WHERE clan_id=? AND player_id=? AND day_key=? FOR UPDATE")) {
            ps.setInt(1, clanId); ps.setLong(2, playerId); ps.setInt(3, day);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? new DailyRow(rs.getInt(1), rs.getInt(2)) : new DailyRow(0, 0); }
        }
    }
    private boolean requestProcessed(Connection c, String requestId) throws SQLException { try (PreparedStatement ps = c.prepareStatement("SELECT request_id FROM clan_gift_request WHERE request_id=?")) { ps.setString(1, requestId); try (ResultSet rs = ps.executeQuery()) { return rs.next(); } } }
    private boolean pairExists(Connection c, int clanId, long sender, long receiver, int day) throws SQLException { try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM clan_gift_pair WHERE clan_id=? AND sender_id=? AND receiver_id=? AND day_key=?")) { ps.setInt(1, clanId); ps.setLong(2, sender); ps.setLong(3, receiver); ps.setInt(4, day); try (ResultSet rs = ps.executeQuery()) { return rs.next(); } } }
    private void saveDaily(Connection c, int clanId, long playerId, int day, int sent, int received) throws SQLException { try (PreparedStatement ps = c.prepareStatement("INSERT INTO clan_gift_daily (clan_id,player_id,day_key,sent_count,received_count) VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE sent_count=VALUES(sent_count),received_count=VALUES(received_count)")) { ps.setInt(1,clanId); ps.setLong(2,playerId); ps.setInt(3,day); ps.setInt(4,sent); ps.setInt(5,received); ps.executeUpdate(); } }
    private void savePair(Connection c, int clanId, long sender, long receiver, int day) throws SQLException { try (PreparedStatement ps = c.prepareStatement("INSERT INTO clan_gift_pair (clan_id,sender_id,receiver_id,day_key) VALUES (?,?,?,?)")) { ps.setInt(1,clanId); ps.setLong(2,sender); ps.setLong(3,receiver); ps.setInt(4,day); ps.executeUpdate(); } }
    private void saveRequest(Connection c, int clanId, long sender, long receiver, long gold, int ruby, String requestId) throws SQLException { try (PreparedStatement ps = c.prepareStatement("INSERT INTO clan_gift_request (request_id,clan_id,sender_id,receiver_id,gold_amount,ruby_amount) VALUES (?,?,?,?,?,?)")) { ps.setString(1,requestId); ps.setInt(2,clanId); ps.setLong(3,sender); ps.setLong(4,receiver); ps.setLong(5,gold); ps.setInt(6,ruby); ps.executeUpdate(); } }
    private void savePending(Connection c, int clanId, long playerId, long gold, int ruby, String requestId) throws SQLException { try (PreparedStatement ps = c.prepareStatement("INSERT INTO clan_pending_reward (clan_id,player_id,source_type,gold_amount,ruby_amount,request_id) VALUES (?,?,'CLAN_GIFT',?,?,?)")) { ps.setInt(1,clanId); ps.setLong(2,playerId); ps.setLong(3,gold); ps.setInt(4,ruby); ps.setString(5,requestId); ps.executeUpdate(); } }

    private static int positive(Properties p, String key, int fallback) { try { return Math.max(1, Integer.parseInt(p.getProperty(key, String.valueOf(fallback)).trim())); } catch (Exception e) { return fallback; } }
    private static int nonNegative(Properties p, String key, int fallback) { try { return Math.max(0, Integer.parseInt(p.getProperty(key, String.valueOf(fallback)).trim())); } catch (Exception e) { return fallback; } }
    private static long positiveLong(Properties p, String key, long fallback) { try { return Math.max(1L, Long.parseLong(p.getProperty(key, String.valueOf(fallback)).trim())); } catch (Exception e) { return fallback; } }
    private static long nonNegativeLong(Properties p, String key, long fallback) { try { return Math.max(0L, Long.parseLong(p.getProperty(key, String.valueOf(fallback)).trim())); } catch (Exception e) { return fallback; } }
    private static String rewardText(long gold, int ruby) { return ruby > 0 ? ruby + " ngọc khóa" : gold + " vàng"; }
    private record DailyRow(int sent, int received) { }
    private record PendingReward(long id, long gold, int ruby) { }
    private record GiftResult(boolean success, String message, long gold, int ruby) {
        static GiftResult ok(long gold, int ruby) { return new GiftResult(true, "", gold, ruby); }
        static GiftResult fail(String message) { return new GiftResult(false, message, 0L, 0); }
        String rewardText() { return ClanGiftService.rewardText(gold, ruby); }
    }
}
