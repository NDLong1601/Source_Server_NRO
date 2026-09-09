package nro.models.clan;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import nro.models.data.LocalManager;
import nro.models.item.Item;
import nro.models.network.Message;
import nro.models.player.InventoryPersistenceSnapshot;
import nro.models.player.Player;
import nro.models.player.PlayerPersistenceComponent;
import nro.models.player.PlayerPersistenceState;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.Service;
import nro.models.utils.Logger;

/**
 * Server-authoritative MVP for the single shared clan tree.  Item consumption,
 * daily limits and all growth requirements are checked here; the client only
 * renders the snapshot it receives.
 */
public final class ClanTreeService {

    public static final byte REQUEST_VIEW = 104;
    public static final byte REQUEST_WATER = 105;
    public static final byte REQUEST_FERTILIZE = 106;
    public static final byte REQUEST_HARVEST = 107;
    public static final byte REQUEST_ASK_HELP = 108;
    public static final byte REQUEST_HELP_WATER = 109;
    public static final byte REQUEST_COMPLETE_UPGRADE = 110;
    public static final byte REQUEST_START_UPGRADE = 111;

    private static final ClanTreeService INSTANCE = new ClanTreeService();

    private final Map<Integer, ClanTreeState> cache = new ConcurrentHashMap<>();
    private final ClanTreeConfig config = ClanTreeConfig.load(Path.of("data", "clan_tree.properties"));
    private volatile boolean schemaReady;

    private ClanTreeService() {
    }

    public static ClanTreeService gI() {
        return INSTANCE;
    }

