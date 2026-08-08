package nro.models.boss.yardrat;


import nro.models.boss.BossID;
import nro.models.boss.BossesData;
import static nro.models.consts.BossType.YARDART;

public class CHIENBINH4 extends Yardart {

    public CHIENBINH4() throws Exception {
        super(YARDART, BossID.CHIEN_BINH_4, BossesData.CHIEN_BINH_4);
    }

    @Override
    protected void init() {
        x = 993;
        x2 = 1063;
        y = 456;
        y2 = 456;
        range = 1000;
        range2 = 150;
        timeHoiHP = 20000;
        rewardRatio = 3;
    }

}
