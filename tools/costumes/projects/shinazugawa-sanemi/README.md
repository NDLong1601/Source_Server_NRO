# Shinazugawa Sanemi

Bộ này thay thế trực tiếp cải trang lỗi hiện có để giữ chuỗi ID liên tục:

- item: `2062` (`TYPE=5`, gender dùng chung `3`);
- HEAD/BODY/LEG part: `2099/2100/2101`;
- icon hành trang: `25041`, lấy từ `source/item-icon-25041.png`;
- ảnh profile khi mặc cải trang: `26010`, ánh xạ qua `head_avatar(2099,26010)`;
- icon hành trang dùng canvas x1 `25x22`;
- profile dùng canvas x1 `64x64`, crop alpha rồi neo sát đáy để không tạo khoảng trống trên tab UI;
- BODY frame: `25023..25029`, `25031..25039` và ảnh bay thực tế `26001`;
- ảnh trong suốt dùng cho HEAD/LEG: `25040`;
- shop Kanao: shop `112`, tab **Cải trang** `1120`;
- option mặc định: chưa cấu hình theo yêu cầu.

Ảnh nguồn là sprite toàn thân. Vì hiệu ứng gió và nhiều tư thế đi qua ranh giới
đầu/thân/chân, toàn bộ hình được đặt ở BODY; HEAD và LEG vẫn đủ đúng `3/14` slot
nhưng dùng ảnh trong suốt. BODY được cắt sát theo từng frame từ canvas tham chiếu
x1 `64x56`; `dx/dy` được bù theo crop và lưu riêng cho từng slot.
Riêng frame đứng `BODY[1] / 25024` có thân xoay sang phải nhưng đầu chỉ nghiêng nhẹ
ở góc 3/4 để vẫn đọc rõ hai mắt như cải trang Goku Cận Vô Cực. Frame được căn giữa,
neo chân theo cùng chuẩn canvas và hạ riêng `3 px` logic (`dy=-33`) để bàn chân chạm
đường mặt đất trong game; client tự lật ảnh khi nhân vật quay sang trái.
BODY `[7]` dùng riêng `fly[1] / 26001` vì client hiển thị slot này khi bay; các
frame đứng, chạy và phần còn lại được giữ nguyên.
Profile phát hành bằng ID mới `26010`; ảnh nguồn vẫn là `source/profile-25022.png`.

Mỗi mức `x1/x2/x3/x4` được render trực tiếp từ ảnh gốc bằng Lanczos. Không phóng
x1 lên các scale lớn bằng nearest-neighbor; đây là cách giữ nét HD tương tự bộ
Goku Cận Vô Cực đang có trong source. PNG đầu ra dùng bảng màu 256 màu có alpha,
định dạng vốn đã được client sử dụng, để tránh payload ảnh lớn gây tải thiếu asset.

## Mapping BODY

| Slot | imageId | Nguồn | Quan sát | Nhóm đề xuất |
|---:|---:|---|---|---|
| 0 | 25023 | guard[1] | Khoanh tay che mặt | Đỡ / chịu đòn |
| 1 | 25024 | idle_side[0] | Thân nghiêng phải, đầu góc 3/4 nhẹ | Đứng yên |
| 2 | 25025 | fly[0] | Bật lên trên không | Bay / nhảy |
| 3 | 25026 | walk[0] | Chạy bước 1 | Chạy |
| 4 | 25027 | walk[1] | Chạy bước 2 | Chạy |
| 5 | 25028 | walk[4] | Chạy bước 3 | Chạy |
| 6 | 25029 | walk[5] | Chạy bước 4 | Chạy |
| 7 | 26001 | fly[1] | Lao bay ngang sang phải trong vệt gió xanh | Bay thực tế BODY[7] |
| 8 | 25031 | guard[2] | Đỡ đòn có va chạm | Tụ lực / kỹ năng |
| 9 | 25032 | attack[2] | Đứng tụ gió quanh người | Kỹ năng |
| 10 | 25033 | attack2[2] | Chém vòng cung thấp | Đánh / kỹ năng |
| 11 | 25034 | attack2[3] | Lướt chém ngang | Đánh / kỹ năng |
| 12 | 25035 | attack2[4] | Chém vòng cung trên đầu | Đánh / kỹ năng |
| 13 | 25036 | fall[2] | Bị đánh ngã về sau | Ngã / đánh văng |
| 14 | 25037 | attack[5] | Xoáy kiếm trong lốc gió | Đánh / kỹ năng |
| 15 | 25038 | guard[3] | Khoanh tay chịu đòn có tia lửa | Phòng thủ |
| 16 | 25039 | run[2] | Lướt nhanh kèm gió | Motion blur / dash |

Tên nhóm chỉ là suy luận thị giác dựa trên bộ Luffy chuẩn; server không chứa bảng
state animation chính thức của client.

## File

- `build_assets.py`: tách sheet, lọc mảnh sprite lân cận, căn neo và tạo x1-x4.
- `manifest.json`: mapping máy đọc được.
- `install.sql`: migration idempotent chứa mapping cuối đã nghiệm thu cho live DB.
- `rollback.sql`: khôi phục đúng item/part cũ và gỡ item khỏi tab Kanao.
- `preview/selected-body-frames.png`: contact sheet 17 frame đã chọn.
- `source/item-icon-25041.png`: ảnh nguồn gốc của icon vật phẩm 25041.
- `source/profile-25022.png`: ảnh nguồn HD của profile 25022.
- `source/idle-side-right-25024.png`: ảnh nguồn HD của frame đứng nghiêng mới.

Nếu rollback, các ảnh mới `25022..25041.png` ở `data/icon/x1..x4` có thể xóa sau
khi chắc chắn không còn part/item nào tham chiếu.
