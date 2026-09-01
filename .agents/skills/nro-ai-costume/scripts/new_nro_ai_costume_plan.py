#!/usr/bin/env python3
"""Create a non-destructive AI motion plan for one full-body NRO costume."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


SHEETS = (
    {
        "key": "guard",
        "file": "guard.png",
        "columns": 2,
        "rows": 2,
        "cells": (
            (0, "Guard, hit, or compact recoil", "Đỡ / chịu đòn"),
            (15, "Defensive impact or block", "Phòng thủ"),
        ),
    },
    {
        "key": "idle",
        "file": "idle.png",
        "columns": 1,
        "rows": 1,
        "cells": ((1, "Neutral side-facing standing pose", "Đứng yên"),),
    },
    {
        "key": "air",
        "file": "air.png",
        "columns": 2,
        "rows": 2,
        "cells": (
            (2, "Airborne, jump, or recoil", "Bay / nhảy"),
            (7, "Horizontal flight; confirm in the client", "Bay thực tế BODY[7] cần xác nhận"),
            (13, "Fall, knockback, or alternate flight", "Ngã / bay thay thế"),
        ),
    },
    {
        "key": "run",
        "file": "run.png",
        "columns": 3,
        "rows": 2,
        "cells": (
            (3, "Movement beat 1", "Chạy 1"),
            (4, "Movement beat 2", "Chạy 2"),
            (5, "Movement beat 3", "Chạy 3"),
            (6, "Movement beat 4", "Chạy 4"),
            (16, "Dash or motion blur", "Motion blur / dash"),
        ),
    },
    {
        "key": "skill",
        "file": "skill.png",
        "columns": 3,
        "rows": 2,
        "cells": (
            (8, "Charge, landing, or skill preparation", "Tụ lực / đáp đất"),
            (9, "Skill pose", "Kỹ năng"),
            (10, "Attack or skill pose 1", "Đánh / kỹ năng"),
            (11, "Attack or skill pose 2", "Đánh / kỹ năng"),
            (12, "Attack or skill pose 3", "Đánh / kỹ năng"),
            (14, "Large skill or finisher", "Kỹ năng lớn"),
        ),
    },
)


def slug(value: str) -> str:
    normalized = value.strip().lower()
    if not re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*", normalized):
        raise argparse.ArgumentTypeError("slug must use lowercase letters, numbers, and single hyphens")
    return normalized


def prompt_for_sheet(sheet: dict[str, object], chroma_key: str) -> str:
    cells = sheet["cells"]
    assert isinstance(cells, tuple)
    states = "; ".join(
        f"cell {index + 1}: {role}" for index, (_, role, _) in enumerate(cells)
    )
    unused = int(sheet["columns"]) * int(sheet["rows"]) - len(cells)
    unused_note = (
        f" The remaining {unused} cell(s) must contain only the flat background." if unused else ""
    )
    return f"""# {sheet['key']} sheet prompt

Use the supplied baseline image as the exact character identity source. Read
`../identity-lock.md` before generating.

Use the separately supplied in-game style reference for NRO chibi proportion, apparent x1
height, outline weight, foot line, and shadow centre. Do not copy realistic/adult body
proportions from the identity art. Generate `idle` first and do not generate another sheet
until the keyed x1 idle pilot is approved in `motion-plan.json`.

Generate a {sheet['columns']}x{sheet['rows']} grid of separate full-body game sprites.
{states}.{unused_note}

