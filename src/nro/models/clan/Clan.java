package nro.models.clan;

import com.mysql.jdbc.log.Log;
import nro.models.data.LocalManager;
import nro.models.map.phoban.RedRibbonHQ;
import nro.models.services.ClanService;
import java.util.ArrayList;
import java.util.List;
import nro.models.player.Player;
import nro.models.server.Client;
import nro.models.services.Service;
import nro.models.network.Message;
import nro.models.database.MrBlue;
import nro.models.utils.Logger;
import nro.models.utils.Util;
import java.sql.Connection;
import java.sql.PreparedStatement;
import nro.models.managers.TopKhiGasHuyDiet;
import nro.models.map.phoban.BanDoKhoBau;
import nro.models.map.phoban.SnakeWay;
import nro.models.map.phoban.DestronGas;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import nro.models.server.Manager;
import nro.models.services.TaskService;
import nro.models.utils.TimeUtil;

public class Clan {

    public static int NEXT_ID = 0;

    public int clanMessageId = 0;
    private final List<ClanMessage> clanMessages;

    public static final byte LEADER = 0;
    public static final byte DEPUTY = 1;
    public static final byte MEMBER = 2;

    public int id;
    public int imgId;
    public String name;
    public String name2;
    public String slogan;
    public int createTime;
    public long powerPoint;
    public byte maxMember;
    public int level;
    public boolean active;
    public int capsuleClan;

    public long lastTimeOpenDoanhTrai;
    public boolean haveGoneDoanhTrai;
    public RedRibbonHQ doanhTrai;
    public Player playerOpenDoanhTrai;

    public final List<ClanMember> members;
    public final List<Player> membersInGame;

    public BanDoKhoBau BanDoKhoBau;
    public long lastTimeOpenBanDoKhoBau;
    public Player playerOpenBanDoKhoBau;
    public long timeOpenBanDoKhoBau;
    public long thoiGianHoanThanhBDKB;
    public int levelDoneBanDoKhoBau;

    public SnakeWay ConDuongRanDoc;
    public long lastTimeOpenConDuongRanDoc;
    public Player playerOpenConDuongRanDoc;
    public long thoiGianHoanThanhCDRD;
    public int levelDoneCDRD;

    public DestronGas KhiGasHuyDiet;
    public long lastTimeOpenKhiGasHuyDiet;
    public Player playerOpenKhiGasHuyDiet;
    public int timesPerDayKGHD;
    public long thoiGianHoanThanhKhiGas;
    public int levelDoneKhiGas;

    public long timeUpdateClan;

    public Clan() {
        this.id = NEXT_ID++;
        this.name = "";
        this.name2 = "";
        this.slogan = "";
        this.maxMember = 10;
        this.level = 1;
        this.createTime = (int) (System.currentTimeMillis() / 1000);
        this.members = new ArrayList<>();
        this.membersInGame = new ArrayList<>();
        this.clanMessages = new ArrayList<>();
    }

    public boolean canUpdateClan(Player player) {
        if (Util.canDoWithTime(timeUpdateClan, 60000)) {
            timeUpdateClan = System.currentTimeMillis();
            return true;
        }
        Service.gI().sendThongBao(player, "Vui lòng đợi " + TimeUtil.getTimeLeft(timeUpdateClan, 60) + " nữa");
        return false;
    }

    public ClanMember getLeader() {
        for (ClanMember cm : members) {
            if (cm.role == LEADER) {
                return cm;
            }
        }
        ClanMember cm = new ClanMember();
        cm.name = "Bang chủ";
        return cm;
    }

    public byte getRole(Player player) {
        for (ClanMember cm : members) {
            if (cm.id == player.id) {
                return cm.role;
            }
        }
        return -1;
    }

    public boolean isLeader(Player player) {
        for (ClanMember cm : members) {
            if (cm.id == player.id && cm.role == LEADER) {
                return true;
            }
        }
        return false;
    }

    public boolean isDeputy(Player player) {
        for (ClanMember cm : members) {
            if (cm.id == player.id && cm.role == DEPUTY) {
                return true;
            }
        }
        return false;
    }
//

