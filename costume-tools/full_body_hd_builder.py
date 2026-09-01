from __future__ import annotations

import argparse
import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageOps


@dataclass(frozen=True)
class SheetConfig:
    key: str
    file_name: str
    columns: int
    rows: int


@dataclass(frozen=True)
class BodyFrameConfig:
    sheet: str
    source_index: int
    observed: str
    group: str
    offset_adjustment: tuple[int, int] = (0, 0)
    horizontal_flip: bool = False


@dataclass(frozen=True)
class CostumeConfig:
    project_dir: Path
    repository_root: Path
    name: str
    slug: str
    item_id: int
    part_ids: tuple[int, int, int]
    profile_icon_id: int
    first_body_image_id: int
    transparent_image_id: int
    item_icon_id: int
    profile_file_name: str
    item_icon_file_name: str
    sheets: tuple[SheetConfig, ...]
    body_mapping: tuple[BodyFrameConfig, ...]
    description: str
    source_directory: str = "source"
    body_image_ids_override: tuple[int, ...] | None = None
    shop_id: int | None = None
    tab_id: int | None = None
    shop_cost: int | None = None
    global_body_offset_adjustment: tuple[int, int] = (0, 0)

    @property
    def body_image_ids(self) -> tuple[int, ...]:
        if self.body_image_ids_override is not None:
            return self.body_image_ids_override
        return tuple(range(self.first_body_image_id, self.first_body_image_id + 17))


@dataclass
class Frame:
    image: Image.Image
    bbox_in_cell: tuple[int, int, int, int]
    cell_size: tuple[int, int]
    source_path: str
    edge_touches: list[str] = field(default_factory=list)
    kept_components: int = 0
    dropped_components: int = 0
    is_empty: bool = False


def mirror_frame(frame: Frame) -> Frame:
    """Mirror one isolated sprite while preserving its position inside the source cell."""
    left, top, right, bottom = frame.bbox_in_cell
    cell_width, _ = frame.cell_size
    return Frame(
        image=ImageOps.mirror(frame.image),
        bbox_in_cell=(cell_width - right, top, cell_width - left, bottom),
        cell_size=frame.cell_size,
        source_path=f"{frame.source_path}:mirrored",
        edge_touches=[
            "right" if edge == "left" else "left" if edge == "right" else edge
            for edge in frame.edge_touches
        ],
        kept_components=frame.kept_components,
        dropped_components=frame.dropped_components,
        is_empty=frame.is_empty,
    )


X1_REFERENCE_CANVAS = (96, 80)
X1_ITEM_ICON_CANVAS = (25, 22)
X1_PROFILE_ICON_CANVAS = (64, 64)
DESTINATION_PIVOT = (48, 68)
BASE_BODY_OFFSET = (-39, -53)
SOURCE_TO_X1_SCALE = 0.11
SOURCE_PIVOT_BOTTOM_MARGIN = 15
CROP_PADDING_X1 = 2
ALPHA_THRESHOLD = 12
PALETTE_COLORS = 256


def alpha_bbox(image: Image.Image, threshold: int = ALPHA_THRESHOLD) -> tuple[int, int, int, int]:
    mask = image.getchannel("A").point(lambda value: 255 if value >= threshold else 0)
    bbox = mask.getbbox()
    if bbox is None:
        raise ValueError("Sprite image is fully transparent")
    return bbox


def _smooth_projection(values: np.ndarray, radius: int) -> np.ndarray:
    if radius <= 0:
        return values.astype(np.float64)
    kernel = np.ones(radius * 2 + 1, dtype=np.float64) / (radius * 2 + 1)
    return np.convolve(values.astype(np.float64), kernel, mode="same")


def snapped_bounds(
    alpha: np.ndarray,
    axis: int,
    count: int,
    search_ratio: float,
    minimum_cell_ratio: float,
) -> list[int]:
    """Move nominal grid cuts to nearby transparent gutters without large cell drift."""
    length = alpha.shape[1] if axis == 0 else alpha.shape[0]
    other_length = alpha.shape[0] if axis == 0 else alpha.shape[1]
    nominal_step = length / count
    occupancy = (alpha >= ALPHA_THRESHOLD).sum(axis=axis)
    smoothed = _smooth_projection(occupancy, max(1, round(nominal_step * 0.006)))
    bounds = [0]
    for index in range(1, count):
        nominal = round(index * nominal_step)
        search_radius = max(3, round(nominal_step * search_ratio))
        minimum_cell = round(nominal_step * minimum_cell_ratio)
        start = max(bounds[-1] + minimum_cell, nominal - search_radius)
        remaining_cells = count - index
        end = min(length - remaining_cells * minimum_cell, nominal + search_radius)
        if start >= end:
            bounds.append(nominal)
            continue
        candidates = np.arange(start, end + 1)
        # Strongly prefer an empty/quiet gutter, then the smallest shift.
        normalized_occupancy = smoothed[candidates] / max(1, other_length)
        distance_penalty = np.abs(candidates - nominal) / max(1, search_radius)
        score = normalized_occupancy * 16.0 + distance_penalty * 0.18
        bounds.append(int(candidates[int(np.argmin(score))]))
    bounds.append(length)
    return bounds


