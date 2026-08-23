from pathlib import Path
import sys

PROJECT_DIR = Path(__file__).resolve().parent
REPOSITORY_ROOT = PROJECT_DIR.parents[2]
sys.path.insert(0, str(REPOSITORY_ROOT / "costume-tools"))

from full_body_hd_builder import BodyFrameConfig, CostumeConfig, SheetConfig, run


CONFIG = CostumeConfig(
    project_dir=PROJECT_DIR,
    repository_root=REPOSITORY_ROOT,
    name="Cải trang Obanai Iguro",
    slug="obanai-iguro",
    item_id=2065,
    part_ids=(2144, 2145, 2146),
    profile_icon_id=26013,
    first_body_image_id=25084,
    transparent_image_id=25101,
    item_icon_id=25102,
    profile_file_name="profile.png",
    item_icon_file_name="avatar_item.png",
    description="Cải trang thành Obanai Iguro",
    body_image_ids_override=(
        25084, 25085, 25125, 25087, 25088, 25089, 25090, 26006, 25092,
        25093, 25094, 25095, 25096, 25097, 25098, 25099, 25100,
    ),
    sheets=(
        SheetConfig("idle", "idle.png", 3, 2),
        SheetConfig("walk", "walk.png", 4, 2),
        SheetConfig("run", "walk_1.png", 3, 2),
        SheetConfig("fly", "nhảy_bay.png", 3, 2),
        SheetConfig("fall", "ngã.png", 4, 2),
        SheetConfig("guard", "chịu đụng.png", 3, 2),
        SheetConfig("attack", "attack.png", 4, 2),
        SheetConfig("attack2", "attack_1.png", 4, 2),
    ),
    body_mapping=(
        BodyFrameConfig("guard", 2, "Đỡ kiếm trong khiên tím", "Đỡ / chịu đòn"),
        BodyFrameConfig("idle", 5, "Đứng nghỉ nghiêng phải cùng bạch xà", "Đứng yên đã căn bóng", (8, 7)),
        BodyFrameConfig("fly", 5, "Bay ngang hướng phải, áo choàng kéo về sau", "Bay / nhảy đã sửa", (4, 10)),
        BodyFrameConfig("run", 0, "Hạ thấp trọng tâm kèm bụi", "Chạy hiệu ứng 1"),
        BodyFrameConfig("run", 1, "Lao tới kèm vệt tốc độ", "Chạy hiệu ứng 2"),
        BodyFrameConfig("run", 3, "Chuyển bước với đường kiếm tím", "Chạy hiệu ứng 3"),
        BodyFrameConfig("run", 4, "Lướt thấp cùng bạch xà", "Chạy hiệu ứng 4"),
        BodyFrameConfig("fly", 5, "Bay ngang hướng phải, áo choàng kéo về sau tại slot bay thực tế", "Bay thực tế BODY[7]", (4, 10)),
        BodyFrameConfig("attack2", 0, "Tụ khí tím quanh thân", "Tụ lực / kỹ năng"),
        BodyFrameConfig("attack2", 3, "Triệu hồi đại xà tím", "Kỹ năng hiệu ứng lớn"),
        BodyFrameConfig("attack", 2, "Chém ngang tạo cung kiếm hồng", "Đánh / kỹ năng"),
        BodyFrameConfig("attack", 4, "Quét kiếm hồng sát đất", "Đánh / kỹ năng"),
        BodyFrameConfig("attack2", 4, "Đại xà trườn quanh kiếm", "Đánh / kỹ năng"),
        BodyFrameConfig("fall", 3, "Bị đánh văng trên không", "Ngã / đánh văng"),
        BodyFrameConfig("attack2", 6, "Chém cùng cánh hoa và vệt xanh", "Kỹ năng"),
        BodyFrameConfig("guard", 3, "Chặn đòn kèm tia va chạm tím", "Phòng thủ"),
        BodyFrameConfig("run", 5, "Lướt cực nhanh với dải tốc độ", "Motion blur / dash"),
    ),
)


if __name__ == "__main__":
    run(CONFIG)
