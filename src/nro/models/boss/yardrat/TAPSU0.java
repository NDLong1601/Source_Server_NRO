package nro.models.boss.yardrat;

import nro.models.boss.BossID;
import nro.models.boss.BossesData;
import static nro.models.consts.BossType.YARDART;

public class TAPSU0 extends Yardart {

    public TAPSU0() throws Exception {
        super(YARDART, BossID.TAP_SU_0, BossesData.TAP_SU_0);
    }

    @Override
    protected void init() {
        x = 170;
        x2 = 240;
        y = 456;
        y2 = 456;
        range = 1000;
        range2 = 150;
        timeHoiHP = 30000;
        rewardRatio = 5;
    }
}
