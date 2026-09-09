package nro.models.server;

import nro.models.data.LocalManager;
import nro.models.database.PlayerDAO;
import lombok.Getter;
import nro.models.map.ItemMap;
import nro.models.player.Player;
import nro.models.network.SessionManager;
import nro.models.interfaces.ISession;
import nro.models.network.MySession;
import nro.models.network.SessionCloseCause;
import nro.models.services.Service;
import nro.models.map.service.ChangeMapService;
import nro.models.services.shenron.SummonDragon;
import nro.models.services_func.TransactionService;
import nro.models.services_dungeon.NgocRongNamecService;
import nro.models.utils.Functions;
import nro.models.utils.Logger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import nro.models.services.shenron.SummonDragonNamek;

public class Client implements Runnable {

    private static Client instance;
    private static final long ADMIN_ONLINE_SYNC_INTERVAL_MS = 5000L;
    private static final long ADMIN_ONLINE_ERROR_LOG_INTERVAL_MS = 60000L;

    private final Object registryLock = new Object();
    private final Map<Long, Player> players_id = new HashMap<>();
    private final Map<Integer, Player> players_userId = new HashMap<>();
    private final Map<String, Player> players_name = new HashMap<>();
    private final List<Player> players = new ArrayList<>();
    private boolean adminOnlineSchemaReady;
    private long lastAdminOnlineSyncMillis;
    private long lastAdminOnlineErrorLogMillis;
    private final ExecutorService updateExecutor;

    private Client() {
        this.updateExecutor = Executors.newSingleThreadExecutor(new NamedThreadFactory("ClientRegistry", false));
        this.updateExecutor.submit(this);
    }

    public static synchronized Client gI() {
        if (instance == null) {
            instance = new Client();
        }
        return instance;
    }

    public void put(Player player) {
        if (player == null) {
            return;
        }
        List<Player> displacedPlayers = putInMemory(player);
        for (Player displaced : displacedPlayers) {
            MySession displacedSession = displaced.getSession();
            if (displacedSession != null && displacedSession != player.getSession()) {
                displacedSession.close(SessionCloseCause.DUPLICATE_LOGIN);
            }
        }
        syncAdminOnlinePlayer(player);
    }

    void putForTesting(Player player) {
        if (player == null) {
            return;
        }
        putInMemory(player);
    }

    void removeForTesting(Player player) {
        if (player == null) {
            return;
        }
        removeFromMemory(player);
    }

    private List<Player> putInMemory(Player player) {
        synchronized (this.registryLock) {
            List<Player> displaced = new ArrayList<>();
            int accountId = player.getSession() != null ? player.getSession().userId : Integer.MIN_VALUE;
            for (Player existing : this.players) {
                if (existing == null || existing == player) {
                    continue;
                }
                MySession existingSession = existing.getSession();
                boolean sameAccount = accountId != Integer.MIN_VALUE && existingSession != null
                        && existingSession.userId == accountId;
                if (existing.id == player.id || java.util.Objects.equals(existing.name, player.name) || sameAccount) {
                    displaced.add(existing);
                }
            }
            for (Player existing : displaced) {
                removeFromMemoryLocked(existing);
            }
            this.players_id.put(player.id, player);
            this.players_name.put(player.name, player);
            if (accountId != Integer.MIN_VALUE) {
                this.players_userId.put(accountId, player);
            }
            if (!this.players.contains(player)) {
                this.players.add(player);
            }
            return displaced;
        }
    }

    private boolean removeFromMemory(Player player) {
        synchronized (this.registryLock) {
            return removeFromMemoryLocked(player);
        }
    }

    private boolean removeFromMemoryLocked(Player player) {
        int beforeSize = this.players.size();
        boolean removedId = this.players_id.entrySet().removeIf(entry -> entry.getValue() == player);
        boolean removedName = this.players_name.entrySet().removeIf(entry -> entry.getValue() == player);
        boolean removedUser = this.players_userId.entrySet().removeIf(entry -> entry.getValue() == player);
        this.players.remove(player);
        return removedId || removedName || removedUser || this.players.size() != beforeSize;
    }

    /**
     * Keeps the online-name lookup in sync when a connected player changes
     * their character name. Player IDs and account IDs are unaffected.
     */
    public void updatePlayerName(Player player, String previousName) {
        if (player == null || previousName == null) {
            return;
        }
        synchronized (this.registryLock) {
            this.players_name.remove(previousName, player);
            this.players_name.put(player.name, player);
        }
    }

    private void remove(Player player) {
        if (player == null) {
            return;
        }
        executeRemovalLifecycle(player,
                () -> removeFromRegistriesAndTeardown(player),
                () -> saveRemovedPlayer(player));
    }

    /**
     * Defines the disconnect lifecycle boundary used by production removal and
     * deterministic regression tests. Claims are closed before cleanup starts,
     * and persistence is attempted even when cleanup throws.
     */
    static void executeRemovalLifecycle(Player player, Runnable cleanup, Runnable save) {
        if (player == null || !player.beginRemoval()) {
            return;
        }
        if (player.achievement != null) {
            try {
                player.achievement.closeClaims();
            } catch (Exception e) {
                Logger.error("[Client.remove] closeClaims failed for player=" + player.id + ": " + e);
            }
        }
        try {
            try {
                cleanup.run();
            } catch (RuntimeException e) {
                Logger.logException(Client.class, e,
                        "Unexpected removal cleanup failure for player " + player.id);
            } finally {
                save.run();
            }
        } finally {
            player.dispose();
        }
    }

