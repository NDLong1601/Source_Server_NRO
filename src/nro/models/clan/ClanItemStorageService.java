package nro.models.clan;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import nro.models.admin.GiftBoxConfigService;
import nro.models.data.LocalManager;
import nro.models.item.Item;
import nro.models.network.Message;
import nro.models.player.Player;
import nro.models.services.ItemService;
import nro.models.services.InventoryService;
import nro.models.services.Service;
import nro.models.services_func.Input;
import nro.models.shop.ItemShop;
import nro.models.utils.Logger;

/** Shared consumable storage purchased by the clan leader at Dr. Drief. */
public final class ClanItemStorageService {

    public static final byte REQUEST_VIEW = 121;
    public static final byte REQUEST_USE = 122;
    public static final String OWNER_SHOP_TAG = "SHOP_CLAN_OWNER";
    public static final int SLOT_COUNT = 30;
    public static final byte COST_CLAN_GOLD = 0;
    public static final byte COST_CLAN_GEM = 1;
    public static final byte COST_CLAN_CAPSULE = 2;
    private static final int MAX_QUANTITY_PER_ITEM = 9_999;
    private static final long RENAME_PENDING_MILLIS = 10 * 60 * 1_000L;
    private static final ClanItemStorageService INSTANCE = new ClanItemStorageService();

    private final Map<Long, PendingRename> pendingRenames = new ConcurrentHashMap<>();
    private volatile boolean schemaReady;

    private ClanItemStorageService() {
    }

    public static ClanItemStorageService gI() {
        return INSTANCE;
    }

    public synchronized void ensureSchema(Connection connection) throws SQLException {
        if (schemaReady) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS clan_item_storage ("
                    + "clan_id INT NOT NULL, slot_index INT NOT NULL, item_template_id INT NOT NULL, "
                    + "quantity INT NOT NULL DEFAULT 0, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY (clan_id,slot_index), UNIQUE KEY uk_clan_storage_item (clan_id,item_template_id)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS clan_item_storage_audit ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT, clan_id INT NOT NULL, actor_id BIGINT NOT NULL, "
                    + "action_type VARCHAR(24) NOT NULL, item_template_id INT NOT NULL, quantity_delta INT NOT NULL, "
                    + "quantity_after INT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY (id), KEY idx_clan_storage_audit (clan_id,id)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
        }
        schemaReady = true;
    }

    public void handleRequest(Player player, byte action, Message message) throws IOException {
        if (action == REQUEST_VIEW) {
            sendStorage(player);
        } else if (action == REQUEST_USE) {
            useSlot(player, message.reader().readUnsignedByte());
        }
    }

