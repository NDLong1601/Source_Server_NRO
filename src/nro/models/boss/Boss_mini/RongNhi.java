package nro.models.boss.Boss_mini;

import nro.models.boss.Boss;
import nro.models.boss.BossData;
import nro.models.boss.BossID;
import nro.models.boss.BossesData;
import nro.models.consts.BossStatus;
import nro.models.consts.ConstPlayer;
import java.util.Random;
import nro.models.map.ItemMap;
import nro.models.map.service.ChangeMapService;
import nro.models.map.service.MapService;
import nro.models.map.Zone;
import nro.models.player.Player;
import nro.models.services.PlayerService;
import nro.models.services.Service;
import nro.models.services.SkillService;
import nro.models.services.TaskService;
import nro.models.skill.Skill;
import nro.models.utils.Util;

/**
 *
 * @author By Mr Blue
 */
public class RongNhi extends Boss {

    private long st;

    public RongNhi() throws Exception {
        super(BossID.RONG_NHI, true, true, BossesData.RONG_NHI);
    }

    @Override
    public void joinMap() {
        super.joinMap();
        st = System.currentTimeMillis();
    }

    @Override
    public void reward(Player plKill) {
        if (Util.isTrue(20, 100)) {
            int[] items = Util.isTrue(20, 100) ? new int[]{1821} : new int[]{18, 19, 20};
            int randomItem = items[new Random().nextInt(items.length)];
            Service.gI().dropItemMap(this.zone, new ItemMap(this.zone, randomItem, 1,
                    this.location.x, this.zone.map.yPhysicInTop(this.location.x, this.location.y - 24), plKill.id));
            int diem = 5;
            plKill.event.addEventPoint(diem);
            Service.gI().sendThongBao(plKill, "+5 Point");
        }
    }

    @Override
    public void autoLeaveMap() {
        if (Util.canDoWithTime(st, 900000)) {
            this.changeStatus(BossStatus.LEAVE_MAP);
        }
        if (this.zone != null && this.zone.getNumOfPlayers() > 0) {
            st = System.currentTimeMillis();
        }
    }
}
