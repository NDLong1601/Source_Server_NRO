package nro.models.admin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import nro.models.boss.Boss;
import nro.models.boss.Boss_Manager.BossManager;
import nro.models.data.LocalManager;
import nro.models.data.LocalResultSet;
import nro.models.event.EventManager;
import nro.models.item.Item;
import nro.models.map.ItemMap;
import nro.models.map.Zone;
import nro.models.mob.Mob;
import nro.models.services.ItemService;
import nro.models.services.Service;
import nro.models.server.Client;
import nro.models.server.ServerManager;
import nro.models.utils.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

/** Runtime configuration for each seasonal event maintained by the admin tool. */
public final class AdminEventConfigService implements Runnable {

    private static final AdminEventConfigService INSTANCE = new AdminEventConfigService();

    private final Map<String, EventProfile> profiles = new HashMap<>();
    private final Map<String, Map<Integer, BossRule>> bosses = new HashMap<>();
    private final List<ItemRule> items = new ArrayList<>();

    public static AdminEventConfigService gI() {
        return INSTANCE;
    }

    private AdminEventConfigService() {
    }

    public void load() {
        profiles.clear();
        bosses.clear();
        items.clear();
        LocalResultSet rs = null;
        try {
            rs = LocalManager.executeQuery("SELECT event_code,drop_multiplier FROM admin_event_config");
            while (rs.next()) {
                String code = rs.getString("event_code");
                profiles.put(code, new EventProfile(
                        Math.max(0D, Double.parseDouble(rs.getString("drop_multiplier")))
                ));
            }
            close(rs);
            rs = LocalManager.executeQuery("SELECT event_code,boss_id,quantity,enabled,map_ids_json FROM admin_event_boss");
            while (rs.next()) {
                String code = rs.getString("event_code");
                BossRule rule = new BossRule(rs.getInt("boss_id"), Math.max(0, rs.getInt("quantity")),
                        rs.getBoolean("enabled"), parseIntArray(rs.getString("map_ids_json")));
                bosses.computeIfAbsent(code, ignored -> new HashMap<>()).put(rule.bossId, rule);
            }
            close(rs);
            rs = LocalManager.executeQuery("SELECT event_code,source_type,source_id,item_id,quantity_min,quantity_max,drop_rate,enabled,options_json FROM admin_event_item ORDER BY id");
            while (rs.next()) {
                items.add(new ItemRule(
                        rs.getString("event_code"), rs.getString("source_type"), rs.getInt("source_id"),
                        rs.getInt("item_id"), Math.max(1, rs.getInt("quantity_min")),
                        Math.max(1, rs.getInt("quantity_max")),
                        Math.max(0D, Double.parseDouble(rs.getString("drop_rate"))),
                        rs.getBoolean("enabled"), parseOptions(rs.getString("options_json"))
                ));
            }
            Logger.success("Loaded event config (profile=" + profiles.size() + ", boss="
                    + bosses.values().stream().mapToInt(Map::size).sum() + ", item=" + items.size() + ")\n");
        } catch (Exception e) {
            Logger.logException(AdminEventConfigService.class, e,
                    "Không thể tải cấu hình sự kiện admin; server tiếp tục dùng cấu hình mặc định");
        } finally {
            close(rs);
        }
    }

    public int getBossQuantity(String eventCode, int bossId, int defaultQuantity) {
        Map<Integer, BossRule> eventBosses = bosses.get(eventCode);
        if (eventBosses == null || !eventBosses.containsKey(bossId)) {
            return defaultQuantity;
        }
        BossRule rule = eventBosses.get(bossId);
        return rule.enabled ? rule.quantity : 0;
    }

    public void spawnAdditionalBosses(String eventCode, Set<Integer> declaredBosses) {
        Map<Integer, BossRule> eventBosses = bosses.get(eventCode);
        if (eventBosses == null) {
            return;
        }
        Set<Integer> declared = declaredBosses == null ? new HashSet<>() : declaredBosses;
        for (BossRule rule : eventBosses.values()) {
            if (!rule.enabled || declared.contains(rule.bossId)) {
                continue;
            }
            for (int i = 0; i < rule.quantity; i++) {
                BossManager.gI().createBoss(rule.bossId);
            }
        }
    }

    public int getEventBossMapId(Boss boss, int defaultMapId) {
        if (boss == null) {
            return defaultMapId;
        }
        for (Map.Entry<String, Map<Integer, BossRule>> eventEntry : bosses.entrySet()) {
            if (!EventManager.gI().isActive(eventEntry.getKey())) {
                continue;
            }
            for (BossRule rule : eventEntry.getValue().values()) {
                if (rule.enabled && rule.mapIds.length > 0 && matchesBossId(rule.bossId, boss)) {
                    return rule.mapIds[ThreadLocalRandom.current().nextInt(rule.mapIds.length)];
                }
            }
        }
        return defaultMapId;
    }

    public List<ItemMap> createMobDrops(Mob mob, long playerId, int x, int y) {
        return createDrops("mob", mob.tempId, null, mob.zone, playerId, x, y);
    }

    public List<ItemMap> createBossDrops(Boss boss, long playerId, int x, int y) {
        return createDrops("boss", (int) boss.id, boss, boss.zone, playerId, x, y);
    }

