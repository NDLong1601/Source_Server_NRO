package nro.models.Bot;

import java.util.List;
import java.util.Random;
import nro.models.consts.ConstPlayer;
import nro.models.map.service.MapService;
import nro.models.mob.Mob;
import nro.models.network.Message;
import nro.models.player.NPoint;
import nro.models.player.Player;
import nro.models.services.PlayerService;
import nro.models.services.Service;
import nro.models.services.SkillService;
import nro.models.services_func.EffectMapService;
import nro.models.skill.PlayerSkill;
import nro.models.skill.Skill;
import nro.models.utils.Logger;
import nro.models.utils.SkillUtil;

import java.util.List;
import java.util.Random;
import nro.models.map.Map;
import nro.models.map.Zone;
import nro.models.map.service.ChangeMapService;

public class BotAttackplayer extends Bot {

    private static final long ATTACK_COOLDOWN_MS = 500;
    private long lastAttackTime = 0;
    private Random random = new Random();
    private Object bot;
    public Player player;
    public Player targetPlayer;

    public BotAttackplayer(short head, short body, short leg, int type, String name, short flag) {
        super(head, body, leg, type, name, null, flag);

        try {
            this.nPoint = new NPoint(this);
            this.nPoint.hpMax = 500000;
            this.nPoint.hp = this.nPoint.hpMax;
            this.nPoint.mpMax = 200000;
            this.nPoint.mp = this.nPoint.mpMax;
            this.nPoint.dame = 100000;

            this.playerSkill = new PlayerSkill(this);

            int[] allSkills = {
                Skill.DRAGON, Skill.KAMEJOKO, Skill.DEMON, Skill.GALICK,
                Skill.MASENKO, Skill.ANTOMIC, Skill.TRI_THUONG, Skill.THAI_DUONG_HA_SAN,
                Skill.LIEN_HOAN, Skill.KAIOKEN, Skill.QUA_CAU_KENH_KHI, Skill.MAKANKOSAPPO,
                Skill.DE_TRUNG, Skill.BIEN_KHI, Skill.TU_SAT, Skill.KHIEN_NANG_LUONG,
                Skill.SOCOLA, Skill.HUYT_SAO, Skill.TROI, Skill.DICH_CHUYEN_TUC_THOI,
                Skill.THOI_MIEN, Skill.SUPER_KAME, Skill.LIEN_HOAN_CHUONG, Skill.MA_PHONG_BA
            };

            for (int skillId : allSkills) {
                Skill skill = SkillUtil.createSkill(skillId, 7);
                this.playerSkill.skills.add(skill);
            }

            if (!this.playerSkill.skills.isEmpty()) {
                this.playerSkill.skillSelect = this.playerSkill.skills.get(0);
            }

            PlayerService.gI().playerMove(this, this.location.x, this.location.y);

        } catch (Exception e) {
            Logger.logException(BotAttackplayer.class, e);
        }
    }

    @Override
    public void update() {
        try {
            if (targetPlayer == null || targetPlayer.isDie()) {
                findTarget();
            }

            if (targetPlayer != null && targetPlayer.zone != null) {
                if (this.zone == null || this.zone.map.mapId != targetPlayer.zone.map.mapId) {
                    changeMap(targetPlayer.zone.map.mapId);
                } else {
                    followTarget();
                    tryAttack();
                }
            }

        } catch (Exception e) {
            Logger.logException(BotAttackplayer.class, e);
        }
    }

    private void findTarget() {
        List<Player> players = MapService.gI().getPlayersInMap(this.zone.map.mapId);
        Player closest = null;
        int minDistance = Integer.MAX_VALUE;

        for (Player p : players) {
            if (p == this || p.isDie() || !p.isPl()) {
                continue;
            }
            int dist = Math.abs(this.location.x - p.location.x);
            if (dist < minDistance) {
                minDistance = dist;
                closest = p;
            }
        }

        this.targetPlayer = closest;
    }

    private void followTarget() {
        if (targetPlayer != null && !targetPlayer.isDie() && this.zone == targetPlayer.zone) {
            PlayerService.gI().playerMove(this, targetPlayer.location.x, targetPlayer.location.y);
        }
    }

    private void tryAttack() {
        if (targetPlayer == null || targetPlayer.isDie()) {
            return;
        }

        if (System.currentTimeMillis() - lastAttackTime < ATTACK_COOLDOWN_MS) {
            return;
        }

        selectRandomSkill();
        attackTarget();
        lastAttackTime = System.currentTimeMillis();
    }

