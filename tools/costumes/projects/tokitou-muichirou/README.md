# Tokitou Muichirou

Bộ cải trang full-body HD dùng chung cho cả ba giới tính:

- item: `2063`, `TYPE=5`, gender `3`;
- HEAD/BODY/LEG part: `2138/2139/2140`;
- icon hành trang: `25061`, lấy từ `source/avatar_item.png`;
- ảnh profile khi mặc: `26011`, lấy từ `source/Profile_Muichirou.png`, neo sát đáy và ánh xạ qua `head_avatar(2138,26011)`;
- BODY frame đang dùng: `25043`, `25044`, `25046..25049`, `25052..25055`,
  `25057`, `25058`, `25062`, ảnh đáp đất `26007` và ảnh bay thực tế `26003`;
- BODY `[2]`, `[13]` và `[16]` cùng dùng animation gốc `25059` qua image ID mới
  `25062`, tránh client tái dùng PNG hướng cũ đã cache;
- ảnh trong suốt HEAD/LEG: `25060`;
- shop Kanao: shop `112`, tab **Cải trang** `1120`, giá `2000` theo convention hiện có;
- option mặc định: không cấu hình vì người dùng chưa yêu cầu.

Tóc, kiếm và sương nước đi xuyên ranh giới đầu/thân/chân nên toàn bộ nhân vật được
vẽ trong BODY. HEAD và LEG vẫn giữ đúng `3/14` slot bằng PNG trong suốt. Các scale
`x1..x4` được render độc lập từ nguồn HD bằng Lanczos rồi quantize PNG 256 màu có
alpha; không phóng x1 thành các scale lớn.

## DATA

```json
HEAD = [[25060,0,0],[25060,0,0],[25060,0,0]]
BODY = [[25043,-9,-36],[25044,-20,-33],[25062,-15,-22],[25046,-12,-17],[25047,-15,-24],[25048,-15,-27],[25049,-15,-36],[26003,-15,-24],[26007,-14,-33],[25052,-11,-33],[25053,-11,-26],[25054,-10,-34],[25055,-11,-36],[25062,-15,-22],[25057,-11,-31],[25058,-19,-36],[25062,-15,-22]]
LEG  = [[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0]]
```

## Mapping BODY

| Slot | imageId | Nguồn | Quan sát | Nhóm đề xuất |
|---:|---:|---|---|---|
| 0 | 25043 | `đỡ[2]` | Giữ kiếm dọc trước mặt | Đỡ / chịu đòn |
| 1 | 25044 | `idle[5]` | Đứng nghỉ, thân và đầu nghiêng nhẹ sang phải | Đứng yên |
| 2 | 25062 | `walk_1[2]` | Lướt cực nhanh kèm bóng mờ và vệt xanh; bản ID mới của animation 25059 | Bay theo yêu cầu |
| 3 | 25046 | `walk_1[0]` | Hạ thấp trọng tâm, chống tay kèm bụi và gió | Chạy có hiệu ứng 1 |
| 4 | 25047 | `walk_1[1]` | Bật lao tới kèm bụi và vệt tốc độ | Chạy có hiệu ứng 2 |
| 5 | 25048 | `walk_1[5]` | Tiếp đất thấp, tóc quét theo vòng gió | Chạy có hiệu ứng 3 |
| 6 | 25049 | `walk_1[6]` | Đứng dậy sau pha lướt, bụi đá còn bay | Chạy có hiệu ứng 4 |
| 7 | 26003 | `bay_nhảy[1]` | Lao bay ngang sang phải, tóc và sương kéo về sau | Bay thực tế BODY[7] |
| 8 | 26007 | `landing_complete[0]` | Đáp đất giữa vòng nước đầy đủ, mép hiệu ứng được tách mềm | Đáp đất BODY[8] |
| 9 | 25052 | `attack_1[7]` | Đâm kiếm thẳng giữa luồng sương | Kỹ năng |
| 10 | 25053 | `attack_1[4]` | Lướt chém ngang có cung kiếm xanh | Đánh / kỹ năng |
| 11 | 25054 | `attack_1[5]` | Xoay người chém vòng cung lớn | Đánh / kỹ năng |
| 12 | 25055 | `attack_1[6]` | Bổ kiếm tạo cột nước và sương bùng nổ | Đánh / kỹ năng |
| 13 | 25062 | `walk_1[2]` | Lướt cực nhanh kèm bóng mờ và vệt xanh; bản ID mới của animation 25059 | Bay thay thế theo yêu cầu |
| 14 | 25057 | `attack_1[2]` | Chém thấp giữa vòng sương bao thân | Đánh / kỹ năng |
| 15 | 25058 | `đỡ[3]` | Chặn đòn kèm tia va chạm xanh | Phòng thủ |
| 16 | 25062 | `walk_1[2]` | Lướt cực nhanh kèm bóng mờ và vệt xanh; bản ID mới của animation 25059 | Motion blur / dash |

BODY `[2..7]`, `[13]` và `[16]` được mirror ngang lúc build để thống nhất hướng gốc sang
phải với BODY `[1]`. Client sẽ tự lật các ảnh này khi nhân vật chạy hoặc bay sang
trái. BODY `[1] / 25044` không bị dựng lại trong lần sửa hướng.

Các nhóm là suy luận thị giác theo bộ Luffy chuẩn; server không chứa bảng state
animation chính thức của client.

## Nhóm ghép đề xuất

- Đứng yên: HEAD trong suốt + BODY `[1]` + LEG trong suốt.
- Bay: BODY `[2]`/`[13]` dùng `25062`; slot hiển thị thực tế BODY `[7]` dùng
  `bay_nhảy[1] / 26003`.
- Chạy: BODY `[3..6]`, đều ưu tiên sheet `walk_1` có hiệu ứng.
- Đánh/kỹ năng: BODY `[8..12]` và `[14]`, đều ưu tiên sheet `attack_1`.
- Đỡ/phòng thủ: BODY `[0]` hoặc `[15]`.
- Dash/mờ tốc độ: BODY `[16]`.

## File

- `build_assets.py`: tách sheet, căn neo, crop và render x1-x4.
- `manifest.json`: mapping máy đọc được, gồm kích thước và dung lượng từng scale.
- `install.sql`: migration idempotent chứa mapping cuối đã nghiệm thu cho live DB.
- `rollback.sql`: gỡ item, part, avatar và dòng shop của riêng bộ này.
- `preview/selected-body-frames.png`: contact sheet 17 frame xuất cuối.
- `source/`: bản sao nguồn mà pipeline phụ thuộc.
