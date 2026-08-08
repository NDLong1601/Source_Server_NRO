package nro.models.mob_bigboss;

import nro.models.item.Item.ItemOption;
import nro.models.map.ItemMap;
import nro.models.mob.BigBoss;
import nro.models.mob.Mob;
import nro.models.network.Message;
import nro.models.player.Player;
import nro.models.services.ItemService;
import nro.models.services.Service;
import nro.models.utils.Util;

public class Hirudegarn extends BigBoss {

    private int errors;

    public Hirudegarn(Mob mob) {
        super(mob);
    }

    @Override
    public void injured(Player plAtt, long damage, boolean dieWhenHpFull) {
        damage = this.point.hp / 100 > 0 ? this.point.hp / 100 : 1;
        super.injured(plAtt, damage, false);
    }

    @Override
    public void update() {
        super.update();
        if (isDie() && (System.currentTimeMillis() - lastTimeDie) > 600000 && lvMob == 3) {
            lvMob = 0;
            action = 0;
            this.location.x = Util.nextInt(100, 900);
            this.location.y = 360;
            this.point.hp = this.point.getHpFull();
            Service.gI().sendBigBoss2(this.zone, action, this);
            Message msg = null;
            try {
                msg = new Message(-9);
                msg.writer().writeByte(this.id);
                msg.writer().writeInt(this.point.gethp());
                msg.writer().writeInt(1);
                Service.gI().sendMessAllPlayerInMap(this.zone, msg);
            } catch (Exception e) {
            } finally {
                if (msg != null) {
                    msg.cleanup();
                }
            }
        } else if (isDie() && (System.currentTimeMillis() - lastTimeDie) > 5000 && lvMob <= 2) {
            switch (lvMob) {
                case 0 -> {
                    lvMob = 1;
                    action = 6;
                    this.point.hp = this.point.getHpFull();
                }
                case 1 -> {
                    lvMob = 2;
                    action = 5;
                    this.point.hp = this.point.getHpFull();
                }
                case 2 -> {
                    lvMob = 3;
                    action = 9;
                }
                default -> {
                }
            }

            int trai = 0;
            int phai = 1;
            int next = 0;
            for (int i = 0; i < 30; i++) {
                int X = next == 0 ? -5 * trai : 5 * phai;
                if (next == 0) {
                    trai++;
                } else {
                    phai++;
                }
                next = next == 0 ? 1 : 0;
                if (trai > 10) {
                    trai = 0;
                }
                if (phai > 10) {
                    phai = 1;
                }
                Service.gI().dropItemMap(
                        this.zone,
                        new ItemMap(zone, 190, 32000, this.location.x + X, this.location.y, -1)
                );
            }
            if (Util.isTrue(20, 100)) {
                ItemMap it = new ItemMap(this.zone, 568, 1, this.location.x, this.zone.map.yPhysicInTop(this.location.x,
                        this.location.y - 24), -1);
                Service.gI().dropItemMap(this.zone, it);
            }
            if (Util.isTrue(20, 100)) {
                ItemMap it = new ItemMap(this.zone, 1795, 1, this.location.x,
                        this.zone.map.yPhysicInTop(this.location.x, this.location.y - 24), -1);

                it.options.add(new ItemOption(250, 0));

                Service.gI().dropItemMap(this.zone, it);
            }

            if (Util.isTrue(1, 40)) {
                short[] possibleIds = {555, 557, 559};
                short idItem = possibleIds[Util.nextInt(possibleIds.length)];
                ItemMap it = new ItemMap(this.zone, idItem, 1, this.location.x,
                        this.zone.map.yPhysicInTop(this.location.x, this.location.y - 24), -1);
                ItemMap itemWithOptions = ItemService.gI().randDoTL(this.zone, 1, this.location.x,
                        this.zone.map.yPhysicInTop(this.location.x, this.location.y - 24), -1);
                it.options.clear();
                it.options.addAll(itemWithOptions.options);
                Service.gI().dropItemMap(this.zone, it);

            }
            Service.gI().sendBigBoss2(this.zone, action, this);
            if (lvMob <= 2) {
                Message msg = null;
                try {
                    msg = new Message(-9);
                    msg.writer().writeByte(this.id);
                    msg.writer().writeInt(this.point.gethp());
                    msg.writer().writeInt(1);
                    Service.gI().sendMessAllPlayerInMap(this.zone, msg);
                } catch (Exception e) {
                } finally {
                    if (msg != null) {
                        msg.cleanup();
                    }
                }
            } else {
                this.location.x = -1000;
                this.location.y = -1000;
            }
        }
        if (isDie() && lvMob == 3 && (System.currentTimeMillis() - lastTimeDie) > 15000) {
            lvMob = 0;
            action = 0;
            this.location.x = Util.nextInt(100, 900);
            this.location.y = 360;
            this.point.hp = this.point.getHpFull();
            Service.gI().sendBigBoss2(this.zone, action, this);
            Message msg = null;
            try {
                msg = new Message(-9);
                msg.writer().writeByte(this.id);
                msg.writer().writeInt(this.point.gethp());
                msg.writer().writeInt(1);
                Service.gI().sendMessAllPlayerInMap(this.zone, msg);
            } catch (Exception e) {
            } finally {
                if (msg != null) {
                    msg.cleanup();
                }
            }
        }
    }

