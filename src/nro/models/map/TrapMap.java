package nro.models.map;
import nro.models.player.Player;
import nro.models.services.PlayerService;
import nro.models.services_func.EffectMapService;
import nro.models.utils.Util;

public class TrapMap {

    public int x;
    public int y;
    public int w;
    public int h;
    public int effectId;
    public long dame;

    public void doPlayer(Player player) {
        if (this.effectId == 49) {
            if (!player.isDie() && Util.canDoWithTime(player.idMark.getLastTimeAnXienTrapBDKB(), 1000)) {
                player.injured(null, dame + (Util.nextLong(-10L, 10L) * dame / 100L), false, false);
                PlayerService.gI().sendInfoHp(player);
                EffectMapService.gI().sendEffectMapToAllInMap(player.zone, effectId, 2, 1, player.location.x - 32, 1040, 1);
                player.idMark.setLastTimeAnXienTrapBDKB(System.currentTimeMillis());
            }
        }
    }

}
