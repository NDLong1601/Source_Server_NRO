package nro.models.clan;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import nro.models.data.LocalManager;
import nro.models.item.Item;
import nro.models.network.Message;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.Service;
import nro.models.services_func.Input;
import nro.models.utils.Logger;
import nro.models.utils.Util;

/**
 * Giai đoạn 4: catalogue, stock and purchases for the clan shop.  The client
 * receives a read-only snapshot; price, unlock and stock decisions stay here.
 */
public final class ClanShopService {

    public static final byte REQUEST_VIEW = 116;
    public static final byte REQUEST_RESTOCK = 117;
    public static final byte REQUEST_BUY = 118;
    public static final byte RESPONSE_SNAPSHOT = 119;

    public static final int RENAME_TICKET_ITEM_ID = 2272;
    public static final int CLAN_GIFT_BOX_ITEM_ID = 2273;
    public static final int CLAN_GIFT_TICKET_ITEM_ID = 2274;

    private static final int REQUEST_ID_MAX = 80;
    private static final ClanShopService INSTANCE = new ClanShopService();
    private final Map<Integer, CatalogItem> catalog = new LinkedHashMap<>();
    private volatile boolean enabled = true;
    private volatile boolean schemaReady;
    private volatile int tierOneLevel = 3;
    private volatile int tierTwoLevel = 10;
    private volatile int tierThreeLevel = 15;

    private ClanShopService() {
        loadConfig();
    }

    public static ClanShopService gI() {
        return INSTANCE;
    }

