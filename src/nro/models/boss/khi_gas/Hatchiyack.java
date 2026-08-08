package nro.models.boss.khi_gas;
import nro.models.boss.Boss;
import nro.models.boss.BossData;
import nro.models.boss.BossID;
import nro.models.consts.BossStatus;
import nro.models.consts.ConstPlayer;
import nro.models.boss.Boss_Manager.GasDestroyManager;
import static nro.models.consts.BossType.PHOBANKGHD;
import nro.models.clan.Clan;
import nro.models.item.Item;
import nro.models.map.ItemMap;
import nro.models.map.Zone;
import nro.models.player.Player;
import nro.models.services.EffectSkillService;
import nro.models.skill.Skill;
import nro.models.services.Service;
import nro.models.map.service.ChangeMapService;
import nro.models.utils.Util;

public class Hatchiyack extends Boss {

    private final int level;
    private Clan clan;

    private static final int[][] FULL_DEMON = new int[][]{{Skill.DEMON, 1}, {Skill.DEMON, 2}, {Skill.DEMON, 3}, {Skill.DEMON, 4}, {Skill.DEMON, 5}, {Skill.DEMON, 6}, {Skill.DEMON, 7}};

    public Hatchiyack(Zone zone, Clan clan, int level, int dame, int hp) throws Exception {
        super(PHOBANKGHD, BossID.HATCHIYACK, new BossData(
                " ",
                ConstPlayer.TRAI_DAT,
                new short[]{639, 640, 641, -1, -1, -1},
                (dame),
                new int[]{hp},
                new int[]{148},
                (int[][]) Util.addArray(FULL_DEMON),
                new String[]{"|-1|Các ngươi dám hạ sư phụ ta",
                    "|-1|Ta sẽ tiêu diệt hết các ngươi"},
                new String[]{"|-1|Đại bác báo thù...",
                    "|-1|Heyyyyyyyy Yaaaaa"},
                new String[]{"|-1|Các ngươi khó mà rời khỏi nơi đây"},
                60
        ));
        this.zone = zone;
        this.level = level;
        this.clan = clan;
    }

    @Override
    public synchronized int injured(Player plAtt, long damage, boolean piercing, boolean isMobAttack) {
        if (!this.isDie()) {
            if (!piercing && Util.isTrue(this.level + 10, 1000)) {
                this.chat("Xí hụt");
                return 0;
            }

            if (plAtt != null && plAtt.idNRNM != -1) {
                return 1;
            }

            damage = this.nPoint.subDameInjureWithDeff(damage + Util.nextInt(-200 * this.level, 0));

            damage -= damage / 100 * (this.level / 5);

            if (!piercing && effectSkill.isShielding) {
                if (damage > nPoint.hpMax) {
                    EffectSkillService.gI().breakShield(this);
                }
                damage = 1;
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
    public void reward(Player plKill) {
        dropCt(0);
        for (int i = 0; i < this.zone.getNumOfPlayers(); i++) {
            int x = (i + 1) * 50;
            dropCt(x);
            dropCt(-x);
        }
    }

    private void dropCt(int x) {
        ItemMap it = new ItemMap(zone, 729, 1, this.location.x + x, this.zone.map.yPhysicInTop(this.location.x,
                this.location.y - 24), -1);
        it.options.clear();
        int ParamMax;
        if (level >= 0 && level <= 9) {
            ParamMax = 14;
        } else if (level <= 110) {
            ParamMax = 14 + (level / 10);  // Cứ tăng 1 mỗi 10 cấp độ
        } else {
            ParamMax = 26;  // Với level 110 trở lên
        }

        if (ParamMax < 3) {
            ParamMax = 3;
        }

        int hsd = Util.nextInt(3, ParamMax);
        it.options.add(new Item.ItemOption(50, ParamMax + Util.nextInt(8, 11)));
        it.options.add(new Item.ItemOption(77, ParamMax + Util.nextInt(8, 11)));
        it.options.add(new Item.ItemOption(103, ParamMax + Util.nextInt(8, 11)));
        it.options.add(new Item.ItemOption(5, ParamMax + Util.nextInt(0, 3)));
        it.options.add(new Item.ItemOption(93, hsd > 21 ? 21 : hsd));
        it.options.add(new Item.ItemOption(30, 0));
        Service.gI().dropItemMap(this.zone, it);
    }

    @Override
    public void joinMap() {
        ChangeMapService.gI().changeMap(this, this.zone, 480, 295);
        this.moveTo(480, 480);
        this.changeStatus(BossStatus.CHAT_S);
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
        if (clan != null && clan.KhiGasHuyDiet != null) {
            clan.KhiGasHuyDiet.hatchiyatchDead = true;
        }
        ChangeMapService.gI().exitMap(this);
        this.lastZone = null;
        this.lastTimeRest = System.currentTimeMillis();
        this.changeStatus(BossStatus.REST);
        GasDestroyManager.gI().removeBoss(this);
        this.dispose();
    }
}