    @Override
    public void attack() {
        if (!isDie() && !effectSkill.isHaveEffectSkill() && Util.canDoWithTime(lastBigBossAttackTime, 3000)) {
            Message msg = null;
            try {
                int[] idAction = new int[]{1, 2, 3, 7};
                if (this.lvMob >= 2) {
                    idAction = new int[]{1, 2};
                }
                action = action == 7 ? 0 : idAction[Util.nextInt(0, idAction.length - 1)];
                if (this.zone.getPlayers().isEmpty()) {
                    return;
                }
                int index = Util.nextInt(0, this.zone.getPlayers().size() - 1);
                Player player = this.zone.getPlayers().get(index);
                if (player == null || player.isDie()) {
                    return;
                }
                if (action == 1) {
                    this.location.x = (short) player.location.x;
                    Service.gI().sendBigBoss2(this.zone, 8, this);
                }
                msg = new Message(101);
                msg.writer().writeByte(action);
                if (action >= 0 && action <= 4) {
                    switch (action) {
                        case 1:
                            msg.writer().writeByte(1);
                            int dame = player.injured(null, this.point.getDameAttack(), false, true);
                            msg.writer().writeInt((int) player.id);
                            msg.writer().writeInt(dame);
                            break;
                        case 3:
                            this.location.x = (short) player.location.x;
                            msg.writer().writeShort(this.location.x);
                            msg.writer().writeShort(this.location.y);
                            break;
                        default:
                            msg.writer().writeByte(this.zone.getPlayers().size());
                            for (int i = 0; i < this.zone.getPlayers().size(); i++) {
                                Player pl = this.zone.getPlayers().get(i);
                                dame = pl.injured(null, this.point.getDameAttack(), false, true);
                                msg.writer().writeInt((int) pl.id);
                                msg.writer().writeInt(dame);
                            }
                            break;
                    }
                } else {
                    if (action == 6 || action == 8) {
                        this.location.x = (short) player.location.x;
                        msg.writer().writeShort(this.location.x);
                        msg.writer().writeShort(this.location.y);
                    }
                }
                Service.gI().sendMessAllPlayerInMap(this.zone, msg);
                lastBigBossAttackTime = System.currentTimeMillis();
            } catch (Exception e) {
                if (errors < 5) {
                    errors++;
                    e.printStackTrace();
                }
            } finally {
                if (msg != null) {
                    msg.cleanup();
                }
            }
        }
    }

}
