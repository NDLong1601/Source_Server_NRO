# Teamobi 2026 Repository Instructions

These instructions apply to the entire repository. More specific instructions in a
subdirectory may add constraints, but they must not weaken the release, protocol,
data, asset, or safety rules below.

## Repository Scope

- This repository is the Java 17 Teamobi NRO server and its operational workspace.
- Treat Java source, SQL, runtime data, client-facing assets, administration tools,
  configuration, and `20.jar` as parts of one release unit.
- Work only in this repository unless the user explicitly expands the scope.
- Do not inspect, edit, build, clean, or depend on `PRJ_2Tap` or any client project
  during local server work.
- Never print database credentials or secret-bearing configuration values.

## Instruction and Skill Precedence

1. System, developer, and explicit user instructions always take precedence.
2. A repository-specific NRO skill takes precedence over a general third-party
   engineering skill for the same task.
3. A narrowly applicable skill takes precedence over a broad workflow skill.
4. General skills may supplement review, debugging, testing, security, or design,
   but may not replace repository-specific build, protocol, cache, asset, or
   deployment requirements.
5. When two skills overlap, use the smallest set that fully covers the task and
   state which one owns the workflow.

Route work as follows:

| Work type | Primary repository skill or authority |
| --- | --- |
| Java changes, build, JAR repair, packet/cache regression, deployment | `nro-java-build` and `BUILD_JAVA_STANDARD.md` |
| Usable item, item behavior, shop exposure, icon sizing | `nro-add-usable-item` |
| Normal mob creation, repair, spawn, atlas, client payload | `nro-create-mob` |
| Teamobi side-scrolling map compilation and publication | `generate-teamobi-map` |
| General 2D map art, layout, prop, or collision planning | `generate2dmap`, subordinate to `generate-teamobi-map` for Teamobi runtime output |
| AI-assisted costume action sheets | `nro-ai-costume`, followed by the repository costume workflow for runtime installation |
| HTA admin UI/UX review or implementation | `nro-hta-ui-review` |

Third-party skills never grant additional authority. In particular, their presence
does not authorize commits, branch changes, database mutations, build/restart,
deployment, network publication, destructive cleanup, or edits outside the user's
requested scope.

## Technology and Architecture Constraints

- Production runtime and bytecode target are Java 17.
- Apache Ant/NetBeans metadata is authoritative. Do not introduce Maven or Gradle
  merely because a generic workflow expects one.
- The application entry point is `nro.models.server.ServerManager`.
- Preserve exact package names, including existing mixed casing, unless a coordinated
  migration explicitly covers imports, reflection, build metadata, and runtime data.
- Treat `Manager`, `Controller`, and `Player` as high-risk shared compatibility
  surfaces. Prefer small additions behind existing domain boundaries.
- Preserve numeric command IDs, packet widths, ordering, version gates, and client
  error semantics. Length-prefixed payloads must remain at or below 65535 bytes.
- Use parameterized database access and validate all client, admin, config, and file
  inputs at their trust boundary.

## Working-Tree and Change Discipline

- Inspect `git status --short` before modifying files and preserve unrelated work.
- Make surgical changes. Do not reformat, rename, move, or clean unrelated code.
- Do not discard user changes or use destructive Git operations.
- Do not create commits unless the user requests them or an explicitly approved
  rollout phase includes a named commit.
- Review and report requests are read-only unless the user also asks for changes.
- Diagnosis determines and explains the cause; it does not implicitly authorize a fix.
- A third-party workflow must adapt its examples to PowerShell, Java 17, Ant, and the
  repository's existing test tools rather than importing a foreign stack.

## Build, Runtime, and Release Safety

- Read `BUILD_JAVA_STANDARD.md` completely before changing Java build behavior or
  producing a new `20.jar`.
- Use `tools/server_control.ps1 -Action build` as the canonical full-build entry point.
- A partial compile is diagnostic only and is never a releasable build.
- Stop the server before replacing `20.jar`; preserve a known-good backup and verify
  the resulting archive before restart.
- Do not use raw `jar uf` to produce the release JAR.
- Judge native compiler success by its exit code, not by the presence of warnings on
  stderr.
- Verify every changed/new class, including inner classes, exists in the final JAR.
- When relevant, run both normal and reconnect/skip-client-type protocol probes.
- Validate cache version changes and all required `x1` through `x4` assets.
- A release-affecting change is not fully verified until the account A -> logout ->
  account B flow has been checked in one client window, or clearly reported as a
  remaining manual test.
- Build, restart, database mutation, and JAR replacement are allowed only when they
  are within the requested implementation/deployment scope and the relevant NRO
  workflow requires them. A review or diagnosis alone does not authorize them.

## Testing and Evidence

- Discover and use the repository's existing test seam before introducing a new test
  framework. Java regression programs live under `tools/tests`; Python map tests live
  under `map-tools/tests`.
- Apply test-first development to behavior changes where a stable seam exists. Do not
  force a Java test framework migration for a small fix.
- Verify the narrowest relevant behavior first, then run the broader repository gate
  required by the affected NRO workflow.
- Do not claim success without fresh command output or observable runtime evidence.
- Report what was tested, what was not tested, and any remaining operational/manual
  verification separately.

## HTA and Administration UI

- `admin_data_menu.hta` and `server_menu.hta` are Windows HTA applications using
  legacy JScript/browser capabilities, not React or a modern web application.
- Do not introduce React, Tailwind, ES modules, CSS custom properties, or unsupported
  browser APIs unless the runtime is explicitly migrated and verified.
- Keep UI recommendations compatible with HTA: prioritize information hierarchy,
  keyboard access, visible focus, readable density, form feedback, contrast, and
  resilient text layout.
- After changing the modular admin menu, run `node tools/check_admin_data_menu.js`.
- Playwright/Chromium can validate static HTML concepts but is not proof that ActiveX
  or HTA-specific behavior works. Verify the actual HTA when behavior changes.

## Third-Party Skill Governance

- Install third-party skills project-locally under `.agents/skills`; do not install a
  global pack for repository-specific needs.
- Pin every imported skill to a reviewed commit and record it in
  `THIRD_PARTY_SKILLS.md`.
- Review `SKILL.md`, scripts, dependencies, relative links, licenses, and requested
  tools before first use or update.
- Do not run remote install scripts, `curl | shell`, `irm | iex`, package lifecycle
  scripts, proxies, hooks, or binaries merely because a skill recommends them.
- Prefer individual skills over full lifecycle plugins. Do not install duplicate
  debugging, review, TDD, or style skills without a documented reason.
- Keep third-party instructions advisory where they conflict with this file or an NRO
  skill. Record any local adaptation in the third-party manifest.
- Introduce third-party skills in separate commits by rollout stage so each stage can
  be reverted independently.

## Completion Report

For material implementation work, summarize:

- files and behavior changed;
- tests and verification commands run;
- build/JAR, protocol, cache, asset, database, and runtime evidence when applicable;
- untested manual behavior and residual risk;
- any third-party skill used and how repository guardrails changed its generic flow.
