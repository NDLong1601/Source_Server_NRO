package nro.models.boss.Golden_fireza;

import nro.models.services.SkillService;
import nro.models.services.Service;
import nro.models.services.EffectSkillService;
import nro.models.utils.SkillUtil;
import nro.models.utils.Util;
import nro.models.utils.TimeUtil;
import nro.models.boss.Boss;
import nro.models.boss.BossID;
import nro.models.boss.BossesData;
import nro.models.consts.BossStatus;
import nro.models.consts.ConstPlayer;
import nro.models.item.Item;
import nro.models.item.Item.ItemOption;
import nro.models.map.ItemMap;
import nro.models.map.service.ChangeMapService;
import nro.models.map.service.MapService;
import nro.models.mob.Mob;
import nro.models.network.Message;
import nro.models.player.Player;
import nro.models.services.PlayerService;

import java.util.*;
import java.util.concurrent.*;

public class GoldenFrieza extends Boss {

    private static final ScheduledExecutorService bombScheduler = Executors.newScheduledThreadPool(1);

    private int status;
    private long lastStatusChange;
    private int timeChanges;
    private boolean callDeathBeam;

    public GoldenFrieza() throws Exception {
        super(BossID.GOLDEN_FRIEZA, BossesData.GOLDEN_FRIEZA);
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
    public void active() {
        super.active();
    }

    @Override
    public synchronized int injured(Player plAtt, long damage, boolean piercing, boolean isMobAttack) {
        if (this.isDie()) return 0;

        if (!piercing && Util.isTrue(this.nPoint.tlNeDon, 1000)) {
            this.chat("Xí hụt");
            return 0;
        }

        damage = this.nPoint.subDameInjureWithDeff(damage);

        if (!piercing && effectSkill.isShielding) {
            if (damage > nPoint.hpMax) {
                EffectSkillService.gI().breakShield(this);
            }
            damage = 1;
        }

        damage = Math.min(damage, 50_000_000);
        this.nPoint.subHP(damage);

        if (isDie()) {
            this.setDie(plAtt);
            die(plAtt);
        }

        return (int) damage;
    }

    @Override
    public void autoLeaveMap() {
        if (!TimeUtil.is21H()) {
            this.leaveMap();
        }
    }

    @Override
    public void joinMap() {
        if (TimeUtil.is21H()) {
            this.name = this.data[this.currentLevel].getName() + " " + Util.nextInt(1, 100);
            super.joinMap();
            if (this.zone != null) {
                for (Mob mob : this.zone.mobs) {
                    mob.injured(this, 99999999, true);
                }
                this.zone.isGoldenFriezaAlive = true;
            }
        } else {
            this.changeStatus(BossStatus.REST);
        }
    }

    @Override
    public void attack() {
        if (Util.canDoWithTime(this.lastTimeAttack, 100) && this.typePk == ConstPlayer.PK_ALL) {
            this.lastTimeAttack = System.currentTimeMillis();

            if (Util.canDoWithTime(lastStatusChange, timeChanges)) {
                callDeathBeam = false;
                timeChanges = Util.nextInt(5000, 10000);
                lastStatusChange = System.currentTimeMillis();
                status = Util.nextInt(3);
            }

            try {
                switch (status) {
                    case 0:
                        setBom();
                        timeChanges = 5000;
                        break;

                    case 1:
                        if (callDeathBeam) {
                            boolean allResting = Arrays.stream(this.bossAppearTogether[this.currentLevel])
                                                       .allMatch(b -> b.bossStatus == BossStatus.REST);
                            if (allResting) {
                                status = 2;
                                lastStatusChange = System.currentTimeMillis();
                                timeChanges = 30000;
                            }
                            return;
                        }
                        callDeathBeam = true;
                        for (Boss boss : this.bossAppearTogether[this.currentLevel]) {
                            if (boss.bossStatus == BossStatus.REST) {
                                boss.changeStatus(BossStatus.RESPAWN);
                            }
                        }
                        timeChanges = 15000;
                        break;

                    default:
                        timeChanges = 30000;
                        Player pl = getPlayerAttack();
                        if (pl == null || pl.isDie()) return;

                        this.playerSkill.skillSelect = this.playerSkill.skills.get(Util.nextInt(0, this.playerSkill.skills.size() - 1));

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
                        break;
                }
            } catch (Exception ex) {
                ex.printStackTrace(); // Logging khi có lỗi
            }
        }
    }

    public void setBom() {
        if (this.playerSkill.prepareTuSat) return;

        this.playerSkill.prepareTuSat = true;
        this.playerSkill.lastTimePrepareTuSat = System.currentTimeMillis();

        try {
            Message msg = new Message(-45);
            msg.writer().writeByte(7);
            msg.writer().writeInt((int) this.id);
            msg.writer().writeShort(104);
            msg.writer().writeShort(2000);
            Service.gI().sendMessAllPlayerInMap(this, msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }

        bombScheduler.schedule(() -> {
            this.playerSkill.prepareTuSat = false;
            List<Player> playersMap = this.zone.getNotBosses();
            if (!MapService.gI().isMapOffline(this.zone.map.mapId)) {
                for (Player pl : playersMap) {
                    if (!this.equals(pl)) {
                        pl.injured(this, 2_100_000_000, true, false);
                        PlayerService.gI().sendInfoHpMpMoney(pl);
                        Service.gI().Send_Info_NV(pl);
                    }
                }
            }
        }, 2500, TimeUnit.MILLISECONDS);
    }

    @Override
    public void leaveMap() {
        this.zone.isGoldenFriezaAlive = false;
        ChangeMapService.gI().exitMap(this);
        this.lastZone = null;
        this.lastTimeRest = System.currentTimeMillis();
        this.changeStatus(BossStatus.REST);
    }
}
