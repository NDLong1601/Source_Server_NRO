package nro.models.matches.dai_hoi_vo_thuat;
import nro.models.consts.ConstPlayer;
import nro.models.matches.giai_dau.DeathOrAliveArenaManager;
import nro.models.boss.Boss;
import nro.models.consts.BossStatus;
import nro.models.boss.vo_dai_hat_mit.BongBang;
import nro.models.boss.vo_dai_hat_mit.Dracula;
import nro.models.boss.vo_dai_hat_mit.NguoiVoHinh;
import nro.models.boss.vo_dai_hat_mit.ThoDauBac;
import nro.models.boss.vo_dai_hat_mit.VuaQuySaTang;
import java.util.ArrayList;
import java.util.List;
import nro.models.player.Player;
import nro.models.services.PlayerService;
import nro.models.services.Service;
import lombok.Getter;
import lombok.Setter;
import nro.models.map.Zone;
import nro.models.matches.DHVT;
import nro.models.npc.Npc;
import nro.models.utils.Util;

public class DeathOrAliveArena {

    @Setter
    @Getter
    private Player player;

    private Boss boss;

    @Setter
    private Npc npc;

    @Setter
    private long timeTotal;

    private int time;
    @Setter
    private int round;
    private int timeWait;

    @Setter
    @Getter
    private int cuocBaHatMit;

    @Setter
    @Getter
    private int cuocPlayer;

    @Setter
    @Getter
    private Zone zone;

    private final List<Player> binhChon = new ArrayList<>();

    public boolean endChallenge;

    public void update() {

        if (player.zone == null) {
            this.endChallenge();
            return;
        }

        if (timeWait > 0) {
            switch (timeWait) {
                case 5 -> {
                    if (round > 1) {
                        npc.npcChat(player, "Khá lắm, chuẩn bị đánh tiếp nào");
                    }
                }
                case 3 ->
                    Service.gI().chat(boss, "Sẵn sàng chưa?");
                case 1 -> {
                    ready();
                    npc.npcChat(player, "Con tắc kè màu xanh màu đỏ...Em bắt về em nấu cà ri...Ồ là la ýe...");
                }
            }
            timeWait--;
            return;
        }

        if (time > 0) {
            time--;
            if (player.isDie()) {
                die();
                return;
            }
            if (player.location != null && !player.isDie() && player != null && player.zone != null) {
                if (boss.isDie()) {
                    round++;
                    timeTotal += (180 - time);
                    traThuongHatMit(true);
                    boss.leaveMap();
                    toTheNextRound();
                }
                if (player.location.y > 336 && !(player.location.x > 322 && player.location.x < 614)) {
                    leave();
                    return;
                }
                if (!player.isPKDHVT) {
                    leave();
                }
            } else {
                if (boss != null) {
                    boss.leaveMap();
                }
                DeathOrAliveArenaManager.gI().remove(this);
            }

        } else {
            timeOut();
        }
    }

    public void ready() {
        setTime(181);
        DeathOrAliveArenaService.gI().sendTypePK(player, boss);
        PlayerService.gI().changeAndSendTypePK(this.player, ConstPlayer.PK_PVP);
        boss.changeStatus(BossStatus.ACTIVE);
        new DHVT(player, boss);
    }

    public void toTheNextRound() {
        try {
            PlayerService.gI().changeAndSendTypePK(player, ConstPlayer.NON_PK);
            Boss bss;
            switch (round) {
                case 0 ->
                    bss = new Dracula(player);
                case 1 ->
                    bss = new NguoiVoHinh(player);
                case 2 ->
                    bss = new BongBang(player);
                case 3 ->
                    bss = new VuaQuySaTang(player);
                case 4 ->
                    bss = new ThoDauBac(player);
                case 5 -> {
                    champion();
                    return;
                }
                default -> {
                    return;
                }
            }
            Service.gI().setPos(player, 401, 336);
            setTimeWait(5);
            setBoss(bss);
        } catch (Exception e) {
        }
    }

    public void setBoss(Boss boss) {
        this.boss = boss;
    }

    public void setTime(int time) {
        this.time = time;
    }

