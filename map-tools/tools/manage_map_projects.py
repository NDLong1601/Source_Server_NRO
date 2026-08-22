#!/usr/bin/env python3
"""Project and guarded publishing service for the Teamobi map editor."""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import struct
import sys
import tempfile
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from PIL import Image


TOOLS_DIR = Path(__file__).resolve().parent
if str(TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(TOOLS_DIR))

import compile_map_plan as compiler


PROJECT_VERSION = 1
ACCEPTANCE_VERSION = 1
PROJECT_ID_PATTERN = re.compile(r"^[a-z0-9][a-z0-9-]{0,63}$")
RUNTIME_TILE_RELATIVE = Path("data/map/tile_map_data")
RUNTIME_BG_RELATIVE = Path("data/map/item_bg_map_data")
MAP_VERSION_RELATIVE = Path("data/map/version.txt")
ACCEPTANCE_CHECK_KEYS = (
    "clientLogin",
    "mapEntered",
    "visualMatchesPreview",
    "layersCorrect",
    "collisionAndGround",
    "waypoints",
    "npcs",
    "mobs",
    "zoomAssets",
    "noClientErrors",
)
ACCEPTANCE_ALWAYS_REQUIRED = (
    "clientLogin",
    "mapEntered",
    "visualMatchesPreview",
    "layersCorrect",
    "collisionAndGround",
    "zoomAssets",
    "noClientErrors",
)


class ProjectError(Exception):
    """Stable error type returned by the project CLI."""


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def configure_cli_utf8() -> None:
    """Keep Vietnamese JSON stable when Windows launches Python with cp1252 stdio."""
    for stream in (sys.stdout, sys.stderr):
        reconfigure = getattr(stream, "reconfigure", None)
        if callable(reconfigure):
            try:
                reconfigure(encoding="utf-8", errors="strict")
            except (OSError, ValueError):
                pass


def canonical_json_bytes(value: Any) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode("utf-8")


