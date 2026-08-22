#!/usr/bin/env python3
"""Build and verify normal Teamobi NRO command-11 mob payloads."""

from __future__ import annotations

import argparse
import io
import json
import struct
import sys
from dataclasses import dataclass
from pathlib import Path

try:
    from PIL import Image, ImageDraw
except ImportError as exc:  # pragma: no cover - environment diagnostic
    raise SystemExit("Pillow is required: install/import PIL before building mob assets") from exc


POSE_NAMES = ("stand", "walk", "attack", "hurt", "die")
FRAME_TO_POSE = (0, 1, 0, 1, 0, 2, 2, 0, 2, 2, 3, 4)
COLS = 3
ROWS = 2
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


@dataclass(frozen=True)
class MobConfig:
    mob_id: int
    cell_width: int
    cell_height: int
    anchor_dx: int
    anchor_dy: int
    sources: tuple[Path, ...]
    source_crops: tuple[tuple[int, int, int, int] | None, ...]
    asset_dir: Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build or verify normal NRO mob payloads (readType=0/typeData=0)."
    )
    subparsers = parser.add_subparsers(dest="command", required=True)
    for command in ("build", "verify"):
        child = subparsers.add_parser(command)
        child.add_argument("--manifest", type=Path, required=True)
        child.add_argument("--project-root", type=Path, required=True)
    return parser.parse_args()


def require_int(value: object, label: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int):
        raise ValueError(f"{label} must be an integer")
    return value


