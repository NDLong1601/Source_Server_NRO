# Cải trang Tomioka Giyuu

Cải trang full-body HD; HEAD và LEG dùng ảnh trong suốt vì tóc, vũ khí và hiệu ứng đi xuyên ranh giới part.
Mỗi scale x1-x4 được render trực tiếp từ ảnh nguồn bằng Lanczos, có viền alpha an toàn và không phóng từ x1.

- Item: `2064`; parts HEAD/BODY/LEG: `2141/2142/2143`.
- Icon hành trang: `25082`; avatar profile: `26012`.
- Shop Kanao: shop `112`, tab `1120`, giá `2000`.
- Không thêm option mặc định.

## DATA

```json
HEAD = [[25081,0,0],[25081,0,0],[25081,0,0]]
BODY = [[25064,-22,-38],[25123,-4,-35],[25124,-15,-28],[25067,-13,-20],[25068,-17,-28],[25069,-16,-30],[25070,-12,-37],[26008,-16,-26],[25072,-11,-36],[25073,-13,-36],[25074,-11,-23],[25075,-12,-29],[25076,-7,-28],[25077,-16,-32],[25078,-18,-36],[25079,-16,-37],[25080,-16,-18]]
LEG  = [[25081,0,0],[25081,0,0],[25081,0,0],[25081,0,0],[25081,0,0],[25081,0,0],[25081,0,0],[25081,0,0],[25081,0,0],[25081,0,0],[25081,0,0],[25081,0,0],[25081,0,0],[25081,0,0]]
```

## Mapping BODY

| Slot | imageId | Nguồn | Quan sát | Nhóm |
|---:|---:|---|---|---|
| 0 | 25064 | `guard[2]` | Đỡ kiếm trong khiên nước | Đỡ / chịu đòn |
| 1 | 25123 | `idle[2]` | Đứng nghỉ hướng phải, đầu góc 3/4 | Đứng yên đã sửa góc đầu |
| 2 | 25124 | `fly[2]` | Bay ngang sang phải trong vòng nước | Bay / nhảy đã sửa |
| 3 | 25067 | `run[0]` | Hạ thấp trọng tâm kèm bụi | Chạy hiệu ứng 1 |
| 4 | 25068 | `run[1]` | Lao tới trong vệt nước | Chạy hiệu ứng 2 |
| 5 | 25069 | `run[5]` | Tiếp đất thấp kèm bụi | Chạy hiệu ứng 3 |
| 6 | 25070 | `run[6]` | Lướt thấp trong sóng nước | Chạy hiệu ứng 4 |
| 7 | 26008 | `fly[3]` | Lao bay ngang sang phải, nước kéo dài phía sau | Bay thực tế BODY[7] |
| 8 | 25072 | `attack2[2]` | Tụ thủy quanh kiếm dựng dọc | Tụ lực / kỹ năng |
| 9 | 25073 | `attack2[5]` | Chém tạo vòng sóng tròn | Kỹ năng |
| 10 | 25074 | `attack[3]` | Chém ngang tạo sóng nước | Đánh / kỹ năng |
| 11 | 25075 | `attack[4]` | Chém chéo tạo thác nước | Đánh / kỹ năng |
| 12 | 25076 | `attack[7]` | Bổ kiếm tạo cột nước | Đánh / kỹ năng |
| 13 | 25077 | `fall[3]` | Bị đánh văng trên không | Ngã / đánh văng |
| 14 | 25078 | `attack2[6]` | Triệu hồi thủy long lớn | Kỹ năng hiệu ứng lớn |
| 15 | 25079 | `guard[3]` | Chặn đòn kèm nước bắn | Phòng thủ |
| 16 | 25080 | `run[3]` | Lướt cực nhanh kèm dư ảnh nước | Motion blur / dash |

## Kiểm soát chất lượng

- `preview/contact-*.png`: toàn bộ ô sau khi dò ranh giới alpha; viền đỏ báo frame chạm mép ô để rà thủ công.
- `preview/selected-body-frames.png`: 17 frame xuất thật trên nền caro tối.
- `qa-report.json`: ranh giới grid, component giữ/bỏ, crop, offset và kiểm tra viền alpha từng scale.
- `install.sql` / `rollback.sql`: cài hoặc gỡ đúng riêng bộ cải trang này.
