# Cải trang Obanai Iguro

Cải trang full-body HD; HEAD và LEG dùng ảnh trong suốt vì tóc, vũ khí và hiệu ứng đi xuyên ranh giới part.
Mỗi scale x1-x4 được render trực tiếp từ ảnh nguồn bằng Lanczos, có viền alpha an toàn và không phóng từ x1.

- Item: `2065`; parts HEAD/BODY/LEG: `2144/2145/2146`.
- Icon hành trang: `25102`; avatar profile: `26013`.
- Shop Kanao: shop `112`, tab `1120`, giá `2000`.
- Không thêm option mặc định.

## DATA

```json
HEAD = [[25101,0,0],[25101,0,0],[25101,0,0]]
BODY = [[25084,-19,-36],[25085,-8,-37],[25125,-15,-32],[25087,-16,-34],[25088,-18,-32],[25089,-19,-37],[25090,-19,-36],[26006,-15,-32],[25092,-9,-30],[25093,-15,-40],[25094,-14,-28],[25095,-12,-38],[25096,-12,-45],[25097,-14,-26],[25098,-12,-44],[25099,-10,-41],[25100,-22,-38]]
LEG  = [[25101,0,0],[25101,0,0],[25101,0,0],[25101,0,0],[25101,0,0],[25101,0,0],[25101,0,0],[25101,0,0],[25101,0,0],[25101,0,0],[25101,0,0],[25101,0,0],[25101,0,0],[25101,0,0]]
```

## Mapping BODY

| Slot | imageId | Nguồn | Quan sát | Nhóm |
|---:|---:|---|---|---|
| 0 | 25084 | `guard[2]` | Đỡ kiếm trong khiên tím | Đỡ / chịu đòn |
| 1 | 25085 | `idle[5]` | Đứng nghỉ nghiêng phải cùng bạch xà | Đứng yên đã căn bóng |
| 2 | 25125 | `fly[5]` | Bay ngang hướng phải, áo choàng kéo về sau | Bay / nhảy đã sửa |
| 3 | 25087 | `run[0]` | Hạ thấp trọng tâm kèm bụi | Chạy hiệu ứng 1 |
| 4 | 25088 | `run[1]` | Lao tới kèm vệt tốc độ | Chạy hiệu ứng 2 |
| 5 | 25089 | `run[3]` | Chuyển bước với đường kiếm tím | Chạy hiệu ứng 3 |
| 6 | 25090 | `run[4]` | Lướt thấp cùng bạch xà | Chạy hiệu ứng 4 |
| 7 | 26006 | `fly[5]` | Bay ngang hướng phải, áo choàng kéo về sau tại slot bay thực tế | Bay thực tế BODY[7] |
| 8 | 25092 | `attack2[0]` | Tụ khí tím quanh thân | Tụ lực / kỹ năng |
| 9 | 25093 | `attack2[3]` | Triệu hồi đại xà tím | Kỹ năng hiệu ứng lớn |
| 10 | 25094 | `attack[2]` | Chém ngang tạo cung kiếm hồng | Đánh / kỹ năng |
| 11 | 25095 | `attack[4]` | Quét kiếm hồng sát đất | Đánh / kỹ năng |
| 12 | 25096 | `attack2[4]` | Đại xà trườn quanh kiếm | Đánh / kỹ năng |
| 13 | 25097 | `fall[3]` | Bị đánh văng trên không | Ngã / đánh văng |
| 14 | 25098 | `attack2[6]` | Chém cùng cánh hoa và vệt xanh | Kỹ năng |
| 15 | 25099 | `guard[3]` | Chặn đòn kèm tia va chạm tím | Phòng thủ |
| 16 | 25100 | `run[5]` | Lướt cực nhanh với dải tốc độ | Motion blur / dash |

## Kiểm soát chất lượng

- `preview/contact-*.png`: toàn bộ ô sau khi dò ranh giới alpha; viền đỏ báo frame chạm mép ô để rà thủ công.
- `preview/selected-body-frames.png`: 17 frame xuất thật trên nền caro tối.
- `qa-report.json`: ranh giới grid, component giữ/bỏ, crop, offset và kiểm tra viền alpha từng scale.
- `install.sql` / `rollback.sql`: cài hoặc gỡ đúng riêng bộ cải trang này.