    private void selectRandomSkill() {
        if (playerSkill != null && !playerSkill.skills.isEmpty()) {
            int index = random.nextInt(playerSkill.skills.size());
            playerSkill.skillSelect = playerSkill.skills.get(index);
        }
    }

    private void attackTarget() {
        if (playerSkill == null || targetPlayer == null || targetPlayer.isDie()) {
            return;
        }

        long damage = this.nPoint.getDameAttack(false);

        if (this.effectSkin != null && this.effectSkin.isXDame) {
            this.effectSkin.isXDame = false;
            if (targetPlayer.isBoss) {
                damage /= 3;
            }
        }

        boolean isMiss = false;
        int realDame = targetPlayer.injured(this, isMiss ? 0 : damage, false, false);

        Skill skill = playerSkill.skillSelect;

        try {
            Message msg = new Message(-60);
            msg.writer().writeInt((int) this.id);
            msg.writer().writeByte(skill.skillId);
            msg.writer().writeByte(1);
            msg.writer().writeInt((int) targetPlayer.id);
            msg.writer().writeByte(1);
            msg.writer().writeByte(0);
            msg.writer().writeInt(realDame);
            msg.writer().writeBoolean(targetPlayer.isDie());
            msg.writer().writeBoolean(nPoint.isCrit);
            Service.gI().sendMessAllPlayerInMap(this, msg);
            msg.cleanup();
        } catch (Exception e) {
            Logger.logException(getClass(), e);
        }

        hutHPMP(this, realDame, targetPlayer, null);

        if (isPl() && targetPlayer.isPl()
                && typePk == ConstPlayer.PK_PVP_2 && targetPlayer.typePk == ConstPlayer.PK_PVP_2) {
            long smtn = nPoint.calSucManhTiemNang(realDame / 10)
                    / (Math.abs(Service.gI().getCurrLevel(this) - Service.gI().getCurrLevel(targetPlayer)) + 1);
            Service.gI().addSMTN(targetPlayer, (byte) 2, smtn, false);
        }
    }

    public void changeMap(int mapId) {
        try {
            Player refPlayer = (player != null) ? player : targetPlayer;
            if (refPlayer == null) {
                return;
            }

            Zone newZone = MapService.gI().getMapCanJoin(refPlayer, mapId, mapId);
            if (newZone == null || this.zone == newZone) {
                return;
            }

            if (this.zone != null) {
                this.zone.removeBot(this);
            }

            this.zone = newZone;
            this.location.x = newZone.map.spawnX;
            this.location.y = newZone.map.spawnY;
            newZone.addBot(this);
            for (Player pl : newZone.getPlayers()) {
                if (pl != null && pl.session != null) {
                    Service.gI().sendAppear(this, pl);
                }
            }
        } catch (Exception e) {
            Logger.logException(BotAttackplayer.class, e);
        }
    }

    private void phanSatThuong(Player attacker, Player target, long dame) {
        if (attacker == null) {
            return;
        }
        int percentPST = target.nPoint.tlPST;
        if (percentPST == 0) {
            return;
        }

        int damePST = (int) (dame * percentPST / 100L);
        if (damePST >= attacker.nPoint.hp) {
            damePST = attacker.nPoint.hp - 1;
        }

        Message msg = null;
        try {
            msg = new Message(56);
            msg.writer().writeInt((int) attacker.id);
            damePST = attacker.injured(attacker, damePST, true, false);
            msg.writer().writeInt(attacker.nPoint.hp);
            msg.writer().writeInt(damePST);
            msg.writer().writeBoolean(false);
            msg.writer().writeByte(36);
            Service.gI().sendMessAllPlayerInMap(attacker, msg);
        } catch (Exception e) {
            Logger.logException(SkillService.class, e);
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    private void hutHPMP(Player player, long dame, Player pl, Mob mob) {
        int tiLeHutHp = player.nPoint.getTileHutHp(mob != null);
        int tiLeHutMp = player.nPoint.getTiLeHutMp();

        int hpHoi = (int) (dame * tiLeHutHp / 100);
        int mpHoi = (int) (dame * tiLeHutMp / 100);

        if (hpHoi > 0 || mpHoi > 0) {
            int x = -1, y = -1;
            if (pl != null) {
                x = pl.location.x;
                y = pl.location.y;
            } else if (mob != null) {
                x = mob.location.x;
                y = mob.location.y;
            }
            EffectMapService.gI().sendEffectMapToAllInMap(player, 37, 3, 1, x, y, -1);
            PlayerService.gI().hoiPhuc(player, hpHoi, mpHoi);
        }
    }

}
