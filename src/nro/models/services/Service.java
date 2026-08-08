package nro.models.services;

import nro.models.map.service.MapService;
import nro.models.map.service.NpcService;
import nro.models.utils.Functions;
import nro.models.data.LocalManager;
import nro.models.consts.ConstNpc;
import nro.models.consts.ConstPlayer;
import nro.models.utils.FileIO;
import nro.models.data.DataGame;
import nro.models.boss.BossData;
import nro.models.boss.nhan_ban.NhanBan;
import nro.models.boss.luyen_tap_tu_dong.TrainingBoss;
import nro.models.consts.ConstAchievement;
import nro.models.database.MrBlue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import nro.models.item.Item;
import nro.models.map.ItemMap;
import nro.models.mob.Mob;
import nro.models.player.Pet;
import nro.models.item.Item.ItemOption;
import java.io.DataOutputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import nro.models.Bot.BotAttackplayer;
import nro.models.database.PlayerDAO;
import nro.models.managers.MyClanTopBanDoKhoBau;
import nro.models.managers.TopBanDoKhoBau;
import nro.models.managers.TopConDuongRanDoc;
import nro.models.managers.TopKhiGasHuyDiet;
import nro.models.map.Zone;
import nro.models.player.Player;
import nro.models.network.MySession;
import nro.models.skill.Skill;
import nro.models.network.Message;
import nro.models.server.Client;
import nro.models.map.service.ChangeMapService;
import nro.models.utils.Logger;
import nro.models.utils.TimeUtil;
import nro.models.utils.Util;
import nro.models.map.MaBuHold;
import nro.models.matches.TOP;
import nro.models.mob_bigboss.GauTuongCuop;
import nro.models.npc.NonInteractiveNPC;
import nro.models.npc.Npc;
import nro.models.npc_list.DuaHau;
import nro.models.player_system.Template.Part;
import nro.models.server.Manager;

/**
 *
 * @author By Mr Blue
 *
 */
public class Service {

    public static final int[] flagTempId = {363, 364, 365, 366, 367, 368, 369, 370, 371, 519, 520, 747};
    public static final int[] flagIconId = {2761, 2330, 2323, 2327, 2326, 2324, 2329, 2328, 2331, 4386, 4385, 2325};

    private static Service instance;

    public static Service gI() {
        if (instance == null) {
            instance = new Service();
        }
        return instance;
    }

    public void showYourNumber(Player player, String Number, String result, String finish, int type) {
        Message msg = null;
        try {
            msg = new Message(-126);
            msg.writer().writeByte(type);
            if (type == 0) {
                msg.writer().writeUTF(Number);
            } else if (type == 1) {
                msg.writer().writeByte(type);
                msg.writer().writeUTF(result);
                msg.writer().writeUTF(finish);
            }
            player.sendMessage(msg);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (msg != null) {
                msg.cleanup();
                msg = null;
            }
        }
    }

    public void SendImgSkill9(short SkillId, int IdAnhSKill) {
        Message msg = new Message(62);
        DataOutputStream ds = msg.writer();
        try {
            ds.writeShort(SkillId);
            ds.writeByte(1);
            ds.writeByte(IdAnhSKill);
            ds.flush();
            Service.gI().sendMessAllPlayer(msg);
            msg.cleanup();
        } catch (Exception e) {
        }
    }

