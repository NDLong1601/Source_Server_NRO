package nro.models.boss.trai_dat;


import nro.models.boss.Boss;
import nro.models.boss.BossID;
import nro.models.consts.BossStatus;
import nro.models.boss.BossesData;
import nro.models.item.Item;
import java.util.List;
import nro.models.map.ItemMap;
import nro.models.player.Player;
import nro.models.services.ItemService;
import nro.models.services.Service;
import nro.models.utils.Util;

public class BOJACK extends Boss {

    private long st;

    public BOJACK() throws Exception {
        super(BossID.BOJACK, false, true, BossesData.BOJACK, BossesData.SUPER_BOJACK);
    }

    @Override
    public void reward(Player plKill) {
       Service.gI().dropItemMap(this.zone, new ItemMap(zone, 77, Util.nextInt(5, 20), this.location.x + Util.nextInt(-50, 50), this.zone.map.yPhysicInTop(this.location.x, this.location.y - 24), plKill.id));
        for (int i = 0; i < Util.nextInt(2); i++) {
            Service.gI().dropItemMap(this.zone, new ItemMap(zone, 77, Util.nextInt(5, 20), this.location.x + i * Util.nextInt(-50, 50), this.zone.map.yPhysicInTop(this.location.x, this.location.y - 24), plKill.id));
        }
        for (int i = 0; i < Util.nextInt(3, 4); i++) {
            Service.gI().dropItemMap(this.zone, new ItemMap(zone, 77, Util.nextInt(5, 20), this.location.x + i * 10, this.zone.map.yPhysicInTop(this.location.x,
                    this.location.y - 24), plKill.id));
        }
        for (int i = 1; i < Util.nextInt(3, 3) + 1; i++) {
            Service.gI().dropItemMap(this.zone, new ItemMap(zone, 77, Util.nextInt(5, 20), this.location.x - i * 10, this.zone.map.yPhysicInTop(this.location.x, this.location.y - 24), plKill.id));
        }
        short itTemp = 427;
        short nr6s = 19;
        short nr7s = 20;
        ItemMap it = new ItemMap(zone, itTemp, 1, this.location.x + Util.nextInt(-50, 50), this.zone.map.yPhysicInTop(this.location.x, this.location.y - 24), plKill.id);
        ItemMap it1 = new ItemMap(zone, nr6s, 1, this.location.x + Util.nextInt(-50, 50), this.zone.map.yPhysicInTop(this.location.x, this.location.y - 24), plKill.id);
        ItemMap it2 = new ItemMap(zone, nr7s, 1, this.location.x + Util.nextInt(-50, 50), this.zone.map.yPhysicInTop(this.location.x, this.location.y - 24), plKill.id);

        List<Item.ItemOption> ops = ItemService.gI().getListOptionItemShop(itTemp);
        if (!ops.isEmpty()) {
            it.options = ops;
        }
        Service.gI().dropItemMap(this.zone, it);
        Service.gI().dropItemMap(this.zone, it1);
        Service.gI().dropItemMap(this.zone, it2);
        int diem = 5;
        plKill.event.addEventPoint(diem);
        Service.gI().sendThongBao(plKill, "+5 Point");
    }

    @Override
    public void joinMap() {
        super.joinMap();
        st = System.currentTimeMillis();
    }

    @Override
    public void autoLeaveMap() {
        if (Util.canDoWithTime(st, 900000)) {
            this.leaveMapNew();
        }
        if (this.zone != null && this.zone.getNumOfPlayers() > 0) {
            st = System.currentTimeMillis();
        }
    }

    @Override
    public void doneChatS() {
        if (this.currentLevel == 1) {
            return;
        }
        this.changeStatus(BossStatus.AFK);
    }
}
