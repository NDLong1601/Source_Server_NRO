package nro.models.admin;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import nro.models.data.LocalManager;
import nro.models.data.LocalResultSet;
import nro.models.item.Item;
import nro.models.map.ItemMap;
import nro.models.map.Zone;
import nro.models.mob.Mob;
import nro.models.services.ItemService;
import nro.models.utils.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

/**
 * Loads the optional Boss/Mob configuration maintained by admin_data_menu.hta.
 * Missing tables are treated as "feature not configured" so old databases still boot.
 */
public final class AdminSpawnConfigService {

    private static final AdminSpawnConfigService INSTANCE = new AdminSpawnConfigService();
    private static final long DEFAULT_MOB_RESPAWN_MILLIS = 3_000L;

    private final Map<Integer, MobConfig> mobConfigs = new HashMap<>();
    private final Map<Integer, BossOverride> bossOverrides = new HashMap<>();
    private final Map<String, List<DropConfig>> drops = new HashMap<>();

    public static AdminSpawnConfigService gI() {
        return INSTANCE;
    }

    private AdminSpawnConfigService() {
    }

    public void load() {
        mobConfigs.clear();
        bossOverrides.clear();
        drops.clear();
        LocalResultSet rs = null;
        try {
            rs = LocalManager.executeQuery("SELECT boss_id,enabled,skills_json,use_time_range,"
                    + "TIME_FORMAT(time_start,'%H:%i') AS time_start,TIME_FORMAT(time_end,'%H:%i') AS time_end,"
                    + "use_interval,interval_minutes,map_id,map_ids_json FROM admin_boss_override");
            while (rs.next()) {
                int legacyMapId = rs.getInt("map_id");
                int[] mapIds = parseIntArray(rs.getString("map_ids_json"));
                if (mapIds.length == 0 && legacyMapId >= 0) {
                    mapIds = new int[]{legacyMapId};
                }
                bossOverrides.put(rs.getInt("boss_id"), new BossOverride(
                        rs.getBoolean("enabled"), parseSkillMatrix(rs.getString("skills_json")),
                        rs.getBoolean("use_time_range"), parseTime(rs.getString("time_start")),
                        parseTime(rs.getString("time_end")), rs.getBoolean("use_interval"),
                        Math.max(1, rs.getInt("interval_minutes")), mapIds
                ));
            }
            close(rs);
            rs = LocalManager.executeQuery("SELECT id,mob_template_id,enabled,use_time_range,"
                    + "TIME_FORMAT(time_start,'%H:%i') AS time_start,TIME_FORMAT(time_end,'%H:%i') AS time_end,"
                    + "use_interval,interval_minutes FROM admin_mob_config");
            while (rs.next()) {
                MobConfig config = new MobConfig(
                        rs.getInt("id"), rs.getInt("mob_template_id"), rs.getBoolean("enabled"),
                        rs.getBoolean("use_time_range"), parseTime(rs.getString("time_start")),
                        parseTime(rs.getString("time_end")), rs.getBoolean("use_interval"),
                        Math.max(1, rs.getInt("interval_minutes"))
                );
                mobConfigs.put(config.templateId, config);
            }
            close(rs);
            rs = LocalManager.executeQuery("SELECT owner_type,owner_id,item_id,quantity_min,quantity_max,drop_rate,options_json FROM admin_spawn_drop ORDER BY id");
            while (rs.next()) {
                DropConfig drop = new DropConfig(
                        rs.getInt("item_id"), Math.max(1, rs.getInt("quantity_min")),
                        Math.max(1, rs.getInt("quantity_max")), Double.parseDouble(rs.getString("drop_rate")),
                        parseUseDefaultOptions(rs.getString("options_json")), parseOptions(rs.getString("options_json"))
                );
                drops.computeIfAbsent(key(rs.getString("owner_type"), rs.getInt("owner_id")), ignored -> new ArrayList<>()).add(drop);
            }
            close(rs);
            rs = LocalManager.executeQuery("SELECT id,name,enabled,use_time_range,"
                    + "TIME_FORMAT(time_start,'%H:%i') AS time_start,TIME_FORMAT(time_end,'%H:%i') AS time_end,"
                    + "use_interval,interval_minutes,map_id,zone_id,spawn_x,spawn_y,gender,head,body,leg,hp,damage,announce "
                    + "FROM admin_boss_config ORDER BY id");
            int bossCount = 0;
            while (rs.next()) {
                BossConfig config = new BossConfig(
                        rs.getInt("id"), rs.getString("name"), rs.getBoolean("enabled"),
                        rs.getBoolean("use_time_range"), parseTime(rs.getString("time_start")),
                        parseTime(rs.getString("time_end")), rs.getBoolean("use_interval"),
                        Math.max(1, rs.getInt("interval_minutes")), rs.getInt("map_id"),
                        rs.getInt("zone_id"), rs.getInt("spawn_x"), rs.getInt("spawn_y"),
                        rs.getByte("gender"), rs.getShort("head"), rs.getShort("body"),
                        rs.getShort("leg"), Math.max(1, rs.getInt("hp")),
                        Math.max(1, rs.getInt("damage")), rs.getBoolean("announce")
                );
                new ManagedBoss(config);
                bossCount++;
            }
            Logger.success("Loaded admin spawn config (custom boss=" + bossCount + ", server boss override="
                    + bossOverrides.size() + ", mob=" + mobConfigs.size() + ")\n");
        } catch (Exception e) {
            Logger.logException(AdminSpawnConfigService.class, e, "Không thể tải cấu hình Boss/Mob admin; server tiếp tục dùng cấu hình mặc định");
        } finally {
            close(rs);
        }
    }

