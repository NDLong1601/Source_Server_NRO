---
name: nro-java-build
description: Build, repair, validate, and deploy the Teamobi NRO Java server in the source-server-nro/source-server repository. Use for Java feature changes, rebuilding or repairing 20.jar, diagnosing javac, Lombok, or ZIP errors, comparing historical builds, fixing item-template or asset-cache regressions, or verifying sequential account logins in local mode. Keep client-nro-unity and every client project out of scope.
---

# NRO Java Build

Build the server as one release unit: Java source, configuration, database data, assets, and `20.jar` must agree. Prefer a reproducible full compile over copying a few changed class files.

## Start Here

1. Read `../../BUILD_JAVA_STANDARD.md` completely before changing build code or producing `20.jar`.
2. Inspect `git status --short` and preserve every unrelated or pre-existing user change.
3. Work only in the current server repository. Never edit, build, clean, or inspect `client-nro-unity` unless the user explicitly changes the scope.
4. For a regression, compare both source history and committed `20.jar` history. Do not assume the latest source produced the latest JAR.
5. Record the current JAR hash and server status before any mutation.

## Required Workflow

### 1. Establish the Baseline

- Check Java, `javac`, and Lombok versions.
- Check whether port `14445` is occupied and whether the server is already running.
- Inspect recent Git history for `20.jar`, build tools, session handling, item data, icons, and the feature being changed.
- Inspect configuration and database shape without printing credentials or sensitive row data.
- Treat a beige panel after account switching as a packet, cache, or render-state symptom until evidence proves otherwise.

### 2. Compile the Complete Source Tree

- Target Java 17 bytecode with `javac --release 17` unless the repository standard changes.
- Put `lib/lombok.jar` on both classpath and annotation processor path.
- Use Lombok `1.18.36` or newer when the active compiler is JDK 23.
- Compile every production Java source. Use a partial compile only as a fast diagnostic, never as the release build.
- Judge native compiler success by its exit code. Do not let a PowerShell warning on stderr masquerade as a failed compile.

### 3. Package and Deploy Safely

- Stop the server before replacing `20.jar`.
- Run `tools/server_control.ps1 -Action build` as the canonical build entry point.
- Keep the existing JAR as a timestamped backup until verification passes.
- Update ZIP entries through the controller's safe archive path; do not use raw `jar uf` for the release build.
- Roll back automatically or manually if packaging, verification, or startup fails.
- Remove temporary build directories after success and after failure.

### 4. Protect Protocol and Asset Cache State

- Keep every length-prefixed payload at or below `65535` bytes.
- For item-template synchronization, verify exactly one reload packet followed by exactly one append packet.
- Increase the relevant data version when item templates, icon mappings, or serialized data change.
- Preserve the connected client's version, zoom level, and client type for the lifetime of the session and across local account relogin.
- Validate all required icon scales (`x1` through `x4`); never use only the currently tested scale as proof.

### 5. Validate Before Handoff

- Confirm the full compiler succeeded and report the compiled class count.
- Inspect `20.jar` for every changed or newly added class.
- Run the normal protocol probe and the reconnect/skip-client-type probe.
- Start the server, confirm port `14445`, and inspect the current error log.
- Exercise the changed feature, including its success, failure, and persistence paths.
- In one client window, log in as account A, log out, then log in as account B. Verify maps, inventory, menus, icons, and feature panels without restarting the client.

### 6. Report Reproducible Evidence

Include:

- absolute path and SHA-256 of the final `20.jar`;
- Java, compiler, and Lombok versions;
- number of compiled classes and important JAR entries;
- protocol packet sizes and probe results;
- backup path and rollback status;
- server port and error-log result;
- manual A-to-B relogin result and any remaining untested behavior.

## Failure Routing

### Lombok methods appear missing

If many unrelated getters, setters, builders, or log fields suddenly fail, inspect annotation processing first. Upgrade Lombok, use `-processorpath`, and run a full compile before modifying domain classes.

### `ZipException: invalid entry size`

Restore the last known-good JAR, stop the server, remove only verified temporary output, and rebuild through `server_control.ps1`. Do not continue patching a suspect archive.

### Source is correct but the feature is absent

Verify the full build ran, the changed class is present in `20.jar`, the runtime loaded that exact JAR, and every router or menu entry points to the new code.

### Second account shows beige panels or missing images

Reproduce account A to account B without closing the window. Check session reset, cached data versions, item packet order and size, client profile retention, icon availability at the negotiated zoom, and exceptions during menu or panel construction.

### Only one image scale fails

Compare icon IDs across `x1` to `x4`, verify file names and case, then invalidate the corresponding data/icon cache version.

### PowerShell reports a warning as a fatal error

Temporarily set native command error handling to continue, capture all compiler output, restore the previous preference, and decide success from `$LASTEXITCODE`.

## Safety Rules

- Never touch `client-nro-unity` in local-mode server work.
- Never overwrite the only known-good `20.jar`.
- Never expose database credentials in command output or reports.
- Never clean the repository broadly to solve a build problem.
- Never discard dirty-worktree changes that are unrelated to the requested build.
- Never call a build successful based only on the first account login.
