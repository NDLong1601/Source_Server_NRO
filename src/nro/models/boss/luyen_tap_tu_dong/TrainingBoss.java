package nro.models.boss.luyen_tap_tu_dong;


import nro.models.consts.ConstRatio;
import nro.models.boss.Boss;
import nro.models.boss.BossData;
import nro.models.boss.Boss_Manager.OtherBossManager;
import nro.models.consts.BossStatus;
import nro.models.consts.BossType;
import nro.models.network.Message;
import nro.models.consts.ConstPlayer;
import java.io.IOException;
import nro.models.services_dungeon.TrainingService;
import nro.models.player.Player;
import nro.models.services.EffectSkillService;
import nro.models.map.service.MapService;
import nro.models.services.PlayerService;
import nro.models.services.Service;
import nro.models.services.SkillService;
import nro.models.map.service.ChangeMapService;
import nro.models.utils.Logger;
import nro.models.utils.SkillUtil;
import nro.models.utils.Util;

public abstract class TrainingBoss extends Boss {

    public Player playerAtt;
    protected long timeJoinMap;
    protected long lastTimeAFK;
    protected long lastTimeMove;
    public boolean doneChatS;
    public long lastTimeChat;
    protected boolean isPlayerDie;
    public long lastTimeBuff;

    public TrainingBoss(BossType LuyenTap, int id, BossData data) throws Exception {
        super(LuyenTap, id, data);
        this.bossStatus = BossStatus.RESPAWN;
    }

    @Override
    public void checkPlayerDie(Player pl) {
        if (pl.isDie()) {
            this.chat("Luyện tập tiếp đi");
            isPlayerDie = true;
            lastTimeAFK = System.currentTimeMillis();
            changeStatus(BossStatus.AFK);
            changeToTypeNonPK();
            Service.gI().sendPlayerVS(playerAtt, null, (byte) 0);
        }
    }

    @Override
    public void active() {
        if (this.typePk == ConstPlayer.NON_PK) {
            this.changeToTypePK();
            Service.gI().sendPVB(playerAtt, this, ConstPlayer.PK_PVP);
        }
        this.attack();
    }

    public void hutMau() {
    }

    public void tanHinh() {

    }

    public void bayLungTung() {

    }

    public void buffPea() {
        if (Util.canDoWithTime(lastTimeBuff, 30000)) {
            this.nPoint.hp += this.nPoint.hpMax / 5;
            this.nPoint.mp = this.nPoint.mpMax;
            Service.gI().sendInfoPlayerEatPea(this);
            lastTimeBuff = System.currentTimeMillis();
        }
    }

    protected void goToXY(int x, int y, boolean isTeleport) {
        if (!isTeleport) {
            byte dir = (byte) (this.location.x - x < 0 ? 1 : -1);
            byte move = (byte) Util.nextInt(50, 100);
            PlayerService.gI().playerMove(this, this.location.x + (dir == 1 ? move : -move), y);
        } else {
            ChangeMapService.gI().changeMapYardrat(this, this.zone, x, y);
        }
    }

    protected void goToXY(int x, int y) {
        byte dir = (byte) (this.location.x - x < 0 ? 1 : -1);
        byte move = (byte) Util.nextInt(50, 100);
        this.location.x = this.location.x + (dir == 1 ? move : -move);
        this.location.y = y;
        MapService.gI().sendPlayerMove(this);
    }

    @Override
    public void attack() {
        try {
            if (playerAtt.location != null && playerAtt != null && playerAtt.zone != null && this.zone != null && this.zone.equals(playerAtt.zone)) {
                if (this.isDie()) {
                    return;
                }
                hutMau();
                tanHinh();
                bayLungTung();
                buffPea();
                this.playerSkill.skillSelect = this.playerSkill.skills.get(Util.nextInt(0, this.playerSkill.skills.size() - 1));
                if (Util.getDistance(this, playerAtt) <= this.getRangeCanAttackWithSkillSelect()) {
                    if (Util.isTrue(15, ConstRatio.PER100) && SkillUtil.isUseSkillChuong(this)) {
                        goToXY(playerAtt.location.x + (Util.getOne(-1, 1) * Util.nextInt(20, 80)), Util.nextInt(10) % 2 == 0 ? playerAtt.location.y : playerAtt.location.y - Util.nextInt(0, 50), false);
                    }
                    SkillService.gI().useSkill(this, playerAtt, null, -1, null);
                    checkPlayerDie(playerAtt);
                } else {
                    goToPlayer(playerAtt, false);
                }
            } else {
                this.leaveMap();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    protected void goToPlayer(Player pl, boolean isTeleport) {
        goToXY(pl.location.x, pl.location.y, isTeleport);
    }

    @Override
    public void joinMap() {
        if (playerAtt.zone != null) {
            this.zone = playerAtt.zone;
            ChangeMapService.gI().changeMap(this, this.zone, 420, 408);
            this.changeStatus(BossStatus.CHAT_S);
        }
    }

    protected void immortalMp() {
        this.nPoint.mp = this.nPoint.mpg;
    }

    @Override
    public synchronized int injured(Player plAtt, long damage, boolean piercing, boolean isMobAttack) {
        if (!this.isDie()) {
            if (!piercing && Util.isTrue(400, 1000)) {
                this.chat("Xí hụt");
                return 0;
            }

            if (plAtt != null && plAtt.idNRNM != -1) {
                return 1;
            }
//            if (damage > this.nPoint.hpMax / 10) {
//                damage = this.nPoint.hpMax / 10;
//            }
            this.nPoint.subHP(damage);

            if (this.nPoint.hp > 0 && this.nPoint.hp < this.nPoint.hpMax / 5) {
                if (Util.canDoWithTime(lastTimeChat, 2000)) {
                    String[] text = {"AAAAAAAAA", "ai da"};
                    this.chat(text[Util.nextInt(text.length)]);
                }
            }

            if (isDie()) {
                this.setDie(plAtt);
                die(plAtt);
            }

            return (int) damage;
        } else {
            return 0;
        }
    }

    protected void notifyPlayeKill(Player player) {

    }

    @Override
    public void die(Player plKill) {
        this.changeStatus(BossStatus.AFK);
        this.chatE();
        this.lastTimeAFK = System.currentTimeMillis();
        Service.gI().sendPlayerVS(playerAtt, null, (byte) 0);
        if (playerAtt.isThachDau) {
            playerAtt.levelLuyenTap++;
        }
    }

    @Override
    public void leaveMap() {
        ChangeMapService.gI().exitMap(this);
        Player npc = TrainingService.gI().getNonInteractiveNPC(zone, (int) this.id);
        if (npc != null) {
            this.nPoint.hp = this.nPoint.hpMax;
            Service.gI().Send_Info_NV(this);
            this.goToPlayer(npc, false);
        } else {
            Message msg;
            try {
                msg = new Message(-6);
                msg.writer().writeInt((int) this.id);
                playerAtt.sendMessage(msg);
                msg.cleanup();
                this.zone = null;
            } catch (IOException e) {
                Logger.logException(MapService.class, e);
            }
            TrainingService.gI().luyenTapEnd(playerAtt, (int) this.id);
        }

        this.lastZone = null;
        this.lastTimeRest = System.currentTimeMillis();
        this.changeStatus(BossStatus.REST);
        OtherBossManager.gI().removeBoss(this);
        this.dispose();
    }
}
