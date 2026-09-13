# Cải trang Kokushibo

Cải trang full-body HD; HEAD và LEG dùng ảnh trong suốt vì tóc, vũ khí và hiệu ứng đi xuyên ranh giới part.
Mỗi scale x1-x4 được render trực tiếp từ ảnh nguồn bằng Lanczos, có viền alpha an toàn và không phóng từ x1.

- Item: `2073`; parts HEAD/BODY/LEG: `2168/2169/2170`.
- Icon hành trang: `26077`; avatar profile: `26078`.
- Shop Kanao: shop `112`, tab `1120`, giá `2000`.
- Không thêm option mặc định.

## DATA

```json
HEAD = [[26076,0,0],[26076,0,0],[26076,0,0]]
BODY = [[26059,-13,-42],[26060,-16,-37],[26061,-6,-27],[26062,-8,-29],[26063,-8,-27],[26064,-8,-41],[26065,-8,-40],[26066,-6,-27],[26089,-11,-22],[26090,-13,-38],[26069,-3,-25],[26070,-11,-33],[26091,-11,-37],[26100,-14,-40],[26092,-14,-40],[26074,-14,-38],[26075,-8,-40]]
LEG  = [[26076,0,0],[26076,0,0],[26076,0,0],[26076,0,0],[26076,0,0],[26076,0,0],[26076,0,0],[26076,0,0],[26076,0,0],[26076,0,0],[26076,0,0],[26076,0,0],[26076,0,0],[26076,0,0]]
```

## Mapping BODY

| Slot | imageId | Nguồn | Quan sát | Nhóm |
|---:|---:|---|---|---|
| 0 | 26059 | `guard[3]` | Đỡ đòn với khiên trăng tím bao quanh thân | Đỡ / chịu đòn |
| 1 | 26060 | `idle[2]` | Đứng nghỉ cầm kiếm, hiệu ứng tím nhẹ | Đứng yên |
| 2 | 26061 | `fly[0]` | Bay ngang với vệt trăng tím | Bay / nhảy |
| 3 | 26062 | `walk[0]` | Chạy/lướt bước 1 | Chạy 1 |
| 4 | 26063 | `walk[1]` | Chạy/lướt bước 2 | Chạy 2 |
| 5 | 26064 | `walk[4]` | Chạy/lướt bước 3 | Chạy 3 |
| 6 | 26065 | `walk[5]` | Lướt nhanh kèm trăng tím | Chạy hiệu ứng 4 |
| 7 | 26066 | `fly[0]` | Bay ngang với vệt trăng tím tại slot bay thực tế | Bay thực tế BODY[7] |
| 8 | 26089 | `attack2[1]` | Chém vòng trăng tím | Tụ lực / kỹ năng ưu tiên attack_1 |
| 9 | 26090 | `attack2[4]` | Tụ kiếm với trăng tím lớn, cắt lại từ lưới 4x2 đúng | Kỹ năng ưu tiên attack_1 |
| 10 | 26069 | `attack[1]` | Chém ngang tạo cung tím | Đánh / kỹ năng |
| 11 | 26070 | `attack[5]` | Lao chém thấp tạo cung tím | Đánh / kỹ năng |
| 12 | 26091 | `attack2[5]` | Tụ trăng tím quanh thân, giữ đủ vòng sáng | Đánh / kỹ năng ưu tiên attack_1 |
| 13 | 26100 | `attack2[6]` | Đáp đất/tụ lực với hiệu ứng tím lớn quanh thân và dưới chân | Đáp đất có hiệu ứng ưu tiên attack_1 |
| 14 | 26092 | `attack2[6]` | Tụ trăng tím lớn quanh thân, giữ đủ luồng tím bên phải | Kỹ năng hiệu ứng lớn ưu tiên attack_1 |
| 15 | 26074 | `guard[4]` | Phòng thủ cầm kiếm thấp, frame sạch | Phòng thủ |
| 16 | 26075 | `walk[5]` | Lướt nhanh kèm trăng tím | Motion blur / dash |

## Kiểm soát chất lượng

- `preview/contact-*.png`: toàn bộ ô sau khi dò ranh giới alpha; viền đỏ báo frame chạm mép ô để rà thủ công.
- `preview/selected-body-frames.png`: 17 frame xuất thật trên nền caro tối.
- `qa-report.json`: ranh giới grid, component giữ/bỏ, crop, offset và kiểm tra viền alpha từng scale.
- `install.sql` / `rollback.sql`: cài hoặc gỡ đúng riêng bộ cải trang này.
