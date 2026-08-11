# Tab views

Each UTF-8 file contains one complete `.panel` element. `core/app.js` loads the views
synchronously before combo initialization and the first data request.

These files are HTML fragments, not standalone documents. The project `.htmlhintrc`
therefore disables only `doctype-first` while keeping structural validation enabled.
