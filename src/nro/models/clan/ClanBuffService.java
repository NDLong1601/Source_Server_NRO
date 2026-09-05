package nro.models.clan;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import nro.models.data.LocalManager;
import nro.models.player.Player;
import nro.models.services.PlayerService;
import nro.models.services.Service;
import nro.models.utils.Logger;
import nro.models.utils.Util;

/** Persistent, non-stacking clan-wide buffs activated from shared support items. */
public final class ClanBuffService {

    public static final long DAY_MILLIS = 24L * 60L * 60L * 1_000L;
    public static final long RECOVERY_INTERVAL_MILLIS = 5_000L;

    private static final ClanBuffService INSTANCE = new ClanBuffService();
    private final Map<Integer, BuffState> states = new ConcurrentHashMap<>();
    private volatile boolean schemaReady;

    public enum BuffType {
        HP_REGEN(0, "Hồi HP", 1),
        KI_REGEN(1, "Hồi KI", 1),
        ATTACK(2, "Sức đánh", 10),
        LUCK(3, "May mắn", 10),
        POWER(4, "Tiềm năng, sức mạnh", 15),
        MOB_GOLD(5, "Vàng từ quái", 20);

        public final int wireId;
        public final String displayName;
        public final int percent;

        BuffType(int wireId, String displayName, int percent) {
            this.wireId = wireId;
            this.displayName = displayName;
            this.percent = percent;
        }

        static BuffType fromWireId(int id) {
            for (BuffType type : values()) {
                if (type.wireId == id) {
                    return type;
                }
            }
            return null;
        }
    }

    public record BuffView(int typeId, int percent, long expiresAt) { }

    public record Activation(BuffType type, int days, long expiresAt) {
        static Activation invalid() {
            return new Activation(null, 0, 0L);
        }

        public boolean success() {
            return type != null && days > 0 && expiresAt > 0L;
        }
    }

    private ClanBuffService() {
    }

    public static ClanBuffService gI() {
        return INSTANCE;
    }

    public synchronized void ensureSchema(Connection connection) throws SQLException {
        if (schemaReady) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS clan_active_buff ("
                    + "clan_id INT NOT NULL, buff_type TINYINT NOT NULL, effect_percent INT NOT NULL, "
                    + "expires_at BIGINT NOT NULL, version BIGINT NOT NULL DEFAULT 1, "
                    + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY (clan_id,buff_type), KEY idx_clan_buff_expiry (expires_at)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS clan_active_buff_audit ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT, clan_id INT NOT NULL, actor_id BIGINT NOT NULL, "
                    + "item_template_id INT NOT NULL, buff_type TINYINT NOT NULL, days_added INT NOT NULL, "
                    + "expires_before BIGINT NOT NULL, expires_after BIGINT NOT NULL, "
                    + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (id), "
                    + "KEY idx_clan_buff_audit (clan_id,id)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
        }
        schemaReady = true;
    }

    public boolean isBuffItem(int itemId) {
        return itemDefinition(itemId) != null;
    }

