# Admin Data Menu

- `css/`: styles loaded in base, layout, components and responsive order.
- `js/core/`: HTA runtime, tab registry, shared utilities, UI helpers and application lifecycle.
- `js/components/`: catalogs and renderers shared by multiple tabs.
- `js/tabs/`: state and actions owned by each tab.
- `views/`: one complete panel per UTF-8 HTML partial.

`admin_data_menu.hta` is the entry point and must stay next to this directory.

Run `node tools/validation/check_admin_data_menu.js` from the project root after moving a view or script.

## Add a tab

1. Add one root `.panel` file to `views/`.
2. Add the tab state/actions to `js/tabs/` and call `RegisterTab(...)` at the end.
3. Add the tab script to `admin_data_menu.hta` before `core/app.js`.
4. Add its navigation button and run the checker.

## Clan configuration

The sidebar opens **Cấu hình bang** directly. The economic report is no longer
displayed or requested by this tab; its backend is retained for other consumers.
The catalog contains 176 supported settings (previously 153), including the tree
level cap, 19 cap-specific upgrade schedules and appearance resource settings.

- Search spans all groups and accepts Vietnamese with or without accents.
- Booleans, seven-field shop items, per-level schedules and RGB colors have dedicated
  editors. Drafts survive group/search changes; save and discard apply to the selected
  setting only. Reload warns before discarding drafts. Closing the HTA loses drafts.
- Saved/default values, units, bounds, file and apply scope are shown next to the form.
  Input is checked in JScript and again by the PowerShell whitelist. A resource-prefix
  or resource-level change requires the matching PNGs in every x1–x4 directory.
- The schedule ending at level N is used only when max_tree_level=N. Image limits and
  appearance version do not resize PNGs. Saving never restarts the server automatically.

Verification (no Java build, database writes or server restart required):

```powershell
node tools/validation/check_admin_data_menu.js
node tools/tests/ClanConfigUiTest.js
powershell -NoProfile -ExecutionPolicy Bypass -File tools/tests/ClanPhase5EAdminBackendTest.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools/tests/ClanConfigHtaRuntimeTest.ps1
```

The HTA runtime test uses the real MSHTML shell/view/scripts and PowerShell backend,
with all writes redirected to marked copies under logs/clan_config_hta_test_*.
It retains a result log and visual-only HTML snapshot. It does not run the production
database dispatcher or prove a physical keyboard session; check those manually when
deploying. A hidden HTA can ignore resizeTo, which the report explicitly records.

Workflow: nro-hta-ui-review owns the implementation. The supplementary security,
test-driven-development and systematic-debugging skills are adapted to legacy JScript,
the existing PowerShell tests and isolated local fixtures, without a new framework,
Java build, production database mutation, client-project access or automatic restart.