def atomic_write_bytes(path: Path, payload: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.parent / f".{path.name}.tmp-{os.getpid()}-{uuid.uuid4().hex}"
    try:
        with temporary.open("wb") as stream:
            stream.write(payload)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if temporary.exists():
            temporary.unlink()


def atomic_write_json(path: Path, value: Any) -> None:
    atomic_write_bytes(path, canonical_json_bytes(value))


def load_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8-sig") as stream:
        return json.load(stream)


def get_projects_root(repo_root: Path, projects_root: Path | None = None) -> Path:
    return (projects_root or repo_root / "map-tools" / "projects").resolve()


def get_releases_root(repo_root: Path, releases_root: Path | None = None) -> Path:
    return (releases_root or repo_root / "map-tools" / "releases").resolve()


def validate_project_id(project_id: str) -> str:
    project_id = project_id.strip().lower()
    if not PROJECT_ID_PATTERN.fullmatch(project_id):
        raise ProjectError("Project ID chỉ được chứa chữ thường, số, dấu gạch ngang và tối đa 64 ký tự")
    return project_id


def validate_release_id(release_id: str) -> str:
    if not re.fullmatch(r"^[0-9]{8}T[0-9]{6}Z-[0-9a-f]{12}$", release_id):
        raise ProjectError("Release ID không hợp lệ")
    return release_id


def project_directory(projects_root: Path, project_id: str) -> Path:
    return projects_root / validate_project_id(project_id)


def project_paths(projects_root: Path, project_id: str) -> tuple[Path, Path, Path]:
    directory = project_directory(projects_root, project_id)
    return directory, directory / "project.json", directory / "teamobi-map-plan.json"


def load_project(projects_root: Path, project_id: str) -> tuple[dict[str, Any], dict[str, Any], Path]:
    directory, metadata_path, plan_path = project_paths(projects_root, project_id)
    if not metadata_path.is_file() or not plan_path.is_file():
        raise ProjectError(f"Không tìm thấy project {project_id}")
    metadata = load_json(metadata_path)
    plan = load_json(plan_path)
    if metadata.get("projectId") != validate_project_id(project_id):
        raise ProjectError(f"Metadata project {project_id} không hợp lệ")
    return metadata, plan, directory


def new_default_plan(
    map_id: int,
    name: str,
    width_tiles: int,
    height_tiles: int,
    tile_set_id: int,
) -> dict[str, Any]:
    if map_id < 0 or map_id > 254:
        raise ProjectError("Map ID phải nằm trong khoảng 0..254")
    if width_tiles < 1 or width_tiles > 127 or height_tiles < 1 or height_tiles > 127:
        raise ProjectError("Kích thước map phải nằm trong khoảng 1..127 tile")
    if tile_set_id < 1 or tile_set_id > 42:
        raise ProjectError("Tileset ID phải nằm trong khoảng 1..42")
    name = name.strip()
    if not name:
        raise ProjectError("Tên map không được để trống")

    ground_y = max(0, height_tiles - 2)
    spawn_x = min(24, max(0, (width_tiles - 1) * compiler.TILE_SIZE))
    return {
        "formatVersion": 1,
        "map": {
            "mapId": map_id,
            "name": name,
            "widthTiles": width_tiles,
            "heightTiles": height_tiles,
            "tileSize": compiler.TILE_SIZE,
            "tileSetId": tile_set_id,
            "planetId": 0,
            "bgType": 0,
            "bgId": 0,
            "type": 0,
            "zones": 10,
            "maxPlayersPerZone": 15,
            "isMapDouble": False,
        },
        "terrain": {
            "baseTile": 0,
            "platforms": [
                {
                    "startTileX": 0,
                    "endTileX": width_tiles - 1,
                    "tileY": ground_y,
                    "thicknessTiles": height_tiles - ground_y,
                }
            ],
        },
        "bgItems": [],
        "gameplay": {
            "playerSpawn": {"x": spawn_x, "y": ground_y * compiler.TILE_SIZE},
            "mobs": [],
            "npcs": [],
            "waypoints": [],
        },
        "metadata": {
            "description": "Draft được tạo bởi Admin Map Editor Giai đoạn 2.",
            "author": "NRO Admin Data",
            "tags": ["admin-map-editor"],
        },
    }


def create_project_from_plan(
    repo_root: Path,
    plan: dict[str, Any],
    source: dict[str, Any],
    projects_root: Path | None = None,
    requested_project_id: str | None = None,
) -> dict[str, Any]:
    if not isinstance(plan, dict) or not isinstance(plan.get("map"), dict):
        raise ProjectError("Map plan phải là JSON object và có object map")
    map_id = plan["map"].get("mapId")
    if not isinstance(map_id, int) or isinstance(map_id, bool) or map_id < 0 or map_id > 254:
        raise ProjectError("Map plan có mapId không hợp lệ")

    project_id = validate_project_id(requested_project_id or f"map-{map_id}")
    root = get_projects_root(repo_root, projects_root)
    directory, metadata_path, plan_path = project_paths(root, project_id)
    if directory.exists():
        raise ProjectError(f"Project {project_id} đã tồn tại")

    created_at = utc_now()
    metadata = {
        "projectVersion": PROJECT_VERSION,
        "projectId": project_id,
        "mapId": map_id,
        "displayName": str(plan["map"].get("name", f"Map {map_id}")),
        "status": "draft",
        "revision": 1,
        "createdAt": created_at,
        "updatedAt": created_at,
        "source": source,
        "lastCompile": None,
        "lastReleasePlan": None,
        "lastRelease": None,
    }
    directory.mkdir(parents=True, exist_ok=False)
    try:
        atomic_write_json(plan_path, plan)
        atomic_write_json(metadata_path, metadata)
    except Exception:
        shutil.rmtree(directory, ignore_errors=True)
        raise
    return get_project(repo_root, project_id, root)


def create_project(
    repo_root: Path,
    map_id: int,
    name: str,
    width_tiles: int,
    height_tiles: int,
    tile_set_id: int,
    projects_root: Path | None = None,
    requested_project_id: str | None = None,
) -> dict[str, Any]:
    plan = new_default_plan(map_id, name, width_tiles, height_tiles, tile_set_id)
    return create_project_from_plan(
        repo_root,
        plan,
        {"kind": "new"},
        projects_root,
        requested_project_id,
    )


def parse_nested_rows(value: Any, label: str, expected_length: int) -> list[list[Any]]:
    if value is None or value == "":
        return []
    parsed = json.loads(value) if isinstance(value, str) else value
    if not isinstance(parsed, list):
        raise ProjectError(f"Dữ liệu {label} trong map_template không phải array")
    rows: list[list[Any]] = []
    for index, item in enumerate(parsed):
        row = json.loads(item) if isinstance(item, str) else item
        if not isinstance(row, list) or len(row) < expected_length:
            raise ProjectError(f"Dòng {label}[{index}] không đúng contract {expected_length} phần tử")
        rows.append(row)
    return rows


def read_runtime_map(repo_root: Path, map_id: int) -> tuple[list[list[int]], list[dict[str, int]]]:
    tile_path = repo_root / RUNTIME_TILE_RELATIVE / str(map_id)
    bg_path = repo_root / RUNTIME_BG_RELATIVE / str(map_id)
    if not tile_path.is_file():
        raise ProjectError(f"Thiếu tile runtime cho map {map_id}: {tile_path}")
    tile_data = tile_path.read_bytes()
    if len(tile_data) < 2:
        raise ProjectError(f"Tile runtime map {map_id} ngắn hơn header 2 byte")
    width, height = tile_data[0], tile_data[1]
    expected = 2 + width * height
    if width < 1 or height < 1 or len(tile_data) != expected:
        raise ProjectError(
            f"Tile runtime map {map_id} không hợp lệ: {width}x{height}, {len(tile_data)} byte, cần {expected}"
        )
    matrix = [
        list(tile_data[2 + row * width : 2 + (row + 1) * width])
        for row in range(height)
    ]

    bg_items: list[dict[str, int]] = []
    if bg_path.is_file():
        bg_data = bg_path.read_bytes()
        if len(bg_data) < 2:
            raise ProjectError(f"Background runtime map {map_id} ngắn hơn header 2 byte")
        count = struct.unpack(">h", bg_data[:2])[0]
        expected_bg = 2 + count * 6
        if count < 0 or len(bg_data) != expected_bg:
            raise ProjectError(
                f"Background runtime map {map_id} không hợp lệ: count={count}, {len(bg_data)} byte, cần {expected_bg}"
            )
        for index in range(count):
            template_id, tile_x, tile_y = struct.unpack(">hhh", bg_data[2 + index * 6 : 8 + index * 6])
            bg_items.append({"templateId": template_id, "tileX": tile_x, "tileY": tile_y})
    return matrix, bg_items


def first_ground_point(matrix: list[list[int]]) -> dict[str, int]:
    height = len(matrix)
    width = len(matrix[0])
    preferred_columns = list(range(min(2, width), width)) + list(range(0, min(2, width)))
    for tile_x in preferred_columns:
        for tile_y in range(height):
            if matrix[tile_y][tile_x] != 0:
                return {"x": tile_x * compiler.TILE_SIZE, "y": tile_y * compiler.TILE_SIZE}
    return {"x": 0, "y": max(0, height - 1) * compiler.TILE_SIZE}


def import_runtime_project(
    repo_root: Path,
    map_id: int,
    template: dict[str, Any],
    projects_root: Path | None = None,
    requested_project_id: str | None = None,
) -> dict[str, Any]:
    matrix, bg_items = read_runtime_map(repo_root, map_id)
    if int(template.get("id", map_id)) != map_id:
        raise ProjectError("Map ID của database snapshot không khớp runtime")
    data = template.get("data", [])
    if isinstance(data, str):
        data = json.loads(data)
    if not isinstance(data, list):
        data = []

    type_id = int(template.get("type", data[0] if len(data) > 0 else 0))
    planet_id = int(template.get("planetId", template.get("planet_id", data[1] if len(data) > 1 else 0)))
    bg_type = int(template.get("bgType", template.get("bg_type", data[2] if len(data) > 2 else 0)))
    tile_id = int(template.get("tileId", template.get("tile_id", data[3] if len(data) > 3 else 1)))
    bg_id = int(template.get("bgId", template.get("bg_id", data[4] if len(data) > 4 else 0)))
    waypoints = parse_nested_rows(template.get("waypoints", []), "waypoints", 10)
    mobs = parse_nested_rows(template.get("mobs", []), "mobs", 5)
    npcs = parse_nested_rows(template.get("npcs", []), "npcs", 3)

    plan = {
        "formatVersion": 1,
        "map": {
            "mapId": map_id,
            "name": str(template.get("name", template.get("NAME", f"Map {map_id}"))),
            "widthTiles": len(matrix[0]),
            "heightTiles": len(matrix),
            "tileSize": compiler.TILE_SIZE,
            "tileSetId": tile_id,
            "planetId": planet_id,
            "bgType": bg_type,
            "bgId": bg_id,
            "type": type_id,
            "zones": int(template.get("zones", 10)),
            "maxPlayersPerZone": int(template.get("maxPlayer", template.get("max_player", 15))),
            "isMapDouble": bool(int(template.get("isMapDouble", template.get("is_map_double", 0)))),
        },
        "terrain": {"matrix": matrix},
        "bgItems": bg_items,
        "gameplay": {
            "playerSpawn": first_ground_point(matrix),
            "mobs": [
                {"mobTemp": int(row[0]), "mobLevel": int(row[1]), "mobHp": int(row[2]), "mobX": int(row[3]), "mobY": int(row[4])}
                for row in mobs
            ],
            "npcs": [
                {"npcId": int(row[0]), "npcX": int(row[1]), "npcY": int(row[2])}
                for row in npcs
            ],
            "waypoints": [
                {
                    "name": str(row[0]),
                    "minX": int(row[1]),
                    "minY": int(row[2]),
                    "maxX": int(row[3]),
                    "maxY": int(row[4]),
                    "isEnter": bool(int(row[5])),
                    "isOffline": bool(int(row[6])),
                    "goMap": int(row[7]),
                    "goX": int(row[8]),
                    "goY": int(row[9]),
                }
                for row in waypoints
            ],
        },
        "metadata": {
            "description": f"Import từ runtime và map_template ID {map_id}; playerSpawn được suy ra và cần kiểm tra.",
            "author": "NRO Admin Data",
            "tags": ["runtime-import", "admin-map-editor"],
        },
    }
    return create_project_from_plan(
        repo_root,
        plan,
        {"kind": "runtime-import", "mapId": map_id, "importedAt": utc_now()},
        projects_root,
        requested_project_id,
    )


def project_summary(metadata: dict[str, Any], directory: Path) -> dict[str, Any]:
    return {
        "projectId": metadata.get("projectId"),
        "mapId": metadata.get("mapId"),
        "displayName": metadata.get("displayName"),
        "status": metadata.get("status"),
        "revision": metadata.get("revision"),
        "updatedAt": metadata.get("updatedAt"),
        "source": metadata.get("source"),
        "directory": str(directory),
        "lastCompile": metadata.get("lastCompile"),
        "lastRelease": metadata.get("lastRelease"),
    }


def list_projects(repo_root: Path, projects_root: Path | None = None) -> dict[str, Any]:
    root = get_projects_root(repo_root, projects_root)
    if not root.is_dir():
        return {"status": "ok", "projects": [], "issues": []}
    projects: list[dict[str, Any]] = []
    issues: list[dict[str, str]] = []
    for directory in sorted(path for path in root.iterdir() if path.is_dir()):
        try:
            metadata = load_json(directory / "project.json")
            projects.append(project_summary(metadata, directory))
        except Exception as error:
            issues.append({"projectId": directory.name, "message": str(error)})
    projects.sort(key=lambda item: str(item.get("updatedAt", "")), reverse=True)
    return {"status": "ok", "projects": projects, "issues": issues}


def get_build_paths(repo_root: Path, project_id: str) -> tuple[Path, Path, Path]:
    output = repo_root / "map-tools" / "output" / "projects" / validate_project_id(project_id)
    return output, output / "preview_clean.png", output / "preview_debug.png"


def get_project(repo_root: Path, project_id: str, projects_root: Path | None = None) -> dict[str, Any]:
    root = get_projects_root(repo_root, projects_root)
    metadata, plan, directory = load_project(root, project_id)
    output, clean, debug = get_build_paths(repo_root, project_id)
    revisions_dir = directory / "revisions"
    revisions = []
    if revisions_dir.is_dir():
        revisions = sorted(path.stem.replace("revision-", "") for path in revisions_dir.glob("revision-*.json"))
    return {
        "status": "ok",
        "project": project_summary(metadata, directory),
        "metadata": metadata,
        "plan": plan,
        "build": {
            "directory": str(output),
            "exists": (output / "manifest.json").is_file(),
            "previewCleanPath": str(clean) if clean.is_file() else "",
            "previewDebugPath": str(debug) if debug.is_file() else "",
        },
        "revisions": revisions,
    }


def load_asset_registry(directory: Path) -> dict[str, Any]:
    path = directory / "assets" / "registry.json"
    if not path.is_file():
        return {"assetRegistryVersion": 1, "assets": []}
    payload = load_json(path)
    if not isinstance(payload, dict) or not isinstance(payload.get("assets"), list):
        raise ProjectError(f"Asset registry không hợp lệ: {path}")
    return payload


def load_generated_offset_registry(directory: Path) -> dict[str, Any]:
    path = directory / "assets" / "generated-offsets.json"
    if not path.is_file():
        return {"generatedOffsetRegistryVersion": 1, "templates": []}
    payload = load_json(path)
    if not isinstance(payload, dict) or not isinstance(payload.get("templates"), list):
        raise ProjectError(f"Generated offset registry không hợp lệ: {path}")
    return payload


def editor_asset_catalog(
    repo_root: Path,
    project_directory: Path,
    combined_registry: dict[int, dict[str, Any]],
) -> list[dict[str, Any]]:
    catalog: list[dict[str, Any]] = []
    for template_id, template in sorted(combined_registry.items()):
        preview = compiler.resolve_bg_asset_path(template, 1, repo_root)
        if not preview.is_file():
            continue
        catalog.append(
            {
                "templateId": template_id,
                "imageId": template["imageId"],
                "name": template.get("name", f"BG {template_id}"),
                "layer": template["layer"],
                "dx": template["dx"],
                "dy": template["dy"],
                "custom": bool(template.get("custom", False)),
                "previewPath": str(preview),
                "hasAllZooms": all(
                    compiler.resolve_bg_asset_path(template, zoom, repo_root).is_file()
                    for zoom in range(1, 5)
                ),
            }
        )
    return catalog


def editor_tile_catalog(repo_root: Path, tile_set_id: int) -> list[dict[str, Any]]:
    catalog: list[dict[str, Any]] = []
    for tile_id in range(1, 128):
        preview_path, source_zoom = compiler.resolve_tile_preview_path(repo_root, tile_set_id, tile_id)
        if preview_path is None:
            continue
        catalog.append(
            {
                "tileId": tile_id,
                "previewPath": str(preview_path),
                "sourceZoom": source_zoom,
            }
        )
    return catalog


def get_editor_state(
    repo_root: Path,
    project_id: str,
    projects_root: Path | None = None,
) -> dict[str, Any]:
    root = get_projects_root(repo_root, projects_root)
    metadata, plan, directory = load_project(root, project_id)
    schema, _ = compiler.load_schema(repo_root)
    schema_issues = compiler.validate_schema(plan, schema)
    if schema_issues:
        first = schema_issues[0]
        raise ProjectError(f"Draft chưa đúng schema tại {first['path']}: {first['message']}")
    profiles, _ = compiler.load_tile_profiles(repo_root)
    profile = profiles.get(str(plan["map"]["tileSetId"]))
    if profile is None:
        raise ProjectError(f"Không có tile profile {plan['map']['tileSetId']}")
    try:
        matrix, stats = compiler.compile_tile_grid(plan, profile)
    except (IndexError, KeyError, TypeError, ValueError) as error:
        raise ProjectError(f"Không materialize được tile matrix: {error}") from error
    registry, _ = compiler.load_bg_registry(repo_root)
    custom_registry, _ = compiler.load_project_bg_registry(directory / "teamobi-map-plan.json")
    for template_id, template in custom_registry.items():
        if template_id in registry:
            raise ProjectError(f"Custom bg template ID {template_id} trùng registry runtime")
        registry[template_id] = template
    frequent_tiles: list[int] = []
    for values in profile.get("frequentTiles", {}).values():
        for value in values:
            if value not in frequent_tiles:
                frequent_tiles.append(value)
    for value in profile.get("semanticDefaults", {}).values():
        if isinstance(value, int) and value not in frequent_tiles:
            frequent_tiles.append(value)
    tile_catalog = editor_tile_catalog(repo_root, plan["map"]["tileSetId"])
    available_tiles = [0, *(item["tileId"] for item in tile_catalog)]
    return {
        "status": "ok",
        "projectId": project_id,
        "mapId": metadata["mapId"],
        "matrix": matrix,
        "stats": stats,
        "materializedFromSemanticTerrain": plan.get("terrain", {}).get("matrix") is None,
        "tilePalette": {
            "tileSetId": plan["map"]["tileSetId"],
            "frequent": frequent_tiles,
            "all": list(range(0, 128)),
            "available": available_tiles,
            "tiles": tile_catalog,
            "visualSource": "client-resource" if tile_catalog else "deterministic-color",
            "physics": profile.get("physics", {}),
        },
        "assets": editor_asset_catalog(repo_root, directory, registry),
    }


RESIZE_ANCHORS = {
    "top-left": ("left", "top"),
    "top": ("center", "top"),
    "top-right": ("right", "top"),
    "left": ("left", "center"),
    "center": ("center", "center"),
    "right": ("right", "center"),
    "bottom-left": ("left", "bottom"),
    "bottom": ("center", "bottom"),
    "bottom-right": ("right", "bottom"),
}


def resize_axis_offset(old_size: int, new_size: int, alignment: str) -> int:
    if alignment in ("left", "top"):
        return 0
    if alignment == "center":
        return (new_size - old_size) // 2
    return new_size - old_size


def clamp_coordinate(value: int, minimum: int, maximum: int) -> int:
    return max(minimum, min(maximum, value))


def resize_waypoint_axis(minimum: int, maximum: int, offset: int, limit: int) -> tuple[int, int, bool]:
    shifted_minimum = minimum + offset
    shifted_maximum = maximum + offset
    new_minimum = clamp_coordinate(shifted_minimum, 0, limit)
    new_maximum = clamp_coordinate(shifted_maximum, 0, limit)
    changed_by_clamp = new_minimum != shifted_minimum or new_maximum != shifted_maximum
    if new_maximum <= new_minimum:
        minimum_size = min(4, limit)
        if new_minimum >= limit:
            new_minimum = max(0, limit - minimum_size)
            new_maximum = limit
        else:
            new_maximum = min(limit, new_minimum + minimum_size)
            new_minimum = max(0, new_maximum - minimum_size)
        changed_by_clamp = True
    return new_minimum, new_maximum, changed_by_clamp


def resize_plan_for_editor(
    repo_root: Path,
    plan: dict[str, Any],
    new_width: int,
    new_height: int,
    anchor: str,
) -> dict[str, Any]:
    if new_width < 1 or new_width > 127 or new_height < 1 or new_height > 127:
        raise ProjectError("Kích thước map sau resize phải nằm trong khoảng 1..127 tile")
    if anchor not in RESIZE_ANCHORS:
        raise ProjectError(f"Anchor resize không hợp lệ: {anchor}")
    schema, _ = compiler.load_schema(repo_root)
    schema_issues = compiler.validate_schema(plan, schema)
    if schema_issues:
        first = schema_issues[0]
        raise ProjectError(f"Plan resize chưa đúng schema tại {first['path']}: {first['message']}")
    profiles, _ = compiler.load_tile_profiles(repo_root)
    profile = profiles.get(str(plan["map"]["tileSetId"]))
    if profile is None:
        raise ProjectError(f"Không có tile profile {plan['map']['tileSetId']}")
    try:
        old_matrix, _ = compiler.compile_tile_grid(plan, profile)
    except (IndexError, KeyError, TypeError, ValueError) as error:
        raise ProjectError(f"Không materialize được matrix trước resize: {error}") from error

    old_height = len(old_matrix)
    old_width = len(old_matrix[0])
    horizontal, vertical = RESIZE_ANCHORS[anchor]
    offset_x = resize_axis_offset(old_width, new_width, horizontal)
    offset_y = resize_axis_offset(old_height, new_height, vertical)
    new_matrix = [[0 for _ in range(new_width)] for _ in range(new_height)]
    cropped_non_empty = 0
    cropped_cells = 0
    for old_y, row in enumerate(old_matrix):
        for old_x, tile_id in enumerate(row):
            new_x, new_y = old_x + offset_x, old_y + offset_y
            if 0 <= new_x < new_width and 0 <= new_y < new_height:
                new_matrix[new_y][new_x] = tile_id
            else:
                cropped_cells += 1
                if tile_id:
                    cropped_non_empty += 1

    resized = json.loads(json.dumps(plan, ensure_ascii=False))
    resized["map"]["widthTiles"] = new_width
    resized["map"]["heightTiles"] = new_height
    resized["terrain"] = {"matrix": new_matrix}

    clamped_bg_items = 0
    for item in resized.get("bgItems", []):
        shifted_x = item["tileX"] + offset_x
        shifted_y = item["tileY"] + offset_y
        item["tileX"] = clamp_coordinate(shifted_x, -5, new_width + 5)
        item["tileY"] = clamp_coordinate(shifted_y, -5, new_height + 5)
        if item["tileX"] != shifted_x or item["tileY"] != shifted_y:
            clamped_bg_items += 1

    pixel_offset_x = offset_x * compiler.TILE_SIZE
    pixel_offset_y = offset_y * compiler.TILE_SIZE
    width_pixels = new_width * compiler.TILE_SIZE
    height_pixels = new_height * compiler.TILE_SIZE
    clamped_entities: list[str] = []

    def shift_entity(entity: dict[str, Any], x_key: str, y_key: str, label: str) -> None:
        shifted_x = entity[x_key] + pixel_offset_x
        shifted_y = entity[y_key] + pixel_offset_y
        entity[x_key] = clamp_coordinate(shifted_x, 0, width_pixels)
        entity[y_key] = clamp_coordinate(shifted_y, 0, height_pixels)
        if entity[x_key] != shifted_x or entity[y_key] != shifted_y:
            clamped_entities.append(label)

    gameplay = resized["gameplay"]
    shift_entity(gameplay["playerSpawn"], "x", "y", "player")
    for index, npc in enumerate(gameplay.get("npcs", [])):
        shift_entity(npc, "npcX", "npcY", f"npc[{index}]")
    for index, mob in enumerate(gameplay.get("mobs", [])):
        shift_entity(mob, "mobX", "mobY", f"mob[{index}]")
    clamped_waypoints: list[int] = []
    current_map_id = resized["map"]["mapId"]
    for index, waypoint in enumerate(gameplay.get("waypoints", [])):
        waypoint["minX"], waypoint["maxX"], clamped_x = resize_waypoint_axis(
            waypoint["minX"], waypoint["maxX"], pixel_offset_x, width_pixels
        )
        waypoint["minY"], waypoint["maxY"], clamped_y = resize_waypoint_axis(
            waypoint["minY"], waypoint["maxY"], pixel_offset_y, height_pixels
        )
        if clamped_x or clamped_y:
            clamped_waypoints.append(index)
        if waypoint.get("goMap") == current_map_id:
            waypoint["goX"] = clamp_coordinate(waypoint["goX"] + pixel_offset_x, 0, width_pixels)
            waypoint["goY"] = clamp_coordinate(waypoint["goY"] + pixel_offset_y, 0, height_pixels)

    report = {
        "oldDimensions": {"widthTiles": old_width, "heightTiles": old_height},
        "newDimensions": {"widthTiles": new_width, "heightTiles": new_height},
        "anchor": anchor,
        "offsetTiles": {"x": offset_x, "y": offset_y},
        "croppedCells": cropped_cells,
        "croppedNonEmptyTiles": cropped_non_empty,
        "clampedBgItems": clamped_bg_items,
        "clampedEntities": clamped_entities,
        "clampedWaypoints": clamped_waypoints,
        "requiresCompileValidation": True,
    }
    return {"status": "ok", "plan": resized, "matrix": new_matrix, "report": report}


def collect_used_asset_ids(repo_root: Path, projects_root: Path) -> tuple[set[int], set[int]]:
    static_registry, _ = compiler.load_bg_registry(repo_root)
    template_ids = set(static_registry)
    image_ids = {int(item["imageId"]) for item in static_registry.values()}
    for zoom in range(1, 5):
        directory = repo_root / "data" / "item_bg_temp" / f"x{zoom}"
        if directory.is_dir():
            for path in directory.glob("*.png"):
                if path.stem.isdigit():
                    image_ids.add(int(path.stem))
    if projects_root.is_dir():
        for directory in projects_root.iterdir():
            if not directory.is_dir():
                continue
            try:
                payload = load_asset_registry(directory)
            except (ProjectError, OSError, json.JSONDecodeError):
                continue
            for asset in payload["assets"]:
                if isinstance(asset, dict):
                    if isinstance(asset.get("templateId"), int):
                        template_ids.add(asset["templateId"])
                    if isinstance(asset.get("imageId"), int):
                        image_ids.add(asset["imageId"])
            try:
                generated = load_generated_offset_registry(directory)
            except (ProjectError, OSError, json.JSONDecodeError):
                continue
            for template in generated["templates"]:
                if isinstance(template, dict) and isinstance(template.get("templateId"), int):
                    template_ids.add(template["templateId"])
    return template_ids, image_ids


def ensure_project_offset_registry(
    repo_root: Path,
    projects_root: Path,
    directory: Path,
    plan: dict[str, Any],
    minimum_template_id: int | None = None,
) -> dict[str, Any]:
    requested_keys = sorted(
        {
            (
                int(item["templateId"]),
                int(item.get("offsetX", 0)),
                int(item.get("offsetY", 0)),
            )
            for item in plan.get("bgItems", [])
            if int(item.get("offsetX", 0)) != 0 or int(item.get("offsetY", 0)) != 0
        }
    )
    registry = load_generated_offset_registry(directory)
    by_key: dict[tuple[int, int, int], dict[str, Any]] = {}
    local_ids: set[int] = set()
    for index, item in enumerate(registry["templates"]):
        if not isinstance(item, dict):
            raise ProjectError(f"Generated offset templates[{index}] không phải object")
        try:
            key = (
                int(item["sourceTemplateId"]),
                int(item["offsetX"]),
                int(item["offsetY"]),
            )
            template_id = int(item["templateId"])
        except (KeyError, TypeError, ValueError) as error:
            raise ProjectError(f"Generated offset templates[{index}] thiếu field bắt buộc") from error
        if key in by_key or template_id in local_ids:
            raise ProjectError("Generated offset registry có key hoặc template ID bị trùng")
        by_key[key] = item
        local_ids.add(template_id)

    template_ids, _ = collect_used_asset_ids(repo_root, projects_root)
    requested_minimum = max(0, int(minimum_template_id or 0))
    changed = False
    for key in requested_keys:
        if key in by_key:
            continue
        template_id = next_available_id(template_ids, requested_minimum)
        template_ids.add(template_id)
        entry = {
            "sourceTemplateId": key[0],
            "offsetX": key[1],
            "offsetY": key[2],
            "templateId": template_id,
            "createdAt": utc_now(),
        }
        registry["templates"].append(entry)
        by_key[key] = entry
        changed = True
    if changed:
        registry["templates"] = sorted(
            registry["templates"],
            key=lambda item: (
                int(item["sourceTemplateId"]),
                int(item["offsetX"]),
                int(item["offsetY"]),
            ),
        )
        registry["updatedAt"] = utc_now()
        atomic_write_json(directory / "assets" / "generated-offsets.json", registry)
    return registry


def next_available_id(used: set[int], requested: int | None) -> int:
    candidate = max(used, default=-1) + 1
    if requested is not None:
        candidate = max(candidate, requested)
    while candidate in used:
        candidate += 1
    if candidate > 32767:
        raise ProjectError("Đã hết ID signed-short cho background asset")
    return candidate


def upload_project_asset(
    repo_root: Path,
    project_id: str,
    source_path: Path,
    name: str,
    layer: int,
    dx: int,
    dy: int,
    projects_root: Path | None = None,
    requested_template_id: int | None = None,
    requested_image_id: int | None = None,
    target_width: int = 0,
    target_height: int = 0,
) -> dict[str, Any]:
    root = get_projects_root(repo_root, projects_root)
    metadata, _, directory = load_project(root, project_id)
    source_path = source_path.resolve()
    if not source_path.is_file():
        raise ProjectError(f"Không tìm thấy PNG upload: {source_path}")
    if layer not in (1, 2, 3, 4):
        raise ProjectError("Layer background phải nằm trong 1..4")
    if dx < -32768 or dx > 32767 or dy < -32768 or dy > 32767:
        raise ProjectError("Dx/Dy phải nằm trong signed-short")
    if bool(target_width) != bool(target_height):
        raise ProjectError("Khi resize x4 phải nhập cả Width và Height")

    try:
        with Image.open(source_path) as opened:
            opened.load()
            if opened.format != "PNG":
                raise ProjectError("Asset upload phải là file PNG")
            source = opened.convert("RGBA")
    except (OSError, ValueError) as error:
        raise ProjectError(f"Không đọc được PNG upload: {error}") from error
    width = target_width or source.width
    height = target_height or source.height
    if width < 1 or height < 1 or width > 4096 or height > 4096 or width * height > 16_777_216:
        raise ProjectError("Kích thước x4 phải từ 1..4096 mỗi chiều và tối đa 16 triệu pixel")

    template_ids, image_ids = collect_used_asset_ids(repo_root, root)
    template_id = next_available_id(template_ids, requested_template_id)
    image_id = next_available_id(image_ids, requested_image_id)
    assets_dir = directory / "assets"
    assets_dir.mkdir(parents=True, exist_ok=True)
    staging = Path(tempfile.mkdtemp(prefix=".upload-", dir=assets_dir))
    installed: list[Path] = []
    try:
        if source.size != (width, height):
            source_x4 = source.resize((width, height), Image.Resampling.LANCZOS)
        else:
            source_x4 = source
        sizes: dict[str, dict[str, int]] = {}
        paths: dict[str, str] = {}
        for zoom in range(1, 5):
            key = f"x{zoom}"
            zoom_width = max(1, round(width * zoom / 4))
            zoom_height = max(1, round(height * zoom / 4))
            image = source_x4 if zoom == 4 else source_x4.resize((zoom_width, zoom_height), Image.Resampling.LANCZOS)
            staged = staging / key / f"{image_id}.png"
            staged.parent.mkdir(parents=True, exist_ok=True)
            image.save(staged, format="PNG", optimize=False, compress_level=9)
            sizes[key] = {"width": zoom_width, "height": zoom_height}
            paths[key] = (Path("assets") / key / f"{image_id}.png").as_posix()
        source_copy = staging / "source" / f"{image_id}.png"
        source_copy.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source_path, source_copy)

        registry = load_asset_registry(directory)
        asset = {
            "assetId": f"bg-{template_id}",
            "templateId": template_id,
            "imageId": image_id,
            "name": name.strip() or f"Custom BG {template_id}",
            "layer": layer,
            "dx": dx,
            "dy": dy,
            "sourceName": source_path.name,
            "sourcePath": (Path("assets") / "source" / f"{image_id}.png").as_posix(),
            "assetPaths": paths,
            "sizes": sizes,
            "createdAt": utc_now(),
        }
        for relative in [Path("source") / f"{image_id}.png", *(Path(f"x{zoom}") / f"{image_id}.png" for zoom in range(1, 5))]:
            source_file = staging / relative
            target = assets_dir / relative
            if target.exists():
                raise ProjectError(f"Asset target đã tồn tại: {target}")
            target.parent.mkdir(parents=True, exist_ok=True)
            os.replace(source_file, target)
            installed.append(target)
        registry["assets"].append(asset)
        registry["updatedAt"] = utc_now()
        atomic_write_json(assets_dir / "registry.json", registry)
    except Exception:
        for path in installed:
            if path.is_file():
                path.unlink()
        raise
    finally:
        shutil.rmtree(staging, ignore_errors=True)

    metadata["status"] = "draft"
    metadata["updatedAt"] = utc_now()
    metadata["assetRevision"] = int(metadata.get("assetRevision", 0)) + 1
    metadata["lastCompile"] = None
    metadata["lastReleasePlan"] = None
    atomic_write_json(directory / "project.json", metadata)
    state = get_editor_state(repo_root, project_id, root)
    state["uploadedAsset"] = asset
    return state


