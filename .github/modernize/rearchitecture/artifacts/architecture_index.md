# Implementation Guide

This index is not the full contract. Before changing code, implementation agents must read the listed per-unit artifacts and the filtered rows in the global artifacts.

## Findings

- The repository is structurally valid for a Java 17 NetBeans/Ant game-server monolith.
- The main architectural risk is coupling, not missing top-level folders.
- `Manager`, `Controller`, and `Player` are shared modules and must not be moved or split without preserving their compatibility surfaces.
- `PlayerAppearanceService` now owns player appearance/equipment calculations; `Player` retains delegating methods so packet, NPC, DAO, and social callers keep their existing API.
- `server/ServerManager.java` starts network, event, boss, minigame, and maintenance threads directly; lifecycle ownership is distributed.
- Naming consistency is imperfect (`Bot`, `daily_Giftcode`, `Boss_Manager`, `shop_ky_gui`), but package moves are high-risk because build metadata and runtime dispatch rely on exact names.

## Unit guides

### server-bootstrap

- Read `units/server-bootstrap/behavior.yaml`, `bindings.yaml`, and `unit_decomposition.yaml`.
- Filter `shared_modules.yaml` by `used_by_units` containing `server-bootstrap`.
- Preserve main-class launch, initialization order, port configuration, and background thread startup.
- Completion evidence: main class still starts, network binds, and required managers initialize in the same order.

### network-accept

- Read `units/network-accept/behavior.yaml`, `bindings.yaml`, and `unit_decomposition.yaml`.
- Filter `wire_contracts.yaml` for `kind: tcp` and `game-protocol`.
- Preserve selector behavior, session creation, session registration, and retry/close semantics.
- Completion evidence: a client connection reaches `SessionManager` and receives the expected session handshake.

### protocol-dispatch

- Read `units/protocol-dispatch/behavior.yaml`, `bindings.yaml`, and `unit_decomposition.yaml`.
- Filter `wire_contracts.yaml` for `kind: game-protocol`; inspect `Controller.java` before changing command routing.
- Preserve numeric command IDs, payload widths, version gates, and error messages.
- Completion evidence: changed command paths parse the same bytes and delegate to the same feature services.

### gameplay-runtime

- Read `units/gameplay-runtime/behavior.yaml`, `bindings.yaml`, and `unit_decomposition.yaml`.
- Filter `shared_modules.yaml` for `gameplay-runtime`, and `data-model.md` for persistence boundaries.
- Preserve player/clan/item/map state, DAO transactions, and runtime configuration loading.
- Completion evidence: affected feature flows still update SQL state and send the same client-visible results.

## Repository hygiene boundary

Source and authoritative inputs are `src/`, `data/`, `sql/`, `lib/`, build metadata, and operational scripts. `build/`, `dist/`, `logs/`, `output/`, and test `bin/obj` are generated or transient and must remain ignored. Root `20.jar` is a release artifact; timestamped `20.jar.bak_*` files are rollback artifacts, not source.
