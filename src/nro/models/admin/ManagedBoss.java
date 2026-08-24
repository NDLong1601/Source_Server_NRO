package nro.models.admin;

import java.util.List;
import nro.models.admin.AdminSpawnConfigService.AdminBossLevel;
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
    private long virtualHpRemaining;

    public ManagedBoss(BossConfig config) throws Exception {
        super(ADMIN_BOSS_ID_BASE - config.id, createData(config));
        this.config = config;
        this.isNotifyDisabled = !config.announce;
        this.wasAllowed = false;
        this.virtualHpRemaining = 0L;
    }

    private static int attackSkill(byte gender) {
        return switch (gender) {
            case 1 -> Skill.DEMON;
            case 2 -> Skill.GALICK;
            default -> Skill.DRAGON;
        };
    }

    private static int visibleHp(long hp) {
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, hp));
    }

    private static BossData[] createData(BossConfig config) {
        AdminBossLevel[] levels = config.levels == null || config.levels.length == 0
                ? new AdminBossLevel[]{new AdminBossLevel(config.name, config.gender, config.head,
                    config.body, config.leg, config.hp, config.damage, config.defense, config.dodge, config.crit)}
                : config.levels;
        BossData[] data = new BossData[levels.length];
        for (int i = 0; i < levels.length; i++) {
            AdminBossLevel level = levels[i];
            int[][] skills = config.skills.length == 0
                    ? new int[][]{{attackSkill(level.gender), 7, 1000}}
                    : config.skills;
            data[i] = new BossData(
                    level.name,
                    level.gender,
                    new short[]{level.head, level.body, level.leg, -1, -1, -1},
                    level.damage,
                    new int[]{visibleHp(level.hp)},
                    new int[]{config.mapId},
                    skills,
                    new String[]{},
                    new String[]{"|-1|Ta đã xuất hiện!"},
                    new String[]{"|-1|Hẹn gặp lại..."},
                    Math.max(1, config.intervalMinutes * 60)
            );
        }
        return data;
    }

    private AdminBossLevel currentLevelConfig() {
        if (config.levels == null || config.levels.length == 0) {
            return new AdminBossLevel(config.name, config.gender, config.head, config.body,
                    config.leg, config.hp, config.damage, config.defense, config.dodge, config.crit);
        }
        int index = Math.max(0, Math.min(this.currentLevel, config.levels.length - 1));
        return config.levels[index];
    }

    @Override
    public void initBase() {
        super.initBase();
        AdminBossLevel level = currentLevelConfig();
        this.virtualHpRemaining = Math.max(0L, level.hp - this.nPoint.hp);
        this.nPoint.defg = level.defense;
        this.nPoint.def = level.defense;
        this.nPoint.critg = level.crit;
        this.nPoint.crit = level.crit;
        this.nPoint.tlNeDon = (short) Math.min(1000, level.dodge * 10);
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
            this.currentLevel = -1;
            this.virtualHpRemaining = 0L;
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
        Zone target = this.currentLevel > 0 && this.lastZone != null ? this.lastZone : getMapJoin();
        if (target == null) {
            this.lastTimeRest = System.currentTimeMillis();
            this.changeStatus(BossStatus.REST);
            return;
        }
        boolean keepPhasePosition = this.currentLevel > 0 && this.lastZone != null;
        int x = keepPhasePosition ? this.location.x : config.spawnX >= 0 ? config.spawnX
                : (target.map.mapWidth > 100 ? Util.nextInt(100, target.map.mapWidth - 100) : Util.nextInt(100));
        int y = keepPhasePosition ? target.map.yPhysicInTop(x, this.location.y)
                : config.spawnY >= 0 ? config.spawnY : target.map.yPhysicInTop(x, 100);
        // ChangeMapService requires the boss to already reference its destination zone.
        this.zone = target;
        ChangeMapService.gI().changeMap(this, target, x, y);
        Service.gI().sendFlagBag(this);
        if (config.announce) {
            ServerNotify.gI().notify("BOSS " + this.name + " vừa xuất hiện tại " + target.map.mapName + " khu " + target.zoneId);
        }
        this.changeStatus(BossStatus.CHAT_S);
    }

    @Override
    public synchronized int injured(Player plAtt, long damage, boolean piercing, boolean isMobAttack) {
        if (this.isDie()) {
            return 0;
        }
        AdminBossLevel level = currentLevelConfig();
        if (!piercing && level.dodge > 0 && Util.isTrue(level.dodge, 100)) {
            this.chat("Xí hụt");
            return 0;
        }
        if (plAtt != null && plAtt.idNRNM != -1) {
            return 1;
        }
        long realDamage = Math.max(0L, damage);
        if (!piercing && level.defense > 0 && realDamage > 0) {
            realDamage = Math.max(1L, realDamage - level.defense);
        }
        if (realDamage <= 0) {
            return 0;
        }
        long visibleBefore = Math.max(0, this.nPoint.hp);
        long visibleAfter = visibleBefore - realDamage;
        if (visibleAfter > 0) {
            this.nPoint.hp = (int) Math.min(Integer.MAX_VALUE, visibleAfter);
            return (int) Math.min(Integer.MAX_VALUE, realDamage);
        }
        long overflowDamage = Math.max(0L, realDamage - visibleBefore);
        if (this.virtualHpRemaining > 0) {
            this.virtualHpRemaining = Math.max(0L, this.virtualHpRemaining - overflowDamage);
            if (this.virtualHpRemaining > 0) {
                int nextVisibleHp = visibleHp(this.virtualHpRemaining);
                this.virtualHpRemaining -= nextVisibleHp;
                this.nPoint.hpg = nextVisibleHp;
                this.nPoint.hpMax = nextVisibleHp;
                this.nPoint.hp = nextVisibleHp;
                return (int) Math.min(Integer.MAX_VALUE, realDamage);
            }
        }
        this.nPoint.hp = 0;
        this.setDie(plAtt);
        die(plAtt);
        return (int) Math.min(Integer.MAX_VALUE, realDamage);
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
