package nro.models.boss.yardrat;


import nro.models.boss.BossID;
import nro.models.boss.BossesData;
import static nro.models.consts.BossType.YARDART;

public class TAPSU3 extends Yardart {

    public TAPSU3() throws Exception {
        super(YARDART, BossID.TAP_SU_3, BossesData.TAP_SU_3);
    }

    @Override
    protected void init() {
        x = 787;
        x2 = 857;
        y = 456;
        y2 = 408;
        range = 1000;
        range2 = 150;
        timeHoiHP = 30000;
        rewardRatio = 5;
    }
}
