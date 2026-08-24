from pathlib import Path
import os
import sys

PROJECT_DIR = Path(__file__).resolve().parent
REAL_REPOSITORY_ROOT = PROJECT_DIR.parents[2]
REPOSITORY_ROOT = Path(os.environ.get("NRO_COSTUME_OUTPUT_ROOT", PROJECT_DIR / "staging-root")).resolve()
sys.path.insert(0, str(REAL_REPOSITORY_ROOT / "costume-tools"))

from full_body_hd_builder import BodyFrameConfig, CostumeConfig, SheetConfig, run


CONFIG = CostumeConfig(
    project_dir=PROJECT_DIR,
    repository_root=REPOSITORY_ROOT,
    name="Cải trang Muzan Demon",
    slug="muzan-demon",
    item_id=2076,
    part_ids=(2177, 2178, 2179),
    profile_icon_id=26160,
    first_body_image_id=26141,
    body_image_ids_override=(
        26141,
        26172,
        26143,
        26144,
        26145,
        26146,
        26147,
        26148,
        26149,
        26150,
        26151,
        26152,
        26153,
        26154,
        26155,
        26156,
        26157,
    ),
    transparent_image_id=26158,
    item_icon_id=26159,
    profile_file_name="profile_muzan_demon.png",
    item_icon_file_name="avatar_item.png",
    description="Cải trang thành Muzan Demon",
    sheets=(
        SheetConfig("idle", "idle.png", 3, 2),
        SheetConfig("walk", "walk.png", 4, 2),
        SheetConfig("run", "walk_1.png", 3, 2),
        SheetConfig("fly", "nhảy_bay.png", 3, 2),
        SheetConfig("fall", "ngã.png", 4, 2),
        SheetConfig("guard", "chịu đựng.png", 3, 2),
        SheetConfig("attack", "attack.png", 3, 2),
        SheetConfig("attack2", "attack_1.png", 3, 2),
    ),
    body_mapping=(
        BodyFrameConfig("guard", 1, "Khoanh tay phòng thủ, tua lưỡi hái bao quanh", "Đỡ / chịu đòn"),
        BodyFrameConfig(
            "idle",
            2,
            "Đứng nghỉ dữ dằn đã lật đúng hướng, mặt lệch nhẹ để client mirror đổi hướng",
            "Đứng yên",
            horizontal_flip=True,
        ),
        BodyFrameConfig("fly", 2, "Lao bay ngang hướng phải, lấy từ lưới 3x2 để tránh mảnh rời", "Bay / nhảy"),
        BodyFrameConfig("run", 0, "Bò thấp chuẩn bị lao, có bụi chân", "Chạy 1 ưu tiên walk_1"),
        BodyFrameConfig("run", 1, "Lao thấp với vệt đỏ quanh chân, lấy từ lưới 3x2", "Chạy 2 ưu tiên walk_1"),
        BodyFrameConfig("run", 2, "Lao ngang hướng phải, vệt đỏ phía sau còn nguyên", "Chạy 3 ưu tiên walk_1"),
        BodyFrameConfig("walk", 7, "Chạy ổn định, dùng thay frame walk_1 bị sát biên", "Chạy 4"),
        BodyFrameConfig("fly", 2, "Lao bay ngang hướng phải tại slot bay thực tế, thân đầy đủ", "Bay thực tế BODY[7]"),
        BodyFrameConfig("attack2", 0, "Tụ aura đỏ quanh thân", "Tụ lực / kỹ năng ưu tiên attack_1"),
        BodyFrameConfig("attack2", 5, "Đứng trong aura đỏ trọn vẹn, thay frame bị cắt", "Kỹ năng ưu tiên attack_1"),
        BodyFrameConfig("attack", 0, "Thủ thế có lưỡi hái bao quanh, không cắt hiệu ứng", "Đánh / kỹ năng"),
        BodyFrameConfig("attack", 5, "Đứng tấn có hiệu ứng quanh thân, không cắt mép", "Đánh / kỹ năng"),
        BodyFrameConfig("attack2", 0, "Tụ aura đỏ quanh thân, dùng thay vòng lưỡi hái bị cắt", "Đánh / kỹ năng ưu tiên attack_1"),
        BodyFrameConfig("run", 0, "Chạm đất có bụi và hiệu ứng đỏ, dùng làm đáp đất sạch biên", "Đáp đất có hiệu ứng"),
        BodyFrameConfig("attack2", 5, "Đứng trong aura đỏ trọn vẹn, kỹ năng lớn", "Kỹ năng hiệu ứng lớn ưu tiên attack_1"),
        BodyFrameConfig("guard", 2, "Phòng thủ cuộn kín bằng tua lưỡi hái", "Phòng thủ"),
        BodyFrameConfig("run", 2, "Lao tốc độ với vệt đỏ trắng phía sau, không mất hiệu ứng", "Motion blur / dash ưu tiên walk_1"),
    ),
)


if __name__ == "__main__":
    run(CONFIG)