def _isolate_cell(cell: Image.Image, source_label: str) -> Frame:
    rgba = np.asarray(cell)
    alpha = rgba[:, :, 3]
    binary = np.where(alpha >= ALPHA_THRESHOLD, 255, 0).astype(np.uint8)
    count, labels, stats, centroids = cv2.connectedComponentsWithStats(binary, connectivity=8)
    if count <= 1:
        # A generated contact sheet may intentionally reserve blank cells. Preserve
        # the cell index for the mapping, then reject it only if a BODY slot uses it.
        return Frame(
            image=Image.new("RGBA", (1, 1), (0, 0, 0, 0)),
            bbox_in_cell=(0, 0, 0, 0),
            cell_size=cell.size,
            source_path=source_label,
            is_empty=True,
        )

    areas = stats[1:, cv2.CC_STAT_AREA]
    largest_area = int(areas.max())
    cell_width, cell_height = cell.size
    keep_ids: list[int] = []
    dropped = 0
    for component_id in range(1, count):
        left = int(stats[component_id, cv2.CC_STAT_LEFT])
        top = int(stats[component_id, cv2.CC_STAT_TOP])
        width = int(stats[component_id, cv2.CC_STAT_WIDTH])
        height = int(stats[component_id, cv2.CC_STAT_HEIGHT])
        area = int(stats[component_id, cv2.CC_STAT_AREA])
        center_x, center_y = centroids[component_id]
        touches_edge = (
            left == 0 or top == 0 or left + width >= cell_width or top + height >= cell_height
        )
        near_corner = (
            (center_x < cell_width * 0.04 or center_x > cell_width * 0.96)
            and (center_y < cell_height * 0.08 or center_y > cell_height * 0.92)
        )
        tiny = area < max(5, round(largest_area * 0.00035))
        boundary_fragment = touches_edge and area < max(12, round(largest_area * 0.05))
        # Keep detached water/fire/snake particles. Only reject tiny corner fragments
        # that touch a grid boundary and are characteristic of a neighbouring frame.
        if boundary_fragment or (touches_edge and near_corner and tiny):
            dropped += 1
            continue
        keep_ids.append(component_id)

    if not keep_ids:
        raise ValueError(f"All sprite components were rejected in {source_label}")
    hard_mask = np.isin(labels, keep_ids)
    expanded = cv2.dilate(hard_mask.astype(np.uint8), np.ones((3, 3), np.uint8), iterations=1).astype(bool)
    mask = expanded & (alpha > 0)
    ys, xs = np.nonzero(mask)
    left, top = int(xs.min()), int(ys.min())
    right, bottom = int(xs.max()) + 1, int(ys.max()) + 1
    isolated = np.zeros((bottom - top, right - left, 4), dtype=np.uint8)
    source_crop = rgba[top:bottom, left:right]
    crop_mask = mask[top:bottom, left:right]
    isolated[crop_mask] = source_crop[crop_mask]

    edge_touches: list[str] = []
    if left == 0:
        edge_touches.append("left")
    if top == 0:
        edge_touches.append("top")
    if right == cell_width:
        edge_touches.append("right")
    if bottom == cell_height:
        edge_touches.append("bottom")
    return Frame(
        image=Image.fromarray(isolated, mode="RGBA"),
        bbox_in_cell=(left, top, right, bottom),
        cell_size=cell.size,
        source_path=source_label,
        edge_touches=edge_touches,
        kept_components=len(keep_ids),
        dropped_components=dropped,
    )


def split_sheet(source_dir: Path, sheet: SheetConfig) -> tuple[list[Frame], dict]:
    source_path = source_dir / sheet.file_name
    if not source_path.is_file():
        raise FileNotFoundError(f"Missing source sheet: {source_path}")
    image = Image.open(source_path).convert("RGBA")
    alpha = np.asarray(image.getchannel("A"))
    y_bounds = snapped_bounds(
        alpha,
        axis=1,
        count=sheet.rows,
        search_ratio=0.10,
        minimum_cell_ratio=0.70,
    )
    x_bounds_by_row: list[list[int]] = []
    frames: list[Frame] = []
    for row in range(sheet.rows):
        # These AI-exported sheets are visual contact sheets, not strict tile grids:
        # large effects make the horizontal spacing different on each row. Detect
        # gutters per row so a frame in row 1 cannot pull the cut for row 2.
        row_alpha = alpha[y_bounds[row] : y_bounds[row + 1], :]
        x_bounds = snapped_bounds(
            row_alpha,
            axis=0,
            count=sheet.columns,
            search_ratio=0.46,
            minimum_cell_ratio=0.42,
        )
        x_bounds_by_row.append(x_bounds)
        for column in range(sheet.columns):
            box = (x_bounds[column], y_bounds[row], x_bounds[column + 1], y_bounds[row + 1])
            frames.append(
                _isolate_cell(
                    image.crop(box),
                    f"{sheet.file_name}:{sheet.key}[{len(frames)}]",
                )
            )
    return frames, {
        "file": sheet.file_name,
        "size": [image.width, image.height],
        "columns": sheet.columns,
        "rows": sheet.rows,
        "xBoundsByRow": x_bounds_by_row,
        "yBounds": y_bounds,
    }


