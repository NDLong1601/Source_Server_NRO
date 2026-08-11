# Admin Data Menu

- `css/`: styles loaded in base, layout, components and responsive order.
- `js/core/`: HTA runtime, tab registry, shared utilities, UI helpers and application lifecycle.
- `js/components/`: catalogs and renderers shared by multiple tabs.
- `js/tabs/`: state and actions owned by each tab.
- `views/`: one complete panel per UTF-8 HTML partial.

`admin_data_menu.hta` is the entry point and must stay next to this directory.

Run `node tools/check_admin_data_menu.js` from the project root after moving a view or script.

## Add a tab

1. Add one root `.panel` file to `views/`.
2. Add the tab state/actions to `js/tabs/` and call `RegisterTab(...)` at the end.
3. Add the tab script to `admin_data_menu.hta` before `core/app.js`.
4. Add its navigation button and run the checker.
