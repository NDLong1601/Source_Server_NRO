# Build and deployment verification — Cải trang Levi Ackerman

Chibi v2 alignment/profile/icon revision was built and deployed on 2026-09-01 after the live ID audit. The database rows, x1-x4 assets, `data/update_data/part`, cache version, release JAR, and running server now use this build.

## Output package

- Item: `2247`; HEAD/BODY/LEG parts: `2198/2199/2200`.
- BODY images: `26555..26571`; transparent placeholder: `26572`.
- Inventory icon: `26573`; profile avatar: `26574`.
- `80` PNGs were generated at `data/icon/x1..x4/` (20 IDs × 4 scales).
- [Manifest](manifest.json), [QA report](qa-report.json), [install SQL](install.sql), [rollback SQL](rollback.sql), and generated [builder](build_assets.py) are present.

## Passed static gates

- HEAD/BODY/LEG have exactly `3/17/14` entries.
- Every BODY frame is sourced from a non-empty cell with `edgeTouches=[]`.
- Each x2/x3/x4 image has exactly 2/3/4 times its x1 dimensions and transparent corner border.
- Profile `26574` is `64×64`; its alpha bbox reaches the bottom row.
- Inventory icon `26573` is `25×22` with a transparent border.
- Idle BODY[1] uses `[26556,-7,-47]`; its x1 height is `65`, so `bottomRelative=18`, matching the verified NRO ground baseline.
- `install.sql` and `rollback.sql` are transactional and contain no `item_shop` `INSERT` or `DELETE` statement.

## Live verification

1. Live audit passed with 0 errors and 0 warnings for item `2247`.
2. Release `20.jar` was rebuilt; `vsData=72` and `vsItem=62`.
3. Server startup loaded 2,201 parts, 2,249 items and 11 gift-box configs; port `14445` is listening and the error log is empty.
4. Protocol probes matched the deployed PNG SHA-256 for idle `26556`, item icon `26573`, and profile `26574` at x1-x4.
5. Final acceptance still requires confirming the corrected idle ground contact, icon and profile in a fully restarted client.
