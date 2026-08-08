package nro.models.mob_bigboss;
import nro.models.mob.BigBoss;
import nro.models.mob.Mob;
import nro.models.network.Message;
import nro.models.player.Player;
import nro.models.services.Service;
import nro.models.utils.Util;

public class VuaBachTuoc extends BigBoss {

    public VuaBachTuoc(Mob mob) {
        super(mob);
    }

    @Override
    public void update() {
        super.update();
        if (this.isDie()) {
            Service.gI().sendBigBoss(this.zone, 7, 0, -1, -1);
        }
    }

    @Override
    public void attack() {
        Player player = getPlayerCanAttack();
        if (!isDie() && !effectSkill.isHaveEffectSkill() && Util.canDoWithTime(lastBigBossAttackTime, 1000)) {
            if (player != null && player.location.x > 440 && player.location.x < 950) {
                int dameMob = this.point.getDameAttack();
                int dame = player.injured(null, dameMob, false, true);
                action = 3;
                int dis = Util.getDistance(player, this);
                if (dis <= 100) {
                    action = 3;
                } else if (dis > 100 && dis <= 200) {
                    action = 4;
                } else if (dis < 500) {
                    action = 5;
                    this.location.x = (short) player.location.x;
                }
                Message msg = null;
                try {
                    msg = new Message(102);
                    msg.writer().writeByte(action);
                    if (action == 3 || action == 4) {
                        msg.writer().writeByte(1); // SIZE PLAYER ATTACK
                        msg.writer().writeInt((int) player.id); // PLAYER ID
                        msg.writer().writeInt(dame); // DAME
                    } else {
                        msg.writer().writeShort(this.location.x);
                    }
                    Service.gI().sendMessAllPlayerInMap(zone, msg);
                } catch (Exception e) {
                } finally {
                    if (msg != null) {
                        msg.cleanup();
                    }
                }
            }
            lastBigBossAttackTime = System.currentTimeMillis();
        }
    }

}
