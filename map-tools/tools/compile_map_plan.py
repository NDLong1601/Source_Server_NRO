#!/usr/bin/env python3
"""Deterministic, staging-only compiler for Teamobi NRO map plans."""

from __future__ import annotations

import argparse
import colorsys
import hashlib
import json
import os
import re
import shutil
import struct
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable

from jsonschema import Draft7Validator
from PIL import Image, ImageDraw, ImageFont


COMPILER_VERSION = "1.3.0"
TILE_SIZE = 24
RUNTIME_TILE_RELATIVE = Path("data/map/tile_map_data")
RUNTIME_BG_RELATIVE = Path("data/map/item_bg_map_data")
# mob_template.TYPE = 4 trong sql/team2026.sql. Các Mob này bay và không cần
# tile nền ngay tại tọa độ spawn.
FLYING_MOB_TEMPLATE_IDS = frozenset({
    7, 8, 9, 10, 11, 12, 21, 25, 28, 29, 30, 31, 32, 33, 37, 43,
    49, 50, 69, 75, 79, 87, 104,
})


@dataclass(frozen=True)
class CompileResult:
    output_dir: Path
    map_id: int
    manifest: dict[str, Any]
    report: dict[str, Any]


class CompilationFailed(Exception):
    def __init__(self, report: dict[str, Any]):
        super().__init__("Map plan validation failed")
        self.report = report


def canonical_json_bytes(value: Any) -> bytes:
    text = json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True)
    return (text + "\n").encode("utf-8")


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(canonical_json_bytes(value))


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def find_repo_root(start: Path) -> Path:
    candidate = start.resolve()
    if candidate.is_file():
        candidate = candidate.parent
    for directory in (candidate, *candidate.parents):
        if (directory / "data" / "map").is_dir() and (directory / "src").is_dir():
            return directory
    raise FileNotFoundError("Không tìm thấy repository root chứa data/map và src")


def json_path(parts: Iterable[Any]) -> str:
    path = "$"
    for part in parts:
        if isinstance(part, int):
            path += f"[{part}]"
        else:
            path += f".{part}"
    return path


def new_issue(severity: str, code: str, path: str, message: str) -> dict[str, str]:
    return {
        "severity": severity,
        "code": code,
        "path": path,
        "message": message,
    }


def load_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8-sig") as stream:
        return json.load(stream)


def load_schema(repo_root: Path) -> tuple[dict[str, Any], Path]:
    schema_path = repo_root / "map-tools" / "schemas" / "teamobi-map-plan.schema.json"
    schema = load_json(schema_path)
    Draft7Validator.check_schema(schema)
    return schema, schema_path


def load_tile_profiles(repo_root: Path) -> tuple[dict[str, Any], Path]:
    profile_path = (
        repo_root
        / ".agents"
        / "skills"
        / "generate-teamobi-map"
        / "references"
        / "tile-profiles.json"
    )
    if not profile_path.is_file():
        raise FileNotFoundError(f"Thiếu tile profile: {profile_path}")
    return load_json(profile_path), profile_path


def load_bg_registry(repo_root: Path) -> tuple[dict[int, dict[str, Any]], Path]:
    sql_path = repo_root / "sql" / "team2026.sql"
    sql = sql_path.read_text(encoding="utf-8-sig", errors="strict")
    statement_pattern = re.compile(
        r"INSERT\s+INTO\s+`bg_item_template`\s*\([^;]+?\)\s*VALUES\s*(.*?);",
        re.IGNORECASE | re.DOTALL,
    )
    row_pattern = re.compile(
        r"\(\s*(-?\d+)\s*,\s*(-?\d+)\s*,\s*(-?\d+)\s*,\s*(-?\d+)\s*,\s*(-?\d+)\s*\)"
    )
    registry: dict[int, dict[str, Any]] = {}
    for statement in statement_pattern.finditer(sql):
        for row in row_pattern.finditer(statement.group(1)):
            template_id, image_id, layer, dx, dy = map(int, row.groups())
            if template_id in registry:
                raise ValueError(f"Trùng bg_item_template ID {template_id} trong {sql_path}")
            registry[template_id] = {
                "templateId": template_id,
                "imageId": image_id,
                "layer": layer,
                "dx": dx,
                "dy": dy,
            }
    if not registry:
        raise ValueError(f"Không đọc được bg_item_template từ {sql_path}")
    return registry, sql_path


def load_project_bg_registry(plan_path: Path) -> tuple[dict[int, dict[str, Any]], Path | None]:
    registry_path = plan_path.parent / "assets" / "registry.json"
    if not registry_path.is_file():
        return {}, None
    payload = load_json(registry_path)
    if not isinstance(payload, dict) or not isinstance(payload.get("assets"), list):
        raise ValueError(f"Custom asset registry không hợp lệ: {registry_path}")
    registry: dict[int, dict[str, Any]] = {}
    project_root = plan_path.parent.resolve()
    for index, asset in enumerate(payload["assets"]):
        if not isinstance(asset, dict):
            raise ValueError(f"Custom asset registry assets[{index}] không phải object")
        try:
            template_id = int(asset["templateId"])
            image_id = int(asset["imageId"])
            layer = int(asset["layer"])
            dx = int(asset.get("dx", 0))
            dy = int(asset.get("dy", 0))
            asset_paths = asset["assetPaths"]
        except (KeyError, TypeError, ValueError) as error:
            raise ValueError(f"Custom asset registry assets[{index}] thiếu field bắt buộc") from error
        if template_id < 0 or template_id > 32767 or image_id < 0 or image_id > 32767:
            raise ValueError(f"Custom asset assets[{index}] có template/image ID ngoài signed-short")
        if layer not in (1, 2, 3, 4):
            raise ValueError(f"Custom asset assets[{index}] có layer ngoài 1..4")
        if template_id in registry:
            raise ValueError(f"Trùng custom bg template ID {template_id}")
        resolved_assets: dict[str, Path] = {}
        for zoom in range(1, 5):
            key = f"x{zoom}"
            relative = Path(str(asset_paths.get(key, "")))
            if not relative.parts or relative.is_absolute() or ".." in relative.parts:
                raise ValueError(f"Custom asset {template_id} có path {key} không an toàn")
            resolved = (project_root / relative).resolve()
            if not is_relative_to(resolved, project_root):
                raise ValueError(f"Custom asset {template_id} có path {key} thoát khỏi project")
            resolved_assets[key] = resolved
        registry[template_id] = {
            "templateId": template_id,
            "imageId": image_id,
            "layer": layer,
            "dx": dx,
            "dy": dy,
            "name": str(asset.get("name", f"Custom asset {template_id}")),
            "custom": True,
            "_assetPaths": resolved_assets,
        }
    return registry, registry_path


