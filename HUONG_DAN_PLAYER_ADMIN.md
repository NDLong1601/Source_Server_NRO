# Hướng dẫn cấu hình và quản lý Player

Mở `admin_menu.bat`, sau đó dùng hai tab mới:

- **Cấu hình Player**: quản lý giá trị khởi tạo nhân vật mới và giới hạn dùng chung toàn server.
- **Quản lý Player**: tìm và chỉnh sửa dữ liệu của từng player ở mức an toàn cơ bản.

## 1. Cấu hình Player toàn server

Các giá trị thật được lưu tại `player.properties`. Giao diện phân biệt hai phạm vi:

Mỗi giá trị được đặt trong một tab con theo đúng nhóm cấu hình. Các tab con được sinh tự động từ catalog nên khi backend có thêm nhóm mới, giao diện cũng tự hiển thị nhóm đó.

- **Nhân vật mới**: vàng/ngọc/coupon, số ô ban đầu, thể lực, sức mạnh/tiềm năng và chỉ số gốc riêng cho Trái Đất, Namek, Xayda. Không tự sửa nhân vật đã tồn tại.
- **Runtime**: trần vàng/ngọc/coupon, hành trang/rương và giới hạn chỉ số theo `limitPower` 0–9. Server đọc lại file trong tối đa một giây sau khi phiên bản Java mới đã được khởi động.

Danh sách giới hạn cấp phải có đúng 10 số nguyên không giảm, phân cách bằng dấu phẩy. Số ô hành trang/rương nằm trong khoảng 1–127; mặc định vẫn là 80/100.

Mọi lần lưu hoặc đưa về mặc định đều xuất hiện trong tab **Lịch sử thay đổi** và có thể hoàn tác theo quy tắc hoàn tác gần nhất.

## 2. Quản lý từng Player

Có thể tìm theo:

- tên nhân vật;
- username;
- Player ID;
- Account ID.

Các trường được hỗ trợ:

- cấp giới hạn, sức mạnh, tiềm năng;
- HP/KI/sức đánh/giáp/chí mạng gốc và chí mạng rồng;
- thể lực hiện tại/tối đa;
- vàng, ngọc xanh, hồng ngọc, coupon;
- tổng ô hành trang và rương;
- khóa/mở khóa và active tài khoản;
- cứu hộ về map nhà, tọa độ 300/336.

### Quy tắc an toàn

1. Chỉ sửa player đã logout.
2. Trạng thái `ONLINE?` được suy ra từ lần login/logout. Nếu server đã dừng nhưng trạng thái bị treo, bật ô xác nhận offline trước khi lưu.
3. Không sửa database khi player đang thực sự online; dữ liệu runtime có thể ghi đè thay đổi lúc player thoát.
4. Chỉ thu nhỏ hành trang/rương khi toàn bộ ô bị cắt ở cuối đang rỗng.
5. Form dùng mã phiên bản dữ liệu. Nếu player thay đổi sau lúc mở form, thao tác lưu bị từ chối và phải tải lại.
6. Các thao tác ghi đều snapshot hàng `player`/`account` và được ghi vào lịch sử hoàn tác.

## 3. Áp dụng phiên bản Java đầu tiên

Source đã được build thành `dist/NgocRongOnline.jar` và xếp hàng tại `20.jar.pending` vì server đang chạy. Restart server một lần từ menu để áp dụng lớp `PlayerConfig`; quy trình khởi động sẽ backup `20.jar` rồi dùng bản pending.

Sau lần restart này, chỉnh sửa các giới hạn runtime trong tab **Cấu hình Player** không cần restart thêm.
