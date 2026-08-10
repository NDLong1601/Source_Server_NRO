# Quản lý lịch sử thay đổi và hoàn tác trong Admin Data

Mở `admin_data_menu.hta`, chọn tab **Lịch sử thay đổi** ở cuối menu bên trái.

Tab này ghi lại các thao tác làm thay đổi dữ liệu từ Admin Data và tạo điểm khôi phục trước khi thực hiện. Khi có sai sót, quản trị viên có thể hoàn tác tuần tự để đưa dữ liệu về trạng thái trước đó.

## Thông tin được ghi lại

Bảng lịch sử hiển thị tối đa 500 thao tác gần nhất, gồm:

- **ID**: mã duy nhất của bản ghi lịch sử.
- **Thời gian**: thời điểm thao tác được thực hiện.
- **Hành động**: tên lệnh nội bộ, ví dụ `savebossoverride`, `savegiftcode` hoặc `undoaudit`.
- **Nội dung**: mô tả ngắn về dữ liệu đã thay đổi.
- **Trạng thái**: thành công hoặc thất bại.
- **Hoàn tác**: cho biết thao tác đã hoàn tác, có thể hoàn tác, đang bị khóa hoặc không hỗ trợ.
- **Kết quả**: thông báo trả về sau khi thực hiện thao tác.

Có thể nhập ID, tên hành động hoặc nội dung vào ô tìm kiếm rồi bấm **Tìm**.

Các thao tác chỉ đọc như tìm kiếm, mở danh sách hoặc tải lại bảng không được ghi vào lịch sử để tránh làm danh sách bị nhiễu.

## Những thao tác được theo dõi

Hệ thống tự ghi lịch sử cho các nhóm sau:

- Thêm hoặc sửa vật phẩm trong `item_template`.
- Thêm, sửa, xóa shop, tab shop, item shop và option shop.
- Thêm, sửa, xóa Giftcode.
- Lưu hoặc trả Boss server về cấu hình mặc định.
- Lưu hoặc gỡ cấu hình Mob/Quái.
- Thêm, sửa, xóa Boss tùy chỉnh nếu chức năng này được sử dụng.
- Thay đổi hoặc khôi phục cấu hình Combine.
- Thay đổi sự kiện và tỉ lệ TNSM.
- Yêu cầu Restart server từ giao diện Admin Data.
- Các lần hoàn tác đã thực hiện.

Thao tác thất bại vẫn được ghi lại để quản trị viên biết đã có hành động nào được thử, nhưng không có nút hoàn tác vì dữ liệu chính chưa được thay đổi thành công.

## Cách hoàn tác một thay đổi

1. Mở tab **Lịch sử thay đổi**.
2. Chọn dòng có trạng thái **Có thể hoàn tác**.
3. Kiểm tra phần hành động, nội dung và kết quả ở khung bên phải.
4. Bấm **Hoàn tác thay đổi này**.
5. Đọc cảnh báo xác nhận và bấm đồng ý.
6. Kiểm tra thông báo hoàn tất; Restart server nếu thay đổi liên quan dữ liệu được JVM nạp khi khởi động.

Sau khi hoàn tác:

- Dòng gốc chuyển thành **Đã hoàn tác** và ghi thời gian hoàn tác.
- Hệ thống tạo thêm một dòng `undoaudit` để lưu lại chính hành động hoàn tác.
- Thay đổi thành công liền trước sẽ trở thành dòng có thể hoàn tác tiếp theo.

## Quy tắc hoàn tác tuần tự

Chỉ thay đổi ghi dữ liệu thành công gần nhất chưa hoàn tác mới có thể quay lại. Không thể chọn trực tiếp một thay đổi cũ khi phía trên còn thay đổi mới hơn.

Ví dụ:

```text
#103  Sửa drop Black Goku       <- có thể hoàn tác
#102  Sửa map Black Goku        <- đang khóa
#101  Sửa skill Black Goku      <- đang khóa
```

Sau khi hoàn tác `#103`, dòng `#102` được mở khóa. Tiếp tục hoàn tác `#102` nếu muốn quay lại trạng thái trước khi sửa map.

Quy tắc này tránh trường hợp khôi phục một bản ghi cũ rồi vô tình ghi đè các thay đổi mới hơn.

Các hành động không thay đổi dữ liệu, bản ghi thất bại, yêu cầu Restart và dòng `undoaudit` không chặn chuỗi hoàn tác.

## Dữ liệu nào được khôi phục

### Dữ liệu MySQL

Trước mỗi thao tác, backend dùng `mysqldump.exe` để chụp đúng các dòng bị ảnh hưởng. Khi hoàn tác, hệ thống xóa trạng thái hiện tại của phạm vi đó và khôi phục dữ liệu đã chụp.

Các quan hệ cha/con cũng được lưu cùng nhau. Ví dụ:

- Hoàn tác tab shop khôi phục tab, item trong tab và option của từng item.
- Hoàn tác item shop khôi phục cả các dòng `item_shop_option`.
- Hoàn tác Boss khôi phục `admin_boss_override` và drop thuộc Boss.
- Hoàn tác Mob khôi phục cấu hình Mob và drop thuộc cấu hình đó.

