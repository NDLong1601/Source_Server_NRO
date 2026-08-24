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
    name="Cải trang Kibutsuji Muzan",
    slug="kibutsuji-muzan",
    item_id=2075,
    part_ids=(2174, 2175, 2176),
    profile_icon_id=26140,
    first_body_image_id=26121,
    body_image_ids_override=(
        26121,
        26167,
        26168,
        26124,
        26125,
        26169,
        26127,
        26170,
        26129,
        26130,
        26131,
        26132,
        26133,
        26134,
        26135,
        26136,
        26171,
    ),
    transparent_image_id=26138,
    item_icon_id=26139,
    profile_file_name="profile_muzan.png",
    item_icon_file_name="avatar_item.png",
    description="Cải trang thành Kibutsuji Muzan",
    sheets=(
        SheetConfig("idle", "idle.png", 3, 2),
        SheetConfig("walk", "walk.png", 4, 2),
        SheetConfig("run", "walk_1.png", 4, 2),
        SheetConfig("fly", "nhảy_bay.png", 4, 2),
        SheetConfig("fall", "ngã.png", 4, 2),
        SheetConfig("guard", "chịu đựng.png", 3, 2),
        SheetConfig("attack", "attack.png", 5, 2),
        SheetConfig("attack2", "attack_1.png", 4, 2),
    ),
    body_mapping=(
        BodyFrameConfig("guard", 4, "Dựng khiên máu đỏ, thân đủ và hiệu ứng nổi rõ", "Đỡ / chịu đòn"),
        BodyFrameConfig("idle", 0, "Đứng nghỉ mới, mặt rõ hơn và không dùng idle cũ", "Đứng yên thay thế"),
        BodyFrameConfig("fly", 4, "Bay ngang sạch biên, áo choàng kéo sau; thay frame fly[3] bị chạm mép", "Bay / nhảy"),
        BodyFrameConfig("run", 0, "Chống tay chuẩn bị lướt, có bụi chân", "Chạy 1 ưu tiên walk_1"),
        BodyFrameConfig("run", 4, "Lướt thấp trong vệt máu đỏ", "Chạy 2 ưu tiên walk_1"),
        BodyFrameConfig("walk", 4, "Sải chạy sạch biên, thay frame walk_1 bị cắt mép", "Chạy 3 sạch biên"),
        BodyFrameConfig("run", 7, "Đứng lại sau pha lướt, còn hiệu ứng đỏ", "Chạy 4 ưu tiên walk_1"),
        BodyFrameConfig("fly", 4, "Bay ngang sạch biên tại slot bay thực tế, thay fly[3] bị chạm mép", "Bay thực tế BODY[7]"),
        BodyFrameConfig("attack2", 0, "Tụ cầu máu trước ngực, hiệu ứng đỏ quanh thân", "Tụ lực / kỹ năng ưu tiên attack_1"),
        BodyFrameConfig("attack2", 1, "Giơ cầu máu, giữ đủ luồng đỏ quanh tay", "Kỹ năng ưu tiên attack_1"),
        BodyFrameConfig("attack", 4, "Xoay chém vòng đỏ lớn", "Đánh / kỹ năng"),
        BodyFrameConfig("attack", 6, "Lao tới với vệt máu ngang", "Đánh / kỹ năng"),
        BodyFrameConfig("attack2", 3, "Chém vòng máu quanh thân", "Đánh / kỹ năng ưu tiên attack_1"),
        BodyFrameConfig("attack", 5, "Đập đất tạo xung đỏ, dùng làm đáp đất", "Đáp đất có hiệu ứng"),
        BodyFrameConfig("attack2", 4, "Bùng aura đỏ phía sau, kỹ năng lớn", "Kỹ năng hiệu ứng lớn ưu tiên attack_1"),
        BodyFrameConfig("guard", 2, "Thủ thế nghiêng, áo choàng che phía sau", "Phòng thủ"),
        BodyFrameConfig("run", 4, "Dash/chạy phụ có vệt đỏ sạch biên, thay frame run[3] bị cắt mép", "Motion blur / dash sạch biên"),
    ),
)


if __name__ == "__main__":
    run(CONFIG)
