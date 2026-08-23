from pathlib import Path
import sys

PROJECT_DIR = Path(__file__).resolve().parent
REPOSITORY_ROOT = PROJECT_DIR.parents[2]
sys.path.insert(0, str(REPOSITORY_ROOT / "costume-tools"))

from full_body_hd_builder import BodyFrameConfig, CostumeConfig, SheetConfig, run


CONFIG = CostumeConfig(
    project_dir=PROJECT_DIR,
    repository_root=REPOSITORY_ROOT,
    name="Cải trang Shinobu Kocho",
    slug="shinobu-kocho",
    item_id=2070,
    part_ids=(2159, 2160, 2161),
    profile_icon_id=26018,
    first_body_image_id=25189,
    transparent_image_id=25206,
    item_icon_id=25207,
    profile_file_name="profile_shinobu.png",
    item_icon_file_name="avatar_item.png",
    description="Cải trang thành Shinobu Kocho",
    body_image_ids_override=(
        25189, 25208, 25209, 25210, 25211, 25212, 25213, 25225, 25197,
        25198, 25199, 25200, 25201, 25214, 25203, 25204, 25215,
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
        BodyFrameConfig("guard", 2, "Chặn đòn bằng khiên cánh bướm tím", "Đỡ / chịu đòn"),
        BodyFrameConfig("idle", 2, "Đứng nghỉ nhìn sang phải, tâm bàn chân căn theo chuẩn Sanemi", "Đứng yên đã căn lại bóng v4", (-5, 4), True),
        BodyFrameConfig("fly", 4, "Bay ngang nhìn sang phải, thân và áo cùng hướng chuyển động", "Bay / nhảy đã thay hình", (0, 0), True),
        BodyFrameConfig("walk", 0, "Chạy sang phải bước 1", "Chạy đã sửa hướng 1", (0, 0), True),
        BodyFrameConfig("walk", 1, "Chạy sang phải bước 2", "Chạy đã sửa hướng 2", (0, 0), True),
        BodyFrameConfig("walk", 4, "Chạy sang phải bước 3", "Chạy đã sửa hướng 3", (0, 0), True),
        BodyFrameConfig("walk", 5, "Chạy sang phải bước 4", "Chạy đã sửa hướng 4", (0, 0), True),
        BodyFrameConfig("fly", 4, "Bay ngang nhìn sang phải tại slot bay thực tế của client", "Bay thực tế đã sửa", (0, 0), True),
        BodyFrameConfig("attack2", 0, "Tụ bướm tím quanh chân", "Tụ lực / kỹ năng"),
        BodyFrameConfig("attack2", 1, "Tụ lực trong đàn bướm tím", "Kỹ năng"),
        BodyFrameConfig("attack", 5, "Quét kiếm tạo cung tím", "Đánh / kỹ năng"),
        BodyFrameConfig("attack", 6, "Xoay người giữa vòng bướm tím", "Đánh / kỹ năng"),
        BodyFrameConfig("attack2", 5, "Chém xoay tạo vòng tím xanh", "Đánh / kỹ năng"),
        BodyFrameConfig("fly", 4, "Bay ngang dự phòng cho slot bay/ngã thứ hai", "Bay / ngã đã thay hình", (0, 0), True),
        BodyFrameConfig("attack2", 6, "Bùng cánh bướm tím xanh quanh thân", "Kỹ năng hiệu ứng lớn"),
        BodyFrameConfig("guard", 3, "Giữ khiên cánh bướm tím sát thân", "Phòng thủ"),
        BodyFrameConfig("attack", 5, "Lướt chém sang phải kéo theo cung tím", "Motion blur / dash đã sửa hướng", (0, 0), True),
    ),
)


if __name__ == "__main__":
    run(CONFIG)