def save_project(
    repo_root: Path,
    project_id: str,
    plan: dict[str, Any],
    projects_root: Path | None = None,
) -> dict[str, Any]:
    root = get_projects_root(repo_root, projects_root)
    metadata, current_plan, directory = load_project(root, project_id)
    if not isinstance(plan, dict) or not isinstance(plan.get("map"), dict):
        raise ProjectError("Draft phải là JSON object và có object map")
    if plan["map"].get("mapId") != metadata["mapId"]:
        raise ProjectError("Không được đổi mapId trong một project; hãy tạo project khác")

    revision = int(metadata.get("revision", 1))
    revision_path = directory / "revisions" / f"revision-{revision:06d}.json"
    if not revision_path.exists():
        atomic_write_json(revision_path, current_plan)
    atomic_write_json(directory / "teamobi-map-plan.json", plan)
    metadata["displayName"] = str(plan["map"].get("name", metadata.get("displayName", project_id)))
    metadata["revision"] = revision + 1
    metadata["updatedAt"] = utc_now()
    metadata["status"] = "draft"
    metadata["lastReleasePlan"] = None
    atomic_write_json(directory / "project.json", metadata)
    return get_project(repo_root, project_id, root)


def restore_revision(
    repo_root: Path,
    project_id: str,
    revision: int,
    projects_root: Path | None = None,
) -> dict[str, Any]:
    root = get_projects_root(repo_root, projects_root)
    _, _, directory = load_project(root, project_id)
    revision_path = directory / "revisions" / f"revision-{revision:06d}.json"
    if not revision_path.is_file():
        raise ProjectError(f"Không tìm thấy revision {revision} của {project_id}")
    return save_project(repo_root, project_id, load_json(revision_path), root)