    public boolean prepareMobUpdate(Mob mob) {
        MobConfig config = mobConfigs.get(mob.tempId);
        if (config == null) {
            return true;
        }
        if (!config.isActiveNow()) {
            if (!mob.isDie()) {
                mob.startDie();
            }
            return false;
        }
        return true;
    }

    public boolean isServerBossEnabled(int bossId) {
        BossOverride override = bossOverrides.get(bossId);
        return override == null || override.enabled;
    }

    public int[][] getServerBossSkills(int bossId, int[][] defaultSkills) {
        BossOverride override = bossOverrides.get(bossId);
        return override == null || override.skills.length == 0 ? defaultSkills : override.skills;
    }

    public boolean isServerBossWithinSchedule(int bossId) {
        BossOverride override = bossOverrides.get(bossId);
        return override == null || override.isActiveNow();
    }

    public long getServerBossRestMillis(int bossId, long defaultMillis) {
        BossOverride override = bossOverrides.get(bossId);
        return override == null || !override.useInterval ? defaultMillis : override.intervalMinutes * 60_000L;
    }

    public int getServerBossMapId(int bossId, int defaultMapId) {
        BossOverride override = bossOverrides.get(bossId);
        return override == null || override.mapIds.length == 0
                ? defaultMapId
                : override.mapIds[ThreadLocalRandom.current().nextInt(override.mapIds.length)];
    }

    public List<ItemMap> createServerBossDrops(int bossId, Zone zone, long playerId, int x, int y) {
        return createDrops("serverboss", bossId, zone, playerId, x, y);
    }

    public long getMobRespawnMillis(int templateId) {
        MobConfig config = mobConfigs.get(templateId);
        if (config == null || !config.useInterval) {
            return DEFAULT_MOB_RESPAWN_MILLIS;
        }
        return config.intervalMinutes * 60_000L;
    }

    public List<ItemMap> createMobDrops(Mob mob, long playerId, int x, int y) {
        MobConfig config = mobConfigs.get(mob.tempId);
        if (config == null || !config.isActiveNow()) {
            return new ArrayList<>();
        }
        return createDrops("mob", config.id, mob.zone, playerId, x, y);
    }

    public List<ItemMap> createBossDrops(int configId, Zone zone, long playerId, int x, int y) {
        return createDrops("boss", configId, zone, playerId, x, y);
    }

