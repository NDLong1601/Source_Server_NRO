# Cải trang Levi Ackerman

Cải trang full-body chibi theo tỷ lệ NRO; HEAD và LEG dùng ảnh trong suốt vì toàn bộ nhân vật nằm trong BODY.
Mỗi scale x1-x4 được render trực tiếp từ ảnh nguồn bằng Lanczos, có viền alpha an toàn và không phóng từ x1.

- Item: `2247`; parts HEAD/BODY/LEG: `2198/2199/2200`.
- Icon hành trang: `26573`; avatar profile: `26574`.
- Nơi nhận: Hộp quà Levi Ackerman ID `2248`; `install.sql` riêng của cải trang không thêm `item_shop`.
- Nguồn profile mới: `source-v2-chibi/profile-user.png`; nguồn icon vật phẩm mới: `source-v2-chibi/item-icon-user.png`.
- Frame đứng BODY[1] đã hạ `3 px` logic (`dy=-47`) để đáy bàn chân chạm baseline NRO `18`.
- Không thêm option mặc định.

## DATA

```json
HEAD = [[26572,0,0],[26572,0,0],[26572,0,0]]
BODY = [[26555,-9,-43],[26556,-7,-47],[26557,-11,-51],[26558,-10,-34],[26559,-17,-28],[26560,-18,-30],[26561,-9,-37],[26562,-27,-36],[26563,-12,-29],[26564,-13,-33],[26565,-9,-35],[26566,-16,-35],[26567,-17,-34],[26568,-14,-38],[26569,-19,-41],[26570,-18,-43],[26571,-18,-26]]
LEG  = [[26572,0,0],[26572,0,0],[26572,0,0],[26572,0,0],[26572,0,0],[26572,0,0],[26572,0,0],[26572,0,0],[26572,0,0],[26572,0,0],[26572,0,0],[26572,0,0],[26572,0,0],[26572,0,0]]
```

## Mapping BODY

| Slot | imageId | Nguồn | Quan sát | Nhóm |
|---:|---:|---|---|---|
| 0 | 26555 | `guard[0]` | Guard, hit, or compact recoil | Đỡ / chịu đòn |
| 1 | 26556 | `idle[0]` | Neutral side-facing standing pose; feet lowered to the verified NRO ground baseline | Đứng yên đã căn lại mặt đất |
| 2 | 26557 | `air[0]` | Airborne, jump, or recoil | Bay / nhảy |
| 3 | 26558 | `run[0]` | Movement beat 1 | Chạy 1 |
| 4 | 26559 | `run[1]` | Movement beat 2 | Chạy 2 |
| 5 | 26560 | `run[2]` | Movement beat 3 | Chạy 3 |
| 6 | 26561 | `run[3]` | Movement beat 4 | Chạy 4 |
| 7 | 26562 | `air[1]` | Horizontal flight; confirm in the client | Bay thực tế BODY[7] cần xác nhận |
| 8 | 26563 | `skill[0]` | Charge, landing, or skill preparation | Tụ lực / đáp đất |
| 9 | 26564 | `skill[1]` | Skill pose | Kỹ năng |
| 10 | 26565 | `skill[2]` | Attack or skill pose 1 | Đánh / kỹ năng |
| 11 | 26566 | `skill[3]` | Attack or skill pose 2 | Đánh / kỹ năng |
| 12 | 26567 | `skill[4]` | Attack or skill pose 3 | Đánh / kỹ năng |
| 13 | 26568 | `air[2]` | Fall, knockback, or alternate flight | Ngã / bay thay thế |
| 14 | 26569 | `skill[5]` | Large skill or finisher | Kỹ năng lớn |
| 15 | 26570 | `guard[1]` | Defensive impact or block | Phòng thủ |
| 16 | 26571 | `run[4]` | Dash or motion blur | Motion blur / dash |

## Kiểm soát chất lượng

- `preview/contact-*.png`: toàn bộ ô sau khi dò ranh giới alpha; viền đỏ báo frame chạm mép ô để rà thủ công.
- `preview/selected-body-frames.png`: 17 frame xuất thật trên nền caro tối.
- `qa-report.json`: ranh giới grid, component giữ/bỏ, crop, offset và kiểm tra viền alpha từng scale.
- `install.sql` / `rollback.sql`: cài hoặc gỡ đúng riêng bộ cải trang này.
