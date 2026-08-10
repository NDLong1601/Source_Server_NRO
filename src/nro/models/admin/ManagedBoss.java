package nro.models.admin;

import java.util.List;
import nro.models.admin.AdminSpawnConfigService.BossConfig;
import nro.models.boss.Boss;
import nro.models.boss.BossData;
import nro.models.consts.BossStatus;
import nro.models.map.ItemMap;
import nro.models.map.Zone;
import nro.models.map.service.ChangeMapService;
import nro.models.map.service.MapService;
import nro.models.player.Player;
import nro.models.server.ServerNotify;
import nro.models.services.Service;
import nro.models.skill.Skill;
import nro.models.utils.Util;

/** A database-defined boss whose lifecycle is controlled by the admin schedule. */
public final class ManagedBoss extends Boss {

    private static final int ADMIN_BOSS_ID_BASE = -1_000_000;
    private final BossConfig config;
    private boolean wasAllowed;

    public ManagedBoss(BossConfig config) throws Exception {
        super(ADMIN_BOSS_ID_BASE - config.id, createData(config));
        this.config = config;
        this.isNotifyDisabled = !config.announce;
        this.wasAllowed = false;
    }

    private static BossData createData(BossConfig config) {
        int attackSkill = switch (config.gender) {
            case 1 -> Skill.DEMON;
            case 2 -> Skill.GALICK;
            default -> Skill.DRAGON;
        };
        return new BossData(
                config.name,
                config.gender,
                new short[]{config.head, config.body, config.leg, -1, -1, -1},
                config.damage,
                new int[]{config.hp},
                new int[]{config.mapId},
                new int[][]{{attackSkill, 7, 1000}},
                new String[]{},
                new String[]{"|-1|Ta đã xuất hiện!"},
                new String[]{"|-1|Hẹn gặp lại..."},
                Math.max(1, config.intervalMinutes * 60)
        );
    }

    @Override
    public void update() {
        boolean allowed = config.isActiveNow();
        if (!allowed) {
            if (this.zone != null) {
                ChangeMapService.gI().exitMap(this);
            }
            this.zone = null;
            this.lastZone = null;
            this.changeStatus(BossStatus.REST);
            this.wasAllowed = false;
            return;
        }
        if (!wasAllowed) {
            this.lastTimeRest = 0;
            this.wasAllowed = true;
        }
        super.update();
    }

    @Override
    public void rest() {
        long delay = config.useInterval ? config.intervalMinutes * 60_000L : 3_000L;
        if (this.lastTimeRest == 0 || Util.canDoWithTime(this.lastTimeRest, delay)) {
            this.changeStatus(BossStatus.RESPAWN);
        }
    }

    @Override
    public Zone getMapJoin() {
        nro.models.map.Map map = MapService.gI().getMapById(config.mapId);
        if (map == null || map.zones.isEmpty()) {
            return null;
        }
        if (config.zoneId >= 0 && config.zoneId < map.zones.size()) {
            return map.zones.get(config.zoneId);
        }
        return map.zones.get(Util.nextInt(map.zones.size()));
    }

    @Override
    public void joinMap() {
        Zone target = getMapJoin();
        if (target == null) {
            this.lastTimeRest = System.currentTimeMillis();
            this.changeStatus(BossStatus.REST);
            return;
        }
        int x = config.spawnX >= 0 ? config.spawnX
                : (target.map.mapWidth > 100 ? Util.nextInt(100, target.map.mapWidth - 100) : Util.nextInt(100));
        int y = config.spawnY >= 0 ? config.spawnY : target.map.yPhysicInTop(x, 100);
        ChangeMapService.gI().changeMap(this, target, x, y);
        Service.gI().sendFlagBag(this);
        if (config.announce) {
            ServerNotify.gI().notify("BOSS " + this.name + " vừa xuất hiện tại " + target.map.mapName + " khu " + target.zoneId);
        }
        this.changeStatus(BossStatus.CHAT_S);
    }

    @Override
    public void reward(Player player) {
        super.reward(player);
        List<ItemMap> items = AdminSpawnConfigService.gI().createBossDrops(
                config.id, this.zone, player.id, this.location.x,
                this.zone.map.yPhysicInTop(this.location.x, this.location.y)
        );
        for (ItemMap item : items) {
            Service.gI().dropItemMap(this.zone, item);
        }
    }
}
