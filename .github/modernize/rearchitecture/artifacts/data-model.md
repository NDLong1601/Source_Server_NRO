# Data model

## Persistence evidence

The Java layer contains DAO classes for players, shops, events, PK history, transactions, training, and rankings under `src/nro/models/database/`. They obtain connections through `LocalManager`, so the database boundary is centralized but the domain model is not isolated from JDBC.

## Migration/data areas

- Canonical schema: `sql/team2026.sql`.
- Incremental changes: dated files under `sql/`.
- Rollback scripts exist for selected migrations, but not uniformly for every migration.
- Runtime content is split between SQL rows and file-backed data/assets under `data/`.

## Key entities

`Player`, `Clan`, `Item`, `Map`, `Mob`, `Npc`, `Shop`, `Task`, `Boss`, and event-specific state are the main domain entities. `Clan` currently spans gameplay state, progression, treasury, tree, shop, gift, item storage, and buffs.

## Architecture finding

The model is hybrid: durable player/game data is SQL-backed, while templates and client assets are loaded into static/global registries. This is valid for a game server but creates startup-order and cache-consistency constraints that must be preserved when reorganizing files.
