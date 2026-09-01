#!/usr/bin/env python3
"""Validate the structural NRO contract in an AI full-body motion plan."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any


def validate(plan: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    schema_version = plan.get("schemaVersion")
    if schema_version not in (1, 2):
        errors.append("schemaVersion must be 1 (legacy) or 2")
    if plan.get("mode") != "full-body":
        errors.append("mode must be full-body")
    if plan.get("head", {}).get("slotCount") != 3:
        errors.append("full-body mode requires exactly 3 HEAD placeholders")
    if plan.get("leg", {}).get("slotCount") != 14:
        errors.append("full-body mode requires exactly 14 LEG placeholders")

    source_directory = plan.get("sourceDirectory")
    if (
        not isinstance(source_directory, str)
        or not source_directory.strip()
        or Path(source_directory).is_absolute()
        or ".." in Path(source_directory).parts
    ):
        errors.append("sourceDirectory must be a non-empty relative path without '..'")

    if schema_version == 2:
        status = plan.get("status")
        if status != "ready-for-builder" and not (
            isinstance(status, str) and status.startswith("deployed")
        ):
            errors.append("schemaVersion 2 status must be ready-for-builder before builder emission")
        art = plan.get("artDirection", {})
        if not isinstance(art.get("styleReference"), str) or not art["styleReference"].strip():
            errors.append("schemaVersion 2 requires an in-game artDirection.styleReference")
        if not isinstance(art.get("targetStyle"), str) or not art["targetStyle"].strip():
            errors.append("schemaVersion 2 requires artDirection.targetStyle")
        target_height = art.get("targetX1VisualHeight")
        if not isinstance(target_height, int) or isinstance(target_height, bool) or target_height <= 0:
            errors.append("artDirection.targetX1VisualHeight must be a positive integer")
        pilot = art.get("pilotIdle", {})
        if pilot.get("status") != "approved":
            errors.append("idle pilot must be approved before action sheets or builder emission")
        if not isinstance(pilot.get("previewPath"), str) or not pilot["previewPath"].strip():
            errors.append("approved idle pilot requires an actual-size previewPath")

        grounding = plan.get("grounding", {})
        if grounding.get("status") != "approved":
            errors.append("grounding must be approved against a verified costume/client reference")
        if not isinstance(grounding.get("reference"), str) or not grounding["reference"].strip():
            errors.append("grounding.reference is required")
        if not isinstance(grounding.get("targetBottomRelative"), int) or isinstance(
            grounding.get("targetBottomRelative"), bool
        ):
            errors.append("grounding.targetBottomRelative must be an integer")
        foot_center = grounding.get("targetFootCenterX")
        if not isinstance(foot_center, (int, float)) or isinstance(foot_center, bool):
            errors.append("grounding.targetFootCenterX must be numeric")

        generation = plan.get("generation", {})
        chroma_key = generation.get("chromaKey")
        if not isinstance(chroma_key, str) or re.fullmatch(r"#[0-9a-fA-F]{6}", chroma_key) is None:
            errors.append("generation.chromaKey must be a six-digit hex colour")
        if generation.get("backgroundMode") != "flat-opaque-chroma":
            errors.append("generation.backgroundMode must be flat-opaque-chroma")
        if generation.get("alphaKeyVerified") is not True:
            errors.append("generation.alphaKeyVerified must be true after deterministic keying")
        if generation.get("alphaSpillVerified") is not True:
            errors.append("generation.alphaSpillVerified must be true after edge inspection")

        companions = plan.get("companionSources", {})
        for role in ("itemIcon", "profile"):
            value = companions.get(role)
            if not isinstance(value, str) or not value.strip():
                errors.append(f"companionSources.{role} is required")

    sheets = {sheet.get("key"): sheet for sheet in plan.get("sheets", []) if isinstance(sheet, dict)}
    if len(sheets) != len(plan.get("sheets", [])):
        errors.append("sheet keys must be unique and non-empty")

    slots = plan.get("bodySlots", [])
    if not isinstance(slots, list):
        errors.append("bodySlots must be a list")
        return errors
    if len(slots) != 17:
        errors.append(f"BODY must contain exactly 17 mappings, got {len(slots)}")
        return errors
    expected_slots = list(range(17))
    slot_values = [row.get("slot") for row in slots if isinstance(row, dict)]
    if len(slot_values) != len(slots) or any(not isinstance(value, int) for value in slot_values):
        errors.append("every BODY mapping needs an integer slot")
    elif sorted(slot_values) != expected_slots:
        errors.append(f"BODY slots must be exactly 0..16, got {sorted(slot_values)}")

    for row in slots:
        if not isinstance(row, dict):
            errors.append("every BODY mapping must be an object")
            continue
        key = row.get("sheet")
        sheet = sheets.get(key)
        if sheet is None:
            errors.append(f"BODY[{row.get('slot')}] references unknown sheet {key!r}")
            continue
        index = row.get("sourceIndex")
        columns, rows = sheet.get("columns"), sheet.get("rows")
        if not isinstance(columns, int) or not isinstance(rows, int) or columns <= 0 or rows <= 0:
            errors.append(f"sheet {key!r} needs positive integer columns and rows")
            continue
        capacity = columns * rows
        if not isinstance(index, int) or not 0 <= index < capacity:
            errors.append(f"BODY[{row.get('slot')}] has invalid sourceIndex {index!r} for {key}")
        if not isinstance(row.get("observed"), str) or not row["observed"].strip():
            errors.append(f"BODY[{row.get('slot')}] needs an observed-pose note")
        if not isinstance(row.get("suggestedGroup"), str) or not row["suggestedGroup"].strip():
            errors.append(f"BODY[{row.get('slot')}] needs a suggested group")
        offset = row.get("offsetAdjustment", [0, 0])
        if (
            not isinstance(offset, list)
            or len(offset) != 2
            or any(not isinstance(value, int) or isinstance(value, bool) for value in offset)
            or any(value < -128 or value > 127 for value in offset)
        ):
            errors.append(f"BODY[{row.get('slot')}] offsetAdjustment must be two signed-byte integers")
    return errors


def warnings(plan: dict[str, Any]) -> list[str]:
    result: list[str] = []
    if plan.get("schemaVersion") == 1:
        result.append(
            "Legacy schemaVersion 1 has no mandatory idle/style/grounding/alpha gates; migrate before a new build."
        )
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--plan", required=True, type=Path)
    parser.add_argument("--report", type=Path, help="Optional JSON validation report path")
    args = parser.parse_args()

    try:
        plan = json.loads(args.plan.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise SystemExit(f"Cannot read plan: {error}") from error
    errors = validate(plan)
    warning_rows = warnings(plan)
    report = {
        "plan": str(args.plan),
        "valid": not errors,
        "errors": errors,
        "warnings": warning_rows,
    }
    encoded = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.report:
        args.report.write_text(encoded, encoding="utf-8")
    print(encoded, end="")
    if errors:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