    /** Caller owns the transaction. The item is consumed only after this succeeds. */
    public Activation activateInTransaction(Connection connection, Clan clan, long actorId, int itemId)
            throws SQLException {
        ItemDefinition definition = itemDefinition(itemId);
        if (connection == null || clan == null || definition == null) {
            return Activation.invalid();
        }
        ensureSchema(connection);
        long now = System.currentTimeMillis();
        long before = 0L;
        long version = 0L;
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT expires_at,version FROM clan_active_buff WHERE clan_id=? AND buff_type=? FOR UPDATE")) {
            select.setInt(1, clan.id);
            select.setInt(2, definition.type.wireId);
            try (ResultSet rs = select.executeQuery()) {
                if (rs.next()) {
                    before = Math.max(0L, rs.getLong(1));
                    version = Math.max(0L, rs.getLong(2));
                }
            }
        }
        long base = Math.max(now, before);
        long duration = definition.days * DAY_MILLIS;
        long after = base > Long.MAX_VALUE - duration ? Long.MAX_VALUE : base + duration;
        try (PreparedStatement upsert = connection.prepareStatement(
                "INSERT INTO clan_active_buff (clan_id,buff_type,effect_percent,expires_at,version) VALUES (?,?,?,?,1) "
                + "ON DUPLICATE KEY UPDATE effect_percent=VALUES(effect_percent),expires_at=VALUES(expires_at),version=version+1")) {
            upsert.setInt(1, clan.id);
            upsert.setInt(2, definition.type.wireId);
            upsert.setInt(3, definition.type.percent);
            upsert.setLong(4, after);
            upsert.executeUpdate();
        }
        try (PreparedStatement audit = connection.prepareStatement(
                "INSERT INTO clan_active_buff_audit (clan_id,actor_id,item_template_id,buff_type,days_added,expires_before,expires_after) "
                + "VALUES (?,?,?,?,?,?,?)")) {
            audit.setInt(1, clan.id);
            audit.setLong(2, actorId);
            audit.setInt(3, itemId);
            audit.setInt(4, definition.type.wireId);
            audit.setInt(5, definition.days);
            audit.setLong(6, before);
            audit.setLong(7, after);
            audit.executeUpdate();
        }
        return new Activation(definition.type, definition.days, after);
    }

    /** Refreshes cache, stats and snapshots after the activation transaction commits. */
    public void afterActivation(Clan clan, Player actor, Activation activation) {
        if (clan == null || activation == null || !activation.success()) {
            return;
        }
        states.remove(clan.id);
        ArrayList<Player> recipients = new ArrayList<>();
        for (Player member : new ArrayList<>(clan.membersInGame)) {
            if (isOnlineMember(member, clan) && !recipients.contains(member)) {
                recipients.add(member);
            }
        }
        if (isOnlineMember(actor, clan) && !recipients.contains(actor)) {
            recipients.add(actor);
        }
        int mask = activeMask(clan.id);
        for (Player member : recipients) {
            member.lastClanBuffMask = mask;
            try {
                member.nPoint.calPoint();
                ClanProgressionService.gI().sendSnapshot(member);
                Service.gI().sendPointSnapshot(member);
            } catch (Exception e) {
                Logger.logException(ClanBuffService.class, e, "Cập nhật buff bang cho " + member.name);
            }
        }
        sendClanNotice(clan, actor.name + " đã kích hoạt " + activation.type.displayName
                + " bang +" + activation.type.percent + "% trong " + activation.days + " ngày.");
    }

    public int basisPoints(Player player, ClanProgressionService.Branch branch) {
        if (player == null || player.clan == null || branch == null) {
            return 0;
        }
        BuffType type = switch (branch) {
            case ATTACK -> BuffType.ATTACK;
            case LUCK -> BuffType.LUCK;
            case POWER -> BuffType.POWER;
            case MOB_GOLD -> BuffType.MOB_GOLD;
            default -> null;
        };
        return type == null || !isActive(player.clan.id, type) ? 0 : type.percent * 100;
    }

    public List<BuffView> activeBuffs(int clanId) {
        if (clanId < 0) {
            return Collections.emptyList();
        }
        long now = System.currentTimeMillis();
        BuffState state = stateFor(clanId);
        ArrayList<BuffView> result = new ArrayList<>();
        for (BuffType type : BuffType.values()) {
            BuffEntry entry = state.entries.get(type);
            if (entry != null && entry.expiresAt > now) {
                result.add(new BuffView(type.wireId, type.percent, entry.expiresAt));
            }
        }
        return result;
    }

    /** Complete six-row snapshot used by the clan information UI. */
    public List<BuffView> buffSnapshot(int clanId) {
        long now = System.currentTimeMillis();
        BuffState state = clanId < 0 ? null : stateFor(clanId);
        ArrayList<BuffView> result = new ArrayList<>(BuffType.values().length);
        for (BuffType type : BuffType.values()) {
            BuffEntry entry = state == null ? null : state.entries.get(type);
            long expiresAt = entry != null && entry.expiresAt > now ? entry.expiresAt : 0L;
            result.add(new BuffView(type.wireId, type.percent, expiresAt));
        }
        return result;
    }

    /** Called from Player.update; handles 5-second recovery and expiry refresh. */
    public void tick(Player player) {
        if (player == null || !player.isPl() || player.nPoint == null) {
            return;
        }
        int mask = player.clan == null ? 0 : activeMask(player.clan.id);
        if (player.lastClanBuffMask < 0) {
            player.lastClanBuffMask = mask;
        } else if (player.lastClanBuffMask != mask) {
            player.lastClanBuffMask = mask;
            player.nPoint.calPoint();
            Service.gI().sendPointSnapshot(player);
            if (player.clan != null) {
                ClanProgressionService.gI().sendSnapshot(player);
            }
        }
        if (player.clan == null || player.isDie()
                || !Util.canDoWithTime(player.lastClanBuffRecoveryAt, RECOVERY_INTERVAL_MILLIS)) {
            return;
        }
        boolean hpActive = (mask & (1 << BuffType.HP_REGEN.wireId)) != 0;
        boolean kiActive = (mask & (1 << BuffType.KI_REGEN.wireId)) != 0;
        boolean hpChanged = false;
        boolean kiChanged = false;
        if (hpActive && player.nPoint.hp < player.nPoint.hpMax) {
            long amount = Math.max(1L, player.nPoint.hpMax / 100L);
            player.nPoint.setHp(Math.min(player.nPoint.hpMax, player.nPoint.hp + amount));
            hpChanged = true;
        }
        if (kiActive && player.nPoint.mp < player.nPoint.mpMax) {
            long amount = Math.max(1L, player.nPoint.mpMax / 100L);
            player.nPoint.setMp(Math.min(player.nPoint.mpMax, player.nPoint.mp + amount));
            kiChanged = true;
        }
        player.lastClanBuffRecoveryAt = System.currentTimeMillis();
        if (hpChanged) {
            PlayerService.gI().sendInfoHp(player);
        }
        if (kiChanged) {
            PlayerService.gI().sendInfoMp(player);
        }
    }

    private int activeMask(int clanId) {
        int mask = 0;
        long now = System.currentTimeMillis();
        for (Map.Entry<BuffType, BuffEntry> entry : stateFor(clanId).entries.entrySet()) {
            if (entry.getValue().expiresAt > now) {
                mask |= 1 << entry.getKey().wireId;
            }
        }
        return mask;
    }

    private boolean isActive(int clanId, BuffType type) {
        BuffEntry entry = stateFor(clanId).entries.get(type);
        return entry != null && entry.expiresAt > System.currentTimeMillis();
    }

    private BuffState stateFor(int clanId) {
        BuffState current = states.get(clanId);
        if (current != null && (current.loaded || System.currentTimeMillis() - current.loadedAt < 5_000L)) {
            return current;
        }
        BuffState loaded = new BuffState();
        loaded.loadedAt = System.currentTimeMillis();
        try (Connection connection = LocalManager.getConnection()) {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT buff_type,effect_percent,expires_at FROM clan_active_buff WHERE clan_id=?")) {
                ps.setInt(1, clanId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        BuffType type = BuffType.fromWireId(rs.getInt(1));
                        if (type != null) {
                            loaded.entries.put(type, new BuffEntry(rs.getInt(2), rs.getLong(3)));
                        }
                    }
                }
            }
            loaded.loaded = true;
        } catch (Exception e) {
            Logger.logException(ClanBuffService.class, e, "Tải buff bang " + clanId);
        }
        states.put(clanId, loaded);
        return loaded;
    }

    private static ItemDefinition itemDefinition(int itemId) {
        if (itemId >= 2252 && itemId <= 2254) {
            return new ItemDefinition(BuffType.MOB_GOLD, daysForOffset(itemId - 2252));
        }
        if (itemId >= 2255 && itemId <= 2257) {
            return new ItemDefinition(BuffType.POWER, daysForOffset(itemId - 2255));
        }
        if (itemId >= 2258 && itemId <= 2260) {
            return new ItemDefinition(BuffType.HP_REGEN, daysForOffset(itemId - 2258));
        }
        if (itemId >= 2261 && itemId <= 2263) {
            return new ItemDefinition(BuffType.KI_REGEN, daysForOffset(itemId - 2261));
        }
        if (itemId >= 2264 && itemId <= 2266) {
            return new ItemDefinition(BuffType.LUCK, daysForOffset(itemId - 2264));
        }
        if (itemId >= 2267 && itemId <= 2269) {
            return new ItemDefinition(BuffType.ATTACK, daysForOffset(itemId - 2267));
        }
        return null;
    }

    private static int daysForOffset(int offset) {
        return offset == 0 ? 1 : offset == 1 ? 3 : 7;
    }

    private static boolean isOnlineMember(Player player, Clan clan) {
        return player != null && player.clan != null && player.clan.id == clan.id
                && player.isPl() && player.nPoint != null;
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

    private record ItemDefinition(BuffType type, int days) { }
    private record BuffEntry(int percent, long expiresAt) { }

    private static final class BuffState {
        final EnumMap<BuffType, BuffEntry> entries = new EnumMap<>(BuffType.class);
        long loadedAt;
        boolean loaded;
    }
}
