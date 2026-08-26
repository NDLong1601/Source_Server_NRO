from __future__ import annotations

import argparse
import json
import shutil
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

from PIL import Image, ImageDraw, ImageFont


PROJECT_ROOT = Path(__file__).resolve().parents[1]
ICON_ROOT = PROJECT_ROOT / "data" / "icon"
PROJECTS_ROOT = PROJECT_ROOT / "costume-tools" / "projects"
BATCH_ROOT = PROJECTS_ROOT / "marvel-costumes-20260825"
PLACEHOLDER_IMAGE_ID = 2955
SHOP_ID = 112
TAB_ID = 1120
SHOP_COST = 2000


@dataclass(frozen=True)
class CostumeSpec:
    slug: str
    item_id: int
    part_ids: tuple[int, int, int]
    name: str
    description: str
    source_dir: Path
    profile_file: str
    head: tuple[tuple[int, int, int], ...]
    body: tuple[tuple[int, int, int], ...]
    leg: tuple[tuple[int, int, int], ...]
    image_start_id: int
    offset_source: str
    needs_client_offset_check: bool = False
    default_options: tuple[tuple[int, int], ...] = ()


def seq(start: int, end: int) -> list[int]:
    return list(range(start, end + 1))


BLACK_PANTHER_HEAD_OFFSETS = ((-4, -15), (-2, -13))
BLACK_PANTHER_BODY_OFFSETS = (
    (2, -7), (-3, -12), (-1, -10), (6, -10), (4, -10), (6, -9), (5, -10), (-3, -16),
    (-9, -11), (-3, -12), (3, -9), (0, -8), (1, -9), (0, -10), (1, -9), (2, -10),
)
BLACK_PANTHER_LEG_OFFSETS = (
    (-1, -7), (-2, -9), (-1, -8), (7, -6), (-3, -8), (3, -6), (1, -7),
    (0, -5), (-10, -7), (-2, -9), (-4, -5), (-7, -6), (-4, -4), (-4, -4),
)

OPTIONS_BLACK_PARTEN = ((50, 28), (77, 28), (103, 28), (14, 20), (97, 20), (30, 0))
OPTIONS_CAPTAIN = ((50, 27), (77, 28), (103, 22), (5, 20), (94, 15), (30, 0))
OPTIONS_SPIDER = ((50, 27), (77, 27), (103, 27), (14, 20), (108, 20), (30, 0))
OPTIONS_HULK = ((50, 30), (77, 30), (103, 30), (5, 30), (10, 30), (80, 50), (30, 0))
OPTIONS_IRON_MAN = ((50, 30), (77, 28), (103, 28), (5, 25), (10, 25), (18, 100), (30, 0))
OPTIONS_GOJO = ((50, 30), (77, 30), (103, 30), (5, 30), (10, 30), (108, 20), (30, 0))


def with_offsets(ids: Iterable[int], offsets: Iterable[tuple[int, int]]) -> tuple[tuple[int, int, int], ...]:
    return tuple((image_id, dx, dy) for image_id, (dx, dy) in zip(ids, offsets))


def idle_slot_first(frames: tuple[tuple[int, int, int], ...]) -> tuple[tuple[int, int, int], ...]:
    if len(frames) < 2:
        return frames
    return (frames[1], frames[0], *frames[2:])


def shift_y(frames: tuple[tuple[int, int, int], ...], amount: int) -> tuple[tuple[int, int, int], ...]:
    shifted: list[tuple[int, int, int]] = []
    for image_id, dx, dy in frames:
        if image_id == PLACEHOLDER_IMAGE_ID:
            shifted.append((image_id, dx, dy))
        else:
            shifted.append((image_id, dx, dy + amount))
    return tuple(shifted)