    public synchronized void loadConfig() {
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream("data/clan_shop.properties")) {
            properties.load(input);
        } catch (Exception ignored) {
            // The defaults below intentionally keep a safe, small catalogue.
        }
        enabled = Boolean.parseBoolean(properties.getProperty("enabled", "true"));
        tierOneLevel = positive(properties, "tier.1.clanLevel", 3);
        tierTwoLevel = positive(properties, "tier.2.clanLevel", 10);
        tierThreeLevel = positive(properties, "tier.3.clanLevel", 15);
        catalog.clear();
        for (int id = 2252; id <= RENAME_TICKET_ITEM_ID; id++) {
            String fallback = defaultRow(id);
            CatalogItem item = CatalogItem.parse(id, properties.getProperty("item." + id, fallback));
            if (item != null) {
                catalog.put(id, item);
            }
        }
    }

    private static int positive(Properties properties, String key, int fallback) {
        try {
            return Math.max(1, Integer.parseInt(properties.getProperty(key, String.valueOf(fallback)).trim()));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    /* tier, stock-cap, restock Capsule-bang, restock gold-bang, price Capsule-ca-nhan,
       minimum contribution, personal daily limit */
    private static String defaultRow(int id) {
        if (id >= 2272) return "3,3,30,1500000,20,300,1";
        if (id >= 2269) return "3,5,20,1000000,15,300,1";
        if (id >= 2261) return "2,8,12,500000,8,100,2";
        return "1,12,6,250000,4,0,3";
    }

    public synchronized void ensureSchema(Connection connection) throws SQLException {
        if (schemaReady) return;
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS clan_shop_stock ("
                    + "clan_id INT NOT NULL, item_template_id INT NOT NULL, stock INT NOT NULL DEFAULT 0, "
                    + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY (clan_id,item_template_id), KEY idx_clan_shop_stock_clan (clan_id)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS clan_shop_purchase ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT, clan_id INT NOT NULL, player_id BIGINT NOT NULL, "
                    + "item_template_id INT NOT NULL, day_key INT NOT NULL, quantity INT NOT NULL DEFAULT 0, "
                    + "request_id VARCHAR(80) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY (id), UNIQUE KEY uk_clan_shop_request (clan_id,request_id), "
                    + "UNIQUE KEY uk_clan_shop_daily (clan_id,player_id,item_template_id,day_key), "
                    + "KEY idx_clan_shop_player_day (clan_id,player_id,day_key)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS clan_shop_restock ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT, clan_id INT NOT NULL, item_template_id INT NOT NULL, "
                    + "request_id VARCHAR(80) NOT NULL, stock_before INT NOT NULL, stock_after INT NOT NULL, "
                    + "actor_id BIGINT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY (id), UNIQUE KEY uk_clan_shop_restock_request (clan_id,request_id), "
                    + "KEY idx_clan_shop_restock_clan (clan_id,id)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
        }
        schemaReady = true;
    }

    public boolean isShopAction(byte action) {
        return action >= REQUEST_VIEW && action <= REQUEST_BUY;
    }

    public boolean isClanSupportItem(int itemId) {
        return itemId >= 2252 && itemId <= RENAME_TICKET_ITEM_ID && catalog.containsKey(itemId);
    }

    public void handleRequest(Player player, byte action, Message message) throws IOException {
        if (action == REQUEST_VIEW) {
            sendSnapshot(player);
        } else if (action == REQUEST_RESTOCK) {
            restock(player, message.reader().readInt(), message.reader().readUTF());
        } else if (action == REQUEST_BUY) {
            buy(player, message.reader().readInt(), message.reader().readUTF());
        }
    }

    public void sendSnapshot(Player player) {
        if (!requireClan(player)) return;
        Clan clan = player.clan;
        Map<Integer, Integer> stock = loadStock(clan.id);
        long contribution = ClanTreasuryService.gI().getContributionForPlayer(clan.id, player.id);
        try {
            Message message = new Message(127);
            message.writer().writeByte(RESPONSE_SNAPSHOT);
            message.writer().writeInt(clan.id);
            message.writer().writeByte(enabled ? 1 : 0);
            message.writer().writeInt(clan.level);
            message.writer().writeInt(player.clanMember == null ? 0 : player.clanMember.clanPoint);
            message.writer().writeLong(contribution);
            message.writer().writeByte(catalog.size());
            for (CatalogItem item : catalog.values()) {
                message.writer().writeInt(item.itemId);
                message.writer().writeUTF(itemName(item.itemId));
                message.writer().writeByte(item.tier);
                message.writer().writeInt(stock.getOrDefault(item.itemId, 0));
                message.writer().writeInt(item.stockCap);
                message.writer().writeInt(item.personalCapsuleCost);
                message.writer().writeLong(item.minContribution);
                message.writer().writeByte(item.dailyLimit);
                message.writer().writeByte(isTierUnlocked(clan, item.tier) ? 1 : 0);
            }
            player.sendMessage(message);
            message.cleanup();
        } catch (Exception e) {
            Logger.logException(ClanShopService.class, e, "Không gửi được Cửa hàng bang");
        }
    }

    private void restock(Player player, int itemId, String requestId) {
        if (!requireClan(player) || !enabled) return;
        CatalogItem item = catalog.get(itemId);
        if (item == null || !validRequestId(requestId)) {
            notify(player, "Yêu cầu nhập hàng không hợp lệ.");
            return;
        }
        Clan clan = player.clan;
        if (!clan.isLeader(player) && !clan.isDeputy(player)) {
            notify(player, "Chỉ bang chủ hoặc phó bang được nhập hàng.");
            return;
        }
        if (!isTierUnlocked(clan, item.tier)) {
            notify(player, "Cửa hàng chưa mở tầng hàng này.");
            return;
        }
        synchronized (clan) {
            try (Connection connection = LocalManager.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    if (restockRequestExists(connection, clan.id, requestId)) {
                        connection.rollback();
                        notify(player, "Yêu cầu nhập hàng này đã được xử lý.");
                        sendSnapshot(player);
                        return;
                    }
                    ClanRow row = lockClan(connection, clan.id);
                    if (row == null || row.capsule < item.restockCapsule || row.gold < item.restockGold) {
                        connection.rollback();
                        notify(player, "Quỹ bang không đủ Capsule hoặc vàng để nhập hàng.");
                        return;
                    }
                    int currentStock = lockStock(connection, clan.id, itemId);
                    if (currentStock >= item.stockCap) {
                        connection.rollback();
                        notify(player, "Mặt hàng này đã đủ tồn kho.");
                        return;
                    }
                    int added = item.stockCap - currentStock;
                    updateClanMoney(connection, clan.id, row.capsule - item.restockCapsule, row.gold - item.restockGold);
                    saveStock(connection, clan.id, itemId, item.stockCap);
                    saveRestock(connection, clan.id, player.id, itemId, requestId, currentStock, item.stockCap);
                    connection.commit();
                    clan.capsuleClan = row.capsule - item.restockCapsule;
                    clan.clanGold = row.gold - item.restockGold;
                    notify(player, "Đã nhập " + added + " " + itemName(itemId) + " vào cửa hàng bang.");
                    sendSnapshot(player);
                } catch (Exception e) {
                    connection.rollback();
                    Logger.logException(ClanShopService.class, e, "Transaction nhập hàng bang");
                    notify(player, "Không thể nhập hàng lúc này.");
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (Exception e) {
                Logger.logException(ClanShopService.class, e, "Mở transaction nhập hàng bang");
                notify(player, "Không thể kết nối cửa hàng bang.");
            }
        }
    }

    private void buy(Player player, int itemId, String requestId) {
        if (!requireClan(player) || !enabled) return;
        CatalogItem item = catalog.get(itemId);
        if (item == null || !validRequestId(requestId)) {
            notify(player, "Yêu cầu mua hàng không hợp lệ.");
            return;
        }
        Clan clan = player.clan;
        if (!isTierUnlocked(clan, item.tier)) {
            notify(player, "Cửa hàng chưa mở tầng hàng này.");
            return;
        }
        if (InventoryService.gI().getCountEmptyBag(player) < 1) {
            notify(player, "Hành trang cần ít nhất một chỗ trống.");
            return;
        }
        synchronized (player) {
            synchronized (clan) {
                if (player.clan != clan || player.clanMember == null) {
                    notify(player, "Bạn không còn thuộc bang hội này.");
                    return;
                }
                if (player.clanMember.clanPoint < item.personalCapsuleCost) {
                    notify(player, "Bạn không đủ Capsule cá nhân để mua.");
                    return;
                }
                long contribution = ClanTreasuryService.gI().getContributionForPlayer(clan.id, player.id);
                if (contribution < item.minContribution) {
                    notify(player, "Bạn cần thêm " + (item.minContribution - contribution) + " điểm cống hiến để mua.");
                    return;
                }
                PurchaseResult result = persistPurchase(clan, player, item, requestId);
                if (!result.success) {
                    notify(player, result.message);
                    return;
                }
                player.clanMember.clanPoint -= item.personalCapsuleCost;
                Item reward = ItemService.gI().createNewItem((short) itemId);
                reward.itemOptions.add(new Item.ItemOption(30, 0)); // khóa khi nhận từ shop bang
                if (!InventoryService.gI().addItemBag(player, reward)) {
                    // The empty-slot check above makes this exceptionally unlikely; the database purchase is still
                    // protected by its request id and cannot be replayed for a second free item.
                    notify(player, "Không thể đưa vật phẩm vào hành trang; liên hệ quản trị với mã " + requestId + ".");
                    return;
                }
                clan.update();
                InventoryService.gI().sendItemBags(player);
                notify(player, "Đã mua " + itemName(itemId) + ". Vật phẩm đã được khóa.");
                sendSnapshot(player);
            }
        }
    }

    private PurchaseResult persistPurchase(Clan clan, Player player, CatalogItem item, String requestId) {
        int today = (int) LocalDate.now(nro.models.utils.TimeUtil.VIETNAM_ZONE).toEpochDay();
        try (Connection connection = LocalManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (requestExists(connection, clan.id, requestId)) {
                    connection.rollback();
                    return PurchaseResult.fail("Yêu cầu mua này đã được xử lý.");
                }
                int stock = lockStock(connection, clan.id, item.itemId);
                if (stock < 1) {
                    connection.rollback();
                    return PurchaseResult.fail("Mặt hàng đã hết. Bang chủ/phó bang cần nhập thêm.");
                }
                int bought = lockDailyPurchases(connection, clan.id, player.id, item.itemId, today);
                if (bought >= item.dailyLimit) {
                    connection.rollback();
                    return PurchaseResult.fail("Bạn đã mua đủ giới hạn hôm nay.");
                }
                saveStock(connection, clan.id, item.itemId, stock - 1);
                savePurchase(connection, clan.id, player.id, item.itemId, today, bought + 1, requestId);
                connection.commit();
                return PurchaseResult.ok();
            } catch (Exception e) {
                connection.rollback();
                Logger.logException(ClanShopService.class, e, "Transaction mua hàng bang");
                return PurchaseResult.fail("Mua hàng thất bại, tài sản của bạn chưa bị trừ.");
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception e) {
            Logger.logException(ClanShopService.class, e, "Mở transaction mua hàng bang");
            return PurchaseResult.fail("Không thể kết nối cửa hàng bang.");
        }
    }

    /** Handles all shop support items except the data-driven clan gift box. */
    public boolean useSupportItem(Player player, Item item) {
        if (player == null || item == null || item.template == null) return false;
        int id = item.template.id;
        if (id < 2252 || id > 2272) return false;
        if (id == RENAME_TICKET_ITEM_ID) {
            if (player.clan == null || !player.clan.isLeader(player)) {
                notify(player, "Chỉ bang chủ có thể dùng Vé đổi tên bang.");
                return true;
            }
            Input.gI().createFormClanRenameByTicket(player);
            return true;
        }
        if (player.clan == null || !player.clan.isLeader(player)) {
            notify(player, "Chỉ Chủ bang được dùng vật phẩm hỗ trợ bang.");
            return true;
        }
        ClanBuffService.Activation activation = ClanBuffService.Activation.invalid();
        boolean success;
        if (ClanBuffService.gI().isBuffItem(id)) {
            try (Connection connection = LocalManager.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    activation = ClanBuffService.gI().activateInTransaction(
                            connection, player.clan, player.id, id);
                    success = activation.success();
                    if (success) {
                        connection.commit();
                    } else {
                        connection.rollback();
                    }
                } catch (Exception e) {
                    connection.rollback();
                    Logger.logException(ClanShopService.class, e, "Kích hoạt buff bang từ hành trang");
                    success = false;
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (Exception e) {
                Logger.logException(ClanShopService.class, e, "Mở transaction buff bang từ hành trang");
                success = false;
            }
        } else {
            success = applyInstantSupportItem(player, id);
        }
        if (!success) {
            notify(player, "Không thể dùng vật phẩm lúc này.");
            return true;
        }
        InventoryService.gI().subQuantityItemsBag(player, item, 1);
        InventoryService.gI().sendItemBags(player);
        if (activation.success()) {
            ClanBuffService.gI().afterActivation(player.clan, player, activation);
            ClanProgressionService.gI().sendBuffSnapshot(player);
        }
        notify(player, "Đã dùng " + item.template.name + ".");
        return true;
    }

    /** Applies an immediate support effect without deciding where its source item is stored. */
    public boolean applyInstantSupportItem(Player player, int id) {
        if (player == null || id < 2252 || id > 2271) {
            return false;
        }
        boolean success = true;
        try {
            switch (id) {
                case 2270, 2271 -> success = ClanTreeService.gI().accelerateUpgrade(player, id == 2271 ? 24 : 12);
                default -> success = false;
            }
        } catch (Exception e) {
            Logger.logException(ClanShopService.class, e, "Dùng vật phẩm hỗ trợ bang");
            success = false;
        }
        return success;
    }

    public void renameClanByTicket(Player player, String rawName) {
        if (player == null || player.clan == null || !player.clan.isLeader(player)) {
            notify(player, "Chỉ bang chủ có thể đổi tên bang.");
            return;
        }
        String name = rawName == null ? "" : rawName.trim();
        if (name.length() < 3 || name.length() > 20 || Util.haveSpecialCharacter(name)) {
            notify(player, "Tên bang dài 3-20 ký tự, không dùng ký tự đặc biệt.");
            return;
        }
        Item ticket = InventoryService.gI().findItemBag(player, RENAME_TICKET_ITEM_ID);
        if (ticket == null || ticket.quantity < 1) {
            if (ClanItemStorageService.gI().renameWithPendingStorageTicket(player, name)) {
                return;
            }
            notify(player, "Bạn không có Vé đổi tên bang trong túi hoặc kho bang.");
            return;
        }
        synchronized (player.clan) {
            try (Connection connection = LocalManager.getConnection();
                    PreparedStatement duplicate = connection.prepareStatement("SELECT id FROM clan WHERE name=? AND id<>? LIMIT 1")) {
                duplicate.setString(1, name);
                duplicate.setInt(2, player.clan.id);
                try (ResultSet rs = duplicate.executeQuery()) {
                    if (rs.next()) {
                        notify(player, "Tên bang này đã tồn tại.");
                        return;
                    }
                }
                player.clan.name = name;
                player.clan.updateName();
                InventoryService.gI().subQuantityItemsBag(player, ticket, 1);
                InventoryService.gI().sendItemBags(player);
                player.clan.sendMyClanForAllMember();
                notify(player, "Đổi tên bang thành công.");
            } catch (Exception e) {
                Logger.logException(ClanShopService.class, e, "Đổi tên bang bằng vé");
                notify(player, "Không thể đổi tên bang lúc này.");
            }
        }
    }

    private Map<Integer, Integer> loadStock(int clanId) {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        try (Connection connection = LocalManager.getConnection()) {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement("SELECT item_template_id,stock FROM clan_shop_stock WHERE clan_id=?")) {
                ps.setInt(1, clanId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) result.put(rs.getInt(1), rs.getInt(2));
                }
            }
        } catch (Exception e) {
            Logger.logException(ClanShopService.class, e, "Tải tồn kho Cửa hàng bang");
        }
        return result;
    }

    private ClanRow lockClan(Connection connection, int clanId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT clan_point,clan_gold FROM clan WHERE id=? FOR UPDATE")) {
            ps.setInt(1, clanId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new ClanRow(rs.getInt(1), rs.getLong(2)) : null;
            }
        }
    }

    private int lockStock(Connection connection, int clanId, int itemId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT stock FROM clan_shop_stock WHERE clan_id=? AND item_template_id=? FOR UPDATE")) {
            ps.setInt(1, clanId); ps.setInt(2, itemId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        }
    }

    private int lockDailyPurchases(Connection connection, int clanId, long playerId, int itemId, int dayKey) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT quantity FROM clan_shop_purchase WHERE clan_id=? AND player_id=? AND item_template_id=? AND day_key=? FOR UPDATE")) {
            ps.setInt(1, clanId); ps.setLong(2, playerId); ps.setInt(3, itemId); ps.setInt(4, dayKey);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        }
    }

    private boolean requestExists(Connection connection, int clanId, String requestId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT id FROM clan_shop_purchase WHERE clan_id=? AND request_id=? LIMIT 1")) {
            ps.setInt(1, clanId); ps.setString(2, requestId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    private boolean restockRequestExists(Connection connection, int clanId, String requestId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT id FROM clan_shop_restock WHERE clan_id=? AND request_id=? LIMIT 1")) {
            ps.setInt(1, clanId); ps.setString(2, requestId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    private void updateClanMoney(Connection connection, int clanId, int capsule, long gold) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE clan SET clan_point=?,clan_gold=? WHERE id=?")) {
            ps.setInt(1, capsule); ps.setLong(2, gold); ps.setInt(3, clanId);
            if (ps.executeUpdate() != 1) throw new SQLException("Không lưu được quỹ bang");
        }
    }

    private void saveStock(Connection connection, int clanId, int itemId, int stock) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO clan_shop_stock (clan_id,item_template_id,stock) VALUES (?,?,?) ON DUPLICATE KEY UPDATE stock=VALUES(stock)")) {
            ps.setInt(1, clanId); ps.setInt(2, itemId); ps.setInt(3, stock); ps.executeUpdate();
        }
    }

    private void savePurchase(Connection connection, int clanId, long playerId, int itemId, int dayKey, int quantity, String requestId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO clan_shop_purchase (clan_id,player_id,item_template_id,day_key,quantity,request_id) VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE quantity=VALUES(quantity),request_id=VALUES(request_id)")) {
            ps.setInt(1, clanId); ps.setLong(2, playerId); ps.setInt(3, itemId); ps.setInt(4, dayKey); ps.setInt(5, quantity); ps.setString(6, requestId); ps.executeUpdate();
        }
    }

    private void saveRestock(Connection connection, int clanId, long actorId, int itemId, String requestId, int stockBefore, int stockAfter) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO clan_shop_restock (clan_id,item_template_id,request_id,stock_before,stock_after,actor_id) VALUES (?,?,?,?,?,?)")) {
            ps.setInt(1, clanId); ps.setInt(2, itemId); ps.setString(3, requestId); ps.setInt(4, stockBefore);
            ps.setInt(5, stockAfter); ps.setLong(6, actorId); ps.executeUpdate();
        }
    }

    private boolean isTierUnlocked(Clan clan, int tier) {
        if (clan == null) return false;
        int requirement = tier == 1 ? tierOneLevel : tier == 2 ? tierTwoLevel : tierThreeLevel;
        return clan.level >= requirement;
    }

    private boolean requireClan(Player player) {
        if (player != null && player.clan != null) return true;
        if (player != null) notify(player, "Bạn cần có bang hội để dùng Cửa hàng bang.");
        return false;
    }

    private static boolean validRequestId(String requestId) {
        return requestId != null && !requestId.isBlank() && requestId.length() <= REQUEST_ID_MAX;
    }

    private static void notify(Player player, String text) { Service.gI().sendThongBao(player, text); }

    private static String itemName(int itemId) {
        try {
            return ItemService.gI().getTemplate((short) itemId).name;
        } catch (Exception ignored) {
            return "vật phẩm #" + itemId;
        }
    }

    private record ClanRow(int capsule, long gold) { }
    private record PurchaseResult(boolean success, String message) {
        static PurchaseResult ok() { return new PurchaseResult(true, ""); }
        static PurchaseResult fail(String message) { return new PurchaseResult(false, message); }
    }

    private record CatalogItem(int itemId, int tier, int stockCap, int restockCapsule,
            long restockGold, int personalCapsuleCost, long minContribution, int dailyLimit) {
        static CatalogItem parse(int id, String row) {
            try {
                String[] values = row.split(",");
                if (values.length != 7) return null;
                return new CatalogItem(id, Math.max(1, Math.min(3, Integer.parseInt(values[0].trim()))),
                        Math.max(1, Integer.parseInt(values[1].trim())), Math.max(0, Integer.parseInt(values[2].trim())),
                        Math.max(0L, Long.parseLong(values[3].trim())), Math.max(1, Integer.parseInt(values[4].trim())),
                        Math.max(0L, Long.parseLong(values[5].trim())), Math.max(1, Integer.parseInt(values[6].trim())));
            } catch (Exception ignored) { return null; }
        }
    }
}
