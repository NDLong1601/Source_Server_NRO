# Third-Party Skill Manifest

This file records reviewed external skill sources, installed paths, local adaptations,
and the staged rollout policy for the Teamobi 2026 NRO server repository. It is not an
installation script.

## Current State

- Rollout stage: **Stage 2 - workflow expansion installed and statically audited**.
- Third-party skills installed by this rollout: **six**.
- Installation scope: project-local `.agents/skills` only.
- Every stage must be reviewed and committed separately.
- Stage 1 fresh-turn discovery passed on 2026-09-06. Stage 2 skills become discoverable
  to Codex on the next turn; their live trigger validation remains pending.

Existing repository-owned skills are not third-party imports:

| Skill | Repository path |
| --- | --- |
| `generate-teamobi-map` | `.agents/skills/generate-teamobi-map` |
| `generate2dmap` | `.agents/skills/generate2dmap` |
| `nro-ai-costume` | `.agents/skills/nro-ai-costume` |
| `nro-create-mob` | `.agents/skills/nro-create-mob` |
| `nro-add-usable-item` | `skills/nro-add-usable-item` |
| `nro-java-build` | `skills/nro-java-build` |

## Reviewed and Pinned Sources

Pins below were resolved on 2026-09-06. A pin is a candidate source snapshot, not
authorization to install or execute it.

| Source | Pinned commit | License | Planned use | Status |
| --- | --- | --- | --- | --- |
| `addyosmani/agent-skills` | `48cb1168aeaaa70dfc2bbf709eddfa2a8ed8129a` | MIT | Selected review, security, simplification, TDD, and observability skills | Five Stage 1/2 skills installed 2026-09-06 |
| `obra/superpowers` | `b36e0829c6d0140e93cfef2ca599b1b07d4a7797` | MIT | `systematic-debugging` only | Stage 1 skill installed 2026-09-06 |
| `tech-leads-club/agent-skills` | `fc886b77e54db38b621f08472434cbb73ef35008` | MIT for software; skill content CC BY 4.0 unless overridden | `security-threat-model` only | Reviewed; not installed |
| `nextlevelbuilder/ui-ux-pro-max-skill` | `314307f156aeab0c6b567bbaa1ce4e7aabd5a636` | MIT | Reference material for a future HTA-specific wrapper | Reviewed; direct install deferred |

## Planned Rollout

### Stage 1 - Core Engineering Workflows

Installed imports:

- `.agents/skills/code-review-and-quality` from
  `addyosmani/agent-skills/skills/code-review-and-quality`
- `.agents/skills/security-and-hardening` from
  `addyosmani/agent-skills/skills/security-and-hardening`
- `.agents/skills/code-simplification` from
  `addyosmani/agent-skills/skills/code-simplification`
- `.agents/skills/systematic-debugging` from
  `obra/superpowers/skills/systematic-debugging`

Completed static gates:

- Inspected every downloaded file and relative reference.
- Confirmed no executable hook, binary, dependency manifest, package lifecycle script,
  symlink, or unreviewed dependency remains in the imported directories.
- Removed upstream skill-development fixtures, the Bash/npm polluter helper, and its
  TypeScript example because they are not part of the runtime workflow for this repo.
- Resolved references to shared upstream checklists and uninstalled Superpowers skills
  without importing their parent plugins.
- Added repository adaptations so root `AGENTS.md`, NRO-specific skills, Java 17/Ant,
  packet limits, cache/relogin validation, and deployment boundaries retain ownership.
- Preserved both MIT notices in `.agents/THIRD_PARTY_NOTICES.md`.

Fresh-turn gate:

- Passed: all four Stage 1 imports appeared in Codex project-local skill discovery on
  the next turn.
- Passed: review, debugging, and simplification descriptions remain narrow, while root
  `AGENTS.md` continues to route item, build, protocol, cache, asset, and deployment work
  to repository-specific skills or authorities.
- The planned three-to-five representative-task soak was not yet accumulated. The user
  explicitly requested the next stage, so Stage 2 installation proceeded while live
  workflow behavior remains subject to observation and rollback by its separate commit.

Local modifications by skill:

| Skill | Adaptation |
| --- | --- |
| `code-review-and-quality` | Read-only review boundary; Java/JAR dependency review; NRO protocol and deployment checks; removed dangling shared references |
| `security-and-hardening` | Added TCP/JDBC/HTA/PowerShell trust boundaries; made HTTP controls conditional; added safe redacted secret-review guidance; adapted JAR supply-chain checks |
| `code-simplification` | Replaced Claude/npm-oriented assumptions with `AGENTS.md`, Java/PowerShell conventions, explicit commit authority, and NRO behavior contracts |
| `systematic-debugging` | Existing evidence before instrumentation; stable seam or controlled probe; repository-native verification; removed uninstalled skill references and non-runtime fixtures |