    public void setTimeWait(int timeWait) {
        this.timeWait = timeWait;
    }

    private void die() {
        traThuongHatMit(false);
        Service.gI().sendThongBao(player, "Bạn đã thua, hẹn gặp lại ở giải sau");
        npc.npcChat(player.zone, "Người tiếp theo chuẩn bị.");
        if (player.zone != null) {
            endChallenge();
        }
    }

    private void timeOut() {
        if (round < 5) {
            traThuongHatMit(false);
            Service.gI().sendThongBao(player, "Bạn đã thua, hẹn gặp lại ở giải sau");
            npc.npcChat(player.zone, "Người tiếp theo chuẩn bị.");
            endChallenge();
        }
    }

    private void champion() {
        if (player.timePKVDST == 0 || player.timePKVDST > timeTotal) {
            player.timePKVDST = timeTotal;
        }
        endChallenge();
        npc.npcChat(player, "Đây là phần thưởng cho con.");
        reward();
    }

    public void leave() {
        if (round < 5) {
            traThuongHatMit(false);
            Service.gI().sendThongBao(player, "Bạn đã thua, hẹn gặp lại ở giải sau");
            npc.npcChat(player.zone, "Người tiếp theo chuẩn bị.");
            setTime(0);
            endChallenge();
        }
    }

    private void reward() {
        player.haveRewardVDST = true;
    }

    private void traThuongHatMit(boolean playerWin) {
        try {
            long tongCuoc = (cuocBaHatMit + cuocPlayer) * 900_000;
            if (cuocBaHatMit >= 0 && !playerWin || cuocPlayer > 0 && playerWin) {
                if (playerWin) {
                    tongCuoc /= cuocPlayer;
                } else {
                    tongCuoc /= cuocBaHatMit;
                }
                for (Player pl : binhChon) {
                    try {
                        if (playerWin) {
                            int cuoc = pl.binhChonPlayer;
                            if (cuoc > 0 && pl.zoneBinhChon.equals(zone)) {
                                long vangNhan = cuoc * tongCuoc;
                                pl.inventory.gold += vangNhan;
                                pl.binhChonPlayer = 0;
                                pl.binhChonHatMit = 0;
                                Service.gI().sendMoney(pl);
                                Service.gI().sendThongBao(pl, "Chúc mừng bạn đã thắng " + cuoc + " bình chọn đúng và được thưởng " + Util.numberToMoney(vangNhan) + " vàng");
                            }
                        } else {
                            int cuoc = pl.binhChonHatMit;
                            if (cuoc > 0 && pl.zoneBinhChon.equals(zone)) {
                                long vangNhan = cuoc * tongCuoc;
                                pl.inventory.gold += vangNhan;
                                pl.binhChonPlayer = 0;
                                pl.binhChonHatMit = 0;
                                Service.gI().sendMoney(pl);
                                Service.gI().sendThongBao(pl, "Chúc mừng bạn đã thắng " + cuoc + " bình chọn đúng và được thưởng " + Util.numberToMoney(vangNhan) + " vàng");
                            }
                        }
                    } catch (Exception e) {
                    }
                }
            }
            cuocBaHatMit = 0;
            cuocPlayer = 0;
            binhChon.clear();
        } catch (Exception e) {
        }
    }

    public void endChallenge() {
        if (!endChallenge) {
            endChallenge = true;
            Service.gI().sendPlayerVS(player, null, (byte) 0);
            if (player.zone != null) {
                PlayerService.gI().hoiSinh(player);
            }
            PlayerService.gI().changeAndSendTypePK(player, ConstPlayer.NON_PK);
            if (player != null && player.zone != null && player.zone.map.mapId == 112) {
                Service.gI().setPos(player, Util.nextInt(100, 200), 408);
            }
            player.isPKDHVT = false;
            if (boss != null) {
                boss.leaveMap();
            }
            zone = null;
            DeathOrAliveArenaManager.gI().remove(this);
        }
    }

    public void addBinhChon(Player pl) {
        if (!binhChon.contains(pl)) {
            binhChon.add(pl);
        }
    }
}