def load_project_offset_registry(
    plan_path: Path,
) -> tuple[dict[tuple[int, int, int], int], Path | None]:
    registry_path = plan_path.parent / "assets" / "generated-offsets.json"
    if not registry_path.is_file():
        return {}, None
    payload = load_json(registry_path)
    if not isinstance(payload, dict) or not isinstance(payload.get("templates"), list):
        raise ValueError(f"Generated offset registry không hợp lệ: {registry_path}")
    registry: dict[tuple[int, int, int], int] = {}
    used_ids: set[int] = set()
    for index, item in enumerate(payload["templates"]):
        if not isinstance(item, dict):
            raise ValueError(f"Generated offset templates[{index}] không phải object")
        try:
            source_template_id = int(item["sourceTemplateId"])
            offset_x = int(item["offsetX"])
            offset_y = int(item["offsetY"])
            template_id = int(item["templateId"])
        except (KeyError, TypeError, ValueError) as error:
            raise ValueError(f"Generated offset templates[{index}] thiếu field bắt buộc") from error
        key = (source_template_id, offset_x, offset_y)
        if source_template_id < 0 or source_template_id > 32767 or template_id < 0 or template_id > 32767:
            raise ValueError(f"Generated offset templates[{index}] có template ID ngoài signed-short")
        if offset_x < -23 or offset_x > 23 or offset_y < -23 or offset_y > 23:
            raise ValueError(f"Generated offset templates[{index}] có fine offset ngoài -23..23")
        if key in registry or template_id in used_ids:
            raise ValueError(f"Generated offset templates[{index}] bị trùng key hoặc template ID")
        registry[key] = template_id
        used_ids.add(template_id)
    return registry, registry_path


def resolve_bg_asset_path(template: dict[str, Any], zoom: int, repo_root: Path) -> Path:
    custom_paths = template.get("_assetPaths")
    if isinstance(custom_paths, dict) and f"x{zoom}" in custom_paths:
        return Path(custom_paths[f"x{zoom}"])
    return repo_root / "data" / "item_bg_temp" / f"x{zoom}" / f"{template['imageId']}.png"


def public_bg_template(template: dict[str, Any]) -> dict[str, Any]:
    return {key: value for key, value in template.items() if not key.startswith("_")}


def validate_schema(plan: Any, schema: dict[str, Any]) -> list[dict[str, str]]:
    validator = Draft7Validator(schema)
    issues: list[dict[str, str]] = []
    errors = sorted(validator.iter_errors(plan), key=lambda item: list(item.absolute_path))
    for error in errors:
        issues.append(
            new_issue("error", "SCHEMA_VALIDATION", json_path(error.absolute_path), error.message)
        )
    return issues


def get_semantic_tile(
    record: dict[str, Any],
    explicit_key: str,
    defaults: dict[str, Any],
    default_key: str,
) -> int | None:
    value = record.get(explicit_key)
    return defaults.get(default_key) if value is None else value


def validate_range(
    issues: list[dict[str, str]],
    value: int,
    minimum: int,
    maximum: int,
    path: str,
    label: str,
) -> None:
    if value < minimum or value > maximum:
        issues.append(
            new_issue(
                "error",
                "COORDINATE_OUT_OF_BOUNDS",
                path,
                f"{label}={value} ngoài khoảng {minimum}..{maximum}",
            )
        )