### File cấu hình

Với Combine, hệ thống lưu nguyên trạng byte của `combine.properties`. Với sự kiện và tỉ lệ TNSM, hệ thống lưu `Config.properties`. Hoàn tác sẽ đưa đúng file về nội dung trước thao tác, kể cả định dạng và xuống dòng.

## Khi nào cần Restart server

Việc hoàn tác chỉ khôi phục dữ liệu lưu trữ. Có cần Restart hay không phụ thuộc chức năng:

- Boss, Mob, Giftcode, item template và nhiều dữ liệu shop được JVM nạp khi khởi động: nên Restart server sau khi hoàn tác.
- Một số cấu hình Combine có cơ chế tự tải lại: thường không cần Restart.
- Giao diện luôn hiển thị thông báo nhắc Restart nếu thao tác có thể ảnh hưởng runtime.

## Trạng thái trong bảng lịch sử

| Trạng thái | Ý nghĩa |
|---|---|
| **Có thể hoàn tác** | Thay đổi thành công gần nhất, có snapshot hợp lệ. |
| **Đang khóa** | Có thay đổi mới hơn cần hoàn tác trước. |
| **Đã hoàn tác** | Snapshot đã được sử dụng để khôi phục dữ liệu. |
| **Không hỗ trợ** | Hành động chỉ để ghi nhận, thất bại hoặc không làm thay đổi dữ liệu cần phục hồi. |

## Bảng và mã nguồn liên quan

Lịch sử được lưu trong bảng:

```text
admin_change_log
```

Các cột quan trọng:

```text
id                ID lịch sử
action_name       Tên hành động
summary           Nội dung thay đổi
status            success / failed
reversible        Có dữ liệu hoàn tác hay không
rollback_payload  Snapshot dùng để khôi phục
result_message    Kết quả thao tác
created_at        Thời điểm thực hiện
undone_at         Thời điểm hoàn tác
```

Các file triển khai:

```text
admin_data_menu.hta
tools/admin_data.ps1
sql/database team2026.sql
```

Trong `tools/admin_data.ps1`, các hàm chính gồm:

```text
Ensure-AuditSchema      Tạo bảng lịch sử nếu chưa tồn tại
Get-AuditContext        Xác định dữ liệu cần chụp trước thao tác
New-DbAuditSnapshot     Chụp các dòng MySQL liên quan
Write-AuditEntry        Ghi kết quả vào lịch sử
List-AuditEntries       Trả danh sách cho giao diện
Undo-AuditEntry         Kiểm tra và thực hiện hoàn tác
```

## Lưu ý an toàn

- Lịch sử chỉ bắt đầu ghi từ khi tính năng này được thêm; log cũ trong `logs/admin_data.log` không có snapshot nên không thể hoàn tác.
- Không sửa trực tiếp `rollback_payload` trong MySQL.
- Không xóa thủ công các dòng lịch sử đang nằm trong chuỗi hoàn tác.
- Máy phải có `mysqldump.exe`; thông thường file này nằm cùng thư mục với `mysql.exe` của XAMPP hoặc MariaDB.
- Nếu không tạo được snapshot, thao tác thay đổi sẽ bị dừng để tránh lưu dữ liệu mà không có điểm quay lại.
- Nên đọc kỹ nội dung và kết quả trước khi bấm hoàn tác, đặc biệt trên server đang có người chơi.

## Ví dụ với Boss

Giả sử Black Goku đang dùng cấu hình mặc định:

1. Chọn Black Goku trong tab **Boss**.
2. Chọn map `102` và `93`, thêm drop và bấm **Lưu cấu hình Boss**.
3. Tab lịch sử tạo một dòng tương tự:

```text
Lưu Boss BLACK_GOKU (-203): 2 map, 6 skill, 1 drop, chu kỳ mặc định
```

4. Chọn dòng đó và bấm **Hoàn tác thay đổi này**.
5. `admin_boss_override` và drop của Black Goku được trả về đúng trạng thái trước khi lưu.
6. Restart server để Boss đang chạy nhận lại cấu hình phù hợp.

## Xử lý lỗi thường gặp

### Nút hoàn tác bị khóa

Còn một thay đổi thành công mới hơn chưa hoàn tác. Hãy tìm dòng có trạng thái **Có thể hoàn tác** và xử lý từ dòng đó trước.

### Báo không tìm thấy `mysqldump.exe`

Kiểm tra thư mục MySQL, ví dụ:

```text
C:\xampp\mysql\bin\mysqldump.exe
```

`mysql.exe` và `mysqldump.exe` nên nằm trong cùng thư mục hoặc đã được thêm vào biến `PATH`.

### Hoàn tác thành công nhưng game chưa thay đổi

Dữ liệu MySQL hoặc file cấu hình đã được khôi phục, nhưng JVM vẫn đang giữ bản cũ trong bộ nhớ. Bấm **Restart** trên thanh công cụ rồi kiểm tra lại.
