# Tokitou Muichirou

Bộ cải trang full-body HD dùng chung cho cả ba giới tính:

- item: `2063`, `TYPE=5`, gender `3`;
- HEAD/BODY/LEG part: `2138/2139/2140`;
- icon hành trang: `25061`, lấy từ `source/avatar_item.png`;
- ảnh profile khi mặc: `25042`, lấy từ `source/Profile_Muichirou.png` và ánh xạ qua `head_avatar(2138,25042)`;
- BODY frame: `25043..25059`;
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
BODY = [[25043,-9,-36],[25044,-20,-33],[25045,-15,-24],[25046,-12,-17],[25047,-15,-24],[25048,-15,-27],[25049,-15,-36],[25050,-11,-18],[25051,-11,-33],[25052,-11,-33],[25053,-11,-26],[25054,-10,-34],[25055,-11,-36],[25056,-15,-25],[25057,-11,-31],[25058,-19,-36],[25059,-15,-22]]
LEG  = [[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0]]
```

## Mapping BODY

| Slot | imageId | Nguồn | Quan sát | Nhóm đề xuất |
|---:|---:|---|---|---|
| 0 | 25043 | `đỡ[2]` | Giữ kiếm dọc trước mặt | Đỡ / chịu đòn |
| 1 | 25044 | `idle[5]` | Đứng nghỉ, thân và đầu nghiêng nhẹ sang phải | Đứng yên |
| 2 | 25045 | `bay_nhảy[1]` | Bay ngang, tóc và vệt gió kéo dài | Bay / nhảy |
| 3 | 25046 | `walk_1[0]` | Hạ thấp trọng tâm, chống tay kèm bụi và gió | Chạy có hiệu ứng 1 |
| 4 | 25047 | `walk_1[1]` | Bật lao tới kèm bụi và vệt tốc độ | Chạy có hiệu ứng 2 |
| 5 | 25048 | `walk_1[5]` | Tiếp đất thấp, tóc quét theo vòng gió | Chạy có hiệu ứng 3 |
| 6 | 25049 | `walk_1[6]` | Đứng dậy sau pha lướt, bụi đá còn bay | Chạy có hiệu ứng 4 |
| 7 | 25050 | `attack_1[1]` | Rút kiếm trong màn sương xanh | Đánh |
| 8 | 25051 | `attack_1[3]` | Nhắm mắt tụ lực giữa vòng sương | Tụ lực / kỹ năng |
| 9 | 25052 | `attack_1[7]` | Đâm kiếm thẳng giữa luồng sương | Kỹ năng |
| 10 | 25053 | `attack_1[4]` | Lướt chém ngang có cung kiếm xanh | Đánh / kỹ năng |
| 11 | 25054 | `attack_1[5]` | Xoay người chém vòng cung lớn | Đánh / kỹ năng |
| 12 | 25055 | `attack_1[6]` | Bổ kiếm tạo cột nước và sương bùng nổ | Đánh / kỹ năng |
| 13 | 25056 | `ngã[2]` | Tư thế bay lùi/ngã ngửa trên không, mắt nhắm | Bay thay thế / ngã / đánh văng |
| 14 | 25057 | `attack_1[2]` | Chém thấp giữa vòng sương bao thân | Đánh / kỹ năng |
| 15 | 25058 | `đỡ[3]` | Chặn đòn kèm tia va chạm xanh | Phòng thủ |
| 16 | 25059 | `walk_1[2]` | Lướt cực nhanh kèm bóng mờ và vệt xanh | Motion blur / dash |

BODY `[2..6]`, `[13]` và `[16]` được mirror ngang lúc build để thống nhất hướng gốc sang
phải với BODY `[1]`. Client sẽ tự lật các ảnh này khi nhân vật chạy hoặc bay sang
trái. BODY `[1] / 25044` không bị dựng lại trong lần sửa hướng.

Các nhóm là suy luận thị giác theo bộ Luffy chuẩn; server không chứa bảng state
animation chính thức của client.

## Nhóm ghép đề xuất

- Đứng yên: HEAD trong suốt + BODY `[1]` + LEG trong suốt.
- Bay/ngã: BODY `[2]` hoặc `[13]`.
- Chạy: BODY `[3..6]`, đều ưu tiên sheet `walk_1` có hiệu ứng.
- Đánh/kỹ năng: BODY `[7..12]` và `[14]`, đều ưu tiên sheet `attack_1`.
- Đỡ/phòng thủ: BODY `[0]` hoặc `[15]`.
- Dash/mờ tốc độ: BODY `[16]`.

## File

- `build_assets.py`: tách sheet, căn neo, crop và render x1-x4.
- `manifest.json`: mapping máy đọc được, gồm kích thước và dung lượng từng scale.
- `install.sql`: migration idempotent cho live DB.
- `rollback.sql`: gỡ item, part, avatar và dòng shop của riêng bộ này.
- `fix-direction.sql`: cập nhật offset part sau khi sửa hướng bay/chạy/dash.
- `rollback-direction.sql`: trả riêng DATA hướng chuyển động về bản trước khi sửa.
- `fix-fly-alt.sql`: cập nhật nhánh bay thay thế BODY `[13]` sau phản hồi trong client.
- `rollback-fly-alt.sql`: trả riêng BODY `[13]` về bản trước lần sửa bay thứ hai.
- `preview/selected-body-frames.png`: contact sheet 17 frame xuất cuối.
- `source/`: bản sao nguồn mà pipeline phụ thuộc.
- `backup-direction-before-v1/`: 24 PNG, DATA part, manifest, preview và cache source
  trước khi sửa hướng chuyển động.
- `backup-fly-alt-before-v2/`: image `25056` x1-x4 và DATA trước khi sửa nhánh bay
  thay thế BODY `[13]`.