Keep the same source-facing direction, palette, clothing, facial features, weapon ownership,
apparent character scale, body centre, and foot/bottom baseline in every cell. Each sprite is
fully visible, centred in its equal cell, and surrounded by generous empty gutters. Use a flat
opaque {chroma_key} background. Do not output transparency, checkerboards, labels, borders,
ground shadows, detached fragments, or anything touching a cell edge. Do not change the
character design or target proportion between cells.
"""


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project-dir", type=Path, required=True)
    parser.add_argument("--slug", type=slug, required=True)
    parser.add_argument("--name", required=True)
    parser.add_argument("--description", required=True)
    parser.add_argument("--chroma-key", default="#ff00ff")
    parser.add_argument("--force", action="store_true", help="Replace only files under ai-motion.")
    args = parser.parse_args()

    project_dir = args.project_dir.resolve()
    if project_dir.name != args.slug:
        raise SystemExit("--project-dir must end with the supplied --slug")
    motion_dir = project_dir / "ai-motion"
    if motion_dir.exists() and not args.force:
        raise SystemExit(f"Refusing to overwrite existing plan: {motion_dir}. Use --force after review.")

    prompt_dir = motion_dir / "prompts"
    prompt_dir.mkdir(parents=True, exist_ok=True)
    (project_dir / "source").mkdir(parents=True, exist_ok=True)

    body_slots: list[dict[str, object]] = []
    sheets: list[dict[str, object]] = []
    for sheet in SHEETS:
        sheet_data = {
            "key": sheet["key"],
            "file": sheet["file"],
            "columns": sheet["columns"],
            "rows": sheet["rows"],
        }
        sheets.append(sheet_data)
        for source_index, (slot, observed, group) in enumerate(sheet["cells"]):
            body_slots.append(
                {
                    "slot": slot,
                    "sheet": sheet["key"],
                    "sourceIndex": source_index,
                    "observed": observed,
                    "suggestedGroup": group,
                    "offsetAdjustment": [0, 0],
                    "horizontalFlip": False,
                    "status": "draft-needs-visual-and-client-review",
                }
            )
        (prompt_dir / f"{sheet['key']}.md").write_text(
            prompt_for_sheet(sheet, args.chroma_key), encoding="utf-8"
        )

    body_slots.sort(key=lambda row: int(row["slot"]))
    plan = {
        "schemaVersion": 2,
        "status": "draft",
        "mode": "full-body",
        "slug": args.slug,
        "name": args.name,
        "description": args.description,
        "baseline": {
            "path": "baseline.png",
            "sourceFacing": "UNCONFIRMED",
            "identityLock": "identity-lock.md",
        },
        "artDirection": {
            "identityReference": "baseline.png",
            "styleReference": "",
            "targetStyle": "NRO chibi",
            "targetX1VisualHeight": None,
            "pilotIdle": {
                "status": "pending",
                "previewPath": "",
                "approvedBy": "",
                "notes": "Generate, chroma-key, and review idle at actual x1 size before other sheets.",
            },
        },
        "grounding": {
            "reference": "",
            "targetBottomRelative": None,
            "targetFootCenterX": None,
            "status": "pending",
        },
        "generation": {
            "provider": "built-in-image-gen",
            "chromaKey": args.chroma_key,
            "maximumCellsPerSheet": 6,
            "backgroundMode": "flat-opaque-chroma",
            "alphaKeyVerified": False,
            "alphaSpillVerified": False,
            "rule": "Generate every pose from the shared baseline; regenerate visual failures rather than warping them.",
        },
        "sourceDirectory": "source",
        "sheets": sheets,
        "bodySlots": body_slots,
        "head": {"mode": "transparent-placeholder", "slotCount": 3},
        "leg": {"mode": "transparent-placeholder", "slotCount": 14},
        "requiredCompanionSources": ["source/item-icon.png", "source/profile.png"],
        "companionSources": {
            "itemIcon": "source/item-icon.png",
            "profile": "source/profile.png",
        },
        "deployment": {
            "state": "not-authorized",
            "requires": ["live-id-audit", "visual-review", "client-slot-review", "explicit-user-deployment-request"],
        },
    }
    (motion_dir / "motion-plan.json").write_text(
        json.dumps(plan, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    (motion_dir / "identity-lock.md").write_text(
        """# Identity lock\n\n- Baseline path: `baseline.png`\n- In-game NRO style reference path: \n- Source-facing direction: \n- Character silhouette and proportions: \n- Target NRO chibi head-to-body ratio and intended x1 visual height: \n- Palette and outline style: \n- Head/hair/accessories that must not change: \n- Weapon/prop and its screen-space owner: \n- Other hand/limb and visible anchor: \n- Safe chroma key after checking all subject colours: \n- Verified reference costume/client screenshot for foot and shadow alignment: \n- Target bottomRelative and foot centre at x1: \n\nDo not generate action sheets until the keyed x1 idle pilot is approved.\n""",
        encoding="utf-8",
    )
    (motion_dir / "next-steps.md").write_text(
        """# Next steps\n\n1. Place the identity reference at `ai-motion/baseline.png`, add a known-good in-game style reference, and fill `identity-lock.md`.\n2. Generate only `prompts/idle.md` on flat chroma. Key it to alpha, render an actual-size x1 preview beside the style reference, and approve `artDirection.pilotIdle` plus `grounding`.\n3. Generate the remaining prompts, save accepted sheets in `source/`, and verify alpha/spill.\n4. Update `motion-plan.json`, including `offsetAdjustment`; after visual review set `status` to `ready-for-builder`, then run the validator.\n5. Audit IDs and use `emit_full_body_builder.py`; it creates `build_assets.py` but never runs it.\n""",
        encoding="utf-8",
    )
    print(f"Created NRO AI costume draft: {motion_dir}")


if __name__ == "__main__":
    main()
