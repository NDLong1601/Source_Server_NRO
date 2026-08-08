package nro.models.event_list;

import nro.models.boss.BossID;
import nro.models.event.Event;

public class LunarNewYear extends Event {

    @Override
    public void npc() {
        createNpc(0, 49, 850, 432);
    }

    @Override
    public void boss() {
        createBoss(BossID.LAN_CON, 10);
    }
}