def load_config(manifest_path: Path) -> MobConfig:
    manifest_path = manifest_path.resolve()
    if not manifest_path.is_file():
        raise FileNotFoundError(f"Manifest not found: {manifest_path}")
    try:
        raw = json.loads(manifest_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise ValueError(f"Invalid JSON manifest: {exc}") from exc
    if not isinstance(raw, dict):
        raise ValueError("Manifest root must be a JSON object")

    mob_id = require_int(raw.get("mobId"), "mobId")
    if not 73 <= mob_id <= 126 or mob_id == 76:
        raise ValueError(
            "Normal new mobId must be 73..126 and must not use special ID 76; "
            "ID 127 would make a contiguous 0-based table reach the unsafe 128-template boundary"
        )

    cell = raw.get("cell", {})
    if not isinstance(cell, dict):
        raise ValueError("cell must be an object")
    cell_width = require_int(cell.get("width", 56), "cell.width")
    cell_height = require_int(cell.get("height", 56), "cell.height")
    if not 16 <= cell_width <= 127 or not 16 <= cell_height <= 127:
        raise ValueError("cell width/height must be in 16..127 for byte-based image metadata")

    anchor = raw.get("anchor", {})
    if not isinstance(anchor, dict):
        raise ValueError("anchor must be an object")
    anchor_dx = require_int(anchor.get("dx", -(cell_width // 2)), "anchor.dx")
    anchor_dy = require_int(anchor.get("dy", -(cell_height - 2)), "anchor.dy")
    if not -32768 <= anchor_dx <= 32767 or not -32768 <= anchor_dy <= 32767:
        raise ValueError("anchor dx/dy must fit signed int16")

    poses = raw.get("poses")
    if not isinstance(poses, dict):
        raise ValueError("poses must be an object containing stand/walk/attack/hurt/die")
    asset_dir = manifest_path.parent
    sources: list[Path] = []
    source_crops: list[tuple[int, int, int, int] | None] = []
    for pose_name in POSE_NAMES:
        value = poses.get(pose_name)
        crop: tuple[int, int, int, int] | None = None
        if isinstance(value, str):
            source_value = value
        elif isinstance(value, dict):
            source_value = value.get("source")
            crop_value = value.get("crop")
            if crop_value is not None:
                if not isinstance(crop_value, dict):
                    raise ValueError(f"poses.{pose_name}.crop must be an object")
                x = require_int(crop_value.get("x"), f"poses.{pose_name}.crop.x")
                y = require_int(crop_value.get("y"), f"poses.{pose_name}.crop.y")
                width = require_int(crop_value.get("width"), f"poses.{pose_name}.crop.width")
                height = require_int(crop_value.get("height"), f"poses.{pose_name}.crop.height")
                if x < 0 or y < 0 or width <= 0 or height <= 0:
                    raise ValueError(
                        f"poses.{pose_name}.crop must have non-negative x/y and positive width/height"
                    )
                crop = (x, y, x + width, y + height)
        else:
            source_value = None
        if not isinstance(source_value, str) or not source_value.strip():
            raise ValueError(f"Missing poses.{pose_name}")
        source = (asset_dir / source_value).resolve()
        if not source.is_file():
            raise FileNotFoundError(f"Missing {pose_name} source: {source}")
        sources.append(source)
        source_crops.append(crop)

    return MobConfig(
        mob_id=mob_id,
        cell_width=cell_width,
        cell_height=cell_height,
        anchor_dx=anchor_dx,
        anchor_dy=anchor_dy,
        sources=tuple(sources),
        source_crops=tuple(source_crops),
        asset_dir=asset_dir,
    )


def trim_alpha(
    source: Path, crop: tuple[int, int, int, int] | None = None
) -> Image.Image:
    with Image.open(source) as opened:
        rgba = opened.convert("RGBA")
    if crop is not None:
        left, top, right, bottom = crop
        if right > rgba.width or bottom > rgba.height:
            raise ValueError(
                f"Crop {crop} exceeds source bounds {rgba.size}: {source}"
            )
        rgba = rgba.crop(crop)
    alpha = rgba.getchannel("A")
    minimum, maximum = alpha.getextrema()
    if maximum == 0:
        raise ValueError(f"Source has no visible pixels: {source}")
    if minimum == 255:
        raise ValueError(f"Source has no transparent background/alpha: {source}")
    bbox = alpha.getbbox()
    if bbox is None:
        raise ValueError(f"Source has no visible alpha bounds: {source}")
    return rgba.crop(bbox)


def fit_pose(
    source: Path,
    crop: tuple[int, int, int, int] | None,
    config: MobConfig,
    zoom: int,
) -> Image.Image:
    image = trim_alpha(source, crop)
    horizontal_padding = min(6, config.cell_width // 4)
    vertical_padding = min(6, config.cell_height // 4)
    max_width = (config.cell_width - horizontal_padding) * zoom
    max_height = (config.cell_height - vertical_padding) * zoom
    scale = min(max_width / image.width, max_height / image.height)
    size = (max(1, round(image.width * scale)), max(1, round(image.height * scale)))
    return image.resize(size, Image.Resampling.LANCZOS)


def build_atlas(config: MobConfig, zoom: int) -> Image.Image:
    atlas = Image.new(
        "RGBA",
        (config.cell_width * COLS * zoom, config.cell_height * ROWS * zoom),
        (0, 0, 0, 0),
    )
    for index, source in enumerate(config.sources):
        pose = fit_pose(source, config.source_crops[index], config, zoom)
        cell_x = (index % COLS) * config.cell_width * zoom
        cell_y = (index // COLS) * config.cell_height * zoom
        x = cell_x + (config.cell_width * zoom - pose.width) // 2
        y = cell_y + config.cell_height * zoom - pose.height - (2 * zoom)
        atlas.alpha_composite(pose, (x, y))
    return atlas


def build_effect_data(config: MobConfig) -> bytes:
    output = io.BytesIO()
    output.write(bytes([len(POSE_NAMES)]))
    for index in range(len(POSE_NAMES)):
        x = (index % COLS) * config.cell_width
        y = (index // COLS) * config.cell_height
        output.write(bytes([index, x, y, config.cell_width, config.cell_height]))
    output.write(struct.pack(">h", len(FRAME_TO_POSE)))
    for pose_index in FRAME_TO_POSE:
        output.write(bytes([1]))
        output.write(struct.pack(">hhB", config.anchor_dx, config.anchor_dy, pose_index))
    output.write(struct.pack(">h", 0))
    return output.getvalue()


def encode_png(image: Image.Image) -> bytes:
    buffer = io.BytesIO()
    image.save(buffer, format="PNG", optimize=True)
    return buffer.getvalue()


def make_payload(effect_data: bytes, png: bytes) -> bytes:
    return (
        bytes([0])
        + struct.pack(">I", len(effect_data))
        + effect_data
        + struct.pack(">I", len(png))
        + png
        + bytes([0])
    )


def parse_effect_data(data: bytes, config: MobConfig) -> None:
    offset = 0
    if not data:
        raise ValueError("EffectData is empty")
    image_count = data[offset]
    offset += 1
    if image_count != len(POSE_NAMES):
        raise ValueError(f"Expected 5 image infos, found {image_count}")
    for index in range(image_count):
        if offset + 5 > len(data):
            raise ValueError("Truncated image info")
        image_id, x, y, width, height = data[offset:offset + 5]
        offset += 5
        expected = (
            index,
            (index % COLS) * config.cell_width,
            (index // COLS) * config.cell_height,
            config.cell_width,
            config.cell_height,
        )
        if (image_id, x, y, width, height) != expected:
            raise ValueError(f"Image info {index} mismatch")

    if offset + 2 > len(data):
        raise ValueError("Missing frame count")
    frame_count = struct.unpack(">h", data[offset:offset + 2])[0]
    offset += 2
    if frame_count != len(FRAME_TO_POSE):
        raise ValueError(f"Expected 12 frames, found {frame_count}")
    for expected_pose in FRAME_TO_POSE:
        if offset + 6 > len(data):
            raise ValueError("Truncated frame data")
        part_count = data[offset]
        offset += 1
        dx, dy, pose = struct.unpack(">hhB", data[offset:offset + 5])
        offset += 5
        if part_count != 1:
            raise ValueError(f"Expected one part per frame, found {part_count}")
        if (dx, dy, pose) != (config.anchor_dx, config.anchor_dy, expected_pose):
            raise ValueError("Frame anchor or action mapping mismatch")

    if offset + 2 > len(data):
        raise ValueError("Missing arrFrame count")
    arr_frame_count = struct.unpack(">h", data[offset:offset + 2])[0]
    offset += 2
    if arr_frame_count < 0:
        raise ValueError("Negative arrFrame count")
    arr_bytes = arr_frame_count * 2
    if offset + arr_bytes != len(data):
        raise ValueError("Truncated or trailing EffectData bytes")


def parse_payload(payload: bytes, config: MobConfig, zoom: int) -> dict[str, int | str]:
    if len(payload) < 14:
        raise ValueError("Payload is too short")
    if payload[0] != 0:
        raise ValueError(f"readType must be 0, found {payload[0]}")
    effect_length = struct.unpack(">I", payload[1:5])[0]
    effect_start = 5
    effect_end = effect_start + effect_length
    if effect_length <= 0 or effect_end + 5 > len(payload):
        raise ValueError("Invalid EffectData byte-array length")
    effect_data = payload[effect_start:effect_end]
    parse_effect_data(effect_data, config)

    png_length = struct.unpack(">I", payload[effect_end:effect_end + 4])[0]
    png_start = effect_end + 4
    png_end = png_start + png_length
    if png_length <= len(PNG_SIGNATURE) or png_end + 1 != len(payload):
        raise ValueError("Invalid PNG byte-array length or trailing bytes")
    png = payload[png_start:png_end]
    if not png.startswith(PNG_SIGNATURE):
        raise ValueError("Embedded image is not PNG")
    if payload[png_end] != 0:
        raise ValueError(f"typeData must be 0, found {payload[png_end]}")

    expected_size = (
        config.cell_width * COLS * zoom,
        config.cell_height * ROWS * zoom,
    )
    with Image.open(io.BytesIO(png)) as image:
        image.load()
        if image.size != expected_size:
            raise ValueError(f"Zoom x{zoom}: expected PNG {expected_size}, found {image.size}")
        rgba = image.convert("RGBA")
        alpha_min, alpha_max = rgba.getchannel("A").getextrema()
        if alpha_max == 0 or alpha_min == 255:
            raise ValueError(f"Zoom x{zoom}: PNG must contain visible pixels and transparency")
        image_mode = image.mode

    return {
        "zoom": zoom,
        "payloadBytes": len(payload),
        "effectDataBytes": effect_length,
        "pngBytes": png_length,
        "width": expected_size[0],
        "height": expected_size[1],
        "mode": image_mode,
    }


def write_atomic(target: Path, data: bytes) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_name(target.name + ".tmp")
    temporary.write_bytes(data)
    temporary.replace(target)


def make_preview(atlas: Image.Image, config: MobConfig, target: Path) -> None:
    zoom = 3
    cell_width = config.cell_width * zoom
    cell_height = config.cell_height * zoom
    footer = 58
    preview = Image.new("RGB", (cell_width * len(POSE_NAMES), cell_height + footer), (32, 25, 40))
    draw = ImageDraw.Draw(preview)
    labels = ("STAND", "WALK / CRAWL", "ATTACK", "HURT", "DIE")
    for index, label in enumerate(labels):
        source_x = (index % COLS) * cell_width
        source_y = (index // COLS) * cell_height
        pose = atlas.crop((source_x, source_y, source_x + cell_width, source_y + cell_height)).convert("RGBA")
        target_x = index * cell_width
        for y in range(0, cell_height, 24):
            for x in range(0, cell_width, 24):
                color = (69, 59, 78) if ((x // 24 + y // 24) % 2 == 0) else (51, 43, 60)
                draw.rectangle((target_x + x, y, target_x + x + 23, y + 23), fill=color)
        preview.paste(pose, (target_x, 0), pose)
        draw.text((target_x + 10, cell_height + 18), label, fill=(245, 224, 255))
    target.parent.mkdir(parents=True, exist_ok=True)
    preview.save(target, format="PNG", optimize=True)


def payload_path(project_root: Path, mob_id: int, zoom: int) -> Path:
    return project_root / "data" / "mob" / f"x{zoom}" / str(mob_id)


def build(config: MobConfig, project_root: Path) -> list[dict[str, int | str]]:
    effect_data = build_effect_data(config)
    parse_effect_data(effect_data, config)
    summaries: list[dict[str, int | str]] = []
    preview_atlas: Image.Image | None = None
    for zoom in range(1, 5):
        rgba_atlas = build_atlas(config, zoom)
        atlas = rgba_atlas.quantize(
            colors=256,
            method=Image.Quantize.FASTOCTREE,
            dither=Image.Dither.NONE,
        )
        atlas_path = config.asset_dir / "atlases" / f"atlas_x{zoom}.png"
        atlas_path.parent.mkdir(parents=True, exist_ok=True)
        atlas.save(atlas_path, format="PNG", optimize=True)
        png = encode_png(atlas)
        target = payload_path(project_root, config.mob_id, zoom)
        write_atomic(target, make_payload(effect_data, png))
        summaries.append(parse_payload(target.read_bytes(), config, zoom))
        if zoom == 3:
            preview_atlas = atlas
    if preview_atlas is None:
        raise RuntimeError("Preview atlas was not built")
    make_preview(preview_atlas, config, config.asset_dir / "preview-actions-x3.png")
    return summaries


def verify(config: MobConfig, project_root: Path) -> list[dict[str, int | str]]:
    summaries: list[dict[str, int | str]] = []
    effect_data: bytes | None = None
    for zoom in range(1, 5):
        target = payload_path(project_root, config.mob_id, zoom)
        if not target.is_file():
            raise FileNotFoundError(f"Missing runtime payload: {target}")
        payload = target.read_bytes()
        current_length = struct.unpack(">I", payload[1:5])[0] if len(payload) >= 5 else -1
        current_effect = payload[5:5 + current_length] if current_length >= 0 else b""
        if effect_data is None:
            effect_data = current_effect
        elif current_effect != effect_data:
            raise ValueError(f"Zoom x{zoom}: EffectData differs from x1")
        summaries.append(parse_payload(payload, config, zoom))
    return summaries


def main() -> int:
    args = parse_args()
    try:
        config = load_config(args.manifest)
        project_root = args.project_root.resolve()
        if not (project_root / "data" / "mob").is_dir():
            raise FileNotFoundError(f"Not an NRO project root (missing data/mob): {project_root}")
        summaries = build(config, project_root) if args.command == "build" else verify(config, project_root)
        print(json.dumps({"status": "ok", "mobId": config.mob_id, "files": summaries}, ensure_ascii=False, indent=2))
        return 0
    except (OSError, ValueError, struct.error) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
