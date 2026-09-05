# Project structure

## Project type

Java 17 server monolith for a real-time online game. The repository is also an operational workspace: source code, runtime data, SQL migrations, client assets, Windows launchers, admin tools, and generated release artifacts coexist at the repository root.

## Functional domains

- **Runtime/bootstrap:** `src/nro/models/server/ServerManager.java`, `Manager.java`, `Controller.java`.
- **Transport/session:** `src/nro/models/network/` and `src/nro/models/interfaces/`.
- **Player/gameplay:** `player/`, `item/`, `skill/`, `task/`, `combine/`, `shop/`. Player presentation/equipment calculations are isolated in `player/PlayerAppearanceService.java`, while `Player.java` remains the compatibility aggregate.
- **World simulation:** `map/`, `mob/`, `boss/`, `mob_bigboss/`, `npc/`, `npc_list/`, `event/`, `event_list/`.
- **Social and features:** `clan/`, `fishing/`, `matches/`, `radar/`, `player_badges/`, `activity/`.
- **Persistence:** `database/` DAOs backed by `data/LocalManager.java`, plus `sql/` migrations.
- **Operations and content tooling:** `admin_data_menu/`, `map-tools/`, `costume-tools/`, `assets/`, `tools/`.

## Layer assessment

The folder layout expresses domain groupings, but the runtime dependency direction is not strict. `server/Controller.java` directly imports a large number of gameplay services and domain classes; `server/Manager.java` owns global mutable registries for maps, items, mobs, NPCs, shops, tasks, and clans. This is workable for the current monolith but is the main coupling hotspot.

## Structure verdict

**Mostly correct for a legacy game-server monolith, not yet cleanly layered.** Keep the existing top-level domains for compatibility. Do not move packages casually without updating reflection, packet handlers, admin tooling, and build metadata.

## Recommended structural boundaries

1. Preserve `server -> network -> Controller -> services/domain -> database` as the documented runtime path.
2. Treat `data/`, `sql/`, `lib/`, and `assets/` as repository-owned runtime/deployment inputs, not Java packages.
3. Keep generated/transient folders (`build/`, `dist/`, `logs/`, `output/`, test `bin/obj`) ignored and outside release commits.
4. Introduce new features under an existing domain package (`clan`, `fishing`, `activity`) rather than adding more root-level feature folders.
