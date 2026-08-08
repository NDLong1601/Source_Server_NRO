package nro.models.boss_con_duong_ran_doc;


import nro.models.boss.Boss;
import nro.models.boss.BossData;
import nro.models.boss.BossID;
import nro.models.consts.BossStatus;
import nro.models.consts.ConstPlayer;
import nro.models.boss.Boss_Manager.SnakeWayManager;
import static nro.models.consts.BossType.PHOBANCDRD;
import nro.models.clan.Clan;
import nro.models.map.ItemMap;
import nro.models.map.Zone;
import nro.models.player.Player;
import nro.models.skill.Skill;
import nro.models.services.Service;
import nro.models.services.SkillService;
import nro.models.map.service.ChangeMapService;
import nro.models.utils.SkillUtil;
import nro.models.utils.Util;

public class NADIC extends Boss {

    private Clan clan;

    private long lastTimeTTNL;

    public NADIC(Zone zone, Clan clan, int dame, int hp) throws Exception {
        super(PHOBANCDRD, BossID.NADIC, new BossData(
                "Nađíc",
                ConstPlayer.XAYDA,
                new short[]{648, 649, 650, -1, -1, -1},
                ((10000 + dame)),
                new int[]{((500000 + hp))},
                new int[]{144},
                new int[][]{
                    {Skill.GALICK, 7, 1000},
                    {Skill.TAI_TAO_NANG_LUONG, 5, 10000},},//skill
                new String[]{},
                new String[]{},
                new String[]{"|-1|Sếp hãy giết nó, trả thù cho em!"},
                60
        ));
        this.zone = zone;
        this.clan = clan;
    }

    @Override
    public void reward(Player plKill) {
        if (Util.isTrue(100, 100)) {
            ItemMap it = new ItemMap(this.zone, 19, 1, this.location.x, this.zone.map.yPhysicInTop(this.location.x,
                    this.location.y - 24), plKill.id);
            Service.gI().dropItemMap(this.zone, it);
        }
    }

    @Override
    public void afk() {
        if (this.clan == null || this.clan.ConDuongRanDoc == null) {
            this.leaveMap();
            return;
        }
        if (this.clan.ConDuongRanDoc.getNumBossAlive() < 3) {
            this.changeStatus(BossStatus.ACTIVE);
        }
    }

    @Override
    public void joinMap() {
        ChangeMapService.gI().changeMap(this, this.zone, 470, 312);
        this.changeStatus(BossStatus.AFK);
    }

    @Override
    public void die(Player plKill) {
        for (Boss boss : clan.ConDuongRanDoc.bosses) {
            if (boss.id == BossID.CADICH) {
                boss.moveTo(boss.location.x + this.location.x > boss.location.x ? - 100 : 100, this.location.y);
                break;
            }
        }
        if (plKill != null) {
            reward(plKill);
        }
        this.changeStatus(BossStatus.DIE);
    }

    @Override
    public void leaveMap() {
        ChangeMapService.gI().exitMap(this);
        this.lastZone = null;
        this.lastTimeRest = System.currentTimeMillis();
        this.changeStatus(BossStatus.REST);
        SnakeWayManager.gI().removeBoss(this);
        this.dispose();
    }

    @Override
    public void attack() {
        if (!this.effectSkill.isCharging && Util.canDoWithTime(this.lastTimeAttack, 100) && this.typePk == ConstPlayer.PK_ALL) {
            this.lastTimeAttack = System.currentTimeMillis();
            try {
                Player pl = getPlayerAttack();
                if (pl == null || pl.isDie()) {
                    return;
                }
                this.playerSkill.skillSelect = this.playerSkill.skills.get(0);
                if (Util.isTrue(1, 20) && Util.canDoWithTime(lastTimeTTNL, 10000)) {
                    this.playerSkill.skillSelect = this.playerSkill.skills.get(1);
                    this.chat("Ốp la...Xay da da!");
                    this.lastTimeTTNL = System.currentTimeMillis();
                }
                if (Util.getDistance(this, pl) <= this.getRangeCanAttackWithSkillSelect()) {
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
                } else {
                    if (Util.isTrue(1, 2)) {
                        this.moveToPlayer(pl);
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
}