    private List<ItemMap> createDrops(String ownerType, int ownerId, Zone zone, long playerId, int x, int y) {
        List<ItemMap> result = new ArrayList<>();
        if (zone == null) {
            return result;
        }
        List<DropConfig> configured = drops.get(key(ownerType, ownerId));
        if (configured == null) {
            return result;
        }
        for (DropConfig drop : configured) {
            if (ThreadLocalRandom.current().nextDouble(100D) >= drop.rate
                    || ItemService.gI().getTemplate(drop.itemId) == null) {
                continue;
            }
            int quantity = drop.quantityMin == drop.quantityMax
                    ? drop.quantityMin
                    : ThreadLocalRandom.current().nextInt(drop.quantityMin, drop.quantityMax + 1);
            ItemMap itemMap = new ItemMap(zone, drop.itemId, quantity, x + ThreadLocalRandom.current().nextInt(-15, 16), y, playerId);
            List<Item.ItemOption> extraOptions = new ArrayList<>();
            for (OptionValue option : drop.options) {
                int param = option.paramMin == option.paramMax
                        ? option.paramMin
                        : ThreadLocalRandom.current().nextInt(option.paramMin, option.paramMax + 1);
                extraOptions.add(new Item.ItemOption(option.id, param));
            }
            itemMap.options.addAll(ItemService.gI().mergeItemOptions((short) drop.itemId, drop.useDefaultOptions, extraOptions));
            result.add(itemMap);
        }
        return result;
    }

    private static String key(String ownerType, int ownerId) {
        return ownerType + ':' + ownerId;
    }

    private static LocalTime parseTime(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalTime.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<OptionValue> parseOptions(String json) {
        List<OptionValue> result = new ArrayList<>();
        Object parsed = JSONValue.parse(json == null ? "[]" : json);
        JSONArray array = parsed instanceof JSONObject object && object.get("options") instanceof JSONArray nested
                ? nested : parsed instanceof JSONArray direct ? direct : null;
        if (array == null) {
            return result;
        }
        for (Object value : array) {
            if (value instanceof JSONObject option && option.get("id") != null) {
                Object legacyParam = option.get("param");
                Object minValue = option.get("paramMin") == null ? legacyParam : option.get("paramMin");
                Object maxValue = option.get("paramMax") == null ? minValue : option.get("paramMax");
                if (minValue == null || maxValue == null) {
                    continue;
                }
                int min = Integer.parseInt(minValue.toString());
                int max = Integer.parseInt(maxValue.toString());
                if (min > max) {
                    int swap = min;
                    min = max;
                    max = swap;
                }
                result.add(new OptionValue(Integer.parseInt(option.get("id").toString()), min, max));
            }
        }
        return result;
    }

    private static boolean parseUseDefaultOptions(String json) {
        Object parsed = JSONValue.parse(json == null ? "[]" : json);
        if (parsed instanceof JSONObject object) {
            Object flag = object.get("useDefaultOptions");
            return flag != null && ("1".equals(flag.toString()) || "true".equalsIgnoreCase(flag.toString()));
        }
        return false;
    }

    private static int[] parseIntArray(String json) {
        Object parsed = JSONValue.parse(json == null ? "[]" : json);
        if (!(parsed instanceof JSONArray array)) {
            return new int[0];
        }
        int[] result = new int[array.size()];
        int count = 0;
        for (Object value : array) {
            if (value != null) {
                result[count++] = Integer.parseInt(value.toString());
            }
        }
        if (count == result.length) {
            return result;
        }
        int[] compact = new int[count];
        System.arraycopy(result, 0, compact, 0, count);
        return compact;
    }

    private static int[][] parseSkillMatrix(String json) {
        Object parsed = JSONValue.parse(json == null ? "[]" : json);
        if (!(parsed instanceof JSONArray array)) {
            return new int[0][];
        }
        List<int[]> result = new ArrayList<>();
        for (Object value : array) {
            if (value instanceof JSONObject skill && skill.get("id") != null
                    && skill.get("level") != null && skill.get("cooldown") != null) {
                result.add(new int[]{
                    Integer.parseInt(skill.get("id").toString()),
                    Integer.parseInt(skill.get("level").toString()),
                    Integer.parseInt(skill.get("cooldown").toString())
                });
            }
        }
        return result.toArray(new int[result.size()][]);
    }

    private static void close(LocalResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (Exception ignored) {
            }
        }
    }

