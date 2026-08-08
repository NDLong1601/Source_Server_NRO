package nro.models.mob_bigboss;
import java.util.ArrayList;
import java.util.List;
import nro.models.mob.BigBoss;
import nro.models.mob.Mob;
import nro.models.network.Message;
import nro.models.player.Player;
import nro.models.services.Service;
import nro.models.utils.Util;

public class Piano extends BigBoss {

    public Piano(Mob mob) {
        super(mob);
    }

    @Override
    public void attack() {
        if (!isDie() && !effectSkill.isHaveEffectSkill() && Util.canDoWithTime(lastBigBossAttackTime, 3000)) {
            // 10 : di chuyển, 11 - 20 : tấn công, 21 : bay, 22 : ..., 23 : die

            if (this.zone.getNotBosses().isEmpty()) {
                return;
            }
            List<Player> players = new ArrayList<>();

            action = Util.nextInt(11, 14);

            switch (action) {
                case 11 -> {
                    for (Player pl : this.zone.getNotBosses()) {
                        if (Util.getDistance(pl, this) < 50) {
                            players.add(pl);
                            break;
                        }
                    }
                }
                case 12 -> {
                    for (Player pl : this.zone.getNotBosses()) {
                        if (Util.getDistance(pl, this) < 100) {
                            players.add(pl);
                            break;
                        }
                    }
                }
                case 13, 14 -> {
                    for (Player pl : this.zone.getNotBosses()) {
                        if (Util.getDistance(pl, this) < 150) {
                            players.add(pl);
                        }
                    }
                }
            }

            if (players.isEmpty()) {
                int index = Util.nextInt(0, this.zone.getNotBosses().size() - 1);
                players.add(this.zone.getNotBosses().get(index));
                action = 10;
//                return;
            }

            Message msg = null;
            try {
                msg = new Message(102);
                msg.writer().writeByte(action);
                msg.writer().writeByte(this.id);
                switch (action) {
                    case 10, 21 -> {
                        for (Player player : players) {
                            this.location.x = player.location.x + Util.nextInt(-10, 10);
                            this.location.y = player.location.y;
                        }
                        msg.writer().writeShort(this.location.x);
                        msg.writer().writeShort(this.location.y);
                    }
                    case 11, 12, 13, 14, 15, 16, 17, 18, 19, 20 -> {
                        msg.writer().writeByte(players.size()); // sl player;
                        int dir = 0;
                        for (Player pl : players) {
                            int dame = pl.injured(null, this.point.getDameAttack(), false, true);
                            msg.writer().writeInt((int) pl.id); // id player
                            msg.writer().writeInt(dame); // dame
                            dir = pl.location.x < this.location.x ? -1 : 1;
                        }
                        msg.writer().writeByte(dir); // dir
                    }
                    case 22 -> {
                    }
                    case 23 -> {
                    }
                    default -> {
                    }
                }
                Service.gI().sendMessAllPlayerInMap(this.zone, msg);
                lastBigBossAttackTime = System.currentTimeMillis();
            } catch (Exception e) {
            } finally {
                if (msg != null) {
                    msg.cleanup();
                    msg = null;
                }
            }
        }
    }

}
