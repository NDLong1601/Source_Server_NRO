# Teamobi NRO repository map

Use these paths and invariants for `C:\Users\PC\Music\Teamobi2026\SRC`. Re-discover them if the repository moves or changes.

## Core files

| Purpose | Path |
|---|---|
| Item templates and seed data | `sql/team2026.sql`, table `item_template` |
| Item-use dispatch | `src/nro/models/services_func/UseItem.java` |
| Pet skill pools and unlock rules | `src/nro/models/player/PetConfig.java`, `pet.properties` |
| Shenron reference behavior | `src/nro/models/services/shenron/SummonDragon.java` |
| Pet reroll helpers | `src/nro/models/player/Pet.java` |
| Item template delivery and cache version | `src/nro/models/data/DataGame.java`, `src/nro/models/data/ItemData.java` |
| Shop packet serialization | `src/nro/models/shop/ShopService.java` |
| Shop database loading | `src/nro/models/database/ShopDAO.java` |
| Admin data backend | `tools/admin_data.ps1` |
| Admin items tab | `admin_data_menu/js/tabs/items.js`, `admin_data_menu/views/items.html` |
| Runtime build/control | `tools/server_control.ps1`, `20.jar` |
| Icons | `data/icon/x1` through `data/icon/x4` |

## Known working references

- Item `1758` is an existing skill-2 reroll consumable and calls `pet.openSkill2()`.
- Item `1759` rerolls pet skill 3.
- `PetConfig.getSkillPool(2)` defaults to skill template IDs `1, 3, 5`.
- Icon `15002` is square: `24, 48, 72, 96` pixels across `x1-x4`.
- Icon `1089` is narrow/tall: `12x16, 24x32, 36x48, 48x64`.

## Protocol invariants

- `Manager.ITEM_TEMPLATES` is loaded into a list and `ItemService.getTemplate(id)` performs direct list indexing. Do not introduce an ID gap.
- A client with stale `DataGame.vsItem` may fail to render an entire shop containing an unknown new template ID even though the server logs no shop exception.
- `DataGame.sendSmallVersion` sends one version byte per small-image ID. Increment the byte for an edited icon ID so clients invalidate their cached PNG.
- `DataGame.sendIcon` reads `data/icon/x<zoom>/<icon-id>.png` at request time; image files do not belong inside the JAR.

## Useful admin reads

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\admin_data.ps1 -Action listitems -Output <file> -Search <item-id>
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\admin_data.ps1 -Action listshops -Output <file>
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\admin_data.ps1 -Action listtabs -Output <file> -ShopId <shop-id>
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\admin_data.ps1 -Action listshopitems -Output <file> -TabId <tab-id>
```

These commands rewrite the rolling `logs/admin_data.log`. Preserve user activity and remove only diagnostic files created by the current task.
