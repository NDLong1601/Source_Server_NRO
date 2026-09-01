package nro.models.boss.dai_hoi_vo_thuat;


import nro.models.boss.BossID;
import nro.models.boss.BossesData;
import static nro.models.consts.BossType.PHOBAN;
import nro.models.consts.ConstTaskBadges;
import nro.models.player.Player;
import nro.models.task.BadgesTaskService;

public class Xinbato extends The23rdMartialArtCongress {

    public Xinbato(Player player) throws Exception {
        super(PHOBAN, BossID.XINBATO, BossesData.XINBATO);
        this.playerAtt = player;
    }

    @Override
    public void die(Player plKill) {
        if (plKill != null) {
            BadgesTaskService.updateCountBagesTask(plKill, ConstTaskBadges.NUOC_ANH_BAO, 1);
        }
        super.die(plKill);
    }
}
