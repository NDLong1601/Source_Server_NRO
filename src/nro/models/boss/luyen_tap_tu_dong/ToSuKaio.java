package nro.models.boss.luyen_tap_tu_dong;


import nro.models.boss.BossID;
import nro.models.consts.BossStatus;
import nro.models.boss.BossesData;
import static nro.models.consts.BossType.PHOBAN;
import nro.models.services_dungeon.TrainingService;
import nro.models.player.Player;
import nro.models.services.Service;
import nro.models.map.service.ChangeMapService;
import nro.models.utils.Util;

public class ToSuKaio extends TrainingBoss {

    private long lastTimeLuyenTap;

    public ToSuKaio(Player player) throws Exception {
        super(PHOBAN, BossID.TO_SU_KAIO, BossesData.TO_SU_KAIO);
        this.playerAtt = player;
    }

    @Override
    public void joinMap() {
        if (playerAtt.zone != null) {
            this.zone = playerAtt.zone;
            ChangeMapService.gI().changeMap(this, this.zone, playerAtt.location.x, playerAtt.location.y);
            this.changeStatus(BossStatus.CHAT_S);
            lastTimeLuyenTap = System.currentTimeMillis();
        }
    }

    @Override
    public synchronized int injured(Player plAtt, long damage, boolean piercing, boolean isMobAttack) {
        return 0;
    }

    @Override
    public void active() {
        if (playerAtt.location != null && playerAtt != null && playerAtt.zone != null && this.zone != null && this.zone.equals(playerAtt.zone)) {
            if (Util.canDoWithTime(lastTimeLuyenTap, 10000)) {
                Service.gI().addSMTN(playerAtt, (byte) 2, TrainingService.gI().getTnsmMoiPhut(playerAtt) / 6, false);
                lastTimeLuyenTap = System.currentTimeMillis();
            }
        } else {
            this.leaveMap();
        }
    }
}
