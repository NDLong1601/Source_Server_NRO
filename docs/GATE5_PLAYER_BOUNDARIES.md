# Gate 5 player boundaries

Gate 5 closes the high-risk transient and persistence fields first. `session`,
lifecycle flags, persistence quarantine, save revision, and persisted event flags
are private owners behind `PlayerRuntimeContext`, `PlayerLifecycleState`,
`PlayerPersistenceState`, and `PlayerEventState`.

The following legacy public surfaces are temporarily approved compatibility
exceptions because protocol, map, combat, and service code still consume them:

- `zone` and `location`: spatial owner migration requires a coordinated Zone API;
- `inventory`, `nPoint`, `playerTask`, and other domain components: callers must use
  their existing domain service where one exists;
- identity and protocol-facing scalar fields: retained until their packet golden
  tests cover replacement accessors.

No new public mutable Player field is allowed. The Gate 5 regression suite enforces
the closed high-risk fields and prevents `PlayerDAO` from reading live mutable
components. Remaining exceptions are migration debt for the next bounded-context
change; they are not permission to add new direct mutation sites.

Persistence contract:

- RPO: 60 seconds (`player.autosave.rpo_ms`);
- snapshots are captured by the player's Zone mailbox;
- `player`, the normalized `player_wallet` mirror, and `super_rank` are written in
  one transaction;
- `save_version` rejects stale writers;
- JSON schema versions stay inside their JSON document; the legacy `player` row
  is already near the storage-engine row-size limit and must not carry a redundant
  schema column;
- failed autosaves use bounded exponential retry and a dead-letter record;
- legacy event arrays remain readable during rollout;
- `data_inventory` remains the read authority until every specialized economy
  writer has migrated; this prevents a partially migrated normalized mirror from
  replacing a newer legacy balance.
