from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageOps


PROJECT_ROOT = Path(__file__).resolve().parents[3]
PROJECT_DIR = Path(__file__).resolve().parent
SOURCE_DIR = PROJECT_DIR / "source"


@dataclass(frozen=True)
class Sheet:
    key: str
    file_name: str
    columns: int
    rows: int


@dataclass(frozen=True)
class Frame:
    image: Image.Image
    bbox_in_cell: tuple[int, int, int, int]
    cell_size: tuple[int, int]
    target_height_x1: int | None = None


SHEETS = (
    Sheet("idle", "idle.png", 3, 2),
    Sheet("walk", "walk.png", 4, 2),
    Sheet("run", "walk_1.png", 4, 2),
    Sheet("fly", "bay_nhảy.png", 4, 2),
    Sheet("fall", "ngã.png", 4, 2),
    Sheet("guard", "đỡ.png", 3, 2),
    Sheet("attack", "attack.png", 5, 2),
    Sheet("attack2", "attack_1.png", 5, 2),
)


# BODY slot -> (sheet key, zero-based cell index, observed pose, suggested group).
# The source art is full-body, so HEAD and LEG deliberately use a transparent image.
BODY_MAPPING = (
    ("guard", 2, "Giữ kiếm dọc trước mặt", "Đỡ / chịu đòn"),
    ("idle", 5, "Đứng nghỉ, thân và đầu nghiêng nhẹ sang phải", "Đứng yên"),
    ("run", 2, "Lướt cực nhanh kèm bóng mờ và vệt xanh", "Bay theo yêu cầu"),
    ("run", 0, "Hạ thấp trọng tâm, chống tay kèm bụi và gió", "Chạy có hiệu ứng 1"),
    ("run", 1, "Bật lao tới kèm bụi và vệt tốc độ", "Chạy có hiệu ứng 2"),
    ("run", 5, "Tiếp đất thấp, tóc quét theo vòng gió", "Chạy có hiệu ứng 3"),
    ("run", 6, "Đứng dậy sau pha lướt, bụi đá còn bay", "Chạy có hiệu ứng 4"),
    ("fly", 1, "Lao bay ngang sang phải, tóc và sương kéo về sau", "Bay thực tế BODY[7]"),
    ("landing_complete", 0, "Đáp đất giữa vòng nước đầy đủ, mép hiệu ứng được tách mềm", "Đáp đất BODY[8]"),
    ("attack2", 7, "Đâm kiếm thẳng giữa luồng sương", "Kỹ năng"),
    ("attack2", 4, "Lướt chém ngang có cung kiếm xanh", "Đánh / kỹ năng"),
    ("attack2", 5, "Xoay người chém vòng cung lớn", "Đánh / kỹ năng"),
    ("attack2", 6, "Bổ kiếm tạo cột nước và sương bùng nổ", "Đánh / kỹ năng"),
    ("run", 2, "Lướt cực nhanh kèm bóng mờ và vệt xanh", "Bay thay thế theo yêu cầu"),
    ("attack2", 2, "Chém thấp giữa vòng sương bao thân", "Đánh / kỹ năng"),
    ("guard", 3, "Chặn đòn kèm tia va chạm xanh", "Phòng thủ"),
    ("run", 2, "Lướt cực nhanh kèm bóng mờ và vệt xanh", "Motion blur / dash"),
)

PROFILE_ICON_ID = 26011
BODY_IMAGE_IDS = (
    25043,
    25044,
    25062,
    25046,
    25047,
    25048,
    25049,
    26003,
    26007,
    25052,
    25053,
    25054,
    25055,
    25062,
    25057,
    25058,
    25062,
)
TRANSPARENT_ID = 25060
ITEM_ICON_ID = 25061
ITEM_ICON_SOURCE = SOURCE_DIR / "avatar_item.png"
PROFILE_ICON_SOURCE = SOURCE_DIR / "Profile_Muichirou.png"

X1_CANVAS = (64, 56)
X1_ITEM_ICON_CANVAS = (25, 22)
X1_PROFILE_ICON_CANVAS = (64, 64)
SOURCE_TO_X1_SCALE = 0.11
SOURCE_PIVOT_BOTTOM_MARGIN = 15
DESTINATION_PIVOT = (32, 51)
BASE_BODY_OFFSET = (-23, -36)
CROP_PADDING_X1 = 1
PALETTE_COLORS = 256

