# Quản lý Giftcode trong Admin Data

Mở `admin_data_menu.hta`, chọn tab **Giftcode**.

## Danh sách và trạng thái

Bảng bên trái hiển thị mã, danh sách quà, số lượt còn lại, thời gian bắt đầu/kết thúc và trạng thái:

- **Còn**: nền xanh.
- **Hết lượt / Hết hạn**: nền đỏ.
- **Chưa bắt đầu**: nền vàng.
- Số lượt `-1` được hiển thị là **Không giới hạn**.

## Thêm hoặc sửa

1. Bấm **Thêm mới** hoặc chọn một dòng Giftcode hiện có.
2. Nhập mã và số lượt; có thể chọn **Không giới hạn**.
3. Chọn một trong hai kiểu hạn dùng:
   - **Trong bao nhiêu ngày**: tính từ lúc bấm lưu.
   - **Từ ngày đến ngày**: nhập theo `YYYY-MM-DD HH:mm:ss`.
4. Tìm vật phẩm và tích checklist để thêm quà. Danh sách có cả Vàng (`-1`), Ngọc (`-2`) và Ngọc khóa (`-3`).
5. Chọn dòng quà ở bảng nhỏ để chỉnh số lượng và checklist option. Mỗi option được chọn có ô `Param` riêng.
6. Bấm **Lưu Giftcode**.

Giftcode được JVM nạp vào bộ nhớ lúc khởi động. Sau khi thêm, sửa hoặc xóa, bấm **Restart** trên thanh công cụ để dữ liệu đang chạy được đồng bộ an toàn.

## Lưu ý dữ liệu

- Mã chỉ gồm chữ, số, `_`, `-` và dài 3-64 ký tự.
- Mỗi Giftcode phải có ít nhất một phần quà.
- Một vật phẩm hoặc option không được lặp lại trong cùng cấu hình.
- Param option hỗ trợ toàn bộ miền số nguyên 32-bit, phù hợp các chỉ số HP/KI lớn.
