from pathlib import Path
import sys

import cv2
import numpy as np
from PIL import Image

PROJECT_DIR = Path(__file__).resolve().parent
REPOSITORY_ROOT = PROJECT_DIR.parents[2]
sys.path.insert(0, str(REPOSITORY_ROOT / "costume-tools"))

from full_body_hd_builder import BodyFrameConfig, CostumeConfig, SheetConfig, run


CONFIG = CostumeConfig(
    project_dir=PROJECT_DIR,
    repository_root=REPOSITORY_ROOT,
    name="Cải trang Kokushibo",
    slug="kokushibo",
    item_id=2073,
    part_ids=(2168, 2169, 2170),
    profile_icon_id=26078,
    first_body_image_id=26059,
    transparent_image_id=26076,
    item_icon_id=26077,
    profile_file_name="profile_kokushibo.png",
    item_icon_file_name="avatar_item.png",
    description="Cải trang thành Kokushibo",
    body_image_ids_override=(
        26059,
        26060,
        26061,
        26062,
        26063,
        26064,
        26065,
        26066,
        26089,
        26090,
        26069,
        26070,
        26091,
        26100,
        26092,
        26074,
        26075,
    ),
    sheets=(
        SheetConfig("idle", "idle.png", 3, 2),
        SheetConfig("walk", "walk.png", 4, 2),
        SheetConfig("fly", "bay.png", 4, 2),
        SheetConfig("fall", "fall_clean.png", 4, 2),
        SheetConfig("guard", "chịu đựng.png", 3, 2),
        SheetConfig("attack", "attack.png", 5, 2),
        SheetConfig("attack2", "attack_1.png", 4, 2),
    ),
    body_mapping=(
        BodyFrameConfig("guard", 3, "Đỡ đòn với khiên trăng tím bao quanh thân", "Đỡ / chịu đòn"),
        BodyFrameConfig("idle", 2, "Đứng nghỉ cầm kiếm, hiệu ứng tím nhẹ", "Đứng yên"),
        BodyFrameConfig("fly", 0, "Bay ngang với vệt trăng tím", "Bay / nhảy"),
        BodyFrameConfig("walk", 0, "Chạy/lướt bước 1", "Chạy 1"),
        BodyFrameConfig("walk", 1, "Chạy/lướt bước 2", "Chạy 2"),
        BodyFrameConfig("walk", 4, "Chạy/lướt bước 3", "Chạy 3"),
        BodyFrameConfig("walk", 5, "Lướt nhanh kèm trăng tím", "Chạy hiệu ứng 4"),
        BodyFrameConfig("fly", 0, "Bay ngang với vệt trăng tím tại slot bay thực tế", "Bay thực tế BODY[7]"),
        BodyFrameConfig("attack2", 1, "Chém vòng trăng tím", "Tụ lực / kỹ năng ưu tiên attack_1"),
        BodyFrameConfig("attack2", 4, "Tụ kiếm với trăng tím lớn, cắt lại từ lưới 4x2 đúng", "Kỹ năng ưu tiên attack_1"),
        BodyFrameConfig("attack", 1, "Chém ngang tạo cung tím", "Đánh / kỹ năng"),
        BodyFrameConfig("attack", 5, "Lao chém thấp tạo cung tím", "Đánh / kỹ năng"),
        BodyFrameConfig("attack2", 5, "Tụ trăng tím quanh thân, giữ đủ vòng sáng", "Đánh / kỹ năng ưu tiên attack_1"),
        BodyFrameConfig("attack2", 6, "Đáp đất/tụ lực với hiệu ứng tím lớn quanh thân và dưới chân", "Đáp đất có hiệu ứng ưu tiên attack_1"),
        BodyFrameConfig("attack2", 6, "Tụ trăng tím lớn quanh thân, giữ đủ luồng tím bên phải", "Kỹ năng hiệu ứng lớn ưu tiên attack_1"),
        BodyFrameConfig("guard", 4, "Phòng thủ cầm kiếm thấp, frame sạch", "Phòng thủ"),
        BodyFrameConfig("walk", 5, "Lướt nhanh kèm trăng tím", "Motion blur / dash"),
    ),
    global_body_offset_adjustment=(0, 4),
)


def prepare_clean_fall_sheet() -> None:
    source_path = PROJECT_DIR / "source" / "ngã.png"
    output_path = PROJECT_DIR / "source" / "fall_clean.png"
    image = Image.open(source_path).convert("RGBA")
    rgba = np.array(image)
    columns = 4
    rows = 2
    cell_width = image.width // columns
    cell_height = image.height // rows
    for row in range(rows):
        for column in range(columns):
            x0 = column * cell_width
            y0 = row * cell_height
            x1 = image.width if column == columns - 1 else (column + 1) * cell_width
            y1 = image.height if row == rows - 1 else (row + 1) * cell_height
            cell = rgba[y0:y1, x0:x1]
            alpha = cell[:, :, 3]
            binary = np.where(alpha >= 16, 255, 0).astype(np.uint8)
            count, labels, stats, centroids = cv2.connectedComponentsWithStats(binary, connectivity=8)
            for component_id in range(1, count):
                left = int(stats[component_id, cv2.CC_STAT_LEFT])
                top = int(stats[component_id, cv2.CC_STAT_TOP])
                width = int(stats[component_id, cv2.CC_STAT_WIDTH])
                height = int(stats[component_id, cv2.CC_STAT_HEIGHT])
                area = int(stats[component_id, cv2.CC_STAT_AREA])
                center_x, center_y = centroids[component_id]
                component_mask = labels == component_id
                rgb_mean = float(cell[:, :, :3][component_mask].mean())
                looks_like_cell_number = (
                    area <= 1400
                    and width <= 70
                    and height <= 70
                    and center_y >= cell.shape[0] * 0.70
                    and cell.shape[1] * 0.35 <= center_x <= cell.shape[1] * 0.65
                    and rgb_mean < 70
                )
                if looks_like_cell_number:
                    cell[component_mask] = 0
    Image.fromarray(rgba, mode="RGBA").save(output_path, optimize=True)


if __name__ == "__main__":
    prepare_clean_fall_sheet()
    run(CONFIG)
