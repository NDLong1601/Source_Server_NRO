# Cải trang Akaza

Cải trang full-body HD; HEAD và LEG dùng ảnh trong suốt vì tóc, vũ khí và hiệu ứng đi xuyên ranh giới part.
Mỗi scale x1-x4 được render trực tiếp từ ảnh nguồn bằng Lanczos, có viền alpha an toàn và không phóng từ x1.

- Item: `2071`; parts HEAD/BODY/LEG: `2162/2163/2164`.
- Icon hành trang: `26037`; avatar profile: `26038`.
- Shop Kanao: shop `112`, tab `1120`, giá `2000`.
- Không thêm option mặc định.

## DATA

```json
HEAD = [[26036,0,0],[26036,0,0],[26036,0,0]]
BODY = [[26019,-12,-45],[26093,-10,-45],[26094,-16,-39],[26022,-11,-44],[26023,-12,-44],[26024,-14,-44],[26025,-10,-46],[26095,-16,-39],[26080,-15,-40],[26081,-21,-46],[26082,-14,-40],[26083,-11,-43],[26084,-20,-42],[26096,-16,-40],[26085,-18,-47],[26034,-12,-45],[26035,-16,-40]]
LEG  = [[26036,0,0],[26036,0,0],[26036,0,0],[26036,0,0],[26036,0,0],[26036,0,0],[26036,0,0],[26036,0,0],[26036,0,0],[26036,0,0],[26036,0,0],[26036,0,0],[26036,0,0],[26036,0,0]]
```

## Mapping BODY

| Slot | imageId | Nguồn | Quan sát | Nhóm |
|---:|---:|---|---|---|
| 0 | 26019 | `guard[4]` | Thủ thế hai tay trước mặt, không chạm mép sheet | Đỡ / chịu đòn |
| 1 | 26093 | `idle[2]` | Đứng nghỉ Akaza góc 3/4 hướng phải, mặt đổi hướng rõ khi client mirror | Đứng yên 3/4 |
| 2 | 26094 | `fly[3]` | Khôi phục dáng bay đầu tiên: lao nghiêng 3/4 hướng phải, không dùng frame chính diện | Bay / nhảy |
| 3 | 26022 | `walk[0]` | Chạy bước 1, biên trong suốt sạch | Chạy 1 |
| 4 | 26023 | `walk[1]` | Chạy bước 2, biên trong suốt sạch | Chạy 2 |
| 5 | 26024 | `walk[4]` | Chạy bước 3, biên trong suốt sạch | Chạy 3 |
| 6 | 26025 | `walk[5]` | Chạy bước 4, biên trong suốt sạch | Chạy 4 |
| 7 | 26095 | `fly[3]` | Khôi phục dáng bay đầu tiên tại slot bay thực tế BODY[7] | Bay thực tế BODY[7] |
| 8 | 26080 | `attack2[1]` | Tụ quyền trong vòng khí xanh, cắt lại từ lưới 3x2 đúng | Tụ lực / kỹ năng ưu tiên attack_1 |
| 9 | 26081 | `attack2[3]` | Đòn đánh vòng khí xanh lớn, giữ đủ vòng sáng | Kỹ năng ưu tiên attack_1 |
| 10 | 26082 | `attack[0]` | Đấm quét vòng khí xanh, cắt lại từ lưới 4x2 đúng | Đánh / kỹ năng |
| 11 | 26083 | `attack[5]` | Xoay người đánh vòng khí, thân đầy đủ và không mất hiệu ứng | Đánh / kỹ năng |
| 12 | 26084 | `attack2[4]` | Quyền kích với hiệu ứng xanh dày, giữ đủ vệt sáng | Đánh / kỹ năng ưu tiên attack_1 |
| 13 | 26096 | `run[7]` | Đáp đất chạm tay xuống nền kèm bụi, thay frame ngã không hiệu ứng | Đáp đất |
| 14 | 26085 | `attack2[5]` | Tụ lực trong vòng xanh lớn, cắt lại đủ thân và aura | Kỹ năng hiệu ứng lớn ưu tiên attack_1 |
| 15 | 26034 | `guard[1]` | Khoanh tay phòng thủ, biên trong suốt sạch | Phòng thủ |
| 16 | 26035 | `run[7]` | Lướt thấp sạch biên từ walk_1 | Motion blur / dash ưu tiên walk_1 |

## Kiểm soát chất lượng

- `preview/contact-*.png`: toàn bộ ô sau khi dò ranh giới alpha; viền đỏ báo frame chạm mép ô để rà thủ công.
- `preview/selected-body-frames.png`: 17 frame xuất thật trên nền caro tối.
- `qa-report.json`: ranh giới grid, component giữ/bỏ, crop, offset và kiểm tra viền alpha từng scale.
- `install.sql` / `rollback.sql`: cài hoặc gỡ đúng riêng bộ cải trang này.
