from pathlib import Path
import sys

PROJECT_DIR = Path(__file__).resolve().parent
REPOSITORY_ROOT = PROJECT_DIR.parents[2]
sys.path.insert(0, str(REPOSITORY_ROOT / "costume-tools"))

from full_body_hd_builder import BodyFrameConfig, CostumeConfig, SheetConfig, run


CONFIG = CostumeConfig(
    project_dir=PROJECT_DIR,
    repository_root=REPOSITORY_ROOT,
    name="Cải trang Rengoku",
    slug="rengoku",
    item_id=2066,
    part_ids=(2147, 2148, 2149),
    profile_icon_id=26014,
    first_body_image_id=25104,
    transparent_image_id=25121,
    item_icon_id=25122,
    profile_file_name="profile_rengoku.png",
    item_icon_file_name="avatar_item.png",
    description="Cải trang thành Rengoku",
    body_image_ids_override=(
        25104, 25126, 25127, 25107, 25108, 25109, 25110, 26002, 25112,
        25113, 25114, 25115, 25116, 25117, 25118, 25119, 25120,
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
        BodyFrameConfig("guard", 2, "Đỡ kiếm trong khiên lửa", "Đỡ / chịu đòn"),
        BodyFrameConfig("idle", 2, "Đứng nghỉ hướng phải, áo choàng kéo về sau", "Đứng yên đã sửa hướng", (12, 1)),
        BodyFrameConfig("fly", 5, "Lao bay sang phải trong vòng hỏa diệm", "Bay / nhảy đã sửa", (1, 4)),
        BodyFrameConfig("run", 0, "Hạ thấp trọng tâm kèm bụi lửa", "Chạy hiệu ứng 1"),
        BodyFrameConfig("run", 1, "Lao tới trong vệt lửa", "Chạy hiệu ứng 2"),
        BodyFrameConfig("run", 4, "Tiếp đất cùng đốm lửa", "Chạy hiệu ứng 3"),
        BodyFrameConfig("run", 6, "Lướt thấp kéo đuôi lửa", "Chạy hiệu ứng 4"),
        BodyFrameConfig("fly", 5, "Lao bay sang phải trong vòng hỏa diệm tại slot bay thực tế", "Bay thực tế BODY[7]", (1, 4)),
        BodyFrameConfig("attack", 2, "Gầm tụ hỏa quanh thân", "Tụ lực / kỹ năng"),
        BodyFrameConfig("attack", 6, "Triệu hồi sư tử lửa lớn", "Kỹ năng hiệu ứng lớn"),
        BodyFrameConfig("attack2", 3, "Chém ngang tạo lưỡi lửa", "Đánh / kỹ năng"),
        BodyFrameConfig("attack2", 4, "Bổ kiếm tạo thác lửa", "Đánh / kỹ năng"),
        BodyFrameConfig("attack2", 6, "Chém bùng hỏa diệm quanh thân", "Đánh / kỹ năng"),
        BodyFrameConfig("fall", 3, "Bị đánh văng trên không", "Ngã / đánh văng"),
        BodyFrameConfig("attack", 5, "Xoay kiếm trong vòng hỏa diệm", "Kỹ năng"),
        BodyFrameConfig("guard", 3, "Chặn đòn kèm tia lửa", "Phòng thủ"),
        BodyFrameConfig("run", 3, "Lướt cực nhanh kèm dư ảnh lửa", "Motion blur / dash"),
    ),
)


if __name__ == "__main__":
    run(CONFIG)