def compile_project(
    repo_root: Path,
    project_id: str,
    projects_root: Path | None = None,
    minimum_template_id: int | None = None,
) -> dict[str, Any]:
    root = get_projects_root(repo_root, projects_root)
    metadata, plan, directory = load_project(root, project_id)
    ensure_project_offset_registry(repo_root, root, directory, plan, minimum_template_id)
    output, _, _ = get_build_paths(repo_root, project_id)
    result = compiler.compile_plan(
        directory / "teamobi-map-plan.json",
        output,
        repo_root,
        force=output.exists(),
    )
    compiled_at = utc_now()
    metadata["status"] = "compiled"
    metadata["updatedAt"] = compiled_at
    metadata["lastCompile"] = {
        "compiledAt": compiled_at,
        "revision": metadata["revision"],
        "buildDirectory": str(output),
        "manifestSha256": compiler.sha256_file(output / "manifest.json"),
        "errors": result.report["summary"]["errors"],
        "warnings": result.report["summary"]["warnings"],
    }
    metadata["lastReleasePlan"] = None
    atomic_write_json(directory / "project.json", metadata)
    response = get_project(repo_root, project_id, root)
    response["validation"] = result.report
    response["manifest"] = result.manifest
    return response


def safe_artifact_path(output: Path, relative: str) -> Path:
    relative_path = Path(relative)
    if relative_path.is_absolute() or ".." in relative_path.parts:
        raise ProjectError(f"Manifest chứa artifact path không an toàn: {relative}")
    path = (output / relative_path).resolve()
    try:
        path.relative_to(output.resolve())
    except ValueError as error:
        raise ProjectError(f"Artifact thoát khỏi build directory: {relative}") from error
    return path


def verify_project_build(
    repo_root: Path,
    project_id: str,
    projects_root: Path | None = None,
) -> dict[str, Any]:
    root = get_projects_root(repo_root, projects_root)
    metadata, _, directory = load_project(root, project_id)
    output, _, _ = get_build_paths(repo_root, project_id)
    manifest_path = output / "manifest.json"
    issues: list[dict[str, str]] = []
    if not manifest_path.is_file():
        return {"verified": False, "issues": [{"code": "MISSING_BUILD", "message": "Project chưa được compile"}]}
    manifest = load_json(manifest_path)
    if metadata.get("status") not in ("compiled", "published") or not metadata.get("lastCompile"):
        issues.append({"code": "PROJECT_NOT_COMPILED", "message": "Project có draft/asset mới sau lần compile cuối"})
    if manifest.get("mapId") != metadata.get("mapId"):
        issues.append({"code": "MAP_ID_MISMATCH", "message": "Map ID trong manifest không khớp project"})
    if not manifest.get("publishReady") or manifest.get("validation", {}).get("errors", 1) != 0:
        issues.append({"code": "BUILD_NOT_READY", "message": "Build còn lỗi validation"})
    plan_hash = compiler.sha256_file(directory / "teamobi-map-plan.json")
    if manifest.get("input", {}).get("sha256") != plan_hash:
        issues.append({"code": "STALE_BUILD", "message": "Draft đã thay đổi sau lần compile cuối"})
    last_manifest_hash = (metadata.get("lastCompile") or {}).get("manifestSha256")
    if last_manifest_hash and compiler.sha256_file(manifest_path) != last_manifest_hash:
        issues.append({"code": "MANIFEST_CHANGED", "message": "Manifest không khớp lần compile đã ghi trong project"})

    artifacts = manifest.get("artifacts")
    if not isinstance(artifacts, dict):
        issues.append({"code": "INVALID_MANIFEST", "message": "Manifest không có object artifacts"})
        artifacts = {}
    for relative, expected in sorted(artifacts.items()):
        try:
            path = safe_artifact_path(output, relative)
        except ProjectError as error:
            issues.append({"code": "UNSAFE_ARTIFACT", "message": str(error)})
            continue
        if not path.is_file():
            issues.append({"code": "MISSING_ARTIFACT", "message": relative})
            continue
        if path.stat().st_size != expected.get("size") or compiler.sha256_file(path) != expected.get("sha256"):
            issues.append({"code": "ARTIFACT_MISMATCH", "message": relative})

    map_id = metadata["mapId"]
    required = [
        f"runtime/tile_map_data/{map_id}",
        f"runtime/item_bg_map_data/{map_id}",
        "map-template.sql",
        "bg-item-template.sql",
    ]
    for relative in required:
        if relative not in artifacts:
            issues.append({"code": "MISSING_REQUIRED_ARTIFACT", "message": relative})
    return {
        "verified": len(issues) == 0,
        "issues": issues,
        "manifestPath": str(manifest_path),
        "manifestSha256": compiler.sha256_file(manifest_path),
        "buildDirectory": str(output),
    }


def detect_server_running(repo_root: Path) -> bool:
    pid_path = repo_root / "logs" / "server.pid"
    if not pid_path.is_file():
        return False
    for line in pid_path.read_text(encoding="utf-8-sig", errors="ignore").splitlines():
        if not line.strip().isdigit():
            continue
        try:
            os.kill(int(line.strip()), 0)
            return True
        except (OSError, ProcessLookupError):
            continue
    return False


def runtime_target_state(path: Path) -> dict[str, Any]:
    return {
        "path": str(path),
        "exists": path.is_file(),
        "sha256": compiler.sha256_file(path) if path.is_file() else None,
        "size": path.stat().st_size if path.is_file() else 0,
    }


