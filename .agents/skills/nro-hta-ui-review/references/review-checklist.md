# NRO HTA UI Review Checklist

Read this checklist for a full UI review or before delivering implemented HTA UI changes.
Apply only items relevant to the affected flow and report non-applicable areas explicitly
when their omission could be mistaken for incomplete verification.

## Runtime and Architecture

- [ ] The change stays within `admin_data_menu.hta`, `server_menu.hta`, or the intended
  `admin_data_menu/` module.
- [ ] New or changed JScript uses syntax and APIs already proven in the target HTA.
- [ ] No React, Tailwind, ES module, CSS custom property, Grid, bundler, transpiler, CDN,
  external font, or new UI dependency was introduced.
- [ ] Existing DOM IDs, globals, inline handler names, and script load order remain valid.
- [ ] A new admin tab has one root `.panel`, one matching `RegisterTab(...)` entry,
  navigation metadata, lifecycle callbacks, and a script loaded before `core/app.js`.
- [ ] UTF-8 Vietnamese text, ampersands/entities, file paths, and command quoting remain
  intact.

## Operator Safety

- [ ] The main action is visually clear and secondary actions are subordinate.
- [ ] Start, stop, restart, build, delete, overwrite, import, and bulk operations are
  named by consequence rather than vague labels such as “OK” or “Run”.
- [ ] Dangerous actions are visually and spatially separated from routine actions.
- [ ] Confirmation is required when an accidental action would stop a service, destroy
  data, overwrite a file, or create a hard-to-reverse state.
- [ ] Progress/busy state prevents duplicate submission without trapping the operator.
- [ ] Success and failure feedback identify what happened and what the operator can do
  next; errors do not expose secrets or raw credentials.

## Keyboard, Focus, and Semantics

- [ ] Every action is reachable and operable by keyboard; hover is never the only path.
- [ ] Tab order follows the visible task flow and does not jump into hidden panels.
- [ ] Focus is clearly visible against every surface. A global `outline: none` is a
  finding unless all affected controls receive an equivalent visible focus treatment.
- [ ] Opening/closing a modal, changing a tab, or reporting multiple validation errors
  moves or restores focus predictably when supported by the HTA runtime.
- [ ] Buttons are buttons, field labels identify their controls, and read-only state is
  distinguishable from disabled state.
- [ ] Status, selection, errors, and required fields use text or shape in addition to
  color.

## Forms and Feedback

- [ ] Labels remain visible after a value is entered; placeholders are hints, not labels.
- [ ] Related fields are grouped and ordered by the operator's task.
- [ ] Validation occurs after meaningful input, names the invalid field/value constraint,
  and supplies a recovery action.
- [ ] Errors appear near the affected field; a long form also provides a clear summary or
  focuses the first invalid field when runtime support permits.
- [ ] Disabled controls look unavailable and do not execute; read-only values remain
  selectable/copyable when useful.
- [ ] Loading, empty, no-results, success, timeout, and failure states are intentional.
- [ ] Toasts do not hide essential information or become the only record of a failed
  operation.

## Navigation and Dense Data

- [ ] The active tab/location is visible without relying on color alone.
- [ ] Navigation labels use one vocabulary and remain understandable without icons.
- [ ] Frequently used actions remain reachable without excessive scrolling; rare or
  dangerous actions do not crowd the primary toolbar.
- [ ] Tables have readable headers, units, consistent alignment, stable row selection,
  and an explicit empty/error state.
- [ ] Long names, Vietnamese labels, paths, IDs, and numeric values wrap or truncate
  deliberately; ellipsis has a tooltip, detail pane, copy action, or other disclosure.
- [ ] Scroll ownership is clear. Nested panes do not hide focused controls, action bars,
  table headers, or feedback.

## Visual Hierarchy and Resilience

- [ ] Page title, context/subtitle, section headings, primary action, secondary actions,
  and destructive actions are visually distinct.
- [ ] Spacing and control heights reuse the target surface's existing rhythm; dense admin
  UI is not converted into a mobile layout.
- [ ] Normal text remains readable at the current desktop size and contrast is checked on
  the actual background; low-contrast gray-on-gray text is avoided.
- [ ] Functional icons and control boundaries are visible; structural icons use one local,
  HTA-proven visual language rather than emoji.
- [ ] The layout is checked at its declared minimum window size and a representative larger
  size, with no inaccessible clipped actions or accidental horizontal scrolling.
- [ ] State feedback does not shift surrounding layout, block input, or depend on animation
  completion for correctness.

## Evidence Before Delivery

- [ ] `node tools/check_admin_data_menu.js` passes after any modular admin-menu change.
- [ ] The actual affected `.hta` was opened for behavior, keyboard/focus, ActiveX,
  file/process, or window-layout changes.
- [ ] Success, failure, disabled, and cancellation paths relevant to the change were
  exercised.
- [ ] Static preview evidence is labeled as visual-only and not presented as HTA proof.
- [ ] The completion report separates verified behavior, untested manual behavior, and
  residual compatibility or operator-safety risk.
