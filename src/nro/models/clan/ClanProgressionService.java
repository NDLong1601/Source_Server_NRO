package nro.models.clan;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import nro.models.data.LocalManager;
import nro.models.network.Message;
import nro.models.consts.ConstPlayer;
import nro.models.player.Player;
import nro.models.services.Service;
import nro.models.utils.Logger;

/**
 * Server-authoritative clan EXP, leveling and potential allocation.
 * The client receives a snapshot only; it never supplies costs, ranks or buffs.
 */
public final class ClanProgressionService {

    public static final byte REQUEST_VIEW = 112;
    public static final byte REQUEST_UPGRADE = 113;
    public static final byte REQUEST_ALLOCATE = 114;
    public static final byte RESPONSE_SNAPSHOT = 115;
    public static final byte RESPONSE_BUFF_SNAPSHOT = 124;
    public static final byte REQUEST_BUFF_SNAPSHOT = 125;

    private static final ClanProgressionService INSTANCE = new ClanProgressionService();
    private final Map<Integer, ProgressState> states = new ConcurrentHashMap<>();
    private final Config config = new Config();
    private volatile boolean schemaReady;

    public enum Branch {
        ATTACK("ATTACK", "Sức đánh", "attack", 50),
        HP("HP", "HP", "hp", 77),
        KI("KI", "KI", "ki", 103),
        LUCK("LUCK", "May mắn", "luck", 236),
        POWER("POWER", "Tiềm năng, sức mạnh", "power", 101),
        MOB_GOLD("MOB_GOLD", "Vàng từ quái", "mob_gold", 100);

        final String key;
        final String display;
        final String configPrefix;
        final int optionId;

        Branch(String key, String display, String configPrefix, int optionId) {
            this.key = key;
            this.display = display;
            this.configPrefix = configPrefix;
            this.optionId = optionId;
        }

        static Branch fromWire(int index) {
            Branch[] values = values();
            return index >= 0 && index < values.length ? values[index] : null;
        }
    }

    private ClanProgressionService() {
    }

    public static ClanProgressionService gI() {
        return INSTANCE;
    }

