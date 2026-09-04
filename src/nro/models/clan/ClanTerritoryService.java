package nro.models.clan;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import nro.models.map.Zone;
import nro.models.map.service.ChangeMapService;
import nro.models.map.service.MapService;
import nro.models.player.Player;
import nro.models.services.Service;

/** Keeps map 153 instances private to a clan and releases idle instances. */
public final class ClanTerritoryService {

    public static final int CLAN_TERRITORY_MAP_ID = 153;
    public static final long IDLE_DISPOSE_MS = 10L * 60L * 1000L;
    private static final int FIRST_DYNAMIC_ZONE_ID = 10;
    private static final int MAX_ZONE_ID = 126; // packet zone id is a signed byte on legacy clients
    private static final ClanTerritoryService INSTANCE = new ClanTerritoryService();

    private final Map<Integer, Zone> territories = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "clan-territory-cleanup");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean started;

    private ClanTerritoryService() {
    }

    public static ClanTerritoryService gI() {
        return INSTANCE;
    }

    public synchronized void start() {
        if (started) return;
        started = true;
        cleanupExecutor.scheduleAtFixedRate(this::disposeIdleTerritories, 1L, 1L, TimeUnit.MINUTES);
    }

    public Zone getOrCreateTerritory(Player player) {
        if (player == null || player.clan == null) {
            return null;
        }
        start();
        int clanId = player.clan.id;
        Zone existing = territories.get(clanId);
        if (existing != null) {
            existing.territoryLastActiveAt = System.currentTimeMillis();
            return existing;
        }
        synchronized (this) {
            existing = territories.get(clanId);
            if (existing != null) return existing;
            nro.models.map.Map territoryMap = MapService.gI().getMapById(CLAN_TERRITORY_MAP_ID);
            if (territoryMap == null) return null;
            int zoneId = nextAvailableZoneId(territoryMap);
            if (zoneId < 0) {
                disposeIdleTerritories();
                zoneId = nextAvailableZoneId(territoryMap);
            }
            if (zoneId < 0) {
                Service.gI().sendThongBao(player, "Lãnh địa bang đang quá tải, vui lòng thử lại sau.");
                return null;
            }
            Zone territory = new Zone(territoryMap, zoneId, Math.max(10, territoryMap.zones.get(0).maxPlayer));
            territory.territoryClanId = clanId;
            territory.territoryLastActiveAt = System.currentTimeMillis();
            territoryMap.zones.add(territory);
            territories.put(clanId, territory);
            return territory;
        }
    }

    public void enterTerritory(Player player, int x, int y) {
        Zone territory = getOrCreateTerritory(player);
        if (territory != null) {
            ChangeMapService.gI().changeMap(player, territory, x, y);
            // The client needs the current tree level before its map-153 renderer
            // can draw the one physical tree at the shared fixed coordinate.
            ClanTreeService.gI().sendSnapshot(player);
        }
    }

    public boolean isTerritoryFor(Player player, Zone zone) {
        return player != null && zone != null && zone.map != null
                && zone.map.mapId == CLAN_TERRITORY_MAP_ID
                && zone.territoryClanId >= 0 && player.clan != null
                && player.clan.id == zone.territoryClanId;
    }

    public void markActive(Zone zone) {
        if (zone != null && zone.territoryClanId >= 0) {
            zone.territoryLastActiveAt = System.currentTimeMillis();
        }
    }

    /** Called from Zone's update loop, so removals from a clan take effect even mid-map. */
    public void enforceMembership(Zone zone) {
        if (zone == null || zone.territoryClanId < 0) return;
        int clanId = zone.territoryClanId;
        for (Player player : new ArrayList<>(zone.getPlayers())) {
            if (player != null && (player.clan == null || player.clan.id != clanId)) {
                ChangeMapService.gI().changeMapNonSpaceship(player, 5, 400, 336);
            }
        }
    }

    public synchronized void disposeIdleTerritories() {
        long now = System.currentTimeMillis();
        for (Map.Entry<Integer, Zone> entry : new ArrayList<>(territories.entrySet())) {
            Zone zone = entry.getValue();
            if (zone != null && zone.getNumOfPlayers() == 0 && now - zone.territoryLastActiveAt >= IDLE_DISPOSE_MS) {
                territories.remove(entry.getKey(), zone);
                zone.map.zones.remove(zone);
                zone.territoryClanId = -1;
            }
        }
    }

    private int nextAvailableZoneId(nro.models.map.Map map) {
        boolean[] used = new boolean[MAX_ZONE_ID + 1];
        for (Zone zone : map.zones) {
            if (zone != null && zone.zoneId >= FIRST_DYNAMIC_ZONE_ID && zone.zoneId <= MAX_ZONE_ID) {
                used[zone.zoneId] = true;
            }
        }
        for (int id = FIRST_DYNAMIC_ZONE_ID; id <= MAX_ZONE_ID; id++) {
            if (!used[id]) return id;
        }
        return -1;
    }
}
