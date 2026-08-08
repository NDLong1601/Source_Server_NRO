package nro.models.boss.dai_hoi_vo_thuat;


import nro.models.boss.BossID;
import nro.models.boss.BossesData;
import static nro.models.consts.BossType.PHOBAN;
import nro.models.player.Player;

public class Pocolo extends The23rdMartialArtCongress {

    public Pocolo(Player player) throws Exception {
        super(PHOBAN, BossID.PO_CO_LO, BossesData.POCOLO);
        this.playerAtt = player;
    }
}
