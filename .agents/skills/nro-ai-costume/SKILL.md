---
name: nro-ai-costume
description: Create Teamobi NRO full-body costume assets from a reference image or an AI-generated character, producing controlled action sheets that can be compiled into the required HEAD/BODY/LEG data. Use for new AI-assisted NRO costumes and animation-frame planning; do not use for mobs or ordinary item icons.
---

# NRO AI Costume

Create an inspectable, full-body costume pipeline for Teamobi. The visual phase uses separate identity and in-game style references; deterministic tooling then keys, splits, aligns, renders, and packages the assets for the NRO client.

## Choose the architecture

Use **full-body BODY** by default: keep the character, hair, weapons, and attached effects in BODY frames, and use one genuine transparent PNG for every HEAD and LEG slot. This avoids destructive splitting through a cape, weapon, or effect. Use separate HEAD/BODY/LEG art only when their layers have already been authored cleanly.

Read [the slot plan](references/full-body-slot-plan.md) before drafting generated frames. Also read the project `nro-create-costume` skill's data-format reference before creating any DATA, and its full-body workflow before compiling assets.

## Pilot gate before action sheets

Do not generate the full motion set from an adult/anime identity reference alone. Use two
explicit roles:

- **Identity reference:** face, hair, clothes, palette, insignia and owned props.
- **In-game style reference:** NRO head-to-body ratio, apparent x1 height, outline weight,
  foot line and shadow centre. Prefer a known-good costume already verified in the same client.

Generate only the neutral idle first. Convert it from a flat chroma background, compile an x1
preview, and compare it beside the style reference or an in-game screenshot. Record the target
`bottomRelative` and foot centre. Do not generate guard/air/run/skill sheets until this pilot is
approved. A large source image looking good at 100% is not approval.

## Workflow

1. Inspect the identity image and a known-good in-game style reference. Record facing, palette,
   silhouette, distinctive clothing, weapon ownership, target chibi proportion, intended x1
   height, ground baseline, foot centre and a safe chroma-key colour. Both reference roles must
   be visible to image generation.
2. Create a draft package; it contains a 17-slot plan and one prompt for each small state sheet:

   ```powershell
   python .agents/skills/nro-ai-costume/scripts/new_nro_ai_costume_plan.py \
     --project-dir costume-tools/projects/<slug> \
     --slug <slug> --name "Cải trang ..." --description "..."
   ```

3. Complete `ai-motion/identity-lock.md`. Generate only `idle` on the chosen **flat opaque
   chroma** background. Never request transparency or a checkerboard from image generation: a
   visible checkerboard may be baked RGB, not alpha. Key the chroma deterministically, inspect
   hair/clothing edges for colour spill, render x1, and complete the pilot/style/grounding gates
   in `motion-plan.json`.
4. After pilot approval, generate the remaining sheets. Keep the sheet geometry, source-facing
   direction, stable feet line, scale, generous gutter, and chosen chroma. Generate no more than
   six cells per sheet. Reject clipped frames, changed identity/proportion, swapped weapon hand,
   baked background, halo, or scale drift; regenerate instead of locally warping the pose.
5. Inspect all sheets and update the plan with the chosen cell, observed pose, group,
   `offsetAdjustment`, and any one-time mirror per BODY slot. Default slot semantics are only a
   draft; client observation decides final mapping.
6. After visual review, set schema-v2 plan `status` to `ready-for-builder`, then validate it
   before using image IDs. The validator must fail closed while any pilot/style/ground/alpha
   gate remains pending:

   ```powershell
   python .agents/skills/nro-ai-costume/scripts/validate_nro_ai_costume_plan.py \
     --plan costume-tools/projects/<slug>/ai-motion/motion-plan.json
   ```

7. Audit live IDs before allocating new IDs. Only then create a builder entry point; this step does not run the builder:

   ```powershell
   python .agents/skills/nro-ai-costume/scripts/emit_full_body_builder.py \
     --plan costume-tools/projects/<slug>/ai-motion/motion-plan.json \
     --item-id <itemId> --head-part-id <headPartId> \
     --first-body-image-id <firstImageId> \
     --transparent-image-id <transparentImageId> \
     --item-icon-id <itemIconId> --profile-icon-id <profileIconId>
   ```

8. Put the selected sheets and the separately assigned item/profile sources in the plan's
   `sourceDirectory`. Run the emitted builder, inspect anchor/x1/contact previews and QA, then
   run `audit_costume.ps1` with `-ProjectRoot` set to the repository root containing
   `Config.properties`. Do not import a live DB, bump versions, build, or restart until the user
   has requested deployment.

## Non-negotiable checks

- Keep exactly `HEAD=3`, `BODY=17`, and `LEG=14`; all DATA elements are `[imageId, dx, dy]`.
- Use 17 newly allocated BODY image IDs for a new full-body costume. Reuse is possible only after client/cache review; do not begin with reused IDs.
- Never inherit dx/dy or a ground baseline from a broken/unverified costume merely to preserve
  its screen position. Match a verified client reference first; store `bottomRelative` and foot
  centre in the plan.
- `BODY[7]` is frequently the visible flight slot in this client, but this must be confirmed in the target client. Do not infer a slot from its name or position alone.
- Render the four x1–x4 PNG scales from the accepted source. Do not create large scales by enlarging x1.
- Treat item icon and profile as separate roles. Default repository canvases are `25x22`
  centred for the inventory icon and `64x64` bottom-aligned for the profile; user-provided files
  keep their assigned roles.
- A static contact sheet is not acceptance: inspect client idle, left/right run, flight/fall, landing, attacks, skill/guard, shadow alignment, inventory icon, and profile after cache refresh.
