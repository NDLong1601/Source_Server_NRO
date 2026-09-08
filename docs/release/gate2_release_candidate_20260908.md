# Gate 2 Release Candidate Verification — 2026-09-08

## Result

Gate 2 (RUN-01 through RUN-08) is packaged and running as a local release candidate. The lifecycle issues found in the post-implementation review are fixed, and all automated source, JAR, lifecycle, real-socket soak-smoke, startup, and protocol checks listed below passed. The physical one-window account A → logout → account B check also passed. Formal Gate 2 closure still requires the roadmap's 24-hour canary.

## Artifact

- Release JAR: `20.jar`
- SHA-256: `9CA47E8616398F9DCC2E0EAA41D61D5BE1B565F46505C8BA5D4A2F13FBFAF02E`
- Canonical full build: `tools/server_control.ps1 -Action build`
- Packaged classes: 848
- Immediate rollback backup: `20.jar.bak_20260908_230920`
- Immediate backup SHA-256: `5ECCECFB306CC2ED77DB0DEB4A9CD480E72FC8FE69D0042EA061E59F9D41F688`
- Runtime: Java/Javac 17.0.20.1; Lombok 1.18.36

## Automated Verification

- `Test-Gate2RegressionSuite.ps1`: 31/31 scenarios passed from source and again against the packaged `20.jar`. Coverage includes close during login finalization, mailbox/dispose serialization, mailbox close races, shutdown ordering/failure isolation, bounded expiring IP caches, real TCP EOF worker termination, and command-specific outbound payload limits.
- The inventory-box UX regression passed 3/3 from source and against the packaged `20.jar`: initial NPC open still sends snapshot plus open, bag-to-box refreshes without reopening the panel, and box-to-bag refreshes immediately.
- Packaged-JAR `Test-Gate2SoakSmoke.ps1 -DurationSeconds 15`: 2,754,724 balanced lifecycle starts/teardowns, 1,440,705 slow consumers, 3,980 real TCP sessions, zero remaining IP leases/sessions, no deadlock, thread count returned from 20 peak to 1, and final heap after GC was 2.03 MB.
- Source soak also passed with 3,421,852 balanced lifecycle iterations and 4,056 real TCP sessions, with zero remaining leases and final thread count 1.
- `Test-SEC07ProductionCompile.ps1`: full Java 17 source compile and all SEC-07 CLI/paging/reconciliation plus wallet, trade, consign, achievement, and activity regressions passed.
- JAR inspection confirmed the changed/new session, network, player, registry, metrics, and runtime-owner classes, including relevant inner classes.
- Startup: the release JAR restarted successfully and is listening on port 14445; `logs/server-error.log` remained empty and startup completed normally.
- Standard protocol probe: passed, including three consecutive confirmation runs, with one reload, two contiguous append packets, one completion packet, and 2,275 item templates. Largest observed payload was 65,521 bytes. The probe's per-read timeout was raised from 15 to 30 seconds to fit its existing 60-second overall window after one immediate post-restart timeout.
- Reconnect/skip-client-type probe: passed; icon 16187 returned at zoom 1 as 21x21 with SHA-256 `F5512D374DD829B22AE3C0F094DDA285756942EFED6AD26CB34C6A9A7D5A76A4`.

## Release Scope and Safety

- The Unity client source was inspected read-only to verify its existing command `-35` snapshot handling. It was not changed, built, or added as a release dependency.
- No asset, cache-version, or database-schema/data migration was required for this server-runtime change.
- Login IP/timestamp persistence is parameterized; raw credentials are not logged and are cleared from the session on close.
- IP authentication/profile retention is bounded to 10,000 addresses with finite inactivity TTLs.
- The running server uses the new release JAR. The rollback backup is retained and was not staged or removed.
- Rollback is artifact-level. The unsafe leaked per-player executor was not reintroduced as a live "legacy tick" switch; reverting that behavior requires restoring a verified backup JAR while the server is stopped.

## Required Post-Release Checks

1. Completed: the physical client flow in one window, account A → logout → account B, passed on 2026-09-08 as confirmed by the user.
2. Complete the 24-hour canary/soak while monitoring runtime snapshots, heap, thread count, close-cause totals, sender high-water bytes, tick exceptions, and rejected update overlaps.
3. If the canary fails, stop the server, restore `20.jar.bak_20260908_230920`, restart, and preserve the failed JAR/logs for diagnosis.

The temporary Gate 2 and inventory-box test harnesses used to produce this evidence were removed from the working tree before commit at the user's request. The production control and protocol-probe tools remain available for operational verification.