def validate_plan_contract(
    plan: dict[str, Any],
    profiles: dict[str, Any],
    registry: dict[int, dict[str, Any]],
    repo_root: Path,
) -> list[dict[str, str]]:
    issues: list[dict[str, str]] = []
    map_data = plan["map"]
    terrain = plan["terrain"]
    width = map_data["widthTiles"]
    height = map_data["heightTiles"]
    width_px = width * TILE_SIZE
    height_px = height * TILE_SIZE
    tile_set_id = map_data["tileSetId"]
    profile = profiles.get(str(tile_set_id))
    if profile is None:
        issues.append(
            new_issue("error", "UNKNOWN_TILE_PROFILE", "$.map.tileSetId", f"Không có profile tileset {tile_set_id}")
        )
        return issues

    if profile.get("confidence") == "low":
        issues.append(
            new_issue(
                "warning",
                "LOW_CONFIDENCE_TILE_PROFILE",
                "$.map.tileSetId",
                f"Tileset {tile_set_id} chưa có map mẫu; cần kiểm tra preview/physics thủ công",
            )
        )

    matrix = terrain.get("matrix")
    if matrix is not None:
        if len(matrix) != height:
            issues.append(
                new_issue(
                    "error",
                    "MATRIX_HEIGHT_MISMATCH",
                    "$.terrain.matrix",
                    f"Matrix có {len(matrix)} hàng, cần đúng {height}",
                )
            )
        for row_index, row in enumerate(matrix):
            if len(row) != width:
                issues.append(
                    new_issue(
                        "error",
                        "MATRIX_WIDTH_MISMATCH",
                        f"$.terrain.matrix[{row_index}]",
                        f"Hàng có {len(row)} ô, cần đúng {width}",
                    )
                )

    defaults = profile["semanticDefaults"]
    operation_specs = (
        ("platforms", "startTileX", "endTileX", "tileY", None),
        ("bridges", "startTileX", "endTileX", "tileY", None),
        ("water", "startTileX", "endTileX", "tileY", None),
    )
    for collection, start_key, end_key, y_key, _ in operation_specs:
        for index, record in enumerate(terrain.get(collection, [])):
            path = f"$.terrain.{collection}[{index}]"
            start_x, end_x, tile_y = record[start_key], record[end_key], record[y_key]
            validate_range(issues, start_x, 0, width - 1, f"{path}.{start_key}", start_key)
            validate_range(issues, end_x, 0, width - 1, f"{path}.{end_key}", end_key)
            validate_range(issues, tile_y, 0, height - 1, f"{path}.{y_key}", y_key)
            if start_x > end_x:
                issues.append(new_issue("error", "INVALID_RANGE", path, "startTileX phải <= endTileX"))

    for index, record in enumerate(terrain.get("platforms", [])):
        path = f"$.terrain.platforms[{index}]"
        end_y = record["tileY"] + record.get("thicknessTiles", 1) - 1
        validate_range(issues, end_y, 0, height - 1, f"{path}.thicknessTiles", "platform endTileY")
        for explicit, default in (
            ("topTile", "topSurface"),
            ("fillTile", "undergroundFill"),
            ("leftCap", "leftEdge"),
            ("rightCap", "rightEdge"),
        ):
            if get_semantic_tile(record, explicit, defaults, default) is None:
                issues.append(new_issue("error", "MISSING_SEMANTIC_TILE", f"{path}.{explicit}", f"Thiếu {explicit} và profile không có {default}"))

    for index, record in enumerate(terrain.get("slopes", [])):
        path = f"$.terrain.slopes[{index}]"
        for key, maximum in (("startTileX", width - 1), ("endTileX", width - 1), ("startTileY", height - 1), ("endTileY", height - 1)):
            validate_range(issues, record[key], 0, maximum, f"{path}.{key}", key)
        dx = abs(record["endTileX"] - record["startTileX"])
        dy = abs(record["endTileY"] - record["startTileY"])
        if dx != dy:
            issues.append(new_issue("error", "UNSUPPORTED_SLOPE", path, "Giai đoạn 1 chỉ hỗ trợ slope 45 độ (abs(dx) == abs(dy))"))
        if get_semantic_tile(record, "slopeTile", defaults, "slope") is None:
            issues.append(new_issue("error", "MISSING_SEMANTIC_TILE", f"{path}.slopeTile", "Tileset không có slope mặc định; phải chỉ định slopeTile"))

    for index, record in enumerate(terrain.get("bridges", [])):
        if get_semantic_tile(record, "bridgeTile", defaults, "bridge") is None:
            issues.append(new_issue("error", "MISSING_SEMANTIC_TILE", f"$.terrain.bridges[{index}].bridgeTile", "Tileset không có bridge mặc định; phải chỉ định bridgeTile"))

    for index, record in enumerate(terrain.get("water", [])):
        path = f"$.terrain.water[{index}]"
        end_y = record["tileY"] + record.get("depthTiles", 2) - 1
        validate_range(issues, end_y, 0, height - 1, f"{path}.depthTiles", "water endTileY")
        for explicit, default in (("surfaceTile", "water"), ("bodyTile", "water")):
            if get_semantic_tile(record, explicit, defaults, default) is None:
                issues.append(new_issue("error", "MISSING_SEMANTIC_TILE", f"{path}.{explicit}", f"Tileset không có {default} mặc định; phải chỉ định {explicit}"))

    for index, record in enumerate(terrain.get("blockers", [])):
        path = f"$.terrain.blockers[{index}]"
        for key, maximum in (("startTileX", width - 1), ("endTileX", width - 1), ("startTileY", height - 1), ("endTileY", height - 1)):
            validate_range(issues, record[key], 0, maximum, f"{path}.{key}", key)
        if record["startTileX"] > record["endTileX"] or record["startTileY"] > record["endTileY"]:
            issues.append(new_issue("error", "INVALID_RANGE", path, "Blocker start phải <= end"))

    for index, record in enumerate(terrain.get("patches", [])):
        path = f"$.terrain.patches[{index}]"
        validate_range(issues, record["tileX"], 0, width - 1, f"{path}.tileX", "tileX")
        validate_range(issues, record["tileY"], 0, height - 1, f"{path}.tileY", "tileY")

    for index, record in enumerate(plan["bgItems"]):
        path = f"$.bgItems[{index}]"
        template_id = record["templateId"]
        template = registry.get(template_id)
        if template is None:
            issues.append(new_issue("error", "UNKNOWN_BG_TEMPLATE", f"{path}.templateId", f"Không tồn tại bg_item_template ID {template_id}"))
            continue
        validate_range(issues, record["tileX"], -5, width + 5, f"{path}.tileX", "tileX")
        validate_range(issues, record["tileY"], -5, height + 5, f"{path}.tileY", "tileY")
        if template["layer"] not in (1, 2, 3, 4):
            issues.append(new_issue("error", "INVALID_BG_LAYER", f"{path}.templateId", f"Template {template_id} có layer {template['layer']} ngoài 1..4"))
        runtime_dx = int(template["dx"]) + int(record.get("offsetX", 0))
        runtime_dy = int(template["dy"]) + int(record.get("offsetY", 0))
        if runtime_dx < -32768 or runtime_dx > 32767:
            issues.append(new_issue("error", "BG_OFFSET_OUT_OF_RANGE", f"{path}.offsetX", f"Dx runtime {runtime_dx} nằm ngoài signed-short"))
        if runtime_dy < -32768 or runtime_dy > 32767:
            issues.append(new_issue("error", "BG_OFFSET_OUT_OF_RANGE", f"{path}.offsetY", f"Dy runtime {runtime_dy} nằm ngoài signed-short"))
        missing_zooms = [zoom for zoom in range(1, 5) if not resolve_bg_asset_path(template, zoom, repo_root).is_file()]
        if missing_zooms:
            issues.append(new_issue("warning", "MISSING_BG_ASSET_ZOOM", f"{path}.templateId", f"Template {template_id} thiếu PNG zoom {missing_zooms}"))

    gameplay = plan["gameplay"]

    def validate_pixel_point(path: str, x: int, y: int, require_grid_y: bool = True) -> None:
        validate_range(issues, x, 0, width_px, f"{path}.x", "pixelX")
        validate_range(issues, y, 0, height_px, f"{path}.y", "pixelY")
        if require_grid_y and y % TILE_SIZE != 0:
            issues.append(new_issue("error", "GROUND_ALIGNMENT", f"{path}.y", f"Y={y} phải chia hết cho {TILE_SIZE}"))

    spawn = gameplay["playerSpawn"]
    validate_pixel_point("$.gameplay.playerSpawn", spawn["x"], spawn["y"])
    for index, mob in enumerate(gameplay.get("mobs", [])):
        validate_pixel_point(f"$.gameplay.mobs[{index}]", mob["mobX"], mob["mobY"])
    for index, npc in enumerate(gameplay.get("npcs", [])):
        validate_pixel_point(f"$.gameplay.npcs[{index}]", npc["npcX"], npc["npcY"])

    for index, waypoint in enumerate(gameplay.get("waypoints", [])):
        path = f"$.gameplay.waypoints[{index}]"
        for key, maximum in (("minX", width_px), ("maxX", width_px), ("minY", height_px), ("maxY", height_px)):
            validate_range(issues, waypoint[key], 0, maximum, f"{path}.{key}", key)
        if waypoint["minX"] > waypoint["maxX"] or waypoint["minY"] > waypoint["maxY"]:
            issues.append(new_issue("error", "INVALID_WAYPOINT_RECT", path, "Waypoint min phải <= max"))

    map_id = map_data["mapId"]
    runtime_tile = repo_root / RUNTIME_TILE_RELATIVE / str(map_id)
    runtime_bg = repo_root / RUNTIME_BG_RELATIVE / str(map_id)
    if runtime_tile.exists() or runtime_bg.exists():
        issues.append(new_issue("warning", "RUNTIME_MAP_ID_EXISTS", "$.map.mapId", f"Map ID {map_id} đã có file runtime; Giai đoạn 1 vẫn chỉ xuất staging"))
    return issues