SPECS: tuple[CostumeSpec, ...] = (
    CostumeSpec(
        slug="black-parten",
        item_id=2077,
        part_ids=(2180, 2181, 2182),
        name="Cải trang Black Parten",
        description="Cải trang thành Black Parten",
        source_dir=Path(r"C:\Users\PC\Downloads\Ảnh chưa làm\Cải trang\Black Parten"),
        profile_file="profile.png",
        head=shift_y(with_offsets([18029, 18030], BLACK_PANTHER_HEAD_OFFSETS), 6) + ((PLACEHOLDER_IMAGE_ID, 0, 0),),
        body=shift_y(idle_slot_first(with_offsets(seq(18031, 18046), BLACK_PANTHER_BODY_OFFSETS)), 6) + ((PLACEHOLDER_IMAGE_ID, 0, 0),),
        leg=shift_y(idle_slot_first(with_offsets(seq(18047, 18060), BLACK_PANTHER_LEG_OFFSETS)), 6),
        image_start_id=26175,
        offset_source="estimated from AWN Black Panther part 1667/1668/1669; source IDs 18029..18060 did not match the found nr_part.json",
        needs_client_offset_check=True,
        default_options=OPTIONS_BLACK_PARTEN,
    ),
    CostumeSpec(
        slug="captain-america",
        item_id=2078,
        part_ids=(2183, 2184, 2185),
        name="Cải trang Captain America",
        description="Cải trang thành Captain America",
        source_dir=Path(r"C:\Users\PC\Downloads\Ảnh chưa làm\Cải trang\captain"),
        profile_file="profile.png",
        head=((31035, 1, -15), (31036, -1, -14), (PLACEHOLDER_IMAGE_ID, 0, 0)),
        body=(
            (31037, -5, -10), (31038, -9, -21), (31039, -4, -12), (31040, -8, -15),
            (31041, -9, -14), (31042, -5, -12), (31043, -5, -15), (31044, -8, -11),
            (31045, -2, -13), (31046, -2, -13), (31047, -2, -12), (31048, -7, -13),
            (31049, -4, -12), (31050, -5, -10), (31051, -1, -13), (31052, -7, -11),
            (PLACEHOLDER_IMAGE_ID, 0, 0),
        ),
        leg=(
            (31053, 4, -3), (31054, -3, -5), (31055, 2, 0), (31056, 1, -2),
            (31057, -1, -1), (31058, -1, 1), (31059, 2, -1), (31060, -1, -1),
            (31061, -1, -3), (31062, -2, -3), (31063, -4, -2), (31064, -3, 1),
            (31065, -3, -3), (PLACEHOLDER_IMAGE_ID, 0, 0),
        ),
        image_start_id=26208,
        offset_source=r"AWN_Version\part\part new.sql parts 1655/1656/1657",
        default_options=OPTIONS_CAPTAIN,
    ),
    CostumeSpec(
        slug="spider-man",
        item_id=2079,
        part_ids=(2186, 2187, 2188),
        name="Cải trang Spider Man",
        description="Cải trang thành Spider Man",
        source_dir=Path(r"C:\Users\PC\Downloads\Ảnh chưa làm\Cải trang\spider man"),
        profile_file="profile.png",
        head=((31067, -2, -14), (31068, 1, -11), (PLACEHOLDER_IMAGE_ID, 0, 0)),
        body=(
            (31069, -6, -14), (31070, -12, -17), (31071, -6, -11), (31072, -4, -12),
            (31073, -7, -10), (31074, -1, -12), (31075, -4, -12), (31076, -12, -11),
            (31077, -7, -12), (31078, -5, -13), (31079, -1, -10), (31080, -7, -13),
            (31081, -3, -8), (31082, -3, -7), (31083, -4, -8), (31084, -6, -8),
            (PLACEHOLDER_IMAGE_ID, 0, 0),
        ),
        leg=(
            (31085, -3, -6), (31086, -7, -4), (31087, 1, -1), (31088, 1, -1),
            (31089, 0, 0), (31090, -2, 0), (31091, 2, -1), (31092, -4, -3),
            (31093, -6, -3), (31094, -1, -3), (31095, -5, -4), (31096, -5, -1),
            (31097, -6, -3), (PLACEHOLDER_IMAGE_ID, 0, 0),
        ),
        image_start_id=26240,
        offset_source=r"AWN_Version\part\part new.sql parts 1658/1659/1660",
        default_options=OPTIONS_SPIDER,
    ),
    CostumeSpec(
        slug="hulk",
        item_id=2080,
        part_ids=(2189, 2190, 2191),
        name="Cải trang Hulk",
        description="Cải trang thành Hulk",
        source_dir=Path(r"C:\Users\PC\Downloads\Ảnh chưa làm\Cải trang\hulk"),
        profile_file="profile.png",
        head=((31099, -2, -12), (31100, -1, -14), (PLACEHOLDER_IMAGE_ID, 0, 0)),
        body=(
            (31101, -10, -7), (31102, -12, -16), (31103, -12, -16), (31104, -5, -12),
            (31105, -8, -16), (31106, -4, -14), (31107, -6, -13), (31108, -9, -10),
            (31109, -7, -12), (31110, -6, -12), (31111, -8, -12), (31112, -9, -14),
            (31113, -2, -15), (31114, -2, -12), (31115, -6, -9), (31116, -10, -11),
            (PLACEHOLDER_IMAGE_ID, 0, 0),
        ),
        leg=(
            (31117, -1, -4), (31118, -6, -4), (31119, 0, -3), (31120, 1, -1),
            (31121, -1, -4), (31122, -1, -2), (31123, 1, -1), (31124, -3, 0),
            (31125, -4, -1), (31126, -2, -4), (31127, -5, -2), (31128, -5, 0),
            (31129, -6, -3), (PLACEHOLDER_IMAGE_ID, 0, 0),
        ),
        image_start_id=26272,
        offset_source=r"AWN_Version\part\part new.sql parts 1661/1662/1663",
        default_options=OPTIONS_HULK,
    ),
    CostumeSpec(
        slug="iron-man",
        item_id=2081,
        part_ids=(2192, 2193, 2194),
        name="Cải trang Iron Man",
        description="Cải trang thành Iron Man",
        source_dir=Path(r"C:\Users\PC\Downloads\Ảnh chưa làm\Cải trang\Iron man"),
        profile_file="profile.png",
        head=shift_y(with_offsets([18079, 18080], BLACK_PANTHER_HEAD_OFFSETS), 6) + ((PLACEHOLDER_IMAGE_ID, 0, 0),),
        body=shift_y(idle_slot_first(with_offsets(seq(18081, 18096), BLACK_PANTHER_BODY_OFFSETS)), 6) + ((PLACEHOLDER_IMAGE_ID, 0, 0),),
        leg=shift_y(idle_slot_first(with_offsets(seq(18097, 18110), BLACK_PANTHER_LEG_OFFSETS)), 6),
        image_start_id=26304,
        offset_source="estimated from AWN Black Panther part 1667/1668/1669; no matching Iron Man DATA was found locally",
        needs_client_offset_check=True,
        default_options=OPTIONS_IRON_MAN,
    ),
    CostumeSpec(
        slug="gojo",
        item_id=2082,
        part_ids=(2195, 2196, 2197),
        name="Cải trang Gojo",
        description="Cải trang thành Gojo",
        source_dir=Path(r"C:\Users\PC\Downloads\Ảnh chưa làm\Cải trang\Cải trang mới 1"),
        profile_file="profile.png",
        head=((31483, 4, -5), (31484, 5, -3), (PLACEHOLDER_IMAGE_ID, 0, 0)),
        body=(
            (31485, 4, 1), (31486, 0, -4), (31487, 6, -2), (31488, 4, -1),
            (31489, 7, 0), (31490, 10, 1), (31491, 8, 0), (31492, 1, -2),
            (31493, 1, -1), (31494, 0, -2), (31495, 6, 0), (31496, 3, 0),
            (31497, 3, 1), (31498, 5, 0), (31499, 3, -2), (31500, 6, -2),
            (PLACEHOLDER_IMAGE_ID, 0, 0),
        ),
        leg=(
            (31501, 4, -4), (31502, 1, -2), (31503, 8, 1), (31504, 5, 1),
            (31505, 4, 2), (31506, 6, 2), (31507, 7, 1), (31508, 3, 0),
            (31509, 1, 1), (31510, 4, -1), (31511, -1, 3), (31512, -2, 4),
            (31513, -3, 2), (PLACEHOLDER_IMAGE_ID, 0, 0),
        ),
        image_start_id=26337,
        offset_source=r"AWN_Version\part\part new.sql parts 1697/1698/1699",
        default_options=OPTIONS_GOJO,
    ),
)


