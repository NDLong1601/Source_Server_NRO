package nro.models.boss.Boss_mini;

import nro.models.boss.Boss;
import nro.models.boss.BossData;
import nro.models.boss.BossID;
import nro.models.consts.BossStatus;
import static nro.models.consts.BossType.ANTROM;
import nro.models.consts.ConstPlayer;
import nro.models.map.ItemMap;
import nro.models.map.Zone;
import nro.models.player.Player;
import nro.models.services.EffectSkillService;
import nro.models.services.Service;
import nro.models.map.service.ChangeMapService;
import nro.models.map.service.MapService;
import nro.models.services.PlayerService;
import nro.models.skill.Skill;
import nro.models.utils.Util;
import nro.models.boss.Boss;
import nro.models.boss.BossData;
import nro.models.boss.BossID;
import nro.models.consts.BossStatus;
import static nro.models.consts.BossType.ANTROM;
import nro.models.consts.ConstPlayer;
import nro.models.consts.ConstTaskBadges;
import java.util.List;
import nro.models.map.ItemMap;
import nro.models.map.Zone;
import nro.models.player.EffectSkin;
import static nro.models.player.EffectSkin.textOdo;
import nro.models.player.Player;
import nro.models.services.EffectSkillService;
import nro.models.services.Service;
import nro.models.services.SkillService;
import nro.models.map.service.ChangeMapService;
import nro.models.map.service.MapService;
import nro.models.services.PlayerService;
import nro.models.skill.Skill;
import nro.models.task.BadgesTaskService;
import nro.models.utils.SkillUtil;
import nro.models.utils.Util;

public class Odo extends Boss {

    private long lastTimeOdo;
    private long lastTimeHpRegen;

