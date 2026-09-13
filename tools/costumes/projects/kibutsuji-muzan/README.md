# Cải trang Kibutsuji Muzan

Cải trang full-body HD; HEAD và LEG dùng ảnh trong suốt vì tóc, vũ khí và hiệu ứng đi xuyên ranh giới part.
Mỗi scale x1-x4 được render trực tiếp từ ảnh nguồn bằng Lanczos, có viền alpha an toàn và không phóng từ x1.

- Item: `2075`; parts HEAD/BODY/LEG: `2174/2175/2176`.
- Icon hành trang: `26139`; avatar profile: `26140`.
- Shop Kanao: shop `112`, tab `1120`, giá `2000`.
- Không thêm option mặc định.

## DATA

```json
HEAD = [[26138,0,0],[26138,0,0],[26138,0,0]]
BODY = [[26121,-23,-46],[26167,-19,-44],[26168,-18,-45],[26124,-18,-29],[26125,-19,-43],[26169,-14,-41],[26127,-19,-47],[26170,-18,-45],[26129,-19,-43],[26130,-13,-43],[26131,-14,-42],[26132,-13,-39],[26133,-13,-43],[26134,-12,-43],[26135,-18,-46],[26136,-18,-43],[26171,-19,-43]]
LEG  = [[26138,0,0],[26138,0,0],[26138,0,0],[26138,0,0],[26138,0,0],[26138,0,0],[26138,0,0],[26138,0,0],[26138,0,0],[26138,0,0],[26138,0,0],[26138,0,0],[26138,0,0],[26138,0,0]]
```

## Mapping BODY

| Slot | imageId | Nguồn | Quan sát | Nhóm |
|---:|---:|---|---|---|
| 0 | 26121 | `guard[4]` | Dựng khiên máu đỏ, thân đủ và hiệu ứng nổi rõ | Đỡ / chịu đòn |
| 1 | 26167 | `idle[0]` | Đứng nghỉ mới, mặt rõ hơn và không dùng idle cũ | Đứng yên thay thế |
| 2 | 26168 | `fly[4]` | Bay ngang sạch biên, áo choàng kéo sau; thay frame fly[3] bị chạm mép | Bay / nhảy |
| 3 | 26124 | `run[0]` | Chống tay chuẩn bị lướt, có bụi chân | Chạy 1 ưu tiên walk_1 |
| 4 | 26125 | `run[4]` | Lướt thấp trong vệt máu đỏ | Chạy 2 ưu tiên walk_1 |
| 5 | 26169 | `walk[4]` | Sải chạy sạch biên, thay frame walk_1 bị cắt mép | Chạy 3 sạch biên |
| 6 | 26127 | `run[7]` | Đứng lại sau pha lướt, còn hiệu ứng đỏ | Chạy 4 ưu tiên walk_1 |
| 7 | 26170 | `fly[4]` | Bay ngang sạch biên tại slot bay thực tế, thay fly[3] bị chạm mép | Bay thực tế BODY[7] |
| 8 | 26129 | `attack2[0]` | Tụ cầu máu trước ngực, hiệu ứng đỏ quanh thân | Tụ lực / kỹ năng ưu tiên attack_1 |
| 9 | 26130 | `attack2[1]` | Giơ cầu máu, giữ đủ luồng đỏ quanh tay | Kỹ năng ưu tiên attack_1 |
| 10 | 26131 | `attack[4]` | Xoay chém vòng đỏ lớn | Đánh / kỹ năng |
| 11 | 26132 | `attack[6]` | Lao tới với vệt máu ngang | Đánh / kỹ năng |
| 12 | 26133 | `attack2[3]` | Chém vòng máu quanh thân | Đánh / kỹ năng ưu tiên attack_1 |
| 13 | 26134 | `attack[5]` | Đập đất tạo xung đỏ, dùng làm đáp đất | Đáp đất có hiệu ứng |
| 14 | 26135 | `attack2[4]` | Bùng aura đỏ phía sau, kỹ năng lớn | Kỹ năng hiệu ứng lớn ưu tiên attack_1 |
| 15 | 26136 | `guard[2]` | Thủ thế nghiêng, áo choàng che phía sau | Phòng thủ |
| 16 | 26171 | `run[4]` | Dash/chạy phụ có vệt đỏ sạch biên, thay frame run[3] bị cắt mép | Motion blur / dash sạch biên |

## Kiểm soát chất lượng

- `preview/contact-*.png`: toàn bộ ô sau khi dò ranh giới alpha; viền đỏ báo frame chạm mép ô để rà thủ công.
- `preview/selected-body-frames.png`: 17 frame xuất thật trên nền caro tối.
- `qa-report.json`: ranh giới grid, component giữ/bỏ, crop, offset và kiểm tra viền alpha từng scale.
- `install.sql` / `rollback.sql`: cài hoặc gỡ đúng riêng bộ cải trang này.
