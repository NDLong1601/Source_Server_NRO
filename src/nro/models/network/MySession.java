package nro.models.network;

import java.net.Socket;

import nro.models.player.Player;
import nro.models.clan.ClanTerritoryService;
import nro.models.clan.ClanTreeService;
import nro.models.clan.ClanGiftService;
import nro.models.server.Controller;
import nro.models.data.DataGame;
import nro.models.data.LocalManager;
import nro.models.database.MrFinn;
import nro.models.item.Item;
import java.io.IOException;
import nro.models.network.Message;
import nro.models.server.Client;
import nro.models.server.Maintenance;
import nro.models.server.Manager;
import nro.models.player_system.AntiLogin;
import nro.models.services.Service;
import nro.models.utils.Logger;
import nro.models.utils.TimeUtil;
import java.util.List;

public class MySession extends Session {

    private static final int MAX_IP_CACHE_ENTRIES = 10_000;
    private static final IpScopedCache<AntiLogin> ANTILOGIN
            = new IpScopedCache<>(MAX_IP_CACHE_ENTRIES, 10 * 60_000L);
    private static final IpScopedCache<ClientProfile> CLIENT_PROFILES
            = new IpScopedCache<>(MAX_IP_CACHE_ENTRIES, 30 * 60_000L);
    private final Object playerLifecycleLock = new Object();
    private Thread playerLifecycleStepOwner;
    private int playerLifecycleStepDepth;
    private boolean playerTeardownDeferred;
    public volatile Player player;

    public volatile byte timeWait = 100;
    public boolean sentKey;

    public static final byte[] KEYS = { 0 };
    public byte curR, curW;

    public String ipAddress;
    public boolean isAdmin;
    public int userId;
    public String uu;
    public String pp;

    public int typeClient;
    public byte zoomLevel;
    private volatile boolean clientInfoReady;
    private boolean assetVersionsSent;
    private boolean infinityCastleAssetsSent;

    public long lastTimeLogout;
    public volatile boolean joinedGame;

    public long lastTimeReadMessage;

    public boolean actived;

    public boolean check;

    public int goldBar;
    public long gold;
    public int eventPoint;
    public List<Item> itemsReward;
    public String dataReward;
    public boolean is_gift_box;
    public double bdPlayer;

    public int version;
    public int vnd;
    public int tongnap;
    public int vip;
    public int luotquay;

    public boolean finishUpdate;

    public MySession() {
        super();
        this.ipAddress = "127.0.0.1";
    }

    public MySession(Socket socket) {
        super(socket);
        ipAddress = socket.getInetAddress().getHostAddress();
        restoreClientProfile();
    }

    private static final class ClientProfile {

        private final int typeClient;
        private final byte zoomLevel;
        private final int version;

        private ClientProfile(int typeClient, byte zoomLevel, int version) {
            this.typeClient = typeClient;
            this.zoomLevel = zoomLevel;
            this.version = version;
        }
    }

    private void restoreClientProfile() {
        ClientProfile profile = CLIENT_PROFILES.get(this.ipAddress);
        if (profile != null && profile.zoomLevel >= 1 && profile.zoomLevel <= 4) {
            this.typeClient = profile.typeClient;
            this.zoomLevel = profile.zoomLevel;
            this.version = profile.version;
            this.clientInfoReady = true;
        }
    }

    public void rememberClientProfile() {
        if (this.zoomLevel >= 1 && this.zoomLevel <= 4) {
            CLIENT_PROFILES.put(this.ipAddress,
                    new ClientProfile(this.typeClient, this.zoomLevel, this.version));
        }
    }

    public void markClientInfoReady() {
        this.clientInfoReady = true;
    }

    public boolean isAssetReady() {
        return this.clientInfoReady && this.zoomLevel >= 1 && this.zoomLevel <= 4;
    }

    public synchronized boolean markAssetVersionsSent() {
        if (!this.isAssetReady() || this.assetVersionsSent) {
            return false;
        }
        this.assetVersionsSent = true;
        return true;
    }

    public synchronized boolean markInfinityCastleAssetsSent() {
        if (!this.isAssetReady() || this.infinityCastleAssetsSent) {
            return false;
        }
        this.infinityCastleAssetsSent = true;
        return true;
    }

    public void sendAssetVersionsIfNeeded() {
        if (this.markAssetVersionsSent()) {
            DataGame.sendSmallVersion(this);
            DataGame.sendBgItemVersion(this);
            DataGame.sendVersionRes(this);
        }
    }