### Stage 2 - Workflow Expansion

Installed imports:

- `.agents/skills/test-driven-development` from
  `addyosmani/agent-skills/skills/test-driven-development`
- `.agents/skills/observability-and-instrumentation` from
  `addyosmani/agent-skills/skills/observability-and-instrumentation`

Completed static gates:

- Inspected both downloaded directories in full; each contains only its `SKILL.md`.
- Confirmed no executable, hook, binary, symlink, dependency manifest, lifecycle script,
  or additional dependency was introduced.
- Removed dangling references to upstream shared checklists and uninstalled companion
  skills without importing their parent plugin.
- Narrowed triggers so TDD applies to behavior changes with a stable test seam and
  observability applies only when durable diagnostic signals are in scope.
- Preserved the Addy Osmani MIT notice and extended its covered-path list in
  `.agents/THIRD_PARTY_NOTICES.md`.

Pending fresh-turn gate:

- Confirm both Stage 2 skills appear in Codex project-local discovery.
- Exercise representative Java behavior-change, no-test-seam, logging, incident,
  HTA, item, and build scenarios.
- Confirm repository-specific NRO skills retain ownership and these general skills
  remain supplemental.

Local modifications by skill:

| Skill | Adaptation |
| --- | --- |
| `test-driven-development` | Stable-seam trigger; `tools/tests` and `map-tools/tests`; Java 17/Ant and HTA gates; controlled-probe fallback; no framework migration or automatic subagent delegation |
| `observability-and-instrumentation` | Explicit observability trigger; existing Java diagnostics first; no automatic telemetry dependencies; NRO command/JDBC/session boundaries; redacted fields and safe controlled verification |

### Stage 3 - On-Demand Security Modeling

Candidate import:

- `tech-leads-club/agent-skills/packages/skills-catalog/skills/(security)/security-threat-model`

Activate only for explicit threat-model or abuse-path requests. Preserve attribution
required by CC BY 4.0 in the installed skill and this manifest.

### Stage 4 - HTA UI Wrapper

Do not directly install the generic UI/UX pack. If needed, create a repository-owned
`nro-hta-ui-review` skill based on reviewed, attributable guidance from UI/UX Pro Max.
The wrapper must:

- target `admin_data_menu.hta`, `server_menu.hta`, and their HTML/CSS/JScript files;
- prohibit unsupported React, Tailwind, ES module, CSS custom property, and modern
  browser assumptions;
- require `node tools/check_admin_data_menu.js` after modular admin UI changes;
- distinguish static browser rendering from actual HTA/ActiveX behavior verification.

## Deliberately Excluded from This Rollout

- Full Addy Osmani or Superpowers lifecycle plugins: excessive trigger overlap with
  repository-specific workflows.
- `verification-before-completion`: substantially duplicated by `nro-java-build` and
  `BUILD_JAVA_STANDARD.md`.
- Karpathy-inspired/coding-guideline skills: duplicate repository guardrails and the
  reviewed source lacked a clear repository license at assessment time.
- Taste Skill: primarily optimized for modern marketing/frontend interfaces rather
  than dense Windows HTA administration tools.
- Caveman: output compression is not a source capability and may hide release evidence.
- Awesome Claude Skills as a bundle: it is a catalog with many unrelated integrations,
  not a coherent project dependency.
- Generic Playwright webapp-testing as proof for HTA behavior: Chromium cannot validate
  the complete ActiveX/JScript runtime contract.
- Subagent-mandatory PR review workflows: orchestration requirements exceed the default
  review scope and overlap existing review controls.

## Import and Update Procedure

For every future imported skill:

1. Confirm the working tree and current rollout branch.
2. Re-check the upstream commit and license; do not silently replace the recorded pin.
3. Download only the approved skill directory into `.agents/skills`.
4. Inspect all Markdown, scripts, templates, binaries, symlinks, dependency manifests,
   hooks, and external tool requirements before execution.
5. Resolve or vendor required relative references explicitly. Do not install a full
   plugin only to satisfy an optional shared checklist.
6. Add any NRO-specific trigger narrowing without weakening upstream attribution.
7. Run the stage's trigger and behavior gates in a fresh Codex turn.
8. Record installed path, source commit, license, local modifications, test evidence,
   and installation date in this file.
9. Commit the stage independently so rollback uses a targeted `git revert`.

Updates follow the same procedure and require reviewing the upstream diff between the
previous and proposed pins. Automatic updates are not allowed.
