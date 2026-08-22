---
name: generate-teamobi-map
description: "Author and compile 2D side-scrolling maps for the Teamobi NRO engine. Converts natural-language level design requests into structured semantic map plans, validates coordinate contracts, compiles 24px tile matrices and background item placements, and renders visual previews without altering server binaries."
---

# Teamobi AI Map Authoring Skill (`generate-teamobi-map`)

## 1. Overview

This skill is the project-specific authoring layer for the Teamobi NRO 2D side-scrolling engine. It translates natural language level concepts into verified, playable map bundles:
* **Semantic Level Planning:** AI designs platforms, slopes, bridges, water, background scenery, mobs, NPCs, and waypoints in structured JSON (`teamobi-map-plan.json`).
* **Deterministic Toolchain Compilation:** Deterministic Python tools compile the plan into exact 24px tile grids (`compiled-tile-map.json`), background item records (`compiled-bg-map.json`), and binary data.
* **Rigorous Validation:** Every map plan is checked against strict coordinate contracts, boundary limits, physics profiles, and ground-alignment rules.
* **Pre-Export Visual Previews:** Multi-layer previews are rendered before any binary or SQL export is proposed.

---

## 2. Hard Coordinate & Dimension Contract

The engine strictly separates **Tile Space** from **Pixel Space**:

| System | Coordinate Unit | Range / Constraints | Formula / Render Target |
|---|---|---|---|
| **Tile Grid Terrain** | 24px Tiles | $0 \le x < \text{widthTiles}$, $0 \le y < \text{heightTiles}$ ($W, H \le 127$) | Direct matrix index in `tile_map_data` |
| **Background Items** | 24px Tiles (`tileX, tileY`) | $-5 \le x \le \text{widthTiles} + 5$ | $\text{pixelX} = \text{tileX} \times 24 + \text{dx}$, $\text{pixelY} = \text{tileY} \times 24 + \text{dy}$ |
| **Player Spawn** | Absolute Pixels (`x, y`) | $0 \le x \le W_{\text{px}}$, $0 \le y \le H_{\text{px}}$ | Feet on floor ($y = \text{floorTileY} \times 24$) |
| **Mob Spawns** | Absolute Pixels (`mobX, mobY`) | $0 \le x \le W_{\text{px}}$, $0 \le y \le H_{\text{px}}$ | Feet on floor ($y = \text{floorTileY} \times 24$) |
| **NPC Spawns** | Absolute Pixels (`npcX, npcY`) | $0 \le x \le W_{\text{px}}$, $0 \le y \le H_{\text{px}}$ | Feet on floor ($y = \text{floorTileY} \times 24$) |
| **Waypoints** | Absolute Pixels | `[minX, minY, maxX, maxY]`, `goX, goY` | Reachable jump/portal zones |

---

## 3. Map Dimensions & Scale Contract

* **Signed Byte Limit:** Due to Java `dis.readByte()` signed byte parsing, valid dimensions are **$1 \le \text{widthTiles}, \text{heightTiles} \le 127$**.
* **Standard Production Size:** $60 \text{ to } 100\text{ tiles wide}$ ($1,440\text{px} \text{ to } 2,400\text{px}$) and $20 \text{ to } 45\text{ tiles high}$ ($480\text{px} \text{ to } 1,080\text{px}$).
* **Multi-stage Series:** Longer dungeons must be authored as multiple connected stages linked via waypoints (e.g. Map 201 $\rightarrow$ Map 202).
* **Unverified Range:** $128 \le \text{tiles} \le 255$ is strictly marked as `UNVERIFIED_RUNTIME_RANGE`.

---

## 4. Shared Theme Background Contract (Cover Mode)

For custom theme series (e.g. **Infinity Castle**):
* **Single Authoritative Master Source:** `map-tools/assets/infinity_castle/background/background.png`.
* **Universal Shared Identity:** All maps in the series (Map 201, Map 202, ...) share the **same background visual identity** and same runtime `bgId`.
* **Uniform Cover Scale & Center Crop:**
  $$\text{scale} = \max\left(\frac{\text{mapWidthPixels}}{\text{backgroundWidth}}, \frac{\text{mapHeightPixels}}{\text{backgroundHeight}}\right)$$
* **No Tiling:** Source image is never horizontally or vertically repeated.
* **Non-Destructive:** Master asset is never modified.

---

## 5. Background Item & Layer Contract

All background props must reference valid template IDs in [`map-tools/output/bg_item_registry.json`](../../../../map-tools/output/bg_item_registry.json).

### Layer Ordering:
* **Layer 1 (Deep Background):** Distant mountains, far fortress halls, distant clouds.
* **Layer 2 (Secondary Background):** Secondary hill silhouettes, atmospheric FX (debris, energy columns, mist).
* **Layer 3 (Midground / Terrain Level):** Walkway planks (`355.png`), stairs (`357.png`), trees, shrines.
* **TileMap Grid:** 24×24 solid tiles.
* **Entities:** Items, waypoints, mobs, NPCs, players.
* **Layer 4 (Foreground Dressing):** Hanging lanterns, tree leaf canopies, foreground fences (drawn in front of characters).

---

## 6. Workflow

```
Natural Language Prompt / Request
               ↓
Write semantic `teamobi-map-plan.json` (widthTiles <= 127)
               ↓
Run compiler: `python map-tools/tools/compile_map_plan.py <plan.json>`
               ↓
Inspect `validation-report.json` and dual previews (`preview_clean.png`, `preview_debug.png`)
               ↓
Present to User for Feedback & Approval
```

---

## 7. Reference Documents

* [Teamobi Map Format Reference](references/teamobi-map-format.md)
* [Coordinate Contract](references/coordinate-contract.md)
* [Layer Contract](references/layer-contract.md)
* [TileSet Profiles (42 TileSets)](references/tile-profiles.json)
* [Plan JSON Schema](schemas/teamobi-map-plan.schema.json)
