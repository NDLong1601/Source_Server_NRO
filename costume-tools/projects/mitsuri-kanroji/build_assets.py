from pathlib import Path
import sys

PROJECT_DIR = Path(__file__).resolve().parent
REPOSITORY_ROOT = PROJECT_DIR.parents[2]
sys.path.insert(0, str(REPOSITORY_ROOT / "costume-tools"))

from full_body_hd_builder import BodyFrameConfig, CostumeConfig, SheetConfig, run


CONFIG = CostumeConfig(
    project_dir=PROJECT_DIR,
    repository_root=REPOSITORY_ROOT,
    name="Cải trang Mitsuri Kanroji",
    slug="mitsuri-kanroji",
    item_id=2069,
    part_ids=(2156, 2157, 2158),
    profile_icon_id=26017,
    first_body_image_id=25169,
    transparent_image_id=25186,
    item_icon_id=25187,
    profile_file_name="profile_Míturi.png",
    item_icon_file_name="avater_item.png",
    description="Cải trang thành Mitsuri Kanroji",
    body_image_ids_override=(
        25169, 25216, 25300, 25172, 25173, 25174, 25175, 26005, 25177,
        25178, 25179, 25180, 25181, 25301, 25183, 25184, 25185,
    ),
    sheets=(
        SheetConfig("idle", "idle.png", 3, 2),
        SheetConfig("walk", "walk.png", 4, 2),
        SheetConfig("run", "walk_1.png", 4, 2),
        SheetConfig("fly", "nhảy_bay.png", 4, 2),
        SheetConfig("fall", "ngã.png", 4, 2),
        SheetConfig("guard", "chịu đựng.png", 3, 2),
        SheetConfig("attack", "attack.png", 5, 2),
        SheetConfig("attack2", "attack_1.png", 5, 2),
    ),
    body_mapping=(
        BodyFrameConfig("guard", 2, "Chặn đòn bằng lưỡi kiếm mềm và tia va chạm", "Đỡ / chịu đòn"),
        BodyFrameConfig("idle", 2, "Đứng nghỉ đã lật ngang để hướng trái/phải khớp client", "Đứng yên đã sửa hướng", (-6, 6), True),
        BodyFrameConfig("fly", 0, "Bay ngang duỗi người, tay đưa về sau; thay hoàn toàn frame bay cũ", "Bay / nhảy đã thay ảnh v3"),
        BodyFrameConfig("run", 0, "Hạ thấp trọng tâm chuẩn bị lao", "Chạy 1"),
        BodyFrameConfig("run", 1, "Bật lao tới kèm bụi", "Chạy 2"),
        BodyFrameConfig("run", 2, "Lướt thấp kéo kiếm mềm", "Chạy 3"),
        BodyFrameConfig("run", 5, "Lướt nhanh với cung kiếm hồng", "Chạy hiệu ứng 4"),
        BodyFrameConfig("fly", 0, "Bay ngang duỗi người tại slot bay thực tế của client", "Bay thực tế BODY[7]"),
        BodyFrameConfig("attack", 1, "Quét kiếm tạo vòng hồng và cánh hoa", "Tụ lực / kỹ năng"),
        BodyFrameConfig("attack", 5, "Vẽ vòng kiếm hình trái tim", "Kỹ năng"),
        BodyFrameConfig("attack2", 5, "Quét kiếm tạo cung hồng thấp", "Đánh / kỹ năng"),
        BodyFrameConfig("attack2", 6, "Lao chém ngang cùng cánh hoa", "Đánh / kỹ năng"),
        BodyFrameConfig("attack", 1, "Xoay kiếm giữa vòng hồng và cánh hoa", "Đánh / kỹ năng"),
        BodyFrameConfig("fly", 0, "Dùng cùng tư thế bay ngang cho nhánh bay/ngã thứ hai của client", "Bay / ngã dự phòng đã thay ảnh v3"),
        BodyFrameConfig("attack", 6, "Tụ vòng kiếm hồng xoắn quanh thân", "Kỹ năng hiệu ứng lớn"),
        BodyFrameConfig("guard", 3, "Chặn đòn bằng khiên kiếm hồng", "Phòng thủ"),
        BodyFrameConfig("run", 5, "Lướt nhanh kéo cung kiếm hồng", "Motion blur / dash"),
    ),
)


if __name__ == "__main__":
    run(CONFIG)