    private List<ItemMap> createDrops(String sourceType, int sourceId, Boss boss, Zone zone,
            long playerId, int x, int y) {
        List<ItemMap> result = new ArrayList<>();
        if (zone == null) {
            return result;
        }
        for (ItemRule rule : items) {
            if (!rule.enabled || !sourceType.equals(rule.sourceType)
                    || !EventManager.gI().isActive(rule.eventCode)
                    || (rule.sourceId != -1 && !matchesSource(rule, sourceId, boss))) {
                continue;
            }
            EventProfile profile = profiles.get(rule.eventCode);
            double multiplier = profile == null ? 1D : profile.dropMultiplier;
            double effectiveRate = Math.min(100D, rule.rate * multiplier);
            if (ThreadLocalRandom.current().nextDouble(100D) >= effectiveRate
                    || ItemService.gI().getTemplate(rule.itemId) == null) {
                continue;
            }
            int min = Math.min(rule.quantityMin, rule.quantityMax);
            int max = Math.max(rule.quantityMin, rule.quantityMax);
            int quantity = min == max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
            ItemMap item = new ItemMap(zone, rule.itemId, quantity,
                    x + ThreadLocalRandom.current().nextInt(-15, 16), y, playerId);
            for (OptionValue option : rule.options) {
                int param = option.paramMin == option.paramMax ? option.paramMin
                        : ThreadLocalRandom.current().nextInt(option.paramMin, option.paramMax + 1);
                item.options.add(new Item.ItemOption(option.id, param));
            }
            result.add(item);
        }
        return result;
    }

    private boolean matchesSource(ItemRule rule, int runtimeId, Boss boss) {
        if (rule.sourceId == runtimeId) {
            return true;
        }
        return boss != null && matchesBossId(rule.sourceId, boss);
    }

    private boolean matchesBossId(int configuredId, Boss boss) {
        return configuredId == (int) boss.id || configuredId == -371
                && "LanCon".equals(boss.getClass().getSimpleName());
    }

    @Override
    public void run() {
        while (ServerManager.isRunning) {
            try {
                processPointGrants();
            } catch (Exception e) {
                Logger.logException(AdminEventConfigService.class, e, "Không thể xử lý hàng đợi cộng điểm sự kiện");
            }
            try {
                Thread.sleep(1_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void processPointGrants() {
        List<PointGrant> grants = new ArrayList<>();
        LocalResultSet rs = null;
        try {
            rs = LocalManager.executeQuery("SELECT id,player_id,points FROM admin_event_point_grant WHERE status='pending' ORDER BY id LIMIT 50");
            while (rs.next()) {
                grants.add(new PointGrant(rs.getLong("id"), rs.getLong("player_id"), rs.getInt("points")));
            }
        } catch (Exception e) {
            return;
        } finally {
            close(rs);
        }
        for (PointGrant grant : grants) {
            try {
                if (LocalManager.executeUpdate("UPDATE admin_event_point_grant SET status='processing',result_message='Đang cộng điểm' WHERE id=? AND status='pending'", grant.id) == 0) {
                    continue;
                }
                nro.models.player.Player online = Client.gI().getPlayer(grant.playerId);
                if (online != null) {
                    online.event.grantEventPoint(grant.points);
                    LocalManager.executeUpdate("UPDATE player SET event_point=? WHERE id=?",
                            online.event.getEventPoint(), grant.playerId);
                    Service.gI().sendThongBao(online, "Bạn được Admin cộng " + grant.points + " điểm sự kiện.");
                } else {
                    LocalManager.executeUpdate("UPDATE player SET event_point=event_point+? WHERE id=?",
                            grant.points, grant.playerId);
                }
                LocalManager.executeUpdate("UPDATE admin_event_point_grant SET status='processed',processed_at=NOW(),result_message='Đã cộng điểm' WHERE id=? AND status='processing'", grant.id);
            } catch (Exception e) {
                try {
                    LocalManager.executeUpdate("UPDATE admin_event_point_grant SET status='failed',processed_at=NOW(),result_message=? WHERE id=? AND status='processing'",
                            e.getMessage() == null ? "Lỗi không xác định" : e.getMessage(), grant.id);
                } catch (Exception logError) {
                    Logger.logException(AdminEventConfigService.class, logError, "Không thể cập nhật trạng thái cộng điểm lỗi");
                }
            }
        }
    }

    private static List<OptionValue> parseOptions(String json) {
        List<OptionValue> result = new ArrayList<>();
        Object parsed = JSONValue.parse(json == null ? "[]" : json);
        if (!(parsed instanceof JSONArray array)) {
            return result;
        }
        for (Object value : array) {
            if (!(value instanceof JSONObject option) || option.get("id") == null) {
                continue;
            }
            Object legacy = option.get("param");
            Object minValue = option.get("paramMin") == null ? legacy : option.get("paramMin");
            Object maxValue = option.get("paramMax") == null ? minValue : option.get("paramMax");
            if (minValue == null || maxValue == null) {
                continue;
            }
            int min = Integer.parseInt(minValue.toString());
            int max = Integer.parseInt(maxValue.toString());
            result.add(new OptionValue(Integer.parseInt(option.get("id").toString()), Math.min(min, max), Math.max(min, max)));
        }
        return result;
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

    private static void close(LocalResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (Exception ignored) {
            }
        }
    }

    private record EventProfile(double dropMultiplier) {
    }

    private record BossRule(int bossId, int quantity, boolean enabled, int[] mapIds) {
    }

    private record ItemRule(String eventCode, String sourceType, int sourceId, int itemId,
            int quantityMin, int quantityMax, double rate, boolean enabled, List<OptionValue> options) {
    }

    private record OptionValue(int id, int paramMin, int paramMax) {
    }

    private record PointGrant(long id, long playerId, int points) {
    }
}
