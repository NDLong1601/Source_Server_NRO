package nro.models.boss.Golden_fireza;


import nro.models.boss.Boss;
import nro.models.boss.BossID;
import nro.models.consts.BossStatus;
import nro.models.boss.BossesData;
import static nro.models.consts.BossType.SKILLSUMMONED;
import nro.models.item.Item;
import java.util.List;
import nro.models.map.ItemMap;
import nro.models.player.Player;
import nro.models.services.ItemService;
import nro.models.map.service.MapService;
import nro.models.services.Service;
import nro.models.services.TaskService;
import nro.models.map.service.ChangeMapService;
import nro.models.utils.Util;

public class DeathBeam3 extends Boss {

    private long st;

    private Player playerAtt;
    public Player playerUseSkill;
    private boolean leaveMap;

    private long lastTimeMove;
    private boolean playerKill;
    private long lastTimeUpdate;

    public DeathBeam3() throws Exception {
        super(SKILLSUMMONED, BossID.DEATH_BEAM_3, BossesData.DEATH_BEAM);
    }
    @Override
    public void reward(Player plKill) {
        int diem = 5;
        plKill.event.addEventPoint(diem);
        Service.gI().sendThongBao(plKill, "+5 Point");
        ItemMap CaiTrangFideVang = new ItemMap(zone, 629,1, this.location.x + Util.nextInt(-50, 50), this.zone.map.yPhysicInTop(this.location.x, this.location.y - 24), plKill.id);
        CaiTrangFideVang.options.add(new Item.ItemOption(30, 1));
        CaiTrangFideVang.options.add(new Item.ItemOption(50, 20));
        CaiTrangFideVang.options.add(new Item.ItemOption(77, 20));
        CaiTrangFideVang.options.add(new Item.ItemOption(103, 20));
        CaiTrangFideVang.options.add(new Item.ItemOption(93, 20));
        Service.gI().dropItemMap(this.zone, CaiTrangFideVang);
    }
    @Override
    public void joinMap() {
        st = System.currentTimeMillis();
        this.zone = this.parentBoss.zone;
        ChangeMapService.gI().changeMap(this, this.zone,
                this.parentBoss.location.x + Util.nextInt(-100, 100), 300);
        Service.gI().sendFlagBag(this);
        playerAtt = this.getPlayerAttack();
        leaveMap = false;
        playerKill = false;
        this.changeStatus(BossStatus.ACTIVE);
    }

    @Override
    public void active() {
        this.attack();
    }

    @Override
    public void afk() {
        if (Util.canDoWithTime(lastTimeUpdate, 3000)) {
            if (Util.isTrue(1, 2)) {
                this.playerAtt = this.getPlayerAttack();
                this.changeStatus(BossStatus.ACTIVE);
            }
            lastTimeUpdate = System.currentTimeMillis();
        }
    }

    @Override
    public void autoLeaveMap() {
        if (!leaveMap) {
            if (Util.canDoWithTime(st, 14800)) {
                leaveMap = true;
            }
        } else {
            if (Util.canDoWithTime(lastTimeMove, 500)) {
                this.location.y -= 30;
                this.moveTo(this.location.x, this.location.y);
                if (this.location.y < 0) {
                    this.leaveMap();
                }

            }
        }
    }

    @Override
    public void moveTo(int x, int y) {
        this.location.x = x;
        this.location.y = y;
        MapService.gI().sendPlayerMove(this);
    }

    @Override
    public void moveToPlayer(Player pl) {
        if (pl.location != null) {
            int move = Math.abs(this.location.x - playerAtt.location.x);
            int dir = this.location.x - playerAtt.location.x > 0 ? -1 : 1;
            int x = this.location.x + dir * 30;
            if (move < 30) {
                x = pl.location.x;
            }
            moveTo(x, pl.location.y);
        }
    }

    @Override
    public void attack() {
        if (leaveMap) {
            return;
        }

        if (playerAtt == null || playerAtt.location == null || playerAtt.isDie() || !playerAtt.zone.equals(this.zone)) {
            this.changeStatus(BossStatus.AFK);
            return;
        }
        if (Util.canDoWithTime(lastTimeMove, 500)) {
            this.moveToPlayer(playerAtt);
        }
        if (Math.abs(this.location.x - playerAtt.location.x) < 5 && Util.canDoWithTime(st, 500)) {
            Service.gI().setPos(this, playerAtt.location.x, playerAtt.location.y);
            if (!playerKill) {
                setDie();
                playerKill = true;
                leaveMap = true;
            }
        }
    }

    @Override
    public void leaveMap() {
        ChangeMapService.gI().exitMap(this);
        this.lastZone = null;
        this.lastTimeRest = System.currentTimeMillis();
        this.changeStatus(BossStatus.REST);
    }

    @Override
    public synchronized int injured(Player plAtt, long damage, boolean piercing, boolean isMobAttack) {
        return 0;
    }

    @Override
    public void setDie() {
//        playerAtt.setDie();
        playerAtt.injured(this.playerUseSkill, 2_100_000_000, true, false);
    }
}