    private void removeFromRegistriesAndTeardown(Player player) {
        removeFromMemory(player);
        Player replacement = getPlayer(player.id);
        if (replacement == null) {
            removeAdminOnlinePlayer(player);
        } else if (replacement != player) {
            syncAdminOnlinePlayer(replacement);
        }

        synchronized (player.getLifecycleLock()) {
            try {
                player.mapIdBeforeLogout = player.zone != null && player.zone.map != null
                        ? player.zone.map.mapId : -1;
                if (player.idNRNM != -1 && player.zone != null && player.location != null) {
                    ItemMap itemMap = new ItemMap(player.zone, player.idNRNM, 1,
                            player.location.x, player.location.y, -1);
                    Service.gI().dropItemMap(player.zone, itemMap);
                    NgocRongNamecService.gI().pNrNamec[player.idNRNM - 353] = "";
                    NgocRongNamecService.gI().idpNrNamec[player.idNRNM - 353] = -1;
                    player.idNRNM = -1;
                }
            } catch (Exception e) {
                Logger.error("[Client.remove] mapId/idNRNM cleanup failed for player=" + player.id + ": " + e);
            }
            try {
                ChangeMapService.gI().exitMap(player);
            } catch (Exception e) {
                Logger.error("[Client.remove] exitMap failed for player=" + player.id + ": " + e);
            }
            try {
                TransactionService.gI().cancelTrade(player);
            } catch (Exception e) {
                Logger.error("[Client.remove] cancelTrade failed for player=" + player.id + ": " + e);
            }
            try {
                if (player.clan != null) {
                    player.clan.removeMemberOnline(null, player);
                }
            } catch (Exception e) {
                Logger.error("[Client.remove] clan.removeMemberOnline failed for player=" + player.id + ": " + e);
            }
            try {
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
            } catch (Exception e) {
                Logger.error("[Client.remove] dragon/shenron disconnect failed for player=" + player.id + ": " + e);
            }
            try {
                if (player.mobMe != null) {
                    player.mobMe.mobMeDie();
                }
            } catch (Exception e) {
                Logger.error("[Client.remove] mobMe.mobMeDie failed for player=" + player.id + ": " + e);
            }
            try {
                if (player.pet != null) {
                    if (player.pet.mobMe != null) {
                        player.pet.mobMe.mobMeDie();
                    }
                    ChangeMapService.gI().exitMap(player.pet);
                }
            } catch (Exception e) {
                Logger.error("[Client.remove] pet teardown failed for player=" + player.id + ": " + e);
            }
        }
    }

    private void saveRemovedPlayer(Player player) {
        synchronized (player.getLifecycleLock()) {
            try {
                PlayerDAO.updatePlayer(player);
            } catch (Exception e) {
                Logger.error("[Client.remove] PlayerDAO.updatePlayer failed for player=" + player.id + ": " + e);
            }
        }
    }

    public void removePlayerFromSession(MySession session, Player player) {
        this.remove(player);
    }

    public void kickSession(MySession session) {
        kickSession(session, SessionCloseCause.ADMIN_KICK);
    }

    public void kickSession(MySession session, SessionCloseCause cause) {
        if (session != null) {
            session.close(cause != null ? cause : SessionCloseCause.ADMIN_KICK);
        }
    }

    public Player getPlayer(long playerId) {
        synchronized (this.registryLock) {
            return this.players_id.get(playerId);
        }
    }

    public Player getPlayerByUser(int userId) {
        synchronized (this.registryLock) {
            return this.players_userId.get(userId);
        }
    }

    public Player getPlayer(String name) {
        synchronized (this.registryLock) {
            return this.players_name.get(name);
        }
    }

    public List<Player> getPlayers() {
        synchronized (this.registryLock) {
            return new ArrayList<>(this.players);
        }
    }

    public void close() {
        close(SessionCloseCause.SERVER_SHUTDOWN);
    }

    public void close(SessionCloseCause cause) {
        List<Player> snapshot = getPlayers();
        Logger.log(Logger.YELLOW, "BEGIN KICK OUT SESSION " + snapshot.size() + "\n");
        for (Player pl : snapshot) {
            if (pl != null && pl.getSession() != null) {
                this.kickSession(pl.getSession(), cause);
            } else if (pl != null) {
                this.remove(pl);
            }
        }
        this.updateExecutor.shutdownNow();
        Logger.success("SUCCESSFUL\n");
    }

    private void update() {
        for (ISession s : SessionManager.gI().getSessions()) {
            if (s instanceof MySession session) {
                if (session.timeWait > 0) {
                    session.timeWait--;
                    if (session.timeWait == 0) {
                        session.close(nro.models.network.SessionCloseCause.LOGIN_TIMEOUT);
                    }
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
            List<Player> snapshot = getPlayers();
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
        return getPlayer((long) playerId);
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