def create_release_plan(
    repo_root: Path,
    project_id: str,
    projects_root: Path | None = None,
    server_running: bool | None = None,
    persist: bool = True,
) -> dict[str, Any]:
    root = get_projects_root(repo_root, projects_root)
    metadata, _, directory = load_project(root, project_id)
    verification = verify_project_build(repo_root, project_id, root)
    if server_running is None:
        server_running = detect_server_running(repo_root)
    output, _, _ = get_build_paths(repo_root, project_id)
    map_id = metadata["mapId"]
    compiled_bg_path = output / "compiled-bg-map.json"
    compiled_bg = load_json(compiled_bg_path) if compiled_bg_path.is_file() else {"customTemplates": []}
    custom_templates = compiled_bg.get("customTemplates", [])
    custom_asset_templates = [item for item in custom_templates if item.get("assets")]
    tile_target = repo_root / RUNTIME_TILE_RELATIVE / str(map_id)
    bg_target = repo_root / RUNTIME_BG_RELATIVE / str(map_id)
    blockers = list(verification["issues"])
    if server_running:
        blockers.append({"code": "SERVER_RUNNING", "message": "Phải dừng server trước khi publish map"})
    plan = {
        "status": "ok",
        "projectId": project_id,
        "mapId": map_id,
        "generatedAt": utc_now(),
        "verified": verification["verified"],
        "serverRunning": server_running,
        "canPublish": len(blockers) == 0,
        "blockers": blockers,
        "manifestSha256": verification.get("manifestSha256"),
        "operations": [
            {
                "kind": "copy-runtime",
                "source": str(output / "runtime" / "tile_map_data" / str(map_id)),
                "target": str(tile_target),
                "before": runtime_target_state(tile_target),
            },
            {
                "kind": "copy-runtime",
                "source": str(output / "runtime" / "item_bg_map_data" / str(map_id)),
                "target": str(bg_target),
                "before": runtime_target_state(bg_target),
            },
            {
                "kind": "database-upsert",
                "source": str(output / "map-template.sql"),
                "target": f"map_template.id={map_id}",
            },
            {
                "kind": "increment-map-version",
                "target": str(repo_root / MAP_VERSION_RELATIVE),
            },
        ] + [
            {
                "kind": "copy-bg-asset",
                "source": str(output / relative),
                "target": str(repo_root / "data" / "item_bg_temp" / Path(relative).relative_to("runtime/item_bg_temp")),
            }
            for template in custom_templates
            for relative in template.get("assets", {}).values()
        ] + [
            {
                "kind": "database-upsert-bg-template",
                "source": str(output / "bg-item-template.sql"),
                "target": f"bg_item_template.id={template['templateId']}",
            }
            for template in custom_templates
        ],
        "customBgTemplates": custom_templates,
        "assetScaling": {
            "status": "ready" if custom_templates else "not-required",
            "customTemplateCount": len(custom_templates),
            "imageCount": len({int(item["imageId"]) for item in custom_asset_templates}),
            "message": (
                "Template/offset phát sinh đã sẵn sàng; PNG custom x1-x4 sẽ được snapshot/publish cùng map."
                if custom_asset_templates
                else "Fine offset được materialize thành bg_item_template; map chỉ tái sử dụng PNG runtime có sẵn."
                if custom_templates
                else "Map chỉ tham chiếu asset runtime có sẵn."
            ),
        },
        "rollback": {
            "strategy": "Snapshot file runtime, version.txt và map_template trước khi ghi",
            "availableAfterPublish": True,
        },
    }
    if persist:
        metadata["lastReleasePlan"] = {
            "generatedAt": plan["generatedAt"],
            "canPublish": plan["canPublish"],
            "manifestSha256": plan["manifestSha256"],
            "blockerCodes": [item["code"] for item in blockers],
        }
        atomic_write_json(directory / "project.json", metadata)
    return plan


def atomic_copy_file(source: Path, target: Path) -> None:
    if not source.is_file():
        raise ProjectError(f"Thiếu source publish: {source}")
    atomic_write_bytes(target, source.read_bytes())


def build_database_rollback_sql(
    map_id: int,
    database_before: dict[str, Any] | None,
    custom_templates: list[dict[str, Any]] | None = None,
) -> str:
    package = database_before if isinstance(database_before, dict) and "mapTemplate" in database_before else None
    map_before = package.get("mapTemplate") if package is not None else database_before
    sql_parts: list[str] = ["-- Auto-generated database rollback for Admin Map Editor.\n"]
    if map_before is None:
        sql_parts.append(f"DELETE FROM `map_template` WHERE `id` = {map_id};\n")
    else:
        database_before = map_before
        normalized = {
            "id": int(database_before.get("id", map_id)),
            "name": str(database_before.get("name", database_before.get("NAME", f"Map {map_id}"))),
            "zones": int(database_before.get("zones", 10)),
            "maxPlayer": int(database_before.get("maxPlayer", database_before.get("max_player", 15))),
            "data": json.loads(database_before["data"]) if isinstance(database_before.get("data"), str) else database_before.get("data", []),
            "type": int(database_before.get("type", 0)),
            "planetId": int(database_before.get("planetId", database_before.get("planet_id", 0))),
            "bgType": int(database_before.get("bgType", database_before.get("bg_type", 0))),
            "tileId": int(database_before.get("tileId", database_before.get("tile_id", 1))),
            "bgId": int(database_before.get("bgId", database_before.get("bg_id", 0))),
            "waypoints": json.loads(database_before["waypoints"]) if isinstance(database_before.get("waypoints"), str) else database_before.get("waypoints", []),
            "mobs": json.loads(database_before["mobs"]) if isinstance(database_before.get("mobs"), str) else database_before.get("mobs", []),
            "npcs": json.loads(database_before["npcs"]) if isinstance(database_before.get("npcs"), str) else database_before.get("npcs", []),
            "isMapDouble": int(database_before.get("isMapDouble", database_before.get("is_map_double", 0))),
        }
        sql_parts.append(
            compiler.build_map_sql(normalized).replace(
                "-- Staging artifact only. Review before executing.\n",
                "-- Restore previous map_template row.\n",
                1,
            )
        )

    before_bg: dict[int, dict[str, Any] | None] = {}
    if package is not None:
        for item in package.get("bgTemplates", []):
            if isinstance(item, dict) and "id" in item:
                before_bg[int(item["id"])] = item.get("row")
    for template in custom_templates or []:
        template_id = int(template["templateId"])
        previous = before_bg.get(template_id)
        if previous is None:
            sql_parts.append(f"DELETE FROM `bg_item_template` WHERE `id` = {template_id};\n")
        else:
            rollback_record = {
                "templateId": template_id,
                "imageId": int(previous.get("imageId", previous.get("image_id", 0))),
                "layer": int(previous.get("layer", 1)),
                "dx": int(previous.get("dx", 0)),
                "dy": int(previous.get("dy", 0)),
                "custom": True,
            }
            sql_parts.append(compiler.build_bg_template_sql([rollback_record]))
    return "".join(sql_parts)


def write_release_metadata(release_dir: Path, metadata: dict[str, Any]) -> None:
    atomic_write_json(release_dir / "release.json", metadata)


def load_release_record(
    repo_root: Path,
    project_id: str,
    release_id: str,
    releases_root: Path | None = None,
) -> tuple[dict[str, Any], Path]:
    validate_project_id(project_id)
    validate_release_id(release_id)
    release_root = get_releases_root(repo_root, releases_root)
    release_dir = release_root / project_id / release_id
    release_path = release_dir / "release.json"
    if not release_path.is_file():
        raise ProjectError(f"Không tìm thấy release {release_id}")
    release = load_json(release_path)
    if release.get("projectId") != project_id or release.get("releaseId") != release_id:
        raise ProjectError("Release metadata không thuộc project yêu cầu")
    return release, release_dir


def release_summary(release: dict[str, Any], release_dir: Path) -> dict[str, Any]:
    acceptance_status = "not-started"
    acceptance_path = release_dir / "acceptance.json"
    if acceptance_path.is_file():
        try:
            acceptance = load_json(acceptance_path)
            if (
                not isinstance(acceptance, dict)
                or acceptance.get("projectId") != release.get("projectId")
                or acceptance.get("releaseId") != release.get("releaseId")
                or acceptance.get("manifestSha256") != release.get("manifestSha256")
                or not isinstance(acceptance.get("checks"), dict)
            ):
                acceptance_status = "invalid"
            else:
                required = required_acceptance_checks(release)
                manual_complete = bool(str(acceptance.get("tester", "")).strip()) and all(
                    acceptance["checks"].get(key) is True for key in required
                )
                acceptance_status = "manual-complete" if manual_complete else "in-progress"
        except (OSError, json.JSONDecodeError, TypeError):
            acceptance_status = "invalid"
    return {
        "releaseId": release["releaseId"],
        "projectId": release["projectId"],
        "mapId": release["mapId"],
        "status": release.get("status", "unknown"),
        "createdAt": release.get("createdAt"),
        "publishedAt": release.get("publishedAt"),
        "runtimeRolledBackAt": release.get("runtimeRolledBackAt"),
        "manifestSha256": release.get("manifestSha256"),
        "mapVersion": release.get("mapVersion", {}).get("publishedValue"),
        "customTemplateCount": len(release.get("customTemplateIds", [])),
        "assetImageCount": len(release.get("assetImageIds", [])),
        "acceptanceStatus": acceptance_status,
        "releaseDirectory": str(release_dir),
    }


def list_project_releases(
    repo_root: Path,
    project_id: str,
    releases_root: Path | None = None,
) -> dict[str, Any]:
    validate_project_id(project_id)
    project_release_root = get_releases_root(repo_root, releases_root) / project_id
    releases: list[dict[str, Any]] = []
    if project_release_root.is_dir():
        for release_dir in sorted(project_release_root.iterdir(), key=lambda path: path.name, reverse=True):
            if not release_dir.is_dir() or not (release_dir / "release.json").is_file():
                continue
            try:
                validate_release_id(release_dir.name)
                release = load_json(release_dir / "release.json")
                if release.get("projectId") == project_id and release.get("releaseId") == release_dir.name:
                    releases.append(release_summary(release, release_dir))
            except (ProjectError, OSError, json.JSONDecodeError, KeyError, TypeError, ValueError):
                continue
    return {"status": "ok", "projectId": project_id, "releases": releases}


def get_project_release(
    repo_root: Path,
    project_id: str,
    release_id: str,
    releases_root: Path | None = None,
) -> dict[str, Any]:
    release, release_dir = load_release_record(repo_root, project_id, release_id, releases_root)
    database_before_path = release_dir / "database-before.json"
    database_before = load_json(database_before_path) if database_before_path.is_file() else None
    return {
        "status": "ok",
        "release": release,
        "summary": release_summary(release, release_dir),
        "databaseBefore": database_before,
        "paths": {
            "publishSql": str(release_dir / "publish.sql"),
            "rollbackDatabaseSql": str(release_dir / "rollback-database.sql"),
            "manifest": str(release_dir / "manifest.json"),
            "acceptance": str(release_dir / "acceptance.json"),
        },
    }


def required_acceptance_checks(release: dict[str, Any]) -> list[str]:
    required = list(ACCEPTANCE_ALWAYS_REQUIRED)
    map_template = release.get("databaseExpected", {}).get("mapTemplate", {})
    for key in ("waypoints", "npcs", "mobs"):
        if isinstance(map_template, dict) and map_template.get(key):
            required.append(key)
    return required


def normalize_acceptance_checks(value: Any) -> dict[str, bool]:
    if not isinstance(value, dict):
        raise ProjectError("Checklist nghiệm thu phải là JSON object")
    unknown = sorted(set(value) - set(ACCEPTANCE_CHECK_KEYS))
    if unknown:
        raise ProjectError(f"Checklist nghiệm thu có mục không hỗ trợ: {', '.join(unknown)}")
    normalized = {key: False for key in ACCEPTANCE_CHECK_KEYS}
    for key, checked in value.items():
        if not isinstance(checked, bool):
            raise ProjectError(f"Checklist {key} phải là boolean")
        normalized[key] = checked
    return normalized


