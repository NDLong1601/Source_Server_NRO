package nro.models.matches;
import nro.models.consts.ConstNpc;
import nro.models.map.Zone;
import nro.models.player.Player;
import nro.models.network.Message;
import nro.models.server.Client;
import nro.models.map.service.NpcService;
import nro.models.services.Service;
import nro.models.map.service.ChangeMapService;
import nro.models.utils.Util;
import java.io.IOException;

public class PVPService {

    private static final int[] GOLD_CHALLENGE = {1000000, 10000000, 100000000};
    private final String[] optionsGoldChallenge;
    private static final byte OPEN_GOLD_SELECT = 0;
    private static final byte ACCEPT_PVP = 1;

    private static PVPService instance;

    public static PVPService gI() {
        if (instance == null) {
            instance = new PVPService();
        }
        return instance;
    }

    public PVPService() {
        this.optionsGoldChallenge = new String[GOLD_CHALLENGE.length];
        for (int i = 0; i < GOLD_CHALLENGE.length; i++) {
            this.optionsGoldChallenge[i] = Util.numberToMoney(GOLD_CHALLENGE[i]) + " vàng";
        }
    }

    //**************************************************************************THÁCH ĐẤU
    public void controllerThachDau(Player player, Message message) {
        try {
            byte action = message.reader().readByte();
            byte type = message.reader().readByte();
            int playerId = message.reader().readInt();
            Player plMap = player.zone.getPlayerInMap(playerId);
            switch (type) {
                case 3:
                    switch (action) {
                        case OPEN_GOLD_SELECT:
                            if (Client.gI().getPlayer(playerId) == null) {
                                if (plMap != null && plMap.isBoss) {
                                    NpcService.gI().createMenuConMeo(player, ConstNpc.IGNORE_MENU,
                                            -1, plMap.name + " (sức mạnh " + Util.numberToMoney(plMap.nPoint.power)
                                            + ")\nBạn muốn cược bao nhiêu vàng?",
                                            this.optionsGoldChallenge);
                                    return;
                                }
                                Service.gI().sendThongBao(player, "Đối thủ đã thoát game");
                                return;
                            }
                            openSelectGold(player, plMap);
                            break;
                        case ACCEPT_PVP:
                            acceptPVP(player);
                            break;
                    }
                    break;
                case 4:
                    switch (action) {
                        case OPEN_GOLD_SELECT:
                            if (Client.gI().getPlayer(playerId) == null) {
                                Service.gI().sendThongBao(player, "Đối thủ đã thoát game");
                                return;
                            }
                            sendInvitePVP(player, plMap);
                            break;
                        case ACCEPT_PVP:
                            acceptPVP2(player);
                            break;
                    }
                    break;
            }
        } catch (IOException ex) {

        }
    }

    private void openSelectGold(Player pl, Player plMap) {
        if (pl == null || plMap == null) {
            return;
        }
        if (pl.pvp != null || plMap.pvp != null) {
            Service.gI().hideWaitDialog(pl);
            Service.gI().sendThongBao(pl, "Đang giao đấu không thể mời.");
            return;
        }
        pl.idMark.setIdPlayThachDau(plMap.id);
        NpcService.gI().createMenuConMeo(pl, ConstNpc.MAKE_MATCH_PVP,
                -1, plMap.name + " (sức mạnh " + Util.numberToMoney(plMap.nPoint.power)
                + ")\nBạn muốn cược bao nhiêu vàng?",
                this.optionsGoldChallenge);
    }

    public void sendInvitePVP(Player pl, byte selectGold) {
        if (pl == null) {
            return;
        }
        // Chưa thể thách đấu hoặc tập luyện lúc này
        Player plMap = pl.zone.getPlayerInMap(pl.idMark.getIdPlayThachDau());
        if (plMap == null) {
            Service.gI().hideWaitDialog(pl);
            if (Client.gI().getPlayer(pl.idMark.getIdPlayThachDau()) == null) {
                Service.gI().sendThongBao(pl, "Đối thủ đã thoát game");
                return;
            }
            Service.gI().sendThongBao(pl, "Đối thủ đã rời map");
            return;
        }
        if (plMap.getSession() == null || !plMap.getSession().actived) {
            Service.gI().sendThongBao(pl, "Đối thủ chưa kích hoạt tài khoản");
            return;
        }
        int goldThachDau = GOLD_CHALLENGE[selectGold];
        if (pl.inventory.gold < goldThachDau) {
            Service.gI().sendThongBao(pl, "Bạn chỉ có " + pl.inventory.gold + " vàng, không đủ tiền cược");
            return;
        }
        if (plMap.inventory.gold < goldThachDau) {
            Service.gI().sendThongBao(pl, "Đối thủ chỉ có " + plMap.inventory.gold + " vàng, không đủ tiền cược");
            return;
        }

        plMap.idMark.setIdPlayThachDau(pl.id);
        plMap.idMark.setGoldThachDau(goldThachDau);

        //Gửi message
        Message msg = null;
        try {
            msg = new Message(-59);
            msg.writer().writeByte(3);
            msg.writer().writeInt((int) pl.id);
            msg.writer().writeInt(goldThachDau);
            msg.writer().writeUTF(pl.name + " (sức mạnh " + Util.numberToMoney(pl.nPoint.getFullTN()) + ") muốn thách đấu bạn với mức cược " + goldThachDau);
            plMap.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
        }
    }

