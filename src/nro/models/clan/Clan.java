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
import nro.models.database.MrFinn;
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
import nro.models.task.TaskConfig;
import java.time.DayOfWeek;
import java.time.LocalDate;

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

    /**
     * Hợp đồng bang tuần. State này được lưu trong cột tops để không bắt buộc
     * triển khai migration cho các server đang chạy schema cũ.
     */
    public long weeklyContractWeek;
    public int weeklyContractProgress;
    public int weeklyContractTarget;
    public int weeklyContractReward;
    public boolean weeklyContractRewarded;
    public long lastTimeOpenGauTuongCuop;
    public long lastTimeRally;

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
        this.weeklyContractTarget = TaskConfig.getClanWeeklyTarget();
        this.weeklyContractReward = TaskConfig.getClanWeeklyReward();
    }

    private long currentContractWeek() {
        return LocalDate.now(TimeUtil.VIETNAM_ZONE)
                .with(DayOfWeek.MONDAY).toEpochDay();
    }

    public synchronized void ensureWeeklyContract() {
        long currentWeek = currentContractWeek();
        if (weeklyContractWeek != currentWeek) {
            weeklyContractWeek = currentWeek;
            weeklyContractProgress = 0;
            weeklyContractTarget = TaskConfig.getClanWeeklyTarget();
            weeklyContractReward = TaskConfig.getClanWeeklyReward();
            weeklyContractRewarded = false;
            weeklyContractWeek = currentWeek;
        }
        if (weeklyContractTarget <= 0) {
            weeklyContractTarget = TaskConfig.getClanWeeklyTarget();
        }
        if (weeklyContractReward < 0) {
            weeklyContractReward = TaskConfig.getClanWeeklyReward();
        }
    }

    public synchronized void loadWeeklyState(String raw) {
        try {
            Object parsed = org.json.simple.JSONValue.parse(raw);
            if (!(parsed instanceof JSONObject state)) {
                return;
            }
            Object value = state.get("week");
            if (value != null) {
                weeklyContractWeek = Long.parseLong(value.toString());
            }
            value = state.get("progress");
            if (value != null) {
                weeklyContractProgress = Integer.parseInt(value.toString());
            }
            value = state.get("target");
            if (value != null) {
                weeklyContractTarget = Integer.parseInt(value.toString());
            }
            value = state.get("reward");
            if (value != null) {
                weeklyContractReward = Integer.parseInt(value.toString());
            }
            value = state.get("rewarded");
            if (value != null) {
                weeklyContractRewarded = Boolean.parseBoolean(value.toString());
            }
            value = state.get("lastGau");
            if (value != null) {
                lastTimeOpenGauTuongCuop = Long.parseLong(value.toString());
            }
        } catch (RuntimeException ignored) {
            // Dữ liệu tops cũ (ví dụ "cc") không phải state hợp đồng.
        }
    }

    public synchronized String getWeeklyStateForPersistence() {
        ensureWeeklyContract();
        JSONObject state = new JSONObject();
        state.put("week", weeklyContractWeek);
        state.put("progress", weeklyContractProgress);
        state.put("target", weeklyContractTarget);
        state.put("reward", weeklyContractReward);
        state.put("rewarded", weeklyContractRewarded);
        state.put("lastGau", lastTimeOpenGauTuongCuop);
        return state.toJSONString();
    }

    /**
     * Cộng tiến độ hợp đồng khi thành viên hạ quái. Chỉ thưởng một lần mỗi
     * tuần và ghi lại vào quỹ bang, tránh phát thưởng theo từng thành viên.
     */
    public synchronized void addWeeklyContractKill(Player player) {
        if (player == null || player.clan != this || !TaskConfig.isClanWeeklyEnabled()) {
            return;
        }
        ensureWeeklyContract();
        if (weeklyContractRewarded || weeklyContractProgress >= weeklyContractTarget) {
            return;
        }
        weeklyContractProgress++;
        int milestone = Math.max(1, weeklyContractTarget / 10);
        boolean shouldPersist = weeklyContractProgress % milestone == 0;
        if (weeklyContractProgress >= weeklyContractTarget) {
            weeklyContractProgress = weeklyContractTarget;
            weeklyContractRewarded = true;
            capsuleClan += weeklyContractReward;
            ClanMember leader = getLeader();
            ClanMessage message = new ClanMessage(this);
            message.type = 0;
            message.playerId = leader.id;
            message.playerName = leader.name;
            message.role = leader.role;
            message.text = "Hợp đồng bang tuần đã hoàn thành, quỹ bang nhận "
                    + Util.numberToMoney(weeklyContractReward) + " Capsule Bang.";
            message.color = ClanMessage.RED;
            addClanMessage(message);
            sendMessageClan(message);
            shouldPersist = true;
        } else if (shouldPersist) {
            ClanMember member = getClanMember((int) player.id);
            ClanMessage message = new ClanMessage(this);
            message.type = 0;
            message.playerId = member == null ? (int) player.id : member.id;
            message.playerName = member == null ? player.name : member.name;
            message.role = member == null ? MEMBER : member.role;
            message.text = "Hợp đồng bang tuần: " + weeklyContractProgress + "/" + weeklyContractTarget;
            message.color = 0;
            addClanMessage(message);
            sendMessageClan(message);
        }
        if (shouldPersist) {
            update();
            sendMyClanForAllMember();
        }
    }

    public synchronized String getWeeklyContractStatus() {
        ensureWeeklyContract();
        if (!TaskConfig.isClanWeeklyEnabled()) {
            return "Hợp đồng bang tuần đang tạm khóa.";
        }
        return "Hợp đồng tuần: hạ quái " + weeklyContractProgress + "/" + weeklyContractTarget
                + "\nThưởng quỹ bang: " + Util.numberToMoney(weeklyContractReward) + " Capsule Bang"
                + (weeklyContractRewarded ? "\nĐã hoàn thành." : "");
    }

    /** Dùng điểm cá nhân trước; chỉ bang chủ/phó mới được dùng quỹ chung. */
    public synchronized boolean spendCapsuleClan(Player player, int amount, String reason) {
        ClanMember member = player == null ? null : getClanMember((int) player.id);
        if (player == null || player.clan != this || member == null || amount <= 0) {
            return false;
        }
        String source;
        if (member.memberPoint >= amount) {
            member.memberPoint -= amount;
            source = "điểm cá nhân";
        } else {
            if (!isLeader(player) && !isDeputy(player)) {
                Service.gI().sendThongBao(player, "Bạn không có đủ điểm cá nhân; chỉ bang chủ/phó được dùng quỹ bang.");
                return false;
            }
            if (capsuleClan < amount) {
                Service.gI().sendThongBao(player, "Bang hội không đủ điểm Capsule Bang để mua vật phẩm này!");
                return false;
            }
            capsuleClan -= amount;
            source = "quỹ bang";
        }
        ClanMessage message = new ClanMessage(this);
        message.type = 0;
        message.playerId = member.id;
        message.playerName = member.name;
        message.role = member.role;
        message.text = member.name + " đã chi " + amount + " Capsule Bang từ " + source
                + (reason == null || reason.isBlank() ? "" : " cho " + reason) + ".";
        message.color = 0;
        addClanMessage(message);
        sendMessageClan(message);
        update();
        sendMyClanForAllMember();
        return true;
    }

    /** Chi trực tiếp quỹ chung cho các nâng cấp bang, không đụng điểm cá nhân. */
    public synchronized boolean spendSharedCapsuleClan(Player player, int amount, String reason) {
        if (player == null || player.clan != this || !canManageTreasury(player) || amount <= 0) {
            if (player != null) {
                Service.gI().sendThongBao(player, "Chỉ bang chủ hoặc bang phó được sử dụng quỹ bang.");
            }
            return false;
        }
        if (capsuleClan < amount) {
            Service.gI().sendThongBao(player, "Bang hội không đủ điểm Capsule Bang để thực hiện.");
            return false;
        }
        capsuleClan -= amount;
        ClanMember member = getClanMember((int) player.id);
        ClanMessage message = new ClanMessage(this);
        message.type = 0;
        message.playerId = member == null ? (int) player.id : member.id;
        message.playerName = member == null ? player.name : member.name;
        message.role = member == null ? MEMBER : member.role;
        message.text = message.playerName + " đã chi " + amount + " Capsule Bang từ quỹ bang"
                + (reason == null || reason.isBlank() ? "" : " cho " + reason) + ".";
        message.color = ClanMessage.RED;
        addClanMessage(message);
        sendMessageClan(message);
        update();
        sendMyClanForAllMember();
        return true;
    }

    public boolean canManageTreasury(Player player) {
        return player != null && player.clan == this && (isLeader(player) || isDeputy(player));
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

        Player playeroffline = MrFinn.loadById(playerId);
        if (playeroffline != null) {
            return playeroffline;
        }

        return null;
    }

    // load db danh sách member
    public void addClanMember(ClanMember cm) {
        this.members.add(cm);
    }

    // thêm vào khi player tạo mới clan or mới vào clan
    public void addClanMember(Player player, byte role) {
        ClanMember cm = new ClanMember(player, this, role);
        this.members.add(cm);
        player.clanMember = cm;
    }

    // xóa khi member rời clan or bị kích
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

        String thongTinLeader = "[" + getLeader().id + "," + getLeader().name + "," + getLeader().head + ","
                + getLeader().body + "," + getLeader().leg + "]";

        String top = getWeeklyStateForPersistence();

        PreparedStatement ps = null;
        try (Connection con = LocalManager.getConnection();) {
            ps = con.prepareStatement(
                    "insert into clan (id, name, name_2, slogan, img_id, power_point, max_member, clan_point, level, members, tops, thanhTichBDKB, thongTinLeader) "
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

        String thongTinLeader = "[" + getLeader().id + "," + getLeader().name + "," + getLeader().head + ","
                + getLeader().body + "," + getLeader().leg + "]";

        dataArray.clear();

        PreparedStatement ps = null;
        try (Connection con = LocalManager.getConnection();) {
            ps = con.prepareStatement(
                    "update clan set slogan = ?, img_id = ?, power_point = ?, max_member = ?, clan_point = ?, "
                            + "level = ?, members = ?, name_2 = ?, tops = ?, thanhTichBDKB = ?, thongTinLeader = ? where id = ? limit 1");
            ps.setString(1, this.slogan);
            ps.setInt(2, this.imgId);
            ps.setLong(3, this.powerPoint);
            ps.setByte(4, this.maxMember);
            ps.setInt(5, this.capsuleClan);
            ps.setInt(6, this.level);
            ps.setString(7, member);
            ps.setString(8, this.name2);
            ps.setString(9, getWeeklyStateForPersistence());
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

    /** Lưu riêng tên bang để thao tác đổi tên không ghi đè dữ liệu thành viên. */
    public void updateName() {
        try (Connection con = LocalManager.getConnection();
                PreparedStatement ps = con.prepareStatement("update clan set name = ? where id = ? limit 1")) {
            ps.setString(1, this.name);
            ps.setInt(2, this.id);
            ps.executeUpdate();
        } catch (Exception e) {
            Logger.logException(Clan.class, e, "Có lỗi khi cập nhật tên bang");
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
        try (Connection con = LocalManager.gI().getConnection();
                PreparedStatement ps = con.prepareStatement("UPDATE clan SET thanhTichBDKB = ? WHERE id = ? LIMIT 1")) {
            ps.setString(1, topBanDoKhoBau);
            ps.setInt(2, clanId);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void updatethanhTichBDKBForLeader() {
        try (Connection con = LocalManager.gI().getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "UPDATE player SET thanhTichBang = ? WHERE id = ? LIMIT 1")) {

            String data = "[" + this.name + "," + this.levelDoneBanDoKhoBau + "," + this.thoiGianHoanThanhBDKB + ","
                    + System.currentTimeMillis() + "]";
            ps.setString(1, data);
            ps.setInt(2, this.getLeader().id);
            ps.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void updatethanhTichKhiGasForLeader() {
        String TopKhiGasHuyDiet = "[" + this.name + "," + this.levelDoneKhiGas + "," + thoiGianHoanThanhKhiGas + ","
                + System.currentTimeMillis() + "]";
        try (Connection con = LocalManager.gI().getConnection();
                PreparedStatement ps = con
                        .prepareStatement("UPDATE player SET thanhTichKhiGas = ? WHERE id = ? LIMIT 1")) {
            ps.setString(1, TopKhiGasHuyDiet);
            ps.setInt(2, this.getLeader().id);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void updatethanhTichCDRDForLeader() {
        String TopConDuongRanDoc = "[" + this.name + "," + this.levelDoneCDRD + "," + thoiGianHoanThanhCDRD + ","
                + System.currentTimeMillis() + "]";
        try (Connection con = LocalManager.gI().getConnection();
                PreparedStatement ps = con
                        .prepareStatement("UPDATE player SET thanhTichCDRD = ? WHERE id = ? LIMIT 1")) {
            ps.setString(1, TopConDuongRanDoc);
            ps.setInt(2, this.getLeader().id);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void updateThongTinLeader(int clanId) {
        String thongTinLeader = "[" + getLeader().id + "," + getLeader().name + "," + getLeader().head + ","
                + getLeader().body + "," + getLeader().leg + "]";
        try (Connection con = LocalManager.gI().getConnection();
                PreparedStatement ps = con.prepareStatement("UPDATE clan SET thongTinLeader = ? WHERE id = ? LIMIT 1")) {
            ps.setString(1, thongTinLeader);
            ps.setInt(2, clanId);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