BODY_SLOT_OFFSET_ADJUSTMENTS: dict[int, tuple[int, int]] = {
    # The idle BODY frame is full-body art; lower its feet to the in-game ground line.
    1: (0, 3),
}

# The NRO client mirrors BODY sprites when the character turns left. These source
# cells were authored facing left, so mirror them once at build time to establish
# the same canonical right-facing direction as the confirmed-good idle slot.
BODY_SLOT_HORIZONTAL_FLIPS = {2, 3, 4, 5, 6, 7, 13, 16}


def proportional_bounds(length: int, count: int) -> list[int]:
    return [round(index * length / count) for index in range(count + 1)]


def split_sheet(sheet: Sheet) -> list[Frame]:
    source_path = SOURCE_DIR / sheet.file_name
    if not source_path.is_file():
        raise FileNotFoundError(f"Missing source sheet: {source_path}")
    image = Image.open(source_path).convert("RGBA")
    x_bounds = proportional_bounds(image.width, sheet.columns)
    y_bounds = proportional_bounds(image.height, sheet.rows)
    frames: list[Frame] = []
    for row in range(sheet.rows):
        for column in range(sheet.columns):
            cell_left, cell_top = x_bounds[column], y_bounds[row]
            cell_right, cell_bottom = x_bounds[column + 1], y_bounds[row + 1]
            cell = image.crop((cell_left, cell_top, cell_right, cell_bottom))
            rgba = np.asarray(cell)
            alpha = rgba[:, :, 3]
            binary = np.where(alpha >= 16, 255, 0).astype(np.uint8)
            component_count, labels, stats, centroids = cv2.connectedComponentsWithStats(
                binary, connectivity=8
            )
            keep_ids: list[int] = []
            cell_width, cell_height = cell.size
            for component_id in range(1, component_count):
                left = int(stats[component_id, cv2.CC_STAT_LEFT])
                top = int(stats[component_id, cv2.CC_STAT_TOP])
                width = int(stats[component_id, cv2.CC_STAT_WIDTH])
                height = int(stats[component_id, cv2.CC_STAT_HEIGHT])
                area = int(stats[component_id, cv2.CC_STAT_AREA])
                center_x, center_y = centroids[component_id]
                touches_edge = (
                    left == 0
                    or top == 0
                    or left + width >= cell_width
                    or top + height >= cell_height
                )
                center_is_plausible = (
                    cell_width * 0.12 <= center_x <= cell_width * 0.88
                    and cell_height * 0.08 <= center_y <= cell_height * 0.94
                )
                if area >= 6 and (not touches_edge or center_is_plausible):
                    keep_ids.append(component_id)
            if not keep_ids:
                raise ValueError(f"No sprite components found for {sheet.key}[{len(frames)}]")
            hard_mask = np.isin(labels, keep_ids)
            # Restore the soft alpha fringe around kept components so curved
            # outlines stay continuous when each HD scale is rendered.
            expanded_mask = cv2.dilate(
                hard_mask.astype(np.uint8), np.ones((3, 3), np.uint8), iterations=1
            ).astype(bool)
            mask = expanded_mask & (alpha > 0)
            ys, xs = np.nonzero(mask)
            left, top = int(xs.min()), int(ys.min())
            right, bottom = int(xs.max()) + 1, int(ys.max()) + 1
            isolated = np.zeros((bottom - top, right - left, 4), dtype=np.uint8)
            source_crop = rgba[top:bottom, left:right]
            crop_mask = mask[top:bottom, left:right]
            isolated[crop_mask] = source_crop[crop_mask]

            frames.append(
                Frame(
                    Image.fromarray(isolated, mode="RGBA"),
                    (left, top, right, bottom),
                    (cell_width, cell_height),
                )
            )
    return frames


def alpha_bbox(image: Image.Image) -> tuple[int, int, int, int]:
    alpha = image.getchannel("A")
    # Ignore near-transparent antialias specks around AI-exported sprites.
    mask = alpha.point(lambda value: 255 if value >= 16 else 0)
    bbox = mask.getbbox()
    if bbox is None:
        raise ValueError("Sprite cell is transparent")
    return bbox