    @Override
    public void sendKey() throws Exception {
        super.sendKey();
        if (this.sentKey() && this.isConnected()) {
            this.startSend();
        }
    }

    public void sendSessionKey() {
        Message msg = new Message(-27);
        try {
            msg.writer().writeByte(KEYS.length);
            msg.writer().writeByte(KEYS[0]);
            for (int i = 1; i < KEYS.length; i++) {
                msg.writer().writeByte(KEYS[i] ^ KEYS[i - 1]);
            }
            this.sendMessage(msg);
            msg.cleanup();
            sentKey = true;
        } catch (IOException e) {
        }
    }

    protected final boolean publishPlayerIfActive(Player candidate, Runnable publication) {
        if (candidate == null || publication == null) {
            return false;
        }
        synchronized (this.playerLifecycleLock) {
            if (this.player != null || !this.isConnected() || this.isClosed()) {
                return false;
            }
            candidate.setSession(this);
            this.player = candidate;
            publication.run();
            return true;
        }
    }

    protected final boolean runPlayerLifecycleStepIfOwned(Player expectedPlayer, Runnable action) {
        if (expectedPlayer == null || action == null) {
            return false;
        }
        final PlayerTeardown[] deferredTeardown = new PlayerTeardown[1];
        try {
            synchronized (this.playerLifecycleLock) {
                if (this.player != expectedPlayer || !this.isConnected() || this.isClosed()
                        || expectedPlayer.isDisposed()) {
                    return false;
                }
                Thread currentThread = Thread.currentThread();
                if (this.playerLifecycleStepOwner == null) {
                    this.playerLifecycleStepOwner = currentThread;
                } else if (this.playerLifecycleStepOwner != currentThread) {
                    return false;
                }
                this.playerLifecycleStepDepth++;
                try {
                    action.run();
                } finally {
                    this.playerLifecycleStepDepth--;
                    if (this.playerLifecycleStepDepth == 0) {
                        this.playerLifecycleStepOwner = null;
                        if (this.playerTeardownDeferred) {
                            deferredTeardown[0] = detachPlayerLocked();
                        }
                    }
                }
                return deferredTeardown[0] == null
                        && this.player == expectedPlayer
                        && this.isConnected()
                        && !this.isClosed()
                        && !expectedPlayer.isDisposed();
            }
        } finally {
            completePlayerTeardown(deferredTeardown[0]);
        }
    }

    protected void removePlayerOnClose(Player closingPlayer) {
        Client.gI().removePlayerFromSession(this, closingPlayer);
    }

