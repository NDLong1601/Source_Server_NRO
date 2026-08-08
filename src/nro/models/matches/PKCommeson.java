package nro.models.matches;
import nro.models.matches.PVP;
import nro.models.matches.TYPE_LOSE_PVP;
import nro.models.matches.TYPE_PVP;
import nro.models.player.Player;
import nro.models.services.EffectSkillService;
import nro.models.services.Service;

public class PKCommeson extends PVP {

    public PKCommeson(Player p1, Player p2) {
        super(TYPE_PVP.THACH_DAU, p1, p2);
    }

    @Override
    public void finish() {

    }

    @Override
    public void dispose() {
        super.dispose();
    }

    @Override
    public void update() {
    }

    @Override
    public void sendResult(Player plLose, TYPE_LOSE_PVP typeLose) {
        if (typeLose == TYPE_LOSE_PVP.RUNS_AWAY) {
            Player pl = p1.isPl() ? p1 : p2;
            EffectSkillService.gI().removePKCommeson(pl);
            if (pl.equals(plLose)) {
                Service.gI().sendThongBao(pl, "Bạn đã thất bại, ngày mai hãy thử sức tiếp");
            }
        }
    }

    @Override
    public void reward(Player plWin) {

    }

}