def escape_sql(text: str) -> str:
    return text.replace("\\", "\\\\").replace("'", "''")


def source_image_ids(spec: CostumeSpec) -> list[int]:
    ids: list[int] = []
    for dataset in (spec.head, spec.body, spec.leg):
        for image_id, _, _ in dataset:
            if image_id > 0 and image_id != PLACEHOLDER_IMAGE_ID and image_id not in ids:
                ids.append(image_id)
    return sorted(ids)


def make_id_map(spec: CostumeSpec) -> dict[int, int]:
    ids = source_image_ids(spec)
    return {source_id: spec.image_start_id + index for index, source_id in enumerate(ids)}


def profile_image_id(spec: CostumeSpec) -> int:
    return spec.image_start_id + len(source_image_ids(spec))


def remap_part(data: tuple[tuple[int, int, int], ...], mapping: dict[int, int]) -> list[list[int]]:
    result: list[list[int]] = []
    for image_id, dx, dy in data:
        result.append([mapping.get(image_id, image_id), dx, dy])
    return result


def options_manifest(spec: CostumeSpec) -> list[dict[str, int]]:
    return [
        {"optionId": option_id, "param": param, "sortOrder": sort_order}
        for sort_order, (option_id, param) in enumerate(spec.default_options)
    ]


def alpha_bbox(image: Image.Image) -> tuple[int, int, int, int] | None:
    return image.convert("RGBA").getchannel("A").getbbox()


