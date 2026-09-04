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
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import nro.models.data.LocalManager;
import nro.models.item.Item;
import nro.models.network.Message;
import nro.models.player.Player;
import nro.models.services.InventoryService;
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

    public static final int WATER_ITEM_ID = 456;
    public static final int FERTILIZER_ITEM_ID = 1094;
    public static final int DAILY_WATER_LIMIT = 5;
    public static final int DAILY_FERTILIZER_LIMIT = 2;
    public static final long ACTION_COOLDOWN_MS = 500L;
    public static final long HELP_COOLDOWN_MS = 10L * 60L * 1000L;
    public static final long HELP_DURATION_MS = 2L * 60L * 60L * 1000L;
    public static final long PRODUCTION_CAP_MS = 24L * 60L * 60L * 1000L;

    private static final int MAX_TREE_LEVEL = 20;
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;
    private static final int[] DEFAULT_UPGRADE_DAYS = {
        1, 1, 1, 2, 2, 2, 3, 3, 3, 4, 4, 5, 5, 6, 7, 8, 9, 11, 13
    };
    private static final long HOURLY_GOLD_PER_LEVEL = 5_000L;
    private static final int HOURLY_CAPSULE_PER_5_LEVELS = 1;
    private static final ClanTreeService INSTANCE = new ClanTreeService();

    private final Map<Integer, ClanTreeState> cache = new ConcurrentHashMap<>();
    private final int[] upgradeDays = loadUpgradeDays();
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
            for (int level = 1; level <= MAX_TREE_LEVEL; level++) {
                statement.setString(1, String.format("cay_lv_%02d", level));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    public boolean isTreeAction(byte action) {
        return action >= REQUEST_VIEW && action <= REQUEST_START_UPGRADE;
    }

    public void handleRequest(Player player, byte action, Message message) throws IOException {
        switch (action) {
            case REQUEST_VIEW:
                sendSnapshot(player);
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
        if (clan != null && clan.id >= 0) {
            getState(clan.id);
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
                if (now - member.lastActionAt < ACTION_COOLDOWN_MS) {
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
                if (member.waterCount >= DAILY_WATER_LIMIT) {
                    notify(player, "Bạn đã tưới đủ " + DAILY_WATER_LIMIT + " lượt hôm nay.");
                    return;
                }
                Item water = InventoryService.gI().findItemBag(player, WATER_ITEM_ID);
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
                InventoryService.gI().sendItemBags(player);
                int exp = ClanProgressionService.gI().expWater();
                boolean expAdded = ClanProgressionService.gI().addExp(player.clan, exp);
                if (expAdded) {
                    ClanProgressionService.gI().sendSnapshot(player);
                }
                notify(player, "Đã tưới Cây bang (" + member.waterCount + "/" + DAILY_WATER_LIMIT + ")."
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
                if (now - member.lastActionAt < ACTION_COOLDOWN_MS) {
                    notify(player, "Thao tác quá nhanh, vui lòng chờ một lát.");
                    return;
                }
                if (member.fertilizerCount >= DAILY_FERTILIZER_LIMIT) {
                    notify(player, "Bạn đã bón đủ " + DAILY_FERTILIZER_LIMIT + " lượt hôm nay.");
                    return;
                }
                Item fertilizer = InventoryService.gI().findItemBag(player, FERTILIZER_ITEM_ID);
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
                InventoryService.gI().sendItemBags(player);
                int exp = ClanProgressionService.gI().expFertilize();
                boolean expAdded = ClanProgressionService.gI().addExp(player.clan, exp);
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
        ClanTreeState state = getState(player.clan.id);
        synchronized (state) {
            settleProduction(state);
            if (state.pendingGold <= 0 && state.pendingCapsule <= 0) {
                persist(state);
                notify(player, "Cây bang chưa có sản lượng để thu hoạch.");
                sendSnapshot(player, state, memberState(player, state));
                return;
            }
            Clan clan = player.clan;
            clan.clanGold += state.pendingGold;
            clan.capsuleClan += state.pendingCapsule;
            long gold = state.pendingGold;
            int capsule = state.pendingCapsule;
            state.pendingGold = 0L;
            state.pendingCapsule = 0;
            state.version++;
            persist(state);
            clan.update();
            notify(player, "Đã thu hoạch cho bang: " + gold + " vàng bang"
                    + (capsule > 0 ? " và " + capsule + " Capsule Bang." : "."));
            sendClanNotice(clan, player.name + " đã thu hoạch Cây bang: +" + gold + " vàng bang"
                    + (capsule > 0 ? ", +" + capsule + " Capsule Bang." : "."));
            broadcastSnapshot(clan, state);
        }
    }

    private void askForHelp(Player player) {
        if (!requireClan(player)) {
            return;
        }
        ClanTreeState state = getState(player.clan.id);
        synchronized (state) {
            long now = System.currentTimeMillis();
            if (now - state.lastHelpAt < HELP_COOLDOWN_MS) {
                notify(player, "Hãy chờ trước khi kêu gọi chăm cây lần nữa.");
                return;
            }
            state.lastHelpAt = now;
            state.helpExpiresAt = now + HELP_DURATION_MS;
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
            if (state.level >= MAX_TREE_LEVEL) {
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
            if (state.level >= MAX_TREE_LEVEL) {
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
            state.growth = Math.max(0L, state.growth - growthRequired(state.level));
            state.water = Math.max(0, state.water - waterRequired(state.level));
            state.fertilizer = Math.max(0, state.fertilizer - fertilizerRequired(state.level));
            state.level++;
            state.upgradeStartedAt = 0L;
            state.upgradeReadyAt = 0L;
            state.version++;
            persist(state);
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
        long elapsed = Math.min(PRODUCTION_CAP_MS, Math.max(0L, now - state.lastProductionAt));
        long hours = elapsed / (60L * 60L * 1000L);
        if (hours <= 0L) {
            return;
        }
        refreshDailyVitality(state);
        int multiplierPercent = state.vitalityAmount >= waterRequired(state.level) ? 100
                : state.vitalityAmount * 100 >= waterRequired(state.level) ? 75 : 50;
        long gold = (HOURLY_GOLD_PER_LEVEL * state.level * hours * multiplierPercent) / 100L;
        long capsule = hours * (state.level / 5) * HOURLY_CAPSULE_PER_5_LEVELS;
        int yieldBasisPoints = ClanProgressionService.gI().treeYieldBasisPoints(state.clanId);
        state.pendingGold += gold * (10_000L + yieldBasisPoints) / 10_000L;
        state.pendingCapsule += (int) Math.min(Integer.MAX_VALUE - state.pendingCapsule,
                capsule * (10_000L + yieldBasisPoints) / 10_000L);
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
                        return new ClanTreeState(clanId, rs.getInt("level"), rs.getLong("growth"),
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
        return new ClanTreeState(clanId, 1, 0L, 0, 0, dayKey(), 0, 0L, 0, now, 0L, 0L, 0L, 0L, 0L);
    }

    private MemberState memberState(Player player, ClanTreeState state) {
        int today = dayKey();
        try (Connection connection = LocalManager.getConnection()) {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT day_key, water_count, fertilizer_count, growth_contribution, last_action_at "
                    + "FROM clan_tree_member WHERE clan_id=? AND player_id=?")) {
                ps.setInt(1, state.clanId);
                ps.setLong(2, player.id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        MemberState member = new MemberState(state.clanId, player.id, rs.getInt(1), rs.getInt(2),
                                rs.getInt(3), rs.getLong(4), rs.getLong(5));
                        if (member.dayKey != today) {
                            member.dayKey = today;
                            member.waterCount = 0;
                            member.fertilizerCount = 0;
                            member.growthContribution = 0L;
                            persistMember(state, member);
                        }
                        return member;
                    }
                }
            }
        } catch (Exception e) {
            Logger.logException(ClanTreeService.class, e, "Không tải được tiến độ chăm cây cá nhân");
        }
        MemberState member = new MemberState(state.clanId, player.id, today, 0, 0, 0L, 0L);
        persistMember(state, member);
        return member;
    }

    private void persist(ClanTreeState state) {
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
        } catch (Exception e) {
            Logger.logException(ClanTreeService.class, e, "Không lưu được Cây bang");
        }
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

    private static String missingUpgradeRequirements(ClanTreeState state, Clan clan) {
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
            message.writer().writeByte(DAILY_WATER_LIMIT);
            message.writer().writeByte(member.fertilizerCount);
            message.writer().writeByte(DAILY_FERTILIZER_LIMIT);
            message.writer().writeLong(state.pendingGold);
            message.writer().writeInt(state.pendingCapsule);
            message.writer().writeLong(state.helpExpiresAt);
            message.writer().writeLong(state.upgradeStartedAt);
            message.writer().writeLong(state.upgradeReadyAt);
            message.writer().writeLong(state.version);
            player.sendMessage(message);
        } catch (Exception e) {
            Logger.logException(ClanTreeService.class, e, "Không gửi được snapshot Cây bang");
        } finally {
            if (message != null) {
                message.cleanup();
            }
        }
    }

    private static long growthRequired(int level) {
        return Math.round(100D * Math.pow(Math.max(1, level), 1.40D));
    }

    private static int waterRequired(int level) {
        return 20 + 10 * Math.max(1, level);
    }

    private static int fertilizerRequired(int level) {
        return Math.max(0, (level + 1) / 3);
    }

    private static int minimumClanLevel(int level) {
        return Math.max(1, (level + 1) / 2);
    }

    private static int growthFromWater(int level) {
        return 10 + Math.min(10, Math.max(0, level - 1));
    }

    private static int growthFromFertilizer(int level) {
        return growthFromWater(level) * 5;
    }

    private long upgradeDurationMs(int targetLevel) {
        return upgradeDaysForTargetLevel(targetLevel) * DAY_MS;
    }

    private int upgradeDaysForTargetLevel(int targetLevel) {
        int index = Math.max(0, Math.min(upgradeDays.length - 1, targetLevel - 2));
        return upgradeDays[index];
    }

    private static int[] loadUpgradeDays() {
        int[] result = DEFAULT_UPGRADE_DAYS.clone();
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream("data/clan_tree.properties")) {
            properties.load(input);
            String raw = properties.getProperty("upgrade_days_to_levels_2_20", "").trim();
            if (raw.isEmpty()) {
                return result;
            }
            String[] parts = raw.split(",");
            if (parts.length != MAX_TREE_LEVEL - 1) {
                Logger.warning("clan_tree.properties: upgrade_days_to_levels_2_20 phải có đúng 19 giá trị.\n");
                return result;
            }
            for (int i = 0; i < parts.length; i++) {
                result[i] = Math.max(0, Integer.parseInt(parts[i].trim()));
            }
        } catch (Exception e) {
            Logger.warning("Không đọc được data/clan_tree.properties, dùng lịch nâng cây mặc định 90 ngày.\n");
        }
        return result;
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
        message.role = Clan.MEMBER;
        message.text = text;
        message.color = ClanMessage.RED;
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
        message.color = ClanMessage.RED;
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

        ClanTreeState(int clanId, int level, long growth, int water, int fertilizer, int vitalityDay,
                int vitalityAmount, long pendingGold, int pendingCapsule, long lastProductionAt,
                long helpExpiresAt, long lastHelpAt, long upgradeStartedAt, long upgradeReadyAt, long version) {
            this.clanId = clanId;
            this.level = Math.max(1, Math.min(MAX_TREE_LEVEL, level));
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
            if (this.level >= MAX_TREE_LEVEL) {
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
}
