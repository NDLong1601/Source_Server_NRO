---
name: nro-add-usable-item
description: Add or update usable item templates in the Teamobi NRO Java server, wire their UseItem behavior, expose them through admin/shop data, prepare correctly scaled x1-x4 PNG icons, invalidate client item/icon caches, and verify the deployed 20.jar. Use for requests to “tạo/thêm vật phẩm sử dụng”, add a consumable or reroll item, place a new item in an NPC shop, fix a shop broken by a new template ID, or resize an NRO item icon that overflows the UI.
---

# Add an NRO usable item

Implement the item end to end. Treat database rows, Java behavior, client template versions, icon assets, and the runtime JAR as one feature.

## Start safely

1. Read any `AGENTS.md` that applies.
2. Inspect `git status --short`; preserve unrelated user changes.
3. Read [references/repository-map.md](references/repository-map.md) before editing this repository.
4. Collect or infer: template ID, icon ID, name, exact description, type, gender, price/currency, use effect, prerequisites, failure text, consumption rule, and target shops.
5. Search for the nearest existing item and effect. Prefer the server's established helper over duplicating game logic.

## Add the template

1. Confirm the template ID is unused in both `sql/team2026.sql` and the live database.
2. Add a complete `item_template` row to the SQL dump. Keep IDs contiguous because `ItemService.getTemplate(id)` indexes `Manager.ITEM_TEMPLATES` by list position.
3. Use the same `TYPE`, flags, and option conventions as the nearest working consumable. Type `29` is the normal support/consumable group in this server.
4. Save the same row through `tools/admin_data.ps1 -Action saveitem` when the task authorizes updating the live admin data.
5. Re-query the row and verify every field, especially `id`, `TYPE`, `icon_id`, description, and power requirement.

## Implement use behavior

1. Add a named item ID constant in `src/nro/models/services_func/UseItem.java`.
2. Route the ID from the inner `switch (item.template.id)` reached by the template type.
3. Put effect logic in a small private method.
4. Validate every prerequisite before mutating state or consuming the item. Handle absent pet, absent skill collection, short collections, empty skills, full bags, and other relevant invalid states.
5. Send the exact requested failure message and return without consuming the item.
6. Reuse existing helpers such as `pet.openSkill2()` when the requested behavior matches Shenron or another established feature.
7. Consume exactly one item only after the effect succeeds. Let the surrounding handler refresh bags unless the local branch exits early or the existing pattern requires an explicit refresh.
8. Avoid counting a failed use as success when adding new task hooks.

## Configure shops and admin data

1. Verify the NPC shop tag and tab IDs with `listshops`, `listtabs`, and `listshopitems`.
2. Use a supported `type_sell` and option mode. For items without options, default-only mode may legitimately produce zero options.
3. Restarting loads shop data into `Manager.SHOPS`; confirm the startup log reports the expected item-template and shop counts.
4. If adding the template makes a shop stop opening while the server has no exception, suspect stale client item data first. Increment `DataGame.vsItem`, rebuild, and require a full client reconnect.

## Prepare icons

Use `scripts/resize_item_icon.ps1` for deterministic resizing. Derive all scales from the highest-quality source, never from a previously downscaled image.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/resize_item_icon.ps1 `
  -Root <repo-root> -IconId <icon-id> -ReferenceId 15002
```

The script copies the reference dimensions for `x1`, `x2`, `x3`, and `x4`, preserves alpha, and overwrites only the requested icon ID. Use `-WhatIf` to preview targets.

After resizing:

1. Verify dimensions, `Format32bppArgb`, and transparent corner pixels.
2. Inspect at least `x1` and `x4` visually.
3. Compare with icon `15002` for square items or `1089` when a narrow/tall item is intended.
4. Increment the per-icon byte returned by `DataGame.sendSmallVersion` whenever an existing icon ID changes. Keep other icon versions unchanged. This forces clients to replace cached PNG data after reconnecting.

## Build and deploy

1. Compile before changing runtime state. Use the full source set if dependent classes in `20.jar` are older than source.
2. Check `java`, `javac`, `jar`, and `javap` versions. Do not trust a build that mixes incompatible JDK tools.
3. Do not restart while players are connected. Check established connections on the configured port.
4. Prefer the repository's server control flow. If building while the server runs, create and verify `20.jar.pending`; the start flow applies it with a backup.
5. Inspect the deployed bytecode with a compatible `javap` to confirm the new handler and cache versions exist.
6. Confirm the server listens on its configured port, `server-error.log` is empty, and startup logs reach “Server initialized”. Account for this repository's slow database startup before declaring failure.
7. Tell the user to exit the client completely and reconnect after changing `vsItem` or a small-image version.

## Completion checks

- Template exists in SQL and, when authorized, live DB/admin menu.
- Item is present in every requested shop with valid price/currency fields.
- Failed prerequisites do not consume the item.
- Successful use applies the effect once and consumes one item.
- Icon dimensions match the chosen reference at all four scales without clipping or lost transparency.
- Client cache versions are updated only where required.
- Source compiles, runtime JAR contains the change, and the server is healthy.
