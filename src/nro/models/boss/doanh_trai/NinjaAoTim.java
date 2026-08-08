package nro.models.boss.doanh_trai;

import nro.models.boss.Boss;
import nro.models.boss.BossData;
import nro.models.boss.BossID;
import nro.models.consts.BossStatus;
import nro.models.consts.ConstPlayer;
import nro.models.boss.Boss_Manager.RedRibbonHQManager;
import static nro.models.consts.BossType.PHOBANDT;
import nro.models.clan.Clan;
import nro.models.map.ItemMap;
import nro.models.map.Zone;
import nro.models.player.Player;
import nro.models.skill.Skill;
import nro.models.services.EffectSkillService;
import nro.models.services.Service;
import nro.models.map.service.ChangeMapService;
import nro.models.utils.Util;

public class NinjaAoTim extends Boss {

    private boolean calledNinja;

    public NinjaAoTim(Zone zone, Clan clan, int dame, int hp) throws Exception {
        super(PHOBANDT, BossID.NINJA_AO_TIM, new BossData(
                "Ninja Áo Tím", //name
                ConstPlayer.TRAI_DAT, //gender
                new short[]{123, 124, 125, -1, -1, -1}, //outfit {head, body, leg, bag, aura, eff}
                ((dame)), //dame
                new int[]{((hp))}, //hp
                new int[]{54}, //map join
                new int[][]{
                    {Skill.DEMON, 3, 1}, {Skill.DEMON, 6, 2}, {Skill.DRAGON, 7, 3}, {Skill.DRAGON, 1, 4}, {Skill.GALICK, 5, 5},
                    {Skill.KAMEJOKO, 7, 6}, {Skill.KAMEJOKO, 6, 7}, {Skill.KAMEJOKO, 5, 8}, {Skill.KAMEJOKO, 4, 9}, {Skill.KAMEJOKO, 3, 10}, {Skill.KAMEJOKO, 2, 11}, {Skill.KAMEJOKO, 1, 12},
                    {Skill.ANTOMIC, 1, 13}, {Skill.ANTOMIC, 2, 14}, {Skill.ANTOMIC, 3, 15}, {Skill.ANTOMIC, 4, 16}, {Skill.ANTOMIC, 5, 17}, {Skill.ANTOMIC, 6, 19}, {Skill.ANTOMIC, 7, 20},
                    {Skill.MASENKO, 1, 21}, {Skill.MASENKO, 5, 22}, {Skill.MASENKO, 6, 23},
                    {Skill.KAMEJOKO, 7, 1000},},
                new String[]{}, //text chat 1
                new String[]{"|-1|Ta sẽ xé xác ngươi ra thành trăm mảnh",
                    "|-1|Ha ha ha"}, //text chat 2
                new String[]{}, //text chat 3
                60
        ));

        this.zone = zone;
        this.clan = clan;
    }

    @Override
    public void reward(Player plKill) {
        int diem = 5;
        plKill.event.addEventPoint(diem);
        Service.gI().sendThongBao(plKill, "+5 Point");

        // Xác suất rơi item 1560 (50%)
        if (Util.isTrue(50, 100)) {
            ItemMap it = new ItemMap(
                    this.zone,
                    17,
                    1,
                    this.location.x,
                    this.zone.map.yPhysicInTop(this.location.x, this.location.y - 24),
                    plKill.id
            );
            Service.gI().dropItemMap(this.zone, it);
        }

        // Xác suất rơi item 611 (30%)
        if (Util.isTrue(30, 100)) {
            ItemMap it = new ItemMap(
                    this.zone,
                    611,
                    Util.nextInt(1, 2),
                    this.location.x,
                    this.zone.map.yPhysicInTop(this.location.x, this.location.y - 24),
                    plKill.id
            );
            Service.gI().dropItemMap(this.zone, it);
        }

        // 100% rơi item 1824
        ItemMap it = new ItemMap(
                this.zone,
                1824,
                1,
                this.location.x,
                this.zone.map.yPhysicInTop(this.location.x, this.location.y - 24),
                plKill.id
        );
        Service.gI().dropItemMap(this.zone, it);
    }

    @Override
    public void joinMap() {
        ChangeMapService.gI().changeMap(this, this.zone, 190, 312);
        this.changeStatus(BossStatus.CHAT_S);
    }

    @Override
    public void doneChatS() {
        Service.gI().setPos(this, 190, 312);
    }

    @Override
    public void active() {
        super.active();
    }

    @Override
    public synchronized int injured(Player plAtt, long damage, boolean piercing, boolean isMobAttack) {
        if (!this.isDie()) {
            if (!piercing && Util.isTrue(30, 100)) {
                this.chat("Xí hụt");
                return 0;
            }
            damage = this.nPoint.subDameInjureWithDeff(damage / 2);
            if (!piercing && effectSkill.isShielding) {
                if (damage > nPoint.hpMax) {
                    EffectSkillService.gI().breakShield(this);
                }
                damage = damage / 2;
            }
            if (this.nPoint.hp <= this.nPoint.hpMax / 2 && !this.calledNinja) {
                if (Util.isTrue(4, 5)) {
                    try {
                        clan.doanhTrai.bosses.add(new NinjaClone(this.zone, this, this.nPoint.dame / 10, this.nPoint.hpMax / 10, BossID.NINJA_AO_TIM1));
                        clan.doanhTrai.bosses.add(new NinjaClone(this.zone, this, this.nPoint.dame / 10, this.nPoint.hpMax / 10, BossID.NINJA_AO_TIM2));
                        clan.doanhTrai.bosses.add(new NinjaClone(this.zone, this, this.nPoint.dame / 10, this.nPoint.hpMax / 10, BossID.NINJA_AO_TIM3));
                        clan.doanhTrai.bosses.add(new NinjaClone(this.zone, this, this.nPoint.dame / 10, this.nPoint.hpMax / 10, BossID.NINJA_AO_TIM4));
                        if (Util.isTrue(1, 2)) {
                            clan.doanhTrai.bosses.add(new NinjaClone(this.zone, this, this.nPoint.dame / 10, this.nPoint.hpMax / 10, BossID.NINJA_AO_TIM5));
                            clan.doanhTrai.bosses.add(new NinjaClone(this.zone, this, this.nPoint.dame / 10, this.nPoint.hpMax / 10, BossID.NINJA_AO_TIM6));
                        }
                    } catch (Exception ex) {
                    }
                }
                this.calledNinja = true;
                return 0;
            }
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

    @Override
    public void die(Player plKill) {
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
        RedRibbonHQManager.gI().removeBoss(this);
        this.dispose();
    }

}