    public void sendPetFollow(Player player, short smallId) {
        Message msg;
        try {
            msg = new Message(31);
            msg.writer().writeInt((int) player.id);
            if (smallId == 0) {
                msg.writer().writeByte(0);
            } else {
                msg.writer().writeByte(1);
                msg.writer().writeShort(smallId);
                msg.writer().writeByte(1);
                int[] fr = new int[]{0, 1, 2, 3, 4, 5, 6, 7};
                msg.writer().writeByte(fr.length);
                for (int i = 0; i < fr.length; i++) {
                    msg.writer().writeByte(fr[i]);
                }
                msg.writer().writeShort(smallId == 15067 ? 65 : 75);
                msg.writer().writeShort(smallId == 15067 ? 65 : 75);
            }
            sendMessAllPlayerInMap(player, msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendBadgesPlayer(Player player, int sec, int idImg) {
        Message msg;
        try {
            msg = new Message(24);
            msg.writer().writeByte(2);
            msg.writer().writeInt((int) player.id);
            msg.writer().writeByte(sec);
            msg.writer().writeShort(idImg);
            Service.gI().sendMessAllPlayerInMap(player, msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendChibi(Player player) {
        short smallId = (short) (player.effectSkill.isChibi ? player.typeChibi + 5000 : 0);
        if (!player.effectSkill.isChibi) {
            sendPetFollow(player, smallId);
            return;
        }
        Message msg;
        try {
            msg = new Message(31);
            msg.writer().writeInt((int) player.id);
            if (smallId == 0) {
                msg.writer().writeByte(0);
            } else {
                msg.writer().writeByte(1);
                msg.writer().writeShort(smallId);
                msg.writer().writeByte(1);
                int[] fr = new int[]{0, 1, 2};
                msg.writer().writeByte(fr.length);
                for (int i = 0; i < fr.length; i++) {
                    msg.writer().writeByte(fr[i]);
                }
                msg.writer().writeShort(32);
                msg.writer().writeShort(32);
            }
            sendMessAllPlayerInMap(player, msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendHaveChibiFollowToAllMap(Player pl) {
        if (pl.zone != null) {
            for (Player plMap : pl.zone.getPlayers()) {
                if (plMap.isPl()) {
                    sendChibiFollowToMe(plMap, pl);
                }
            }
        }
    }

    public void ClosePanel(Player pl) {
        Message msg = null;
        try {
            msg = new Message(-86);
            msg.writer().writeByte(7);
            pl.sendMessage(msg);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public void sendChibiFollowToMe(Player me, Player pl) {
        short smallId = (short) (pl.effectSkill.isChibi ? pl.typeChibi + 5000 : 0);
        Message msg;
        try {
            msg = new Message(31);
            msg.writer().writeInt((int) pl.id);
            msg.writer().writeByte(1);
            msg.writer().writeShort(smallId);
            msg.writer().writeByte(1);
            int[] fr = new int[]{0, 1, 2};
            msg.writer().writeByte(fr.length);
            for (int i = 0; i < fr.length; i++) {
                msg.writer().writeByte(fr[i]);
            }
            msg.writer().writeShort(32);
            msg.writer().writeShort(32);
            me.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void showListTop(Player player, List<TOP> tops) {
        Message msg;
        try {
            msg = new Message(-96);
            msg.writer().writeByte(0);
            msg.writer().writeUTF("Bảng Xếp Hạng");
            msg.writer().writeByte(tops.size());

            for (int i = 0; i < tops.size(); i++) {
                TOP top = tops.get(i);
                Player pl = MrBlue.loadById(top.getId_player());

                if (pl == null) {
                    continue;
                }

                msg.writer().writeInt(i + 1);
                msg.writer().writeInt((int) pl.id);
                msg.writer().writeShort(pl.getHead());
                if (player.getSession().version > 214) {
                    msg.writer().writeShort(-1);
                }
                msg.writer().writeShort(pl.getBody());
                msg.writer().writeShort(pl.getLeg());
                msg.writer().writeUTF(pl.name);
                msg.writer().writeUTF(top.getInfo1());
                msg.writer().writeUTF(top.getInfo2());
            }

            player.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void showListTopTraiDat(Player player, List<TOP> tops) {
        Message msg;
        try {
            List<TOP> filteredTops = new ArrayList<>();
            for (TOP top : tops) {
                Player pl = MrBlue.loadById(top.getId_player());
                if (pl != null && pl.gender == 0) {
                    filteredTops.add(top);
                }
            }

            msg = new Message(-96);
            msg.writer().writeByte(0);
            msg.writer().writeUTF("Bảng Xếp Hạng");
            msg.writer().writeByte(filteredTops.size());
            for (int i = 0; i < filteredTops.size(); i++) {
                TOP top = filteredTops.get(i);
                Player pl = MrBlue.loadById(top.getId_player());
                msg.writer().writeInt(i + 1);
                msg.writer().writeInt((int) pl.id);
                msg.writer().writeShort(pl.getHead());
                if (player.getSession().version > 214) {
                    msg.writer().writeShort(-1);
                }
                msg.writer().writeShort(pl.getBody());
                msg.writer().writeShort(pl.getLeg());
                msg.writer().writeUTF(pl.name);
                msg.writer().writeUTF(top.getInfo1());
                msg.writer().writeUTF(top.getInfo2());
            }
            player.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void showListTopNamek(Player player, List<TOP> tops) {
        Message msg;
        try {
            List<TOP> filteredTops = new ArrayList<>();
            for (TOP top : tops) {
                Player pl = MrBlue.loadById(top.getId_player());
                if (pl != null && pl.gender == 1) {
                    filteredTops.add(top);
                }
            }

            msg = new Message(-96);
            msg.writer().writeByte(0);
            msg.writer().writeUTF("Bảng Xếp Hạng");
            msg.writer().writeByte(filteredTops.size());
            for (int i = 0; i < filteredTops.size(); i++) {
                TOP top = filteredTops.get(i);
                Player pl = MrBlue.loadById(top.getId_player());
                msg.writer().writeInt(i + 1);
                msg.writer().writeInt((int) pl.id);
                msg.writer().writeShort(pl.getHead());
                if (player.getSession().version > 214) {
                    msg.writer().writeShort(-1);
                }
                msg.writer().writeShort(pl.getBody());
                msg.writer().writeShort(pl.getLeg());
                msg.writer().writeUTF(pl.name);
                msg.writer().writeUTF(top.getInfo1());
                msg.writer().writeUTF(top.getInfo2());
            }
            player.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void showListTopXayda(Player player, List<TOP> tops) {
        Message msg;
        try {
            List<TOP> filteredTops = new ArrayList<>();
            for (TOP top : tops) {
                Player pl = MrBlue.loadById(top.getId_player());
                if (pl != null && pl.gender == 2) {
                    filteredTops.add(top);
                }
            }

            msg = new Message(-96);
            msg.writer().writeByte(0);
            msg.writer().writeUTF("Bảng Xếp Hạng");
            msg.writer().writeByte(filteredTops.size());
            for (int i = 0; i < filteredTops.size(); i++) {
                TOP top = filteredTops.get(i);
                Player pl = MrBlue.loadById(top.getId_player());
                msg.writer().writeInt(i + 1);
                msg.writer().writeInt((int) pl.id);
                msg.writer().writeShort(pl.getHead());
                if (player.getSession().version > 214) {
                    msg.writer().writeShort(-1);
                }
                msg.writer().writeShort(pl.getBody());
                msg.writer().writeShort(pl.getLeg());
                msg.writer().writeUTF(pl.name);
                msg.writer().writeUTF(top.getInfo1());
                msg.writer().writeUTF(top.getInfo2());
            }
            player.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void showTopClanBDKB(Player player) {
        TopBanDoKhoBau.getInstance().load();
        List<Player> list = TopBanDoKhoBau.getInstance().getList();
        Message msg = new Message(-96);
        try {
            msg.writer().writeByte(0);
            msg.writer().writeUTF("Top Bang Hội");
            msg.writer().writeByte(list.size());
            for (int i = 0; i < list.size(); i++) {
                Player pl = list.get(i);
                msg.writer().writeInt(i + 1); // thứ hạng
                msg.writer().writeInt((int) pl.id);
                msg.writer().writeShort(pl.getHead());
                if (player.getSession().version > 214) {
                    msg.writer().writeShort(-1);
                }
                msg.writer().writeShort(pl.getBody());
                msg.writer().writeShort(pl.getLeg());
                msg.writer().writeUTF(pl.nameClan);
                msg.writer().writeUTF("Lv: " + pl.levelBDKBDone + " (" + Util.convertSecondsToTime(pl.lastTimeUpdateTopBDKB) + ")");
                msg.writer().writeUTF("Bang chủ " + pl.name + "\n[" + Util.convertMilliseconds(pl.timeBDKBDone) + "]");
            }
            player.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void showMyTopClanBDKB(Player player) {
        MyClanTopBanDoKhoBau.getInstance().load2(player.clan.getLeader().id);
        List<Player> list = MyClanTopBanDoKhoBau.getInstance().getList();
        Message msg = new Message(-96);
        try {
            msg.writer().writeByte(0);
            msg.writer().writeUTF("Thành tích bang");
            msg.writer().writeByte(list.size());
            for (int i = 0; i < list.size(); i++) {
                Player pl = list.get(i);
                msg.writer().writeInt(i + 1);
                msg.writer().writeInt((int) pl.id);
                msg.writer().writeShort(pl.getHead());
                if (player.getSession().version > 214) {
                    msg.writer().writeShort(-1);
                }
                msg.writer().writeShort(pl.getBody());
                msg.writer().writeShort(pl.getLeg());
                msg.writer().writeUTF(pl.nameClan);
                msg.writer().writeUTF("Lv: " + pl.levelBDKBDone + " (" + Util.convertSecondsToTime(pl.lastTimeUpdateTopBDKB) + ")");
                msg.writer().writeUTF("Bang chủ: " + pl.name + "\n[" + Util.convertMilliseconds(pl.timeBDKBDone) + "]");
            }
            player.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void showTopClanKhiGas(Player player) {
        TopKhiGasHuyDiet.getInstance().load();
        List<Player> list = TopKhiGasHuyDiet.getInstance().getList();
        Message msg = new Message(-96);
        try {
            msg.writer().writeByte(0);
            msg.writer().writeUTF("Top Bang Hội");
            msg.writer().writeByte(list.size());
            for (int i = 0; i < list.size(); i++) {
                Player pl = list.get(i);
                msg.writer().writeInt(i + 1); // thứ hạng
                msg.writer().writeInt((int) pl.id);
                msg.writer().writeShort(pl.getHead());
                if (player.getSession().version > 214) {
                    msg.writer().writeShort(-1);
                }
                msg.writer().writeShort(pl.getBody());
                msg.writer().writeShort(pl.getLeg());
                msg.writer().writeUTF(pl.nameClan);
                msg.writer().writeUTF("Lv: " + pl.levelKhiGasDone + " (" + Util.convertSecondsToTime(pl.lastTimeUpdateTopKhiGas) + ")");
                msg.writer().writeUTF("Bang chủ " + pl.name + "\n[" + Util.convertMilliseconds(pl.timeKhiGasDone) + "]");
            }
            player.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void showTopClanCDRD(Player player) {
        TopConDuongRanDoc.getInstance().load();
        List<Player> list = TopConDuongRanDoc.getInstance().getList();
        Message msg = new Message(-96);
        try {
            msg.writer().writeByte(0);
            msg.writer().writeUTF("Top Bang Hội");
            msg.writer().writeByte(list.size());
            for (int i = 0; i < list.size(); i++) {
                Player pl = list.get(i);
                msg.writer().writeInt(i + 1); // thứ hạng
                msg.writer().writeInt((int) pl.id);
                msg.writer().writeShort(pl.getHead());
                if (player.getSession().version > 214) {
                    msg.writer().writeShort(-1);
                }
                msg.writer().writeShort(pl.getBody());
                msg.writer().writeShort(pl.getLeg());
                msg.writer().writeUTF(pl.nameClan);
                msg.writer().writeUTF("Lv: " + pl.levelCDRDDone + " (" + Util.convertSecondsToTime(pl.lastTimeUpdateTopCDRD) + ")");
                msg.writer().writeUTF("Bang chủ " + pl.name + "\n[" + Util.convertMilliseconds(pl.timeCDRDDone) + "]");
            }
            player.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendMessAllPlayer(Message msg) {
        PlayerService.gI().sendMessageAllPlayer(msg);
    }

    public void sendMessAllPlayerIgnoreMe(Player player, Message msg) {
        PlayerService.gI().sendMessageIgnore(player, msg);
    }

    public void sendMessAllPlayerInMap(Zone zone, Message msg) {
        if (zone == null) {
            msg.dispose();
            return;
        }
        List<Player> players = zone.getPlayers();
        if (players.isEmpty()) {
            msg.dispose();
            return;
        }
        for (int i = players.size() - 1; i >= 0; i--) {
            Player pl = players.get(i);
            if (pl != null) {
                pl.sendMessage(msg);
            }
        }
        msg.cleanup();
    }

    public void sendMessAllPlayerInMap(Player player, Message msg) {
        if (player == null || player.zone == null) {
            msg.dispose();
            return;
        }
        if (MapService.gI().isMapOffline(player.zone.map.mapId)) {
            if (player instanceof TrainingBoss || player instanceof NonInteractiveNPC) {
                List<Player> players = player.zone.getPlayers();
                if (players.isEmpty()) {
                    msg.dispose();
                    return;
                }
                for (int i = 0; i < players.size(); i++) {
                    Player pl = players.get(i);
                    if (pl != null && (player instanceof NonInteractiveNPC || ((TrainingBoss) player).playerAtt.equals(pl))) {
                        pl.sendMessage(msg);
                    }
                }
            } else {
                player.sendMessage(msg);
            }
        } else {
            List<Player> players = player.zone.getPlayers();
            if (players.isEmpty()) {
                msg.dispose();
                return;
            }
            for (int i = 0; i < players.size(); i++) {
                Player pl = players.get(i);
                if (pl != null && pl.getSession() != null && pl.isPl()) {
                    pl.sendMessage(msg);
                }
            }
        }
        msg.cleanup();
    }

    public void sendMessAnotherNotMeInMap(Player player, Message msg) {
        if (player == null || player.zone == null) {
            msg.cleanup();
            return;
        }
        if (MapService.gI().isMapOffline(player.zone.map.mapId)) {
            if (player instanceof TrainingBoss || player instanceof NonInteractiveNPC) {
                List<Player> players = player.zone.getPlayers();
                if (players.isEmpty()) {
                    msg.dispose();
                    return;
                }
                for (int i = 0; i < players.size(); i++) {
                    Player pl = players.get(i);
                    if (pl != null && !pl.equals(player) && (player instanceof NonInteractiveNPC || ((TrainingBoss) player).playerAtt.equals(pl))) {
                        pl.sendMessage(msg);
                    }
                }
            }
        } else {
            List<Player> players = player.zone.getPlayers();
            if (players.isEmpty()) {
                msg.dispose();
                return;
            }
            for (int i = 0; i < players.size(); i++) {
                Player pl = players.get(i);
                if (pl != null && pl.getSession() != null && !pl.equals(player) && pl.isPl()) {
                    pl.sendMessage(msg);
                }
            }
        }
        msg.cleanup();
    }

    public void Send_Info_NV(Player pl) {
        Message msg;
        try {
            msg = Service.gI().messageSubCommand((byte) 14);//Cập nhật máu
            msg.writer().writeInt((int) pl.id);
            msg.writer().writeInt(pl.nPoint.hp);
            msg.writer().writeByte(0);//Hiệu ứng Ăn Đậu
            msg.writer().writeInt(pl.nPoint.hpMax);
            sendMessAnotherNotMeInMap(pl, msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();

        }
    }

    public void Send_Info_NV_do_Injure(Player pl) {
        Message msg;
        try {
            msg = Service.gI().messageSubCommand((byte) 14);//Cập nhật máu
            msg.writer().writeInt((int) pl.id);
            msg.writer().writeInt(pl.nPoint.hp);
            msg.writer().writeByte(2);
            msg.writer().writeInt(pl.nPoint.hpMax);
            sendMessAnotherNotMeInMap(pl, msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();

        }
    }

    public void sendInfoPlayerEatPea(Player pl) {
        Message msg;
        try {
            msg = Service.gI().messageSubCommand((byte) 14);
            msg.writer().writeInt((int) pl.id);
            msg.writer().writeInt(pl.nPoint.hp);
            msg.writer().writeByte(1);
            msg.writer().writeInt(pl.nPoint.hpMax);
            sendMessAnotherNotMeInMap(pl, msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();

        }
    }

    public void reload_HP_NV(Player pl) {
        Message msg = null;
        try {
            msg = messageSubCommand((byte) 9);
            msg.writer().writeInt((int) pl.id);
            msg.writer().writeInt(pl.nPoint.hp);
            msg.writer().writeInt(pl.nPoint.hpMax);
            sendMessAnotherNotMeInMap(pl, msg);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public void resetPoint(Player player, int x, int y) {
        Message msg;
        try {
            player.location.x = x;
            player.location.y = y;
            msg = new Message(46);
            msg.writer().writeShort(x);
            msg.writer().writeShort(y);
            player.sendMessage(msg);
            msg.cleanup();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void clearMap(Player player) {
        Message msg;
        try {
            msg = new Message(-22);
            player.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isMessageContainsThang(String text) {
        return text.toLowerCase().contains("thang");
    }

    public void chat(Player player, String text) {
        if (text.equals("part")) {
            Manager.loadPart();
            DataGame.updateData(player.getSession());
            return;
        }
        if (isMessageContainsThang(text)) {

            player.setLastChatMessage(text);
        } else {
            Message msg;
            try {
                msg = new Message(44);
                msg.writer().writeInt((int) player.id);
                msg.writer().writeUTF(text);
                sendMessAllPlayerInMap(player, msg);
                msg.cleanup();
            } catch (Exception e) {
                Logger.logException(Service.class, e);
            }
        }
    }

    public void chatJustForMe(Player me, Player plChat, String text) {
        Message msg;
        try {
            msg = new Message(44);
            msg.writer().writeInt((int) plChat.id);
            msg.writer().writeUTF(text);
            me.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
        }
    }

    public Npc getNpc(Player player) {
        Npc closestNpc = null;
        double closestDistance = Double.MAX_VALUE;
        for (Npc npc : player.zone.map.npcs) {
            double distance = Util.getDistance(player, npc);
            if (distance <= 150 && distance < closestDistance) {
                closestDistance = distance;
                closestNpc = npc;
            }
        }
        return closestNpc;
    }

    public void Transport(Player pl) {
        Message msg = null;
        try {
            msg = new Message(-105);
            msg.writer().writeShort(pl.maxTime);
            msg.writer().writeByte(pl.type);
            pl.sendMessage(msg);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public void Transport(Player pl, int type) {
        Message msg = null;
        try {
            msg = new Message(-105);
            msg.writer().writeShort(pl.maxTime);
            msg.writer().writeByte(type);
            pl.sendMessage(msg);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public void point(Player player) {
        if (player == null || player.nPoint == null) {
            return;
        }
        player.nPoint.calPoint();
        Send_Info_NV(player);
        if (!player.isPet && !player.isBot && !player.isBoss && !player.isNewPet) {
            Message msg;
            try {
                msg = new Message(-42);
                msg.writer().writeInt(player.nPoint.hpg);
                msg.writer().writeInt(player.nPoint.mpg);
                msg.writer().writeInt(player.nPoint.dameg);
                msg.writer().writeInt(player.nPoint.hpMax);
                msg.writer().writeInt(player.nPoint.mpMax);
                msg.writer().writeInt(player.nPoint.hp);
                msg.writer().writeInt(player.nPoint.mp);
                msg.writer().writeByte(player.nPoint.speed);
                msg.writer().writeByte(20);
                msg.writer().writeByte(20);
                msg.writer().writeByte(1);
                msg.writer().writeInt(player.nPoint.dame);
                msg.writer().writeInt(player.nPoint.def);
                msg.writer().writeByte(player.nPoint.crit);
                msg.writer().writeLong(player.nPoint.tiemNang);
                msg.writer().writeShort(100);
                msg.writer().writeShort(player.nPoint.defg);
                msg.writer().writeByte(player.nPoint.critg);
                player.sendMessage(msg);
                msg.cleanup();
            } catch (Exception e) {
                Logger.logException(Service.class, e);
            }
        }
    }

    public String name(Player player) {
        if (player.isPl() && player.clan != null) {
            try {
                if (!player.clan.name2.isEmpty()) {
                    return "[" + player.clan.name2 + "] " + player.name;
                } else if (player.clan.name.length() > 3) {
                    return "[" + player.clan.name.substring(0, 3) + "] " + player.name;
                } else {
                    return "[" + player.clan.name + "] " + player.name;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (player.name == null) {
            return "";
        }
        return player.name;
    }

    public void player(Player pl) {
        if (pl == null) {
            return;
        }
        Message msg;
        try {
            msg = messageSubCommand((byte) 0);
            msg.writer().writeInt((int) pl.id);
            msg.writer().writeByte(pl.playerTask.taskMain.id);
            msg.writer().writeByte(pl.gender);
            msg.writer().writeShort(pl.head);
            msg.writer().writeUTF(pl.name);
            msg.writer().writeByte(0); //cPK
            msg.writer().writeByte(pl.typePk);
            msg.writer().writeLong(pl.nPoint.power);
            msg.writer().writeShort(0);
            msg.writer().writeShort(0);
            msg.writer().writeByte(pl.gender);
            ArrayList<Skill> skills = (ArrayList<Skill>) pl.playerSkill.skills;
            msg.writer().writeByte(pl.playerSkill.getSizeSkill());
            for (Skill skill : skills) {
                if (skill.skillId != -1) {
                    msg.writer().writeShort(skill.skillId);
                }
            }
            if (pl.getSession().version >= 214) {
                msg.writer().writeLong(pl.inventory.gold);
            } else {
                msg.writer().writeInt((int) pl.inventory.gold);
            }
            msg.writer().writeInt(pl.inventory.ruby);
            msg.writer().writeInt(pl.inventory.gem);
            ArrayList<Item> itemsBody = (ArrayList<Item>) pl.inventory.itemsBody;
            msg.writer().writeByte(itemsBody.size());
            for (Item item : itemsBody) {
                if (!item.isNotNullItem()) {
                    msg.writer().writeShort(-1);
                } else {
                    msg.writer().writeShort(item.template.id);
                    msg.writer().writeInt(item.quantity);
                    msg.writer().writeUTF(item.getInfo());
                    msg.writer().writeUTF(item.getContent());
                    List<ItemOption> itemOptions = item.itemOptions;
                    msg.writer().writeByte(itemOptions.size());
                    for (ItemOption itemOption : itemOptions) {
                        msg.writer().writeByte(itemOption.optionTemplate.id);
                        msg.writer().writeShort(itemOption.param);
                    }
                }

            }
            ArrayList<Item> itemsBag = (ArrayList<Item>) pl.inventory.itemsBag;
            msg.writer().writeByte(itemsBag.size());
            for (int i = 0; i < itemsBag.size(); i++) {
                Item item = itemsBag.get(i);
                if (!item.isNotNullItem()) {
                    msg.writer().writeShort(-1);
                } else {
                    msg.writer().writeShort(item.template.id);
                    msg.writer().writeInt(item.quantity);
                    msg.writer().writeUTF(item.getInfo());
                    msg.writer().writeUTF(item.getContent());
                    List<ItemOption> itemOptions = item.itemOptions;
                    msg.writer().writeByte(itemOptions.size());
                    for (ItemOption itemOption : itemOptions) {
                        msg.writer().writeByte(itemOption.optionTemplate.id);
                        msg.writer().writeShort(itemOption.param);
                    }
                }

            }
            ArrayList<Item> itemsBox = (ArrayList<Item>) pl.inventory.itemsBox;
            msg.writer().writeByte(itemsBox.size());
            for (int i = 0; i < itemsBox.size(); i++) {
                Item item = itemsBox.get(i);
                if (!item.isNotNullItem()) {
                    msg.writer().writeShort(-1);
                } else {
                    msg.writer().writeShort(item.template.id);
                    msg.writer().writeInt(item.quantity);
                    msg.writer().writeUTF(item.getInfo());
                    msg.writer().writeUTF(item.getContent());
                    List<ItemOption> itemOptions = item.itemOptions;
                    msg.writer().writeByte(itemOptions.size());
                    for (ItemOption itemOption : itemOptions) {
                        msg.writer().writeByte(itemOption.optionTemplate.id);
                        msg.writer().writeShort(itemOption.param);
                    }
                }
            }
            DataGame.sendHeadAvatar(msg);
            msg.writer().writeShort(514);
            msg.writer().writeShort(515);
            msg.writer().writeShort(537);
            msg.writer().writeByte(pl.fusion.typeFusion != ConstPlayer.NON_FUSION ? 1 : 0);
            msg.writer().writeInt(pl.deltaTime);
            msg.writer().writeByte(pl.isNewMember ? 1 : 0);
            msg.writer().writeShort(pl.getAura());
            msg.writer().writeByte(pl.getEffFront());
            msg.writer().writeShort(pl.getHat());
            pl.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            Logger.logException(Service.class, e);
        }
    }

    public Message messageNotLogin(byte command) throws IOException {
        Message ms = new Message(-29);
        ms.writer().writeByte(command);
        return ms;
    }

    public Message messageNotMap(byte command) throws IOException {
        Message ms = new Message(-28);
        ms.writer().writeByte(command);
        return ms;
    }

    public Message messageSubCommand(byte command) throws IOException {
        Message ms = new Message(-30);
        ms.writer().writeByte(command);
        return ms;
    }

    public void addSMTN(Player player, byte type, long param, boolean isOri) {
        if (player.nPoint == null) {
            return;
        }

        if (player.isPet) {
            player.nPoint.powerUp(param);
            player.nPoint.tiemNangUp(param);

            Player master = ((Pet) player).master;

            long masterParam = (long) (param * 0.5);
            masterParam = master.nPoint.calSubTNSM(masterParam);

            long maxPower = master.nPoint.getPowerLimit();
            if (master.nPoint.power >= maxPower) {
                return;
            }
            if (master.nPoint.power + masterParam > maxPower) {
                masterParam = maxPower - master.nPoint.power;
            }

            master.nPoint.powerUp(masterParam);
            master.nPoint.tiemNangUp(masterParam);

            addSMTN(master, type, masterParam, true);

        } else if (player.isBot) {
            player.nPoint.power += param;
            player.nPoint.tiemNang += param;

        } else {
            long maxPower = player.nPoint.getPowerLimit();

            if (player.nPoint.power >= maxPower) {
                return;
            }
            if (player.nPoint.power + param > maxPower) {
                param = maxPower - player.nPoint.power;
                if (param <= 0) {
                    return;
                }
            }

            switch (type) {
                case 1:
                    player.nPoint.tiemNangUp(param);
                    break;
                case 2:
                    player.nPoint.powerUp(param);
                    player.nPoint.tiemNangUp(param);
                    break;
                default:
                    player.nPoint.powerUp(param);
                    break;
            }

            PlayerService.gI().sendTNSM(player, type, param);

            if (isOri && player.clan != null) {
                player.clan.addSMTNClan(player, param);
            }
        }
    }

    public String get_HanhTinh(int hanhtinh) {
        switch (hanhtinh) {
            case 0:
                return "Trái Đất";
            case 1:
                return "Namếc";
            case 2:
                return "Xayda";
            default:
                return "";
        }
    }

    public List<String> ListCaption(int gender) {
        List<String> Captions = new ArrayList<>();
        Captions.add("Tân thủ");
        Captions.add("Tập sự sơ cấp");
        Captions.add("Tập sự trung cấp");
        Captions.add("Tập sự cao cấp");
        Captions.add("Tân binh");
        Captions.add("Chiến binh");
        Captions.add("Chiến binh cao cấp");
        Captions.add("Vệ binh");
        Captions.add("Vệ binh hoàng gia");
        Captions.add("Siêu " + (gender == 0 ? "nhân" : get_HanhTinh(gender)) + " cấp 1");
        Captions.add("Siêu " + (gender == 0 ? "nhân" : get_HanhTinh(gender)) + " cấp 2");
        Captions.add("Siêu " + (gender == 0 ? "nhân" : get_HanhTinh(gender)) + " cấp 3");
        Captions.add("Siêu " + (gender == 0 ? "nhân" : get_HanhTinh(gender)) + " cấp 4");
        Captions.add("Thần " + get_HanhTinh(gender) + " cấp 1");
        Captions.add("Thần " + get_HanhTinh(gender) + " cấp 2");
        Captions.add("Thần " + get_HanhTinh(gender) + " cấp 3");
        Captions.add("Giới Vương Thần cấp 1");
        Captions.add("Giới Vương Thần cấp 2");
        Captions.add("Giới Vương Thần cấp 3");
        Captions.add("Thiên đạo");
        Captions.add("kẻ hũy diệt vũ trụ");
        Captions.add("vô địch đa giới");
        return Captions;
    }

    public String getCurrStrLevel(Player pl) {
        return ListCaption(pl.gender).get(getCurrLevel(pl));
    }

    public int getCurrLevel(Player pl) {
        if (pl.nPoint == null) {
            return 0;
        }
        long sucmanh = pl.nPoint.power;
        if (sucmanh < 3000) {
            return 0;
        } else if (sucmanh < 15000) {
            return 1;
        } else if (sucmanh < 40000) {
            return 2;
        } else if (sucmanh < 90000) {
            return 3;
        } else if (sucmanh < 170000) {
            return 4;
        } else if (sucmanh < 340000) {
            return 5;
        } else if (sucmanh < 700000) {
            return 6;
        } else if (sucmanh < 1500000) {
            return 7;
        } else if (sucmanh < 15000000) {
            return 8;
        } else if (sucmanh < 150000000) {
            return 9;
        } else if (sucmanh < 1500000000) {
            return 10;
        } else if (sucmanh < 5000000000L) {
            return 11;
        } else if (sucmanh < 10000000000L) {
            return 12;
        } else if (sucmanh < 40000000000L) {
            return 13;
        } else if (sucmanh < 50010000000L) {
            return 14;
        } else if (sucmanh < 60010000000L) {
            return 15;
        } else if (sucmanh < 70010000000L) {
            return 16;
        } else if (sucmanh < 80010000000L) {
            return 17;
        } else if (sucmanh < 90010000000L) {
            return 18;
        }
        return 19;
    }

    public void hsChar(Player pl, int hp, int mp) {
        Message msg;
        try {
            if (pl.isPl() && pl.effectSkill != null && pl.effectSkill.isBodyChangeTechnique) {
                PlayerService.gI().changeAndSendTypePK(pl, 5);
            }
            pl.setJustRevivaled();
            pl.nPoint.setHp(hp);
            pl.nPoint.setMp(mp);
            if (pl.isPl()) {
                msg = new Message(-16);
                pl.sendMessage(msg);
                msg.cleanup();
                PlayerService.gI().sendInfoHpMpMoney(pl);
            }

            msg = messageSubCommand((byte) 15);
            msg.writer().writeInt((int) pl.id);
            msg.writer().writeInt(hp);
            msg.writer().writeInt(mp);
            msg.writer().writeShort(pl.location.x);
            msg.writer().writeShort(pl.location.y);
            sendMessAllPlayerInMap(pl, msg);
            msg.cleanup();

            Send_Info_NV(pl);
            PlayerService.gI().sendInfoHpMp(pl);
            AchievementService.gI().checkDoneTask(pl, ConstAchievement.THANH_HOI_SINH);
        } catch (Exception e) {
            Logger.logException(Service.class, e);
        }
    }

    public void charDie(Player pl) {
        if (pl == null || pl.location == null) {
            return;
        }
        Message msg;
        try {
            if (!pl.isPet && !pl.isBot && !pl.isNewPet && pl.isPl()) {
                msg = new Message(-17);
                msg.writer().writeByte((int) pl.id);
                msg.writer().writeShort(pl.location.x);
                msg.writer().writeShort(pl.location.y);
                pl.sendMessage(msg);
                msg.cleanup();
            } else if (pl.isPet) {
                ((Pet) pl).lastTimeDie = System.currentTimeMillis();
            }
            msg = new Message(-8);
            msg.writer().writeShort((int) pl.id);
            int cPk = 0;
            msg.writer().writeByte(cPk); //cpk
            msg.writer().writeShort(pl.location.x);
            msg.writer().writeShort(pl.location.y);
            sendMessAnotherNotMeInMap(pl, msg);
            msg.cleanup();

            Send_Info_NV(pl);
        } catch (Exception e) {
            Logger.logException(Service.class, e);
        }
    }

    public void attackMob(Player pl, int mobId, boolean isMobMe, int masterId) {
        if (pl != null && pl.zone != null) {
            if (!isMobMe) {
                for (Mob mob : pl.zone.mobs) {
                    if (mob.id == mobId) {
                        SkillService.gI().useSkill(pl, null, mob, -1, null);
                        break;
                    }
                }
            } else {
                Player plAtt = pl.zone.getPlayerInMap(masterId);
                if (plAtt != null && SkillService.gI().canAttackPlayer(pl, plAtt)) {
                    Mob mob = plAtt.mobMe;
                    if (mob != null) {
                        mob.injured(pl, pl.nPoint.getDameAttack(false), true);
                    }
                }
            }
        }
    }

    public void Send_Caitrang(Player player) {
        if (player != null) {
            Message msg;
            try {
                msg = new Message(-90);
                msg.writer().writeByte(1);// check type
                msg.writer().writeInt((int) player.id); //id player
                short head = player.getHead();
                short body = player.getBody();
                short leg = player.getLeg();

                msg.writer().writeShort(head);//set head
                msg.writer().writeShort(body);//setbody
                msg.writer().writeShort(leg);//set leg
                msg.writer().writeByte(player.effectSkill.isMonkey ? 1 : 0);//set khỉ
                sendMessAllPlayerInMap(player, msg);
                msg.cleanup();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void setNotMonkey(Player player) {
        Message msg;
        try {
            msg = new Message(-90);
            msg.writer().writeByte(-1);
            msg.writer().writeInt((int) player.id);
            Service.gI().sendMessAllPlayerInMap(player, msg);
            msg.cleanup();
        } catch (Exception e) {
            Logger.logException(Service.class, e);
        }
    }

    public void sendFlagBag(Player pl) {
        Message msg;
        try {
            msg = new Message(-64);
            msg.writer().writeInt((int) pl.id);
            msg.writer().writeByte(pl.getFlagBag());
            sendMessAllPlayerInMap(pl, msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendThongBaoOK(Player pl, String text) {
        if (pl.isPet || pl.isNewPet) {
            return;
        }
        Message msg;
        try {
            msg = new Message(-26);
            msg.writer().writeUTF(text);
            pl.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            Logger.logException(Service.class, e);
        }
    }

    public void sendThongBaoOK(MySession session, String text) {
        Message msg;
        try {
            msg = new Message(-26);
            msg.writer().writeUTF(text);
            session.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendThongBaoAllPlayer(String thongBao) {
        Message msg;
        try {
            msg = new Message(-25);
            msg.writer().writeUTF(thongBao);
            this.sendMessAllPlayer(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendBigMessage(Player player, int iconId, String text) {
        try {
            Message msg;
            msg = new Message(-70);
            msg.writer().writeShort(iconId);
            msg.writer().writeUTF(text);
            msg.writer().writeByte(0);
            player.sendMessage(msg);
            msg.cleanup();
        } catch (IOException e) {

        }
    }

    public void sendThongBaoFromAdmin(Player player, String text) {
        sendBigMessage(player, 1139, text);
    }

    public void sendThongBao(Player pl, String thongBao) {
        Message msg;
        try {
            msg = new Message(-25);
            msg.writer().writeUTF(thongBao);
            pl.sendMessage(msg);
            msg.cleanup();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendThongBao(List<Player> pl, String thongBao) {
        for (int i = 0; i < pl.size(); i++) {
            Player ply = pl.get(i);
            if (ply != null) {
                this.sendThongBao(ply, thongBao);
            }
        }
    }

    public void sendThongBaoToAnotherNotMe(Player me, String text) {
        for (int i = 0; i < Client.gI().getPlayers().size(); i++) {
            Player pl = Client.gI().getPlayers().get(i);
            if (pl != null && !pl.equals(me)) {
                this.sendThongBao(pl, text);
            }
        }
    }

    public void sendMoney(Player pl) {
        Message msg;
        try {
            msg = new Message(6);
            if (pl.getSession().version >= 214) {
                msg.writer().writeLong(pl.inventory.gold);
            } else {
                msg.writer().writeInt((int) pl.inventory.gold);
            }
            msg.writer().writeInt(pl.inventory.gem);
            msg.writer().writeInt(pl.inventory.ruby);
            pl.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendToAntherMePickItem(Player player, int itemMapId) {
        Message msg;
        try {
            msg = new Message(-19);
            msg.writer().writeShort(itemMapId);
            msg.writer().writeInt((int) player.id);
            sendMessAnotherNotMeInMap(player, msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();

        }
    }

    public void openFlagUI(Player pl) {
        Message msg;
        try {
            msg = new Message(-103);
            msg.writer().writeByte(0);
            msg.writer().writeByte(flagTempId.length);
            for (int i = 0; i < flagTempId.length; i++) {
                msg.writer().writeShort(flagTempId[i]);
                msg.writer().writeByte(1);
                switch (flagTempId[i]) {
                    case 363:
                        msg.writer().writeByte(73);
                        msg.writer().writeShort(0);
                        break;
                    case 371:
                        msg.writer().writeByte(88);
                        msg.writer().writeShort(10);
                        break;
                    default:
                        msg.writer().writeByte(88);
                        msg.writer().writeShort(5);
                        break;
                }
            }
            pl.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void changeFlag(Player pl, int index) {
        Message msg;
        try {
            pl.cFlag = (byte) index;
            msg = new Message(-103);
            msg.writer().writeByte(1);
            msg.writer().writeInt((int) pl.id);
            msg.writer().writeByte(index);
            Service.gI().sendMessAllPlayerInMap(pl, msg);
            msg.cleanup();

            msg = new Message(-103);
            msg.writer().writeByte(2);
            msg.writer().writeByte(index);
            msg.writer().writeShort(flagIconId[index]);
            Service.gI().sendMessAllPlayerInMap(pl, msg);
            msg.cleanup();

            if (pl.pet != null) {
                pl.pet.cFlag = (byte) index;
                msg = new Message(-103);
                msg.writer().writeByte(1);
                msg.writer().writeInt((int) pl.pet.id);
                msg.writer().writeByte(index);
                Service.gI().sendMessAllPlayerInMap(pl.pet, msg);
                msg.cleanup();

                msg = new Message(-103);
                msg.writer().writeByte(2);
                msg.writer().writeByte(index);
                msg.writer().writeShort(index > -1 ? flagIconId[index] : index);
                Service.gI().sendMessAllPlayerInMap(pl.pet, msg);
                msg.cleanup();
            }
            pl.idMark.setLastTimeChangeFlag(System.currentTimeMillis());
        } catch (Exception e) {
            Logger.logException(Service.class, e);
        }
    }

    public void sendFlagPlayerToMe(Player me, Player pl) {
        Message msg;
        try {
            msg = new Message(-103);
            msg.writer().writeByte(2);
            msg.writer().writeByte(pl.cFlag);
            msg.writer().writeShort(flagIconId[pl.cFlag]);
            me.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void chooseFlag(Player pl, int index) {
        if (index < 0) {
            return;
        }
        if (MapService.gI().isMapBlackBallWar(pl.zone.map.mapId) || MapService.gI().isMapMaBu(pl.zone.map.mapId)) {
            sendThongBao(pl, "Không được đổi cờ lúc này");
            return;
        }
        if (Util.canDoWithTime(pl.idMark.getLastTimeChangeFlag(), 60000)) {
            changeFlag(pl, index);
        } else {
            sendThongBao(pl, "Chỉ được đổi cờ sau " + TimeUtil.getTimeLeft(pl.idMark.getLastTimeChangeFlag(), 60) + " nữa");
        }
    }

    public void attackPlayer(Player pl, int idPlAnPem) {
        Player player;
        if (MapService.gI().isMapOffline(pl.zone.map.mapId)) {
            player = pl.zone.getPlayerInMapOffline(pl, idPlAnPem);
        } else {
            player = pl.zone.getPlayerInMap(idPlAnPem);
        }
        SkillService.gI().useSkill(pl, player, null, -1, null);
    }

    public void releaseCooldownSkill(Player pl) {
        Message msg;
        try {
            msg = new Message(-94);
            for (Skill skill : pl.playerSkill.skills) {
                msg.writer().writeShort(skill.skillId);
                skill.lastTimeUseThisSkill = System.currentTimeMillis() - skill.coolDown;
                int leftTime = 0;
                msg.writer().writeInt(leftTime);
            }
            pl.sendMessage(msg);
            pl.nPoint.setMp(pl.nPoint.mpMax);
            PlayerService.gI().sendInfoHpMpMoney(pl);
            msg.cleanup();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendTimeSkill(Player pl) {
        Message msg;
        try {
            msg = new Message(-94);
            for (Skill skill : pl.playerSkill.skills) {
                msg.writer().writeShort(skill.skillId);
                int timeLeft = (int) (skill.lastTimeUseThisSkill + skill.coolDown - System.currentTimeMillis());
                if (timeLeft < 0) {
                    timeLeft = 0;
                }
                msg.writer().writeInt(timeLeft);
            }
            pl.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendTimeSkill(Player pl, Skill skill) {
        Message msg;
        try {
            msg = new Message(-94);
            msg.writer().writeShort(skill.skillId);
            int timeLeft = (int) (skill.lastTimeUseThisSkill + skill.coolDown - System.currentTimeMillis());
            if (timeLeft < 0) {
                timeLeft = 0;
            }
            msg.writer().writeInt(timeLeft);
            pl.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void releaseCooldownSkill(Player pl, Skill skill) {
        Message msg;
        try {
            msg = new Message(-94);
            msg.writer().writeShort(skill.skillId);
            skill.lastTimeUseThisSkill = System.currentTimeMillis() - skill.coolDown;
            int leftTime = 0;
            msg.writer().writeInt(leftTime);
            pl.sendMessage(msg);
            pl.nPoint.setMp(pl.nPoint.mpMax);
            PlayerService.gI().sendInfoHpMpMoney(pl);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void dropItemMap(Zone zone, ItemMap item) {
        Message msg;
        try {
            msg = new Message(68);
            msg.writer().writeShort(item.itemMapId);
            msg.writer().writeShort(item.itemTemplate.id);
            msg.writer().writeShort(item.x);
            msg.writer().writeShort(item.y);
            msg.writer().writeInt(3);//
            sendMessAllPlayerInMap(zone, msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void dropItemMapForMe(Player player, ItemMap item) {
        Message msg;
        try {
            msg = new Message(68);
            msg.writer().writeShort(item.itemMapId);
            msg.writer().writeShort(item.itemTemplate.id);
            msg.writer().writeShort(item.x);
            msg.writer().writeShort(item.y);
            msg.writer().writeInt(3);//
            player.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            Logger.logException(Service.class, e);
        }
    }

    public void showInfoPet(Player pl) {
        if (pl != null && pl.pet != null) {
            Message msg;
            try {
                msg = new Message(-107);
                msg.writer().writeByte(2);
                msg.writer().writeShort(pl.pet.getAvatar());
                msg.writer().writeByte(pl.pet.inventory.itemsBody.size());

                for (Item item : pl.pet.inventory.itemsBody) {
                    if (!item.isNotNullItem()) {
                        msg.writer().writeShort(-1);
                    } else {
                        msg.writer().writeShort(item.template.id);
                        msg.writer().writeInt(item.quantity);
                        msg.writer().writeUTF(item.getInfo());
                        msg.writer().writeUTF(item.getContent());

                        int countOption = item.itemOptions.size();
                        msg.writer().writeByte(countOption);
                        for (ItemOption iop : item.itemOptions) {
                            msg.writer().writeByte(iop.optionTemplate.id);
                            msg.writer().writeShort(iop.param);
                        }
                    }
                }

                msg.writer().writeInt(pl.pet.nPoint.hp); //hp
                msg.writer().writeInt(pl.pet.nPoint.hpMax); //hpfull
                msg.writer().writeInt(pl.pet.nPoint.mp); //mp
                msg.writer().writeInt(pl.pet.nPoint.mpMax); //mpfull
                msg.writer().writeInt(pl.pet.nPoint.dame); //damefull
                msg.writer().writeUTF(pl.pet.name); //name
                msg.writer().writeUTF(getCurrStrLevel(pl.pet)); //curr level
                msg.writer().writeLong(pl.pet.nPoint.power); //power
                msg.writer().writeLong(pl.pet.nPoint.tiemNang); //tiềm năng
                msg.writer().writeByte(pl.pet.getStatus()); //status
                msg.writer().writeShort(pl.pet.nPoint.stamina); //stamina
                msg.writer().writeShort(pl.pet.nPoint.maxStamina); //stamina full
                msg.writer().writeByte(pl.pet.nPoint.crit); //crit
                msg.writer().writeShort(pl.pet.nPoint.def); //def
                int sizeSkill = pl.pet.playerSkill.skills.size();
                msg.writer().writeByte(5); //count pet skill
                for (int i = 0; i < sizeSkill; i++) {
                    if (pl.pet.playerSkill.skills.get(i).skillId != -1) {
                        msg.writer().writeShort(pl.pet.playerSkill.skills.get(i).skillId);
                    } else {
                        switch (i) {
                            case 1:
                                msg.writer().writeShort(-1);
                                msg.writer().writeUTF("Cần đạt sức mạnh 150tr để mở");
                                break;
                            case 2:
                                msg.writer().writeShort(-1);
                                msg.writer().writeUTF("Cần đạt sức mạnh 1tỷ5 để mở");
                                break;
                            case 3:
                                msg.writer().writeShort(-1);
                                msg.writer().writeUTF("Cần đạt sức mạnh 20tỷ để mở");
                                break;
                            case 4:
                                msg.writer().writeShort(-1);
                                if (pl.pet.typePet == 2 || pl.pet.typePet == 3 || pl.pet.typePet == 4) {
                                    msg.writer().writeUTF("Cần đạt sức mạnh 40tỷ để mở");
                                } else {
                                    msg.writer().writeUTF("Không thể mở kỹ năng này");
                                }
                                break;
                            default:
                                msg.writer().writeShort(-1);
                                msg.writer().writeUTF("Cần đạt sức mạnh 60tỷ để mở");
                                break;
                        }
                    }
                }
                pl.sendMessage(msg);
                msg.cleanup();

            } catch (Exception e) {
                Logger.logException(Service.class, e);
            }
        }
    }

    public void sendSpeedPlayer(Player pl, int speed) {
        Message msg;
        try {
            msg = Service.gI().messageSubCommand((byte) 8);
            msg.writer().writeInt((int) pl.id);
            msg.writer().writeByte(speed != -1 ? speed : pl.nPoint.speed);
            pl.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            Logger.logException(Service.class, e);
        }
    }

    public void setPos(Player player, int x, int y) {
        player.location.x = x;
        player.location.y = y;
        Message msg;
        try {
            msg = new Message(123);
            msg.writer().writeInt((int) player.id);
            msg.writer().writeShort(x);
            msg.writer().writeShort(y);
            msg.writer().writeByte(1);
            sendMessAllPlayerInMap(player, msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setPos2(Player player, int x, int y) {
        Message msg;
        try {
            msg = new Message(123);
            msg.writer().writeInt((int) player.id);
            msg.writer().writeShort(x);
            msg.writer().writeShort(y);
            msg.writer().writeByte(1);
            sendMessAnotherNotMeInMap(player, msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setPos0(Player player, int x, int y) {
        player.location.x = x;
        player.location.y = y;
        Message msg;
        try {
            msg = new Message(123);
            msg.writer().writeInt((int) player.id);
            msg.writer().writeShort(x);
            msg.writer().writeShort(y);
            msg.writer().writeByte(0);
            sendMessAllPlayerInMap(player, msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void getPlayerMenu(Player player, int playerId) {

        Message msg;
        try {
            msg = new Message(-79);
            Player pl = player.zone.getPlayerInMap(playerId);
            if (pl != null && (pl.nPoint != null)) {
                msg.writer().writeInt(playerId);
                msg.writer().writeLong(pl.nPoint.power);
                msg.writer().writeUTF(Service.gI().getCurrStrLevel(pl));
                player.sendMessage(msg);
            }
            msg.cleanup();
            if (player.idMark.isAcpTrade()) {
                player.idMark.setAcpTrade(false);
                return;
            }
//            SubMenuService.gI().showMenu(player);
        } catch (Exception e) {
            Logger.logException(Service.class, e);
        }
    }

    public void hideWaitDialog(Player pl) {
        Message msg;
        try {
            msg = new Message(-99);
            msg.writer().writeByte(-1);
            pl.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void chatPrivate(Player plChat, Player plReceive, String text) {
        if (Functions.isSpam(plChat, text)) {
            return;
        }
        Message msg = null;
        try {
            msg = new Message(92);
            msg.writer().writeUTF(plChat.name);
            msg.writer().writeUTF("|5|" + text);
            msg.writer().writeInt((int) plChat.id);
            msg.writer().writeShort(plChat.getHead());
            if (plChat.getSession().version > 214) {
                msg.writer().writeShort(-1);
            }
            msg.writer().writeShort(plChat.getBody());
            msg.writer().writeShort(plChat.getFlagBag());
            msg.writer().writeShort(plChat.getLeg());
            msg.writer().writeByte(1);
            plChat.sendMessage(msg);
            // Receive
            msg = new Message(92);
            msg.writer().writeUTF(plChat.name);
            msg.writer().writeUTF("|5|" + text);
            msg.writer().writeInt((int) plChat.id);
            msg.writer().writeShort(plChat.getHead());
            if (plReceive.getSession().version > 214) {
                msg.writer().writeShort(-1);
            }
            msg.writer().writeShort(plChat.getBody());
            msg.writer().writeShort(plChat.getFlagBag());
            msg.writer().writeShort(plChat.getLeg());
            msg.writer().writeByte(1);
            plReceive.sendMessage(msg);
        } catch (Exception e) {
            e.printStackTrace();

        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public void changePassword(Player player, String oldPass, String newPass, String rePass) {
        if (player.getSession().pp.equals(oldPass)) {
            if (newPass.length() >= 6) {
                if (newPass.equals(rePass)) {
                    player.getSession().pp = newPass;
                    try {
                        LocalManager.executeUpdate("update account set password = ? where id = ? and username = ?",
                                rePass, player.getSession().userId, player.getSession().uu);
                        Service.gI().sendThongBao(player, "Đổi mật khẩu thành công!");
                    } catch (Exception ex) {
                        Service.gI().sendThongBao(player, "Đổi mật khẩu thất bại!");
                        Logger.logException(Service.class, ex);
                    }
                } else {
                    Service.gI().sendThongBao(player, "Mật khẩu nhập lại không đúng!");
                }
            } else {
                Service.gI().sendThongBao(player, "Mật khẩu ít nhất 6 ký tự!");
            }
        } else {
            Service.gI().sendThongBao(player, "Mật khẩu cũ không đúng!");
        }
    }

    public void switchToCreateChar(MySession session) {
        Message msg;
        try {
            msg = new Message(2);
            session.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendCaption(MySession session, byte gender) {
        Message msg;
        try {
            msg = new Message(-41);
            List<String> captions = ListCaption(gender);
            msg.writer().writeByte(captions.size());
            for (String caption : captions) {
                msg.writer().writeUTF(caption);
            }
            session.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendHavePet(Player player) {
        Message msg;
        try {
            msg = new Message(-107);
            msg.writer().writeByte(player.pet == null ? 0 : 1);
            player.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendWaitToLogin(MySession session, int secondsWait) {
        Message msg;
        try {
            msg = new Message(122);
            msg.writer().writeShort(secondsWait);
            session.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            Logger.logException(Service.class, e);
        }
    }

    public void sendMessage(MySession session, int cmd, String path) {
        Message msg;
        try {
            msg = new Message(cmd);
            msg.writer().write(FileIO.readFile(path));
            session.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendNangDong(Player player) {
        Message msg;
        try {
            msg = new Message(-97);
            msg.writer().writeInt(0);
            player.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setClientType(MySession session, Message msg) {
        try {
            session.typeClient = (msg.reader().readByte());//client_type
            session.zoomLevel = msg.reader().readByte();//zoom_level
            msg.reader().readBoolean();//is_gprs
            msg.reader().readInt();//width
            msg.reader().readInt();//height
            msg.reader().readBoolean();//is_qwerty
            msg.reader().readBoolean();//is_touch
            String platform = msg.reader().readUTF();
            String[] arrPlatform = platform.split("\\|");
            session.version = Integer.parseInt(arrPlatform[1].replaceAll("\\.", ""));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            msg.cleanup();
        }
        DataGame.sendLinkIP(session);
    }

    public void dropSatellite(Player pl, Item item, Zone map, int x, int y) {
        ItemMap itemMap = new ItemMap(map, item.template, item.quantity, x, y, pl.id);
        itemMap.options = item.itemOptions;
        if (pl.clan != null) {
            itemMap.clanId = pl.clan.id;
        }
        map.addItem(itemMap);
        Message msg = null;
        try {
            msg = new Message(68);
            msg.writer().writeShort(itemMap.itemMapId);
            msg.writer().writeShort(itemMap.itemTemplate.id);
            msg.writer().writeShort(itemMap.x);
            msg.writer().writeShort(itemMap.y);
            msg.writer().writeInt(-2);
            msg.writer().writeShort(200);
            sendMessAllPlayerInMap(map, msg);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (msg != null) {
                msg.cleanup();
                msg = null;
            }
        }
    }

    public void mabaove(Player player, int mbv) {
        if (Integer.toString(mbv).length() != 6) {
            Service.gI().sendThongBaoOK(player, "Mã bảo vệ phải có độ dài là 6 số.");
        } else if (player.mbv == 0) {
            player.idMark.setMbv(mbv);
            NpcService.gI().createMenuConMeo(player, ConstNpc.MA_BAO_VE, -1, "Bạn chưa từng kích hoạt chức năng mã bảo vệ để kích hoạt bạn cần có 30K vàng, mật khẩu của bạn là: " + mbv, "Đồng ý", "Từ chối");
        } else if (player.mbv != mbv) {
            Service.gI().sendThongBao(player, "Mật khẩu không đúng. Vui lòng kiểm tra lại");
        } else {
            if (player.baovetaikhoan) {
                NpcService.gI().createMenuConMeo(player, ConstNpc.MA_BAO_VE, -1, "Tài khoản đang được bảo vệ\nBạn có muốn tắt bảo vệ không?", "Đồng ý", "Từ chối");
            } else {
                NpcService.gI().createMenuConMeo(player, ConstNpc.MA_BAO_VE, -1, "Tài khoản không được bảo vệ\nBạn muốn bật chứ năng bảo vệ tài khoản?", "Đồng ý", "Từ chối");
            }
        }
    }

    public void sendEffPlayer(Player pl, Player plReceive, int idEff, int layer, int loop, int loopCount) {
        Message msg = null;
        try {
            msg = new Message(-128);
            msg.writer().writeByte(0);
            msg.writer().writeInt((int) pl.id);
            msg.writer().writeShort(idEff);
            msg.writer().writeByte(layer);
            msg.writer().writeByte(loop);
            msg.writer().writeShort(loopCount);
            msg.writer().writeByte(0);
            plReceive.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Message showInputText(String title, int maxLength) throws IOException {
        Message msg = new Message(-71);
        msg.writer().writeUTF(title);
        msg.writer().writeShort(maxLength);
        return msg;
    }

    public void sendEffAllPlayer(Player pl, int idEff, int layer, int loop, int loopCount) {
        try {
            Message msg = new Message(-128);
            msg.writer().writeByte(0);
            msg.writer().writeInt((int) pl.id);
            msg.writer().writeShort(idEff);
            msg.writer().writeByte(layer);
            msg.writer().writeByte(loop);
            msg.writer().writeShort(loopCount);
            msg.writer().writeByte(0);
            sendMessAllPlayerInMap(pl.zone, msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendEffCaiTrang(Player pl, int costumeId) {
        int headEffId = -1;
        int bodyEffId = -1;
        int legEffId = -1;

        switch (costumeId) {
            case 1794:
                headEffId = 281;
                bodyEffId = 282;
                legEffId = 283;
                break;
            default:
                return;
        }

        int[] effIds = {headEffId, bodyEffId, legEffId};

        try {
            for (int i = 0; i < effIds.length; i++) {
                if (effIds[i] == -1) {
                    continue;
                }
                Message msg = new Message(-128);
                msg.writer().writeByte(0); // type effect
                msg.writer().writeInt((int) pl.id); // player id
                msg.writer().writeShort(effIds[i]); // id eff
                msg.writer().writeByte(i); // 0: head, 1: body, 2: leg
                msg.writer().writeByte(0); // loop
                msg.writer().writeShort(0); // loopCount
                msg.writer().writeByte(0); // reserved
                Service.gI().sendMessAllPlayerInMap(pl.zone, msg);
                msg.cleanup();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void removeEffAllPlayer(Player pl) {
        Message msg = null;
        try {
            msg = new Message(-128);
            msg.writer().writeByte(2);
            msg.writer().writeInt((int) pl.id);
            sendMessAllPlayerInMap(pl.zone, msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void removeEffPlayer(Player pl, int idEff) {
        Message msg = null;
        try {
            msg = new Message(-128);
            msg.writer().writeByte(1);
            msg.writer().writeInt((int) pl.id);
            msg.writer().writeShort(idEff);
            sendMessAllPlayerInMap(pl.zone, msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendEffPlayer(Player pl) {
        if (pl.isPl()) {
            if (pl.setClothes.songoku >= 5
                    || pl.setClothes.kaioken >= 5
                    || pl.setClothes.kirin >= 5
                    || pl.setClothes.thienXinHang >= 5) {
                Service.gI().sendEffAllPlayer(pl, 1200, 1, -1, 1);
            } else if (pl.setClothes.ocTieu >= 5
                    || pl.setClothes.pikkoroDaimao >= 5
                    || pl.setClothes.picolo >= 5) {
                Service.gI().sendEffAllPlayer(pl, 10277, 1, -1, 1);
            } else if (pl.setClothes.thanVuTruKaio >= 5) {
                Service.gI().sendEffAllPlayer(pl, 10277, 1, -1, 1);
                Service.gI().sendEffAllPlayer(pl, 5017, 1, -1, 1);
            } else if (pl.setClothes.kakarot >= 5
                    || pl.setClothes.nappa >= 5
                    || pl.setClothes.cadic >= 5) {
                Service.gI().sendEffAllPlayer(pl, 1202, 1, -1, 1);
            }
        }
    }

    public void sendEffAllPlayerMapToMe(Player pl) {
        try {
            for (Player plM : pl.zone.getPlayers()) {
                if (plM.isPl() && plM.inventory.itemsBody.size() >= 10) {
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void Send_Body_Mob(Mob mob, int type, int idIcon) {
        Message msg = null;
        try {
            msg = new Message(-112);
            msg.writer().writeByte(type);
            msg.writer().writeByte(mob.id);
            if (type == 1) {
                msg.writer().writeShort(idIcon);//set body
            }
            sendMessAllPlayerInMap(mob.zone, msg);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (msg != null) {
                msg.cleanup();
                msg = null;
            }
        }
    }

    public void sendPlayerVS(Player pVS1, Player pVS2, byte type) {
        Message msg = null;
        try {
            pVS1.typePk = type;
            msg = new Message(-30);
            msg.writer().writeByte((byte) 35);
            msg.writer().writeInt((int) pVS1.id); //ID PLAYER
            msg.writer().writeByte(type); //TYPE PK
            pVS1.sendMessage(msg);
            if (pVS2.isPl()) {
                pVS2.sendMessage(msg);
            }
        } catch (Exception e) {
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public void sendPVB(Player pVS1, Player pVS2, byte type) {
        Message msg = null;
        try {
            pVS1.typePk = type;
            msg = new Message(-30);
            msg.writer().writeByte((byte) 35);
            msg.writer().writeInt((int) pVS1.id); // ID PLAYER
            msg.writer().writeByte(type); // TYPE PK
            pVS1.sendMessage(msg);

            if (pVS2 != null) {
                msg = new Message(-30);
                msg.writer().writeByte((byte) 35);
                msg.writer().writeInt((int) pVS2.id); // ID PLAYER
                msg.writer().writeByte(type); // TYPE PK
                pVS1.sendMessage(msg);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public void sendPVP(Player p1, Player p2) {
        Message msg;
        try {
            msg = Service.gI().messageSubCommand((byte) 35);
            msg.writer().writeInt((int) p2.id);
            msg.writer().writeByte(3);
            p1.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void exitMap(Player player, long playerExitMapId) {
        Message msg;
        try {
            msg = new Message(-6);
            msg.writer().writeInt((int) playerExitMapId);
            player.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            Logger.logException(Service.class, e);
        }
    }

    public void SendPowerInfo(Player player) {
        Message msg = null;
        try {
            msg = new Message(-115);
            msg.writer().writeUTF("TL");
            msg.writer().writeShort(player.fightMabu.pointMabu);
            msg.writer().writeShort(player.fightMabu.POINT_MAX);
            msg.writer().writeShort(3);
            player.sendMessage(msg);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public void SendPercentPowerInfo(Player player) {
        Message msg = null;
        try {
            msg = new Message(-115);
            msg.writer().writeUTF("%");
            msg.writer().writeShort(player.fightMabu.pointPercent);
            msg.writer().writeShort(player.fightMabu.POINT_MAX * 2);
            msg.writer().writeShort(3);
            player.sendMessage(msg);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public void SendMabu(Zone zone, int percent) {
        Message msg = null;
        try {
            msg = new Message(-117);
            msg.writer().writeByte(percent);
            sendMessAllPlayerInMap(zone, msg);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public void callNhanBan(Player player) {
        List<Skill> skillList = new ArrayList<>();
        for (byte i = 0; i < player.playerSkill.skills.size(); i++) {
            Skill skill = player.playerSkill.skills.get(i);
            if (skill.point > 0 && skill.template.id != Skill.TU_SAT && skill.template.id != Skill.TROI) {
                skillList.add(skill);
            }
        }
        int[][] skillTemp = new int[skillList.size()][3];
        for (byte i = 0; i < skillList.size(); i++) {
            Skill skill = skillList.get(i);
            if (skill.point > 0 && skill.template.id != Skill.TU_SAT && skill.template.id != Skill.TROI) {
                skillTemp[i][0] = skill.template.id;
                skillTemp[i][1] = skill.point;
                skillTemp[i][2] = skill.coolDown;
            }
        }
        BossData bossDataClone = new BossData(
                player.name,
                player.gender,
                new short[]{player.getHead(), player.getBody(), player.getLeg(), player.getFlagBag(), player.getAura(), player.getEffFront()},
                Functions.maxint(player.nPoint.dame * 10L),
                new int[]{Functions.maxint(player.nPoint.hpMax * 10L)},
                new int[]{140},
                skillTemp,
                new String[]{"|-2|Boss nhân bản đã xuất hiện rồi"}, //text chat 1
                new String[]{"|-1|Ta sẽ thay thế ngươi, haha"}, //text chat 2
                new String[]{"|-1|Lần khác ta sẽ xử đẹp ngươi"}, //text chat 3
                60
        );

        try {
            new NhanBan(player, bossDataClone);
            EffectSkillService.gI().setPKCommeson(player, 300000);
            player.lastPkCommesonTime = System.currentTimeMillis();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendBigBoss(Zone zone, int action, int size, int id, int dame) {
        Message msg = null;
        try {
            msg = new Message(102);
            msg.writer().writeByte(action);
            if (action != 6 && action != 7) {
                msg.writer().writeByte(size); // SIZE PLAYER ATTACK
                msg.writer().writeInt(id); // PLAYER ID
                msg.writer().writeInt(dame); // DAME
            }
            sendMessAllPlayerInMap(zone, msg);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public void sendBigBoss2(Zone zone, int action, Mob bigboss) {
        Message msg = null;
        try {
            msg = new Message(101);
            msg.writer().writeByte(action);
            msg.writer().writeShort(bigboss.location.x);
            msg.writer().writeShort(bigboss.location.y);
            sendMessAllPlayerInMap(zone, msg);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public void sendBigBoss2(Player player, int action, Mob bigboss) {
        Message msg = null;
        try {
            msg = new Message(101);
            msg.writer().writeByte(action);
            msg.writer().writeShort(bigboss.location.x);
            msg.writer().writeShort(bigboss.location.y);
            player.sendMessage(msg);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public void sendMabuHold(Player player, int action, short x, short y) {
        Message msg;
        try {
            player.location.x = x;
            player.location.y = y;
            if (action == 0) {
                setPos(player, x, y);
            }
            msg = new Message(52);
            msg.writer().writeByte(action); // 0 false, 1 true
            msg.writer().writeInt((int) player.id);
            msg.writer().writeShort(x);
            msg.writer().writeShort(y);
            sendMessAllPlayerInMap(player, msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendMabuHoldToMe(Player player, Player plReceive, int action, short x, short y) {
        Message msg;
        try {
            msg = new Message(52);
            msg.writer().writeByte(action); // 0 false, 1 true
            msg.writer().writeInt((int) player.id);
            msg.writer().writeShort(x);
            msg.writer().writeShort(y);
            plReceive.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendEffMabuHoldAllPlayerMapToMe(Player pl) {
        if (pl == null || pl.zone == null) {
            return;
        }
        for (Player plM : pl.zone.getPlayers()) {
            if (plM.isPl()) {
                if (plM.maBuHold != null) {
                    sendMabuHoldToMe(plM, pl, 1, (short) plM.maBuHold.x, (short) plM.maBuHold.y);
                }
            }
        }
    }

    public void sendEffMabuEat(Player player, Player plTarget) {
        Message msg;
        try {
            msg = new Message(52);
            msg.writer().writeByte(2);
            msg.writer().writeInt((int) player.id);
            msg.writer().writeInt((int) plTarget.id);
            sendMessAllPlayerInMap(player, msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendMabuEat(Player player, Player plTarget) {
        if (plTarget.isPl() && plTarget.maBuHold == null) {
            MaBuHold mabuHold = player.zone.getMaBuHold();
            if (mabuHold != null) {
                Executors.newSingleThreadExecutor().submit(() -> {
                    int zoneId = player.zone.zoneId;
                    player.zone.setMaBuHold(mabuHold.slot, zoneId, plTarget);
                    sendEffMabuEat(player, plTarget);
                    Functions.sleep(3000);
                    if (player.zone == null || player.zone.map.mapId != 127) {
                        return;
                    }
                    Zone zone = MapService.gI().getMapById(128).zones.get(zoneId);
                    ChangeMapService.gI().changeMap(plTarget, zone, -1, 336);
                    Functions.sleep(500);
                    plTarget.isMabuHold = false;
                    if (plTarget.effectSkill != null && !plTarget.effectSkill.isShielding) {
                        EffectSkillService.gI().setMabuHold(plTarget, mabuHold);
                        Functions.sleep(1500);
                        if (plTarget.fusion != null && plTarget.pet != null && plTarget.fusion.typeFusion != ConstPlayer.NON_FUSION) {
                            plTarget.pet.unFusion();
                        }
                    }
                });
            }
        }
    }

    public void sendMabuAttackSkill(Player player) {
        Message msg;
        try {
            int skillId[] = {0, 1, 3};
            int skill = skillId[Util.nextInt(3)];
            if (Util.isTrue(1, 10)) {
                skill = 2;
            }
            msg = new Message(51);
            msg.writer().writeInt((int) player.id); // charid
            msg.writer().writeByte(skill); // skill id 0 1 2 3
            msg.writer().writeShort(player.location.x); // x
            msg.writer().writeShort(player.location.y); // y
            msg.writer().writeByte(player.zone.getNotBosses().size()); // số player
            for (Player plM : player.zone.getNotBosses()) {
                msg.writer().writeInt((int) plM.id);
                int damage = plM.injured(player, player.nPoint.dame + plM.nPoint.hp / 10, true, false);
                msg.writer().writeInt(damage);
            }
            sendMessAllPlayerInMap(player, msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //========================READ OPT========================
    public Message messageReadOpt(byte command) throws IOException {
        Message ms = new Message(24);
        ms.writer().writeByte(command);
        return ms;
    }

    public void sendMessageServer(String data) {
        Message msg;
        try {
            msg = messageReadOpt((byte) 4);
            msg.writer().writeUTF(data);
            sendMessAllPlayer(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendHideNpc(Player player, int npcId, boolean isHide) {
        Message msg;
        try {
            msg = new Message(-73);
            msg.writer().writeByte(npcId);
            msg.writer().writeByte(isHide ? 0 : 1);
            player.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendTopRank(Player pl) {
        Message msg = null;
        try {
            msg = new Message(-119);
            msg.writer().writeInt(pl.superRank.rank);
            pl.sendMessage(msg);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (msg != null) {
                msg.cleanup();
                msg = null;
            }
        }
    }

    public void moveFast(Player pl, int x, int y) {
        Message msg;
        try {
            msg = new Message(58);
            msg.writer().writeInt((int) pl.id);
            msg.writer().writeShort(x);
            msg.writer().writeShort(y);
            msg.writer().writeInt((int) pl.id);
            pl.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void dropAndPickItemDNC(Player pl, int itemId) {
        ItemMap item = new ItemMap(pl.zone, itemId, 1, pl.location.x, pl.location.y, pl.id);
        item.options.add(new ItemOption(71 - (itemId - 220), 0));
        Service.gI().dropItemMap(pl.zone, item);
        pl.zone.pickItem(pl, item.itemMapId);
    }

    public void dropAndPickItem(Player pl, int itemId, int quantity) {
        ItemMap item = new ItemMap(pl.zone, itemId, quantity, pl.location.x, pl.location.y, pl.id);
        Service.gI().dropItemMap(pl.zone, item);
        pl.zone.pickItem(pl, item.itemMapId);
    }

    public void sendTypePK(Player player, Player boss) {
        Message msg;
        try {
            msg = Service.gI().messageSubCommand((byte) 35);
            msg.writer().writeInt((int) boss.id);
            msg.writer().writeByte(3);
            player.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Get Top
    public void playerInfoUpdate(Player pl, Player plR, String plName, int plHead, int plBody, int plLeg) {
        if (pl == null) {
            return;
        }
        Message msg = null;
        try {
            msg = messageSubCommand((byte) 7);
            msg.writer().writeInt((int) pl.id);
            if (pl.clan != null) {
                msg.writer().writeInt(pl.clan.id);
            } else if (pl.isCopy) {
                msg.writer().writeInt(-2);
            } else {
                msg.writer().writeInt(-1);
            }
            msg.writer().writeByte(Service.gI().getCurrLevel(pl));
            msg.writer().writeBoolean(false);
            msg.writer().writeByte(pl.typePk);
            msg.writer().writeByte(pl.gender);
            msg.writer().writeByte(pl.gender);
            msg.writer().writeShort(plHead);
            msg.writer().writeUTF(plName);
            msg.writer().writeInt(pl.nPoint.hp);
            msg.writer().writeInt(pl.nPoint.hpMax);
            msg.writer().writeShort(plBody);
            msg.writer().writeShort(plLeg);
            int flagbag = pl.getFlagBag();
            if (pl.isPl() && plR.getSession().version >= 220) {
                switch (flagbag) {
                    case 83 ->
                        flagbag = 205;
                }
            }
            msg.writer().writeByte(flagbag); //bag
            msg.writer().writeByte(-1);
            msg.writer().writeShort(pl.location.x);
            msg.writer().writeShort(pl.location.y);
            msg.writer().writeShort(0);
            msg.writer().writeShort(0);
            msg.writer().writeByte(0);
            msg.writer().writeShort(pl.getAura()); //idauraeff
            msg.writer().writeByte(pl.getEffFront()); //seteff
            msg.writer().writeShort(pl.getHat()); //id hat
            plR.sendMessage(msg);
        } catch (IOException e) {
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }

    }

    public void sendLoginFail(MySession session, boolean isLoggingIn) {
        Message msg;
        try {
            msg = new Message(-102);
            msg.writer().writeByte(isLoggingIn ? 1 : 0);
            session.doSendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stealMoney(Player pl, int stealMoney) {//danh cho boss an trom
        Message msg;
        try {
            msg = new Message(95);
            msg.writer().writeInt(stealMoney);
            pl.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void LogicEffect(Player pl, int id, int layer, int loop, int loopcount, int stand, int times) {
        try {
            // Gửi hiệu ứng
            Message msg = new Message(-128);
            msg.writer().writeByte(0);
            msg.writer().writeInt((int) pl.id);
            msg.writer().writeShort(id);
            msg.writer().writeByte(layer);
            msg.writer().writeByte(loop);
            msg.writer().writeShort(loopcount);
            msg.writer().writeByte(stand);

            sendMessAllPlayerInMap(pl.zone, msg);
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.schedule(() -> removeEffectChar(pl, id), times, TimeUnit.MILLISECONDS);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void removeEffectChar(Player pl, int id) {
        try {
            // Gửi thông báo xóa hiệu ứng
            Message msg = new Message(-128);
            msg.writer().writeByte(1); // Thông báo xóa hiệu ứng
            msg.writer().writeInt((int) pl.id);
            msg.writer().writeShort(id);

            sendMessAllPlayerInMap(pl.zone, msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendServerMessage(Player player, String text) {
        Message msg = null;
        try {
            msg = new Message(-25);
            msg.writer().writeUTF(text);
            player.sendMessage(msg);
        } catch (IOException e) {
            Logger.logException(Service.class, e);
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public void sendServerAlert(Player player, String text) {
        Message msg = null;
        try {
            msg = new Message(94);
            msg.writer().writeUTF(text);
            player.sendMessage(msg);
        } catch (IOException e) {
            Logger.logException(Service.class, e);
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public void sendDialogMessage(Player pl, String text) {
        Message msg = null;
        try {
            msg = new Message(-26);
            msg.writer().writeUTF(text);
            pl.sendMessage(msg);
        } catch (IOException e) {
            Logger.logException(Service.class, e);
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public void sendDialogMessage(MySession session, String text) {
        Message msg = null;
        try {
            msg = new Message(-26);
            msg.writer().writeUTF(text);
            session.sendMessage(msg);
        } catch (IOException e) {
            Logger.logException(Service.class, e);
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public void updatePlayerTotalDamage(Player pl) {
        java.sql.PreparedStatement ps = null;
        try (java.sql.Connection con = LocalManager.getConnection();) {
            ps = con.prepareStatement("update player SET total_damage_maydam = ? WHERE id = ?");
            ps.setLong(1, pl.total_damage_maydam);
            ps.setLong(2, pl.id);

            ps.executeUpdate();

        } catch (Exception e) {
            Logger.logException(PlayerDAO.class, e, "Lỗi update Total dame");
        }
    }

    public void updatePlayerPointMayDam(Player pl) {
        java.sql.PreparedStatement ps = null;
        try (java.sql.Connection con = LocalManager.getConnection()) {
            ps = con.prepareStatement("UPDATE player SET point_maydam = ? WHERE id = ?");
            ps.setInt(1, pl.point_maydam);
            ps.setLong(2, pl.id);
            ps.executeUpdate();
        } catch (Exception e) {
            Logger.logException(PlayerDAO.class, e, "Lỗi update point_maydam");
        }
    }

    public void sendDanhQuaiNhanNgoc(Player player) {
        if (player.event.luotNhanNgocMienPhi == 1) {
            Service.gI().sendThongBao(player, "Hôm nay bạn sẽ nhận được từ 1 đến 2 viên ngọc khi tiêu diệt 1 con quái");
        }
    }

    public static void sendIcon(MySession session, int id) {
        try {
            Message msg = new Message(-122);
            msg.writer().writeInt(id);
            if (id == 5427) {
                msg.writer().writeInt(0);
            } else {
                msg.writer().writeInt(0);
            }
            session.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            Logger.logException(DataGame.class, e);
        }
    }

    public void sendInfoCharMoiToMe(Player plReceive, BotAttackplayer bot) {
        if (plReceive == null || plReceive.session == null || bot == null) {
            return;
        }

        Message msg = null;
        try {
            msg = new Message(-5);
            msg.writer().writeInt((int) bot.id);
            msg.writer().writeInt(-1);

            msg.writer().writeByte(Service.gI().getCurrLevel(bot));

            msg.writer().writeBoolean(false);

            msg.writer().writeByte(bot.typePk);

            msg.writer().writeByte(bot.gender);
            msg.writer().writeByte(bot.gender);

            msg.writer().writeShort(bot.head);
            msg.writer().writeUTF(bot.name);
            msg.writer().writeInt(bot.nPoint.hp);
            msg.writer().writeInt(bot.nPoint.hpMax);
            msg.writer().writeShort(bot.body);
            msg.writer().writeShort(bot.leg);

            int flagbag = bot.getFlagBag();
            msg.writer().writeByte(flagbag);
            msg.writer().writeByte(-1);

            msg.writer().writeShort(bot.location.x);
            msg.writer().writeShort(bot.location.y);

            msg.writer().writeShort(0);
            msg.writer().writeShort(0);

            msg.writer().writeByte(0);

            msg.writer().writeByte(0);

            msg.writer().writeByte(bot.effectSkill != null && bot.effectSkill.isMonkey ? 1 : 0);

            msg.writer().writeShort(0);

            msg.writer().writeByte(bot.cFlag);

            msg.writer().writeByte(0);

            msg.writer().writeShort(bot.getAura());

            msg.writer().writeByte(bot.getEffFront());

            msg.writer().writeShort(bot.getHat());

            plReceive.sendMessage(msg);

            msg.cleanup();

            if (bot.isDie()) {
                msg = new Message(-8);
                msg.writer().writeInt((int) bot.id);
                msg.writer().writeByte(0);
                msg.writer().writeShort(bot.location.x);
                msg.writer().writeShort(bot.location.y);
                plReceive.sendMessage(msg);
                msg.cleanup();
            }
        } catch (Exception e) {
            Logger.logException(Service.class, e);
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public void sendAppear(BotAttackplayer bot, Player player) {
        if (bot == null || bot.zone == null || player == null || player.session == null) {
            return;
        }

        sendInfoCharMoiToMe(player, bot);
    }

    public void sendDisappear(BotAttackplayer bot, Player player) {
        if (player == null || player.session == null || bot == null) {
            return;
        }

        Message msg = null;
        try {
            msg = new Message(-6);
            msg.writer().writeInt((int) bot.id);
            player.session.sendMessage(msg);
            Logger.error("Đã gửi hiệu ứng biến mất của bot " + bot.name + " tới " + player.name);
        } catch (Exception e) {
            Logger.logException(Service.class, e);
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public void sendPlayerInfo(Player player) {
        if (player == null || player.session == null) {
            return;
        }

        Message msg = null;
        try {
            msg = messageSubCommand((byte) 7);
            msg.writer().writeInt((int) player.id);
            msg.writer().writeInt(player.clan != null ? player.clan.id : -1);
            msg.writer().writeByte(player.typePk);
            msg.writer().writeByte(player.gender);
            msg.writer().writeShort(player.getHead());
            msg.writer().writeUTF(player.name);
            msg.writer().writeInt(player.nPoint.hp);
            msg.writer().writeInt(player.nPoint.hpMax);
            msg.writer().writeShort(player.getBody());
            msg.writer().writeShort(player.getLeg());
            msg.writer().writeByte(player.getFlagBag());
            msg.writer().writeByte(-1);
            msg.writer().writeShort(player.location.x);
            msg.writer().writeShort(player.location.y);
            msg.writer().writeShort(0);
            msg.writer().writeShort(0);
            msg.writer().writeByte(0);
            sendMessAllPlayer(msg);
        } catch (Exception e) {
            Logger.logException(Service.class, e);
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public void loadMob(Player p, GauTuongCuop gauTuong) {
        try {
            Message msg = new Message(-13);
            msg.writer().writeByte(5);
            msg.writer().writeByte(1);
            msg.writer().writeShort(gauTuong.id);
            msg.writer().writeByte(gauTuong.tempId);
            msg.writer().writeByte(gauTuong.status);
            msg.writer().writeShort(gauTuong.location.x);
            msg.writer().writeShort(gauTuong.location.y);
            msg.writer().writeInt(gauTuong.point.gethp());
            msg.writer().writeInt(gauTuong.point.getHpFull());
            msg.writer().writeByte(0);
            msg.writer().writeInt(gauTuong.level);
            msg.writer().writeBoolean(true);
            msg.writer().writeByte(gauTuong.type);
            p.session.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
