# Teamobi NRO Map Format Reference

## 1. Map System Overview

In the Teamobi NRO engine, a map consists of three core components:
1. **Tile Matrix (`data/map/tile_map_data/<mapId>`)**: 2D grid of 24×24 pixel tiles controlling terrain, physics, and ground movement.
2. **Background Prop Placement (`data/map/item_bg_map_data/<mapId>`)**: Placed visual structures (houses, trees, shrines, bridges, rock formations, lanterns).
3. **Database Metadata (`map_template` & `bg_item_template`)**: Global parameters, waypoints, mobs, NPCs, and sprite rendering offsets.

---

## 2. Binary Specifications

### `tile_map_data/<mapId>`
* **Byte 0**: `widthTiles` (uint8, `1..255`)
* **Byte 1**: `heightTiles` (uint8, `1..255`)
* **Bytes 2+**: Row-major byte array of `widthTiles * heightTiles` tile indices.
* **Pixel Size**: $W_{\text{px}} = \text{widthTiles} \times 24$, $H_{\text{px}} = \text{heightTiles} \times 24$.
* **Tile ID 0**: Empty space (air / fall-through).
* **Tile ID > 0**: 1-based tile index inside active TileSet.

### `item_bg_map_data/<mapId>`
* **Bytes 0..1**: `itemCount` (int16 Big-Endian).
* **For each item (6 bytes)**:
  * `templateId` (int16 Big-Endian): Points to `bg_item_template.id`.
  * `tileX` (int16 Big-Endian): Horizontal position in **24px tile units**.
  * `tileY` (int16 Big-Endian): Vertical position in **24px tile units**.

---

## 3. Database Metadata

### `bg_item_template`
* `id` (`int`): Unique template ID.
* `image_id` (`int`): PNG image ID loaded from `data/item_bg_temp/x<zoom>/<image_id>.png`.
* `layer` (`int`): Depth layer (`1`=Deep BG, `2`=Mid BG, `3`=Terrain Level, `4`=Foreground).
* `dx`, `dy` (`int`): Fine pixel offset adjustments applied during rendering.

### `map_template`
* `id` (`int`): Map ID.
* `name` (`varchar`): Map name.
* `zones`, `max_player`: Concurrency parameters.
* `tile_id`: TileSet index (1..42) defining tileset graphics and physics flags in `tile_set_info`.
* `bg_id`, `bg_type`: Parallax background scenery configuration.
* `waypoints`: JSON array of portals `[name, minX, minY, maxX, maxY, isEnter, isOffline, goMap, goX, goY]`.
* `mobs`: JSON array of monster spawns `[mobTemp, mobLevel, mobHp, mobX, mobY]`.
* `npcs`: JSON array of NPC spawns `[npcId, npcX, npcY]`.
