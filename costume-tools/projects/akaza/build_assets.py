from pathlib import Path
import sys

PROJECT_DIR = Path(__file__).resolve().parent
REPOSITORY_ROOT = PROJECT_DIR.parents[2]
sys.path.insert(0, str(REPOSITORY_ROOT / "costume-tools"))

from full_body_hd_builder import BodyFrameConfig, CostumeConfig, SheetConfig, run


CONFIG = CostumeConfig(
    project_dir=PROJECT_DIR,
    repository_root=REPOSITORY_ROOT,
    name="Cải trang Akaza",
    slug="akaza",
    item_id=2071,
    part_ids=(2162, 2163, 2164),
    profile_icon_id=26038,
    first_body_image_id=26019,
    transparent_image_id=26036,
    item_icon_id=26037,
    profile_file_name="profile_Akaza.png",
    item_icon_file_name="avatar_item.png",
    description="Cải trang thành Akaza",
    body_image_ids_override=(
        26019,
        26093,
        26094,
        26022,
        26023,
        26024,
        26025,
        26095,
        26080,
        26081,
        26082,
        26083,
        26084,
        26096,
        26085,
        26034,
        26035,
    ),
    sheets=(
        SheetConfig("idle", "idle.png", 3, 2),
        SheetConfig("walk", "walk.png", 4, 2),
        SheetConfig("run", "walk_1.png", 4, 2),
        SheetConfig("fly", "nhảy_bay.png", 4, 2),
        SheetConfig("fall", "ngã.png", 4, 2),
        SheetConfig("guard", "chịu đựng.png", 3, 2),
        SheetConfig("attack", "attack.png", 4, 2),
        SheetConfig("attack2", "attack_1.png", 3, 2),
    ),
    body_mapping=(
        BodyFrameConfig("guard", 4, "Thủ thế hai tay trước mặt, không chạm mép sheet", "Đỡ / chịu đòn"),
        BodyFrameConfig("idle", 2, "Đứng nghỉ Akaza góc 3/4 hướng phải, mặt đổi hướng rõ khi client mirror", "Đứng yên 3/4"),
        BodyFrameConfig("fly", 3, "Khôi phục dáng bay đầu tiên: lao nghiêng 3/4 hướng phải, không dùng frame chính diện", "Bay / nhảy"),
        BodyFrameConfig("walk", 0, "Chạy bước 1, biên trong suốt sạch", "Chạy 1"),
        BodyFrameConfig("walk", 1, "Chạy bước 2, biên trong suốt sạch", "Chạy 2"),
        BodyFrameConfig("walk", 4, "Chạy bước 3, biên trong suốt sạch", "Chạy 3"),
        BodyFrameConfig("walk", 5, "Chạy bước 4, biên trong suốt sạch", "Chạy 4"),
        BodyFrameConfig("fly", 3, "Khôi phục dáng bay đầu tiên tại slot bay thực tế BODY[7]", "Bay thực tế BODY[7]"),
        BodyFrameConfig("attack2", 1, "Tụ quyền trong vòng khí xanh, cắt lại từ lưới 3x2 đúng", "Tụ lực / kỹ năng ưu tiên attack_1"),
        BodyFrameConfig("attack2", 3, "Đòn đánh vòng khí xanh lớn, giữ đủ vòng sáng", "Kỹ năng ưu tiên attack_1"),
        BodyFrameConfig("attack", 0, "Đấm quét vòng khí xanh, cắt lại từ lưới 4x2 đúng", "Đánh / kỹ năng"),
        BodyFrameConfig("attack", 5, "Xoay người đánh vòng khí, thân đầy đủ và không mất hiệu ứng", "Đánh / kỹ năng"),
        BodyFrameConfig("attack2", 4, "Quyền kích với hiệu ứng xanh dày, giữ đủ vệt sáng", "Đánh / kỹ năng ưu tiên attack_1"),
        BodyFrameConfig("run", 7, "Đáp đất chạm tay xuống nền kèm bụi, thay frame ngã không hiệu ứng", "Đáp đất"),
        BodyFrameConfig("attack2", 5, "Tụ lực trong vòng xanh lớn, cắt lại đủ thân và aura", "Kỹ năng hiệu ứng lớn ưu tiên attack_1"),
        BodyFrameConfig("guard", 1, "Khoanh tay phòng thủ, biên trong suốt sạch", "Phòng thủ"),
        BodyFrameConfig("run", 7, "Lướt thấp sạch biên từ walk_1", "Motion blur / dash ưu tiên walk_1"),
    ),
)


if __name__ == "__main__":
    run(CONFIG)
