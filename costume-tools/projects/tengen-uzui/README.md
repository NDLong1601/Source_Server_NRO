# Cải trang Tengen Uzui

Cải trang full-body HD; HEAD và LEG dùng ảnh trong suốt vì tóc, vũ khí và hiệu ứng đi xuyên ranh giới part.
Mỗi scale x1-x4 được render trực tiếp từ ảnh nguồn bằng Lanczos, có viền alpha an toàn và không phóng từ x1.

- Item: `2067`; parts HEAD/BODY/LEG: `2150/2151/2152`.
- Icon hành trang: `25147`; avatar profile: `26015`.
- Shop Kanao: shop `112`, tab `1120`, giá `2000`.
- Không thêm option mặc định.

## DATA

```json
HEAD = [[25146,0,0],[25146,0,0],[25146,0,0]]
BODY = [[25129,-17,-28],[25219,-4,-38],[25220,-18,-34],[25132,-13,-22],[25133,-15,-28],[25134,-18,-30],[25135,-13,-26],[26009,-18,-34],[25137,-16,-40],[25138,-11,-33],[25139,-14,-30],[25140,-6,-34],[25141,-16,-40],[25221,-13,-40],[25143,-17,-34],[25144,-14,-31],[25145,-15,-28]]
LEG  = [[25146,0,0],[25146,0,0],[25146,0,0],[25146,0,0],[25146,0,0],[25146,0,0],[25146,0,0],[25146,0,0],[25146,0,0],[25146,0,0],[25146,0,0],[25146,0,0],[25146,0,0],[25146,0,0]]
```

## Mapping BODY

| Slot | imageId | Nguồn | Quan sát | Nhóm |
|---:|---:|---|---|---|
| 0 | 25129 | `guard[2]` | Bắt chéo song đao và xích trước thân | Đỡ / chịu đòn |
| 1 | 25219 | `idle[1]` | Đứng nghỉ đã lật ngang để hướng trái/phải khớp client | Đứng yên đã sửa hướng |
| 2 | 25220 | `fly[6]` | Lao bay ngang sang phải kèm vệt hỏa âm; phát hành bằng image ID mới để bỏ cache cũ | Bay / nhảy đã sửa |
| 3 | 25132 | `run[0]` | Hạ thấp trọng tâm, bụi đá bắn sau chân | Chạy hiệu ứng 1 |
| 4 | 25133 | `run[1]` | Lao tới cùng cung đao và bụi | Chạy hiệu ứng 2 |
| 5 | 25134 | `run[4]` | Trượt thấp tạo vòng bụi | Chạy hiệu ứng 3 |
| 6 | 25135 | `run[5]` | Tiếp đất thấp cùng bụi | Chạy hiệu ứng 4 |
| 7 | 26009 | `fly[6]` | Lao bay ngang sang phải, chân kéo vệt hỏa âm | Bay thực tế BODY[7] |
| 8 | 25137 | `attack[5]` | Tụ lực trong vòng cung vàng | Tụ lực / kỹ năng |
| 9 | 25138 | `attack2[5]` | Quét song đao tạo vòng hỏa âm thấp | Kỹ năng |
| 10 | 25139 | `attack2[3]` | Xoay người trong vòng hỏa âm | Đánh / kỹ năng |
| 11 | 25140 | `attack2[9]` | Kết thúc đòn, giơ tay giữ nhịp | Đánh / kỹ năng |
| 12 | 25141 | `attack[5]` | Chém xoay trong vòng cung vàng | Đánh / kỹ năng |
| 13 | 25221 | `fall[0]` | Rơi trên không với dáng duỗi toàn thân cùng tỉ lệ khung đứng | Rơi / nhảy xuống đã cân tỉ lệ |
| 14 | 25143 | `fly[2]` | Xoay song đao trong vòng hỏa âm tròn | Kỹ năng hiệu ứng lớn |
| 15 | 25144 | `guard[0]` | Hạ tấn chặn đòn bằng song đao | Phòng thủ |
| 16 | 25145 | `run[1]` | Lao nhanh kéo theo cung đao và bụi | Motion blur / dash |

## Kiểm soát chất lượng

- `preview/contact-*.png`: toàn bộ ô sau khi dò ranh giới alpha; viền đỏ báo frame chạm mép ô để rà thủ công.
- `preview/selected-body-frames.png`: 17 frame xuất thật trên nền caro tối.
- `qa-report.json`: ranh giới grid, component giữ/bỏ, crop, offset và kiểm tra viền alpha từng scale.
- `install.sql` / `rollback.sql`: cài hoặc gỡ đúng riêng bộ cải trang này.
