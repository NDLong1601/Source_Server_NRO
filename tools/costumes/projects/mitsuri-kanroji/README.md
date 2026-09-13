# Cải trang Mitsuri Kanroji

Cải trang full-body HD; HEAD và LEG dùng ảnh trong suốt vì tóc, vũ khí và hiệu ứng đi xuyên ranh giới part.
Mỗi scale x1-x4 được render trực tiếp từ ảnh nguồn bằng Lanczos, có viền alpha an toàn và không phóng từ x1.

- Item: `2069`; parts HEAD/BODY/LEG: `2156/2157/2158`.
- Icon hành trang: `25187`; avatar profile: `26017`.
- Shop Kanao: shop `112`, tab `1120`, giá `2000`.
- Không thêm option mặc định.

## DATA

```json
HEAD = [[25186,0,0],[25186,0,0],[25186,0,0]]
BODY = [[25169,-17,-37],[25216,-7,-34],[25300,-13,-35],[25172,-10,-18],[25173,-12,-23],[25174,-13,-23],[25175,-12,-34],[26005,-13,-35],[25177,-10,-29],[25178,-11,-39],[25179,-11,-42],[25180,-11,-41],[25181,-10,-29],[25301,-13,-35],[25183,-9,-43],[25184,-12,-39],[25185,-12,-34]]
LEG  = [[25186,0,0],[25186,0,0],[25186,0,0],[25186,0,0],[25186,0,0],[25186,0,0],[25186,0,0],[25186,0,0],[25186,0,0],[25186,0,0],[25186,0,0],[25186,0,0],[25186,0,0],[25186,0,0]]
```

## Mapping BODY

| Slot | imageId | Nguồn | Quan sát | Nhóm |
|---:|---:|---|---|---|
| 0 | 25169 | `guard[2]` | Chặn đòn bằng lưỡi kiếm mềm và tia va chạm | Đỡ / chịu đòn |
| 1 | 25216 | `idle[2]` | Đứng nghỉ đã lật ngang để hướng trái/phải khớp client | Đứng yên đã sửa hướng |
| 2 | 25300 | `fly[0]` | Bay ngang duỗi người, tay đưa về sau; thay hoàn toàn frame bay cũ | Bay / nhảy đã thay ảnh v3 |
| 3 | 25172 | `run[0]` | Hạ thấp trọng tâm chuẩn bị lao | Chạy 1 |
| 4 | 25173 | `run[1]` | Bật lao tới kèm bụi | Chạy 2 |
| 5 | 25174 | `run[2]` | Lướt thấp kéo kiếm mềm | Chạy 3 |
| 6 | 25175 | `run[5]` | Lướt nhanh với cung kiếm hồng | Chạy hiệu ứng 4 |
| 7 | 26005 | `fly[0]` | Bay ngang duỗi người tại slot bay thực tế của client | Bay thực tế BODY[7] |
| 8 | 25177 | `attack[1]` | Quét kiếm tạo vòng hồng và cánh hoa | Tụ lực / kỹ năng |
| 9 | 25178 | `attack[5]` | Vẽ vòng kiếm hình trái tim | Kỹ năng |
| 10 | 25179 | `attack2[5]` | Quét kiếm tạo cung hồng thấp | Đánh / kỹ năng |
| 11 | 25180 | `attack2[6]` | Lao chém ngang cùng cánh hoa | Đánh / kỹ năng |
| 12 | 25181 | `attack[1]` | Xoay kiếm giữa vòng hồng và cánh hoa | Đánh / kỹ năng |
| 13 | 25301 | `fly[0]` | Dùng cùng tư thế bay ngang cho nhánh bay/ngã thứ hai của client | Bay / ngã dự phòng đã thay ảnh v3 |
| 14 | 25183 | `attack[6]` | Tụ vòng kiếm hồng xoắn quanh thân | Kỹ năng hiệu ứng lớn |
| 15 | 25184 | `guard[3]` | Chặn đòn bằng khiên kiếm hồng | Phòng thủ |
| 16 | 25185 | `run[5]` | Lướt nhanh kéo cung kiếm hồng | Motion blur / dash |

## Kiểm soát chất lượng

- `preview/contact-*.png`: toàn bộ ô sau khi dò ranh giới alpha; viền đỏ báo frame chạm mép ô để rà thủ công.
- `preview/selected-body-frames.png`: 17 frame xuất thật trên nền caro tối.
- `qa-report.json`: ranh giới grid, component giữ/bỏ, crop, offset và kiểm tra viền alpha từng scale.
- `install.sql` / `rollback.sql`: cài hoặc gỡ đúng riêng bộ cải trang này.