def load_complete_landing_frame() -> Frame:
    """Recover the full BODY[8] water ring from the packed attack sheet.

    The old proportional 5-column crop started at x~=1003 even though this
    frame's water effect starts at the transparent gutter around x=953. Its
    right edge overlaps the following source pose, so use the low-occupancy
    seam and taper only the shared fringe instead of leaving a hard rectangle.
    """
    source_path = SOURCE_DIR / "attack_1.png"
    if not source_path.is_file():
        raise FileNotFoundError(f"Missing landing source: {source_path}")
    source = Image.open(source_path).convert("RGBA")
    row_bottom = round(source.height / 2)
    left = round(source.width * 0.570)
    seam_start = round(source.width * 0.7835)
    right = round(source.width * 0.800)
    curated = np.asarray(source.crop((left, 0, right, row_bottom))).copy()

    local_seam = seam_start - left
    if not (0 < local_seam < curated.shape[1]):
        raise ValueError("Landing alpha seam falls outside the curated source crop")
    fade_width = curated.shape[1] - local_seam
    fade = np.linspace(1.0, 0.0, fade_width, endpoint=True, dtype=np.float32)
    curated[:, local_seam:, 3] = np.rint(
        curated[:, local_seam:, 3].astype(np.float32) * fade[None, :]
    ).astype(np.uint8)

    curated_image = Image.fromarray(curated, mode="RGBA")
    bbox = alpha_bbox(curated_image)
    return Frame(
        image=curated_image.crop(bbox),
        bbox_in_cell=bbox,
        cell_size=curated_image.size,
    )


def load_standalone_frame(source_path: Path, target_height_x1: int) -> Frame:
    if not source_path.is_file():
        raise FileNotFoundError(f"Missing standalone frame: {source_path}")
    image = Image.open(source_path).convert("RGBA")
    bbox = alpha_bbox(image)
    return Frame(
        image=image.crop(bbox),
        bbox_in_cell=bbox,
        cell_size=image.size,
        target_height_x1=target_height_x1,
    )


def mirror_frame(frame: Frame) -> Frame:
    left, top, right, bottom = frame.bbox_in_cell
    cell_width, cell_height = frame.cell_size
    return Frame(
        image=ImageOps.mirror(frame.image),
        bbox_in_cell=(cell_width - right, top, cell_width - left, bottom),
        cell_size=frame.cell_size,
        target_height_x1=frame.target_height_x1,
    )


def render_frame(frame: Frame, scale: int) -> Image.Image:
    crop = frame.image
    if frame.target_height_x1 is not None:
        target_height = frame.target_height_x1 * scale
        target_width = max(1, round(crop.width * target_height / crop.height))
        maximum_width = (X1_CANVAS[0] - 2) * scale
        if target_width > maximum_width:
            target_width = maximum_width
            target_height = max(1, round(crop.height * target_width / crop.width))
        crop = crop.resize((target_width, target_height), Image.Resampling.LANCZOS)
        destination_x = (X1_CANVAS[0] * scale - crop.width) // 2
        destination_y = DESTINATION_PIVOT[1] * scale - crop.height
    else:
        target_size = (
            max(1, round(crop.width * SOURCE_TO_X1_SCALE * scale)),
            max(1, round(crop.height * SOURCE_TO_X1_SCALE * scale)),
        )
        crop = crop.resize(target_size, Image.Resampling.LANCZOS)

        source_pivot_x = frame.cell_size[0] / 2
        source_pivot_y = frame.cell_size[1] - SOURCE_PIVOT_BOTTOM_MARGIN
        destination_x = round(
            (
                DESTINATION_PIVOT[0]
                + (frame.bbox_in_cell[0] - source_pivot_x) * SOURCE_TO_X1_SCALE
            )
            * scale
        )
        destination_y = round(
            (
                DESTINATION_PIVOT[1]
                + (frame.bbox_in_cell[1] - source_pivot_y) * SOURCE_TO_X1_SCALE
            )
            * scale
        )

    canvas = Image.new(
        "RGBA", (X1_CANVAS[0] * scale, X1_CANVAS[1] * scale), (0, 0, 0, 0)
    )
    canvas.alpha_composite(crop, (destination_x, destination_y))
    return canvas


