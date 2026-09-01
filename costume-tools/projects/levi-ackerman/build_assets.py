from __future__ import annotations

import sys
from pathlib import Path

PROJECT_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = Path('C:\\Users\\PC\\Music\\Teamobi2026\\SRC')
sys.path.insert(0, str(PROJECT_ROOT / "costume-tools"))

from full_body_hd_builder import BodyFrameConfig, CostumeConfig, SheetConfig, run


SHEETS = (
    SheetConfig(key='guard', file_name='guard.png', columns=2, rows=2),
    SheetConfig(key='idle', file_name='idle.png', columns=1, rows=1),
    SheetConfig(key='air', file_name='air.png', columns=2, rows=2),
    SheetConfig(key='run', file_name='run.png', columns=3, rows=2),
    SheetConfig(key='skill', file_name='skill.png', columns=3, rows=2),
)

BODY_MAPPING = (
    BodyFrameConfig(sheet='guard', source_index=0, observed='Guard, hit, or compact recoil', group='Đỡ / chịu đòn', offset_adjustment=(0, 0), horizontal_flip=False),
    BodyFrameConfig(sheet='idle', source_index=0, observed='Neutral side-facing standing pose; feet lowered to the verified NRO ground baseline', group='Đứng yên đã căn lại mặt đất', offset_adjustment=(1, 5), horizontal_flip=False),
    BodyFrameConfig(sheet='air', source_index=0, observed='Airborne, jump, or recoil', group='Bay / nhảy', offset_adjustment=(0, -3), horizontal_flip=False),
    BodyFrameConfig(sheet='run', source_index=0, observed='Movement beat 1', group='Chạy 1', offset_adjustment=(-7, 2), horizontal_flip=False),
    BodyFrameConfig(sheet='run', source_index=1, observed='Movement beat 2', group='Chạy 2', offset_adjustment=(-7, 4), horizontal_flip=False),
    BodyFrameConfig(sheet='run', source_index=2, observed='Movement beat 3', group='Chạy 3', offset_adjustment=(-2, 4), horizontal_flip=False),
    BodyFrameConfig(sheet='run', source_index=3, observed='Movement beat 4', group='Chạy 4', offset_adjustment=(-2, 1), horizontal_flip=False),
    BodyFrameConfig(sheet='air', source_index=1, observed='Horizontal flight; confirm in the client', group='Bay thực tế BODY[7] cần xác nhận', offset_adjustment=(-1, 1), horizontal_flip=False),
    BodyFrameConfig(sheet='skill', source_index=0, observed='Charge, landing, or skill preparation', group='Tụ lực / đáp đất', offset_adjustment=(-4, 2), horizontal_flip=False),
    BodyFrameConfig(sheet='skill', source_index=1, observed='Skill pose', group='Kỹ năng', offset_adjustment=(-4, 2), horizontal_flip=False),
    BodyFrameConfig(sheet='skill', source_index=2, observed='Attack or skill pose 1', group='Đánh / kỹ năng', offset_adjustment=(-1, 1), horizontal_flip=False),
    BodyFrameConfig(sheet='skill', source_index=3, observed='Attack or skill pose 2', group='Đánh / kỹ năng', offset_adjustment=(-7, 0), horizontal_flip=False),
    BodyFrameConfig(sheet='skill', source_index=4, observed='Attack or skill pose 3', group='Đánh / kỹ năng', offset_adjustment=(-4, 2), horizontal_flip=False),
    BodyFrameConfig(sheet='air', source_index=2, observed='Fall, knockback, or alternate flight', group='Ngã / bay thay thế', offset_adjustment=(0, 7), horizontal_flip=False),
    BodyFrameConfig(sheet='skill', source_index=5, observed='Large skill or finisher', group='Kỹ năng lớn', offset_adjustment=(0, -5), horizontal_flip=False),
    BodyFrameConfig(sheet='guard', source_index=1, observed='Defensive impact or block', group='Phòng thủ', offset_adjustment=(-3, -1), horizontal_flip=False),
    BodyFrameConfig(sheet='run', source_index=4, observed='Dash or motion blur', group='Motion blur / dash', offset_adjustment=(-2, 5), horizontal_flip=False),
)

CONFIG = CostumeConfig(
    project_dir=PROJECT_DIR,
    repository_root=PROJECT_ROOT,
    name='Cải trang Levi Ackerman',
    slug='levi-ackerman',
    item_id=2247,
    part_ids=(2198, 2199, 2200),
    profile_icon_id=26574,
    first_body_image_id=26555,
    transparent_image_id=26572,
    item_icon_id=26573,
    profile_file_name="profile-user.png",
    item_icon_file_name="item-icon-user.png",
    sheets=SHEETS,
    body_mapping=BODY_MAPPING,
    description='Cải trang Levi Ackerman theo ảnh tham chiếu',
    source_directory='source-v2-chibi',
    shop_id=None,
    tab_id=None,
    shop_cost=None,
)


if __name__ == "__main__":
    run(CONFIG)