def compile_tile_grid(plan: dict[str, Any], profile: dict[str, Any]) -> tuple[list[list[int]], dict[str, int]]:
    map_data = plan["map"]
    terrain = plan["terrain"]
    width, height = map_data["widthTiles"], map_data["heightTiles"]
    defaults = profile["semanticDefaults"]
    matrix = terrain.get("matrix")
    if matrix is None:
        base = terrain.get("baseTile", 0)
        grid = [[base for _ in range(width)] for _ in range(height)]
    else:
        grid = [list(row) for row in matrix]

    writes = 0

    def set_tile(x: int, y: int, tile: int) -> None:
        nonlocal writes
        grid[y][x] = tile
        writes += 1

    for record in terrain.get("platforms", []):
        start_x, end_x, top_y = record["startTileX"], record["endTileX"], record["tileY"]
        thickness = record.get("thicknessTiles", 1)
        top = get_semantic_tile(record, "topTile", defaults, "topSurface")
        fill = get_semantic_tile(record, "fillTile", defaults, "undergroundFill")
        left = get_semantic_tile(record, "leftCap", defaults, "leftEdge")
        right = get_semantic_tile(record, "rightCap", defaults, "rightEdge")
        for x in range(start_x, end_x + 1):
            set_tile(x, top_y, int(top))
            for y in range(top_y + 1, top_y + thickness):
                set_tile(x, y, int(fill))
        set_tile(start_x, top_y, int(left))
        set_tile(end_x, top_y, int(right))

    for record in terrain.get("slopes", []):
        x0, x1 = record["startTileX"], record["endTileX"]
        y0, y1 = record["startTileY"], record["endTileY"]
        count = abs(x1 - x0) + 1
        step_x = 1 if x1 >= x0 else -1
        step_y = 1 if y1 >= y0 else -1
        slope_tile = int(get_semantic_tile(record, "slopeTile", defaults, "slope"))
        fill_tile = record.get("fillTile")
        for offset in range(count):
            x, y = x0 + offset * step_x, y0 + offset * step_y
            set_tile(x, y, slope_tile)
            if fill_tile is not None:
                for fill_y in range(y + 1, height):
                    set_tile(x, fill_y, int(fill_tile))

    for record in terrain.get("bridges", []):
        tile = int(get_semantic_tile(record, "bridgeTile", defaults, "bridge"))
        for x in range(record["startTileX"], record["endTileX"] + 1):
            set_tile(x, record["tileY"], tile)

    for record in terrain.get("water", []):
        surface = int(get_semantic_tile(record, "surfaceTile", defaults, "water"))
        body = int(get_semantic_tile(record, "bodyTile", defaults, "water"))
        for x in range(record["startTileX"], record["endTileX"] + 1):
            set_tile(x, record["tileY"], surface)
            for y in range(record["tileY"] + 1, record["tileY"] + record.get("depthTiles", 2)):
                set_tile(x, y, body)

    for record in terrain.get("blockers", []):
        tile = record["blockerTile"]
        for y in range(record["startTileY"], record["endTileY"] + 1):
            for x in range(record["startTileX"], record["endTileX"] + 1):
                set_tile(x, y, tile)

    for record in terrain.get("patches", []):
        set_tile(record["tileX"], record["tileY"], record["tileId"])

    non_empty = sum(1 for row in grid for tile in row if tile != 0)
    return grid, {"writeOperations": writes, "nonEmptyTiles": non_empty, "totalTiles": width * height}