def edge_touches(image: Image.Image) -> list[str]:
    bbox = alpha_bbox(image)
    if bbox is None:
        return []
    left, top, right, bottom = bbox
    touches: list[str] = []
    if left == 0:
        touches.append("left")
    if top == 0:
        touches.append("top")
    if right == image.width:
        touches.append("right")
    if bottom == image.height:
        touches.append("bottom")
    return touches


def frame_x1_size(image: Image.Image) -> tuple[int, int]:
    return max(1, round(image.width / 4)), max(1, round(image.height / 4))


def save_scaled_frame(source_path: Path, image_id: int, force: bool) -> dict[str, object]:
    image = Image.open(source_path).convert("RGBA")
    x1_width, x1_height = frame_x1_size(image)
    result: dict[str, object] = {
        "source": str(source_path),
        "sourceSize": [image.width, image.height],
        "sourceBbox": list(alpha_bbox(image) or []),
        "sourceEdgeTouches": edge_touches(image),
        "x1Size": [x1_width, x1_height],
        "scales": {},
    }
    for scale in (1, 2, 3, 4):
        target_path = ICON_ROOT / f"x{scale}" / f"{image_id}.png"
        if target_path.exists() and not force:
            raise FileExistsError(f"Refusing to overwrite existing runtime asset: {target_path}")
        target_path.parent.mkdir(parents=True, exist_ok=True)
        target_size = (x1_width * scale, x1_height * scale)
        resized = image.resize(target_size, Image.Resampling.LANCZOS)
        resized.save(target_path, optimize=True)
        result["scales"][f"x{scale}"] = {
            "path": str(target_path),
            "size": [resized.width, resized.height],
            "bytes": target_path.stat().st_size,
            "edgeTouches": edge_touches(resized),
            "transparentBorder": len(edge_touches(resized)) == 0,
        }
    return result


def save_profile(source_path: Path, image_id: int, force: bool) -> dict[str, object]:
    image = Image.open(source_path).convert("RGBA")
    bbox = alpha_bbox(image)
    if bbox is None:
        raise ValueError(f"Profile image is fully transparent: {source_path}")
    crop = image.crop(bbox)
    result: dict[str, object] = {
        "source": str(source_path),
        "sourceSize": [image.width, image.height],
        "sourceBbox": list(bbox),
        "canvasX1": [64, 64],
        "verticalAlign": "bottom",
        "scales": {},
    }
    for scale in (1, 2, 3, 4):
        target_path = ICON_ROOT / f"x{scale}" / f"{image_id}.png"
        if target_path.exists() and not force:
            raise FileExistsError(f"Refusing to overwrite existing runtime asset: {target_path}")
        target_path.parent.mkdir(parents=True, exist_ok=True)
        canvas_size = 64 * scale
        max_size = canvas_size - 2 * scale
        ratio = min(max_size / crop.width, max_size / crop.height)
        resized = crop.resize((max(1, round(crop.width * ratio)), max(1, round(crop.height * ratio))), Image.Resampling.LANCZOS)
        canvas = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
        x = (canvas_size - resized.width) // 2
        y = canvas_size - resized.height
        canvas.alpha_composite(resized, (x, y))
        canvas.save(target_path, optimize=True)
        result["scales"][f"x{scale}"] = {
            "path": str(target_path),
            "size": [canvas.width, canvas.height],
            "bytes": target_path.stat().st_size,
            "bbox": list(alpha_bbox(canvas) or []),
            "transparentBorder": len(edge_touches(canvas)) == 0,
        }
    return result


