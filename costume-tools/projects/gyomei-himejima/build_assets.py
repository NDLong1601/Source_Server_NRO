from pathlib import Path
import sys

PROJECT_DIR = Path(__file__).resolve().parent
REPOSITORY_ROOT = PROJECT_DIR.parents[2]
sys.path.insert(0, str(REPOSITORY_ROOT / "costume-tools"))

from full_body_hd_builder import BodyFrameConfig, CostumeConfig, SheetConfig, run


CONFIG = CostumeConfig(
    project_dir=PROJECT_DIR,
    repository_root=REPOSITORY_ROOT,
    name="Cải trang Gyomei Himejima",
    slug="gyomei-himejima",
    item_id=2068,
    part_ids=(2153, 2154, 2155),
    profile_icon_id=26016,
    first_body_image_id=25149,
    transparent_image_id=25166,
    item_icon_id=25167,
    profile_file_name="profile_gyomei.png",
    item_icon_file_name="avatar_item.png",
    description="Cải trang thành Gyomei Himejima",
    body_image_ids_override=(
        25149, 25150, 25151, 25152, 25153, 25154, 25155, 26004, 25157,
        25158, 25159, 25160, 25161, 25162, 25163, 25164, 25165,
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
        BodyFrameConfig("guard", 2, "Chặn đòn bằng khiên khí xanh", "Đỡ / chịu đòn"),
        BodyFrameConfig("idle", 5, "Đứng nghỉ cầm chùy và rìu, đầu nghiêng nhẹ", "Đứng yên đã căn đất và bóng", (9, 10)),
        BodyFrameConfig("fly", 7, "Bay ngang sang phải, áo choàng kéo về sau", "Bay / nhảy"),
        BodyFrameConfig("run", 0, "Chạy bước thấp với chùy phía sau", "Chạy 1"),
        BodyFrameConfig("run", 1, "Chạy chuyển bước", "Chạy 2"),
        BodyFrameConfig("run", 4, "Chạy bước mở rộng", "Chạy 3"),
        BodyFrameConfig("run", 6, "Chạy sải chân", "Chạy 4"),
        BodyFrameConfig("fly", 7, "Bay ngang sang phải, áo choàng kéo về sau tại slot bay thực tế", "Bay thực tế BODY[7]"),
        BodyFrameConfig("attack2", 5, "Nện đất bùng đá vàng", "Tụ lực / kỹ năng"),
        BodyFrameConfig("attack2", 6, "Quét rìu tạo vòng khí vàng", "Kỹ năng"),
        BodyFrameConfig("attack", 1, "Vung chùy tạo cung vàng", "Đánh / kỹ năng"),
        BodyFrameConfig("attack", 5, "Quét xích tạo vòng khí xám", "Đánh / kỹ năng"),
        BodyFrameConfig("attack", 6, "Bổ chùy từ trên cao kèm cung vàng", "Đánh / kỹ năng"),
        BodyFrameConfig("fall", 2, "Bị đánh văng ra sau trên không", "Ngã / đánh văng"),
        BodyFrameConfig("attack2", 9, "Tụ lực giữa hào quang vàng", "Kỹ năng hiệu ứng lớn"),
        BodyFrameConfig("guard", 3, "Giữ khiên khí xanh sát thân", "Phòng thủ"),
        BodyFrameConfig("fly", 5, "Xoay rìu và xích thành vòng khi lao tới", "Motion blur / dash"),
    ),
)


if __name__ == "__main__":
    run(CONFIG)