def validate_ground_alignment(plan: dict[str, Any], grid: list[list[int]]) -> list[dict[str, str]]:
    issues: list[dict[str, str]] = []
    height, width = len(grid), len(grid[0])

    def check(path: str, x: int, y: int) -> None:
        if x < 0 or y < 0 or x > width * TILE_SIZE or y > height * TILE_SIZE or y % TILE_SIZE != 0:
            return
        tile_x = min(width - 1, x // TILE_SIZE)
        tile_y = min(height - 1, y // TILE_SIZE)
        if grid[tile_y][tile_x] == 0:
            issues.append(new_issue("error", "NO_GROUND_AT_ENTITY", path, f"Không có tile nền tại ({tile_x},{tile_y}) cho điểm chân ({x},{y})"))

    gameplay = plan["gameplay"]
    spawn = gameplay["playerSpawn"]
    check("$.gameplay.playerSpawn", spawn["x"], spawn["y"])
    for index, mob in enumerate(gameplay.get("mobs", [])):
        if int(mob["mobTemp"]) in FLYING_MOB_TEMPLATE_IDS:
            continue
        check(f"$.gameplay.mobs[{index}]", mob["mobX"], mob["mobY"])
    for index, npc in enumerate(gameplay.get("npcs", [])):
        check(f"$.gameplay.npcs[{index}]", npc["npcX"], npc["npcY"])
    return issues


def build_bg_records(
    plan: dict[str, Any],
    registry: dict[int, dict[str, Any]],
    repo_root: Path,
    offset_registry: dict[tuple[int, int, int], int] | None = None,
) -> list[dict[str, Any]]:
    fine_offset_keys = sorted(
        {
            (
                int(placement["templateId"]),
                int(placement.get("offsetX", 0)),
                int(placement.get("offsetY", 0)),
            )
            for placement in plan["bgItems"]
            if int(placement.get("offsetX", 0)) != 0 or int(placement.get("offsetY", 0)) != 0
        }
    )
    offset_registry = offset_registry or {}
    used_template_ids = set(registry)
    for key, template_id in offset_registry.items():
        if template_id in used_template_ids:
            raise ValueError(
                f"Generated offset {key} dùng template ID {template_id} đã tồn tại trong bg registry"
            )
        used_template_ids.add(template_id)
    generated_template_ids: dict[tuple[int, int, int], int] = {}
    candidate = 32767
    for key in fine_offset_keys:
        if key in offset_registry:
            generated_template_ids[key] = offset_registry[key]
            continue
        while candidate in used_template_ids and candidate >= 0:
            candidate -= 1
        if candidate < 0:
            raise ValueError("Đã hết ID signed-short để materialize fine offset của background asset")
        generated_template_ids[key] = candidate
        used_template_ids.add(candidate)
        candidate -= 1

    records: list[dict[str, Any]] = []
    for index, placement in enumerate(plan["bgItems"]):
        source_template_id = int(placement["templateId"])
        template = registry[source_template_id]
        offset_x = int(placement.get("offsetX", 0))
        offset_y = int(placement.get("offsetY", 0))
        generated = offset_x != 0 or offset_y != 0
        runtime_template_id = generated_template_ids.get(
            (source_template_id, offset_x, offset_y), source_template_id
        )
        runtime_dx = int(template["dx"]) + offset_x
        runtime_dy = int(template["dy"]) + offset_y
        assets = {
            f"x{zoom}": str(Path("data") / "item_bg_temp" / f"x{zoom}" / f"{template['imageId']}.png")
            for zoom in range(1, 5)
            if resolve_bg_asset_path(template, zoom, repo_root).is_file()
        }
        records.append(
            {
                "index": index,
                "templateId": runtime_template_id,
                "sourceTemplateId": source_template_id,
                "tileX": int(placement["tileX"]),
                "tileY": int(placement["tileY"]),
                "editorOffsetX": offset_x,
                "editorOffsetY": offset_y,
                "imageId": template["imageId"],
                "layer": template["layer"],
                "dx": runtime_dx,
                "dy": runtime_dy,
                "custom": bool(template.get("custom", False)),
                "generatedOffsetTemplate": generated,
                "requiresTemplateInstall": bool(template.get("custom", False)) or generated,
                "_assetPaths": {
                    key: str(value) for key, value in template.get("_assetPaths", {}).items()
                },
                "pixelX": int(placement["tileX"]) * TILE_SIZE + runtime_dx,
                "pixelY": int(placement["tileY"]) * TILE_SIZE + runtime_dy,
                "assets": assets,
            }
        )
    return records


def public_bg_record(record: dict[str, Any]) -> dict[str, Any]:
    return {key: value for key, value in record.items() if not key.startswith("_")}


def build_map_template(plan: dict[str, Any]) -> dict[str, Any]:
    map_data = plan["map"]
    gameplay = plan["gameplay"]
    waypoints = [
        [
            item["name"], item["minX"], item["minY"], item["maxX"], item["maxY"],
            1 if item.get("isEnter", False) else 0,
            1 if item.get("isOffline", False) else 0,
            item["goMap"], item["goX"], item["goY"],
        ]
        for item in gameplay.get("waypoints", [])
    ]
    mobs = [
        [item["mobTemp"], item["mobLevel"], item["mobHp"], item["mobX"], item["mobY"]]
        for item in gameplay.get("mobs", [])
    ]
    npcs = [[item["npcId"], item["npcX"], item["npcY"]] for item in gameplay.get("npcs", [])]
    return {
        "id": map_data["mapId"],
        "name": map_data["name"],
        "zones": map_data.get("zones", 10),
        "maxPlayer": map_data.get("maxPlayersPerZone", 15),
        "data": [
            map_data.get("type", 0),
            map_data.get("planetId", 0),
            map_data.get("bgType", 0),
            map_data["tileSetId"],
            map_data.get("bgId", 0),
        ],
        "type": map_data.get("type", 0),
        "planetId": map_data.get("planetId", 0),
        "bgType": map_data.get("bgType", 0),
        "tileId": map_data["tileSetId"],
        "bgId": map_data.get("bgId", 0),
        "waypoints": waypoints,
        "mobs": mobs,
        "npcs": npcs,
        "isMapDouble": 1 if map_data.get("isMapDouble", False) else 0,
        "playerSpawn": gameplay["playerSpawn"],
    }


def sql_quote(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def build_map_sql(template: dict[str, Any]) -> str:
    columns = [
        "id", "NAME", "zones", "max_player", "data", "type", "planet_id", "bg_type",
        "tile_id", "bg_id", "waypoints", "mobs", "npcs", "is_map_double",
    ]
    json_value = lambda value: json.dumps(value, ensure_ascii=False, separators=(",", ":"))
    values = [
        str(template["id"]),
        sql_quote(template["name"]),
        str(template["zones"]),
        str(template["maxPlayer"]),
        sql_quote(json_value(template["data"])),
        str(template["type"]),
        str(template["planetId"]),
        str(template["bgType"]),
        str(template["tileId"]),
        str(template["bgId"]),
        sql_quote(json_value(template["waypoints"])),
        sql_quote(json_value(template["mobs"])),
        sql_quote(json_value(template["npcs"])),
        str(template["isMapDouble"]),
    ]
    update_columns = [column for column in columns if column != "id"]
    updates = ",\n  ".join(f"`{column}` = VALUES(`{column}`)" for column in update_columns)
    return (
        "-- Staging artifact only. Review before executing.\n"
        f"INSERT INTO `map_template` ({', '.join(f'`{column}`' for column in columns)})\n"
        f"VALUES ({', '.join(values)})\n"
        "ON DUPLICATE KEY UPDATE\n  " + updates + ";\n"
    )


def build_bg_template_sql(records: list[dict[str, Any]]) -> str:
    templates: dict[int, dict[str, Any]] = {}
    for record in records:
        if record.get("requiresTemplateInstall") or record.get("custom"):
            templates[record["templateId"]] = record
    if not templates:
        return "-- No custom bg_item_template rows in this bundle.\n"
    values = []
    for template_id, record in sorted(templates.items()):
        values.append(
            f"({template_id}, {int(record['imageId'])}, {int(record['layer'])}, {int(record['dx'])}, {int(record['dy'])})"
        )
    return (
        "-- Custom background templates generated by the map compiler.\n"
        "INSERT INTO `bg_item_template` (`id`, `image_id`, `layer`, `dx`, `dy`)\nVALUES\n  "
        + ",\n  ".join(values)
        + "\nON DUPLICATE KEY UPDATE\n"
        "  `image_id` = VALUES(`image_id`),\n"
        "  `layer` = VALUES(`layer`),\n"
        "  `dx` = VALUES(`dx`),\n"
        "  `dy` = VALUES(`dy`);\n"
    )


def tile_color(tile_id: int) -> tuple[int, int, int, int]:
    hue = ((tile_id * 47) % 360) / 360.0
    saturation = 0.50 + ((tile_id * 13) % 25) / 100.0
    value = 0.62 + ((tile_id * 7) % 25) / 100.0
    red, green, blue = colorsys.hsv_to_rgb(hue, saturation, value)
    return int(red * 255), int(green * 255), int(blue * 255), 235


def resolve_tile_preview_path(repo_root: Path, tile_set_id: int, tile_id: int) -> tuple[Path | None, int | None]:
    """Resolve the client tile PNG used for editor/compiler previews.

    Zoom 2 is preferred because the repository stores individual 48x48 tile PNGs
    there for all production tilesets. Zoom 3/4 are safe fallbacks. Zoom 1 is an
    atlas for only part of the tileset range and therefore is intentionally not
    treated as an individual tile source.
    """
    if tile_id <= 0 or tile_id > 127:
        return None, None
    for zoom in (2, 3, 4):
        candidate = repo_root / "data" / "res" / f"x{zoom}" / f"{tile_set_id}${tile_id}"
        if candidate.is_file():
            return candidate, zoom
    return None, None


def load_tile_preview_sprites(
    repo_root: Path,
    tile_set_id: int,
    grid: list[list[int]],
) -> tuple[dict[int, Image.Image], dict[int, Path], list[int]]:
    used_tile_ids = sorted({tile for row in grid for tile in row if tile})
    sprites: dict[int, Image.Image] = {}
    paths: dict[int, Path] = {}
    missing: list[int] = []
    for tile_id in used_tile_ids:
        path, _ = resolve_tile_preview_path(repo_root, tile_set_id, tile_id)
        if path is None:
            missing.append(tile_id)
            continue
        try:
            with Image.open(path) as opened:
                opened.load()
                sprite = opened.convert("RGBA")
        except OSError:
            missing.append(tile_id)
            continue
        if sprite.size != (TILE_SIZE, TILE_SIZE):
            sprite = sprite.resize((TILE_SIZE, TILE_SIZE), Image.Resampling.LANCZOS)
        sprites[tile_id] = sprite
        paths[tile_id] = path
    return sprites, paths, missing


def create_base_preview(width_px: int, height_px: int, bg_id: int) -> Image.Image:
    base_hue = ((bg_id * 37) % 360) / 360.0
    top_rgb = tuple(int(channel * 255) for channel in colorsys.hsv_to_rgb(base_hue, 0.28, 0.34))
    bottom_rgb = tuple(int(channel * 255) for channel in colorsys.hsv_to_rgb(base_hue, 0.38, 0.16))
    image = Image.new("RGBA", (width_px, height_px), (*top_rgb, 255))
    draw = ImageDraw.Draw(image)
    denominator = max(1, height_px - 1)
    for y in range(height_px):
        ratio = y / denominator
        color = tuple(int(top_rgb[index] * (1 - ratio) + bottom_rgb[index] * ratio) for index in range(3))
        draw.line((0, y, width_px, y), fill=(*color, 255))
    return image


def paste_bg_layer(
    image: Image.Image,
    records: list[dict[str, Any]],
    layer: int,
    repo_root: Path,
    debug_draw: ImageDraw.ImageDraw | None = None,
) -> None:
    for record in records:
        if record["layer"] != layer:
            continue
        custom_paths = record.get("_assetPaths", {})
        asset = (
            Path(custom_paths["x1"])
            if record.get("custom") and custom_paths.get("x1")
            else repo_root / "data" / "item_bg_temp" / "x1" / f"{record['imageId']}.png"
        )
        x, y = record["pixelX"], record["pixelY"]
        if asset.is_file():
            with Image.open(asset) as source:
                sprite = source.convert("RGBA")
                image.alpha_composite(sprite, dest=(x, y))
                box = (x, y, x + sprite.width - 1, y + sprite.height - 1)
        else:
            box = (x, y, x + TILE_SIZE - 1, y + TILE_SIZE - 1)
            ImageDraw.Draw(image).rectangle(box, fill=(255, 0, 255, 130), outline=(255, 255, 255, 255))
        if debug_draw is not None:
            debug_draw.rectangle(box, outline=(255, 180, 40, 255), width=1)
            debug_draw.text((x + 2, y + 2), f"B{record['templateId']}/L{layer}", fill=(255, 255, 255, 255))


def draw_tiles(image: Image.Image, grid: list[list[int]], tile_sprites: dict[int, Image.Image]) -> None:
    draw = ImageDraw.Draw(image, "RGBA")
    for tile_y, row in enumerate(grid):
        for tile_x, tile_id in enumerate(row):
            if tile_id == 0:
                continue
            left, top = tile_x * TILE_SIZE, tile_y * TILE_SIZE
            sprite = tile_sprites.get(tile_id)
            if sprite is not None:
                image.alpha_composite(sprite, dest=(left, top))
            else:
                draw.rectangle((left, top, left + TILE_SIZE - 1, top + TILE_SIZE - 1), fill=tile_color(tile_id))
                draw.line((left, top, left + TILE_SIZE - 1, top), fill=(255, 255, 255, 70))


def draw_entities(image: Image.Image, plan: dict[str, Any], debug: bool) -> None:
    draw = ImageDraw.Draw(image, "RGBA")
    gameplay = plan["gameplay"]
    for index, waypoint in enumerate(gameplay.get("waypoints", [])):
        box = (waypoint["minX"], waypoint["minY"], waypoint["maxX"], waypoint["maxY"])
        draw.rectangle(box, fill=(180, 70, 255, 55), outline=(220, 150, 255, 230), width=2)
        if debug:
            draw.text((waypoint["minX"] + 2, max(0, waypoint["minY"] - 12)), f"WP{index}", fill=(245, 210, 255, 255))

    spawn = gameplay["playerSpawn"]
    x, y = spawn["x"], spawn["y"]
    draw.ellipse((x - 6, y - 18, x + 6, y - 6), fill=(40, 220, 255, 230), outline=(255, 255, 255, 255))
    draw.line((x, y - 6, x, y), fill=(255, 255, 255, 255), width=2)
    if debug:
        draw.text((x + 8, y - 20), "PLAYER", fill=(110, 235, 255, 255))

    for index, mob in enumerate(gameplay.get("mobs", [])):
        x, y = mob["mobX"], mob["mobY"]
        draw.polygon(((x, y - 18), (x - 8, y), (x + 8, y)), fill=(255, 75, 65, 235), outline=(255, 255, 255, 255))
        if debug:
            draw.text((x + 9, y - 18), f"M{mob['mobTemp']}#{index}", fill=(255, 180, 175, 255))

    for index, npc in enumerate(gameplay.get("npcs", [])):
        x, y = npc["npcX"], npc["npcY"]
        draw.rectangle((x - 6, y - 17, x + 6, y - 1), fill=(255, 210, 40, 235), outline=(255, 255, 255, 255))
        if debug:
            draw.text((x + 8, y - 18), f"N{npc['npcId']}#{index}", fill=(255, 235, 145, 255))


def add_debug_overlay(image: Image.Image, grid: list[list[int]], plan: dict[str, Any]) -> None:
    draw = ImageDraw.Draw(image, "RGBA")
    width_px, height_px = image.size
    for x in range(0, width_px + 1, TILE_SIZE):
        draw.line((x, 0, x, height_px), fill=(255, 255, 255, 55))
    for y in range(0, height_px + 1, TILE_SIZE):
        draw.line((0, y, width_px, y), fill=(255, 255, 255, 55))
    font = ImageFont.load_default()
    if len(grid) <= 45 and len(grid[0]) <= 100:
        for tile_y, row in enumerate(grid):
            for tile_x, tile_id in enumerate(row):
                if tile_id:
                    draw.text((tile_x * TILE_SIZE + 2, tile_y * TILE_SIZE + 2), str(tile_id), font=font, fill=(255, 255, 255, 225), stroke_width=1, stroke_fill=(0, 0, 0, 180))
    label = f"MAP {plan['map']['mapId']} | {len(grid[0])}x{len(grid)} tiles | tileset {plan['map']['tileSetId']}"
    draw.rectangle((0, 0, min(width_px, 430), 18), fill=(0, 0, 0, 170))
    draw.text((4, 3), label, font=font, fill=(255, 255, 255, 255))


def render_previews(
    output_dir: Path,
    plan: dict[str, Any],
    grid: list[list[int]],
    bg_records: list[dict[str, Any]],
    repo_root: Path,
    tile_sprites: dict[int, Image.Image],
) -> None:
    width_px = plan["map"]["widthTiles"] * TILE_SIZE
    height_px = plan["map"]["heightTiles"] * TILE_SIZE
    for debug, filename in ((False, "preview_clean.png"), (True, "preview_debug.png")):
        image = create_base_preview(width_px, height_px, plan["map"].get("bgId", 0))
        debug_draw = ImageDraw.Draw(image, "RGBA") if debug else None
        for layer in (1, 2, 3):
            paste_bg_layer(image, bg_records, layer, repo_root, debug_draw)
        draw_tiles(image, grid, tile_sprites)
        draw_entities(image, plan, debug)
        paste_bg_layer(image, bg_records, 4, repo_root, debug_draw)
        if debug:
            add_debug_overlay(image, grid, plan)
        image.save(output_dir / filename, format="PNG", optimize=False, compress_level=9)


def write_runtime_binaries(
    output_dir: Path,
    plan: dict[str, Any],
    grid: list[list[int]],
    bg_records: list[dict[str, Any]],
) -> tuple[Path, Path]:
    map_id = plan["map"]["mapId"]
    width, height = plan["map"]["widthTiles"], plan["map"]["heightTiles"]
    tile_bytes = bytes([width, height, *(tile for row in grid for tile in row)])
    tile_path = output_dir / "runtime" / "tile_map_data" / str(map_id)
    tile_path.parent.mkdir(parents=True, exist_ok=True)
    tile_path.write_bytes(tile_bytes)

    bg_payload = bytearray(struct.pack(">h", len(bg_records)))
    for record in bg_records:
        bg_payload.extend(struct.pack(">hhh", record["templateId"], record["tileX"], record["tileY"]))
    bg_path = output_dir / "runtime" / "item_bg_map_data" / str(map_id)
    bg_path.parent.mkdir(parents=True, exist_ok=True)
    bg_path.write_bytes(bytes(bg_payload))
    return tile_path, bg_path


def copy_custom_bg_assets(output_dir: Path, bg_records: list[dict[str, Any]]) -> list[dict[str, Any]]:
    install_records: dict[int, dict[str, Any]] = {}
    for record in bg_records:
        if record.get("requiresTemplateInstall") or record.get("custom"):
            install_records[int(record["templateId"])] = record

    copied: list[dict[str, Any]] = []
    seen_images: set[int] = set()
    for _, record in sorted(install_records.items()):
        output_assets: dict[str, str] = {}
        if record.get("custom") and record["imageId"] not in seen_images:
            seen_images.add(record["imageId"])
            for zoom in range(1, 5):
                key = f"x{zoom}"
                source_text = record.get("_assetPaths", {}).get(key)
                if not source_text:
                    raise FileNotFoundError(f"Custom asset {record['templateId']} thiếu source {key}")
                source = Path(source_text)
                if not source.is_file():
                    raise FileNotFoundError(f"Custom asset {record['templateId']} thiếu file {source}")
                target = output_dir / "runtime" / "item_bg_temp" / key / f"{record['imageId']}.png"
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(source, target)
                output_assets[key] = target.relative_to(output_dir).as_posix()
        copied.append(
            {
                "templateId": record["templateId"],
                "sourceTemplateId": record.get("sourceTemplateId", record["templateId"]),
                "imageId": record["imageId"],
                "layer": record["layer"],
                "dx": record["dx"],
                "dy": record["dy"],
                "generatedOffsetTemplate": bool(record.get("generatedOffsetTemplate", False)),
                "assets": output_assets,
            }
        )
    return copied


def build_manifest(
    output_dir: Path,
    plan_path: Path,
    plan: dict[str, Any],
    report: dict[str, Any],
    references: dict[str, Path],
) -> dict[str, Any]:
    artifacts: dict[str, dict[str, Any]] = {}
    for path in sorted(output_dir.rglob("*")):
        if not path.is_file() or path.name == "manifest.json":
            continue
        relative = path.relative_to(output_dir).as_posix()
        artifacts[relative] = {"sha256": sha256_file(path), "size": path.stat().st_size}
    return {
        "compiler": {"name": "teamobi-map-compiler", "version": COMPILER_VERSION},
        "status": "staging",
        "publishReady": report["summary"]["errors"] == 0,
        "mapId": plan["map"]["mapId"],
        "dimensions": {
            "widthTiles": plan["map"]["widthTiles"],
            "heightTiles": plan["map"]["heightTiles"],
            "tileSize": TILE_SIZE,
        },
        "input": {"sha256": sha256_file(plan_path)},
        "references": {
            name: {"sha256": sha256_file(path), "file": path.name}
            for name, path in sorted(references.items())
        },
        "validation": report["summary"],
        "artifacts": artifacts,
    }


def is_relative_to(path: Path, parent: Path) -> bool:
    try:
        path.relative_to(parent)
        return True
    except ValueError:
        return False


def install_staging_directory(staging: Path, output_dir: Path, force: bool) -> Path | None:
    previous = Path(str(output_dir) + ".previous")
    if output_dir.exists() and not force:
        raise FileExistsError(f"Output đã tồn tại: {output_dir}. Dùng --force để thay thế an toàn.")
    if not output_dir.exists():
        os.replace(staging, output_dir)
        return None

    if previous.exists():
        if previous.is_dir():
            shutil.rmtree(previous)
        else:
            previous.unlink()
    os.replace(output_dir, previous)
    try:
        os.replace(staging, output_dir)
    except Exception:
        os.replace(previous, output_dir)
        raise
    return previous


def compile_plan(
    plan_path: Path,
    output_dir: Path | None = None,
    repo_root: Path | None = None,
    force: bool = False,
) -> CompileResult:
    plan_path = plan_path.resolve()
    repo_root = (repo_root or find_repo_root(plan_path)).resolve()
    plan = load_json(plan_path)
    schema, schema_path = load_schema(repo_root)
    profiles, profile_path = load_tile_profiles(repo_root)
    registry, registry_source = load_bg_registry(repo_root)
    project_registry, project_registry_source = load_project_bg_registry(plan_path)
    offset_registry, offset_registry_source = load_project_offset_registry(plan_path)
    for template_id, template in project_registry.items():
        if template_id in registry:
            raise ValueError(f"Custom bg template ID {template_id} trùng registry runtime")
        registry[template_id] = template

    issues = validate_schema(plan, schema)
    if issues:
        report = {
            "compilerVersion": COMPILER_VERSION,
            "summary": {"errors": len(issues), "warnings": 0},
            "issues": issues,
        }
        raise CompilationFailed(report)

    issues.extend(validate_plan_contract(plan, profiles, registry, repo_root))
    profile = profiles[str(plan["map"]["tileSetId"])]
    if not any(issue["severity"] == "error" for issue in issues):
        grid, stats = compile_tile_grid(plan, profile)
        issues.extend(validate_ground_alignment(plan, grid))
    else:
        grid, stats = [], {"writeOperations": 0, "nonEmptyTiles": 0, "totalTiles": 0}

    issues.sort(key=lambda item: (0 if item["severity"] == "error" else 1, item["path"], item["code"]))
    error_count = sum(1 for issue in issues if issue["severity"] == "error")
    warning_count = sum(1 for issue in issues if issue["severity"] == "warning")
    report = {
        "compilerVersion": COMPILER_VERSION,
        "summary": {"errors": error_count, "warnings": warning_count},
        "stats": stats,
        "issues": issues,
    }
    if error_count:
        raise CompilationFailed(report)

    map_id = plan["map"]["mapId"]
    if output_dir is None:
        output_dir = repo_root / "map-tools" / "output" / f"map-{map_id}"
    output_dir = output_dir.resolve()
    runtime_data_root = (repo_root / "data").resolve()
    if is_relative_to(output_dir, runtime_data_root):
        raise ValueError("Output staging không được nằm trong data/ runtime")
    if output_dir == repo_root:
        raise ValueError("Output staging không được là repository root")

    output_dir.parent.mkdir(parents=True, exist_ok=True)
    staging = Path(tempfile.mkdtemp(prefix=f".{output_dir.name}.staging-", dir=output_dir.parent))
    installed = False
    try:
        bg_records = build_bg_records(plan, registry, repo_root, offset_registry)
        tile_sprites, tile_preview_paths, missing_tile_previews = load_tile_preview_sprites(
            repo_root,
            plan["map"]["tileSetId"],
            grid,
        )
        tile_path, bg_path = write_runtime_binaries(staging, plan, grid, bg_records)
        custom_templates = copy_custom_bg_assets(staging, bg_records)
        compiled_tile = {
            "mapId": map_id,
            "widthTiles": plan["map"]["widthTiles"],
            "heightTiles": plan["map"]["heightTiles"],
            "tileSetId": plan["map"]["tileSetId"],
            "matrix": grid,
            "previewAssets": {
                "source": "data/res/x2..x4/<tileset>$<tileId>",
                "resolvedTileIds": sorted(tile_preview_paths),
                "missingTileIds": missing_tile_previews,
                "fallback": "deterministic-color",
            },
            "binary": {
                "path": tile_path.relative_to(staging).as_posix(),
                "size": tile_path.stat().st_size,
                "sha256": sha256_file(tile_path),
            },
        }
        compiled_bg = {
            "mapId": map_id,
            "count": len(bg_records),
            "items": [public_bg_record(record) for record in bg_records],
            "customTemplates": custom_templates,
            "binary": {
                "path": bg_path.relative_to(staging).as_posix(),
                "size": bg_path.stat().st_size,
                "sha256": sha256_file(bg_path),
            },
        }
        template = build_map_template(plan)
        registry_output = {str(key): public_bg_template(value) for key, value in sorted(registry.items())}
        write_json(staging / "teamobi-map-plan.json", plan)
        write_json(staging / "compiled-tile-map.json", compiled_tile)
        write_json(staging / "compiled-bg-map.json", compiled_bg)
        write_json(staging / "compiled-map-template.json", template)
        write_json(staging / "bg_item_registry.json", registry_output)
        write_json(staging / "validation-report.json", report)
        (staging / "map-template.sql").write_text(build_map_sql(template), encoding="utf-8", newline="\n")
        (staging / "bg-item-template.sql").write_text(build_bg_template_sql(bg_records), encoding="utf-8", newline="\n")
        render_previews(staging, plan, grid, bg_records, repo_root, tile_sprites)
        references = {"schema": schema_path, "tileProfiles": profile_path, "bgRegistrySource": registry_source}
        for tile_id, tile_preview_path in sorted(tile_preview_paths.items()):
            references[f"tilePreview.{plan['map']['tileSetId']}.{tile_id}"] = tile_preview_path
        if project_registry_source is not None:
            references["projectAssetRegistry"] = project_registry_source
        if offset_registry_source is not None:
            references["projectGeneratedOffsetRegistry"] = offset_registry_source
        manifest = build_manifest(
            staging,
            plan_path,
            plan,
            report,
            references,
        )
        write_json(staging / "manifest.json", manifest)
        install_staging_directory(staging, output_dir, force)
        installed = True
        return CompileResult(output_dir=output_dir, map_id=map_id, manifest=manifest, report=report)
    finally:
        if not installed and staging.exists():
            shutil.rmtree(staging, ignore_errors=True)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Compile Teamobi map plan into a verified staging bundle")
    parser.add_argument("plan", type=Path, help="Path to teamobi-map-plan.json")
    parser.add_argument("--output", type=Path, help="Exact staging output directory")
    parser.add_argument("--repo-root", type=Path, help="Repository root (auto-detected by default)")
    parser.add_argument("--force", action="store_true", help="Atomically replace output and keep one .previous rollback")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        result = compile_plan(args.plan, args.output, args.repo_root, args.force)
    except CompilationFailed as error:
        print(json.dumps(error.report, ensure_ascii=False, indent=2, sort_keys=True), file=sys.stderr)
        return 2
    except (FileExistsError, FileNotFoundError, ValueError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 3
    summary = {
        "status": "ok",
        "mapId": result.map_id,
        "output": str(result.output_dir),
        "errors": result.report["summary"]["errors"],
        "warnings": result.report["summary"]["warnings"],
        "manifestSha256": sha256_file(result.output_dir / "manifest.json"),
    }
    print(json.dumps(summary, ensure_ascii=False, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
