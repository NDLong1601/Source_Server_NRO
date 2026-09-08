# Gate 3 Release Candidate Verification — 2026-09-09

## Result

Gate 3 (ARC-C01 through ARC-C08) is implemented, packaged, and running as a
local release candidate. `Controller` remains the session-facing compatibility
facade and delegates the frozen 78-command inventory to explicit auth/asset,
economy, inventory/shop, world/combat, and social/clan/activity owners.

Automated source, packaged-JAR, command-registry, security-boundary, economy,
startup, and protocol checks passed. Formal operational closure still requires
the physical one-window account A → logout → account B check against this JAR
and a new runtime canary because deploying Gate 3 restarted the server.

## Artifact

- Release JAR: `20.jar`
- SHA-256: `57A7E72C1F04F8674C0346D9CA063A3A0F55D953B04EB364A4D5555456D13D03`
- Canonical full build: `tools/server_control.ps1 -Action build`
- Classes updated by build: 871
- Class entries in final JAR: 3,711
- Immediate rollback backup: `20.jar.bak_20260909_003734`
- Rollback content: pre-Gate-3 Gate 2 release JAR
- Runtime/compiler: Java/Javac 17.0.20.1; Lombok 1.18.36

## Implementation evidence

- `Controller.java` decreased from 1,069 to 104 lines and contains no command
  switch or gameplay mutation.
- `ControllerCommandRegistry` assigns all 78 frozen signed-byte commands to
  exactly one non-legacy domain owner and rejects duplicate registration.
- Unknown commands retain the legacy no-op response behavior while incrementing
  a dedicated metric.
- Truncated payloads are classified as malformed, contained to the dispatch
  call, and counted separately from handler failures.
- Exception logging is limited to five events per session and 100 globally per
  minute. Logs include session ID, command, category, and exception type but no
  payload, password, username, or full IP address.
- Command `66` now validates image keys before filesystem lookup and rejects
  traversal, absolute paths, empty values, and names longer than 64 characters.
- Full command and payload ownership is documented in
  `docs/architecture/controller_dispatch_model.md`.

## Verification

- TDD RED evidence: the Gate 3 dispatcher test initially failed to compile
  because the dispatcher contracts did not exist; the asset-boundary extension
  initially failed because `AssetRequestPolicy` did not exist.
- `Test-Gate3RegressionSuite.ps1`: passed from freshly full-compiled source.
  It verifies all 78 owners, duplicate rejection, signed-byte normalization,
  player policy, legacy fallback, error limiting/classification, asset-name
  validation, and the Controller facade boundary.
- `Test-SEC07ProductionCompile.ps1`: passed after the domain extraction,
  including full Java 17 compile and wallet, trade, consignment, achievement,
  activity, JDBC paging, reconciliation, and CLI regressions.
- Packaged-JAR Gate 3 dispatcher test: passed.
- Packaged-JAR achievement/Controller wire regression: 727 assertions passed.
- JAR inspection confirmed `Controller`, `ServerRuntimeMetrics`, registry,
  dispatcher, five domain handlers, error reporter/limiter, and asset policy.
- Standard protocol probe: passed with 2,275 item templates, one reload, two
  contiguous append packets, one completion packet, and maximum observed
  payload 65,521 bytes.
- Reconnect/skip-client-type probe: passed; icon 16187 at zoom 1 was 21x21 with
  SHA-256 `F5512D374DD829B22AE3C0F094DDA285756942EFED6AD26CB34C6A9A7D5A76A4`.
- Server restarted from the release JAR as PID 29288 and is listening on port
  14445. `logs/server-error.log` is empty after startup and both probes.

## Release scope and remaining checks

- No client project was inspected, changed, built, or made a dependency.
- No database, asset, or cache-version change was required.
- No dependency was added.
- Complete the physical one-window A → logout → B flow and verify map,
  inventory, NPC menus, trade/consignment, clan/activity, combat, and icons.
- Restart the canary clock from the Gate 3 startup at 2026-09-09 00:39 local
  time. Monitor heap, threads, protocol error categories, tick exceptions,
  rejected player overlaps, close causes, and sender high-water bytes.
- If validation fails, stop the server, restore
  `20.jar.bak_20260909_003734`, restart, and preserve the failed JAR and logs.
