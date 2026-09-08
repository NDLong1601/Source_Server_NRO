package nro.models.server;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import nro.models.consts.ConstMap;
import nro.models.map.service.MapService;
import nro.models.player_system.Template.MapTemplate;
import nro.models.utils.Logger;

/** Builds a complete world off-side. Publication belongs to WorldRegistry. */
public final class WorldFactory {

    private static final ThreadLocal<nro.models.map.Map> BUILDING_MAP = new ThreadLocal<>();

    public List<nro.models.map.Map> create(MapTemplate[] templates) {
        int[][] tileTypeTop = readTileIndexTileType(ConstMap.TILE_TOP);
        List<nro.models.map.Map> worlds = new ArrayList<>(templates.length);
        boolean[] seenMapIds = new boolean[255];
        for (int templateIndex = 0; templateIndex < templates.length; templateIndex++) {
            MapTemplate template = templates[templateIndex];
            if (template == null) {
                throw new IllegalArgumentException("map_template contains null at index " + templateIndex);
            }
            if (template.id < 0 || template.id >= seenMapIds.length) {
                throw new IllegalArgumentException("map_template ID is outside packet range: " + template.id);
            }
            if (seenMapIds[template.id]) {
                throw new IllegalArgumentException("duplicate map_template ID: " + template.id);
            }
            seenMapIds[template.id] = true;
            int[][] tileMap = readTileMap(template.id);
            int tileIndex = template.tileId - 1;
            if (tileIndex < 0 || tileIndex >= tileTypeTop.length || tileTypeTop[tileIndex] == null) {
                throw new IllegalStateException("Missing top-tile metadata for map " + template.id);
            }
            nro.models.map.Map map = new nro.models.map.Map(
                    template.id, template.name, template.planetId, template.tileId,
                    template.bgId, template.bgType, template.type, tileMap, tileTypeTop[tileIndex],
                    template.zones, template.maxPlayerPerZone, template.wayPoints);
            map.initMob(template.mobTemp, template.mobLevel, template.mobHp, template.mobX, template.mobY);
            BUILDING_MAP.set(map);
            try {
                map.initNpc(template.npcId, template.npcX, template.npcY);
            } finally {
                BUILDING_MAP.remove();
            }
            worlds.add(map);
        }
        return List.copyOf(worlds);
    }

    public static nro.models.map.Map currentBuildingMap(int mapId) {
        nro.models.map.Map map = BUILDING_MAP.get();
        return map != null && map.mapId == mapId ? map : null;
    }

    private int[][] readTileIndexTileType(int tileTypeFocus) {
        try (DataInputStream input = new DataInputStream(new FileInputStream("data/map/tile_set_info"))) {
            int numTileMap = input.readUnsignedByte();
            int[][] result = new int[numTileMap][];
            for (int i = 0; i < numTileMap; i++) {
                int typeCount = input.readUnsignedByte();
                for (int j = 0; j < typeCount; j++) {
                    int tileType = input.readInt();
                    int indexCount = input.readUnsignedByte();
                    if (tileType == tileTypeFocus) result[i] = new int[indexCount];
                    for (int k = 0; k < indexCount; k++) {
                        int index = input.readUnsignedByte();
                        if (tileType == tileTypeFocus) result[i][k] = index;
                    }
                }
            }
            return result;
        } catch (IOException error) {
            throw new IllegalStateException("Cannot load data/map/tile_set_info", error);
        }
    }

    private int[][] readTileMap(int mapId) {
        File file = new File("data/map/tile_map_data/" + mapId);
        if (!file.isFile()) return null;
        try (DataInputStream input = new DataInputStream(new FileInputStream(file))) {
            int width = input.readUnsignedByte();
            int height = input.readUnsignedByte();
            if (width < 1 || width > 127 || height < 1 || height > 127) {
                throw new IOException("invalid dimensions " + width + "x" + height);
            }
            int[][] tileMap = new int[height][width];
            for (int[] row : tileMap) {
                for (int x = 0; x < row.length; x++) row[x] = input.readByte();
            }
            return tileMap;
        } catch (IOException error) {
            Logger.logException(MapService.class, error, "Invalid tile map ID " + mapId);
            return null;
        }
    }
}