    public void login(String username, String password) {
        AntiLogin al = ANTILOGIN.computeIfAbsent(this.ipAddress, k -> new AntiLogin());
        if (!al.canLogin()) {
            Service.gI().sendThongBaoOK(this, al.getNotifyCannotLogin());
            return;
        }

        if (Manager.LOCAL) {
            Service.gI().sendThongBaoOK(this, "Server này chỉ để lưu dữ liệu\nVui lòng qua server khác");
            return;
        }
        if (Maintenance.isRunning) {
            Service.gI().sendThongBaoOK(this, "Server đang trong thời gian bảo trì, vui lòng quay lại sau");
            return;
        }
        if (!this.isAdmin && Client.gI().getPlayers().size() >= Manager.MAX_PLAYER) {
            Service.gI().sendThongBaoOK(this, "Máy chủ hiện đang quá tải, "
                    + "cư dân vui lòng di chuyển sang máy chủ khác.");
            return;
        }
        if (this.player == null && this.isConnected() && !this.isClosed()) {
            Player pl = null;
            try {
                long st = System.currentTimeMillis();
                this.uu = username;
                this.pp = password;
                pl = MrFinn.login(this, al);
                if (pl != null) {
                    Player loginPlayer = pl;
                    boolean published = publishPlayerIfActive(loginPlayer, () -> {
                        this.timeWait = 0;
                        this.joinedGame = true;
                        loginPlayer.nPoint.calPoint();
                        loginPlayer.nPoint.setHp(loginPlayer.nPoint.hp);
                        loginPlayer.nPoint.setMp(loginPlayer.nPoint.mp);
                        loginPlayer.zone.addPlayer(loginPlayer);
                        if (loginPlayer.pet != null) {
                            loginPlayer.pet.nPoint.calPoint();
                            loginPlayer.pet.nPoint.setHp(loginPlayer.pet.nPoint.hp);
                            loginPlayer.pet.nPoint.setMp(loginPlayer.pet.nPoint.mp);
                        }
                        Client.gI().put(loginPlayer);
                    });
                    if (!published) {
                        pl.dispose();
                        return;
                    }
                    if (!runPlayerLifecycleStepIfOwned(loginPlayer, this::sendAssetVersionsIfNeeded)) {
                        return;
                    }

                    if (!runPlayerLifecycleStepIfOwned(loginPlayer,
                            () -> nro.models.ledger.MoneyLedgerService.gI()
                                    .recoverPendingDeliveriesOnLogin(loginPlayer))) {
                        return;
                    }
                    if (pl.persistenceQuarantined) {
                        this.close(SessionCloseCause.INTERNAL_ERROR);
                        return;
                    }
                    if (!runPlayerLifecycleStepIfOwned(loginPlayer, () -> {
                        DataGame.sendVersionGame(this);
                        DataGame.sendDataItemBG(this);
                    })) {
                        return;
                    }
                    if (!runPlayerLifecycleStepIfOwned(loginPlayer, () -> Controller.gI().sendInfo(this))) {
                        return;
                    }
                    // Logging back in while the saved location is map 153 does not
                    // pass through ClanTerritoryService.enterTerritory(). Send the
                    // snapshot only after sendMyClan so the client can associate it
                    // with the clan before drawing the shared tree.
                    if (!runPlayerLifecycleStepIfOwned(loginPlayer, () -> {
                        if (ClanTerritoryService.gI().isTerritoryFor(loginPlayer, loginPlayer.zone)) {
                            ClanTreeService.gI().sendSnapshot(loginPlayer);
                        }
                        ClanGiftService.gI().deliverPending(loginPlayer);
                    })) {
                        return;
                    }
                    if (!runPlayerLifecycleStepIfOwned(loginPlayer, () -> {
                        Logger.warning("[" + TimeUtil.getCurrHour() + ":" + TimeUtil.getCurrMin()
                                + "] - Player Login: " + loginPlayer.name + ": "
                                + (System.currentTimeMillis() - st) + " ms\n");
                        if (loginPlayer.notify != null && !loginPlayer.notify.equals("null")
                                && !loginPlayer.notify.isEmpty()) {
                            Service.gI().sendThongBao(loginPlayer, loginPlayer.notify);
                            loginPlayer.notify = null;
                        }
                    })) {
                        return;
                    }
                }
            } catch (Exception e) {
                Logger.logException(MySession.class, e, "Login finalization failed");
                if (pl != null && this.player == pl) {
                    this.close(SessionCloseCause.INTERNAL_ERROR);
                } else if (pl != null) {
                    pl.dispose();
                }
            }
        }
    }

    @Override
    protected void onSessionClosing(SessionCloseCause cause) {
        try {
            PlayerTeardown teardown;
            synchronized (this.playerLifecycleLock) {
                if (this.playerLifecycleStepOwner == Thread.currentThread()
                        && this.playerLifecycleStepDepth > 0) {
                    this.playerTeardownDeferred = true;
                    return;
                }
                teardown = detachPlayerLocked();
            }
            completePlayerTeardown(teardown);
        } finally {
            this.uu = null;
            this.pp = null;
        }
    }

    private PlayerTeardown detachPlayerLocked() {
        Player closingPlayer = this.player;
        boolean updateLogoutTimestamp = this.joinedGame;
        this.player = null;
        this.joinedGame = false;
        this.playerTeardownDeferred = false;
        return new PlayerTeardown(closingPlayer, updateLogoutTimestamp);
    }

    private void completePlayerTeardown(PlayerTeardown teardown) {
        if (teardown == null) {
            return;
        }
        try {
            if (teardown.player != null) {
                removePlayerOnClose(teardown.player);
            }
        } catch (Throwable error) {
            Logger.error("[SESSION] event=player_teardown_failed sessionId=" + this.getID()
                    + " errorType=" + error.getClass().getSimpleName() + "\n");
        }
        if (teardown.updateLogoutTimestamp) {
            try {
                LocalManager.executeUpdate("update account set last_time_logout = ? where id = ?",
                        new java.sql.Timestamp(System.currentTimeMillis()), this.userId);
            } catch (Exception e) {
                Logger.logException(MySession.class, e);
            }
        }
    }

    private static final class PlayerTeardown {

        private final Player player;
        private final boolean updateLogoutTimestamp;

        private PlayerTeardown(Player player, boolean updateLogoutTimestamp) {
            this.player = player;
            this.updateLogoutTimestamp = updateLogoutTimestamp;
        }
    }
}