def acceptance_result(release: dict[str, Any], release_dir: Path) -> dict[str, Any]:
    acceptance_path = release_dir / "acceptance.json"
    required = required_acceptance_checks(release)
    saved = acceptance_path.is_file()
    if saved:
        acceptance = load_json(acceptance_path)
        if not isinstance(acceptance, dict):
            raise ProjectError("File nghiệm thu không phải JSON object")
        if (
            acceptance.get("projectId") != release.get("projectId")
            or acceptance.get("releaseId") != release.get("releaseId")
            or acceptance.get("manifestSha256") != release.get("manifestSha256")
        ):
            raise ProjectError("File nghiệm thu không thuộc release hiện tại")
        checks = normalize_acceptance_checks(acceptance.get("checks"))
        tester = str(acceptance.get("tester", "")).strip()
        notes = str(acceptance.get("notes", ""))
    else:
        acceptance = {}
        checks = {key: False for key in ACCEPTANCE_CHECK_KEYS}
        tester = ""
        notes = ""
    manual_complete = bool(tester) and all(checks[key] for key in required)
    return {
        "status": "ok",
        "projectId": release["projectId"],
        "releaseId": release["releaseId"],
        "releaseStatus": release.get("status"),
        "mapId": release["mapId"],
        "saved": saved,
        "requiredChecks": required,
        "optionalChecks": [key for key in ACCEPTANCE_CHECK_KEYS if key not in required],
        "manualComplete": manual_complete,
        "acceptance": {
            "acceptanceVersion": ACCEPTANCE_VERSION,
            "tester": tester,
            "notes": notes,
            "checks": checks,
            "updatedAt": acceptance.get("updatedAt"),
            "completedAt": acceptance.get("completedAt") if manual_complete else None,
        },
        "path": str(acceptance_path),
    }


def get_release_acceptance(
    repo_root: Path,
    project_id: str,
    release_id: str,
    releases_root: Path | None = None,
) -> dict[str, Any]:
    release, release_dir = load_release_record(repo_root, project_id, release_id, releases_root)
    return acceptance_result(release, release_dir)


def save_release_acceptance(
    repo_root: Path,
    project_id: str,
    release_id: str,
    payload: Any,
    releases_root: Path | None = None,
) -> dict[str, Any]:
    release, release_dir = load_release_record(repo_root, project_id, release_id, releases_root)
    if release.get("status") != "published":
        raise ProjectError("Chỉ được cập nhật nghiệm thu cho release đang published")
    if not isinstance(payload, dict):
        raise ProjectError("Dữ liệu nghiệm thu phải là JSON object")
    tester = str(payload.get("tester", "")).strip()
    notes = str(payload.get("notes", "")).strip()
    if len(tester) > 80:
        raise ProjectError("Tên người kiểm tra tối đa 80 ký tự")
    if len(notes) > 4000:
        raise ProjectError("Ghi chú nghiệm thu tối đa 4000 ký tự")
    checks = normalize_acceptance_checks(payload.get("checks"))
    required = required_acceptance_checks(release)
    manual_complete = bool(tester) and all(checks[key] for key in required)
    acceptance_path = release_dir / "acceptance.json"
    previous_completed_at = None
    if acceptance_path.is_file():
        previous = load_json(acceptance_path)
        if isinstance(previous, dict):
            previous_completed_at = previous.get("completedAt")
    now = utc_now()
    acceptance = {
        "acceptanceVersion": ACCEPTANCE_VERSION,
        "projectId": project_id,
        "releaseId": release_id,
        "mapId": release["mapId"],
        "manifestSha256": release["manifestSha256"],
        "tester": tester,
        "notes": notes,
        "checks": checks,
        "manualComplete": manual_complete,
        "updatedAt": now,
        "completedAt": (previous_completed_at or now) if manual_complete else None,
    }
    atomic_write_json(acceptance_path, acceptance)
    return acceptance_result(release, release_dir)


def expected_release_targets(repo_root: Path, release: dict[str, Any]) -> dict[str, Path]:
    map_id = int(release.get("mapId", -1))
    if map_id < 0 or map_id > 254:
        raise ProjectError("Release có Map ID không an toàn")
    targets = {
        "tile": (repo_root / RUNTIME_TILE_RELATIVE / str(map_id)).resolve(),
        "background": (repo_root / RUNTIME_BG_RELATIVE / str(map_id)).resolve(),
    }
    for image_id_value in release.get("assetImageIds", []):
        image_id = int(image_id_value)
        if image_id < 0 or image_id > 32767:
            raise ProjectError("Release có asset image ID không an toàn")
        for zoom in range(1, 5):
            targets[f"asset-x{zoom}-{image_id}"] = (
                repo_root / "data" / "item_bg_temp" / f"x{zoom}" / f"{image_id}.png"
            ).resolve()
    return targets


def verify_release_runtime(
    repo_root: Path,
    project_id: str,
    release_id: str,
    releases_root: Path | None = None,
) -> dict[str, Any]:
    release, release_dir = load_release_record(repo_root, project_id, release_id, releases_root)
    expected_targets = expected_release_targets(repo_root, release)
    snapshots = release.get("runtimeSnapshots", [])
    issues: list[dict[str, Any]] = []
    files: list[dict[str, Any]] = []
    rolled_back = release.get("status") == "runtime-rolled-back"
    snapshot_labels = [str(item.get("label", "")) for item in snapshots] if isinstance(snapshots, list) else []
    if (
        not isinstance(snapshots, list)
        or len(snapshots) != len(expected_targets)
        or len(set(snapshot_labels)) != len(snapshot_labels)
        or set(snapshot_labels) != set(expected_targets)
    ):
        issues.append({"code": "INVALID_RELEASE_SNAPSHOT", "message": "Release không có đủ runtime snapshot"})
        snapshots = []

    release_file_contract = {
        "manifest": release_dir / "manifest.json",
        "publishSql": release_dir / "publish.sql",
        "rollbackDatabaseSql": release_dir / "rollback-database.sql",
        "databaseBefore": release_dir / "database-before.json",
    }
    release_files: list[dict[str, Any]] = []
    recorded_release_files = release.get("releaseFiles")
    if not isinstance(recorded_release_files, dict):
        issues.append({"code": "LEGACY_RELEASE_NO_PACKAGE_HASH", "message": "Release cũ thiếu checksum gói publish/rollback"})
    else:
        for label, expected_path in release_file_contract.items():
            record = recorded_release_files.get(label)
            recorded_path = Path(str(record.get("path", ""))).resolve() if isinstance(record, dict) else None
            expected_sha = record.get("sha256") if isinstance(record, dict) else None
            current_exists = expected_path.is_file()
            current_sha = compiler.sha256_file(expected_path) if current_exists else None
            matched = recorded_path == expected_path.resolve() and current_exists and bool(expected_sha) and current_sha == expected_sha
            release_files.append(
                {
                    "label": label,
                    "path": str(expected_path),
                    "expectedSha256": expected_sha,
                    "currentSha256": current_sha,
                    "matched": matched,
                }
            )
            if not matched:
                issues.append({"code": "RELEASE_PACKAGE_DRIFT", "label": label, "message": f"File release {label} bị thiếu hoặc sai checksum"})
    for snapshot in snapshots:
        label = str(snapshot.get("label", ""))
        target = Path(str(snapshot.get("target", ""))).resolve()
        expected_target = expected_targets.get(label)
        if expected_target is None or target != expected_target:
            issues.append({"code": "UNSAFE_RUNTIME_TARGET", "label": label, "message": "Target runtime không khớp contract"})
            continue
        if rolled_back:
            expected_exists = bool(snapshot.get("existedBefore"))
            expected_sha = snapshot.get("sha256Before") if expected_exists else None
        else:
            expected_exists = True
            expected_sha = snapshot.get("sha256Published")
            deployed = Path(str(snapshot.get("deployed", ""))).resolve()
            safe_deployed = (release_dir / "deployed" / label).resolve()
            if deployed != safe_deployed or not deployed.is_file() or compiler.sha256_file(deployed) != expected_sha:
                issues.append({"code": "RELEASE_PAYLOAD_CORRUPT", "label": label, "message": "Payload đã deploy trong release bị thiếu hoặc sai checksum"})
        current_exists = target.is_file()
        current_sha = compiler.sha256_file(target) if current_exists else None
        matched = current_exists == expected_exists and (not expected_exists or current_sha == expected_sha)
        files.append(
            {
                "label": label,
                "target": str(target),
                "expectedExists": expected_exists,
                "currentExists": current_exists,
                "expectedSha256": expected_sha,
                "currentSha256": current_sha,
                "matched": matched,
            }
        )
        if expected_exists and not expected_sha:
            issues.append({"code": "LEGACY_RELEASE_NO_DEPLOYED_HASH", "label": label, "message": "Release cũ thiếu checksum payload đã publish"})
        elif not matched:
            issues.append({"code": "RUNTIME_FILE_DRIFT", "label": label, "message": f"Runtime {label} khác release"})

    version = release.get("mapVersion", {})
    version_path = Path(str(version.get("path", ""))).resolve()
    safe_version_path = (repo_root / MAP_VERSION_RELATIVE).resolve()
    version_expected_exists = bool(version.get("existedBefore")) if rolled_back else True
    version_expected_value = version.get("previousValue") if rolled_back else version.get("publishedValue")
    version_current_exists = version_path.is_file() if version_path == safe_version_path else False
    version_current_value: int | None = None
    if version_current_exists:
        try:
            version_current_value = int(version_path.read_text(encoding="utf-8-sig").strip())
        except ValueError:
            issues.append({"code": "INVALID_MAP_VERSION", "message": "data/map/version.txt không chứa số nguyên"})
    if not version_expected_exists:
        version_value_matched = True
    elif rolled_back:
        version_value_matched = version_current_value == version_expected_value
    else:
        # map/version.txt is a repository-wide monotonic counter. Publishing a
        # later map legitimately raises it, so an older published release only
        # requires that the current value has not moved backwards.
        version_value_matched = (
            isinstance(version_current_value, int)
            and isinstance(version_expected_value, int)
            and version_current_value >= version_expected_value
        )
    version_matched = (
        version_path == safe_version_path
        and version_current_exists == version_expected_exists
        and version_value_matched
    )
    if not version_matched:
        issues.append({"code": "MAP_VERSION_DRIFT", "message": "Map version hiện tại khác trạng thái release mong đợi"})
    return {
        "status": "ok",
        "projectId": project_id,
        "releaseId": release_id,
        "releaseStatus": release.get("status"),
        "mode": "rolled-back" if rolled_back else "published",
        "verified": len(issues) == 0,
        "files": files,
        "releaseFiles": release_files,
        "mapVersion": {
            "path": str(safe_version_path),
            "expectedExists": version_expected_exists,
            "currentExists": version_current_exists,
            "expectedValue": version_expected_value,
            "currentValue": version_current_value,
            "matched": version_matched,
        },
        "issues": issues,
    }


