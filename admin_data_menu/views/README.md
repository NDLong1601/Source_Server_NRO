# Tab views

Each UTF-8 file contains one complete `.panel` element. `core/app.js` loads the views
synchronously before combo initialization and the first data request.

These files are HTML fragments, not standalone documents. The project `.htmlhintrc`
therefore disables only `doctype-first` while keeping structural validation enabled.

Use explicit HTML5 element types, omit the XHTML slash on void elements, and keep
presentation rules in `../css/`. Run `node tools/fix_admin_views.js` from the project
root to normalize mechanical markup issues; the main checker also verifies this.
