package nro.models.server;

import nro.models.data.LocalManager;
import nro.models.database.PlayerDAO;
import lombok.Getter;
import nro.models.map.ItemMap;
import nro.models.player.Player;
import nro.models.network.SessionManager;
import nro.models.interfaces.ISession;
import nro.models.network.MySession;
import nro.models.services.Service;
import nro.models.map.service.ChangeMapService;
import nro.models.services.shenron.SummonDragon;
import nro.models.services_func.TransactionService;
import nro.models.services_dungeon.NgocRongNamecService;
import nro.models.utils.Functions;
import nro.models.utils.Logger;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import nro.models.services.shenron.SummonDragonNamek;

public class Client implements Runnable {

    private static Client instance;
    private static final long ADMIN_ONLINE_SYNC_INTERVAL_MS = 5000L;
    private static final long ADMIN_ONLINE_ERROR_LOG_INTERVAL_MS = 60000L;

    private final Map<Long, Player> players_id = new HashMap<>();
    private final Map<Integer, Player> players_userId = new HashMap<>();
    private final Map<String, Player> players_name = new HashMap<>();
    @Getter
    private final List<Player> players = new ArrayList<>();
    private boolean adminOnlineSchemaReady;
    private long lastAdminOnlineSyncMillis;
    private long lastAdminOnlineErrorLogMillis;

    private Client() {
        Executors.newSingleThreadExecutor().submit(this, "Update Client");
    }

    public static Client gI() {
        if (instance == null) {
            instance = new Client();
        }
        return instance;
    }

    public void put(Player player) {
        if (!players_id.containsKey(player.id)) {
            this.players_id.put(player.id, player);
        }
        if (!players_name.containsValue(player)) {
            this.players_name.put(player.name, player);
        }
        if (!players_userId.containsValue(player)) {
            this.players_userId.put(player.getSession().userId, player);
        }
        if (!players.contains(player)) {
            this.players.add(player);
        }
        syncAdminOnlinePlayer(player);

    }

    /**
     * Keeps the online-name lookup in sync when a connected player changes
     * their character name. Player IDs and account IDs are unaffected.
     */
    public void updatePlayerName(Player player, String previousName) {
        if (player == null || previousName == null) {
            return;
        }
        this.players_name.remove(previousName, player);
        this.players_name.put(player.name, player);
    }

    private void remove(MySession session) {
        if (session.player != null) {
            this.remove(session.player);
            session.player.dispose();
        }
        if (session.joinedGame) {
            session.joinedGame = false;
            try {
                LocalManager.executeUpdate("update account set last_time_logout = ? where id = ?",
                        new Timestamp(System.currentTimeMillis()), session.userId);
            } catch (Exception e) {
                Logger.logException(Client.class, e);
            }
        }
        ServerManager.gI().disconnect(session);
    }

    private void remove(Player player) {
        this.players_id.remove(player.id);
        this.players_name.remove(player.name);
        this.players_userId.remove(player.getSession().userId);
        this.players.remove(player);
        removeAdminOnlinePlayer(player);
        if (!player.beforeDispose) {
            player.beforeDispose = true;
            player.mapIdBeforeLogout = player.zone.map.mapId;
            if (player.idNRNM != -1) {
                ItemMap itemMap = new ItemMap(player.zone, player.idNRNM, 1, player.location.x, player.location.y, -1);
                Service.gI().dropItemMap(player.zone, itemMap);
                NgocRongNamecService.gI().pNrNamec[player.idNRNM - 353] = "";
                NgocRongNamecService.gI().idpNrNamec[player.idNRNM - 353] = -1;
                player.idNRNM = -1;
            }
            ChangeMapService.gI().exitMap(player);
            TransactionService.gI().cancelTrade(player);
            if (player.clan != null) {
                player.clan.removeMemberOnline(null, player);
            }
            if (SummonDragon.gI().playerSummonShenron != null
                    && SummonDragon.gI().playerSummonShenron.id == player.id) {
                SummonDragon.gI().isPlayerDisconnect = true;
            }
            if (SummonDragonNamek.gI().playerSummonShenron != null
                    && SummonDragonNamek.gI().playerSummonShenron.id == player.id) {
                SummonDragonNamek.gI().isPlayerDisconnect = true;
            }
            if (player.shenronEvent != null) {
                player.shenronEvent.isPlayerDisconnect = true;
            }
            if (player.mobMe != null) {
                player.mobMe.mobMeDie();
            }
            if (player.pet != null) {
                if (player.pet.mobMe != null) {
                    player.pet.mobMe.mobMeDie();
                }
                ChangeMapService.gI().exitMap(player.pet);
            }
        }
        PlayerDAO.updatePlayer(player);
    }

    public void kickSession(MySession session) {
        if (session != null) {
            this.remove(session);
            session.disconnect();
        }
    }

