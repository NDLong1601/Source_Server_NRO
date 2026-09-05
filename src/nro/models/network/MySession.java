package nro.models.network;

import java.net.Socket;

import nro.models.player.Player;
import nro.models.clan.ClanTerritoryService;
import nro.models.clan.ClanTreeService;
import nro.models.clan.ClanGiftService;
import nro.models.server.Controller;
import nro.models.data.DataGame;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MySession extends Session {

    private static final Map<String, AntiLogin> ANTILOGIN = new HashMap<>();
    private static final Map<String, ClientProfile> CLIENT_PROFILES = new ConcurrentHashMap<>();
    public Player player;

    public byte timeWait = 100;
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
    public boolean joinedGame;

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
        this.startSend();
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

    public void login(String username, String password) {
        AntiLogin al = ANTILOGIN.get(this.ipAddress);
        if (al == null) {
            al = new AntiLogin();
            ANTILOGIN.put(this.ipAddress, al);
        }
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
        if (this.player == null) {
            Player pl = null;
            try {
                long st = System.currentTimeMillis();
                this.uu = username;
                this.pp = password;
                pl = MrFinn.login(this, al);
                if (pl != null) {
                    // Some clients reconnect when switching accounts without
                    // sending setClientType again. Reuse the last profile for
                    // this local endpoint so icon/resource requests keep the
                    // correct zoom directory.
                    sendAssetVersionsIfNeeded();
                    this.timeWait = 0;
                    this.joinedGame = true;
                    pl.nPoint.calPoint();
                    pl.nPoint.setHp(pl.nPoint.hp);
                    pl.nPoint.setMp(pl.nPoint.mp);
                    pl.zone.addPlayer(pl);
                    if (pl.pet != null) {
                        pl.pet.nPoint.calPoint();
                        pl.pet.nPoint.setHp(pl.pet.nPoint.hp);
                        pl.pet.nPoint.setMp(pl.pet.nPoint.mp);
                    }

                    pl.setSession(this);
                    Client.gI().put(pl);
                    this.player = pl;
                    DataGame.sendVersionGame(this);
                    DataGame.sendDataItemBG(this);
                    Controller.gI().sendInfo(this);
                    // Logging back in while the saved location is map 153 does not
                    // pass through ClanTerritoryService.enterTerritory(). Send the
                    // snapshot only after sendMyClan so the client can associate it
                    // with the clan before drawing the shared tree.
                    if (ClanTerritoryService.gI().isTerritoryFor(pl, pl.zone)) {
                        ClanTreeService.gI().sendSnapshot(pl);
                    }
                    ClanGiftService.gI().deliverPending(pl);
                    Logger.warning("[" + TimeUtil.getCurrHour() + ":" + TimeUtil.getCurrMin() + "] - Player Login: "
                            + this.player.name + ": " + (System.currentTimeMillis() - st) + " ms\n");
                    if (this.player.notify != null && !this.player.notify.equals("null")
                            && !this.player.notify.isEmpty() && this.player.notify.length() > 0) {
                        Service.gI().sendThongBao(this.player, this.player.notify);
                        this.player.notify = null;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (pl != null) {
                    pl.dispose();
                }
            }
        }
    }
}
