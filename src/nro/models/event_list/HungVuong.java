package nro.models.event_list;


import nro.models.boss.BossID;
import nro.models.event.Event;

public class HungVuong extends Event {

    @Override
    public void npc() {
        createNpc(183, 66, 650, 480);
    }

    @Override
    public void boss() {
        createBoss(BossID.THUY_TINH, 10);
    }
}