    public synchronized void ensureSchema(Connection connection) throws SQLException {
        if (schemaReady) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS clan_tree ("
                    + "clan_id INT NOT NULL, level INT NOT NULL DEFAULT 1, growth BIGINT NOT NULL DEFAULT 0, "
                    + "water INT NOT NULL DEFAULT 0, fertilizer INT NOT NULL DEFAULT 0, "
                    + "vitality_day INT NOT NULL DEFAULT 0, vitality_amount INT NOT NULL DEFAULT 0, "
                    + "pending_gold BIGINT NOT NULL DEFAULT 0, pending_capsule INT NOT NULL DEFAULT 0, "
                    + "last_production_at BIGINT NOT NULL, help_expires_at BIGINT NOT NULL DEFAULT 0, "
                    + "last_help_at BIGINT NOT NULL DEFAULT 0, upgrade_started_at BIGINT NOT NULL DEFAULT 0, "
                    + "upgrade_ready_at BIGINT NOT NULL DEFAULT 0, version BIGINT NOT NULL DEFAULT 0, "
                    + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY (clan_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS clan_tree_member ("
                    + "clan_id INT NOT NULL, player_id BIGINT NOT NULL, day_key INT NOT NULL, "
                    + "water_count INT NOT NULL DEFAULT 0, fertilizer_count INT NOT NULL DEFAULT 0, "
                    + "growth_contribution BIGINT NOT NULL DEFAULT 0, last_action_at BIGINT NOT NULL DEFAULT 0, "
                    + "PRIMARY KEY (clan_id, player_id), KEY idx_clan_tree_member_day (clan_id, day_key)) "
                    + "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
        }
        ensureTreeColumn(connection, "upgrade_started_at", "BIGINT NOT NULL DEFAULT 0");
        ensureTreeColumn(connection, "upgrade_ready_at", "BIGINT NOT NULL DEFAULT 0");
        registerTreeImages(connection);
        schemaReady = true;
    }

    private void ensureTreeColumn(Connection connection, String columnName, String definition) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SHOW COLUMNS FROM clan_tree LIKE ?")) {
            ps.setString(1, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE clan_tree ADD COLUMN " + columnName + " " + definition);
        }
    }

    private void registerTreeImages(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT IGNORE INTO img_by_name (`NAME`, n_frame) VALUES (?, 1)")) {
            for (int level = 1; level <= config.maxLevel(); level++) {
                statement.setString(1, ClanAppearanceService.gI().resourceName(level));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    public boolean isTreeAction(byte action) {
        return action >= REQUEST_VIEW && action <= REQUEST_START_UPGRADE;
    }

    public void handleRequest(Player player, byte action, Message message) throws IOException {
        ClanFeatureFlags flags = ClanFeatureFlags.gI();
        if (!flags.isEnabled(ClanFeatureFlags.Feature.TREE)) {
            notify(player, "Cây bang đang tạm khóa.");
            return;
        }
        if (action != REQUEST_VIEW && !flags.canMutate(ClanFeatureFlags.Feature.TREE)) {
            notify(player, "Cây bang đang ở chế độ chỉ xem; thao tác thay đổi tạm khóa.");
            return;
        }
        switch (action) {
            case REQUEST_VIEW:
                sendSnapshot(player);
                ClanAppearanceService.gI().sendLegacyStatus(player);
                break;
            case REQUEST_WATER:
            case REQUEST_HELP_WATER:
                water(player, action == REQUEST_HELP_WATER);
                break;
            case REQUEST_FERTILIZE:
                fertilize(player);
                break;
            case REQUEST_HARVEST:
                harvest(player);
                break;
            case REQUEST_ASK_HELP:
                askForHelp(player);
                break;
            case REQUEST_COMPLETE_UPGRADE:
                completeUpgrade(player);
                break;
            case REQUEST_START_UPGRADE:
                requestUpgrade(player);
                break;
            default:
                break;
        }
    }

    /** A clan owns a level-1 tree immediately after its database row exists. */
    public void initializeClan(Clan clan) {
        if (ClanFeatureFlags.gI().isEnabled(ClanFeatureFlags.Feature.TREE)
                && clan != null && clan.id >= 0) {
            getState(clan.id);
        }
    }

    public void disposeClan(int clanId) {
        if (clanId >= 0) {
            cache.remove(clanId);
        }
    }

    /** Minimal immutable tree state used by Clan Value without exposing mutable tree internals. */
    public ValueState valueState(int clanId) {
        if (clanId < 0) {
            return new ValueState(0, 0L);
        }
        ClanTreeState state = getState(clanId);
        synchronized (state) {
            return new ValueState(state.level, state.version);
        }
    }

    public record ValueState(int level, long version) {
    }

    public int maxLevel() {
        return config.maxLevel();
    }

    /**
     * Admin-only visual-test override. It preserves accumulated resources and
     * settles production at the old level before selecting the requested level.
     */
    public AdminLevelChange setLevelForAdminTesting(Player admin, int requestedLevel) {
        if (admin == null || !admin.isAdmin()) {
            return AdminLevelChange.failure("Bạn không có quyền dùng lệnh này.");
        }
        Clan clan = admin.clan;
        if (clan == null || clan.id < 0 || clan.getClanMember((int) admin.id) == null) {
            return AdminLevelChange.failure("Admin cần thuộc một bang hội để chỉnh cấp Cây bang.");
        }
        if (requestedLevel < 1 || requestedLevel > config.maxLevel()) {
            return AdminLevelChange.failure("Cấp cây phải nằm trong khoảng 1-" + config.maxLevel() + ".");
        }

        ClanTreeState state = getState(clan.id);
        synchronized (state) {
            if (!admin.isAdmin() || admin.clan == null || admin.clan.id != state.clanId
                    || admin.clan.getClanMember((int) admin.id) == null) {
                return AdminLevelChange.failure("Trạng thái admin hoặc bang hội đã thay đổi; lệnh bị hủy.");
            }
            int previousLevel = state.level;
            if (previousLevel == requestedLevel) {
                ClanValueService.gI().sendSnapshot(admin);
                ClanAppearanceService.gI().sendSnapshot(admin);
                return AdminLevelChange.success(previousLevel, requestedLevel, false);
            }

            ClanTreeState backup = copyState(state);
            settleProduction(state);
            state.level = requestedLevel;
            state.upgradeStartedAt = 0L;
            state.upgradeReadyAt = 0L;
            state.version = safeAdd(state.version, 1L);
            if (!persist(state)) {
                restoreState(state, backup);
                return AdminLevelChange.failure("Không lưu được cấp Cây bang; dữ liệu cũ đã được giữ nguyên.");
            }

            ClanValueService.gI().snapshot(clan);
            broadcastSnapshot(clan, state);
            ClanValueService.gI().sendSnapshot(admin);
            ClanAppearanceService.gI().sendSnapshot(admin);
            Logger.logln(Logger.YELLOW, "[ADMIN][CLAN_TREE_LEVEL] actorId=" + admin.id
                    + " clanId=" + clan.id + " previous=" + previousLevel + " current=" + requestedLevel);
            return AdminLevelChange.success(previousLevel, requestedLevel, true);
        }
    }

    public record AdminLevelChange(boolean success, boolean changed, int previousLevel,
            int currentLevel, String message) {

        private static AdminLevelChange success(int previousLevel, int currentLevel, boolean changed) {
            return new AdminLevelChange(true, changed, previousLevel, currentLevel, "");
        }

        private static AdminLevelChange failure(String message) {
            return new AdminLevelChange(false, false, 0, 0, message);
        }
    }

    /**
     * Startup backfill for clans created before the clan-tree feature existed.
     * INSERT IGNORE deliberately preserves every existing tree's level and progress.
     */
    public void initializeClan(Connection connection, Clan clan) throws SQLException {
        if (connection == null || clan == null || clan.id < 0) {
            return;
        }
        ensureSchema(connection);
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT IGNORE INTO clan_tree (clan_id, last_production_at) VALUES (?, ?)")) {
            insert.setInt(1, clan.id);
            insert.setLong(2, System.currentTimeMillis());
            insert.executeUpdate();
        }
    }

    public void sendSnapshot(Player player) {
        if (!ClanFeatureFlags.gI().isEnabled(ClanFeatureFlags.Feature.TREE)) {
            return;
        }
        if (!requireClan(player)) {
            return;
        }
        ClanTreeState state = getState(player.clan.id);
        synchronized (state) {
            settleProduction(state);
            persist(state);
            sendSnapshot(player, state, memberState(player, state));
        }
    }

    /** Consumed only after a shop acceleration voucher verifies an active tree upgrade. */
    public boolean accelerateUpgrade(Player player, int hours) {
        if (!ClanFeatureFlags.gI().canMutate(ClanFeatureFlags.Feature.TREE)) {
            notify(player, "Cây bang đang ở chế độ chỉ xem; chưa thể tăng tốc.");
            return false;
        }
        if (!requireClan(player) || hours <= 0) {
            return false;
        }
        ClanTreeState state = getState(player.clan.id);
        synchronized (state) {
            if (player.clan == null || player.clan.id != state.clanId) {
                notify(player, "Bạn không còn thuộc bang hội này.");
                return false;
            }
            long now = System.currentTimeMillis();
            if (state.upgradeReadyAt <= now) {
                notify(player, "Cây bang không có nâng cấp đang chờ để tăng tốc.");
                return false;
            }
            state.upgradeReadyAt = Math.max(now, state.upgradeReadyAt - hours * 60L * 60L * 1000L);
            state.version++;
            persist(state);
            notify(player, "Đã rút ngắn thời gian nâng Cây bang " + hours + " giờ.");
            broadcastSnapshot(player.clan, state);
            return true;
        }
    }

    private void water(Player player, boolean fromHelp) {
        if (!requireClan(player)) {
            return;
        }
        ClanTreeState state = getState(player.clan.id);
        synchronized (player) {
            synchronized (state) {
                if (player.clan == null || player.clan.id != state.clanId) {
                    notify(player, "Bạn không còn thuộc bang hội này.");
                    return;
                }
                MemberState member = memberState(player, state);
                long now = System.currentTimeMillis();
                if (state.upgradeReadyAt > 0L) {
                    notifyUpgradeState(player, state, now);
                    return;
                }
                if (now - member.lastActionAt < config.actionCooldownMs()) {
                    notify(player, "Thao tác quá nhanh, vui lòng chờ một lát.");
                    return;
                }
                if (fromHelp && state.helpExpiresAt < now) {
                    notify(player, "Lời kêu gọi chăm cây đã hết hạn.");
                    return;
                }
                if (fromHelp && state.water >= waterRequired(state.level)) {
                    notify(player, "Cây bang đã đủ nước cho cấp hiện tại.");
                    return;
                }
                if (member.waterCount >= config.dailyWaterLimit()) {
                    notify(player, "Bạn đã tưới đủ " + config.dailyWaterLimit() + " lượt hôm nay.");
                    return;
                }
                Item water = InventoryService.gI().findItemBag(player, config.waterItemId());
                if (water == null || water.quantity < 1) {
                    notify(player, "Bạn cần Bình nước để tưới Cây bang.");
                    return;
                }
                InventoryService.gI().subQuantityItemsBag(player, water, 1);
                member.waterCount++;
                member.growthContribution += growthFromWater(state.level);
                member.lastActionAt = now;
                state.water++;
                state.growth += growthFromWater(state.level);
                refreshDailyVitality(state);
                state.vitalityAmount++;
                state.version++;
                persist(state);
                persistMember(state, member);
                ClanEconomyMetricsService.gI().record(ClanEconomyMetricsService.Signal.TREE_WATER,
                        1L, state.clanId);
                if (state.vitalityAmount == waterRequired(state.level)) {
                    ClanEconomyMetricsService.gI().record(
                            ClanEconomyMetricsService.Signal.TREE_VITALITY_REACHED, state.clanId);
                }
                InventoryService.gI().sendItemBags(player);
                int exp = ClanProgressionService.gI().expWater();
                ClanTreasuryService.gI().addActivityContribution(player.clan.id, player.id, 10L);
                boolean expAdded = ClanProgressionService.gI().addExp(player.clan, exp, player);
                if (expAdded) {
                    ClanProgressionService.gI().sendSnapshot(player);
                }
                notify(player, "Đã tưới Cây bang (" + member.waterCount + "/" + config.dailyWaterLimit() + ")."
                        + (expAdded ? " +" + exp + " Clan EXP (" + player.clan.clanExp + "/"
                                + ClanProgressionService.gI().expRequired(player.clan.level) + ")." : ""));
                sendSnapshot(player, state, member);
                broadcastSnapshot(player.clan, state);
            }
        }
    }

    private void fertilize(Player player) {
        if (!requireClan(player)) {
            return;
        }
        ClanTreeState state = getState(player.clan.id);
        synchronized (player) {
            synchronized (state) {
                MemberState member = memberState(player, state);
                long now = System.currentTimeMillis();
                if (state.upgradeReadyAt > 0L) {
                    notifyUpgradeState(player, state, now);
                    return;
                }
                if (now - member.lastActionAt < config.actionCooldownMs()) {
                    notify(player, "Thao tác quá nhanh, vui lòng chờ một lát.");
                    return;
                }
                if (member.fertilizerCount >= config.dailyFertilizerLimit()) {
                    notify(player, "Bạn đã bón đủ " + config.dailyFertilizerLimit() + " lượt hôm nay.");
                    return;
                }
                Item fertilizer = InventoryService.gI().findItemBag(player, config.fertilizerItemId());
                if (fertilizer == null || fertilizer.quantity < 1) {
                    notify(player, "Bạn cần Phân bón để chăm Cây bang.");
                    return;
                }
                InventoryService.gI().subQuantityItemsBag(player, fertilizer, 1);
                member.fertilizerCount++;
                member.growthContribution += growthFromFertilizer(state.level);
                member.lastActionAt = now;
                state.fertilizer++;
                state.growth += growthFromFertilizer(state.level);
                refreshDailyVitality(state);
                state.vitalityAmount++;
                state.version++;
                persist(state);
                persistMember(state, member);
                ClanEconomyMetricsService.gI().record(ClanEconomyMetricsService.Signal.TREE_FERTILIZE,
                        1L, state.clanId);
                if (state.vitalityAmount == waterRequired(state.level)) {
                    ClanEconomyMetricsService.gI().record(
                            ClanEconomyMetricsService.Signal.TREE_VITALITY_REACHED, state.clanId);
                }
                InventoryService.gI().sendItemBags(player);
                int exp = ClanProgressionService.gI().expFertilize();
                ClanTreasuryService.gI().addActivityContribution(player.clan.id, player.id, 30L);
                boolean expAdded = ClanProgressionService.gI().addExp(player.clan, exp, player);
                if (expAdded) {
                    ClanProgressionService.gI().sendSnapshot(player);
                }
                notify(player, "Đã bón phân cho Cây bang."
                        + (expAdded ? " +" + exp + " Clan EXP (" + player.clan.clanExp + "/"
                                + ClanProgressionService.gI().expRequired(player.clan.level) + ")." : ""));
                sendSnapshot(player, state, member);
                broadcastSnapshot(player.clan, state);
            }
        }
    }

    private void harvest(Player player) {
        if (!requireClan(player)) {
            return;
        }
        if (!ClanFeatureFlags.gI().canMutate(ClanFeatureFlags.Feature.TREASURY)) {
            notify(player, "Kho bang đang ở chế độ chỉ xem; chưa thể nhận sản lượng Cây bang.");
            return;
        }
        PlayerPersistenceState persistence = player.getPersistenceState();
        if (player.isRemovingOrDisposed() || player.isPersistenceQuarantined()) {
            notify(player, "Dữ liệu nhân vật đang được bảo vệ; vui lòng đăng nhập lại trước khi thu hoạch.");
            return;
        }
        if (!beginExclusiveSave(persistence)) {
            notify(player, "Nhân vật đang được lưu dữ liệu, vui lòng thử thu hoạch lại.");
            return;
        }
        try {
            Clan clan = player.clan;
            if (clan == null) {
                notify(player, "Bạn không còn thuộc bang hội này.");
                return;
            }
            ClanTreeState state = getState(clan.id);
            synchronized (player) {
                synchronized (player.inventory) {
                    synchronized (clan) {
                        synchronized (state) {
                            if (player.clan != clan || clan.id != state.clanId) {
                                notify(player, "Bạn không còn thuộc bang hội này.");
                                return;
                            }
                            long expectedTreeVersion = state.version;
                            settleProduction(state);
                            if (state.pendingGold <= 0 && state.pendingCapsule <= 0) {
                                persist(state);
                                notify(player, "Cây bang chưa có sản lượng để thu hoạch.");
                                sendSnapshot(player, state, memberState(player, state));
                                return;
                            }
                            Item giftBox = ItemService.gI().createNewItem(
                                    (short) ClanShopService.CLAN_GIFT_BOX_ITEM_ID);
                            giftBox.itemOptions.add(new Item.ItemOption(30, 0));
                            List<Item> committedBag = InventoryService.gI().copyList(player.inventory.itemsBag);
                            if (!InventoryService.gI().addItemList(committedBag, giftBox)) {
                                notify(player, "Hành trang đã đầy, cần chỗ trống để nhận Hộp quà bang khi thu hoạch.");
                                return;
                            }
                            InventoryPersistenceSnapshot inventory = InventoryPersistenceSnapshot.capture(
                                    player, player.getWallet().getBalance(nro.models.player.Currency.GOLD),
                                    (int) player.getWallet().getBalance(nro.models.player.Currency.GEM), committedBag);
                            long expectedSaveVersion = persistence.saveVersion();
                            HarvestResult result = persistHarvest(player, clan, state, expectedTreeVersion,
                                    expectedSaveVersion, inventory);
                            if (!result.success) {
                                if (result.reloadTree) {
                                    cache.remove(clan.id, state);
                                }
                                if (result.quarantinePlayer) {
                                    player.quarantinePersistence();
                                }
                                notify(player, result.message);
                                return;
                            }
                            ClanEconomyMetricsService.gI().record(
                                    ClanEconomyMetricsService.Signal.TREE_HARVEST, result.gold, clan.id);
                            state.pendingGold = 0L;
                            state.pendingCapsule = 0;
                            state.version = result.treeVersion;
                            clan.clanGold = result.clanGold;
                            clan.capsuleClan = result.clanCapsule;
                            clan.treasuryVersion = result.treasuryVersion;
                            try {
                                inventory.applyTo(player);
                                persistence.acknowledgeExternalCommit(expectedSaveVersion,
                                        expectedSaveVersion + 1L, PlayerPersistenceComponent.INVENTORY);
                            } catch (RuntimeException projectionFailure) {
                                player.quarantinePersistence();
                                Logger.logException(ClanTreeService.class, projectionFailure,
                                        "Thu hoạch đã commit nhưng không chiếu được hành trang");
                                notify(player, "Thu hoạch đã được ghi nhận; vui lòng đăng nhập lại để đồng bộ Hộp quà bang.");
                                return;
                            }
                            InventoryService.gI().sendItemBags(player);
                            notify(player, "Đã thu hoạch cho bang: " + result.gold + " vàng bang"
                                    + (result.capsule > 0 ? " và " + result.capsule + " Capsule Bang" : "")
                                    + "; bạn nhận 1 Hộp quà bang.");
                            sendClanNotice(clan, player.name + " đã thu hoạch Cây bang: +" + result.gold
                                    + " vàng bang" + (result.capsule > 0
                                    ? ", +" + result.capsule + " Capsule Bang." : "."));
                            ClanTreasuryService.gI().sendSnapshot(player);
                            broadcastSnapshot(clan, state);
                        }
                    }
                }
            }
        } finally {
            persistence.finishSave();
        }
    }

    private HarvestResult persistHarvest(Player player, Clan clan, ClanTreeState state,
            long expectedTreeVersion, long expectedSaveVersion, InventoryPersistenceSnapshot inventory) {
        try (Connection connection = LocalManager.getConnection()) {
            ensureSchema(connection);
            ClanTreasuryService.gI().ensureSchema(connection);
            connection.setAutoCommit(false);
            try {
                ClanFunds funds = lockClanFunds(connection, clan.id);
                if (funds == null) {
                    connection.rollback();
                    return HarvestResult.fail("Bang hội không còn tồn tại.", true);
                }
                long databaseTreeVersion = lockTreeVersion(connection, clan.id);
                if (databaseTreeVersion != expectedTreeVersion) {
                    connection.rollback();
                    return HarvestResult.fail("Dữ liệu Cây bang vừa thay đổi; vui lòng mở lại cây.", true);
                }
                if (!lockPlayerForHarvest(connection, player.id, clan.id, expectedSaveVersion)) {
                    connection.rollback();
                    return HarvestResult.fail(
                            "Phiên dữ liệu nhân vật không còn đồng bộ; vui lòng đăng nhập lại.", false, true);
                }
                if (funds.gold > Long.MAX_VALUE - state.pendingGold
                        || funds.capsule > Integer.MAX_VALUE - state.pendingCapsule
                        || funds.treasuryVersion == Long.MAX_VALUE || state.version == Long.MAX_VALUE) {
                    connection.rollback();
                    return HarvestResult.fail("Quỹ bang đã chạm giới hạn lưu trữ; chưa thể thu hoạch.", false);
                }
                long nextGold = funds.gold + state.pendingGold;
                int nextCapsule = funds.capsule + state.pendingCapsule;
                long nextTreasuryVersion = funds.treasuryVersion + 1L;
                long nextTreeVersion = state.version + 1L;
                updateTreeAfterHarvest(connection, state, expectedTreeVersion, nextTreeVersion);
                updateClanAfterHarvest(connection, clan.id, nextGold, nextCapsule, nextTreasuryVersion);
                updatePlayerBagAfterHarvest(connection, player.id, clan.id, expectedSaveVersion, inventory);
                String sourceId = clan.id + ":" + nextTreeVersion;
                String metadata = "{\"treeLevel\":" + state.level + ",\"treeVersion\":"
                        + nextTreeVersion + "}";
                if (state.pendingGold > 0L) {
                    ClanTreasuryService.gI().appendLedger(connection, clan.id, player.id, player.name,
                            "TREE_HARVEST", ClanTreasuryService.CURRENCY_GOLD, state.pendingGold, nextGold,
                            null, metadata, ClanTreasuryService.ledgerRequestId("TREE_HARVEST", sourceId,
                                    ClanTreasuryService.CURRENCY_GOLD));
                }
                if (state.pendingCapsule > 0) {
                    ClanTreasuryService.gI().appendLedger(connection, clan.id, player.id, player.name,
                            "TREE_HARVEST", ClanTreasuryService.CURRENCY_CAPSULE, state.pendingCapsule,
                            nextCapsule, null, metadata, ClanTreasuryService.ledgerRequestId(
                                    "TREE_HARVEST", sourceId, ClanTreasuryService.CURRENCY_CAPSULE));
                }
                connection.commit();
                return HarvestResult.ok(state.pendingGold, state.pendingCapsule, nextGold, nextCapsule,
                        nextTreasuryVersion, nextTreeVersion);
            } catch (Exception error) {
                connection.rollback();
                ClanEconomyMetricsService.gI().record(
                        ClanEconomyMetricsService.Signal.TRANSACTION_ROLLBACK, clan.id);
                Logger.logException(ClanTreeService.class, error, "Transaction thu hoạch Cây bang");
                return HarvestResult.fail("Không thể thu hoạch lúc này; sản lượng và Hộp quà chưa thay đổi.", false);
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException resetError) {
                    Logger.logException(ClanTreeService.class, resetError,
                            "Không khôi phục được auto-commit sau thu hoạch Cây bang");
                }
            }
        } catch (Exception error) {
            Logger.logException(ClanTreeService.class, error, "Không mở được transaction thu hoạch Cây bang");
            return HarvestResult.fail("Không thể kết nối Cây bang; vui lòng thử lại.", false);
        }
    }

    private void askForHelp(Player player) {
        if (!requireClan(player)) {
            return;
        }
        ClanTreeState state = getState(player.clan.id);
        synchronized (state) {
            long now = System.currentTimeMillis();
            if (now - state.lastHelpAt < config.helpCooldownMs()) {
                notify(player, "Hãy chờ trước khi kêu gọi chăm cây lần nữa.");
                return;
            }
            state.lastHelpAt = now;
            state.helpExpiresAt = safeAdd(now, config.helpDurationMs());
            state.version++;
            persist(state);
            sendClanHelpRequest(player.clan, player);
            broadcastSnapshot(player.clan, state);
        }
    }

    private void requestUpgrade(Player player) {
        if (!requireClan(player)) {
            return;
        }
        Clan clan = player.clan;
        ClanTreeState state = getState(clan.id);
        synchronized (state) {
            if (state.level >= config.maxLevel()) {
                notify(player, "Cây bang đã đạt cấp tối đa.");
                sendSnapshot(player, state, memberState(player, state));
                return;
            }
            if (state.upgradeReadyAt > 0L) {
                notifyUpgradeState(player, state, System.currentTimeMillis());
                sendSnapshot(player, state, memberState(player, state));
                return;
            }
            String missing = missingUpgradeRequirements(state, clan);
            if (!missing.isEmpty()) {
                notify(player, "Chưa đủ điều kiện nâng Cây bang lên cấp " + (state.level + 1)
                        + ". Còn thiếu:\n" + missing);
                sendSnapshot(player, state, memberState(player, state));
                return;
            }
            startUpgrade(state, clan);
            persist(state);
            notify(player, "Đã bắt đầu nâng Cây bang lên cấp " + (state.level + 1)
                    + ". Thời gian chờ: " + upgradeDaysForTargetLevel(state.level + 1) + " ngày.");
            broadcastSnapshot(clan, state);
        }
    }

    private void startUpgrade(ClanTreeState state, Clan clan) {
        long now = System.currentTimeMillis();
        state.upgradeStartedAt = now;
        state.upgradeReadyAt = now + upgradeDurationMs(state.level + 1);
        state.version++;
        sendClanNotice(clan, "Cây bang bắt đầu nâng lên cấp " + (state.level + 1)
                + ", hoàn tất sau " + upgradeDaysForTargetLevel(state.level + 1) + " ngày.");
    }

    private void completeUpgrade(Player player) {
        if (!requireClan(player)) {
            return;
        }
        ClanTreeState state = getState(player.clan.id);
        synchronized (state) {
            Clan clan = player.clan;
            long now = System.currentTimeMillis();
            if (state.level >= config.maxLevel()) {
                notify(player, "Cây bang đã đạt cấp tối đa.");
                sendSnapshot(player, state, memberState(player, state));
                return;
            }
            if (state.upgradeReadyAt <= 0L) {
                notify(player, "Cây bang chưa bắt đầu nâng cấp. Hãy chọn Nâng cấp để kiểm tra điều kiện.");
                sendSnapshot(player, state, memberState(player, state));
                return;
            }
            if (now < state.upgradeReadyAt) {
                notifyUpgradeState(player, state, now);
                sendSnapshot(player, state, memberState(player, state));
                return;
            }
            long upgradeDuration = state.upgradeStartedAt <= 0L
                    ? 0L : Math.max(0L, now - state.upgradeStartedAt);
            state.growth = Math.max(0L, state.growth - growthRequired(state.level));
            state.water = Math.max(0, state.water - waterRequired(state.level));
            state.fertilizer = Math.max(0, state.fertilizer - fertilizerRequired(state.level));
            state.level++;
            state.upgradeStartedAt = 0L;
            state.upgradeReadyAt = 0L;
            state.version++;
            persist(state);
            ClanEconomyMetricsService.gI().record(
                    ClanEconomyMetricsService.Signal.TREE_LEVEL_UP, upgradeDuration, clan.id);
            notify(player, "Cây bang đã hoàn tất nâng lên cấp " + state.level + "!");
            sendClanNotice(clan, player.name + " đã hoàn tất nâng Cây bang lên cấp " + state.level + "!");
            broadcastSnapshot(clan, state);
        }
    }

    private static void notifyUpgradeState(Player player, ClanTreeState state, long now) {
        if (now >= state.upgradeReadyAt) {
            notify(player, "Cây bang đã nâng xong. Hãy chọn Hoàn tất nâng cấp tại cây.");
        } else {
            notify(player, "Cây bang đang nâng cấp, còn " + formatDuration(state.upgradeReadyAt - now) + ".");
        }
    }

    private void settleProduction(ClanTreeState state) {
        long now = System.currentTimeMillis();
        if (state.lastProductionAt <= 0L) {
            state.lastProductionAt = now;
            return;
        }
        long elapsed = Math.min(config.productionCapMs(), Math.max(0L, now - state.lastProductionAt));
        long hours = elapsed / (60L * 60L * 1000L);
        if (hours <= 0L) {
            return;
        }
        refreshDailyVitality(state);
        int multiplierPercent = state.vitalityAmount >= waterRequired(state.level) ? 100
                : state.vitalityAmount * 100 >= waterRequired(state.level) ? 75 : 50;
        int yieldBasisPoints = ClanProgressionService.gI().treeYieldBasisPoints(state.clanId);
        ClanTreeConfig.ProductionYield production = config.productionYield(
                state.level, hours, multiplierPercent, yieldBasisPoints);
        state.pendingGold = safeAdd(state.pendingGold, production.gold());
        state.pendingCapsule = safeAdd(state.pendingCapsule, production.capsule());
        state.lastProductionAt += hours * 60L * 60L * 1000L;
        state.version++;
    }

    private ClanTreeState getState(int clanId) {
        return cache.computeIfAbsent(clanId, this::load);
    }

    private ClanTreeState load(int clanId) {
        long now = System.currentTimeMillis();
        try (Connection connection = LocalManager.getConnection()) {
            ensureSchema(connection);
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT IGNORE INTO clan_tree (clan_id, last_production_at) VALUES (?, ?)")) {
                insert.setInt(1, clanId);
                insert.setLong(2, now);
                insert.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM clan_tree WHERE clan_id=?")) {
                ps.setInt(1, clanId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new ClanTreeState(config.maxLevel(), clanId, rs.getInt("level"), rs.getLong("growth"),
                                rs.getInt("water"), rs.getInt("fertilizer"), rs.getInt("vitality_day"),
                                rs.getInt("vitality_amount"), rs.getLong("pending_gold"),
                                rs.getInt("pending_capsule"), rs.getLong("last_production_at"),
                                rs.getLong("help_expires_at"), rs.getLong("last_help_at"),
                                rs.getLong("upgrade_started_at"), rs.getLong("upgrade_ready_at"),
                                rs.getLong("version"));
                    }
                }
            }
        } catch (Exception e) {
            Logger.logException(ClanTreeService.class, e, "Không tải được Cây bang");
        }
        return new ClanTreeState(config.maxLevel(), clanId, 1, 0L, 0, 0, dayKey(), 0, 0L, 0,
                now, 0L, 0L, 0L, 0L, 0L);
    }

    private MemberState memberState(Player player, ClanTreeState state) {
        int today = dayKey();
        MemberState member = null;
        boolean needsPersist = false;
        try (Connection connection = LocalManager.getConnection()) {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT day_key, water_count, fertilizer_count, growth_contribution, last_action_at "
                    + "FROM clan_tree_member WHERE clan_id=? AND player_id=?")) {
                ps.setInt(1, state.clanId);
                ps.setLong(2, player.id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        member = new MemberState(state.clanId, player.id, rs.getInt(1), rs.getInt(2),
                                rs.getInt(3), rs.getLong(4), rs.getLong(5));
                        if (member.dayKey != today) {
                            member.dayKey = today;
                            member.waterCount = 0;
                            member.fertilizerCount = 0;
                            member.growthContribution = 0L;
                            // Do not borrow another connection while the result set's connection is still leased.
                            // This previously deadlocked a one-connection pool at the Vietnam day rollover.
                            needsPersist = true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Logger.logException(ClanTreeService.class, e, "Không tải được tiến độ chăm cây cá nhân");
            return new MemberState(state.clanId, player.id, today, 0, 0, 0L, 0L);
        }
        if (member == null) {
            member = new MemberState(state.clanId, player.id, today, 0, 0, 0L, 0L);
            needsPersist = true;
        }
        if (needsPersist) {
            persistMember(state, member);
        }
        return member;
    }

    private boolean persist(ClanTreeState state) {
        try (Connection connection = LocalManager.getConnection()) {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO clan_tree (clan_id, level, growth, water, fertilizer, vitality_day, vitality_amount, "
                    + "pending_gold, pending_capsule, last_production_at, help_expires_at, last_help_at, "
                    + "upgrade_started_at, upgrade_ready_at, version) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
                    + "level=VALUES(level), growth=VALUES(growth), water=VALUES(water), fertilizer=VALUES(fertilizer), "
                    + "vitality_day=VALUES(vitality_day), vitality_amount=VALUES(vitality_amount), "
                    + "pending_gold=VALUES(pending_gold), pending_capsule=VALUES(pending_capsule), "
                    + "last_production_at=VALUES(last_production_at), help_expires_at=VALUES(help_expires_at), "
                    + "last_help_at=VALUES(last_help_at), upgrade_started_at=VALUES(upgrade_started_at), "
                    + "upgrade_ready_at=VALUES(upgrade_ready_at), version=VALUES(version)")) {
                ps.setInt(1, state.clanId); ps.setInt(2, state.level); ps.setLong(3, state.growth);
                ps.setInt(4, state.water); ps.setInt(5, state.fertilizer); ps.setInt(6, state.vitalityDay);
                ps.setInt(7, state.vitalityAmount); ps.setLong(8, state.pendingGold); ps.setInt(9, state.pendingCapsule);
                ps.setLong(10, state.lastProductionAt); ps.setLong(11, state.helpExpiresAt); ps.setLong(12, state.lastHelpAt);
                ps.setLong(13, state.upgradeStartedAt); ps.setLong(14, state.upgradeReadyAt);
                ps.setLong(15, state.version); ps.executeUpdate();
            }
            return true;
        } catch (Exception e) {
            Logger.logException(ClanTreeService.class, e, "Không lưu được Cây bang");
            return false;
        }
    }

    private ClanTreeState copyState(ClanTreeState state) {
        return new ClanTreeState(config.maxLevel(), state.clanId, state.level, state.growth,
                state.water, state.fertilizer, state.vitalityDay, state.vitalityAmount,
                state.pendingGold, state.pendingCapsule, state.lastProductionAt,
                state.helpExpiresAt, state.lastHelpAt, state.upgradeStartedAt,
                state.upgradeReadyAt, state.version);
    }

    private static void restoreState(ClanTreeState target, ClanTreeState source) {
        target.level = source.level;
        target.growth = source.growth;
        target.water = source.water;
        target.fertilizer = source.fertilizer;
        target.vitalityDay = source.vitalityDay;
        target.vitalityAmount = source.vitalityAmount;
        target.pendingGold = source.pendingGold;
        target.pendingCapsule = source.pendingCapsule;
        target.lastProductionAt = source.lastProductionAt;
        target.helpExpiresAt = source.helpExpiresAt;
        target.lastHelpAt = source.lastHelpAt;
        target.upgradeStartedAt = source.upgradeStartedAt;
        target.upgradeReadyAt = source.upgradeReadyAt;
        target.version = source.version;
    }

    private ClanFunds lockClanFunds(Connection connection, int clanId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT clan_gold,clan_point,treasury_version FROM clan WHERE id=? FOR UPDATE")) {
            ps.setInt(1, clanId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new ClanFunds(Math.max(0L, rs.getLong(1)),
                        Math.max(0, rs.getInt(2)), Math.max(0L, rs.getLong(3))) : null;
            }
        }
    }

    private long lockTreeVersion(Connection connection, int clanId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT version FROM clan_tree WHERE clan_id=? FOR UPDATE")) {
            ps.setInt(1, clanId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1L;
            }
        }
    }

    private boolean lockPlayerForHarvest(Connection connection, long playerId, int clanId,
            long expectedSaveVersion) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT clan_id,save_version FROM player WHERE id=? FOR UPDATE")) {
            ps.setLong(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) == clanId && rs.getLong(2) == expectedSaveVersion;
            }
        }
    }

    private void updateTreeAfterHarvest(Connection connection, ClanTreeState state,
            long expectedVersion, long nextVersion) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE clan_tree SET level=?,growth=?,water=?,fertilizer=?,vitality_day=?,vitality_amount=?,"
                + "pending_gold=0,pending_capsule=0,last_production_at=?,help_expires_at=?,last_help_at=?,"
                + "upgrade_started_at=?,upgrade_ready_at=?,version=? WHERE clan_id=? AND version=?")) {
            ps.setInt(1, state.level);
            ps.setLong(2, state.growth);
            ps.setInt(3, state.water);
            ps.setInt(4, state.fertilizer);
            ps.setInt(5, state.vitalityDay);
            ps.setInt(6, state.vitalityAmount);
            ps.setLong(7, state.lastProductionAt);
            ps.setLong(8, state.helpExpiresAt);
            ps.setLong(9, state.lastHelpAt);
            ps.setLong(10, state.upgradeStartedAt);
            ps.setLong(11, state.upgradeReadyAt);
            ps.setLong(12, nextVersion);
            ps.setInt(13, state.clanId);
            ps.setLong(14, expectedVersion);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Cây bang đã thay đổi trong lúc thu hoạch");
            }
        }
    }

    private void updateClanAfterHarvest(Connection connection, int clanId, long gold,
            int capsule, long treasuryVersion) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE clan SET clan_gold=?,clan_point=?,treasury_version=? WHERE id=?")) {
            ps.setLong(1, gold);
            ps.setInt(2, capsule);
            ps.setLong(3, treasuryVersion);
            ps.setInt(4, clanId);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Không thể cập nhật quỹ bang khi thu hoạch");
            }
        }
    }

    private void updatePlayerBagAfterHarvest(Connection connection, long playerId, int clanId,
            long expectedSaveVersion, InventoryPersistenceSnapshot inventory) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE player SET items_bag=?,save_version=save_version+1 "
                + "WHERE id=? AND clan_id=? AND save_version=?")) {
            ps.setString(1, inventory.getItemsBagJson());
            ps.setLong(2, playerId);
            ps.setInt(3, clanId);
            ps.setLong(4, expectedSaveVersion);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Không thể lưu Hộp quà thu hoạch vào hành trang");
            }
        }
    }

    private static boolean beginExclusiveSave(PlayerPersistenceState persistence) {
        if (persistence.tryBeginSave()) {
            return true;
        }
        return persistence.awaitSaveCompletion(5_000L) && persistence.tryBeginSave();
    }

    private void persistMember(ClanTreeState state, MemberState member) {
        try (Connection connection = LocalManager.getConnection()) {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO clan_tree_member (clan_id, player_id, day_key, water_count, fertilizer_count, growth_contribution, last_action_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE day_key=VALUES(day_key), "
                    + "water_count=VALUES(water_count), fertilizer_count=VALUES(fertilizer_count), "
                    + "growth_contribution=VALUES(growth_contribution), last_action_at=VALUES(last_action_at)")) {
                ps.setInt(1, member.clanId); ps.setLong(2, member.playerId); ps.setInt(3, member.dayKey);
                ps.setInt(4, member.waterCount); ps.setInt(5, member.fertilizerCount);
                ps.setLong(6, member.growthContribution); ps.setLong(7, member.lastActionAt); ps.executeUpdate();
            }
        } catch (Exception e) {
            Logger.logException(ClanTreeService.class, e, "Không lưu được tiến độ chăm cây cá nhân");
        }
    }

    private void broadcastSnapshot(Clan clan, ClanTreeState state) {
        for (Player member : new ArrayList<>(clan.membersInGame)) {
            if (member != null && member.clan != null && member.clan.id == clan.id) {
                sendSnapshot(member, state, memberState(member, state));
            }
        }
    }

    private String missingUpgradeRequirements(ClanTreeState state, Clan clan) {
        StringBuilder missing = new StringBuilder();
        appendMissing(missing, "Bình nước", Math.max(0L, (long) waterRequired(state.level) - state.water));
        appendMissing(missing, "Phân bón", Math.max(0L, (long) fertilizerRequired(state.level) - state.fertilizer));
        appendMissing(missing, "Điểm sinh trưởng", Math.max(0L, growthRequired(state.level) - state.growth));
        int requiredClanLevel = minimumClanLevel(state.level);
        if (clan != null && clan.level < requiredClanLevel) {
            if (missing.length() > 0) {
                missing.append('\n');
            }
            missing.append("- Cấp bang: thiếu ").append(requiredClanLevel - clan.level)
                    .append(" cấp (cần cấp ").append(requiredClanLevel).append(')');
        }
        return missing.toString();
    }

    private static void appendMissing(StringBuilder missing, String label, long amount) {
        if (amount <= 0L) {
            return;
        }
        if (missing.length() > 0) {
            missing.append('\n');
        }
        missing.append("- ").append(label).append(": ").append(amount);
    }

    private void sendSnapshot(Player player, ClanTreeState state, MemberState member) {
        Message message = null;
        try {
            message = new Message(127);
            message.writer().writeByte(REQUEST_VIEW);
            message.writer().writeInt(state.clanId);
            message.writer().writeByte(state.level);
            message.writer().writeLong(state.growth);
            message.writer().writeLong(growthRequired(state.level));
            message.writer().writeInt(state.water);
            message.writer().writeInt(waterRequired(state.level));
            message.writer().writeInt(state.fertilizer);
            message.writer().writeInt(fertilizerRequired(state.level));
            message.writer().writeByte(member.waterCount);
            message.writer().writeByte(config.dailyWaterLimit());
            message.writer().writeByte(member.fertilizerCount);
            message.writer().writeByte(config.dailyFertilizerLimit());
            message.writer().writeLong(state.pendingGold);
            message.writer().writeInt(state.pendingCapsule);
            message.writer().writeLong(state.helpExpiresAt);
            message.writer().writeLong(state.upgradeStartedAt);
            message.writer().writeLong(state.upgradeReadyAt);
            message.writer().writeLong(state.version);
            // Detail v2 keeps Clan EXP and the cosmetic state tied to this tree snapshot.
            // Older clients safely ignore these trailing fields.
            message.writer().writeByte(2);
            message.writer().writeLong(player.clan.clanExp);
            message.writer().writeLong(ClanProgressionService.gI().expRequired(player.clan.level));
            message.writer().writeLong(player.clan.progressionVersion);
            ClanValueService.Snapshot clanValue = ClanValueService.gI().snapshot(player.clan);
            ClanAppearanceService.AppearanceView appearance = ClanAppearanceService.gI()
                    .snapshot(player.clan, state.level, clanValue);
            ClanAppearanceProtocol.writeDetails(message.writer(), state.clanId,
                    appearance.enabled(), appearance.appearance());
            player.sendMessage(message);
        } catch (Exception e) {
            Logger.logException(ClanTreeService.class, e, "Không gửi được snapshot Cây bang");
        } finally {
            if (message != null) {
                message.cleanup();
            }
        }
    }

    private long growthRequired(int level) {
        return config.growthRequired(level);
    }

    private int waterRequired(int level) {
        return config.waterRequired(level);
    }

    private int fertilizerRequired(int level) {
        return config.fertilizerRequired(level);
    }

    private int minimumClanLevel(int level) {
        return config.minimumClanLevel(level);
    }

    private int growthFromWater(int level) {
        return config.growthFromWater(level);
    }

    private int growthFromFertilizer(int level) {
        return config.growthFromFertilizer(level);
    }

    private long upgradeDurationMs(int targetLevel) {
        return config.upgradeDurationMs(targetLevel);
    }

    private int upgradeDaysForTargetLevel(int targetLevel) {
        return config.upgradeDaysForTargetLevel(targetLevel);
    }

    private static String formatDuration(long millis) {
        long minutes = Math.max(1L, (millis + 59_999L) / 60_000L);
        long days = minutes / (24L * 60L);
        long hours = (minutes % (24L * 60L)) / 60L;
        long remainMinutes = minutes % 60L;
        if (days > 0L) {
            return days + " ngày " + hours + " giờ";
        }
        if (hours > 0L) {
            return hours + " giờ " + remainMinutes + " phút";
        }
        return remainMinutes + " phút";
    }

    private static int dayKey() {
        return (int) LocalDate.now(nro.models.utils.TimeUtil.VIETNAM_ZONE).toEpochDay();
    }

    private static long safeAdd(long left, long right) {
        if (left < 0L || right < 0L) {
            return Math.max(0L, left);
        }
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static int safeAdd(int left, int right) {
        if (left < 0 || right < 0) {
            return Math.max(0, left);
        }
        return left > Integer.MAX_VALUE - right ? Integer.MAX_VALUE : left + right;
    }

    private static void refreshDailyVitality(ClanTreeState state) {
        int today = dayKey();
        if (state.vitalityDay != today) {
            state.vitalityDay = today;
            state.vitalityAmount = 0;
        }
    }

    private boolean requireClan(Player player) {
        if (player == null || player.clan == null || player.clan.getClanMember((int) player.id) == null) {
            if (player != null) {
                notify(player, "Bạn cần thuộc một bang hội để dùng Cây bang.");
            }
            return false;
        }
        return true;
    }

    private void sendClanNotice(Clan clan, String text) {
        if (clan == null) return;
        ClanMember actor = clan.getLeader();
        ClanMessage message = new ClanMessage(clan);
        message.type = 0;
        message.playerId = actor.id;
        message.playerName = "Cây bang";
        message.role = Clan.DEPUTY;
        message.text = text;
        message.color = ClanMessage.GREEN;
        clan.addClanMessage(message);
        clan.sendMessageClan(message);
    }

    /** Type 4 is rendered by the updated clients as a clan-chat action button. */
    private void sendClanHelpRequest(Clan clan, Player requester) {
        if (clan == null || requester == null) return;
        ClanMember member = clan.getClanMember((int) requester.id);
        ClanMessage message = new ClanMessage(clan);
        message.type = 4;
        message.playerId = (int) requester.id;
        message.playerName = requester.name;
        message.role = member == null ? Clan.MEMBER : member.role;
        message.text = requester.name + " đang kêu gọi tưới Cây bang.";
        message.color = ClanMessage.GREEN;
        clan.addClanMessage(message);
        clan.sendMessageClan(message);
    }

    private static void notify(Player player, String text) {
        Service.gI().sendThongBao(player, text);
    }

    private static final class ClanTreeState {
        final int clanId;
        int level;
        long growth;
        int water;
        int fertilizer;
        int vitalityDay;
        int vitalityAmount;
        long pendingGold;
        int pendingCapsule;
        long lastProductionAt;
        long helpExpiresAt;
        long lastHelpAt;
        long upgradeStartedAt;
        long upgradeReadyAt;
        long version;

        ClanTreeState(int maxLevel, int clanId, int level, long growth, int water, int fertilizer, int vitalityDay,
                int vitalityAmount, long pendingGold, int pendingCapsule, long lastProductionAt,
                long helpExpiresAt, long lastHelpAt, long upgradeStartedAt, long upgradeReadyAt, long version) {
            this.clanId = clanId;
            this.level = Math.max(1, Math.min(maxLevel, level));
            this.growth = Math.max(0L, growth);
            this.water = Math.max(0, water);
            this.fertilizer = Math.max(0, fertilizer);
            this.vitalityDay = vitalityDay;
            this.vitalityAmount = Math.max(0, vitalityAmount);
            this.pendingGold = Math.max(0L, pendingGold);
            this.pendingCapsule = Math.max(0, pendingCapsule);
            this.lastProductionAt = lastProductionAt;
            this.helpExpiresAt = helpExpiresAt;
            this.lastHelpAt = lastHelpAt;
            this.upgradeStartedAt = Math.max(0L, upgradeStartedAt);
            this.upgradeReadyAt = Math.max(0L, upgradeReadyAt);
            if (this.level >= maxLevel) {
                this.upgradeStartedAt = 0L;
                this.upgradeReadyAt = 0L;
            }
            this.version = Math.max(0L, version);
        }
    }

    private static final class MemberState {
        final int clanId;
        final long playerId;
        int dayKey;
        int waterCount;
        int fertilizerCount;
        long growthContribution;
        long lastActionAt;

        MemberState(int clanId, long playerId, int dayKey, int waterCount, int fertilizerCount,
                long growthContribution, long lastActionAt) {
            this.clanId = clanId;
            this.playerId = playerId;
            this.dayKey = dayKey;
            this.waterCount = waterCount;
            this.fertilizerCount = fertilizerCount;
            this.growthContribution = growthContribution;
            this.lastActionAt = lastActionAt;
        }
    }

    private record ClanFunds(long gold, int capsule, long treasuryVersion) {
    }

    private static final class HarvestResult {
        final boolean success;
        final String message;
        final boolean reloadTree;
        final boolean quarantinePlayer;
        final long gold;
        final int capsule;
        final long clanGold;
        final int clanCapsule;
        final long treasuryVersion;
        final long treeVersion;

        private HarvestResult(boolean success, String message, boolean reloadTree, boolean quarantinePlayer,
                long gold, int capsule, long clanGold, int clanCapsule, long treasuryVersion, long treeVersion) {
            this.success = success;
            this.message = message;
            this.reloadTree = reloadTree;
            this.quarantinePlayer = quarantinePlayer;
            this.gold = gold;
            this.capsule = capsule;
            this.clanGold = clanGold;
            this.clanCapsule = clanCapsule;
            this.treasuryVersion = treasuryVersion;
            this.treeVersion = treeVersion;
        }

        static HarvestResult ok(long gold, int capsule, long clanGold, int clanCapsule,
                long treasuryVersion, long treeVersion) {
            return new HarvestResult(true, "", false, false, gold, capsule, clanGold, clanCapsule,
                    treasuryVersion, treeVersion);
        }

        static HarvestResult fail(String message, boolean reloadTree) {
            return fail(message, reloadTree, false);
        }

        static HarvestResult fail(String message, boolean reloadTree, boolean quarantinePlayer) {
            return new HarvestResult(false, message, reloadTree, quarantinePlayer,
                    0L, 0, 0L, 0, 0L, 0L);
        }
    }
}