def role_label(role: str, slot: int, total: int, placeholder: bool) -> tuple[str, str]:
    if placeholder:
        return "Ảnh trong suốt", "placeholder/motion"
    if role == "HEAD":
        return ("Đầu chính, dùng cho icon hành trang" if slot == 0 else "Đầu nghiêng/động"), (
            "đứng/icon" if slot == 0 else "chạy/bay"
        )
    if role == "BODY":
        groups = {
            0: "đỡ/chịu đòn",
            1: "đứng hoặc chuyển động chính",
            2: "bay/ngã",
            3: "chạy/tiếp cận",
            4: "chạy/tiếp cận",
            5: "chạy/tiếp cận",
            6: "chạy/tiếp cận",
            7: "bay/đòn phụ",
            8: "tụ lực/kỹ năng",
            9: "kỹ năng",
            10: "đánh",
            11: "đánh",
            12: "đánh",
            13: "ngã/đánh văng",
            14: "đánh/kỹ năng lớn",
            15: "phòng thủ",
            16: "motion/placeholder",
        }
        return "Thân/tay theo thứ tự DATA nguồn", groups.get(slot, "hành động")
    groups = {
        0: "bay/ngã",
        1: "đứng",
        2: "chạy",
        3: "chạy",
        4: "chạy",
        5: "chạy",
        6: "chạy",
        7: "nhảy/đáp",
        8: "nhảy/đáp",
        9: "đánh/kỹ năng",
        10: "đánh/kỹ năng",
        11: "đánh/kỹ năng",
        12: "đánh/kỹ năng",
        13: "motion/placeholder",
    }
    return "Chân theo thứ tự DATA nguồn", groups.get(slot, "hành động")


def slot_manifest(role: str, data: tuple[tuple[int, int, int], ...], mapping: dict[int, int]) -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    for slot, (source_id, dx, dy) in enumerate(data):
        placeholder = source_id == PLACEHOLDER_IMAGE_ID or source_id <= 0
        observed, group = role_label(role, slot, len(data), placeholder)
        rows.append(
            {
                "slot": slot,
                "sourceImageId": source_id,
                "imageId": mapping.get(source_id, source_id),
                "dx": dx,
                "dy": dy,
                "observed": observed,
                "suggestedGroup": group,
                "placeholder": placeholder,
                "sourceFacing": "right/unknown from static asset",
                "mirrored": False,
            }
        )
    return rows


