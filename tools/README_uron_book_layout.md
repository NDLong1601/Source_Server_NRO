# Uron book display contract

Book appearance is owned by server PNG assets; tab placement and item order are
owned by `item_shop`. Do not patch client item names, icon scale, or local caches.

## Icon size

The approved reference is Liên hoàn, item 481 / icon 26512. Its original source
is `data/icon/orig/26512.png`; the solid book bounds (alpha >= 128) are 50x52.
The reviewed x1 appearance occupies about 18x19 pixels on a 24x24 canvas.

All book icons use the 24/48/72/96 canvas sizes at x1/x2/x3/x4. Normalize the
**visible book bounds** into an 18x19 logical-pixel region and center them.
Preserve aspect ratio and the soft alpha edge. A narrow book can have a slightly
narrower bounding box; resampling can move the solid alpha boundary by one pixel.
Resizing the entire PNG without accounting for its transparent margins is not
sufficient: the legacy books filled the canvas while newer books had margins.

`normalize_uron_book_icons.ps1` stages all 174 shop icons at all four scales. It
uses `orig/` where available and a pinned Git x4 original otherwise. It never
resamples an already resized live PNG. It saves source copies, all before/after
PNGs, a comparison sheet and a report. Validate the sheet before deployment.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/normalize_uron_book_icons.ps1 `
  -OutputDirectory C:/path/to/new-staging-directory
```

The `-Apply` switch deploys after all image and live-file conflict checks pass.
Use a new staging directory for every run; keep the previous backup until the
release has been checked. Review the 174-icon scope guard when adding books.

`DataGame.fileVersion` uses `CRC32(PNG) % 126 + 1`, not a full checksum. The
normalizer guarantees that each edited PNG gets a different version byte from
its previous live file. In a collision it inserts a harmless PNG text chunk.
Restart the server after deployment to rebuild cached version arrays. No item
version bump is needed if template IDs, text and icon mappings did not change.

## Ordering across races

Egg books (items 335..341) use icons 26519..26525, while Teleport books
(items 488..494) use icons 26491..26497, preserving level 1..7 order.
The source folders were initially attributed to the opposite items. Correct
the template mapping, not the PNGs; changing icon IDs requires an item-template
version bump (57 for this correction), not new image-cache versions.

`ShopDAO` loads `create_time DESC, id ASC`. The second key prevents random order
when rows have equal timestamps. Uron and the skill-learning shop then filter
this common list by player gender, retaining its order.

The seven shared Khiên năng lượng books (434..440, gender 3) must follow **all**
race-specific special books. Ngục Giam Lá Chắn (2233..2239, gender 0) is directly
before Khiên. Placing Khiên after only the Earth groups makes it first for Namec
and Xayda. `20260831_standardize_uron_book_layout.sql` anchors both groups after
the oldest other special book, with unique descending timestamps for levels 1..7.
It is safe to reapply and does not modify prices, options or race eligibility.

Run `tools/verify_uron_book_order.ps1` to check all nine tab/race combinations.
The icon wire check validates the real server's version array and image bytes:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/codex_protocol_probe.ps1 `
  -Zoom 2 -IconId 14643 -IconManifest C:/path/to/staging/report.csv
```
