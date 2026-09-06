---
name: nro-hta-ui-review
description: Review or improve UI/UX for admin_data_menu.hta, server_menu.hta, and their HTA HTML/CSS/JScript modules. Use for HTA layout, hierarchy, keyboard access, focus, forms, feedback, dense data interfaces, or legacy-runtime compatibility; do not use for ordinary web or mobile UI.
---

# NRO HTA UI Review

Improve the NRO administration interfaces without breaking their Windows HTA runtime,
operator workflows, or repository architecture.

## Ownership and Authority

- Read root `AGENTS.md` first. This skill owns UI/UX review and UI-focused edits for
  `admin_data_menu.hta`, `server_menu.hta`, and files under `admin_data_menu/`.
- A review request is read-only. Edit files only when the user asks to implement or fix
  the interface.
- Use the project-local `security-and-hardening` skill only when a change affects
  ActiveX, commands, paths, credentials, database access, or another trust boundary.
- This skill does not authorize a server build/restart, database mutation, `20.jar`
  replacement, dependency installation, or changes to Java/game behavior.

## Read the Relevant Surface

Inspect the smallest complete flow, including its entry point and shared dependencies:

| Surface | Required context |
| --- | --- |
| Modular admin data UI | `admin_data_menu.hta`, `admin_data_menu/README.md`, the affected `views/`, `js/tabs/`, `js/components/`, `js/core/`, and CSS files |
| Server dashboard | The affected markup, style, and inline JScript in `server_menu.hta` |
| New admin tab | One root `.panel` view, its `RegisterTab(...)` module, navigation button, CSS, load order before `core/app.js`, and neighboring tab patterns |

Do not review a screenshot in isolation when the task concerns interaction or runtime
behavior. Trace the visible control through its handler, state update, feedback, and any
ActiveX/file/process boundary.

## HTA Runtime Contract

- Both entry points run as Windows HTA applications using MSHTML/JScript, not a modern
  browser application.
- Preserve `IE=edge`, UTF-8 handling, existing global function conventions, script load
  order, DOM IDs, inline handlers, and ActiveX integration unless the task explicitly
  covers a coordinated migration.
- Default to syntax and APIs already proven in neighboring files: `var`, `function`,
  classic loops, and established DOM helpers. Do not introduce `let`, `const`, arrow
  functions, classes, template literals, optional chaining, `async`/`await`, `Promise`,
  `fetch`, ES modules, or bundler/transpiler assumptions.
- Do not introduce React, Tailwind, CSS custom properties, CSS Grid, external web fonts,
  CDN assets, or a new UI dependency. A technique already used by the HTA, such as the
  existing flex layout in `server_menu.hta`, may be retained but still requires actual
  HTA verification after behavior or layout changes.
- Prefer local assets and native controls. Do not force SVG or another format merely
  because generic web guidance recommends it; use a format already proven by the target
  HTA and keep structural icons consistent and non-emoji.
- Never treat Chromium, a static HTML preview, Node syntax parsing, or a screenshot as
  proof that JScript, ActiveX, file access, or process control works in `mshta.exe`.

## Review Priorities

Evaluate in this order:

1. **Operator safety and task completion** — the primary task is obvious; start, stop,
   restart, build, delete, overwrite, and bulk actions are clearly named, separated from
   routine actions, and confirmed when consequence warrants it.
2. **Keyboard and focus** — logical tab order, visible focus on every interactive control,
   no hover-only action, clear escape/cancel path, and focused controls not hidden by
   sticky or scrolling regions. Never remove outlines without an equivalent replacement.
3. **Forms and feedback** — visible labels, grouped fields, required/read-only/disabled
   states, specific validation near the field, clear recovery guidance, and busy/success/
   failure feedback for long or external actions.
4. **Hierarchy and navigation** — active location is unmistakable; page title, section,
   toolbar, primary action, secondary action, and destructive action form a predictable
   hierarchy; navigation wording remains concise and consistent.
5. **Dense data usability** — readable headers and units, stable selection, useful empty/
   loading/error states, bounded scrolling, and a way to inspect values hidden by
   ellipsis. Do not use color as the only status or selection signal.
6. **Readability and resilience** — sufficient text/control contrast, consistent spacing,
   Vietnamese labels and long IDs do not clip without disclosure, and the layout works at
   the declared minimum window and representative larger sizes. Do not impose mobile
   breakpoints or touch-target rules on this desktop operator tool.
7. **Motion and polish** — use restrained state feedback that does not shift layout or
   block input. Motion is optional and must never carry required meaning.

For a full review or before delivering implemented UI changes, read
[references/review-checklist.md](references/review-checklist.md).

## Workflow

1. State whether the request is a read-only review or an authorized implementation.
2. Identify the operator goal, affected controls, dangerous actions, runtime boundaries,
   and the smallest complete file set.
3. Inspect a neighboring working pattern before proposing a new component or style.
4. For reviews, report prioritized findings with file and line evidence, runtime impact,
   and a concrete HTA-compatible correction; do not edit.
5. For implementation, make the smallest coherent change, reuse existing helpers and
   selectors, and avoid unrelated visual restyling.
6. Verify the source structure, then the actual behavior appropriate to the change.

## Verification

- After any modular admin-menu change, run:

  ```powershell
  node tools/check_admin_data_menu.js
  ```

  This checks referenced assets, JavaScript parsing, duplicate globals/functions, tab
  registration, view markup, DOM IDs, and inline handler ownership. It does not prove
  HTA/JScript or ActiveX runtime compatibility.
- Open the actual affected HTA after interaction, focus, file/process, ActiveX, window
  sizing, or behavior changes. Exercise keyboard-only navigation and the modified success,
  failure, disabled, and cancellation paths where applicable.
- A static browser preview may supplement visual inspection, but report it separately and
  never substitute it for HTA evidence.
- Report what was checked, what was exercised in the actual HTA, and any remaining manual
  verification. Do not claim runtime success from source inspection alone.

## Provenance

This repository-owned wrapper adapts selected UI hierarchy, accessibility, interaction,
form-feedback, layout-resilience, typography, color, and data-interface guidance from
UI/UX Pro Max at commit `314307f156aeab0c6b567bbaa1ce4e7aabd5a636`.
The upstream package is not installed or executed. See `.agents/THIRD_PARTY_NOTICES.md`
and root `THIRD_PARTY_SKILLS.md` for source and license records.
