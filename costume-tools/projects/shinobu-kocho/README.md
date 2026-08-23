# Cải trang Shinobu Kocho

Cải trang full-body HD; HEAD và LEG dùng ảnh trong suốt vì tóc, vũ khí và hiệu ứng đi xuyên ranh giới part.
Mỗi scale x1-x4 được render trực tiếp từ ảnh nguồn bằng Lanczos, có viền alpha an toàn và không phóng từ x1.

- Item: `2070`; parts HEAD/BODY/LEG: `2159/2160/2161`.
- Icon hành trang: `25207`; avatar profile: `26018`.
- Shop Kanao: shop `112`, tab `1120`, giá `2000`.
- Không thêm option mặc định.

## DATA

```json
HEAD = [[25206,0,0],[25206,0,0],[25206,0,0]]
BODY = [[25189,-21,-33],[25208,-7,-38],[25209,-20,-43],[25210,-12,-29],[25211,-12,-29],[25212,-13,-41],[25213,-10,-40],[25225,-20,-43],[25197,-8,-30],[25198,-10,-30],[25199,-11,-41],[25200,-11,-42],[25201,-12,-42],[25214,-20,-43],[25203,-12,-41],[25204,-7,-42],[25215,-13,-41]]
LEG  = [[25206,0,0],[25206,0,0],[25206,0,0],[25206,0,0],[25206,0,0],[25206,0,0],[25206,0,0],[25206,0,0],[25206,0,0],[25206,0,0],[25206,0,0],[25206,0,0],[25206,0,0],[25206,0,0]]
```

## Mapping BODY

| Slot | imageId | Nguồn | Quan sát | Nhóm |
|---:|---:|---|---|---|
| 0 | 25189 | `guard[2]` | Chặn đòn bằng khiên cánh bướm tím | Đỡ / chịu đòn |
| 1 | 25208 | `idle[2]` | Đứng nghỉ nhìn sang phải, tâm bàn chân căn theo chuẩn Sanemi | Đứng yên đã căn lại bóng v4 |
| 2 | 25209 | `fly[4]` | Bay ngang nhìn sang phải, thân và áo cùng hướng chuyển động | Bay / nhảy đã thay hình |
| 3 | 25210 | `walk[0]` | Chạy sang phải bước 1 | Chạy đã sửa hướng 1 |
| 4 | 25211 | `walk[1]` | Chạy sang phải bước 2 | Chạy đã sửa hướng 2 |
| 5 | 25212 | `walk[4]` | Chạy sang phải bước 3 | Chạy đã sửa hướng 3 |
| 6 | 25213 | `walk[5]` | Chạy sang phải bước 4 | Chạy đã sửa hướng 4 |
| 7 | 25225 | `fly[4]` | Bay ngang nhìn sang phải tại slot bay thực tế của client | Bay thực tế đã sửa |
| 8 | 25197 | `attack2[0]` | Tụ bướm tím quanh chân | Tụ lực / kỹ năng |
| 9 | 25198 | `attack2[1]` | Tụ lực trong đàn bướm tím | Kỹ năng |
| 10 | 25199 | `attack[5]` | Quét kiếm tạo cung tím | Đánh / kỹ năng |
| 11 | 25200 | `attack[6]` | Xoay người giữa vòng bướm tím | Đánh / kỹ năng |
| 12 | 25201 | `attack2[5]` | Chém xoay tạo vòng tím xanh | Đánh / kỹ năng |
| 13 | 25214 | `fly[4]` | Bay ngang dự phòng cho slot bay/ngã thứ hai | Bay / ngã đã thay hình |
| 14 | 25203 | `attack2[6]` | Bùng cánh bướm tím xanh quanh thân | Kỹ năng hiệu ứng lớn |
| 15 | 25204 | `guard[3]` | Giữ khiên cánh bướm tím sát thân | Phòng thủ |
| 16 | 25215 | `attack[5]` | Lướt chém sang phải kéo theo cung tím | Motion blur / dash đã sửa hướng |

## Kiểm soát chất lượng

- `preview/contact-*.png`: toàn bộ ô sau khi dò ranh giới alpha; viền đỏ báo frame chạm mép ô để rà thủ công.
- `preview/selected-body-frames.png`: 17 frame xuất thật trên nền caro tối.
- `qa-report.json`: ranh giới grid, component giữ/bỏ, crop, offset và kiểm tra viền alpha từng scale.
- `install.sql` / `rollback.sql`: cài hoặc gỡ đúng riêng bộ cải trang này.
