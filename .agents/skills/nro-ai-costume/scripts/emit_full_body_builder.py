#!/usr/bin/env python3
"""Emit, but never execute, a Teamobi full-body costume builder from a reviewed plan."""

from __future__ import annotations

import argparse
import importlib.util
import json
from pathlib import Path
from typing import Any


def load_validator() -> Any:
    path = Path(__file__).with_name("validate_nro_ai_costume_plan.py")
    spec = importlib.util.spec_from_file_location("nro_ai_costume_validator", path)
    if spec is None or spec.loader is None:
        raise RuntimeError("Cannot load plan validator")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def positive(value: str) -> int:
    parsed = int(value)
    if parsed <= 0:
        raise argparse.ArgumentTypeError("must be a positive integer")
    return parsed


def python_string(value: object) -> str:
    return repr(str(value))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--plan", required=True, type=Path)
    parser.add_argument("--item-id", required=True, type=positive)
    parser.add_argument("--head-part-id", required=True, type=positive)
    parser.add_argument("--first-body-image-id", required=True, type=positive)
    parser.add_argument("--transparent-image-id", required=True, type=positive)
    parser.add_argument("--item-icon-id", required=True, type=positive)
    parser.add_argument("--profile-icon-id", required=True, type=positive)
    parser.add_argument("--shop-id", type=positive)
    parser.add_argument("--tab-id", type=positive)
    parser.add_argument("--shop-cost", type=positive)
    parser.add_argument("--overwrite", action="store_true")
    args = parser.parse_args()

    shop_values = (args.shop_id, args.tab_id, args.shop_cost)
    if any(value is None for value in shop_values) and any(value is not None for value in shop_values):
        raise SystemExit("--shop-id, --tab-id, and --shop-cost must be supplied together")

    plan_path = args.plan.resolve()
    plan = json.loads(plan_path.read_text(encoding="utf-8"))
    validator = load_validator()
    errors = validator.validate(plan)
    if errors:
        raise SystemExit("Plan is invalid:\n- " + "\n- ".join(errors))
    for warning in validator.warnings(plan):
        print(f"Warning: {warning}")
    if plan.get("status") == "draft":
        print("Warning: plan is still draft; verify every chosen source cell before running the generated builder.")

    project_dir = plan_path.parent.parent
    source_directory = str(plan.get("sourceDirectory", "source"))
    source_dir = project_dir / source_directory
    expected_source_files = [source_dir / str(sheet["file"]) for sheet in plan["sheets"]]
    missing = [str(path) for path in expected_source_files if not path.is_file()]
    if missing:
        raise SystemExit("Missing selected source sheets:\n- " + "\n- ".join(missing))
    companion_sources = plan.get(
        "companionSources",
        {"itemIcon": f"{source_directory}/item-icon.png", "profile": f"{source_directory}/profile.png"},
    )
    item_icon_path = project_dir / str(companion_sources["itemIcon"])
    profile_path = project_dir / str(companion_sources["profile"])
    for companion in (item_icon_path, profile_path):
        if not companion.is_file():
            raise SystemExit(f"Missing companion image: {companion}")
    try:
        item_icon_name = item_icon_path.relative_to(source_dir).as_posix()
        profile_name = profile_path.relative_to(source_dir).as_posix()
    except ValueError as error:
        raise SystemExit("Companion images must be inside sourceDirectory") from error

    output = project_dir / "build_assets.py"
    if output.exists() and not args.overwrite:
        raise SystemExit(f"Refusing to overwrite {output}; use --overwrite after review.")

    sheet_rows = []
    for sheet in plan["sheets"]:
        sheet_rows.append(
            "    SheetConfig("
            f"key={python_string(sheet['key'])}, file_name={python_string(sheet['file'])}, "
            f"columns={int(sheet['columns'])}, rows={int(sheet['rows'])}),"
        )
    mapping_rows = []
    for row in sorted(plan["bodySlots"], key=lambda item: item["slot"]):
        offset = row.get("offsetAdjustment", [0, 0])
        mapping_rows.append(
            "    BodyFrameConfig("
            f"sheet={python_string(row['sheet'])}, source_index={int(row['sourceIndex'])}, "
            f"observed={python_string(row['observed'])}, group={python_string(row['suggestedGroup'])}, "
            f"offset_adjustment=({int(offset[0])}, {int(offset[1])}), "
            f"horizontal_flip={bool(row.get('horizontalFlip', False))}),"
        )

    project_root_literal = python_string(str(project_dir.parents[2]))
    code = f'''from __future__ import annotations

import sys
from pathlib import Path

PROJECT_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = Path({project_root_literal})
sys.path.insert(0, str(PROJECT_ROOT / "costume-tools"))

from full_body_hd_builder import BodyFrameConfig, CostumeConfig, SheetConfig, run


SHEETS = (
{chr(10).join(sheet_rows)}
)

BODY_MAPPING = (
{chr(10).join(mapping_rows)}
)

CONFIG = CostumeConfig(
    project_dir=PROJECT_DIR,
    repository_root=PROJECT_ROOT,
    name={python_string(plan['name'])},
    slug={python_string(plan['slug'])},
    item_id={args.item_id},
    part_ids=({args.head_part_id}, {args.head_part_id + 1}, {args.head_part_id + 2}),
    profile_icon_id={args.profile_icon_id},
    first_body_image_id={args.first_body_image_id},
    transparent_image_id={args.transparent_image_id},
    item_icon_id={args.item_icon_id},
    profile_file_name={python_string(profile_name)},
    item_icon_file_name={python_string(item_icon_name)},
    sheets=SHEETS,
    body_mapping=BODY_MAPPING,
    description={python_string(plan['description'])},
    source_directory={python_string(source_directory)},
    shop_id={args.shop_id!r},
    tab_id={args.tab_id!r},
    shop_cost={args.shop_cost!r},
)


if __name__ == "__main__":
    run(CONFIG)
'''
    output.write_text(code, encoding="utf-8")
    print(f"Emitted builder: {output}")
    print("Review source images, run build_assets.py, inspect QA, then audit before any deployment.")


if __name__ == "__main__":
    main()
