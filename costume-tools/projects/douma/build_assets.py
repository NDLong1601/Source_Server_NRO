from pathlib import Path
import sys

PROJECT_DIR = Path(__file__).resolve().parent
REPOSITORY_ROOT = PROJECT_DIR.parents[2]
sys.path.insert(0, str(REPOSITORY_ROOT / "costume-tools"))

from full_body_hd_builder import BodyFrameConfig, CostumeConfig, SheetConfig, run


CONFIG = CostumeConfig(
    project_dir=PROJECT_DIR,
    repository_root=REPOSITORY_ROOT,
    name="Cải trang Douma",
    slug="douma",
    item_id=2072,
    part_ids=(2165, 2166, 2167),
    profile_icon_id=26058,
    first_body_image_id=26039,
    transparent_image_id=26056,
    item_icon_id=26057,
    profile_file_name="profile_douma.png",
    item_icon_file_name="avatar_item.png",
    description="Cải trang thành Douma",
    body_image_ids_override=(
        26039,
        26097,
        26098,
        26042,
        26043,
        26044,
        26045,
        26099,
        26047,
        26048,
        26049,
        26050,
        26051,
        26052,
        26053,
        26054,
        26055,
    ),
    sheets=(
        SheetConfig("idle", "idle.png", 3, 2),
        SheetConfig("walk", "walk.png", 4, 2),
        SheetConfig("run", "walk_1.png", 4, 2),
        SheetConfig("fly", "nhảy_bay.png", 3, 2),
        SheetConfig("fall", "ngã.png", 4, 2),
        SheetConfig("guard", "chịu đựng.png", 3, 2),
        SheetConfig("attack", "attack.png", 5, 2),
        SheetConfig("attack2", "attack_1.png", 5, 2),
    ),
    body_mapping=(
        BodyFrameConfig("guard", 2, "Đỡ đòn giữa khiên băng xanh lam", "Đỡ / chịu đòn"),
        BodyFrameConfig("idle", 0, "Đứng nghỉ góc 3/4 hướng phải, mặt đổi hướng rõ khi client mirror", "Đứng yên 3/4", (0, 0), True),
        BodyFrameConfig("fly", 5, "Bay ngang đã mirror sang hướng phải để khớp chiều di chuyển", "Bay / nhảy", (0, 0), True),
        BodyFrameConfig("run", 0, "Lướt bước 1 với hiệu ứng băng", "Chạy 1 ưu tiên walk_1"),
        BodyFrameConfig("run", 4, "Lướt bước 2 với hiệu ứng băng, frame sạch", "Chạy 2 ưu tiên walk_1"),
        BodyFrameConfig("run", 4, "Chuyển bước thấp, áo kéo sau", "Chạy 3 ưu tiên walk_1"),
        BodyFrameConfig("run", 7, "Lướt nhanh trong băng vụ quanh chân", "Chạy hiệu ứng 4 ưu tiên walk_1"),
        BodyFrameConfig("fly", 5, "Bay ngang đã mirror sang hướng phải tại slot bay thực tế BODY[7]", "Bay thực tế BODY[7]", (0, 0), True),
        BodyFrameConfig("attack2", 0, "Quạt băng tạo vòng tuyết quanh thân", "Tụ lực / kỹ năng ưu tiên attack_1"),
        BodyFrameConfig("attack2", 5, "Tạo băng vụ lớn quanh thân", "Kỹ năng ưu tiên attack_1"),
        BodyFrameConfig("attack", 0, "Quét quạt băng ngang", "Đánh / kỹ năng"),
        BodyFrameConfig("attack2", 8, "Quạt băng xoay tròn quanh thân", "Đánh / kỹ năng ưu tiên attack_1"),
        BodyFrameConfig("attack2", 9, "Đứng gọi cánh hoa băng", "Đánh / kỹ năng ưu tiên attack_1"),
        BodyFrameConfig("fall", 3, "Bị đánh văng/ngã về sau, frame sạch", "Ngã / đánh văng"),
        BodyFrameConfig("attack2", 5, "Tụ băng lớn quanh người", "Kỹ năng hiệu ứng lớn ưu tiên attack_1"),
        BodyFrameConfig("guard", 4, "Phòng thủ cúi thấp trong băng vụ", "Phòng thủ"),
        BodyFrameConfig("run", 7, "Lướt nhanh trong băng vụ quanh chân", "Motion blur / dash ưu tiên walk_1"),
    ),
)


if __name__ == "__main__":
    run(CONFIG)