    public static class ScheduleConfig {
        public final int id;
        public final boolean enabled;
        public final boolean useTimeRange;
        public final LocalTime timeStart;
        public final LocalTime timeEnd;
        public final boolean useInterval;
        public final int intervalMinutes;

        ScheduleConfig(int id, boolean enabled, boolean useTimeRange, LocalTime timeStart,
                LocalTime timeEnd, boolean useInterval, int intervalMinutes) {
            this.id = id;
            this.enabled = enabled;
            this.useTimeRange = useTimeRange;
            this.timeStart = timeStart;
            this.timeEnd = timeEnd;
            this.useInterval = useInterval;
            this.intervalMinutes = intervalMinutes;
        }

        public boolean isActiveNow() {
            if (!enabled) {
                return false;
            }
            if (!useTimeRange || timeStart == null || timeEnd == null || timeStart.equals(timeEnd)) {
                return true;
            }
            LocalTime now = LocalTime.now();
            if (timeStart.isBefore(timeEnd)) {
                return !now.isBefore(timeStart) && now.isBefore(timeEnd);
            }
            return !now.isBefore(timeStart) || now.isBefore(timeEnd);
        }
    }

    private static final class MobConfig extends ScheduleConfig {
        private final int templateId;

        private MobConfig(int id, int templateId, boolean enabled, boolean useTimeRange,
                LocalTime timeStart, LocalTime timeEnd, boolean useInterval, int intervalMinutes) {
            super(id, enabled, useTimeRange, timeStart, timeEnd, useInterval, intervalMinutes);
            this.templateId = templateId;
        }
    }

    public static final class BossConfig extends ScheduleConfig {
        public final String name;
        public final int mapId;
        public final int zoneId;
        public final int spawnX;
        public final int spawnY;
        public final byte gender;
        public final short head;
        public final short body;
        public final short leg;
        public final int hp;
        public final int damage;
        public final boolean announce;

        private BossConfig(int id, String name, boolean enabled, boolean useTimeRange,
                LocalTime timeStart, LocalTime timeEnd, boolean useInterval, int intervalMinutes,
                int mapId, int zoneId, int spawnX, int spawnY, byte gender, short head,
                short body, short leg, int hp, int damage, boolean announce) {
            super(id, enabled, useTimeRange, timeStart, timeEnd, useInterval, intervalMinutes);
            this.name = name;
            this.mapId = mapId;
            this.zoneId = zoneId;
            this.spawnX = spawnX;
            this.spawnY = spawnY;
            this.gender = gender;
            this.head = head;
            this.body = body;
            this.leg = leg;
            this.hp = hp;
            this.damage = damage;
            this.announce = announce;
        }
    }

    private record OptionValue(int id, int paramMin, int paramMax) {
    }

    private record DropConfig(int itemId, int quantityMin, int quantityMax, double rate,
            boolean useDefaultOptions, List<OptionValue> options) {
    }

    private record BossOverride(boolean enabled, int[][] skills, boolean useTimeRange,
            LocalTime timeStart, LocalTime timeEnd, boolean useInterval, int intervalMinutes, int[] mapIds) {

        private boolean isActiveNow() {
            if (!enabled) {
                return false;
            }
            if (!useTimeRange || timeStart == null || timeEnd == null || timeStart.equals(timeEnd)) {
                return true;
            }
            LocalTime now = LocalTime.now();
            return timeStart.isBefore(timeEnd)
                    ? !now.isBefore(timeStart) && now.isBefore(timeEnd)
                    : !now.isBefore(timeStart) || now.isBefore(timeEnd);
        }
    }
}
