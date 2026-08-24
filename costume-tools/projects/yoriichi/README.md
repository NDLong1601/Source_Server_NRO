# Cải trang Yoriichi

Cải trang full-body HD; HEAD và LEG dùng ảnh trong suốt vì tóc, vũ khí và hiệu ứng đi xuyên ranh giới part.
Mỗi scale x1-x4 được render trực tiếp từ ảnh nguồn bằng Lanczos, có viền alpha an toàn và không phóng từ x1.

- Item: `2074`; parts HEAD/BODY/LEG: `2171/2172/2173`.
- Icon hành trang: `26119`; avatar profile: `26120`.
- Shop Kanao: shop `112`, tab `1120`, giá `2000`.
- Không thêm option mặc định.

## DATA

```json
HEAD = [[26118,0,0],[26118,0,0],[26118,0,0]]
BODY = [[26101,-16,-46],[26161,-19,-44],[26173,-13,-36],[26162,-17,-24],[26163,-13,-36],[26164,-14,-41],[26165,-11,-44],[26174,-15,-46],[26109,-14,-44],[26110,-19,-43],[26111,-15,-29],[26112,-13,-45],[26113,-17,-44],[26114,-17,-47],[26115,-21,-46],[26116,-13,-30],[26166,-13,-37]]
LEG  = [[26118,0,0],[26118,0,0],[26118,0,0],[26118,0,0],[26118,0,0],[26118,0,0],[26118,0,0],[26118,0,0],[26118,0,0],[26118,0,0],[26118,0,0],[26118,0,0],[26118,0,0],[26118,0,0]]
```

## Mapping BODY

| Slot | imageId | Nguồn | Quan sát | Nhóm |
|---:|---:|---|---|---|
| 0 | 26101 | `guard[4]` | Chặn kiếm có tia lửa, thân đầy đủ | Đỡ / chịu đòn |
| 1 | 26161 | `idle[0]` | Đứng nghỉ đã lật đúng hướng, mặt 3/4 đổi hướng rõ khi client mirror | Đứng yên |
| 2 | 26173 | `fly[1]` | Bay phụ dùng dáng mới, đã lật đúng hướng thay fly[2] cũ bị ngược | Bay / nhảy thay thế |
| 3 | 26162 | `run[0]` | Hạ thấp trọng tâm bắt đầu lướt, đã lật đúng hướng | Chạy 1 ưu tiên walk_1 |
| 4 | 26163 | `walk[2]` | Sải chạy sạch biên, đã lật đúng hướng thay frame walk_1 chạm mép | Chạy 2 sạch biên |
| 5 | 26164 | `walk[5]` | Chạy nghiêng sạch biên, đã lật đúng hướng thay frame walk_1 chạm mép | Chạy 3 sạch biên |
| 6 | 26165 | `run[7]` | Trở về thế chạy sạch biên, đã lật đúng hướng | Chạy 4 ưu tiên walk_1 |
| 7 | 26174 | `fly[3]` | Bay thực tế dùng dáng kiếm mới, đã lật đúng hướng thay fly[2] cũ bị ngược | Bay thực tế BODY[7] thay thế |
| 8 | 26109 | `attack2[0]` | Tụ kiếm đỏ dưới chân, dùng làm mở kỹ năng | Tụ lực / kỹ năng ưu tiên attack_1 |
| 9 | 26110 | `attack2[2]` | Chém vòng đỏ trên cao, giữ đủ vòng sáng | Kỹ năng ưu tiên attack_1 |
| 10 | 26111 | `attack[2]` | Chém ngang với cung kiếm đỏ | Đánh / kỹ năng |
| 11 | 26112 | `attack[4]` | Đâm kiếm có vệt đỏ, thân đầy đủ | Đánh / kỹ năng |
| 12 | 26113 | `attack[5]` | Xoay chém vòng đỏ lớn | Đánh / kỹ năng |
| 13 | 26114 | `attack2[4]` | Đáp đất/tụ kiếm với hiệu ứng đỏ quanh chân | Đáp đất có hiệu ứng |
| 14 | 26115 | `attack2[3]` | Cung trăng đỏ lớn, không lấy mảnh hiệu ứng rời | Kỹ năng hiệu ứng lớn ưu tiên attack_1 |
| 15 | 26116 | `guard[2]` | Kiếm chắn ngang trước mặt | Phòng thủ |
| 16 | 26166 | `walk[3]` | Dash/chạy phụ sạch biên, mặt đọc rõ hơn và đã lật đúng hướng | Motion blur / dash sạch biên |

## Kiểm soát chất lượng

- `preview/contact-*.png`: toàn bộ ô sau khi dò ranh giới alpha; viền đỏ báo frame chạm mép ô để rà thủ công.
- `preview/selected-body-frames.png`: 17 frame xuất thật trên nền caro tối.
- `qa-report.json`: ranh giới grid, component giữ/bỏ, crop, offset và kiểm tra viền alpha từng scale.
- `install.sql` / `rollback.sql`: cài hoặc gỡ đúng riêng bộ cải trang này.
