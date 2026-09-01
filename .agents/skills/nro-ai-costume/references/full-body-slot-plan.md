# Draft slot plan for an NRO full-body costume

This is an authoring plan, not an authoritative client state table. It is based on the full-body sets in `costume-tools/projects/tokitou-muichirou`, `shinazugawa-sanemi`, and `tengen-uzui`. Confirm the final mapping in the actual client before deployment.

## Stage 0: approve art direction and idle at x1

Before generating any multi-cell action sheet:

1. Assign an identity reference and a separate known-good NRO style/client reference.
2. Generate only `idle.png` on a flat opaque chroma background. Do not request a transparent
   or checkerboard output from the image model.
3. Key the background and inspect alpha at x1 and x4. Reject magenta/green spill, baked
   checkerboard pixels, opaque corner pixels, clipped hair/boots and low-alpha debris.
4. Compile the idle at actual x1 size beside the style reference. Match apparent height,
   head-to-body ratio, line weight and silhouette; identity art does not authorize realistic
   adult proportions when the target client is chibi.
5. Measure `bottomRelative = dy + imageHeightX1` and foot centre against a verified costume or
   client screenshot. Mark the pilot and grounding gates approved before generating other sheets.

| BODY slot | Draft source sheet | Draft role | Required check |
|---:|---|---|---|
| 0 | `guard[0]` | Guard, hit, or compact recoil | Body remains readable at x1 |
| 1 | `idle[0]` | Neutral standing pose | Client idle and default facing |
| 2 | `air[0]` | Airborne, jump, or recoil | Flight/fall state |
| 3–6 | `run[0..3]` | Four movement beats | Left/right run and baseline |
| 7 | `air[1]` | Horizontal flight | Confirm this slot in client first |
| 8 | `skill[0]` | Charge, landing, or skill preparation | Landing/charge animation |
| 9–12 | `skill[1..4]` | Attack or skill silhouettes | Weapon and limbs stay attached |
| 13 | `air[2]` | Fall or alternate flight | Client fall/knockback state |
| 14 | `skill[5]` | Large skill or finisher | Full effect remains inside frame |
| 15 | `guard[1]` | Block or defensive impact | Body and effect baseline |
| 16 | `run[4]` | Dash or motion blur | Client dash/motion state |

## Suggested source sheets

Generate each sheet with a common character reference and the same source-facing direction. Use a flat chroma key that is absent from the character and leave large empty gutters between cells.

| Sheet | Grid | Cells used | Purpose |
|---|---:|---:|---|
| `idle.png` | 1×1 | 1 | One neutral full-body reference pose |
| `guard.png` | 2×2 | 2 | Guard/recoil and defensive impact |
| `air.png` | 2×2 | 3 | Airborne, horizontal flight, fall |
| `run.png` | 3×2 | 5 | Four locomotion beats and dash |
| `skill.png` | 3×2 | 6 | Charge plus five attack/skill poses |

The unused cells in a grid must stay entirely chroma background; they are never mapped to BODY.

## Prompt constraints

Every sheet prompt needs these clauses, plus a sheet-specific action description:

```text
Use the supplied baseline image as the exact character identity source.
Use the supplied in-game style reference for chibi proportion, x1 scale, outline weight,
foot line and shadow centre. Do not copy realistic/adult proportions from the identity art.
Keep the same side-facing direction, palette, clothing, facial features, weapon ownership,
apparent character scale, body centre, and foot/bottom baseline in every cell.
Draw complete full-body sprites, each centred in equal cells with generous empty gutters.
Use a flat opaque <CHROMA_KEY> background, never transparency or a checkerboard; no labels, grid lines, shadows, detached fragments,
or content touching a cell edge.
```

When a weapon or distinctive limb is involved, name it in screen space in every prompt, for example: `the sword remains in the screen-right hand; the screen-left hand never holds it`.
