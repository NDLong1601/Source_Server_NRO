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
    name="Cải trang Yoriichi",
    slug="yoriichi",
    item_id=2074,
    part_ids=(2171, 2172, 2173),
    profile_icon_id=26120,
    first_body_image_id=26101,
    body_image_ids_override=(
        26101,
        26161,
        26173,
        26162,
        26163,
        26164,
        26165,
        26174,
        26109,
        26110,
        26111,
        26112,
        26113,
        26114,
        26115,
        26116,
        26166,
    ),
    transparent_image_id=26118,
    item_icon_id=26119,
    profile_file_name="profile_yoriichi.png",
    item_icon_file_name="avatar_item.png",
    description="Cải trang thành Yoriichi",
    sheets=(
        SheetConfig("idle", "idle.png", 3, 2),
        SheetConfig("walk", "walk.png", 4, 2),
        SheetConfig("run", "walk_1.png", 4, 2),
        SheetConfig("fly", "nhảy_bay.png", 3, 2),
        SheetConfig("fall", "ngã.png", 4, 2),
        SheetConfig("guard", "chịu đựng.png", 3, 2),
        SheetConfig("attack", "attack.png", 4, 2),
        SheetConfig("attack2", "attack_1.png", 3, 2),
    ),
    body_mapping=(
        BodyFrameConfig("guard", 4, "Chặn kiếm có tia lửa, thân đầy đủ", "Đỡ / chịu đòn"),
        BodyFrameConfig(
            "idle",
            0,
            "Đứng nghỉ đã lật đúng hướng, mặt 3/4 đổi hướng rõ khi client mirror",
            "Đứng yên",
            horizontal_flip=True,
        ),
        BodyFrameConfig(
            "fly",
            1,
            "Bay phụ dùng dáng mới, đã lật đúng hướng thay fly[2] cũ bị ngược",
            "Bay / nhảy thay thế",
            horizontal_flip=True,
        ),
        BodyFrameConfig(
            "run",
            0,
            "Hạ thấp trọng tâm bắt đầu lướt, đã lật đúng hướng",
            "Chạy 1 ưu tiên walk_1",
            horizontal_flip=True,
        ),
        BodyFrameConfig(
            "walk",
            2,
            "Sải chạy sạch biên, đã lật đúng hướng thay frame walk_1 chạm mép",
            "Chạy 2 sạch biên",
            horizontal_flip=True,
        ),
        BodyFrameConfig(
            "walk",
            5,
            "Chạy nghiêng sạch biên, đã lật đúng hướng thay frame walk_1 chạm mép",
            "Chạy 3 sạch biên",
            horizontal_flip=True,
        ),
        BodyFrameConfig(
            "run",
            7,
            "Trở về thế chạy sạch biên, đã lật đúng hướng",
            "Chạy 4 ưu tiên walk_1",
            horizontal_flip=True,
        ),
        BodyFrameConfig(
            "fly",
            3,
            "Bay thực tế dùng dáng kiếm mới, đã lật đúng hướng thay fly[2] cũ bị ngược",
            "Bay thực tế BODY[7] thay thế",
            horizontal_flip=True,
        ),
        BodyFrameConfig("attack2", 0, "Tụ kiếm đỏ dưới chân, dùng làm mở kỹ năng", "Tụ lực / kỹ năng ưu tiên attack_1"),
        BodyFrameConfig("attack2", 2, "Chém vòng đỏ trên cao, giữ đủ vòng sáng", "Kỹ năng ưu tiên attack_1"),
        BodyFrameConfig("attack", 2, "Chém ngang với cung kiếm đỏ", "Đánh / kỹ năng"),
        BodyFrameConfig("attack", 4, "Đâm kiếm có vệt đỏ, thân đầy đủ", "Đánh / kỹ năng"),
        BodyFrameConfig("attack", 5, "Xoay chém vòng đỏ lớn", "Đánh / kỹ năng"),
        BodyFrameConfig("attack2", 4, "Đáp đất/tụ kiếm với hiệu ứng đỏ quanh chân", "Đáp đất có hiệu ứng"),
        BodyFrameConfig("attack2", 3, "Cung trăng đỏ lớn, không lấy mảnh hiệu ứng rời", "Kỹ năng hiệu ứng lớn ưu tiên attack_1"),
        BodyFrameConfig("guard", 2, "Kiếm chắn ngang trước mặt", "Phòng thủ"),
        BodyFrameConfig(
            "walk",
            3,
            "Dash/chạy phụ sạch biên, mặt đọc rõ hơn và đã lật đúng hướng",
            "Motion blur / dash sạch biên",
            horizontal_flip=True,
        ),
    ),
)


if __name__ == "__main__":
    run(CONFIG)