    public void addSMTNClan(Player plOri, long param) {
        for (int i = this.membersInGame.size() - 1; i >= 0; i--) {
            Player pl = this.membersInGame.get(i);

            // Kiểm tra các điều kiện
            if (pl != null && !plOri.equals(pl) && pl.zone != null && plOri.zone.equals(pl.zone)) {
                int levelOri = Service.gI().getCurrLevel(plOri); // Cấp độ người khởi xướng
                int levelTarget = Service.gI().getCurrLevel(pl); // Cấp độ mục tiêu
                int levelDiff = Math.abs(levelTarget - levelOri); // Chênh lệch cấp độ
                long tnsm = param / (levelDiff == 0 ? 1 : levelDiff);
                // Thêm SMTN cho người nhận
                Service.gI().addSMTN(pl, (byte) 1, tnsm, false);
            }
        }
    }

    public void sendMessageClan(ClanMessage cmg) {
        Message msg;
        try {
            msg = new Message(-51);
            msg.writer().writeByte(cmg.type);
            msg.writer().writeInt(cmg.id);
            msg.writer().writeInt(cmg.playerId);
            if (cmg.type == 2) {
                msg.writer().writeUTF(cmg.playerName + " (" + Util.numberToMoney(cmg.playerPower) + ")");
            } else {
                msg.writer().writeUTF(cmg.playerName);
            }
            msg.writer().writeByte(cmg.role);
            msg.writer().writeInt(cmg.time);
            if (cmg.type == 0) {
                msg.writer().writeUTF(cmg.text);
                msg.writer().writeByte(cmg.color);
            } else if (cmg.type == 1) {
                msg.writer().writeByte(cmg.receiveDonate);
                msg.writer().writeByte(cmg.maxDonate);
                msg.writer().writeByte(cmg.isNewMessage);
            }
            for (Player pl : this.membersInGame) {
                pl.sendMessage(msg);
            }
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void addClanMessage(ClanMessage cmg) {
        this.clanMessages.add(0, cmg);
        if (clanMessages.size() > 20) {
            for (int i = clanMessages.size() - 1; i >= 20; i--) {
                clanMessages.remove(i).dispose();
            }
        }
    }

    public ClanMessage getClanMessage(int clanMessageId) {
        for (ClanMessage cmg : this.clanMessages) {
            if (cmg.id == clanMessageId) {
                return cmg;
            }
        }
        return null;
    }

    public List<ClanMessage> getCurrClanMessages() {
        List<ClanMessage> list = new ArrayList();
        if (this.clanMessages.size() <= 20) {
            list.addAll(this.clanMessages);
        } else {
            for (int i = 0; i < 20; i++) {
                list.add(this.clanMessages.get(i));
            }
        }
        return list;
    }

    public void sendMyClanForAllMember() {
        for (Player pl : this.membersInGame) {
            if (pl != null) {
                ClanService.gI().sendMyClan(pl);
            }
        }
    }

    public void sendFlagBagForAllMember() {
        for (Player pl : this.membersInGame) {
            if (pl != null) {
                Service.gI().sendFlagBag(pl);
            }
        }
    }

    public void addMemberOnline(Player player) {
        this.membersInGame.add(player);
    }

    public void removeMemberOnline(ClanMember cm, Player player) {
        if (player != null) {
            this.membersInGame.remove(player);
        }
        if (cm != null) {
            for (int i = this.membersInGame.size() - 1; i >= 0; i--) {
                if (this.membersInGame.get(i).id == cm.id) {
                    this.membersInGame.remove(i);
                    break;
                }
            }
        }
    }

    public Player getPlayerOnline(int playerId) {
        for (Player player : this.membersInGame) {
            if (player.id == playerId) {
                return player;
            }
        }

        Player playeroffline = MrBlue.loadById(playerId);
        if (playeroffline != null) {
            return playeroffline;
        }

        return null;
    }

    //load db danh sách member
    public void addClanMember(ClanMember cm) {
        this.members.add(cm);
    }

    //thêm vào khi player tạo mới clan or mới vào clan
    public void addClanMember(Player player, byte role) {
        ClanMember cm = new ClanMember(player, this, role);
        this.members.add(cm);
        player.clanMember = cm;
    }

    //xóa khi member rời clan or bị kích
    public void removeClanMember(ClanMember cm) {
        this.members.remove(cm);
        cm.dispose();
    }

    public byte getCurrMembers() {
        return (byte) this.members.size();
    }

    public List<ClanMember> getMembers() {
        return this.members;
    }

    public ClanMember getClanMember(int memberId) {
        for (ClanMember cm : members) {
            if (cm.id == memberId) {
                return cm;
            }
        }
        return null;
    }

    public void reloadClanMember() {
        for (ClanMember cm : this.members) {
            Player pl = Client.gI().getPlayer(cm.id);
            if (pl != null) {
                cm.powerPoint = pl.nPoint.power;
            }
        }
    }

    public void insert() {
        JSONArray dataArray = new JSONArray();
        JSONObject dataObject = new JSONObject();
        for (ClanMember cm : this.members) {
            dataObject.put("id", cm.id);
            dataObject.put("name", cm.name);
            dataObject.put("head", cm.head);
            dataObject.put("body", cm.body);
            dataObject.put("leg", cm.leg);
            dataObject.put("role", cm.role);
            dataObject.put("donate", cm.donate);
            dataObject.put("receive_donate", cm.receiveDonate);
            dataObject.put("member_point", cm.memberPoint);
            dataObject.put("clan_point", cm.clanPoint);
            dataObject.put("join_time", cm.joinTime);
            dataObject.put("ask_pea_time", cm.timeAskPea);
            dataObject.put("power", cm.powerPoint);
            dataArray.add(dataObject.toJSONString());
            dataObject.clear();
        }

        String member = dataArray.toJSONString();
        dataArray.clear();

        String topBanDoKhoBau = "[" + levelDoneBanDoKhoBau + "," + thoiGianHoanThanhBDKB + "]";

        String thongTinLeader = "[" + getLeader().id + "," + getLeader().name + "," + getLeader().head + "," + getLeader().body + "," + getLeader().leg + "]";

        String top = dataArray.toJSONString();

        PreparedStatement ps = null;
        try (Connection con = LocalManager.getConnection();) {
            ps = con.prepareStatement("insert into clan (id, name, name_2, slogan, img_id, power_point, max_member, clan_point, level, members, tops, thanhTichBDKB, thongTinLeader) "
                    + "values (?,?,?,?,?,?,?,?,?,?,?,?,?)");
            ps.setInt(1, this.id);
            ps.setString(2, this.name);
            ps.setString(3, this.name2);
            ps.setString(4, this.slogan);
            ps.setInt(5, this.imgId);
            ps.setLong(6, this.powerPoint);
            ps.setByte(7, this.maxMember);
            ps.setInt(8, this.capsuleClan);
            ps.setInt(9, this.level);
            ps.setString(10, member);
            ps.setString(11, top);
            ps.setString(12, topBanDoKhoBau);
            ps.setString(13, thongTinLeader);
            ps.executeUpdate();
            ps.close();
        } catch (Exception e) {
            Logger.logException(Clan.class, e, "Có lỗi khi insert clan vào db");
        } finally {
            try {
                ps.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    public void update() {
        JSONArray dataArray = new JSONArray();
        JSONObject dataObject = new JSONObject();
        for (ClanMember cm : this.members) {
            dataObject.put("id", cm.id);
            dataObject.put("name", cm.name);
            dataObject.put("head", cm.head);
            dataObject.put("body", cm.body);
            dataObject.put("leg", cm.leg);
            dataObject.put("role", cm.role);
            dataObject.put("donate", cm.donate);
            dataObject.put("receive_donate", cm.receiveDonate);
            dataObject.put("member_point", cm.memberPoint);
            dataObject.put("clan_point", cm.clanPoint);
            dataObject.put("join_time", cm.joinTime);
            dataObject.put("ask_pea_time", cm.timeAskPea);
            dataObject.put("power", cm.powerPoint);
            dataArray.add(dataObject.toJSONString());
            dataObject.clear();
        }

        String member = dataArray.toJSONString();
        String topBanDoKhoBau = "[" + levelDoneBanDoKhoBau + "," + thoiGianHoanThanhBDKB + "]";

        String thongTinLeader = "[" + getLeader().id + "," + getLeader().name + "," + getLeader().head + "," + getLeader().body + "," + getLeader().leg + "]";

        dataArray.clear();

        PreparedStatement ps = null;
        try (Connection con = LocalManager.getConnection();) {
            ps = con.prepareStatement("update clan set slogan = ?, img_id = ?, power_point = ?, max_member = ?, clan_point = ?, "
                    + "level = ?, members = ?, name_2 = ?, tops = ?, thanhTichBDKB = ?, thongTinLeader = ? where id = ? limit 1");
            ps.setString(1, this.slogan);
            ps.setInt(2, this.imgId);
            ps.setLong(3, this.powerPoint);
            ps.setByte(4, this.maxMember);
            ps.setInt(5, this.capsuleClan);
            ps.setInt(6, this.level);
            ps.setString(7, member);
            ps.setString(8, this.name2);
            ps.setString(9, "cc");
            ps.setString(10, topBanDoKhoBau);
            ps.setString(11, thongTinLeader);
            ps.setInt(12, this.id);
            ps.executeUpdate();
            ps.close();
        } catch (Exception e) {
            Logger.logException(Clan.class, e, "Có lỗi khi insert clan vào db");
        } finally {
            try {
                ps.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void deleteDB(int id) {
        PreparedStatement ps;
        try (Connection con = LocalManager.getConnection();) {
            ps = con.prepareStatement("delete from clan where id = ?");
            ps.setInt(1, id);
            ps.executeUpdate();
            ps.close();
        } catch (Exception e) {
            Logger.logException(Clan.class, e, "Có lỗi khi delete clan");
        }
    }

    public void updatethanhTichBDKB(int clanId) {
        String topBanDoKhoBau = "[" + levelDoneBanDoKhoBau + "," + thoiGianHoanThanhBDKB + "]";
        try (Connection con = LocalManager.gI().getConnection(); PreparedStatement ps = con.prepareStatement("UPDATE clan SET thanhTichBDKB = ? WHERE id = ? LIMIT 1")) {
            ps.setString(1, topBanDoKhoBau);
            ps.setInt(2, clanId);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void updatethanhTichBDKBForLeader() {
        try (Connection con = LocalManager.gI().getConnection(); PreparedStatement ps = con.prepareStatement(
                "UPDATE player SET thanhTichBang = ? WHERE id = ? LIMIT 1")) {

            String data = "[" + this.name + "," + this.levelDoneBanDoKhoBau + "," + this.thoiGianHoanThanhBDKB + "," + System.currentTimeMillis() + "]";
            ps.setString(1, data);
            ps.setInt(2, this.getLeader().id);
            ps.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void updatethanhTichKhiGasForLeader() {
        String TopKhiGasHuyDiet = "[" + this.name + "," + this.levelDoneKhiGas + "," + thoiGianHoanThanhKhiGas + "," + System.currentTimeMillis() + "]";
        try (Connection con = LocalManager.gI().getConnection(); PreparedStatement ps = con.prepareStatement("UPDATE player SET thanhTichKhiGas = ? WHERE id = ? LIMIT 1")) {
            ps.setString(1, TopKhiGasHuyDiet);
            ps.setInt(2, this.getLeader().id);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void updatethanhTichCDRDForLeader() {
        String TopConDuongRanDoc = "[" + this.name + "," + this.levelDoneCDRD + "," + thoiGianHoanThanhCDRD + "," + System.currentTimeMillis() + "]";
        try (Connection con = LocalManager.gI().getConnection(); PreparedStatement ps = con.prepareStatement("UPDATE player SET thanhTichCDRD = ? WHERE id = ? LIMIT 1")) {
            ps.setString(1, TopConDuongRanDoc);
            ps.setInt(2, this.getLeader().id);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void updateThongTinLeader(int clanId) {
        String thongTinLeader = "[" + getLeader().id + "," + getLeader().name + "," + getLeader().head + "," + getLeader().body + "," + getLeader().leg + "]";
        try (Connection con = LocalManager.gI().getConnection(); PreparedStatement ps = con.prepareStatement("UPDATE clan SET thanhTichBDKB = ? WHERE id = ? LIMIT 1")) {
            ps.setString(1, thongTinLeader);
            ps.setInt(2, clanId);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