def quantize_for_client(image: Image.Image) -> Image.Image:
    """Keep smooth per-scale rendering while making protocol payloads predictable."""
    return image.quantize(
        colors=PALETTE_COLORS,
        method=Image.Quantize.FASTOCTREE,
        dither=Image.Dither.NONE,
    )


def logical_crop_rect(rendered: list[Image.Image]) -> tuple[int, int, int, int]:
    bboxes = [alpha_bbox(image) for image in rendered]
    left = min(bbox[0] // scale for scale, bbox in enumerate(bboxes, start=1))
    top = min(bbox[1] // scale for scale, bbox in enumerate(bboxes, start=1))
    right = max(
        (bbox[2] + scale - 1) // scale
        for scale, bbox in enumerate(bboxes, start=1)
    )
    bottom = max(
        (bbox[3] + scale - 1) // scale
        for scale, bbox in enumerate(bboxes, start=1)
    )
    return (
        max(0, left - CROP_PADDING_X1),
        max(0, top - CROP_PADDING_X1),
        min(X1_CANVAS[0], right + CROP_PADDING_X1),
        min(X1_CANVAS[1], bottom + CROP_PADDING_X1),
    )


def write_frame_scales(
    image_id: int,
    frame: Frame,
    offset_adjustment: tuple[int, int] = (0, 0),
    write_output: bool = True,
) -> tuple[dict, Image.Image]:
    rendered = [render_frame(frame, scale) for scale in range(1, 5)]
    crop_rect = logical_crop_rect(rendered)
    scale_metadata: dict[str, dict[str, int | list[int]]] = {}
    preview_x4 = Image.new(
        "RGBA", (X1_CANVAS[0] * 4, X1_CANVAS[1] * 4), (0, 0, 0, 0)
    )
    for scale, fixed_canvas in enumerate(rendered, start=1):
        output_dir = PROJECT_ROOT / "data" / "icon" / f"x{scale}"
        output_dir.mkdir(parents=True, exist_ok=True)
        output_path = output_dir / f"{image_id}.png"
        scaled_rect = tuple(value * scale for value in crop_rect)
        cropped = fixed_canvas.crop(scaled_rect)
        encoded = quantize_for_client(cropped)
        if write_output:
            encoded.save(output_path, optimize=True)
        elif not output_path.is_file():
            raise FileNotFoundError(f"Missing existing output for unselected slot: {output_path}")
        scale_metadata[f"x{scale}"] = {
            "size": [encoded.width, encoded.height],
            "bytes": output_path.stat().st_size,
        }
        if scale == 4:
            preview_x4.alpha_composite(
                encoded.convert("RGBA"), (crop_rect[0] * 4, crop_rect[1] * 4)
            )
    metadata = {
        "dx": BASE_BODY_OFFSET[0] + crop_rect[0] + offset_adjustment[0],
        "dy": BASE_BODY_OFFSET[1] + crop_rect[1] + offset_adjustment[1],
        "offsetAdjustment": list(offset_adjustment),
        "cropX1": list(crop_rect),
        "scales": scale_metadata,
    }
    return metadata, preview_x4


def render_icon_source(
    source_path: Path,
    scale: int,
    canvas_x1: tuple[int, int],
    align_bottom: bool = False,
) -> Image.Image:
    source = Image.open(source_path).convert("RGBA")
    bbox = alpha_bbox(source)
    source = source.crop(bbox)
    canvas_size = (canvas_x1[0] * scale, canvas_x1[1] * scale)
    canvas = Image.new("RGBA", canvas_size, (0, 0, 0, 0))
    source.thumbnail(canvas_size, Image.Resampling.LANCZOS)
    x = (canvas.width - source.width) // 2
    y = canvas.height - source.height if align_bottom else (canvas.height - source.height) // 2
    canvas.alpha_composite(source, (x, y))
    return canvas


def write_profile_icon_scales() -> None:
    if not PROFILE_ICON_SOURCE.is_file():
        raise FileNotFoundError(f"Missing profile icon source: {PROFILE_ICON_SOURCE}")
    for scale in range(1, 5):
        output_dir = PROJECT_ROOT / "data" / "icon" / f"x{scale}"
        output_dir.mkdir(parents=True, exist_ok=True)
        quantize_for_client(
            render_icon_source(
                PROFILE_ICON_SOURCE,
                scale,
                X1_PROFILE_ICON_CANVAS,
                align_bottom=True,
            )
        ).save(
            output_dir / f"{PROFILE_ICON_ID}.png", optimize=True
        )


def write_item_icon_scales() -> None:
    if not ITEM_ICON_SOURCE.is_file():
        raise FileNotFoundError(f"Missing item icon source: {ITEM_ICON_SOURCE}")
    for scale in range(1, 5):
        output_dir = PROJECT_ROOT / "data" / "icon" / f"x{scale}"
        output_dir.mkdir(parents=True, exist_ok=True)
        quantize_for_client(
            render_icon_source(ITEM_ICON_SOURCE, scale, X1_ITEM_ICON_CANVAS)
        ).save(
            output_dir / f"{ITEM_ICON_ID}.png", optimize=True
        )


def write_transparent_scales() -> None:
    for scale in range(1, 5):
        output_dir = PROJECT_ROOT / "data" / "icon" / f"x{scale}"
        output_dir.mkdir(parents=True, exist_ok=True)
        Image.new("RGBA", (scale, scale), (0, 0, 0, 0)).save(
            output_dir / f"{TRANSPARENT_ID}.png", optimize=True
        )


def build_contact_sheets(all_cells: dict[str, list[Frame]]) -> None:
    output_dir = PROJECT_DIR / "preview"
    output_dir.mkdir(parents=True, exist_ok=True)
    font = ImageFont.load_default()
    for sheet in SHEETS:
        cells = all_cells[sheet.key]
        thumb_size = (210, 150)
        label_height = 22
        contact = Image.new(
            "RGBA",
            (sheet.columns * thumb_size[0], sheet.rows * (thumb_size[1] + label_height)),
            (27, 32, 41, 255),
        )
        draw = ImageDraw.Draw(contact)
        for index, frame in enumerate(cells):
            sprite = frame.image.copy()
            sprite.thumbnail((thumb_size[0] - 8, thumb_size[1] - 8), Image.Resampling.LANCZOS)
            column = index % sheet.columns
            row = index // sheet.columns
            origin_x = column * thumb_size[0]
            origin_y = row * (thumb_size[1] + label_height)
            x = origin_x + (thumb_size[0] - sprite.width) // 2
            y = origin_y + (thumb_size[1] - sprite.height) // 2
            contact.alpha_composite(sprite, (x, y))
            draw.rectangle(
                (origin_x, origin_y, origin_x + thumb_size[0] - 1, origin_y + thumb_size[1] - 1),
                outline=(86, 101, 120, 255),
            )
            draw.text(
                (origin_x + 6, origin_y + thumb_size[1] + 4),
                f"{sheet.key}[{index}]",
                fill=(238, 242, 247, 255),
                font=font,
            )
        contact.save(output_dir / f"contact-{sheet.key}.png", optimize=True)


def build_selected_preview(selected_frames_x4: list[Image.Image]) -> None:
    tile_size = (X1_CANVAS[0] * 4, X1_CANVAS[1] * 4)
    columns = 5
    rows = 4
    label_height = 28
    preview = Image.new(
        "RGBA",
        (columns * tile_size[0], rows * (tile_size[1] + label_height)),
        (225, 239, 249, 255),
    )
    draw = ImageDraw.Draw(preview)
    font = ImageFont.load_default()
    for index, image_x4 in enumerate(selected_frames_x4):
        row, column = divmod(index, columns)
        x = column * tile_size[0]
        y = row * (tile_size[1] + label_height)
        preview.alpha_composite(image_x4, (x, y))
        draw.rectangle((x, y, x + tile_size[0] - 1, y + tile_size[1] - 1), outline=(91, 117, 140, 255))
        draw.text((x + 6, y + tile_size[1] + 6), f"BODY[{index}] / {BODY_IMAGE_IDS[index]}", fill=(20, 38, 54, 255), font=font)
    preview.save(PROJECT_DIR / "preview" / "selected-body-frames.png", optimize=True)


def build_manifest(frame_exports: list[dict]) -> None:
    manifest = {
        "itemId": 2063,
        "name": "Cải trang Tokitou Muichirou",
        "iconId": ITEM_ICON_ID,
        "profileIconId": PROFILE_ICON_ID,
        "headAvatar": {"headId": 2138, "avatarId": PROFILE_ICON_ID},
        "partIds": {"head": 2138, "body": 2139, "leg": 2140},
        "transparentImageId": TRANSPARENT_ID,
        "scaleMode": "direct-source-lanczos-trimmed-indexed-alpha",
        "paletteColors": PALETTE_COLORS,
        "itemIconCanvasX1": list(X1_ITEM_ICON_CANVAS),
        "profileIconCanvasX1": list(X1_PROFILE_ICON_CANVAS),
        "profileVerticalAlign": "bottom",
        "bodyReferenceCanvasX1": list(X1_CANVAS),
        "bodyOffsetMode": "per-frame crop-compensated",
        "mirroredHorizontally": sorted(BODY_SLOT_HORIZONTAL_FLIPS),
        "head": [[TRANSPARENT_ID, 0, 0] for _ in range(3)],
        "body": [
            {
                "slot": index,
                "imageId": BODY_IMAGE_IDS[index],
                "dx": frame_exports[index]["dx"],
                "dy": frame_exports[index]["dy"],
                "cropX1": frame_exports[index]["cropX1"],
                "scales": frame_exports[index]["scales"],
                "sheet": row[0],
                "sourceIndex": row[1],
                "observed": row[2],
                "suggestedGroup": row[3],
                "mirroredHorizontally": index in BODY_SLOT_HORIZONTAL_FLIPS,
            }
            for index, row in enumerate(BODY_MAPPING)
        ],
        "leg": [[TRANSPARENT_ID, 0, 0] for _ in range(14)],
        "defaultOptions": [],
        "shop": {"shopId": 112, "tabId": 1120, "npc": "Kanao", "tab": "Cải trang"},
    }
    (PROJECT_DIR / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--contacts-only", action="store_true")
    parser.add_argument(
        "--slots",
        nargs="*",
        type=int,
        help="Only rewrite the listed BODY slot image files; still refresh preview/manifest.",
    )
    parser.add_argument(
        "--profile",
        action="store_true",
        help="Rewrite the bottom-aligned profile image during a partial BODY build.",
    )
    args = parser.parse_args()

    selected_slots = None if args.slots is None else set(args.slots)
    if selected_slots is not None and not selected_slots.issubset(range(17)):
        raise ValueError(f"BODY slots must be within 0..16: {sorted(selected_slots)}")

    all_cells = {sheet.key: split_sheet(sheet) for sheet in SHEETS}
    all_cells["landing_complete"] = [load_complete_landing_frame()]
    build_contact_sheets(all_cells)
    if args.contacts_only:
        return

    selected_frames_x4: list[Image.Image] = []
    frame_exports: list[dict] = []
    for slot, (image_id, mapping) in enumerate(
        zip(BODY_IMAGE_IDS, BODY_MAPPING, strict=True)
    ):
        sheet_key, source_index, _, _ = mapping
        frame = all_cells[sheet_key][source_index]
        if slot in BODY_SLOT_HORIZONTAL_FLIPS:
            frame = mirror_frame(frame)
        export, preview_x4 = write_frame_scales(
            image_id,
            frame,
            BODY_SLOT_OFFSET_ADJUSTMENTS.get(slot, (0, 0)),
            write_output=selected_slots is None or slot in selected_slots,
        )
        frame_exports.append(export)
        selected_frames_x4.append(preview_x4)

    if selected_slots is None:
        write_transparent_scales()
    if selected_slots is None or args.profile:
        write_profile_icon_scales()
    if selected_slots is None:
        write_item_icon_scales()
    build_selected_preview(selected_frames_x4)
    build_manifest(frame_exports)


if __name__ == "__main__":
    main()
