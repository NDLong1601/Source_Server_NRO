# Cải trang Gyomei Himejima

Cải trang full-body HD; HEAD và LEG dùng ảnh trong suốt vì tóc, vũ khí và hiệu ứng đi xuyên ranh giới part.
Mỗi scale x1-x4 được render trực tiếp từ ảnh nguồn bằng Lanczos, có viền alpha an toàn và không phóng từ x1.

- Item: `2068`; parts HEAD/BODY/LEG: `2153/2154/2155`.
- Icon hành trang: `25167`; avatar profile: `26016`.
- Shop Kanao: shop `112`, tab `1120`, giá `2000`.
- Không thêm option mặc định.

## DATA

```json
HEAD = [[25166,0,0],[25166,0,0],[25166,0,0]]
BODY = [[25149,-17,-34],[25150,-9,-35],[25151,-14,-40],[25152,-11,-31],[25153,-11,-30],[25154,-11,-41],[25155,-12,-41],[26004,-14,-40],[25157,-12,-44],[25158,-11,-42],[25159,-8,-29],[25160,-11,-40],[25161,-12,-42],[25162,-9,-30],[25163,-12,-41],[25164,-5,-43],[25165,-10,-41]]
LEG  = [[25166,0,0],[25166,0,0],[25166,0,0],[25166,0,0],[25166,0,0],[25166,0,0],[25166,0,0],[25166,0,0],[25166,0,0],[25166,0,0],[25166,0,0],[25166,0,0],[25166,0,0],[25166,0,0]]
```

## Mapping BODY

| Slot | imageId | Nguồn | Quan sát | Nhóm |
|---:|---:|---|---|---|
| 0 | 25149 | `guard[2]` | Chặn đòn bằng khiên khí xanh | Đỡ / chịu đòn |
| 1 | 25150 | `idle[5]` | Đứng nghỉ cầm chùy và rìu, đầu nghiêng nhẹ | Đứng yên đã căn đất và bóng |
| 2 | 25151 | `fly[7]` | Bay ngang sang phải, áo choàng kéo về sau | Bay / nhảy |
| 3 | 25152 | `run[0]` | Chạy bước thấp với chùy phía sau | Chạy 1 |
| 4 | 25153 | `run[1]` | Chạy chuyển bước | Chạy 2 |
| 5 | 25154 | `run[4]` | Chạy bước mở rộng | Chạy 3 |
| 6 | 25155 | `run[6]` | Chạy sải chân | Chạy 4 |
| 7 | 26004 | `fly[7]` | Bay ngang sang phải, áo choàng kéo về sau tại slot bay thực tế | Bay thực tế BODY[7] |
| 8 | 25157 | `attack2[5]` | Nện đất bùng đá vàng | Tụ lực / kỹ năng |
| 9 | 25158 | `attack2[6]` | Quét rìu tạo vòng khí vàng | Kỹ năng |
| 10 | 25159 | `attack[1]` | Vung chùy tạo cung vàng | Đánh / kỹ năng |
| 11 | 25160 | `attack[5]` | Quét xích tạo vòng khí xám | Đánh / kỹ năng |
| 12 | 25161 | `attack[6]` | Bổ chùy từ trên cao kèm cung vàng | Đánh / kỹ năng |
| 13 | 25162 | `fall[2]` | Bị đánh văng ra sau trên không | Ngã / đánh văng |
| 14 | 25163 | `attack2[9]` | Tụ lực giữa hào quang vàng | Kỹ năng hiệu ứng lớn |
| 15 | 25164 | `guard[3]` | Giữ khiên khí xanh sát thân | Phòng thủ |
| 16 | 25165 | `fly[5]` | Xoay rìu và xích thành vòng khi lao tới | Motion blur / dash |

## Kiểm soát chất lượng

- `preview/contact-*.png`: toàn bộ ô sau khi dò ranh giới alpha; viền đỏ báo frame chạm mép ô để rà thủ công.
- `preview/selected-body-frames.png`: 17 frame xuất thật trên nền caro tối.
- `qa-report.json`: ranh giới grid, component giữ/bỏ, crop, offset và kiểm tra viền alpha từng scale.
- `install.sql` / `rollback.sql`: cài hoặc gỡ đúng riêng bộ cải trang này.