    public Player getPlayer(long playerId) {
        return this.players_id.get(playerId);
    }

    public Player getPlayerByUser(int userId) {
        return this.players_userId.get(userId);
    }

    public Player getPlayer(String name) {
        return this.players_name.get(name);
    }

    public List<Player> getPlayers() {
        return this.players;
    }

    public void close() {
        Logger.log(Logger.YELLOW, "BEGIN KICK OUT SESSION " + players.size() + "\n");
        while (!players.isEmpty()) {
            Player pl = players.remove(0);
            if (pl != null && pl.getSession() != null) {
                this.kickSession(pl.getSession());
            }
        }
        Logger.success("SUCCESSFUL\n");
    }

    private void update() {
        for (int i = SessionManager.gI().getSessions().size() - 1; i >= 0; i--) {
            ISession s = SessionManager.gI().getSessions().get(i);
            MySession session = (MySession) s;
            if (session == null) {
                SessionManager.gI().getSessions().remove(i);
                continue;
            }
            if (session.timeWait > 0) {
                session.timeWait--;
                if (session.timeWait == 0) {
                    kickSession(session);
                }
            }
        }
        syncAdminOnlinePlayers();
    }

    private void ensureAdminOnlineSchema() throws Exception {
        if (adminOnlineSchemaReady) {
            return;
        }
        LocalManager.executeUpdate("CREATE TABLE IF NOT EXISTS admin_player_online ("
                + "player_id BIGINT NOT NULL PRIMARY KEY,"
                + "account_id INT NOT NULL DEFAULT 0,"
                + "map_id INT NOT NULL DEFAULT -1,"
                + "zone_id INT NOT NULL DEFAULT -1,"
                + "last_seen DATETIME NOT NULL,"
                + "KEY idx_admin_player_online_seen (last_seen)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");
        adminOnlineSchemaReady = true;
    }

    private void syncAdminOnlinePlayer(Player player) {
        if (player == null || player.getSession() == null) {
            return;
        }
        try {
            ensureAdminOnlineSchema();
            int mapId = -1;
            int zoneId = -1;
            if (player.zone != null) {
                if (player.zone.map != null) {
                    mapId = player.zone.map.mapId;
                }
                zoneId = player.zone.zoneId;
            }
            LocalManager.executeUpdate(
                    "INSERT INTO admin_player_online (player_id,account_id,map_id,zone_id,last_seen) "
                            + "VALUES (?,?,?,?,NOW()) ON DUPLICATE KEY UPDATE account_id=VALUES(account_id),"
                            + "map_id=VALUES(map_id),zone_id=VALUES(zone_id),last_seen=NOW()",
                    player.id, player.getSession().userId, mapId, zoneId);
        } catch (Exception e) {
            logAdminOnlineSyncError(e);
        }
    }

    private void removeAdminOnlinePlayer(Player player) {
        if (player == null) {
            return;
        }
        try {
            ensureAdminOnlineSchema();
            LocalManager.executeUpdate("DELETE FROM admin_player_online WHERE player_id=?", player.id);
        } catch (Exception e) {
            logAdminOnlineSyncError(e);
        }
    }

    private void syncAdminOnlinePlayers() {
        long now = System.currentTimeMillis();
        if (now - lastAdminOnlineSyncMillis < ADMIN_ONLINE_SYNC_INTERVAL_MS) {
            return;
        }
        lastAdminOnlineSyncMillis = now;
        try {
            ensureAdminOnlineSchema();
            List<Player> snapshot = new ArrayList<>(players);
            for (Player player : snapshot) {
                syncAdminOnlinePlayer(player);
            }
            LocalManager.executeUpdate(
                    "DELETE FROM admin_player_online WHERE last_seen < DATE_SUB(NOW(), INTERVAL 2 MINUTE)");
        } catch (Exception e) {
            logAdminOnlineSyncError(e);
        }
    }

    private void logAdminOnlineSyncError(Exception e) {
        long now = System.currentTimeMillis();
        if (now - lastAdminOnlineErrorLogMillis >= ADMIN_ONLINE_ERROR_LOG_INTERVAL_MS) {
            lastAdminOnlineErrorLogMillis = now;
            Logger.logException(Client.class, e, "Không thể cập nhật trạng thái online cho admin");
        }
    }

    public Player getPlayerByID(int playerId) {
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            if (player != null && player.id == playerId) {
                return player;
            }
        }
        return null;
    }

    @Override
    public void run() {
        while (ServerManager.isRunning) {
            long st = System.currentTimeMillis();
            try {
                update();
            } catch (Exception e) {
                e.printStackTrace();
            }
            Functions.sleep(Math.max(1000 - (System.currentTimeMillis() - st), 10));
        }
    }

}
