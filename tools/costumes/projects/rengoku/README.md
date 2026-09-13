# Cải trang Rengoku

Cải trang full-body HD; HEAD và LEG dùng ảnh trong suốt vì tóc, vũ khí và hiệu ứng đi xuyên ranh giới part.
Mỗi scale x1-x4 được render trực tiếp từ ảnh nguồn bằng Lanczos, có viền alpha an toàn và không phóng từ x1.

- Item: `2066`; parts HEAD/BODY/LEG: `2147/2148/2149`.
- Icon hành trang: `25122`; avatar profile: `26014`.
- Shop Kanao: shop `112`, tab `1120`, giá `2000`.
- Không thêm option mặc định.

## DATA

```json
HEAD = [[25121,0,0],[25121,0,0],[25121,0,0]]
BODY = [[25104,-24,-35],[25126,-9,-36],[25127,-15,-33],[25107,-14,-24],[25108,-17,-34],[25109,-18,-31],[25110,-16,-38],[26002,-15,-33],[25112,-12,-32],[25113,-16,-43],[25114,-12,-20],[25115,-9,-33],[25116,-10,-23],[25117,-15,-30],[25118,-15,-37],[25119,-21,-39],[25120,-17,-23]]
LEG  = [[25121,0,0],[25121,0,0],[25121,0,0],[25121,0,0],[25121,0,0],[25121,0,0],[25121,0,0],[25121,0,0],[25121,0,0],[25121,0,0],[25121,0,0],[25121,0,0],[25121,0,0],[25121,0,0]]
```

## Mapping BODY

| Slot | imageId | Nguồn | Quan sát | Nhóm |
|---:|---:|---|---|---|
| 0 | 25104 | `guard[2]` | Đỡ kiếm trong khiên lửa | Đỡ / chịu đòn |
| 1 | 25126 | `idle[2]` | Đứng nghỉ hướng phải, áo choàng kéo về sau | Đứng yên đã sửa hướng |
| 2 | 25127 | `fly[5]` | Lao bay sang phải trong vòng hỏa diệm | Bay / nhảy đã sửa |
| 3 | 25107 | `run[0]` | Hạ thấp trọng tâm kèm bụi lửa | Chạy hiệu ứng 1 |
| 4 | 25108 | `run[1]` | Lao tới trong vệt lửa | Chạy hiệu ứng 2 |
| 5 | 25109 | `run[4]` | Tiếp đất cùng đốm lửa | Chạy hiệu ứng 3 |
| 6 | 25110 | `run[6]` | Lướt thấp kéo đuôi lửa | Chạy hiệu ứng 4 |
| 7 | 26002 | `fly[5]` | Lao bay sang phải trong vòng hỏa diệm tại slot bay thực tế | Bay thực tế BODY[7] |
| 8 | 25112 | `attack[2]` | Gầm tụ hỏa quanh thân | Tụ lực / kỹ năng |
| 9 | 25113 | `attack[6]` | Triệu hồi sư tử lửa lớn | Kỹ năng hiệu ứng lớn |
| 10 | 25114 | `attack2[3]` | Chém ngang tạo lưỡi lửa | Đánh / kỹ năng |
| 11 | 25115 | `attack2[4]` | Bổ kiếm tạo thác lửa | Đánh / kỹ năng |
| 12 | 25116 | `attack2[6]` | Chém bùng hỏa diệm quanh thân | Đánh / kỹ năng |
| 13 | 25117 | `fall[3]` | Bị đánh văng trên không | Ngã / đánh văng |
| 14 | 25118 | `attack[5]` | Xoay kiếm trong vòng hỏa diệm | Kỹ năng |
| 15 | 25119 | `guard[3]` | Chặn đòn kèm tia lửa | Phòng thủ |
| 16 | 25120 | `run[3]` | Lướt cực nhanh kèm dư ảnh lửa | Motion blur / dash |

## Kiểm soát chất lượng

- `preview/contact-*.png`: toàn bộ ô sau khi dò ranh giới alpha; viền đỏ báo frame chạm mép ô để rà thủ công.
- `preview/selected-body-frames.png`: 17 frame xuất thật trên nền caro tối.
- `qa-report.json`: ranh giới grid, component giữ/bỏ, crop, offset và kiểm tra viền alpha từng scale.
- `install.sql` / `rollback.sql`: cài hoặc gỡ đúng riêng bộ cải trang này.
