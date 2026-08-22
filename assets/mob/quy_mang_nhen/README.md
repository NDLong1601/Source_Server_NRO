# Quỷ Mạng Nhện (Mob 119)

- Ground mob (`TYPE = 1`), HP 600,000, level 12 on map 187.
- `source/` preserves all supplied transparent action artwork. The runtime
  atlas uses stand, crawl, web attack, hurt and death poses.
- `atlases/` contains the generated x1-x4 indexed-color atlases.
- `preview-actions-x3.png` is the visual action check.
- Runtime payloads are written to `data/mob/x1/119` through `data/mob/x4/119`.
- Each payload follows client command 11 exactly: read type, EffectData byte
  array, PNG byte array and final `typeData` byte.

Rebuild:

```powershell
python ".agents\skills\nro-create-mob\scripts\build_mob_payload.py" build `
  --manifest "assets\mob\quy_mang_nhen\mob.json" --project-root .
```
