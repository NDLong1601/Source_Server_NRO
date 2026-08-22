# Layer & Background Contract for Teamobi NRO Maps

## Rendering Pipeline & Z-Ordering

The client renders world elements in the following strict multi-layer order:

```
[Layer 0]: Theme Background Backdrop (Single Cover Mode or Parallax)
  └── [Layer 1]: Deep Background Props (bg_item_template.layer = 1)
        └── [Layer 2]: Secondary Background Scenery (bg_item_template.layer = 2)
              └── [Layer 3]: Midground Terrain Structures (bg_item_template.layer = 3)
                    └── [TileMap]: 24x24 Solid Tile Grid (data/map/tile_map_data)
                          └── [Entities]: Ground Items, Waypoints, NPCs, Mobs, Players
                                └── [Layer 4]: Foreground Occluders & Canopies (bg_item_template.layer = 4)
                                      └── [Screen Layer]: Weather FX & UI Overlays
```

---

## 1. Shared Theme Background Contract (Cover Mode)

For custom theme series (e.g. **Infinity Castle**):

* **Single Authoritative Master Source:**
  All maps in the series (e.g., Map 201, Map 202, Map 203, ...) use the **SAME** authoritative master image:
  `map-tools/assets/infinity_castle/background/background.png`
* **Shared Visual Identity:**
  - Do NOT split the background into separate left/right cropped images for different maps.
  - Do NOT create separate or divergent background files for Map 201 vs Map 202.
  - All maps in the series share the same visual backdrop and same runtime `bgId` assignment (e.g. `bgId = 0` or shared theme `bgId`).
* **Cover Scaling Rules:**
  - **No Tiling:** The background must never be horizontally or vertically tiled.
  - **Uniform Proportional Scale:**
    $$\text{scale} = \max\left(\frac{\text{mapWidthPixels}}{\text{backgroundWidth}}, \frac{\text{mapHeightPixels}}{\text{backgroundHeight}}\right)$$
  - **Aspect Ratio Preservation:** The source aspect ratio is strictly preserved.
  - **Center Crop:** The uniformly scaled image is center-cropped to the exact map dimensions ($W_{\text{px}} \times H_{\text{px}}$).
  - **Non-Destructive:** The master source asset in `map-tools/assets/` is never modified or overwritten.

---

## 2. Layer Definitions & Guidelines

### Layer 1: Deep Background
* **Z-Index:** Behind midground terrain and structures.
* **Usage:** Distant mountain silhouettes, distant fortress towers, great halls, far clouds.
* **Collision:** Non-colliding.

### Layer 2: Secondary Background
* **Z-Index:** Between deep background and midground.
* **Usage:** Distant rock pillars, atmospheric FX (floating debris, energy pillars, mist auras).

### Layer 3: Midground / Walkable Level
* **Z-Index:** On the same visual plane as 24×24 terrain tiles, drawn behind characters and mobs.
* **Usage:** Solid walkways (e.g. `355.png`), staircases (e.g. `357.png`), bridges, trees, shrines.

### Layer 4: Foreground Dressing
* **Z-Index:** Drawn **in front of player characters, mobs, and NPCs**.
* **Usage:** Hanging lanterns, roof eaves that players walk beneath, foreground fences.
* **Rule:** Must be used sparingly so gameplay action, character silhouette, and combat visibility are not obscured.