    public Odo() throws Exception {
        super(BossID.O_DO1, new BossData(
                "Ở Dơ " + Util.nextInt(1, 49),
                ConstPlayer.TRAI_DAT,
                new short[]{400, 401, 402, -1, -1, -1},
                1000,
                new int[]{500000},
                new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 79, 80, 81, 82, 83, 84, 92, 93, 94, 96, 97, 98, 99, 100, 102, 103, 104, 105, 106, 107, 108, 109, 110}, //map join
                new int[][]{
                    {Skill.TAI_TAO_NANG_LUONG, 1}},
                new String[]{}, //text chat 1
                new String[]{}, //text chat 2
                new String[]{},
                600000));
    }

    @Override
    public int injured(Player plAtt, long damage, boolean piercing, boolean isMobAttack) {
        if (!this.isDie()) {
            damage = 50000;
            this.nPoint.subHP(damage);
            if (isDie()) {
                this.setDie(plAtt);
                die(plAtt);
            }
            return (int) damage;
        } else {
            return 0;
        }
    }

    private void updateOdo() {
        try {
            int param = 10;
            int randomTime = Util.nextInt(3000, 5000);
            if (Util.canDoWithTime(lastTimeOdo, randomTime)) {
                List<Player> playersMap = this.zone.getNotBosses();
                for (int i = playersMap.size() - 1; i >= 0; i--) {
                    Player pl = playersMap.get(i);
                    if (pl != null && pl.nPoint != null && !this.equals(pl) && !pl.isBoss && !pl.isDie()
                            && Util.getDistance(this, pl) <= 200) {
                        int subHp = (int) ((long) pl.nPoint.hpMax * param / 100);
                        if (subHp >= pl.nPoint.hp) {
                            subHp = pl.nPoint.hp - 1;
                        }
                        this.chat("Bùm Bùm");
                        Service.gI().chat(pl, textOdo[Util.nextInt(0, textOdo.length - 1)]);
                        PlayerService.gI().sendInfoHpMpMoney(pl);
                        pl.injured(null, subHp, true, false);
                    }
                }
                this.lastTimeOdo = System.currentTimeMillis(); // Cập nhật thời gian của Odo
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void regenHp() {
        try {
            if (Util.canDoWithTime(lastTimeHpRegen, 30000)) {
                int regenPercentage = Util.nextInt(10, 20);
                int regenAmount = (this.nPoint.hpMax * regenPercentage / 100);
                PlayerService.gI().hoiPhuc(this, regenAmount, 0);
                this.chat("Mùi Của Các Ngươi Thơm Quá!! HAHA");
                this.lastTimeHpRegen = System.currentTimeMillis();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void attack() {
        if (Util.canDoWithTime(this.lastTimeAttack, 100) && this.typePk == ConstPlayer.PK_ALL) {
            this.lastTimeAttack = System.currentTimeMillis();
            try {
                Player pl = this.getPlayerAttack();
                if (pl == null || pl.isDie()) {
                    return;
                }
                this.playerSkill.skillSelect = this.playerSkill.skills.get(Util.nextInt(0, this.playerSkill.skills.size() - 1));

                if (Util.getDistance(this, pl) <= 40) {
                    if (Util.isTrue(5, 20)) {
                        if (SkillUtil.isUseSkillChuong(this)) {
                            this.moveTo(pl.location.x + (Util.getOne(-1, 1) * Util.nextInt(20, 200)),
                                    Util.nextInt(10) % 2 == 0 ? pl.location.y : pl.location.y - Util.nextInt(0, 70));
                        } else {
                            this.moveTo(pl.location.x + (Util.getOne(-1, 1) * Util.nextInt(10, 40)),
                                    Util.nextInt(10) % 2 == 0 ? pl.location.y : pl.location.y - Util.nextInt(0, 50));
                        }
                    }
                    SkillService.gI().useSkill(this, pl, null, -1, null);
                    checkPlayerDie(pl);
                    this.updateOdo();

                } else {
                    if (Util.isTrue(1, 2)) {
                        this.moveToPlayer(pl);
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        this.regenHp();
    }

    @Override
    public void moveTo(int x, int y) {
        byte dir = (byte) (this.location.x - x < 0 ? 1 : -1);
        byte move = (byte) Util.nextInt(30, 40);
        PlayerService.gI().playerMove(this, this.location.x + (dir == 1 ? move : -move), y);
    }

    @Override
    public void reward(Player plKill) {
        try {
            int count1591 = Util.nextInt(1, 1);
            int count1594 = Util.nextInt(1, 1);

            for (int i = 0; i < count1591; i++) {
                int x = this.location.x + Util.nextInt(-20, 20);
                int y = this.zone.map.yPhysicInTop(x, this.location.y - 24);
                ItemMap item = new ItemMap(this.zone, 1591, 1, x, y, plKill.id);
                Service.gI().dropItemMap(this.zone, item);
            }

            for (int i = 0; i < count1594; i++) {
                int x = this.location.x + Util.nextInt(-20, 20);
                int y = this.zone.map.yPhysicInTop(x, this.location.y - 24);
                ItemMap item = new ItemMap(this.zone, 1594, 1, x, y, plKill.id);
                Service.gI().dropItemMap(this.zone, item);
            }

            BadgesTaskService.updateCountBagesTask(plKill, ConstTaskBadges.O_DO, 1);
            int diem = 5;
            plKill.event.addEventPoint(diem);
            Service.gI().sendThongBao(plKill, "+5 Point");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private long st;

    @Override
    public void active() {
        if (this.typePk == ConstPlayer.NON_PK) {
            this.changeToTypePK();
        }
        this.attack();
        if (Util.canDoWithTime(st, 900000)) {
            this.changeStatus(BossStatus.LEAVE_MAP);
        }
    }

    @Override
    public void joinMap() {
        this.joinMap2();
        st = System.currentTimeMillis();
    }

    public void joinMap2() {
        if (this.zone == null) {
            if (this.parentBoss != null) {
                this.zone = parentBoss.zone;
            } else if (this.lastZone == null) {
                this.zone = getMapJoin();
            } else {
                this.zone = this.lastZone;
            }
        }
        if (this.zone != null) {
            try {
                int zoneid = 0;
                this.zone = this.zone.map.zones.get(zoneid);
                ChangeMapService.gI().changeMap(this, this.zone, -1, -1);

                this.changeStatus(BossStatus.CHAT_S);
            } catch (Exception e) {
                this.changeStatus(BossStatus.REST);
            }
        } else {
            this.changeStatus(BossStatus.RESPAWN);
        }
    }

    @Override
    public void leaveMap() {
        ChangeMapService.gI().exitMap(this);
        this.lastZone = null;
        this.lastTimeRest = System.currentTimeMillis();
        this.changeStatus(BossStatus.REST);
    }
}
