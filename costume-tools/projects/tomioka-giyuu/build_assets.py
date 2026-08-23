from pathlib import Path
import sys

PROJECT_DIR = Path(__file__).resolve().parent
REPOSITORY_ROOT = PROJECT_DIR.parents[2]
sys.path.insert(0, str(REPOSITORY_ROOT / "costume-tools"))

from full_body_hd_builder import BodyFrameConfig, CostumeConfig, SheetConfig, run


CONFIG = CostumeConfig(
    project_dir=PROJECT_DIR,
    repository_root=REPOSITORY_ROOT,
    name="Cải trang Tomioka Giyuu",
    slug="tomioka-giyuu",
    item_id=2064,
    part_ids=(2141, 2142, 2143),
    profile_icon_id=26012,
    first_body_image_id=25064,
    transparent_image_id=25081,
    item_icon_id=25082,
    profile_file_name="profile_giyuu.png",
    item_icon_file_name="avatar_item.png",
    description="Cải trang thành Tomioka Giyuu",
    body_image_ids_override=(
        25064, 25123, 25124, 25067, 25068, 25069, 25070, 26008, 25072,
        25073, 25074, 25075, 25076, 25077, 25078, 25079, 25080,
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
        BodyFrameConfig("guard", 2, "Đỡ kiếm trong khiên nước", "Đỡ / chịu đòn"),
        BodyFrameConfig("idle", 2, "Đứng nghỉ hướng phải, đầu góc 3/4", "Đứng yên đã sửa góc đầu", (12, 3)),
        BodyFrameConfig("fly", 2, "Bay ngang sang phải trong vòng nước", "Bay / nhảy đã sửa", (3, -3)),
        BodyFrameConfig("run", 0, "Hạ thấp trọng tâm kèm bụi", "Chạy hiệu ứng 1"),
        BodyFrameConfig("run", 1, "Lao tới trong vệt nước", "Chạy hiệu ứng 2"),
        BodyFrameConfig("run", 5, "Tiếp đất thấp kèm bụi", "Chạy hiệu ứng 3"),
        BodyFrameConfig("run", 6, "Lướt thấp trong sóng nước", "Chạy hiệu ứng 4"),
        BodyFrameConfig("fly", 3, "Lao bay ngang sang phải, nước kéo dài phía sau", "Bay thực tế BODY[7]"),
        BodyFrameConfig("attack2", 2, "Tụ thủy quanh kiếm dựng dọc", "Tụ lực / kỹ năng"),
        BodyFrameConfig("attack2", 5, "Chém tạo vòng sóng tròn", "Kỹ năng"),
        BodyFrameConfig("attack", 3, "Chém ngang tạo sóng nước", "Đánh / kỹ năng"),
        BodyFrameConfig("attack", 4, "Chém chéo tạo thác nước", "Đánh / kỹ năng"),
        BodyFrameConfig("attack", 7, "Bổ kiếm tạo cột nước", "Đánh / kỹ năng"),
        BodyFrameConfig("fall", 3, "Bị đánh văng trên không", "Ngã / đánh văng"),
        BodyFrameConfig("attack2", 6, "Triệu hồi thủy long lớn", "Kỹ năng hiệu ứng lớn"),
        BodyFrameConfig("guard", 3, "Chặn đòn kèm nước bắn", "Phòng thủ"),
        BodyFrameConfig("run", 3, "Lướt cực nhanh kèm dư ảnh nước", "Motion blur / dash"),
    ),
)


if __name__ == "__main__":
    run(CONFIG)
