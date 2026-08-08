package nro.models.matches;
import nro.models.consts.ConstAchievement;
import nro.models.matches.PVP;
import nro.models.matches.TYPE_LOSE_PVP;
import nro.models.matches.TYPE_PVP;
import nro.models.services.AchievementService;
import nro.models.player.Player;
import nro.models.server.Client;
import nro.models.services.Service;
import nro.models.utils.Util;

public class ThachDau extends PVP {

    private int goldThachDau;
    private long goldReward;

    public ThachDau(Player p1, Player p2, int goldThachDau) {
        super(TYPE_PVP.THACH_DAU, p1, p2);
        this.goldThachDau = goldThachDau;
        this.goldReward = goldThachDau / 100 * 80;
    }

    @Override
    public void start() {
        this.p1.inventory.gold -= this.goldThachDau;
        this.p2.inventory.gold -= this.goldThachDau;
        Service.gI().sendMoney(this.p1);
        Service.gI().sendMoney(this.p2);
        super.start();
    }

    @Override
    public void finish() {

    }

    @Override
    public void dispose() {
        super.dispose();
    }

    @Override
    public void update() {
    }

    @Override
    public void reward(Player plWin) {
        plWin.inventory.gold += this.goldReward;
        Service.gI().sendMoney(plWin);
    }

    @Override
    public void sendResult(Player plLose, TYPE_LOSE_PVP typeLose) {
        if (typeLose == TYPE_LOSE_PVP.RUNS_AWAY) {
            Player plL = Client.gI().getPlayer(plLose.id);
            if (plL == null) {
                Service.gI().sendThongBao(p1.equals(plLose) ? p2 : p1, "Đối thủ rời game, bạn thắng được " + Util.numberToMoney(this.goldReward) + " vàng");
            } else {
                Service.gI().sendThongBao(p1.equals(plLose) ? p2 : p1, "Đối thủ sợ quá bỏ chạy, bạn thắng được " + Util.numberToMoney(this.goldReward) + " vàng");
            }
            Service.gI().sendThongBao(p1.equals(plLose) ? p1 : p2, "Bạn bị xử thua vì đã bỏ chạy");
            (p1.equals(plLose) ? p1 : p2).inventory.gold -= this.goldThachDau;
        } else if (typeLose == TYPE_LOSE_PVP.DEAD) {
            Service.gI().sendThongBao(p1.equals(plLose) ? p2 : p1, "Đối thủ đã kiệt sức, bạn thắng được " + Util.numberToMoney(this.goldReward) + " vàng");
            Service.gI().sendThongBao(p1.equals(plLose) ? p1 : p2, "Bạn đã thua vì đã kiệt sức");
            (p1.equals(plLose) ? p1 : p2).inventory.gold -= this.goldThachDau;
        }
        Service.gI().sendMoney(p1.equals(plLose) ? p1 : p2);
        if (!p1.equals(plLose)) {
            AchievementService.gI().checkDoneTask(p1, ConstAchievement.TRAM_TRAN_TRAM_THANG);
        }
    }

}