    public void sendInvitePVP(Player pl, Player plMap) {
        if (pl == null) {
            return;
        }
        if (plMap == null) {
            Service.gI().sendThongBao(pl, "Đối thủ đã rời map");
            return;
        }
        if (plMap.getSession() == null || !plMap.getSession().actived) {
            Service.gI().sendThongBao(pl, "Đối thủ chưa kích hoạt tài khoản");
            return;
        }

        if (Service.gI().getCurrLevel(pl) != Service.gI().getCurrLevel(plMap)) {
            Service.gI().sendThongBao(pl, "Luyện tập cùng đối thủ ở cấp " + Service.gI().getCurrStrLevel(pl) + " để đạt hiệu quả tốt nhất");
        }

        plMap.idMark.setIdPlayThachDau(pl.id);

        //Gửi message
        Message msg = null;
        try {
            msg = new Message(-59);
            msg.writer().writeByte(4);
            msg.writer().writeInt((int) pl.id);
            msg.writer().writeInt(0);
            msg.writer().writeUTF(pl.name + " (sức mạnh " + Util.numberToMoney(pl.nPoint.power) + ") muốn luyện tập với bạn");
            plMap.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
        }
    }

    private void acceptPVP(Player pl) {
        if (pl == null) {
            return;
        }
        Player plMap = pl.zone.getPlayerInMap(pl.idMark.getIdPlayThachDau());

        if (plMap == null) {
            Service.gI().hideWaitDialog(pl);
            if (Client.gI().getPlayer(pl.idMark.getIdPlayThachDau()) == null) {
                Service.gI().sendThongBao(pl, "Đối thủ đã thoát game");
                return;
            }
            Service.gI().sendThongBao(pl, "Đối thủ đã rời map");
            return;
        }
        if (pl.pvp != null || plMap.pvp != null) {
            Service.gI().hideWaitDialog(pl);
            Service.gI().sendThongBao(pl, "Đang giao đấu không thể mời.");
            return;
        }
        int goldThachDau = pl.idMark.getGoldThachDau();

        if (pl.inventory.gold < goldThachDau) {
            Service.gI().hideWaitDialog(pl);
            Service.gI().sendThongBao(pl, "Bạn chỉ có " + pl.inventory.gold + " vàng, không đủ tiền cược");
            return;
        }
        if (plMap.inventory.gold < goldThachDau) {
            Service.gI().hideWaitDialog(pl);
            Service.gI().sendThongBao(pl, "Đối thủ chỉ có " + plMap.inventory.gold + " vàng, không đủ tiền cược");
            return;
        }
        ThachDau thachDau = new ThachDau(pl, plMap, goldThachDau);
    }

    private void acceptPVP2(Player pl) {
        if (pl == null) {
            return;
        }
        Player plMap = pl.zone.getPlayerInMap(pl.idMark.getIdPlayThachDau());
        if (plMap == null) {
            Service.gI().hideWaitDialog(pl);
            Service.gI().sendThongBao(pl, "Đối thủ đã rời map");
            return;
        }
        if (pl.pvp != null || plMap.pvp != null) {
            Service.gI().hideWaitDialog(pl);
            Service.gI().sendThongBao(pl, "Đang giao đấu không thể mời.");
            return;
        }
        LuyenTap luyenTap = new LuyenTap(pl, plMap);
    }

    //**************************************************************************TRẢ THÙ
    public void openSelectRevenge(Player pl, long idEnemy) {
        Player enemy = Client.gI().getPlayer(idEnemy);
        if (enemy == null) {
            Service.gI().hideWaitDialog(pl);
            Service.gI().sendThongBao(pl, "Đang offline");
            return;
        }

        pl.idMark.setIdEnemy(idEnemy);
        if (!Util.canDoWithTime(pl.idMark.getLastTimeRevenge(), 300000)) {
            acceptRevenge(pl);
            return;
        }
        NpcService.gI().createMenuConMeo(pl, ConstNpc.REVENGE,
                -1, "Bạn muốn đến ngay chỗ hắn, phí là 1 ngọc\nvà được tìm thoải mái trong 5 phút nhé", "OK", "Từ chối");
    }

    public void acceptRevenge(Player pl) {
        if (Util.canDoWithTime(pl.idMark.getLastTimeRevenge(), 300000)) {
            if (pl.inventory.getGem() < 1) {
                Service.gI().sendThongBao(pl, "Bạn không đủ ngọc, còn thiếu 1 ngọc nữa");
                return;
            }
            pl.idMark.setLastTimeRevenge(System.currentTimeMillis());
            pl.inventory.subGem(1);
            Service.gI().sendMoney(pl);
        }
        Player enemy = Client.gI().getPlayer(pl.idMark.getIdEnemy());
        if (enemy == null) {
            Service.gI().hideWaitDialog(pl);
            Service.gI().sendThongBao(pl, "Đang offline");
            return;
        }
        if (pl.pvp != null || enemy.pvp != null) {
            Service.gI().hideWaitDialog(pl);
            Service.gI().sendThongBao(pl, "Chưa thể đến lúc này, vui lòng thử lại sau ít phút");
            return;
        }
        Zone mapGo = enemy.zone;
        if ((mapGo = ChangeMapService.gI().checkMapCanJoin(pl, mapGo)) == null || mapGo.isFullPlayer()) {
            Service.gI().hideWaitDialog(pl);
            Service.gI().sendThongBao(pl, "Chưa thể đến lúc này, vui lòng thử lại sau ít phút");
            return;
        }
        TraThu traThu = new TraThu(pl, enemy);
    }
}