def make_contact_sheet(spec: CostumeSpec, project_dir: Path, mapping: dict[int, int]) -> Path:
    files = [spec.source_dir / f"{source_id}.png" for source_id in source_image_ids(spec)]
    files.append(spec.source_dir / spec.profile_file)
    cols = 8
    cell_w, cell_h, label_h, title_h = 184, 164, 30, 34
    rows = (len(files) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell_w, title_h + rows * (cell_h + label_h)), (246, 246, 246, 255))
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default()
    draw.text((8, 10), spec.name, fill=(0, 0, 0, 255), font=font)
    for index, path in enumerate(files):
        row, col = divmod(index, cols)
        x0 = col * cell_w
        y0 = title_h + row * (cell_h + label_h)
        for y in range(y0, y0 + cell_h, 8):
            for x in range(x0, x0 + cell_w, 8):
                color = (222, 222, 222, 255) if ((x // 8 + y // 8) & 1) else (255, 255, 255, 255)
                draw.rectangle((x, y, min(x + 7, x0 + cell_w - 1), min(y + 7, y0 + cell_h - 1)), fill=color)
        image = Image.open(path).convert("RGBA")
        ratio = min((cell_w - 16) / image.width, (cell_h - 16) / image.height, 1.0)
        display = image.resize((max(1, round(image.width * ratio)), max(1, round(image.height * ratio))), Image.Resampling.LANCZOS)
        sheet.alpha_composite(display, (x0 + (cell_w - display.width) // 2, y0 + (cell_h - display.height) // 2))
        draw.rectangle((x0, y0, x0 + cell_w - 1, y0 + cell_h - 1), outline=(120, 120, 120, 255))
        if path.name == spec.profile_file:
            label = f"profile -> {profile_image_id(spec)}"
        else:
            source_id = int(path.stem)
            label = f"{source_id} -> {mapping[source_id]}"
        draw.text((x0 + 4, y0 + cell_h + 2), label, fill=(0, 0, 0, 255), font=font)
        draw.text((x0 + 4, y0 + cell_h + 14), f"{image.width}x{image.height}", fill=(0, 0, 0, 255), font=font)
    output = project_dir / "preview" / "contact-sheet.png"
    output.parent.mkdir(parents=True, exist_ok=True)
    sheet.convert("RGB").save(output)
    return output


def create_sql_for_specs(specs: Iterable[CostumeSpec], install_path: Path, rollback_path: Path) -> None:
    specs = tuple(specs)
    part_values: list[str] = []
    item_values: list[str] = []
    avatar_values: list[str] = []
    option_values: list[str] = []
    shop_inserts: list[str] = []
    item_ids = [spec.item_id for spec in specs]
    part_ids: list[int] = []
    for spec in specs:
        mapping = make_id_map(spec)
        head_data = json.dumps(remap_part(spec.head, mapping), separators=(",", ":"))
        body_data = json.dumps(remap_part(spec.body, mapping), separators=(",", ":"))
        leg_data = json.dumps(remap_part(spec.leg, mapping), separators=(",", ":"))
        head_id, body_id, leg_id = spec.part_ids
        part_ids.extend(spec.part_ids)
        part_values.extend(
            [
                f"({head_id}, 0, '{head_data}')",
                f"({body_id}, 1, '{body_data}')",
                f"({leg_id}, 2, '{leg_data}')",
            ]
        )
        avatar_values.append(f"({head_id}, {profile_image_id(spec)})")
        item_values.append(
            "("
            f"{spec.item_id}, 5, 3, '{escape_sql(spec.name)}', '{escape_sql(spec.description)}', "
            f"1, {make_id_map(spec)[source_image_ids(spec)[0]]}, -1, 0, 0, 0, 0, {head_id}, {body_id}, {leg_id}"
            ")"
        )
        option_values.extend(
            f"({spec.item_id}, {option_id}, {param}, {sort_order})"
            for sort_order, (option_id, param) in enumerate(spec.default_options)
        )
        shop_inserts.append(
            "INSERT INTO item_shop\n"
            "(tab_id, temp_id, is_new, is_sell, type_sell, cost, icon_spec, option_mode, create_time)\n"
            f"SELECT {TAB_ID}, {spec.item_id}, 1, 1, 1, {SHOP_COST}, 0, 0, NOW()\n"
            f"WHERE EXISTS (SELECT 1 FROM tab_shop WHERE id = {TAB_ID} AND shop_id = {SHOP_ID})\n"
            f"  AND NOT EXISTS (SELECT 1 FROM item_shop WHERE tab_id = {TAB_ID} AND temp_id = {spec.item_id});"
        )

    item_id_csv = ", ".join(str(i) for i in item_ids)
    part_id_csv = ", ".join(str(i) for i in part_ids)
    options_sql = ""
    if option_values:
        options_sql = (
            "INSERT INTO item_default_option (item_template_id, option_id, param, sort_order) VALUES\n"
            + ",\n".join(option_values)
            + ";\n\n"
        )
    install = (
        "-- Install Marvel/hero costumes generated by costume-tools/build_marvel_costumes.py\n"
        "SET NAMES utf8mb4;\n"
        "START TRANSACTION;\n\n"
        "DELETE FROM item_shop_option WHERE item_shop_id IN "
        f"(SELECT id FROM item_shop WHERE tab_id = {TAB_ID} AND temp_id IN ({item_id_csv}));\n"
        f"DELETE FROM item_shop WHERE tab_id = {TAB_ID} AND temp_id IN ({item_id_csv});\n"
        f"DELETE FROM item_default_option WHERE item_template_id IN ({item_id_csv});\n"
        f"DELETE FROM head_avatar WHERE head_id IN ({part_id_csv});\n"
        f"DELETE FROM item_template WHERE id IN ({item_id_csv});\n"
        f"DELETE FROM part WHERE id IN ({part_id_csv});\n\n"
        "INSERT INTO part (id, `TYPE`, `DATA`) VALUES\n"
        + ",\n".join(part_values)
        + ";\n\n"
        "INSERT INTO head_avatar (head_id, avatar_id) VALUES\n"
        + ",\n".join(avatar_values)
        + ";\n\n"
        "INSERT INTO item_template\n"
        "(id, `TYPE`, gender, `NAME`, description, level, icon_id, part, is_up_to_up, power_require, gold, gem, head, body, leg)\n"
        "VALUES\n"
        + ",\n".join(item_values)
        + ";\n\n"
        "DELETE FROM item_default_option WHERE item_template_id IN (" + item_id_csv + ");\n\n"
        + options_sql
        + "\n\n".join(shop_inserts)
        + "\n\nCOMMIT;\n"
    )
    rollback = (
        "-- Roll back Marvel/hero costumes generated by costume-tools/build_marvel_costumes.py\n"
        "SET NAMES utf8mb4;\n"
        "START TRANSACTION;\n\n"
        "DELETE FROM item_shop_option WHERE item_shop_id IN "
        f"(SELECT id FROM item_shop WHERE tab_id = {TAB_ID} AND temp_id IN ({item_id_csv}));\n"
        f"DELETE FROM item_shop WHERE tab_id = {TAB_ID} AND temp_id IN ({item_id_csv});\n"
        f"DELETE FROM item_default_option WHERE item_template_id IN ({item_id_csv});\n"
        f"DELETE FROM head_avatar WHERE head_id IN ({part_id_csv});\n"
        f"DELETE FROM item_template WHERE id IN ({item_id_csv});\n"
        f"DELETE FROM part WHERE id IN ({part_id_csv});\n\n"
        "COMMIT;\n"
    )
    install_path.write_text(install, encoding="utf-8", newline="\n")
    rollback_path.write_text(rollback, encoding="utf-8", newline="\n")


def copy_sql_to_project(batch_install: Path, batch_rollback: Path, spec: CostumeSpec, project_dir: Path) -> None:
    create_sql_for_specs([spec], project_dir / "install.sql", project_dir / "rollback.sql")
    shutil.copy2(batch_install, BATCH_ROOT / "install_all.sql")
    shutil.copy2(batch_rollback, BATCH_ROOT / "rollback_all.sql")


def build_project(spec: CostumeSpec, force: bool) -> dict[str, object]:
    project_dir = PROJECTS_ROOT / spec.slug
    (project_dir / "source").mkdir(parents=True, exist_ok=True)
    mapping = make_id_map(spec)
    runtime_assets: dict[str, object] = {}
    source_files: list[str] = []
    for source_id, target_id in mapping.items():
        source_path = spec.source_dir / f"{source_id}.png"
        if not source_path.is_file():
            raise FileNotFoundError(f"Missing source image: {source_path}")
        source_files.append(str(source_path))
        runtime_assets[str(target_id)] = save_scaled_frame(source_path, target_id, force=force)
        shutil.copy2(source_path, project_dir / "source" / source_path.name)
    profile_source = spec.source_dir / spec.profile_file
    if not profile_source.is_file():
        raise FileNotFoundError(f"Missing profile image: {profile_source}")
    runtime_assets[str(profile_image_id(spec))] = save_profile(profile_source, profile_image_id(spec), force=force)
    shutil.copy2(profile_source, project_dir / "source" / spec.profile_file)
    preview = make_contact_sheet(spec, project_dir, mapping)
    head_data = remap_part(spec.head, mapping)
    body_data = remap_part(spec.body, mapping)
    leg_data = remap_part(spec.leg, mapping)
    manifest = {
        "itemId": spec.item_id,
        "name": spec.name,
        "description": spec.description,
        "gender": 3,
        "sourceDir": str(spec.source_dir),
        "offsetSource": spec.offset_source,
        "needsClientOffsetCheck": spec.needs_client_offset_check,
        "iconId": mapping[source_image_ids(spec)[0]],
        "profileIconId": profile_image_id(spec),
        "headAvatar": {"headId": spec.part_ids[0], "avatarId": profile_image_id(spec)},
        "partIds": {"head": spec.part_ids[0], "body": spec.part_ids[1], "leg": spec.part_ids[2]},
        "placeholderImageId": PLACEHOLDER_IMAGE_ID,
        "oldToNewImageIds": {str(k): v for k, v in mapping.items()},
        "profileSource": str(profile_source),
        "profileImageId": profile_image_id(spec),
        "head": head_data,
        "body": body_data,
        "leg": leg_data,
        "headSlots": slot_manifest("HEAD", spec.head, mapping),
        "bodySlots": slot_manifest("BODY", spec.body, mapping),
        "legSlots": slot_manifest("LEG", spec.leg, mapping),
        "runtimeAssets": runtime_assets,
        "shop": {"shopId": SHOP_ID, "tabId": TAB_ID, "cost": SHOP_COST},
        "options": options_manifest(spec),
        "previews": {"contactSheet": str(preview)},
        "qa": {
            "staticStatus": "passed-with-client-check-needed" if spec.needs_client_offset_check else "passed-static",
            "checks": [
                "HEAD has 3 slots",
                "BODY has 17 slots",
                "LEG has 14 slots",
                "all referenced image IDs are in Java short range",
                "all dx/dy values are in Java byte range",
                "runtime PNGs were written to x1-x4",
                "profile is cropped and bottom-aligned to 64x64 x1 canvas",
                "default item options are present",
            ],
        },
    }
    (project_dir / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8", newline="\n")
    (project_dir / "qa-report.json").write_text(json.dumps(manifest["qa"], ensure_ascii=False, indent=2), encoding="utf-8", newline="\n")
    readme = (
        f"# {spec.name}\n\n"
        f"- Item: `{spec.item_id}`; parts HEAD/BODY/LEG: `{spec.part_ids[0]}/{spec.part_ids[1]}/{spec.part_ids[2]}`.\n"
        f"- Icon hành trang: `{mapping[source_image_ids(spec)[0]]}`; profile: `{profile_image_id(spec)}`.\n"
        f"- Nguồn: `{spec.source_dir}`.\n"
        f"- Offset: {spec.offset_source}.\n"
        f"- Shop: `{SHOP_ID}`, tab `{TAB_ID}`, giá `{SHOP_COST}`.\n"
        f"- Option mặc định: `{json.dumps(options_manifest(spec), ensure_ascii=False, separators=(',', ':'))}`.\n\n"
        "## DATA\n\n"
        "```json\n"
        f"HEAD = {json.dumps(head_data, ensure_ascii=False, separators=(',', ':'))}\n"
        f"BODY = {json.dumps(body_data, ensure_ascii=False, separators=(',', ':'))}\n"
        f"LEG  = {json.dumps(leg_data, ensure_ascii=False, separators=(',', ':'))}\n"
        "```\n\n"
        "## QA\n\n"
        f"- Static QA: `{manifest['qa']['staticStatus']}`.\n"
        "- Cần kiểm thử trong client: đứng, chạy trái/phải, bay/rơi, đánh, kỹ năng, icon và profile.\n"
    )
    (project_dir / "README.md").write_text(readme, encoding="utf-8", newline="\n")
    return manifest


def validate_specs() -> None:
    for spec in SPECS:
        if len(spec.head) != 3:
            raise ValueError(f"{spec.slug} HEAD must have 3 slots")
        if len(spec.body) != 17:
            raise ValueError(f"{spec.slug} BODY must have 17 slots")
        if len(spec.leg) != 14:
            raise ValueError(f"{spec.slug} LEG must have 14 slots")
        for role_name, data in (("HEAD", spec.head), ("BODY", spec.body), ("LEG", spec.leg)):
            for slot, (image_id, dx, dy) in enumerate(data):
                if not -32768 <= image_id <= 32767:
                    raise ValueError(f"{spec.slug} {role_name}[{slot}] image id out of range: {image_id}")
                if not -128 <= dx <= 127 or not -128 <= dy <= 127:
                    raise ValueError(f"{spec.slug} {role_name}[{slot}] offset out of range: {dx},{dy}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--force", action="store_true", help="overwrite generated runtime assets if they already exist")
    args = parser.parse_args()
    validate_specs()
    BATCH_ROOT.mkdir(parents=True, exist_ok=True)
    manifests = []
    for spec in SPECS:
        manifests.append(build_project(spec, force=args.force))
    batch_install = BATCH_ROOT / "install_all.sql"
    batch_rollback = BATCH_ROOT / "rollback_all.sql"
    create_sql_for_specs(SPECS, batch_install, batch_rollback)
    for spec in SPECS:
        create_sql_for_specs([spec], PROJECTS_ROOT / spec.slug / "install.sql", PROJECTS_ROOT / spec.slug / "rollback.sql")
    (BATCH_ROOT / "manifest.json").write_text(json.dumps(manifests, ensure_ascii=False, indent=2), encoding="utf-8", newline="\n")
    summary = {
        "items": {str(spec.item_id): spec.name for spec in SPECS},
        "partRange": [SPECS[0].part_ids[0], SPECS[-1].part_ids[-1]],
        "imageRange": [SPECS[0].image_start_id, profile_image_id(SPECS[-1])],
        "installSql": str(batch_install),
        "rollbackSql": str(batch_rollback),
    }
    (BATCH_ROOT / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8", newline="\n")
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
