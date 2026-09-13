# Cải trang Douma

Cải trang full-body HD; HEAD và LEG dùng ảnh trong suốt vì tóc, vũ khí và hiệu ứng đi xuyên ranh giới part.
Mỗi scale x1-x4 được render trực tiếp từ ảnh nguồn bằng Lanczos, có viền alpha an toàn và không phóng từ x1.

- Item: `2072`; parts HEAD/BODY/LEG: `2165/2166/2167`.
- Icon hành trang: `26057`; avatar profile: `26058`.
- Shop Kanao: shop `112`, tab `1120`, giá `2000`.
- Không thêm option mặc định.

## DATA

```json
HEAD = [[26056,0,0],[26056,0,0],[26056,0,0]]
BODY = [[26039,-20,-42],[26097,-14,-45],[26098,-19,-42],[26042,-14,-24],[26043,-16,-39],[26044,-16,-39],[26045,-14,-46],[26099,-19,-42],[26047,-8,-34],[26048,-12,-32],[26049,-6,-38],[26050,-10,-41],[26051,-6,-38],[26052,-18,-34],[26053,-12,-32],[26054,-15,-41],[26055,-14,-46]]
LEG  = [[26056,0,0],[26056,0,0],[26056,0,0],[26056,0,0],[26056,0,0],[26056,0,0],[26056,0,0],[26056,0,0],[26056,0,0],[26056,0,0],[26056,0,0],[26056,0,0],[26056,0,0],[26056,0,0]]
```

## Mapping BODY

| Slot | imageId | Nguồn | Quan sát | Nhóm |
|---:|---:|---|---|---|
| 0 | 26039 | `guard[2]` | Đỡ đòn giữa khiên băng xanh lam | Đỡ / chịu đòn |
| 1 | 26097 | `idle[0]` | Đứng nghỉ góc 3/4 hướng phải, mặt đổi hướng rõ khi client mirror | Đứng yên 3/4 |
| 2 | 26098 | `fly[5]` | Bay ngang đã mirror sang hướng phải để khớp chiều di chuyển | Bay / nhảy |
| 3 | 26042 | `run[0]` | Lướt bước 1 với hiệu ứng băng | Chạy 1 ưu tiên walk_1 |
| 4 | 26043 | `run[4]` | Lướt bước 2 với hiệu ứng băng, frame sạch | Chạy 2 ưu tiên walk_1 |
| 5 | 26044 | `run[4]` | Chuyển bước thấp, áo kéo sau | Chạy 3 ưu tiên walk_1 |
| 6 | 26045 | `run[7]` | Lướt nhanh trong băng vụ quanh chân | Chạy hiệu ứng 4 ưu tiên walk_1 |
| 7 | 26099 | `fly[5]` | Bay ngang đã mirror sang hướng phải tại slot bay thực tế BODY[7] | Bay thực tế BODY[7] |
| 8 | 26047 | `attack2[0]` | Quạt băng tạo vòng tuyết quanh thân | Tụ lực / kỹ năng ưu tiên attack_1 |
| 9 | 26048 | `attack2[5]` | Tạo băng vụ lớn quanh thân | Kỹ năng ưu tiên attack_1 |
| 10 | 26049 | `attack[0]` | Quét quạt băng ngang | Đánh / kỹ năng |
| 11 | 26050 | `attack2[8]` | Quạt băng xoay tròn quanh thân | Đánh / kỹ năng ưu tiên attack_1 |
| 12 | 26051 | `attack2[9]` | Đứng gọi cánh hoa băng | Đánh / kỹ năng ưu tiên attack_1 |
| 13 | 26052 | `fall[3]` | Bị đánh văng/ngã về sau, frame sạch | Ngã / đánh văng |
| 14 | 26053 | `attack2[5]` | Tụ băng lớn quanh người | Kỹ năng hiệu ứng lớn ưu tiên attack_1 |
| 15 | 26054 | `guard[4]` | Phòng thủ cúi thấp trong băng vụ | Phòng thủ |
| 16 | 26055 | `run[7]` | Lướt nhanh trong băng vụ quanh chân | Motion blur / dash ưu tiên walk_1 |

## Kiểm soát chất lượng

- `preview/contact-*.png`: toàn bộ ô sau khi dò ranh giới alpha; viền đỏ báo frame chạm mép ô để rà thủ công.
- `preview/selected-body-frames.png`: 17 frame xuất thật trên nền caro tối.
- `qa-report.json`: ranh giới grid, component giữ/bỏ, crop, offset và kiểm tra viền alpha từng scale.
- `install.sql` / `rollback.sql`: cài hoặc gỡ đúng riêng bộ cải trang này.