    public synchronized void ensureSchema(Connection connection) throws SQLException {
        if (schemaReady) {
            return;
        }
        ensureClanColumn(connection, "clan_exp", "BIGINT NOT NULL DEFAULT 0");
        ensureClanColumn(connection, "potential_total", "INT NOT NULL DEFAULT 0");
        ensureClanColumn(connection, "potential_unspent", "INT NOT NULL DEFAULT 0");
        ensureClanColumn(connection, "progression_version", "BIGINT NOT NULL DEFAULT 0");
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS clan_potential ("
                    + "clan_id INT NOT NULL, branch_key VARCHAR(32) NOT NULL, rank_value INT NOT NULL DEFAULT 0, "
                    + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY (clan_id, branch_key), KEY idx_clan_potential_clan (clan_id)) "
                    + "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS clan_potential_audit ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT, clan_id INT NOT NULL, actor_id BIGINT NOT NULL, "
                    + "branch_key VARCHAR(32) NOT NULL, rank_before INT NOT NULL, rank_after INT NOT NULL, "
                    + "unspent_before INT NOT NULL, unspent_after INT NOT NULL, point_cost INT NOT NULL, "
                    + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (id), "
                    + "KEY idx_clan_potential_audit_clan (clan_id, id)) "
                    + "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
        }
        migrateBranch(connection, "PVE_REDUCTION", Branch.LUCK.key);
        migrateBranch(connection, "TREE_YIELD", Branch.POWER.key);
        schemaReady = true;
    }

    private void migrateBranch(Connection connection, String oldKey, String newKey) throws SQLException {
        try (PreparedStatement merge = connection.prepareStatement(
                "UPDATE clan_potential current_value "
                + "JOIN clan_potential legacy_value ON legacy_value.clan_id=current_value.clan_id "
                + "SET current_value.rank_value=GREATEST(current_value.rank_value,legacy_value.rank_value) "
                + "WHERE current_value.branch_key=? AND legacy_value.branch_key=?")) {
            merge.setString(1, newKey);
            merge.setString(2, oldKey);
            merge.executeUpdate();
        }
        try (PreparedStatement copy = connection.prepareStatement(
                "INSERT INTO clan_potential (clan_id,branch_key,rank_value) "
                + "SELECT legacy_value.clan_id,?,legacy_value.rank_value FROM clan_potential legacy_value "
                + "WHERE legacy_value.branch_key=? AND NOT EXISTS (SELECT 1 FROM clan_potential current_value "
                + "WHERE current_value.clan_id=legacy_value.clan_id AND current_value.branch_key=?)")) {
            copy.setString(1, newKey);
            copy.setString(2, oldKey);
            copy.setString(3, newKey);
            copy.executeUpdate();
        }
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM clan_potential WHERE branch_key=?")) {
            delete.setString(1, oldKey);
            delete.executeUpdate();
        }
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

    /** Called after a newly created clan has been inserted. */
    public void initializeClan(Clan clan) {
        if (clan == null || clan.id < 0) {
            return;
        }
        ProgressState state = new ProgressState(clan.id);
        states.putIfAbsent(clan.id, state);
        clan.clanExp = 0L;
        clan.potentialTotal = 0;
        clan.potentialUnspent = 0;
        try (Connection connection = LocalManager.getConnection()) {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE clan SET clan_exp=0, potential_total=0, potential_unspent=0, progression_version=0 WHERE id=?")) {
                ps.setInt(1, clan.id);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            Logger.logException(ClanProgressionService.class, e, "Không khởi tạo tiến trình bang");
        }
    }

    /** Loads and safely backfills one clan. Invoked once during Manager startup. */
    public void loadForClan(Connection connection, Clan clan) throws SQLException {
        ensureSchema(connection);
        ProgressState state = new ProgressState(clan.id);
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT clan_exp, potential_total, potential_unspent, progression_version FROM clan WHERE id=?")) {
            ps.setInt(1, clan.id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    clan.clanExp = Math.max(0L, rs.getLong(1));
                    clan.potentialTotal = Math.max(0, rs.getInt(2));
                    clan.potentialUnspent = Math.max(0, rs.getInt(3));
                    clan.progressionVersion = Math.max(0L, rs.getLong(4));
                }
            }
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT branch_key, rank_value FROM clan_potential WHERE clan_id=?")) {
            ps.setInt(1, clan.id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        Branch branch = Branch.valueOf(rs.getString(1));
                        state.ranks.put(branch, Math.max(0, Math.min(config.maxRank(branch), rs.getInt(2))));
                    } catch (IllegalArgumentException ignored) {
                        // Unknown future/retired branch is not applied by this server version.
                    }
                }
            }
        }
        states.put(clan.id, state);
        reconcilePotential(connection, clan, state);
    }

    private void reconcilePotential(Connection connection, Clan clan, ProgressState state) throws SQLException {
        int total = totalPointsForLevel(clan.level);
        int spent = spentPoints(state);
        int unspent = Math.max(0, total - spent);
        int maxMember = Math.max(clan.maxMember, memberSlotsForLevel(clan.level));
        if (clan.potentialTotal != total || clan.potentialUnspent != unspent
                || clan.maxMember != maxMember) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE clan SET potential_total=?, potential_unspent=?, max_member=? WHERE id=?")) {
                ps.setInt(1, total);
                ps.setInt(2, unspent);
                ps.setInt(3, maxMember);
                ps.setInt(4, clan.id);
                ps.executeUpdate();
            }
        }
        clan.potentialTotal = total;
        clan.potentialUnspent = unspent;
        clan.maxMember = maxMember;
    }

    public boolean isProgressionAction(byte action) {
        return (action >= REQUEST_VIEW && action <= REQUEST_ALLOCATE)
                || action == REQUEST_BUFF_SNAPSHOT;
    }

    public void handleRequest(Player player, byte action, Message message) throws IOException {
        switch (action) {
            case REQUEST_VIEW -> sendSnapshot(player);
            case REQUEST_UPGRADE -> upgrade(player);
            case REQUEST_ALLOCATE -> allocate(player, Branch.fromWire(message.reader().readUnsignedByte()));
            case REQUEST_BUFF_SNAPSHOT -> sendBuffSnapshot(player);
            default -> {
            }
        }
    }

    /** Earned activity EXP. No currency deposit can call this method. */
    public boolean addExp(Clan clan, long amount) {
        return addExp(clan, amount, null);
    }

    /** Earned activity EXP with an actor that must receive the refreshed snapshot. */
    public boolean addExp(Clan clan, long amount, Player guaranteedRecipient) {
        if (clan == null || amount <= 0L) {
            return false;
        }
        boolean updated = false;
        synchronized (clan) {
            long previous = clan.clanExp;
            long next = safeAdd(previous, amount);
            if (next == previous) {
                return false;
            }
            try (Connection connection = LocalManager.getConnection()) {
                ensureSchema(connection);
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE clan SET clan_exp=?, progression_version=progression_version+1 WHERE id=?")) {
                    ps.setLong(1, next);
                    ps.setInt(2, clan.id);
                    if (ps.executeUpdate() > 0) {
                        clan.clanExp = next;
                        clan.progressionVersion++;
                        updated = true;
                    }
                }
            } catch (Exception e) {
                Logger.logException(ClanProgressionService.class, e, "Không cộng Clan EXP");
            }
        }
        if (updated) {
            broadcastSnapshot(clan, guaranteedRecipient);
        }
        return updated;
    }

    private void upgrade(Player player) {
        if (!requireClan(player)) {
            return;
        }
        Clan clan = player.clan;
        if (!clan.isLeader(player)) {
            notify(player, "Chỉ bang chủ được nâng cấp bang.");
            return;
        }
        UpgradeResult result;
        synchronized (clan) {
            result = persistUpgrade(clan);
        }
        if (!result.success) {
            notify(player, result.message);
            return;
        }
        notify(player, "Bang đã lên cấp " + clan.level + ". Nhận 5 Điểm tiềm năng bang.");
        sendClanNotice(clan, player.name + " đã nâng bang lên cấp " + clan.level + ".");
        refreshAndBroadcastStats(clan, player);
        clan.sendMyClanForAllMember();
    }

    /** Shared entry point for the legacy NPC and the progression packet. */
    public void requestUpgrade(Player player) {
        upgrade(player);
    }

    private UpgradeResult persistUpgrade(Clan clan) {
        int level = Clan.normalizeLevel(clan.level);
        if (level >= config.technicalMaxLevel()) {
            return UpgradeResult.fail("Bang đã đạt trần kỹ thuật cấp " + config.technicalMaxLevel() + ".");
        }
        long expNeed = expRequired(level);
        int capsuleNeed = capsuleRequired(level);
        long goldNeed = goldRequired(level);
        long gemNeed = gemRequired(level);
        if (clan.clanExp < expNeed) {
            return UpgradeResult.fail("Cần " + expNeed + " Clan EXP để nâng cấp.");
        }
        if (clan.capsuleClan < capsuleNeed) {
            return UpgradeResult.fail("Cần " + capsuleNeed + " Capsule Bang.");
        }
        if (clan.clanGold < goldNeed) {
            return UpgradeResult.fail("Cần " + goldNeed + " vàng bang.");
        }
        if (clan.clanGem < gemNeed) {
            return UpgradeResult.fail("Cần " + gemNeed + " ngọc bang.");
        }
        int newLevel = level + 1;
        long newExp = clan.clanExp - expNeed;
        int newCapsule = clan.capsuleClan - capsuleNeed;
        long newGold = clan.clanGold - goldNeed;
        long newGem = clan.clanGem - gemNeed;
        int newTotal = totalPointsForLevel(newLevel);
        int newUnspent = clan.potentialUnspent + 5;
        int newMaxMember = Math.max(clan.maxMember, memberSlotsForLevel(newLevel));
        try (Connection connection = LocalManager.getConnection()) {
            ensureSchema(connection);
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE clan SET level=?, clan_exp=?, clan_point=?, clan_gold=?, clan_gem=?, max_member=?, "
                    + "potential_total=?, potential_unspent=?, progression_version=progression_version+1 WHERE id=?")) {
                ps.setInt(1, newLevel);
                ps.setLong(2, newExp);
                ps.setInt(3, newCapsule);
                ps.setLong(4, newGold);
                ps.setLong(5, newGem);
                ps.setInt(6, newMaxMember);
                ps.setInt(7, newTotal);
                ps.setInt(8, newUnspent);
                ps.setInt(9, clan.id);
                if (ps.executeUpdate() != 1) {
                    connection.rollback();
                    return UpgradeResult.fail("Không thể lưu nâng cấp bang.");
                }
            }
            connection.commit();
            clan.level = newLevel;
            clan.clanExp = newExp;
            clan.capsuleClan = newCapsule;
            clan.clanGold = newGold;
            clan.clanGem = newGem;
            clan.maxMember = newMaxMember;
            clan.potentialTotal = newTotal;
            clan.potentialUnspent = newUnspent;
            clan.progressionVersion++;
            return UpgradeResult.ok();
        } catch (Exception e) {
            Logger.logException(ClanProgressionService.class, e, "Không nâng cấp bang");
            return UpgradeResult.fail("Nâng cấp thất bại, hãy thử lại.");
        }
    }

    private void allocate(Player player, Branch branch) {
        if (!requireClan(player)) {
            return;
        }
        if (branch == null) {
            notify(player, "Nhánh tiềm năng không hợp lệ.");
            return;
        }
        Clan clan = player.clan;
        if (!clan.isLeader(player)) {
            notify(player, "Chỉ bang chủ được cộng điểm tiềm năng.");
            return;
        }
        AllocationResult result;
        synchronized (clan) {
            result = persistAllocation(clan, player, branch);
        }
        if (!result.success) {
            notify(player, result.message);
            return;
        }
        notify(player, "Đã tăng " + branch.display + " lên bậc " + result.newRank + ".");
        refreshAndBroadcastStats(clan, player);
    }

    private AllocationResult persistAllocation(Clan clan, Player player, Branch branch) {
        ProgressState state = stateFor(clan);
        int oldRank = state.rank(branch);
        int maxRank = config.maxRank(branch);
        if (oldRank >= maxRank) {
            return AllocationResult.fail("Nhánh này đã đạt bậc tối đa.");
        }
        int cost = costForRank(oldRank);
        if (clan.potentialUnspent < cost) {
            return AllocationResult.fail("Không đủ Điểm tiềm năng (cần " + cost + ").");
        }
        int newRank = oldRank + 1;
        int newUnspent = clan.potentialUnspent - cost;
        try (Connection connection = LocalManager.getConnection()) {
            ensureSchema(connection);
            connection.setAutoCommit(false);
            try (PreparedStatement rank = connection.prepareStatement(
                    "INSERT INTO clan_potential (clan_id, branch_key, rank_value) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE rank_value=VALUES(rank_value)")) {
                rank.setInt(1, clan.id);
                rank.setString(2, branch.key);
                rank.setInt(3, newRank);
                rank.executeUpdate();
            }
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE clan SET potential_unspent=?, progression_version=progression_version+1 WHERE id=?")) {
                update.setInt(1, newUnspent);
                update.setInt(2, clan.id);
                if (update.executeUpdate() != 1) {
                    connection.rollback();
                    return AllocationResult.fail("Không thể lưu điểm tiềm năng.");
                }
            }
            try (PreparedStatement audit = connection.prepareStatement(
                    "INSERT INTO clan_potential_audit (clan_id, actor_id, branch_key, rank_before, rank_after, "
                    + "unspent_before, unspent_after, point_cost) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                audit.setInt(1, clan.id);
                audit.setLong(2, player.id);
                audit.setString(3, branch.key);
                audit.setInt(4, oldRank);
                audit.setInt(5, newRank);
                audit.setInt(6, clan.potentialUnspent);
                audit.setInt(7, newUnspent);
                audit.setInt(8, cost);
                audit.executeUpdate();
            }
            connection.commit();
            state.ranks.put(branch, newRank);
            clan.potentialUnspent = newUnspent;
            clan.progressionVersion++;
            return AllocationResult.ok(newRank);
        } catch (Exception e) {
            Logger.logException(ClanProgressionService.class, e, "Không cộng tiềm năng bang");
            return AllocationResult.fail("Cộng điểm thất bại, hãy thử lại.");
        }
    }

    public void sendSnapshot(Player player) {
        if (!requireClan(player)) {
            return;
        }
        Clan clan = player.clan;
        ProgressState state = stateFor(clan);
        try {
            Message message = new Message(127);
            message.writer().writeByte(RESPONSE_SNAPSHOT);
            message.writer().writeInt(clan.id);
            message.writer().writeInt(clan.level);
            message.writer().writeLong(clan.clanExp);
            message.writer().writeLong(expRequired(clan.level));
            message.writer().writeInt(capsuleRequired(clan.level));
            message.writer().writeLong(goldRequired(clan.level));
            message.writer().writeLong(gemRequired(clan.level));
            message.writer().writeInt(clan.potentialTotal);
            message.writer().writeInt(clan.potentialUnspent);
            message.writer().writeLong(clan.progressionVersion);
            for (Branch branch : Branch.values()) {
                message.writer().writeByte(state.rank(branch));
            }
            // Detail v1 carries only live character stats. Clan buffs use their own
            // response packet so compatibility parsing cannot silently hide them.
            message.writer().writeByte(1);
            message.writer().writeInt(player.nPoint == null ? 0 : player.nPoint.hpMax);
            message.writer().writeInt(player.nPoint == null ? 0 : player.nPoint.mpMax);
            message.writer().writeInt(player.nPoint == null ? 0 : player.nPoint.dame);
            message.writer().writeInt(player.nPoint == null ? 0 : player.nPoint.hp);
            message.writer().writeInt(player.nPoint == null ? 0 : player.nPoint.mp);
            player.sendMessage(message);
            message.cleanup();
            sendBuffSnapshot(player, clan);
        } catch (Exception e) {
            Logger.logException(ClanProgressionService.class, e, "Không gửi tiến trình bang");
        }
    }

    /** Explicit endpoint used by the clan-info tab to refresh buffs independently. */
    public void sendBuffSnapshot(Player player) {
        if (!requireClan(player)) {
            return;
        }
        sendBuffSnapshot(player, player.clan);
    }

    /** Independent packet so a progression-detail compatibility issue cannot hide clan buffs. */
    private void sendBuffSnapshot(Player player, Clan clan) {
        Message message = null;
        try {
            List<ClanBuffService.BuffView> buffStates = ClanBuffService.gI().buffSnapshot(clan.id);
            long snapshotAt = System.currentTimeMillis();
            message = new Message(127);
            message.writer().writeByte(RESPONSE_BUFF_SNAPSHOT);
            message.writer().writeInt(clan.id);
            message.writer().writeByte(1);
            message.writer().writeByte(buffStates.size());
            for (ClanBuffService.BuffView buff : buffStates) {
                message.writer().writeByte(buff.typeId());
                message.writer().writeShort(buff.percent());
                message.writer().writeLong(Math.max(0L, buff.expiresAt() - snapshotAt));
            }
            player.sendMessage(message);
        } catch (Exception e) {
            Logger.logException(ClanProgressionService.class, e, "Không gửi trạng thái buff bang");
        } finally {
            if (message != null) {
                message.cleanup();
            }
        }
    }

    private void broadcastSnapshot(Clan clan) {
        broadcastSnapshot(clan, null);
    }

    private void broadcastSnapshot(Clan clan, Player guaranteedRecipient) {
        boolean recipientIncluded = false;
        for (Player member : new ArrayList<>(clan.membersInGame)) {
            if (member != null && member.clan != null && member.clan.id == clan.id) {
                sendSnapshot(member);
                if (member == guaranteedRecipient) {
                    recipientIncluded = true;
                }
            }
        }
        if (!recipientIncluded && guaranteedRecipient != null
                && guaranteedRecipient.clan != null && guaranteedRecipient.clan.id == clan.id) {
            sendSnapshot(guaranteedRecipient);
        }
    }

    private boolean requireClan(Player player) {
        if (player == null || player.clan == null) {
            if (player != null) {
                notify(player, "Bạn chưa có bang hội.");
            }
            return false;
        }
        return true;
    }

    private void refreshAndBroadcastStats(Clan clan, Player guaranteedRecipient) {
        ArrayList<Player> recipients = new ArrayList<>();
        for (Player member : new ArrayList<>(clan.membersInGame)) {
            if (isOnlineClanPlayer(member, clan) && !recipients.contains(member)) {
                recipients.add(member);
            }
        }
        if (isOnlineClanPlayer(guaranteedRecipient, clan) && !recipients.contains(guaranteedRecipient)) {
            recipients.add(guaranteedRecipient);
        }

        // Recalculate first, then send command 127 before the legacy point packet.
        // A failure for one member must never prevent the actor receiving the new rank.
        for (Player member : recipients) {
            try {
                recalculatePlayerStats(member);
            } catch (Exception e) {
                Logger.logException(ClanProgressionService.class, e,
                        "Không tính lại chỉ số bang cho " + member.name);
            }
        }
        for (Player member : recipients) {
            sendSnapshot(member);
        }
        for (Player member : recipients) {
            try {
                Service.gI().sendPointSnapshot(member);
            } catch (Exception e) {
                Logger.logException(ClanProgressionService.class, e,
                        "Không gửi chỉ số bang cho " + member.name);
            }
        }
    }

    private boolean isOnlineClanPlayer(Player player, Clan clan) {
        return player != null && player.clan != null && player.clan.id == clan.id
                && player.isPl() && player.nPoint != null;
    }

    private void recalculatePlayerStats(Player player) {
        int oldHp = player.nPoint.hp;
        int oldMp = player.nPoint.mp;
        int oldHpMax = player.nPoint.hpMax;
        int oldMpMax = player.nPoint.mpMax;
        boolean hpWasFull = oldHpMax > 0 && oldHp >= oldHpMax;
        boolean mpWasFull = oldMpMax > 0 && oldMp >= oldMpMax;

        player.nPoint.calPoint();
        if (hpWasFull && player.nPoint.hpMax > oldHpMax) {
            player.nPoint.hp = player.nPoint.hpMax;
        }
        if (mpWasFull && player.nPoint.mpMax > oldMpMax) {
            player.nPoint.mp = player.nPoint.mpMax;
        }
    }

    public int rank(Clan clan, Branch branch) {
        if (clan == null || branch == null) {
            return 0;
        }
        return stateFor(clan).rank(branch);
    }

    /** Basis points, 100 = 1%. Option 50/77/103 gain 0.2% per point; the rest gain 1% per point. */
    public int statBasisPoints(Player player, Branch branch, boolean playerVersusPlayer) {
        if (player == null || !player.isPl() || player.clan == null || branch == null) {
            return 0;
        }
        int progression = effectBasisPoints(branch, rank(player.clan, branch));
        int temporary = ClanBuffService.gI().basisPoints(player, branch);
        return (int) Math.min(10_000L, (long) progression + temporary);
    }

    public int mobGoldBasisPoints(Player player) {
        return statBasisPoints(player, Branch.MOB_GOLD, false);
    }

    public int treeYieldBasisPoints(int clanId) {
        return 0;
    }

    public int pveReductionBasisPoints(Player player) {
        return 0;
    }

    public int luckPercent(Player player) {
        return statBasisPoints(player, Branch.LUCK, false) / 100;
    }

    public int powerGainBasisPoints(Player player) {
        return statBasisPoints(player, Branch.POWER, false);
    }

    /** PvP modes use the 50% coefficient for static HP/KI clan bonuses too. */
    public boolean isPvpMode(Player player) {
        return player != null && (player.pvp != null
                || player.typePk == ConstPlayer.PK_PVP
                || player.typePk == ConstPlayer.PK_PVP_2
                || player.typePk == ConstPlayer.PK_ALL);
    }

    public int expWater() { return config.expWater(); }
    public int expFertilize() { return config.expFertilize(); }
    public int expWeeklyContract() { return config.expWeeklyContract(); }
    public long expRequired(int level) { return config.expRequired(level); }
    public int capsuleRequired(int level) { return config.capsuleRequired(level); }
    public long goldRequired(int level) { return config.goldRequired(level); }
    public long gemRequired(int level) { return config.gemRequired(level); }
    public int costForRank(int currentRank) { return 1; }
    public int totalPointsForLevel(int level) { return Math.max(0, Clan.normalizeLevel(level) - 1) * 5; }

    /** Returns the exact effect without discarding partial percentages (20 basis points = 0.2%). */
    public int effectBasisPoints(Branch branch, int rank) {
        if (branch == null) {
            return 0;
        }
        int safeRank = Math.max(0, rank);
        int basisPointsPerRank = branch == Branch.ATTACK || branch == Branch.HP || branch == Branch.KI
                ? 20 : 100;
        return (int) Math.min(10_000L, (long) safeRank * basisPointsPerRank);
    }

    private int spentPoints(ProgressState state) {
        int spent = 0;
        for (Branch branch : Branch.values()) {
            spent += state.rank(branch);
        }
        return spent;
    }

    public int memberSlotsForLevel(int level) {
        int normalizedLevel = Clan.normalizeLevel(level);
        int slots = Clan.DEFAULT_MAX_MEMBER + normalizedLevel - 1;
        return Math.min(Clan.MAX_MEMBER_LIMIT, slots);
    }

    private ProgressState stateFor(Clan clan) {
        return states.computeIfAbsent(clan.id, ProgressState::new);
    }

    private static long safeAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, left + right);
    }

    private static void notify(Player player, String text) {
        Service.gI().sendThongBao(player, text);
    }

    private static void sendClanNotice(Clan clan, String text) {
        ClanMember leader = clan.getLeader();
        if (leader == null) {
            return;
        }
        ClanMessage message = new ClanMessage(clan);
        message.type = 0;
        message.playerId = leader.id;
        message.playerName = leader.name;
        message.role = leader.role;
        message.text = text;
        message.color = ClanMessage.RED;
        clan.addClanMessage(message);
        clan.sendMessageClan(message);
    }

    private static final class ProgressState {
        final int clanId;
        final EnumMap<Branch, Integer> ranks = new EnumMap<>(Branch.class);

        ProgressState(int clanId) {
            this.clanId = clanId;
        }

        int rank(Branch branch) {
            return ranks.getOrDefault(branch, 0);
        }
    }

    private static final class UpgradeResult {
        final boolean success;
        final String message;

        private UpgradeResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        static UpgradeResult ok() { return new UpgradeResult(true, ""); }
        static UpgradeResult fail(String message) { return new UpgradeResult(false, message); }
    }

    private static final class AllocationResult {
        final boolean success;
        final String message;
        final int newRank;

        private AllocationResult(boolean success, String message, int newRank) {
            this.success = success;
            this.message = message;
            this.newRank = newRank;
        }
        static AllocationResult ok(int rank) { return new AllocationResult(true, "", rank); }
        static AllocationResult fail(String message) { return new AllocationResult(false, message, 0); }
    }

    private static final class Config {
        private final Properties values = new Properties();

        Config() {
            try (FileInputStream input = new FileInputStream("data/clan_progression.properties")) {
                values.load(input);
            } catch (Exception e) {
                Logger.error("Không đọc được data/clan_progression.properties, dùng giá trị mặc định.\n");
            }
        }

        int technicalMaxLevel() { return (int) bounded("technical_max_level", 1000, 1, Clan.TECHNICAL_MAX_LEVEL); }
        int expWater() { return (int) bounded("exp_tree_water", 10, 0, 1_000_000); }
        int expFertilize() { return (int) bounded("exp_tree_fertilize", 30, 0, 1_000_000); }
        int expWeeklyContract() { return (int) bounded("exp_weekly_contract", 250, 0, 100_000_000); }
        int maxRank(Branch branch) { return (int) bounded(branch.configPrefix + "_max_rank", 20, 0, 100); }
        long expRequired(int level) {
            return scaled("base_exp", 100L, level, 1.55d, 1L, Long.MAX_VALUE);
        }
        int capsuleRequired(int level) {
            return (int) Math.min(Integer.MAX_VALUE, scaled("base_capsule", 100L, level, 1.35d, 1L, Integer.MAX_VALUE));
        }
        long goldRequired(int level) {
            return level < 5 ? 0L : scaled("gold_base", 100_000L, level, 1.35d, 0L, Long.MAX_VALUE);
        }
        long gemRequired(int level) {
            return (level + 1) % 10 == 0 ? bounded("gem_per_ten_levels", 5L, 0L, 1_000_000L) * ((level + 1) / 10L) : 0L;
        }

        private long scaled(String key, long fallback, int level, double power, long min, long max) {
            double value = bounded(key, fallback, 0L, Long.MAX_VALUE) * Math.pow(Math.max(1, level), power);
            if (!Double.isFinite(value) || value >= max) return max;
            return Math.max(min, Math.min(max, Math.round(value)));
        }
        private long bounded(String key, long fallback, long min, long max) {
            try {
                long value = Long.parseLong(values.getProperty(key, String.valueOf(fallback)).trim());
                return Math.max(min, Math.min(max, value));
            } catch (Exception ignored) {
                return fallback;
            }
        }
    }
}