def publish_runtime(
    repo_root: Path,
    project_id: str,
    database_before: dict[str, Any] | None,
    projects_root: Path | None = None,
    releases_root: Path | None = None,
    server_running: bool | None = None,
) -> dict[str, Any]:
    root = get_projects_root(repo_root, projects_root)
    metadata, _, project_dir = load_project(root, project_id)
    release_root = get_releases_root(repo_root, releases_root)
    release_root.mkdir(parents=True, exist_ok=True)
    lock_path = release_root / ".publish.lock"
    try:
        lock_fd = os.open(lock_path, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
    except FileExistsError as error:
        raise ProjectError("Một tác vụ publish map khác đang chạy") from error

    release_dir: Path | None = None
    keep_lock = False
    try:
        os.write(lock_fd, f"pid={os.getpid()}\nproject={project_id}\n".encode("utf-8"))
        os.close(lock_fd)
        effective_running = detect_server_running(repo_root) if server_running is None else server_running
        release_plan = create_release_plan(repo_root, project_id, root, effective_running, persist=False)
        if not release_plan["canPublish"]:
            codes = ", ".join(item["code"] for item in release_plan["blockers"])
            raise ProjectError(f"Không thể publish: {codes}")

        output, _, _ = get_build_paths(repo_root, project_id)
        map_id = metadata["mapId"]
        compiled_bg = load_json(output / "compiled-bg-map.json")
        custom_templates = compiled_bg.get("customTemplates", [])
        release_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + release_plan["manifestSha256"][:12]
        release_dir = release_root / validate_project_id(project_id) / release_id
        release_dir.mkdir(parents=True, exist_ok=False)
        atomic_write_bytes(
            lock_path,
            f"pid={os.getpid()}\nproject={project_id}\nrelease={release_id}\n".encode("utf-8"),
        )
        targets: list[tuple[str, Path, Path]] = [
            ("tile", repo_root / RUNTIME_TILE_RELATIVE / str(map_id), output / "runtime" / "tile_map_data" / str(map_id)),
            ("background", repo_root / RUNTIME_BG_RELATIVE / str(map_id), output / "runtime" / "item_bg_map_data" / str(map_id)),
        ]
        for template in custom_templates:
            image_id = int(template["imageId"])
            for zoom, relative_text in sorted(template.get("assets", {}).items()):
                if not re.fullmatch(r"x[1-4]", str(zoom)):
                    raise ProjectError(f"Bundle có zoom asset không hợp lệ: {zoom}")
                label = f"asset-{zoom}-{image_id}"
                relative = Path(relative_text)
                source = output / relative
                target = repo_root / "data" / "item_bg_temp" / str(zoom) / f"{image_id}.png"
                targets.append((label, target, source))
        version_path = repo_root / MAP_VERSION_RELATIVE
        version_existed = version_path.is_file()
        version_before = version_path.read_bytes() if version_existed else b""
        current_version = 0
        if version_existed:
            try:
                current_version = int(version_before.decode("utf-8-sig").strip())
            except ValueError as error:
                raise ProjectError("data/map/version.txt không chứa số nguyên hợp lệ") from error
        snapshots = []
        for label, target, source in targets:
            existed = target.is_file()
            backup = release_dir / "backup" / label
            deployed = release_dir / "deployed" / label
            if existed:
                atomic_copy_file(target, backup)
            atomic_copy_file(source, deployed)
            snapshots.append(
                {
                    "label": label,
                    "target": str(target),
                    "existedBefore": existed,
                    "backup": str(backup) if existed else "",
                    "sha256Before": compiler.sha256_file(target) if existed else None,
                    "deployed": str(deployed),
                    "sha256Published": compiler.sha256_file(deployed),
                }
            )
        if version_existed:
            atomic_write_bytes(release_dir / "backup" / "version.txt", version_before)

        atomic_copy_file(output / "manifest.json", release_dir / "manifest.json")
        publish_sql = (
            (output / "bg-item-template.sql").read_text(encoding="utf-8-sig")
            + "\n"
            + (output / "map-template.sql").read_text(encoding="utf-8-sig")
        )
        atomic_write_bytes(release_dir / "publish.sql", publish_sql.encode("utf-8"))
        atomic_write_json(release_dir / "database-before.json", database_before)
        atomic_write_bytes(
            release_dir / "rollback-database.sql",
            build_database_rollback_sql(map_id, database_before, custom_templates).encode("utf-8"),
        )
        release_file_contract = {
            "manifest": release_dir / "manifest.json",
            "publishSql": release_dir / "publish.sql",
            "rollbackDatabaseSql": release_dir / "rollback-database.sql",
            "databaseBefore": release_dir / "database-before.json",
        }
        compiled_template = load_json(output / "compiled-map-template.json")
        database_expected = {
            "mapTemplate": compiled_template,
            "bgTemplates": [
                {
                    "id": int(item["templateId"]),
                    "imageId": int(item["imageId"]),
                    "layer": int(item["layer"]),
                    "dx": int(item["dx"]),
                    "dy": int(item["dy"]),
                }
                for item in custom_templates
            ],
        }
        release_metadata = {
            "releaseVersion": 2,
            "releaseId": release_id,
            "projectId": project_id,
            "mapId": map_id,
            "status": "prepared",
            "createdAt": utc_now(),
            "manifestSha256": release_plan["manifestSha256"],
            "customTemplateIds": [int(item["templateId"]) for item in custom_templates],
            "assetImageIds": sorted({int(item["imageId"]) for item in custom_templates if item.get("assets")}),
            "runtimeSnapshots": snapshots,
            "releaseFiles": {
                label: {"path": str(path), "sha256": compiler.sha256_file(path)}
                for label, path in release_file_contract.items()
            },
            "databaseExpected": database_expected,
            "mapVersion": {
                "path": str(version_path),
                "existedBefore": version_existed,
                "backup": str(release_dir / "backup" / "version.txt") if version_existed else "",
                "previousValue": current_version if version_existed else None,
                "sha256Before": compiler.sha256_bytes(version_before) if version_existed else None,
            },
        }
        write_release_metadata(release_dir, release_metadata)

        for _, target, source in targets:
            atomic_copy_file(source, target)
        atomic_write_bytes(version_path, f"{current_version + 1}\n".encode("utf-8"))
        release_metadata["status"] = "runtime-installed"
        release_metadata["runtimeInstalledAt"] = utc_now()
        release_metadata["mapVersion"]["publishedValue"] = current_version + 1
        write_release_metadata(release_dir, release_metadata)
        keep_lock = True
        return {
            "status": "ok",
            "releaseId": release_id,
            "releaseDirectory": str(release_dir),
            "databaseSqlPath": str(release_dir / "publish.sql"),
            "databaseRollbackSqlPath": str(release_dir / "rollback-database.sql"),
            "mapVersion": current_version + 1,
        }
    except Exception:
        if release_dir is not None and (release_dir / "release.json").is_file():
            release = load_json(release_dir / "release.json")
            if release.get("status") in ("prepared", "runtime-installed"):
                rollback_runtime(repo_root, project_id, release["releaseId"], root, release_root, False)
        raise
    finally:
        try:
            os.close(lock_fd)
        except OSError:
            pass
        if not keep_lock and lock_path.exists():
            lock_path.unlink()


def rollback_runtime(
    repo_root: Path,
    project_id: str,
    release_id: str,
    projects_root: Path | None = None,
    releases_root: Path | None = None,
    server_running: bool | None = None,
) -> dict[str, Any]:
    if server_running is None:
        server_running = detect_server_running(repo_root)
    if server_running:
        raise ProjectError("Phải dừng server trước khi rollback runtime map")
    validate_project_id(project_id)
    validate_release_id(release_id)
    release_root = get_releases_root(repo_root, releases_root)
    release_dir = release_root / project_id / release_id
    release_path = release_dir / "release.json"
    if not release_path.is_file():
        raise ProjectError(f"Không tìm thấy release {release_id}")
    release = load_json(release_path)
    if release.get("projectId") != project_id:
        raise ProjectError("Release không thuộc project yêu cầu")
    if release.get("status") == "runtime-rolled-back":
        return {"status": "ok", "releaseId": release_id, "alreadyRolledBack": True}
    previous_status = release.get("status")
    if previous_status not in ("prepared", "runtime-installed", "published"):
        raise ProjectError(f"Release không thể rollback từ trạng thái {previous_status}")
    published_project_context: tuple[dict[str, Any], Path] | None = None
    if previous_status == "published":
        project_root = get_projects_root(repo_root, projects_root)
        project_metadata, _, project_dir = load_project(project_root, project_id)
        last_release = project_metadata.get("lastRelease")
        if not isinstance(last_release, dict) or last_release.get("releaseId") != release_id:
            raise ProjectError("Chỉ được rollback release published mới nhất của project")
        published_project_context = project_metadata, project_dir
    if previous_status in ("runtime-installed", "published"):
        verification = verify_release_runtime(repo_root, project_id, release_id, release_root)
        if not verification["verified"]:
            codes = ", ".join(issue["code"] for issue in verification["issues"])
            raise ProjectError(f"Runtime đã drift; từ chối rollback để tránh ghi đè dữ liệu khác: {codes}")
    expected_targets = expected_release_targets(repo_root, release)
    snapshots = release.get("runtimeSnapshots", [])
    snapshot_labels = [str(item.get("label", "")) for item in snapshots] if isinstance(snapshots, list) else []
    if (
        not isinstance(snapshots, list)
        or len(snapshots) != len(expected_targets)
        or len(set(snapshot_labels)) != len(snapshot_labels)
        or set(snapshot_labels) != set(expected_targets)
    ):
        raise ProjectError("Release không có đủ runtime/asset snapshot")
    restore_actions: list[tuple[Path, Path | None]] = []
    for snapshot in snapshots:
        label = snapshot.get("label")
        target = Path(snapshot["target"]).resolve()
        expected_target = expected_targets.get(label)
        if expected_target is None or target != expected_target:
            raise ProjectError(f"Runtime snapshot {label} có target không an toàn")
        if snapshot.get("existedBefore"):
            backup = Path(snapshot["backup"]).resolve()
            if backup != (release_dir / "backup" / str(label)).resolve():
                raise ProjectError(f"Runtime snapshot {label} có backup path không an toàn")
            if not backup.is_file() or compiler.sha256_file(backup) != snapshot.get("sha256Before"):
                raise ProjectError(f"Backup runtime {label} bị thiếu hoặc sai checksum")
            restore_actions.append((target, backup))
        else:
            restore_actions.append((target, None))
    version = release["mapVersion"]
    version_path = Path(version["path"]).resolve()
    if version_path != (repo_root / MAP_VERSION_RELATIVE).resolve():
        raise ProjectError("Release có map version target không an toàn")
    if version.get("existedBefore"):
        version_backup = Path(version["backup"]).resolve()
        if version_backup != (release_dir / "backup" / "version.txt").resolve():
            raise ProjectError("Release có map version backup không an toàn")
        if not version_backup.is_file() or compiler.sha256_file(version_backup) != version.get("sha256Before"):
            raise ProjectError("Backup map version bị thiếu hoặc sai checksum")
    else:
        version_backup = None
    for target, backup in restore_actions:
        if backup is not None:
            atomic_copy_file(backup, target)
        elif target.is_file():
            target.unlink()
    if version_backup is not None:
        atomic_copy_file(version_backup, version_path)
    elif version_path.is_file():
        version_path.unlink()
    release["rolledBackFromStatus"] = previous_status
    release["status"] = "runtime-rolled-back"
    release["runtimeRolledBackAt"] = utc_now()
    write_release_metadata(release_dir, release)
    if previous_status == "published":
        assert published_project_context is not None
        project_metadata, project_dir = published_project_context
        last_release = project_metadata.get("lastRelease")
        project_metadata["status"] = "draft"
        project_metadata["updatedAt"] = release["runtimeRolledBackAt"]
        last_release["status"] = "rolled-back"
        last_release["rolledBackAt"] = release["runtimeRolledBackAt"]
        atomic_write_json(project_dir / "project.json", project_metadata)
    lock_path = release_root / ".publish.lock"
    if lock_path.is_file():
        lock_text = lock_path.read_text(encoding="utf-8-sig", errors="ignore")
        if f"project={project_id}\n" in lock_text and f"release={release_id}\n" in lock_text:
            lock_path.unlink()
    return {"status": "ok", "releaseId": release_id, "alreadyRolledBack": False, "previousStatus": previous_status}


def finalize_release(
    repo_root: Path,
    project_id: str,
    release_id: str,
    projects_root: Path | None = None,
    releases_root: Path | None = None,
) -> dict[str, Any]:
    root = get_projects_root(repo_root, projects_root)
    validate_project_id(project_id)
    validate_release_id(release_id)
    metadata, _, project_dir = load_project(root, project_id)
    release_root = get_releases_root(repo_root, releases_root)
    release_path = release_root / project_id / release_id / "release.json"
    if not release_path.is_file():
        raise ProjectError(f"Không tìm thấy release {release_id}")
    release = load_json(release_path)
    if release.get("status") != "runtime-installed":
        raise ProjectError(f"Release không ở trạng thái runtime-installed: {release.get('status')}")
    published_at = utc_now()
    release["status"] = "published"
    release["publishedAt"] = published_at
    write_release_metadata(release_path.parent, release)
    metadata["status"] = "published"
    metadata["updatedAt"] = published_at
    metadata["lastRelease"] = {
        "releaseId": release_id,
        "publishedAt": published_at,
        "manifestSha256": release["manifestSha256"],
        "releaseDirectory": str(release_path.parent),
        "databaseRollbackSqlPath": str(release_path.parent / "rollback-database.sql"),
    }
    atomic_write_json(project_dir / "project.json", metadata)
    lock_path = release_root / ".publish.lock"
    if lock_path.is_file():
        lock_text = lock_path.read_text(encoding="utf-8-sig", errors="ignore")
        if f"project={project_id}\n" in lock_text and f"release={release_id}\n" in lock_text:
            lock_path.unlink()
    return {"status": "ok", "release": metadata["lastRelease"]}


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Manage Teamobi map editor projects")
    parser.add_argument("--repo-root", type=Path, help="Repository root (auto-detected by default)")
    parser.add_argument("--projects-root", type=Path, help="Override project storage (tests/tools)")
    parser.add_argument("--releases-root", type=Path, help="Override release storage (tests/tools)")
    subparsers = parser.add_subparsers(dest="command", required=True)

    subparsers.add_parser("list")
    get_parser = subparsers.add_parser("get")
    get_parser.add_argument("--project-id", required=True)

    editor_parser = subparsers.add_parser("editor-state")
    editor_parser.add_argument("--project-id", required=True)

    resize_parser = subparsers.add_parser("resize-plan")
    resize_parser.add_argument("--plan-json", type=Path, required=True)
    resize_parser.add_argument("--width", type=int, required=True)
    resize_parser.add_argument("--height", type=int, required=True)
    resize_parser.add_argument("--anchor", choices=tuple(RESIZE_ANCHORS), required=True)

    create_parser = subparsers.add_parser("create")
    create_parser.add_argument("--map-id", type=int, required=True)
    create_parser.add_argument("--name", required=True)
    create_parser.add_argument("--width", type=int, required=True)
    create_parser.add_argument("--height", type=int, required=True)
    create_parser.add_argument("--tile-set-id", type=int, required=True)
    create_parser.add_argument("--project-id")

    import_parser = subparsers.add_parser("import-runtime")
    import_parser.add_argument("--map-id", type=int, required=True)
    import_parser.add_argument("--template-json", type=Path, required=True)
    import_parser.add_argument("--project-id")

    save_parser = subparsers.add_parser("save")
    save_parser.add_argument("--project-id", required=True)
    save_parser.add_argument("--plan-json", type=Path, required=True)

    upload_parser = subparsers.add_parser("upload-asset")
    upload_parser.add_argument("--project-id", required=True)
    upload_parser.add_argument("--source", type=Path, required=True)
    upload_parser.add_argument("--name", default="")
    upload_parser.add_argument("--layer", type=int, required=True)
    upload_parser.add_argument("--dx", type=int, default=0)
    upload_parser.add_argument("--dy", type=int, default=0)
    upload_parser.add_argument("--template-id", type=int)
    upload_parser.add_argument("--image-id", type=int)
    upload_parser.add_argument("--width", type=int, default=0)
    upload_parser.add_argument("--height", type=int, default=0)

    restore_parser = subparsers.add_parser("restore-revision")
    restore_parser.add_argument("--project-id", required=True)
    restore_parser.add_argument("--revision", type=int, required=True)

    compile_parser = subparsers.add_parser("compile")
    compile_parser.add_argument("--project-id", required=True)
    compile_parser.add_argument("--minimum-template-id", type=int)

    verify_parser = subparsers.add_parser("verify")
    verify_parser.add_argument("--project-id", required=True)

    list_releases_parser = subparsers.add_parser("list-releases")
    list_releases_parser.add_argument("--project-id", required=True)

    get_release_parser = subparsers.add_parser("get-release")
    get_release_parser.add_argument("--project-id", required=True)
    get_release_parser.add_argument("--release-id", required=True)

    verify_release_parser = subparsers.add_parser("verify-release")
    verify_release_parser.add_argument("--project-id", required=True)
    verify_release_parser.add_argument("--release-id", required=True)

    get_acceptance_parser = subparsers.add_parser("get-acceptance")
    get_acceptance_parser.add_argument("--project-id", required=True)
    get_acceptance_parser.add_argument("--release-id", required=True)

    save_acceptance_parser = subparsers.add_parser("save-acceptance")
    save_acceptance_parser.add_argument("--project-id", required=True)
    save_acceptance_parser.add_argument("--release-id", required=True)
    save_acceptance_parser.add_argument("--acceptance-json", type=Path, required=True)

    release_plan_parser = subparsers.add_parser("release-plan")
    release_plan_parser.add_argument("--project-id", required=True)
    release_plan_parser.add_argument("--server-running", choices=("0", "1"))

    publish_parser = subparsers.add_parser("publish-runtime")
    publish_parser.add_argument("--project-id", required=True)
    publish_parser.add_argument("--database-before-json", type=Path, required=True)
    publish_parser.add_argument("--server-running", choices=("0", "1"))

    rollback_parser = subparsers.add_parser("rollback-runtime")
    rollback_parser.add_argument("--project-id", required=True)
    rollback_parser.add_argument("--release-id", required=True)
    rollback_parser.add_argument("--server-running", choices=("0", "1"))

    finalize_parser = subparsers.add_parser("finalize-release")
    finalize_parser.add_argument("--project-id", required=True)
    finalize_parser.add_argument("--release-id", required=True)
    return parser


def parse_server_running(value: str | None) -> bool | None:
    return None if value is None else value == "1"


def main(argv: list[str] | None = None) -> int:
    configure_cli_utf8()
    args = build_parser().parse_args(argv)
    repo_root = (args.repo_root or compiler.find_repo_root(Path.cwd())).resolve()
    projects_root = args.projects_root.resolve() if args.projects_root else None
    releases_root = args.releases_root.resolve() if args.releases_root else None
    try:
        if args.command == "list":
            result = list_projects(repo_root, projects_root)
        elif args.command == "get":
            result = get_project(repo_root, args.project_id, projects_root)
        elif args.command == "editor-state":
            result = get_editor_state(repo_root, args.project_id, projects_root)
        elif args.command == "resize-plan":
            result = resize_plan_for_editor(repo_root, load_json(args.plan_json), args.width, args.height, args.anchor)
        elif args.command == "create":
            result = create_project(repo_root, args.map_id, args.name, args.width, args.height, args.tile_set_id, projects_root, args.project_id)
        elif args.command == "import-runtime":
            result = import_runtime_project(repo_root, args.map_id, load_json(args.template_json), projects_root, args.project_id)
        elif args.command == "save":
            result = save_project(repo_root, args.project_id, load_json(args.plan_json), projects_root)
        elif args.command == "upload-asset":
            result = upload_project_asset(
                repo_root,
                args.project_id,
                args.source,
                args.name,
                args.layer,
                args.dx,
                args.dy,
                projects_root,
                args.template_id,
                args.image_id,
                args.width,
                args.height,
            )
        elif args.command == "restore-revision":
            result = restore_revision(repo_root, args.project_id, args.revision, projects_root)
        elif args.command == "compile":
            result = compile_project(
                repo_root,
                args.project_id,
                projects_root,
                args.minimum_template_id,
            )
        elif args.command == "verify":
            result = {"status": "ok", **verify_project_build(repo_root, args.project_id, projects_root)}
        elif args.command == "list-releases":
            result = list_project_releases(repo_root, args.project_id, releases_root)
        elif args.command == "get-release":
            result = get_project_release(repo_root, args.project_id, args.release_id, releases_root)
        elif args.command == "verify-release":
            result = verify_release_runtime(repo_root, args.project_id, args.release_id, releases_root)
        elif args.command == "get-acceptance":
            result = get_release_acceptance(repo_root, args.project_id, args.release_id, releases_root)
        elif args.command == "save-acceptance":
            result = save_release_acceptance(
                repo_root,
                args.project_id,
                args.release_id,
                load_json(args.acceptance_json),
                releases_root,
            )
        elif args.command == "release-plan":
            result = create_release_plan(repo_root, args.project_id, projects_root, parse_server_running(args.server_running))
        elif args.command == "publish-runtime":
            result = publish_runtime(
                repo_root,
                args.project_id,
                load_json(args.database_before_json),
                projects_root,
                releases_root,
                parse_server_running(args.server_running),
            )
        elif args.command == "rollback-runtime":
            result = rollback_runtime(
                repo_root,
                args.project_id,
                args.release_id,
                projects_root,
                releases_root,
                parse_server_running(args.server_running),
            )
        elif args.command == "finalize-release":
            result = finalize_release(repo_root, args.project_id, args.release_id, projects_root, releases_root)
        else:
            raise ProjectError(f"Lệnh không hợp lệ: {args.command}")
    except compiler.CompilationFailed as error:
        print(json.dumps({"status": "error", "error": "validation", "report": error.report}, ensure_ascii=False, indent=2), file=sys.stderr)
        return 2
    except (ProjectError, FileNotFoundError, FileExistsError, ValueError, json.JSONDecodeError) as error:
        print(json.dumps({"status": "error", "message": str(error)}, ensure_ascii=False, indent=2), file=sys.stderr)
        return 3
    print(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
