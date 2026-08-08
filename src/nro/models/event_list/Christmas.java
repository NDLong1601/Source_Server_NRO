package nro.models.event_list;

import nro.models.boss.BossID;
import nro.models.event.Event;

public class Christmas extends Event {

    @Override
    public void boss() {
        createBoss(BossID.ONG_GIA_NOEL, 30);
    }
}