def _render_frame(frame: Frame, scale: int) -> Image.Image:
    target_size = (
        max(1, round(frame.image.width * SOURCE_TO_X1_SCALE * scale)),
        max(1, round(frame.image.height * SOURCE_TO_X1_SCALE * scale)),
    )
    rendered_sprite = frame.image.resize(target_size, Image.Resampling.LANCZOS)
    source_pivot_x = frame.cell_size[0] / 2
    source_pivot_y = frame.cell_size[1] - SOURCE_PIVOT_BOTTOM_MARGIN
    destination_x = round(
        (DESTINATION_PIVOT[0] + (frame.bbox_in_cell[0] - source_pivot_x) * SOURCE_TO_X1_SCALE)
        * scale
    )
    destination_y = round(
        (DESTINATION_PIVOT[1] + (frame.bbox_in_cell[1] - source_pivot_y) * SOURCE_TO_X1_SCALE)
        * scale
    )
    canvas = Image.new(
        "RGBA",
        (X1_REFERENCE_CANVAS[0] * scale, X1_REFERENCE_CANVAS[1] * scale),
        (0, 0, 0, 0),
    )
    if (
        destination_x < scale
        or destination_y < scale
        or destination_x + rendered_sprite.width > canvas.width - scale
        or destination_y + rendered_sprite.height > canvas.height - scale
    ):
        raise ValueError(
            f"{frame.source_path} exceeds safe render canvas at x{scale}: "
            f"destination=({destination_x},{destination_y}), size={rendered_sprite.size}, canvas={canvas.size}"
        )
    canvas.alpha_composite(rendered_sprite, (destination_x, destination_y))
    return canvas


def _quantize(image: Image.Image) -> Image.Image:
    return image.quantize(
        colors=PALETTE_COLORS,
        method=Image.Quantize.FASTOCTREE,
        dither=Image.Dither.NONE,
    )


