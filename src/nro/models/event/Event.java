package nro.models.event;

import java.util.HashSet;
import java.util.Set;
import nro.models.admin.AdminEventConfigService;
import nro.models.boss.Boss_Manager.BossManager;
import nro.models.ievent.IEvent;
import nro.models.map.service.MapService;
import nro.models.npc.NpcFactory;
import nro.models.utils.Logger;

public abstract class Event implements IEvent {

    private String eventCode = "default";
    private final Set<Integer> declaredBosses = new HashSet<>();

    public void init(String code) {
        this.eventCode = code;
        init();
        AdminEventConfigService.gI().spawnAdditionalBosses(eventCode, declaredBosses);
    }

    @Override
    public void init() {
        npc();
        boss();
        itemMap();
        itemBoss();
    }

    @Override
    public void npc() {

    }

    @Override
    public void createNpc(int mapId, int npcId, int x, int y) {
        MapService.gI().getMapById(mapId).npcs.add(NpcFactory.createNPC(mapId, 1, x, y, npcId));
    }

    @Override
    public void boss() {

    }

    @Override
    public void createBoss(int bossId, int... total) {
        int len = 1;
        if (total.length > 0) {
            len = total[0];
        }
        declaredBosses.add(bossId);
        len = AdminEventConfigService.gI().getBossQuantity(eventCode, bossId, len);
        try {
            for (int i = 0; i < len; i++) {
                BossManager.gI().createBoss(bossId);
            }
        } catch (Exception e) {
            Logger.error(e + "\n");
        }
    }

    @Override
    public void itemMap() {

    }

    @Override
    public void itemBoss() {

    }
}
