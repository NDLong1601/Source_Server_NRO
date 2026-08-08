package nro.models.boss.yardrat;


import nro.models.boss.BossID;
import nro.models.boss.BossesData;
import static nro.models.consts.BossType.YARDART;

public class TANBINH0 extends Yardart {

    public TANBINH0() throws Exception {
        super(YARDART, BossID.TAN_BINH_0, BossesData.TAN_BINH_0);
    }

    @Override
    protected void init() {
        x = 170;
        x2 = 240;
        y = 456;
        y2 = 456;
        range = 1000;
        range2 = 150;
        timeHoiHP = 25000;
        rewardRatio = 4;
    }
}
