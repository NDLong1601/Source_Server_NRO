# Cải trang Muzan Demon

Cải trang full-body HD; HEAD và LEG dùng ảnh trong suốt vì tóc, vũ khí và hiệu ứng đi xuyên ranh giới part.
Mỗi scale x1-x4 được render trực tiếp từ ảnh nguồn bằng Lanczos, có viền alpha an toàn và không phóng từ x1.

- Item: `2076`; parts HEAD/BODY/LEG: `2177/2178/2179`.
- Icon hành trang: `26159`; avatar profile: `26160`.
- Shop Kanao: shop `112`, tab `1120`, giá `2000`.
- Không thêm option mặc định.

## DATA

```json
HEAD = [[26158,0,0],[26158,0,0],[26158,0,0]]
BODY = [[26141,-18,-44],[26172,-19,-44],[26143,-17,-39],[26144,-18,-36],[26145,-18,-36],[26146,-21,-34],[26147,-13,-42],[26148,-17,-39],[26149,-17,-39],[26150,-19,-45],[26151,-19,-41],[26152,-17,-45],[26153,-17,-39],[26154,-18,-36],[26155,-19,-45],[26156,-18,-44],[26157,-21,-34]]
LEG  = [[26158,0,0],[26158,0,0],[26158,0,0],[26158,0,0],[26158,0,0],[26158,0,0],[26158,0,0],[26158,0,0],[26158,0,0],[26158,0,0],[26158,0,0],[26158,0,0],[26158,0,0],[26158,0,0]]
```

## Mapping BODY

| Slot | imageId | Nguồn | Quan sát | Nhóm |
|---:|---:|---|---|---|
| 0 | 26141 | `guard[1]` | Khoanh tay phòng thủ, tua lưỡi hái bao quanh | Đỡ / chịu đòn |
| 1 | 26172 | `idle[2]` | Đứng nghỉ dữ dằn đã lật đúng hướng, mặt lệch nhẹ để client mirror đổi hướng | Đứng yên |
| 2 | 26143 | `fly[2]` | Lao bay ngang hướng phải, lấy từ lưới 3x2 để tránh mảnh rời | Bay / nhảy |
| 3 | 26144 | `run[0]` | Bò thấp chuẩn bị lao, có bụi chân | Chạy 1 ưu tiên walk_1 |
| 4 | 26145 | `run[1]` | Lao thấp với vệt đỏ quanh chân, lấy từ lưới 3x2 | Chạy 2 ưu tiên walk_1 |
| 5 | 26146 | `run[2]` | Lao ngang hướng phải, vệt đỏ phía sau còn nguyên | Chạy 3 ưu tiên walk_1 |
| 6 | 26147 | `walk[7]` | Chạy ổn định, dùng thay frame walk_1 bị sát biên | Chạy 4 |
| 7 | 26148 | `fly[2]` | Lao bay ngang hướng phải tại slot bay thực tế, thân đầy đủ | Bay thực tế BODY[7] |
| 8 | 26149 | `attack2[0]` | Tụ aura đỏ quanh thân | Tụ lực / kỹ năng ưu tiên attack_1 |
| 9 | 26150 | `attack2[5]` | Đứng trong aura đỏ trọn vẹn, thay frame bị cắt | Kỹ năng ưu tiên attack_1 |
| 10 | 26151 | `attack[0]` | Thủ thế có lưỡi hái bao quanh, không cắt hiệu ứng | Đánh / kỹ năng |
| 11 | 26152 | `attack[5]` | Đứng tấn có hiệu ứng quanh thân, không cắt mép | Đánh / kỹ năng |
| 12 | 26153 | `attack2[0]` | Tụ aura đỏ quanh thân, dùng thay vòng lưỡi hái bị cắt | Đánh / kỹ năng ưu tiên attack_1 |
| 13 | 26154 | `run[0]` | Chạm đất có bụi và hiệu ứng đỏ, dùng làm đáp đất sạch biên | Đáp đất có hiệu ứng |
| 14 | 26155 | `attack2[5]` | Đứng trong aura đỏ trọn vẹn, kỹ năng lớn | Kỹ năng hiệu ứng lớn ưu tiên attack_1 |
| 15 | 26156 | `guard[2]` | Phòng thủ cuộn kín bằng tua lưỡi hái | Phòng thủ |
| 16 | 26157 | `run[2]` | Lao tốc độ với vệt đỏ trắng phía sau, không mất hiệu ứng | Motion blur / dash ưu tiên walk_1 |

## Kiểm soát chất lượng

- `preview/contact-*.png`: toàn bộ ô sau khi dò ranh giới alpha; viền đỏ báo frame chạm mép ô để rà thủ công.
- `preview/selected-body-frames.png`: 17 frame xuất thật trên nền caro tối.
- `qa-report.json`: ranh giới grid, component giữ/bỏ, crop, offset và kiểm tra viền alpha từng scale.
- `install.sql` / `rollback.sql`: cài hoặc gỡ đúng riêng bộ cải trang này.
