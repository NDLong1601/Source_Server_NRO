from pathlib import Path
import sys

PROJECT_DIR = Path(__file__).resolve().parent
REPOSITORY_ROOT = PROJECT_DIR.parents[2]
sys.path.insert(0, str(REPOSITORY_ROOT / "costume-tools"))

from full_body_hd_builder import BodyFrameConfig, CostumeConfig, SheetConfig, run


CONFIG = CostumeConfig(
    project_dir=PROJECT_DIR,
    repository_root=REPOSITORY_ROOT,
    name="Cải trang Tengen Uzui",
    slug="tengen-uzui",
    item_id=2067,
    part_ids=(2150, 2151, 2152),
    profile_icon_id=26015,
    first_body_image_id=25129,
    transparent_image_id=25146,
    item_icon_id=25147,
    profile_file_name="profile_Tengen.png",
    item_icon_file_name="avatar_item.png",
    description="Cải trang thành Tengen Uzui",
    body_image_ids_override=(
        25129, 25219, 25220, 25132, 25133, 25134, 25135, 26009, 25137,
        25138, 25139, 25140, 25141, 25221, 25143, 25144, 25145,
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
        BodyFrameConfig("guard", 2, "Bắt chéo song đao và xích trước thân", "Đỡ / chịu đòn"),
        BodyFrameConfig("idle", 1, "Đứng nghỉ đã lật ngang để hướng trái/phải khớp client", "Đứng yên đã sửa hướng", (4, 2), True),
        BodyFrameConfig("fly", 6, "Lao bay ngang sang phải kèm vệt hỏa âm; phát hành bằng image ID mới để bỏ cache cũ", "Bay / nhảy đã sửa"),
        BodyFrameConfig("run", 0, "Hạ thấp trọng tâm, bụi đá bắn sau chân", "Chạy hiệu ứng 1"),
        BodyFrameConfig("run", 1, "Lao tới cùng cung đao và bụi", "Chạy hiệu ứng 2"),
        BodyFrameConfig("run", 4, "Trượt thấp tạo vòng bụi", "Chạy hiệu ứng 3"),
        BodyFrameConfig("run", 5, "Tiếp đất thấp cùng bụi", "Chạy hiệu ứng 4"),
        BodyFrameConfig("fly", 6, "Lao bay ngang sang phải, chân kéo vệt hỏa âm", "Bay thực tế BODY[7]"),
        BodyFrameConfig("attack", 5, "Tụ lực trong vòng cung vàng", "Tụ lực / kỹ năng"),
        BodyFrameConfig("attack2", 5, "Quét song đao tạo vòng hỏa âm thấp", "Kỹ năng"),
        BodyFrameConfig("attack2", 3, "Xoay người trong vòng hỏa âm", "Đánh / kỹ năng"),
        BodyFrameConfig("attack2", 9, "Kết thúc đòn, giơ tay giữ nhịp", "Đánh / kỹ năng"),
        BodyFrameConfig("attack", 5, "Chém xoay trong vòng cung vàng", "Đánh / kỹ năng"),
        BodyFrameConfig("fall", 0, "Rơi trên không với dáng duỗi toàn thân cùng tỉ lệ khung đứng", "Rơi / nhảy xuống đã cân tỉ lệ", (0, 0), True),
        BodyFrameConfig("fly", 2, "Xoay song đao trong vòng hỏa âm tròn", "Kỹ năng hiệu ứng lớn"),
        BodyFrameConfig("guard", 0, "Hạ tấn chặn đòn bằng song đao", "Phòng thủ"),
        BodyFrameConfig("run", 1, "Lao nhanh kéo theo cung đao và bụi", "Motion blur / dash"),
    ),
)


if __name__ == "__main__":
    run(CONFIG)