    public void purchaseFromOwnerShop(Player player, ItemShop itemShop) {
        if (!requireClan(player) || itemShop == null || itemShop.temp == null) {
            return;
        }
        Clan clan = player.clan;
        int itemId = itemShop.temp.id;
        long cost = Math.max(0, itemShop.cost);
        byte currency = itemShop.typeSell;
        if (!clan.isLeader(player)) {
            notify(player, "Chỉ Chủ bang được mua vật phẩm cho kho bang.");
            return;
        }
        if (!ClanShopService.gI().isClanSupportItem(itemId) || cost <= 0
                || (currency != COST_CLAN_GOLD && currency != COST_CLAN_GEM && currency != COST_CLAN_CAPSULE)) {
            notify(player, "Vật phẩm hoặc giá trong shop bang không hợp lệ.");
            return;
        }

        synchronized (clan) {
            try (Connection connection = LocalManager.getConnection()) {
                ensureSchema(connection);
                connection.setAutoCommit(false);
                try {
                    ClanFunds funds = lockClanFunds(connection, clan.id);
                    if (!funds.canPay(currency, cost)) {
                        connection.rollback();
                        notify(player, "Quỹ bang không đủ " + cost + " " + currencyName(currency) + ".");
                        return;
                    }
                    StorageRow row = lockItem(connection, clan.id, itemId);
                    int slot = row == null ? firstEmptySlot(connection, clan.id) : row.slot;
                    int before = row == null ? 0 : row.quantity;
                    if (slot < 0) {
                        connection.rollback();
                        notify(player, "Kho vật phẩm bang đã đầy.");
                        return;
                    }
                    if (before >= MAX_QUANTITY_PER_ITEM) {
                        connection.rollback();
                        notify(player, "Vật phẩm này đã đạt giới hạn " + MAX_QUANTITY_PER_ITEM + ".");
                        return;
                    }
                    int after = before + 1;
                    ClanFunds afterFunds = funds.pay(currency, cost);
                    updateClanFunds(connection, clan.id, afterFunds);
                    saveStorage(connection, clan.id, slot, itemId, after);
                    saveAudit(connection, clan.id, player.id, "PURCHASE", itemId, 1, after);
                    connection.commit();
                    clan.capsuleClan = afterFunds.capsule();
                    clan.clanGold = afterFunds.gold();
                    clan.clanGem = afterFunds.gem();
                    notify(player, "Đã mua " + itemShop.temp.name + " vào ô " + (slot + 1) + " của kho bang.");
                    ClanTreasuryService.gI().sendSnapshot(player);
                } catch (Exception e) {
                    connection.rollback();
                    Logger.logException(ClanItemStorageService.class, e, "Mua vật phẩm vào kho bang");
                    notify(player, "Mua vật phẩm thất bại; tiền tệ bang chưa bị trừ.");
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (Exception e) {
                Logger.logException(ClanItemStorageService.class, e, "Mở transaction kho vật phẩm bang");
                notify(player, "Không thể kết nối kho vật phẩm bang.");
            }
        }
    }

    public void sendStorage(Player player) {
        if (!requireClan(player)) {
            return;
        }
        StorageRow[] slots = loadSlots(player.clan.id);
        Message data = null;
        Message open = null;
        try {
            data = new Message(-35);
            data.writer().writeByte(0);
            data.writer().writeByte(SLOT_COUNT);
            for (StorageRow row : slots) {
                if (row == null || row.quantity <= 0) {
                    data.writer().writeShort(-1);
                    continue;
                }
                Item item = ItemService.gI().createNewItem((short) row.itemId, row.quantity);
                item.itemOptions.add(new Item.ItemOption(30, 0));
                data.writer().writeShort(item.template.id);
                data.writer().writeInt(item.quantity);
                data.writer().writeUTF(item.getInfo());
                data.writer().writeUTF(item.getContent());
                List<Item.ItemOption> options = ItemService.gI().getVisibleItemOptions(item);
                data.writer().writeByte(options.size());
                for (Item.ItemOption option : options) {
                    data.writer().writeByte(option.optionTemplate.id);
                    data.writer().writeShort(option.param);
                }
            }
            player.sendMessage(data);

            open = new Message(-35);
            open.writer().writeByte(1);
            open.writer().writeByte(1); // legacy client flag: shared clan box
            player.sendMessage(open);
        } catch (Exception e) {
            Logger.logException(ClanItemStorageService.class, e, "Gửi kho vật phẩm bang");
            notify(player, "Không thể mở kho vật phẩm bang.");
        } finally {
            if (data != null) {
                data.cleanup();
            }
            if (open != null) {
                open.cleanup();
            }
        }
    }

    private void useSlot(Player player, int slot) {
        if (!requireClan(player) || slot < 0 || slot >= SLOT_COUNT) {
            return;
        }
        Clan clan = player.clan;
        boolean activatedClanBuff = false;
        synchronized (clan) {
            StorageRow row = getSlot(clan.id, slot);
            if (row == null || row.quantity <= 0) {
                notify(player, "Ô kho này không có vật phẩm.");
                sendStorage(player);
                return;
            }
            if (row.itemId == ClanShopService.RENAME_TICKET_ITEM_ID) {
                if (!clan.isLeader(player)) {
                    notify(player, "Chỉ Chủ bang được dùng Vé đổi tên bang.");
                    return;
                }
                pendingRenames.put(player.id, new PendingRename(clan.id, System.currentTimeMillis() + RENAME_PENDING_MILLIS));
                Input.gI().createFormClanRenameByTicket(player);
                return;
            }
            if (row.itemId == ClanShopService.CLAN_GIFT_TICKET_ITEM_ID) {
                notify(player, "Phiếu quà bang phải nằm trong hành trang người tặng.");
                return;
            }
            if (row.itemId >= 2252 && row.itemId <= 2271 && !clan.isLeader(player)) {
                notify(player, "Chỉ Chủ bang được dùng vật phẩm hỗ trợ trong kho bang.");
                return;
            }

            ClanBuffService.Activation activatedBuff = ClanBuffService.Activation.invalid();
            try (Connection connection = LocalManager.getConnection()) {
                ensureSchema(connection);
                connection.setAutoCommit(false);
                try {
                    StorageRow locked = lockSlot(connection, clan.id, slot);
                    if (locked == null || locked.quantity <= 0 || locked.itemId != row.itemId) {
                        connection.rollback();
                        notify(player, "Vật phẩm trong kho vừa thay đổi, hãy mở lại kho.");
                        sendStorage(player);
                        return;
                    }
                    boolean success;
                    if (locked.itemId == ClanShopService.CLAN_GIFT_BOX_ITEM_ID) {
                        success = GiftBoxConfigService.gI().openFromClanStorage(player, locked.itemId);
                    } else if (ClanBuffService.gI().isBuffItem(locked.itemId)) {
                        activatedBuff = ClanBuffService.gI().activateInTransaction(
                                connection, clan, player.id, locked.itemId);
                        success = activatedBuff.success();
                    } else {
                        success = ClanShopService.gI().applyInstantSupportItem(player, locked.itemId);
                    }
                    if (!success) {
                        connection.rollback();
                        return;
                    }
                    int after = locked.quantity - 1;
                    updateQuantity(connection, clan.id, slot, after);
                    saveAudit(connection, clan.id, player.id, "USE", locked.itemId, -1, after);
                    connection.commit();
                    if (activatedBuff.success()) {
                        ClanBuffService.gI().afterActivation(clan, player, activatedBuff);
                        activatedClanBuff = true;
                    }
                    if (locked.itemId == ClanShopService.CLAN_GIFT_BOX_ITEM_ID) {
                        InventoryService.gI().sendItemBags(player);
                    }
                    Service.gI().sendMoney(player);
                    notify(player, "Đã dùng " + itemName(locked.itemId) + " từ kho bang.");
                } catch (Exception e) {
                    connection.rollback();
                    Logger.logException(ClanItemStorageService.class, e, "Dùng vật phẩm trong kho bang");
                    notify(player, "Không thể dùng vật phẩm lúc này.");
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (Exception e) {
                Logger.logException(ClanItemStorageService.class, e, "Mở transaction dùng kho bang");
                notify(player, "Không thể kết nối kho vật phẩm bang.");
            }
        }
        sendStorage(player);
        // Send once more after the storage response. This is the last packet in
        // the successful-use sequence, so the client cannot retain the previous
        // duration while the shared storage is already showing the new quantity.
        if (activatedClanBuff) {
            ClanProgressionService.gI().sendBuffSnapshot(player);
        }
    }

    public boolean hasItem(int clanId, int itemId) {
        try (Connection connection = LocalManager.getConnection()) {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT quantity FROM clan_item_storage WHERE clan_id=? AND item_template_id=?")) {
                ps.setInt(1, clanId);
                ps.setInt(2, itemId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        } catch (Exception e) {
            Logger.logException(ClanItemStorageService.class, e, "Kiểm tra vật phẩm kho bang");
            return false;
        }
    }

    /** The caller owns the transaction and commits the shared ticket consumption. */
    public boolean consumeInTransaction(Connection connection, int clanId, int itemId, int quantity,
            long actorId, String action) throws SQLException {
        ensureSchema(connection);
        StorageRow row = lockItem(connection, clanId, itemId);
        if (row == null || row.quantity < quantity) {
            return false;
        }
        int after = row.quantity - quantity;
        updateQuantity(connection, clanId, row.slot, after);
        saveAudit(connection, clanId, actorId, action, itemId, -quantity, after);
        return true;
    }

    public boolean renameWithPendingStorageTicket(Player player, String name) {
        if (!requireClan(player) || !player.clan.isLeader(player)) {
            return false;
        }
        PendingRename pending = pendingRenames.get(player.id);
        if (pending == null || pending.clanId != player.clan.id || pending.expiresAt < System.currentTimeMillis()) {
            pendingRenames.remove(player.id);
            return false;
        }
        Clan clan = player.clan;
        synchronized (clan) {
            try (Connection connection = LocalManager.getConnection()) {
                ensureSchema(connection);
                connection.setAutoCommit(false);
                try {
                    try (PreparedStatement duplicate = connection.prepareStatement(
                            "SELECT id FROM clan WHERE name=? AND id<>? LIMIT 1")) {
                        duplicate.setString(1, name);
                        duplicate.setInt(2, clan.id);
                        try (ResultSet rs = duplicate.executeQuery()) {
                            if (rs.next()) {
                                connection.rollback();
                                notify(player, "Tên bang này đã tồn tại.");
                                return true;
                            }
                        }
                    }
                    if (!consumeInTransaction(connection, clan.id, ClanShopService.RENAME_TICKET_ITEM_ID,
                            1, player.id, "RENAME")) {
                        connection.rollback();
                        notify(player, "Kho bang không còn Vé đổi tên bang.");
                        return true;
                    }
                    try (PreparedStatement update = connection.prepareStatement("UPDATE clan SET name=? WHERE id=?")) {
                        update.setString(1, name);
                        update.setInt(2, clan.id);
                        if (update.executeUpdate() != 1) {
                            throw new SQLException("Không tìm thấy bang cần đổi tên");
                        }
                    }
                    connection.commit();
                    pendingRenames.remove(player.id);
                    clan.name = name;
                    clan.sendMyClanForAllMember();
                    notify(player, "Đổi tên bang thành công.");
                    return true;
                } catch (Exception e) {
                    connection.rollback();
                    Logger.logException(ClanItemStorageService.class, e, "Đổi tên bằng vé trong kho bang");
                    notify(player, "Không thể đổi tên bang lúc này.");
                    return true;
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (Exception e) {
                Logger.logException(ClanItemStorageService.class, e, "Mở transaction đổi tên kho bang");
                notify(player, "Không thể kết nối kho vật phẩm bang.");
                return true;
            }
        }
    }

    private StorageRow[] loadSlots(int clanId) {
        StorageRow[] slots = new StorageRow[SLOT_COUNT];
        try (Connection connection = LocalManager.getConnection()) {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT slot_index,item_template_id,quantity FROM clan_item_storage WHERE clan_id=? AND quantity>0")) {
                ps.setInt(1, clanId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int slot = rs.getInt(1);
                        if (slot >= 0 && slot < SLOT_COUNT) {
                            slots[slot] = new StorageRow(slot, rs.getInt(2), rs.getInt(3));
                        }
                    }
                }
            }
        } catch (Exception e) {
            Logger.logException(ClanItemStorageService.class, e, "Tải kho vật phẩm bang");
        }
        return slots;
    }

    private StorageRow getSlot(int clanId, int slot) {
        try (Connection connection = LocalManager.getConnection()) {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT item_template_id,quantity FROM clan_item_storage WHERE clan_id=? AND slot_index=?")) {
                ps.setInt(1, clanId);
                ps.setInt(2, slot);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? new StorageRow(slot, rs.getInt(1), rs.getInt(2)) : null;
                }
            }
        } catch (Exception e) {
            Logger.logException(ClanItemStorageService.class, e, "Đọc ô kho vật phẩm bang");
            return null;
        }
    }

    private ClanFunds lockClanFunds(Connection connection, int clanId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT clan_point,clan_gold,clan_gem FROM clan WHERE id=? FOR UPDATE")) {
            ps.setInt(1, clanId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Không tìm thấy bang");
                }
                return new ClanFunds(Math.max(0, rs.getInt(1)), Math.max(0L, rs.getLong(2)), Math.max(0L, rs.getLong(3)));
            }
        }
    }

    private void updateClanFunds(Connection connection, int clanId, ClanFunds funds) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE clan SET clan_point=?,clan_gold=?,clan_gem=? WHERE id=?")) {
            ps.setInt(1, funds.capsule());
            ps.setLong(2, funds.gold());
            ps.setLong(3, funds.gem());
            ps.setInt(4, clanId);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Không cập nhật được tiền tệ bang");
            }
        }
    }

    private StorageRow lockItem(Connection connection, int clanId, int itemId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT slot_index,quantity FROM clan_item_storage WHERE clan_id=? AND item_template_id=? FOR UPDATE")) {
            ps.setInt(1, clanId);
            ps.setInt(2, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new StorageRow(rs.getInt(1), itemId, rs.getInt(2)) : null;
            }
        }
    }

    private StorageRow lockSlot(Connection connection, int clanId, int slot) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT item_template_id,quantity FROM clan_item_storage WHERE clan_id=? AND slot_index=? FOR UPDATE")) {
            ps.setInt(1, clanId);
            ps.setInt(2, slot);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new StorageRow(slot, rs.getInt(1), rs.getInt(2)) : null;
            }
        }
    }

    private int firstEmptySlot(Connection connection, int clanId) throws SQLException {
        boolean[] used = new boolean[SLOT_COUNT];
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT slot_index FROM clan_item_storage WHERE clan_id=? AND quantity>0 FOR UPDATE")) {
            ps.setInt(1, clanId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int slot = rs.getInt(1);
                    if (slot >= 0 && slot < SLOT_COUNT) {
                        used[slot] = true;
                    }
                }
            }
        }
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (!used[slot]) {
                return slot;
            }
        }
        return -1;
    }

    private void saveStorage(Connection connection, int clanId, int slot, int itemId, int quantity) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO clan_item_storage (clan_id,slot_index,item_template_id,quantity) VALUES (?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE item_template_id=VALUES(item_template_id),quantity=VALUES(quantity)")) {
            ps.setInt(1, clanId);
            ps.setInt(2, slot);
            ps.setInt(3, itemId);
            ps.setInt(4, quantity);
            ps.executeUpdate();
        }
    }

    private void updateQuantity(Connection connection, int clanId, int slot, int quantity) throws SQLException {
        if (quantity <= 0) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM clan_item_storage WHERE clan_id=? AND slot_index=?")) {
                ps.setInt(1, clanId);
                ps.setInt(2, slot);
                ps.executeUpdate();
            }
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE clan_item_storage SET quantity=? WHERE clan_id=? AND slot_index=?")) {
            ps.setInt(1, quantity);
            ps.setInt(2, clanId);
            ps.setInt(3, slot);
            ps.executeUpdate();
        }
    }

    private void saveAudit(Connection connection, int clanId, long actorId, String action, int itemId,
            int delta, int after) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO clan_item_storage_audit (clan_id,actor_id,action_type,item_template_id,quantity_delta,quantity_after) "
                + "VALUES (?,?,?,?,?,?)")) {
            ps.setInt(1, clanId);
            ps.setLong(2, actorId);
            ps.setString(3, action);
            ps.setInt(4, itemId);
            ps.setInt(5, delta);
            ps.setInt(6, after);
            ps.executeUpdate();
        }
    }

    private static String itemName(int itemId) {
        try {
            return ItemService.gI().getTemplate(itemId).name;
        } catch (Exception ignored) {
            return "vật phẩm";
        }
    }

    private static String currencyName(byte currency) {
        return switch (currency) {
            case COST_CLAN_GOLD -> "Vàng bang";
            case COST_CLAN_GEM -> "Ngọc bang";
            case COST_CLAN_CAPSULE -> "Capsule bang";
            default -> "tiền tệ bang";
        };
    }

    private static boolean requireClan(Player player) {
        if (player == null || player.clan == null || player.clanMember == null) {
            if (player != null) {
                notify(player, "Bạn cần có bang hội để dùng kho vật phẩm bang.");
            }
            return false;
        }
        return true;
    }

    private static void notify(Player player, String text) {
        if (player != null) {
            Service.gI().sendThongBao(player, text);
        }
    }

    private record StorageRow(int slot, int itemId, int quantity) {
    }

    private record PendingRename(int clanId, long expiresAt) {
    }

    private record ClanFunds(int capsule, long gold, long gem) {
        boolean canPay(byte currency, long amount) {
            return switch (currency) {
                case COST_CLAN_GOLD -> gold >= amount;
                case COST_CLAN_GEM -> gem >= amount;
                case COST_CLAN_CAPSULE -> capsule >= amount;
                default -> false;
            };
        }

        ClanFunds pay(byte currency, long amount) {
            return switch (currency) {
                case COST_CLAN_GOLD -> new ClanFunds(capsule, gold - amount, gem);
                case COST_CLAN_GEM -> new ClanFunds(capsule, gold, gem - amount);
                case COST_CLAN_CAPSULE -> new ClanFunds((int) (capsule - amount), gold, gem);
                default -> this;
            };
        }
    }
}