def _logical_crop(rendered: list[Image.Image]) -> tuple[int, int, int, int]:
    bboxes = [alpha_bbox(image) for image in rendered]
    left = min(bbox[0] // scale for scale, bbox in enumerate(bboxes, start=1))
    top = min(bbox[1] // scale for scale, bbox in enumerate(bboxes, start=1))
    right = max((bbox[2] + scale - 1) // scale for scale, bbox in enumerate(bboxes, start=1))
    bottom = max((bbox[3] + scale - 1) // scale for scale, bbox in enumerate(bboxes, start=1))
    crop = (
        left - CROP_PADDING_X1,
        top - CROP_PADDING_X1,
        right + CROP_PADDING_X1,
        bottom + CROP_PADDING_X1,
    )
    if crop[0] < 0 or crop[1] < 0 or crop[2] > X1_REFERENCE_CANVAS[0] or crop[3] > X1_REFERENCE_CANVAS[1]:
        raise ValueError(f"Sprite lacks the required transparent crop margin: crop={crop}")
    return crop


def _has_transparent_border(image: Image.Image) -> bool:
    alpha = np.asarray(image.convert("RGBA"))[:, :, 3]
    return bool(
        np.all(alpha[0, :] == 0)
        and np.all(alpha[-1, :] == 0)
        and np.all(alpha[:, 0] == 0)
        and np.all(alpha[:, -1] == 0)
    )


def _write_frame_scales(
    config: CostumeConfig,
    image_id: int,
    frame: Frame,
    offset_adjustment: tuple[int, int],
    write_output: bool = True,
) -> tuple[dict, Image.Image]:
    rendered = [_render_frame(frame, scale) for scale in range(1, 5)]
    crop = _logical_crop(rendered)
    metadata: dict[str, dict] = {}
    preview_x4 = Image.new("RGBA", (X1_REFERENCE_CANVAS[0] * 4, X1_REFERENCE_CANVAS[1] * 4))
    for scale, canvas in enumerate(rendered, start=1):
        output_dir = config.repository_root / "data" / "icon" / f"x{scale}"
        output_dir.mkdir(parents=True, exist_ok=True)
        scaled_crop = tuple(value * scale for value in crop)
        encoded = _quantize(canvas.crop(scaled_crop))
        output_path = output_dir / f"{image_id}.png"
        if write_output:
            encoded.save(output_path, optimize=True)
        elif not output_path.is_file():
            raise FileNotFoundError(f"Missing existing output for unselected slot: {output_path}")
        decoded = Image.open(output_path).convert("RGBA")
        expected_size = ((crop[2] - crop[0]) * scale, (crop[3] - crop[1]) * scale)
        if decoded.size != expected_size:
            raise ValueError(f"Unexpected size for {output_path}: {decoded.size} != {expected_size}")
        if not _has_transparent_border(decoded):
            raise ValueError(f"Missing transparent border in {output_path}")
        metadata[f"x{scale}"] = {
            "size": list(decoded.size),
            "bytes": output_path.stat().st_size,
            "transparentBorder": True,
        }
        if scale == 4:
            preview_x4.alpha_composite(decoded, (crop[0] * 4, crop[1] * 4))
    dx = BASE_BODY_OFFSET[0] + crop[0] + offset_adjustment[0]
    dy = BASE_BODY_OFFSET[1] + crop[1] + offset_adjustment[1]
    if not (-128 <= dx <= 127 and -128 <= dy <= 127):
        raise ValueError(f"Offset outside signed byte range for image {image_id}: ({dx},{dy})")
    return {
        "dx": dx,
        "dy": dy,
        "offsetAdjustment": list(offset_adjustment),
        "cropX1": list(crop),
        "sourceBbox": list(frame.bbox_in_cell),
        "cellSize": list(frame.cell_size),
        "edgeTouches": frame.edge_touches,
        "keptComponents": frame.kept_components,
        "droppedComponents": frame.dropped_components,
        "scales": metadata,
    }, preview_x4


def _render_standalone(
    source_path: Path,
    scale: int,
    canvas_x1: tuple[int, int],
    vertical_align: str = "center",
) -> Image.Image:
    source = Image.open(source_path).convert("RGBA")
    source = source.crop(alpha_bbox(source))
    maximum_size = ((canvas_x1[0] - 2) * scale, (canvas_x1[1] - 2) * scale)
    source.thumbnail(maximum_size, Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (canvas_x1[0] * scale, canvas_x1[1] * scale), (0, 0, 0, 0))
    if vertical_align == "center":
        destination_y = (canvas.height - source.height) // 2
    elif vertical_align == "bottom":
        # Head-avatar art is drawn directly above the profile tabs. Centering a
        # 60-62 px bust in a 64 px canvas leaves 1-3 logical pixels below it,
        # which the client enlarges into the visible horizontal gap reported on
        # costume items 2062..2070. Keep the source crop and scale, but anchor
        # its last opaque row to the bottom edge like the known-good avatars.
        destination_y = canvas.height - source.height
    else:
        raise ValueError(f"Unsupported standalone vertical alignment: {vertical_align}")
    canvas.alpha_composite(source, ((canvas.width - source.width) // 2, destination_y))
    return canvas


def _write_standalone_scales(
    config: CostumeConfig,
    source_name: str,
    image_id: int,
    canvas_x1: tuple[int, int],
    write_output: bool = True,
    vertical_align: str = "center",
) -> dict:
    source_path = config.project_dir / config.source_directory / source_name
    if not source_path.is_file():
        raise FileNotFoundError(f"Missing standalone source: {source_path}")
    scales: dict[str, dict] = {}
    for scale in range(1, 5):
        output_dir = config.repository_root / "data" / "icon" / f"x{scale}"
        output_dir.mkdir(parents=True, exist_ok=True)
        output_path = output_dir / f"{image_id}.png"
        if write_output:
            _quantize(
                _render_standalone(
                    source_path,
                    scale,
                    canvas_x1,
                    vertical_align=vertical_align,
                )
            ).save(output_path, optimize=True)
        elif not output_path.is_file():
            raise FileNotFoundError(f"Missing existing standalone output: {output_path}")
        decoded = Image.open(output_path).convert("RGBA")
        if decoded.size != (canvas_x1[0] * scale, canvas_x1[1] * scale):
            raise ValueError(f"Unexpected icon size: {output_path} -> {decoded.size}")
        if vertical_align == "bottom":
            bbox = alpha_bbox(decoded)
            if bbox[3] != decoded.height:
                raise ValueError(f"Bottom-aligned profile does not touch canvas bottom: {output_path}")
        elif not _has_transparent_border(decoded):
            raise ValueError(f"Missing transparent border in {output_path}")
        scales[f"x{scale}"] = {
            "size": list(decoded.size),
            "bytes": output_path.stat().st_size,
            "verticalAlign": vertical_align,
        }
    return scales


def _write_transparent_scales(config: CostumeConfig) -> None:
    for scale in range(1, 5):
        output_dir = config.repository_root / "data" / "icon" / f"x{scale}"
        output_dir.mkdir(parents=True, exist_ok=True)
        Image.new("RGBA", (scale, scale), (0, 0, 0, 0)).save(
            output_dir / f"{config.transparent_image_id}.png", optimize=True
        )


def _checkerboard(size: tuple[int, int], square: int = 16) -> Image.Image:
    image = Image.new("RGBA", size, (31, 38, 48, 255))
    draw = ImageDraw.Draw(image)
    for y in range(0, size[1], square):
        for x in range(0, size[0], square):
            if (x // square + y // square) % 2:
                draw.rectangle((x, y, min(x + square - 1, size[0] - 1), min(y + square - 1, size[1] - 1)), fill=(45, 54, 67, 255))
    return image


def _build_contacts(config: CostumeConfig, cells: dict[str, list[Frame]]) -> None:
    preview_dir = config.project_dir / "preview"
    preview_dir.mkdir(parents=True, exist_ok=True)
    font = ImageFont.load_default()
    for sheet in config.sheets:
        frames = cells[sheet.key]
        tile = (240, 180)
        label_height = 24
        contact = _checkerboard((sheet.columns * tile[0], sheet.rows * (tile[1] + label_height)), 12)
        draw = ImageDraw.Draw(contact)
        for index, frame in enumerate(frames):
            sprite = frame.image.copy()
            sprite.thumbnail((tile[0] - 12, tile[1] - 12), Image.Resampling.LANCZOS)
            column = index % sheet.columns
            row = index // sheet.columns
            x0 = column * tile[0]
            y0 = row * (tile[1] + label_height)
            contact.alpha_composite(sprite, (x0 + (tile[0] - sprite.width) // 2, y0 + (tile[1] - sprite.height) // 2))
            color = (244, 96, 96, 255) if frame.edge_touches else (126, 154, 185, 255)
            draw.rectangle((x0, y0, x0 + tile[0] - 1, y0 + tile[1] - 1), outline=color, width=2)
            suffix = f" edge={','.join(frame.edge_touches)}" if frame.edge_touches else ""
            draw.text((x0 + 5, y0 + tile[1] + 5), f"{sheet.key}[{index}]{suffix}", fill=(240, 244, 249, 255), font=font)
        contact.save(preview_dir / f"contact-{sheet.key}.png", optimize=True)


def _build_selected_preview(config: CostumeConfig, images_x4: list[Image.Image]) -> None:
    tile = (X1_REFERENCE_CANVAS[0] * 4, X1_REFERENCE_CANVAS[1] * 4)
    columns = 5
    rows = 4
    label_height = 28
    preview = _checkerboard((columns * tile[0], rows * (tile[1] + label_height)), 16)
    draw = ImageDraw.Draw(preview)
    font = ImageFont.load_default()
    for index, image_x4 in enumerate(images_x4):
        row, column = divmod(index, columns)
        x = column * tile[0]
        y = row * (tile[1] + label_height)
        preview.alpha_composite(image_x4, (x, y))
        draw.rectangle((x, y, x + tile[0] - 1, y + tile[1] - 1), outline=(120, 153, 183, 255), width=2)
        mapping = config.body_mapping[index]
        draw.text(
            (x + 6, y + tile[1] + 7),
            f"BODY[{index}] {config.body_image_ids[index]} {mapping.sheet}[{mapping.source_index}]",
            fill=(241, 245, 249, 255),
            font=font,
        )
    preview.save(config.project_dir / "preview" / "selected-body-frames.png", optimize=True)


def _build_selected_x1_preview(config: CostumeConfig, frame_exports: list[dict]) -> None:
    tile = (X1_REFERENCE_CANVAS[0] * 4, X1_REFERENCE_CANVAS[1] * 4)
    columns = 5
    rows = 4
    label_height = 28
    preview = _checkerboard((columns * tile[0], rows * (tile[1] + label_height)), 16)
    draw = ImageDraw.Draw(preview)
    font = ImageFont.load_default()
    for index, export in enumerate(frame_exports):
        image_id = config.body_image_ids[index]
        cropped = Image.open(config.repository_root / "data" / "icon" / "x1" / f"{image_id}.png").convert("RGBA")
        reference = Image.new("RGBA", X1_REFERENCE_CANVAS, (0, 0, 0, 0))
        reference.alpha_composite(cropped, (export["cropX1"][0], export["cropX1"][1]))
        enlarged = reference.resize(tile, Image.Resampling.NEAREST)
        row, column = divmod(index, columns)
        x = column * tile[0]
        y = row * (tile[1] + label_height)
        preview.alpha_composite(enlarged, (x, y))
        draw.rectangle((x, y, x + tile[0] - 1, y + tile[1] - 1), outline=(120, 153, 183, 255), width=2)
        draw.text(
            (x + 6, y + tile[1] + 7),
            f"BODY[{index}] {image_id} x1 enlarged 4x",
            fill=(241, 245, 249, 255),
            font=font,
        )
    preview.save(config.project_dir / "preview" / "selected-body-frames-x1-nearest.png", optimize=True)


def _build_client_anchor_preview(config: CostumeConfig, frame_exports: list[dict]) -> None:
    scale = 4
    tile_x1 = X1_REFERENCE_CANVAS
    tile = (tile_x1[0] * scale, tile_x1[1] * scale)
    label_height = 32
    preview = _checkerboard((tile[0] * 2, tile[1] + label_height), 16)
    draw = ImageDraw.Draw(preview)
    font = ImageFont.load_default()
    # Sanemi's verified idle feet end at logical y=18 and center near x=10.
    origin_x1 = (40, 50)
    ground_y = (origin_x1[1] + 18) * scale
    shadow_center_x = (origin_x1[0] + 10) * scale
    for column, slot in enumerate((1, 2)):
        tile_left = column * tile[0]
        draw.line((tile_left, ground_y, tile_left + tile[0] - 1, ground_y), fill=(228, 190, 92, 255), width=2)
        draw.ellipse(
            (
                tile_left + shadow_center_x - 13 * scale,
                ground_y - 2 * scale,
                tile_left + shadow_center_x + 13 * scale,
                ground_y + 3 * scale,
            ),
            fill=(5, 8, 12, 125),
        )
        draw.line(
            (
                tile_left + origin_x1[0] * scale - 4,
                origin_x1[1] * scale,
                tile_left + origin_x1[0] * scale + 4,
                origin_x1[1] * scale,
            ),
            fill=(255, 95, 95, 255),
            width=2,
        )
        export = frame_exports[slot]
        image_id = config.body_image_ids[slot]
        sprite = Image.open(config.repository_root / "data" / "icon" / "x4" / f"{image_id}.png").convert("RGBA")
        paste_x = tile_left + (origin_x1[0] + export["dx"]) * scale
        paste_y = (origin_x1[1] + export["dy"]) * scale
        preview.alpha_composite(sprite, (paste_x, paste_y))
        draw.text(
            (tile_left + 6, tile[1] + 8),
            f"BODY[{slot}] {image_id} dx={export['dx']} dy={export['dy']}",
            fill=(241, 245, 249, 255),
            font=font,
        )
    preview.save(config.project_dir / "preview" / "client-anchor-idle-fly.png", optimize=True)


def _data_rows(rows: Iterable[Iterable[int]]) -> str:
    return json.dumps(list(rows), separators=(",", ":"))


def _write_install_files(config: CostumeConfig, manifest: dict) -> None:
    head_data = _data_rows(manifest["head"])
    body_data = _data_rows([[row["imageId"], row["dx"], row["dy"]] for row in manifest["body"]])
    leg_data = _data_rows(manifest["leg"])
    head_id, body_id, leg_id = config.part_ids
    escaped_name = config.name.replace("'", "''")
    escaped_description = config.description.replace("'", "''")
    install_sql = f"""-- Install {config.name}; IDs were allocated contiguously from the live team2026 database.\nSTART TRANSACTION;\n\nDELETE FROM part WHERE id IN ({head_id}, {body_id}, {leg_id});\nINSERT INTO part (id, `TYPE`, `DATA`) VALUES\n({head_id}, 0, '{head_data}'),\n({body_id}, 1, '{body_data}'),\n({leg_id}, 2, '{leg_data}');\n\nDELETE FROM head_avatar WHERE head_id = {head_id};\nINSERT INTO head_avatar (head_id, avatar_id) VALUES ({head_id}, {config.profile_icon_id});\n\nINSERT INTO item_template\n(id, `TYPE`, gender, `NAME`, description, level, icon_id, part, is_up_to_up, power_require, gold, gem, head, body, leg)\nVALUES\n({config.item_id}, 5, 3, '{escaped_name}', '{escaped_description}', 1, {config.item_icon_id}, -1, 0, 0, 0, 0, {head_id}, {body_id}, {leg_id})\nON DUPLICATE KEY UPDATE\n`TYPE`=VALUES(`TYPE`), gender=VALUES(gender), `NAME`=VALUES(`NAME`),\ndescription=VALUES(description), level=VALUES(level), icon_id=VALUES(icon_id),\npart=VALUES(part), is_up_to_up=VALUES(is_up_to_up), power_require=VALUES(power_require),\ngold=VALUES(gold), gem=VALUES(gem), head=VALUES(head), body=VALUES(body), leg=VALUES(leg);\n\nDELETE FROM item_default_option WHERE item_template_id = {config.item_id};\n\nINSERT INTO item_shop\n(tab_id, temp_id, is_new, is_sell, type_sell, cost, icon_spec, option_mode, create_time)\nSELECT {config.tab_id}, {config.item_id}, 1, 1, 1, {config.shop_cost}, 0, 0, NOW()\nWHERE EXISTS (SELECT 1 FROM tab_shop WHERE id = {config.tab_id} AND shop_id = {config.shop_id})\n  AND NOT EXISTS (SELECT 1 FROM item_shop WHERE tab_id = {config.tab_id} AND temp_id = {config.item_id});\n\nCOMMIT;\n"""
    rollback_sql = f"""-- Remove only the rows installed for {config.name}.\nSTART TRANSACTION;\n\nDELETE FROM item_shop_option WHERE item_shop_id IN\n    (SELECT id FROM item_shop WHERE tab_id = {config.tab_id} AND temp_id = {config.item_id});\nDELETE FROM item_shop WHERE tab_id = {config.tab_id} AND temp_id = {config.item_id};\nDELETE FROM item_default_option WHERE item_template_id = {config.item_id};\nDELETE FROM head_avatar WHERE head_id = {head_id} AND avatar_id = {config.profile_icon_id};\nDELETE FROM item_template WHERE id = {config.item_id}\n    AND head = {head_id} AND body = {body_id} AND leg = {leg_id};\nDELETE FROM part WHERE id IN ({head_id}, {body_id}, {leg_id});\n\nCOMMIT;\n"""
    if config.shop_id is None:
        install_sql = f"""-- Install {config.name}; no item_shop row was requested.
START TRANSACTION;

DELETE FROM part WHERE id IN ({head_id}, {body_id}, {leg_id});
INSERT INTO part (id, `TYPE`, `DATA`) VALUES
({head_id}, 0, '{head_data}'),
({body_id}, 1, '{body_data}'),
({leg_id}, 2, '{leg_data}');

DELETE FROM head_avatar WHERE head_id = {head_id};
INSERT INTO head_avatar (head_id, avatar_id) VALUES ({head_id}, {config.profile_icon_id});

INSERT INTO item_template
(id, `TYPE`, gender, `NAME`, description, level, icon_id, part, is_up_to_up, power_require, gold, gem, head, body, leg)
VALUES
({config.item_id}, 5, 3, '{escaped_name}', '{escaped_description}', 1, {config.item_icon_id}, -1, 0, 0, 0, 0, {head_id}, {body_id}, {leg_id})
ON DUPLICATE KEY UPDATE
`TYPE`=VALUES(`TYPE`), gender=VALUES(gender), `NAME`=VALUES(`NAME`),
description=VALUES(description), level=VALUES(level), icon_id=VALUES(icon_id), part=VALUES(part), is_up_to_up=VALUES(is_up_to_up), power_require=VALUES(power_require),
gold=VALUES(gold), gem=VALUES(gem), head=VALUES(head), body=VALUES(body), leg=VALUES(leg);

DELETE FROM item_default_option WHERE item_template_id = {config.item_id};
-- Add an item_shop/admin/gift distribution separately when requested.
COMMIT;
"""
        rollback_sql = f"""-- Remove only the rows installed for {config.name}; no item_shop row was created.
START TRANSACTION;

DELETE FROM item_default_option WHERE item_template_id = {config.item_id};
DELETE FROM head_avatar WHERE head_id = {head_id} AND avatar_id = {config.profile_icon_id};
DELETE FROM item_template WHERE id = {config.item_id}
    AND head = {head_id} AND body = {body_id} AND leg = {leg_id};
DELETE FROM part WHERE id IN ({head_id}, {body_id}, {leg_id});

COMMIT;
"""
    (config.project_dir / "install.sql").write_text(install_sql, encoding="utf-8")
    (config.project_dir / "rollback.sql").write_text(rollback_sql, encoding="utf-8")


def _write_readme(config: CostumeConfig, manifest: dict) -> None:
    head_data = _data_rows(manifest["head"])
    body_data = _data_rows([[row["imageId"], row["dx"], row["dy"]] for row in manifest["body"]])
    leg_data = _data_rows(manifest["leg"])
    lines = [
        f"# {config.name}",
        "",
        "Cải trang full-body HD; HEAD và LEG dùng ảnh trong suốt vì tóc, vũ khí và hiệu ứng đi xuyên ranh giới part.",
        "Mỗi scale x1-x4 được render trực tiếp từ ảnh nguồn bằng Lanczos, có viền alpha an toàn và không phóng từ x1.",
        "",
        f"- Item: `{config.item_id}`; parts HEAD/BODY/LEG: `{config.part_ids[0]}/{config.part_ids[1]}/{config.part_ids[2]}`.",
        f"- Icon hành trang: `{config.item_icon_id}`; avatar profile: `{config.profile_icon_id}`.",
        (
            f"- Shop: shop `{config.shop_id}`, tab `{config.tab_id}`, giá `{config.shop_cost}`."
            if config.shop_id is not None
            else "- Nơi nhận: chưa cấu hình; `install.sql` không thêm `item_shop`."
        ),
        "- Không thêm option mặc định.",
        "",
        "## DATA",
        "",
        "```json",
        f"HEAD = {head_data}",
        f"BODY = {body_data}",
        f"LEG  = {leg_data}",
        "```",
        "",
        "## Mapping BODY",
        "",
        "| Slot | imageId | Nguồn | Quan sát | Nhóm |",
        "|---:|---:|---|---|---|",
    ]
    for row in manifest["body"]:
        lines.append(
            f"| {row['slot']} | {row['imageId']} | `{row['sheet']}[{row['sourceIndex']}]` | {row['observed']} | {row['suggestedGroup']} |"
        )
    lines.extend(
        [
            "",
            "## Kiểm soát chất lượng",
            "",
            "- `preview/contact-*.png`: toàn bộ ô sau khi dò ranh giới alpha; viền đỏ báo frame chạm mép ô để rà thủ công.",
            "- `preview/selected-body-frames.png`: 17 frame xuất thật trên nền caro tối.",
            "- `qa-report.json`: ranh giới grid, component giữ/bỏ, crop, offset và kiểm tra viền alpha từng scale.",
            "- `install.sql` / `rollback.sql`: cài hoặc gỡ đúng riêng bộ cải trang này.",
            "",
        ]
    )
    (config.project_dir / "README.md").write_text("\n".join(lines), encoding="utf-8")


def build(
    config: CostumeConfig,
    contacts_only: bool = False,
    selected_slots: set[int] | None = None,
    write_profile: bool = False,
    write_item_icon: bool = False,
) -> None:
    if len(config.body_mapping) != 17:
        raise ValueError(f"BODY mapping must contain exactly 17 slots, got {len(config.body_mapping)}")
    if selected_slots is not None and not selected_slots.issubset(range(17)):
        raise ValueError(f"BODY slots must be within 0..16: {sorted(selected_slots)}")
    if len(config.body_image_ids) != 17 or len(set(config.body_image_ids)) != 17:
        raise ValueError(f"BODY image IDs must contain 17 unique values: {config.body_image_ids}")
    if config.part_ids != tuple(range(config.part_ids[0], config.part_ids[0] + 3)):
        raise ValueError(f"Part IDs are not contiguous: {config.part_ids}")
    shop_values = (config.shop_id, config.tab_id, config.shop_cost)
    if any(value is None for value in shop_values) and any(value is not None for value in shop_values):
        raise ValueError("shop_id, tab_id, and shop_cost must either all be set or all be None")
    reserved_image_ids = {
        config.profile_icon_id,
        config.transparent_image_id,
        config.item_icon_id,
    }
    overlapping_ids = sorted(set(config.body_image_ids) & reserved_image_ids)
    if overlapping_ids:
        raise ValueError(f"BODY image IDs overlap reserved image IDs: {overlapping_ids}")

    source_dir = config.project_dir / config.source_directory
    all_cells: dict[str, list[Frame]] = {}
    sheets_qa: dict[str, dict] = {}
    for sheet in config.sheets:
        frames, qa = split_sheet(source_dir, sheet)
        all_cells[sheet.key] = frames
        sheets_qa[sheet.key] = qa
    _build_contacts(config, all_cells)
    if contacts_only:
        return

    frame_exports: list[dict] = []
    selected_previews: list[Image.Image] = []
    for slot, (image_id, mapping) in enumerate(zip(config.body_image_ids, config.body_mapping, strict=True)):
        try:
            frame = all_cells[mapping.sheet][mapping.source_index]
        except (KeyError, IndexError) as error:
            raise ValueError(f"Invalid mapping at BODY[{slot}]: {mapping}") from error
        if frame.is_empty:
            raise ValueError(f"BODY[{slot}] references an intentionally blank cell: {mapping}")
        if mapping.horizontal_flip:
            frame = mirror_frame(frame)
        combined_offset_adjustment = (
            config.global_body_offset_adjustment[0] + mapping.offset_adjustment[0],
            config.global_body_offset_adjustment[1] + mapping.offset_adjustment[1],
        )
        export, preview = _write_frame_scales(
            config,
            image_id,
            frame,
            combined_offset_adjustment,
            write_output=selected_slots is None or slot in selected_slots,
        )
        export.update(
            {
                "slot": slot,
                "imageId": image_id,
                "sheet": mapping.sheet,
                "sourceIndex": mapping.source_index,
                "observed": mapping.observed,
                "suggestedGroup": mapping.group,
                "mirroredHorizontally": mapping.horizontal_flip,
            }
        )
        frame_exports.append(export)
        selected_previews.append(preview)

    if selected_slots is None:
        _write_transparent_scales(config)
    profile_scales = _write_standalone_scales(
        config,
        config.profile_file_name,
        config.profile_icon_id,
        X1_PROFILE_ICON_CANVAS,
        write_output=selected_slots is None or write_profile,
        vertical_align="bottom",
    )
    item_scales = _write_standalone_scales(
        config,
        config.item_icon_file_name,
        config.item_icon_id,
        X1_ITEM_ICON_CANVAS,
        write_output=selected_slots is None or write_item_icon,
    )
    _build_selected_preview(config, selected_previews)
    _build_selected_x1_preview(config, frame_exports)
    _build_client_anchor_preview(config, frame_exports)

    head_id, body_id, leg_id = config.part_ids
    manifest = {
        "itemId": config.item_id,
        "name": config.name,
        "iconId": config.item_icon_id,
        "profileIconId": config.profile_icon_id,
        "headAvatar": {"headId": head_id, "avatarId": config.profile_icon_id},
        "partIds": {"head": head_id, "body": body_id, "leg": leg_id},
        "transparentImageId": config.transparent_image_id,
        "bodyImageIds": list(config.body_image_ids),
        "scaleMode": "direct-source-lanczos-alpha-safe-grid-snap",
        "paletteColors": PALETTE_COLORS,
        "bodyReferenceCanvasX1": list(X1_REFERENCE_CANVAS),
        "itemIconCanvasX1": list(X1_ITEM_ICON_CANVAS),
        "profileIconCanvasX1": list(X1_PROFILE_ICON_CANVAS),
        "profileVerticalAlign": "bottom",
        "bodyOffsetMode": "per-frame-crop-compensated",
        "head": [[config.transparent_image_id, 0, 0] for _ in range(3)],
        "body": frame_exports,
        "leg": [[config.transparent_image_id, 0, 0] for _ in range(14)],
        "defaultOptions": [],
        "shop": (
            {"shopId": config.shop_id, "tabId": config.tab_id, "cost": config.shop_cost}
            if config.shop_id is not None
            else None
        ),
    }
    (config.project_dir / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    qa_report = {
        "status": "passed",
        "checks": [
            "17 BODY slots",
            "3 HEAD slots and 14 LEG slots",
            "x1-x4 rendered independently from source",
            "exact integer scale dimensions",
            "transparent border on every non-transparent export",
            "signed-byte dx/dy",
            "contiguous part IDs",
        ],
        "sheets": sheets_qa,
        "body": frame_exports,
        "profileScales": profile_scales,
        "itemScales": item_scales,
    }
    (config.project_dir / "qa-report.json").write_text(
        json.dumps(qa_report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    _write_install_files(config, manifest)
    _write_readme(config, manifest)


def run(config: CostumeConfig) -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--contacts-only", action="store_true")
    parser.add_argument(
        "--slots",
        nargs="*",
        type=int,
        help="Only rewrite the listed BODY image files; still refresh metadata and previews.",
    )
    parser.add_argument(
        "--profile",
        action="store_true",
        help="Rewrite the bottom-aligned profile image even during a partial BODY build.",
    )
    parser.add_argument(
        "--item-icon",
        action="store_true",
        help="Rewrite the inventory item icon even during a partial BODY build.",
    )
    args = parser.parse_args()
    selected_slots = None if args.slots is None else set(args.slots)
    build(
        config,
        contacts_only=args.contacts_only,
        selected_slots=selected_slots,
        write_profile=args.profile,
        write_item_icon=args.item_icon,
    )
