package nro.models.event_list;


import nro.models.boss.BossID;
import nro.models.event.Event;

public class Default extends Event {

    @Override
    public void boss() {
        createBoss(BossID.BROLY, 30);
    }

}
