package nro.models.mob_bigboss;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nro.models.clan.Clan;
import nro.models.clan.ClanMember;
import nro.models.mob.Mob;
import nro.models.network.Message;
import nro.models.player.Player;
import nro.models.services.Service;
import nro.models.utils.Util;

public class GauTuongCuop extends Mob {

    private long lastAttackTime;
    private final Map<Player, Long> damageMap = new HashMap<>();

    public GauTuongCuop(Mob mob) {
        super(mob);
        this.zone = mob.zone;
        this.name = "Gấu Tướng Cướp";
        this.type = 5;
    }

    @Override
    public void update() {
        if (!this.isDie()) {
            this.attack();
        }
    }

    @Override
    public void attack() {
        if (!isDie() && !effectSkill.isHaveEffectSkill() && Util.canDoWithTime(lastAttackTime, 300)) {
            List<Player> playersInZone = this.zone.getNotBosses();
            if (playersInZone.isEmpty()) {
                return;
            }

            int action = Util.nextInt(11, 15);
            List<Player> targets = new ArrayList<>();

            for (Player p : playersInZone) {
                int dist = Util.getDistance(p, this);
                if ((action == 11 && dist < 50)
                        || (action == 12 && dist < 100)
                        || ((action == 13 || action == 14) && dist < 150)
                        || (action == 15 && dist < 200)) {
                    targets.add(p);
                }
            }

            if (targets.isEmpty()) {
                targets.add(playersInZone.get(Util.nextInt(playersInZone.size())));
                action = 10;
            }

            Message msg = null;
            try {
                msg = new Message(102);
                msg.writer().writeByte(action);
                msg.writer().writeByte(this.id);

                if (action == 10 || action == 21) {
                    Player pl = targets.get(0);
                    this.location.x = pl.location.x + Util.nextInt(-10, 10);
                    this.location.y = pl.location.y;
                    msg.writer().writeShort(this.location.x);
                    msg.writer().writeShort(this.location.y);
                } else {
                    msg.writer().writeByte(targets.size());
                    int dir = 0;
                    for (Player pl : targets) {
                        int dame = pl.injured(null, this.getDameAttack(), false, true);
                        msg.writer().writeInt((int) pl.id);
                        msg.writer().writeInt(dame);
                        dir = pl.location.x < this.location.x ? -1 : 1;
                    }
                    msg.writer().writeByte(dir);
                }

                Service.gI().sendMessAllPlayerInMap(this.zone, msg);
                lastAttackTime = System.currentTimeMillis();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (msg != null) {
                    msg.cleanup();
                }
            }
        }
    }

    @Override
    public void injured(Player plAtt, long damage, boolean dieWhenHpFull) {
        damage = Math.max((long) (damage * 0.05), 1);

        if (plAtt != null) {
            damageMap.put(plAtt, damageMap.getOrDefault(plAtt, 0L) + damage);
        }

        super.injured(plAtt, damage, dieWhenHpFull);
    }

    @Override
    public void setDie() {
        if (!this.isDie()) {
            Message msg = null;
            try {
                msg = new Message(-30);
                msg.writer().writeByte(-1);
                msg.writer().writeByte(1);
                msg.writer().writeByte(this.id);
                Service.gI().sendMessAllPlayerInMap(this.zone, msg);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (msg != null) {
                    msg.cleanup();
                }
            }

            rewardContributors();

            damageMap.clear();
        }

        super.setDie();
    }

    public int getDameAttack() {
        return 19_999_999;
    }

    private void rewardContributors() {
        if (damageMap.isEmpty()) {
            return;
        }

        long totalDamage = damageMap.values().stream().mapToLong(Long::longValue).sum();
        if (totalDamage == 0) {
            return;
        }

        Map<Clan, Integer> clanCapsuleMap = new HashMap<>();
        Map<Player, Integer> playerCapsuleMap = new HashMap<>();

        int totalCapsule = 50;
        int distributedCapsule = 0;

        for (Map.Entry<Player, Long> entry : damageMap.entrySet()) {
            Player player = entry.getKey();
            long playerDamage = entry.getValue();

            if (player == null || player.clan == null) {
                continue;
            }

            double ratio = (double) playerDamage / totalDamage;
            int capsules = (int) Math.floor(ratio * totalCapsule);

            if (capsules <= 0) {
                capsules = 1;
            }
            playerCapsuleMap.put(player, capsules);
            clanCapsuleMap.put(player.clan, clanCapsuleMap.getOrDefault(player.clan, 0) + capsules);
            distributedCapsule += capsules;
        }

        if (distributedCapsule < totalCapsule) {
            Player topPlayer = Collections.max(damageMap.entrySet(), Map.Entry.comparingByValue()).getKey();
            if (topPlayer != null) {
                int remain = totalCapsule - distributedCapsule;
                playerCapsuleMap.put(topPlayer, playerCapsuleMap.get(topPlayer) + remain);
                clanCapsuleMap.put(topPlayer.clan, clanCapsuleMap.get(topPlayer.clan) + remain);
            }
        }

        for (Map.Entry<Player, Integer> entry : playerCapsuleMap.entrySet()) {
            Player player = entry.getKey();
            int capsuleReceived = entry.getValue();

            player.lastClanCheckIn = System.currentTimeMillis();

            for (ClanMember cm : player.clan.getMembers()) {
                if (cm.id == player.id) {
                    cm.memberPoint += capsuleReceived;
                    cm.clanPoint += capsuleReceived;
                    break;
                }
            }

            Service.gI().sendThongBao(player, "Bạn nhận được " + capsuleReceived + " Capsule bang hội từ Gấu Tướng Cướp!");
        }

        for (Map.Entry<Clan, Integer> entry : clanCapsuleMap.entrySet()) {
            Clan clan = entry.getKey();
            clan.capsuleClan += entry.getValue();
        }
    }

}
