# Coordinate Contract for Teamobi NRO Maps

## HARD RULE: Coordinate Systems Must Never Be Mixed

The engine uses two strictly separated coordinate spaces:

```
+-------------------------------------------------------------------------+
|                              MAP SYSTEM                                 |
+------------------------------------+------------------------------------+
|         TILE SPACE (24px)          |         PIXEL SPACE (1px)          |
+------------------------------------+------------------------------------+
| - Tile Grid (tileX, tileY)         | - Player Spawn (x, y)              |
| - BG Items (tileX, tileY)          | - Mob Spawns (mobX, mobY)          |
| - Platform Bounds (start/endTileX) | - NPC Standing (npcX, npcY)        |
| - Slope / Bridge Segments          | - Waypoint Trigger Rectangles      |
|                                    | - Waypoint Destination Spawn       |
|                                    | - Final Render Pixels (pixelX/Y)   |
+------------------------------------+------------------------------------+
```

---

## 1. Engine Dimension Limits & Safe Contract

Due to the Teamobi engine's network protocol and Java server binary reading `dis.readByte()` (signed 8-bit byte $[-128..127]$):

| Metric | Valid Range | Status |
|---|---|---|
| **`widthTiles`** | **$1 \le W \le 127$** | ✅ **VERIFIED SAFE RUNTIME RANGE** |
| **`heightTiles`** | **$1 \le H \le 127$** | ✅ **VERIFIED SAFE RUNTIME RANGE** |
| **Standard Exploration Width** | **$60 \le W \le 100$** | ✅ **OFFICIAL PRODUCTION STANDARD** |
| **Unverified Range** | **$128 \le W, H \le 255$** | ❌ **`UNVERIFIED_RUNTIME_RANGE`** (Negative signed byte error) |

---

## 2. Background Item Conversion Contract

In `item_bg_map_data`, coordinates are stored as integer tile units:
* `tileX`: 0 .. widthTiles - 1
* `tileY`: 0 .. heightTiles - 1

When rendered by the client, the final pixel position is calculated as:
$$\text{pixelX} = (\text{tileX} \times 24) + \text{dx}$$
$$\text{pixelY} = (\text{tileY} \times 24) + \text{dy}$$

* **Negative tile coordinates** (e.g. `tileX = -1`, `tileY = -4`) are allowed and supported for boundary tree canopies and cliff tops.

---

## 3. Gameplay Entity Placement Contract

* **Player Spawn:** Expressed in absolute pixels $(x, y)$.
  * Typical ground standing position: $y = \text{floorTileY} \times 24$.
* **Mob Placement:** Expressed in absolute pixels $(x, y)$.
  * Mob feet must touch walkable solid surface ($y = \text{groundTileY} \times 24$).
* **NPC Placement:** Expressed in absolute pixels $(x, y)$.
  * NPC feet must align with solid floor ($y = \text{groundTileY} \times 24$).
* **Waypoints:** Expressed in absolute pixels:
  * Trigger box: `[minX, minY, maxX, maxY]`
  * Target destination coordinates: `goX, goY`
