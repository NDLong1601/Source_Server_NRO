# NRO Server Dashboard - Changelog

## Mục đích

File này ghi lại các thay đổi đã thực hiện cho menu `run.bat` và dashboard quản lý server, để lần sau có thể xem lại nhanh các lỗi đã sửa và cách vận hành.

## File đã thêm hoặc chỉnh sửa

- `run.bat`
  - Mở dashboard HTA khi chạy không tham số.
  - Nhận các lệnh nội bộ: `start`, `stop`, `restart`, `status`, `event`, `eventrestart`, `exp`, `exprestart`, `build`, `openlogs`.
  - Sửa truyền tham số có dấu phẩy như `hungvuong,topup` để không bị mất phần sau dấu phẩy.

- `server_menu.hta`
  - Dashboard giao diện Windows bằng HTA.
  - Sidebar bên trái có nút máy chủ, sự kiện, tỉ lệ tiềm năng/sức mạnh, công cụ.
  - Bên phải là bảng log với tab `Log server`, `Log lỗi`, `Log điều khiển`.
  - Sự kiện được đổi từ dropdown sang từng button riêng, có trạng thái `Đang bật` / `Đang tắt`.
  - Bấm sự kiện sẽ hiện xác nhận, sau đó tự restart server để áp dụng.
  - Thêm nút TNSM `x1`, `x2`, `x3`; bấm xong tự restart server để áp dụng.

- `tools/server_control.ps1`
  - Script điều khiển thật cho dashboard.
  - Quản lý start/stop/restart/status/event/exp/build/log.
  - Thêm khóa điều khiển liên tiến trình để tránh nhiều lệnh status tự chạy cùng lúc gây tranh file.
  - Ghi/đọc file log, PID, status bằng cơ chế retry và atomic write.
  - Status chỉ hiển thị PID thật đang listen port server.

- `Config.properties`
  - Mặc định hiện tại:

```properties
server.event=none
server.expserver=1
```

- `src/nro/models/event/EventManager.java`
  - Đổi sự kiện sang đọc từ `Config.properties`.
  - Mặc định nếu không đọc được config là `none`.
  - Hỗ trợ các giá trị:
    - `none`
    - `hungvuong,topup`
    - `trungthu,topup`
    - `halloween,topup`
    - `christmas,topup`
    - `lunar_new_year,topup`
    - `womens_day,topup`
    - `topup`
    - `all`

## Lỗi đã sửa

### 1. Lỗi `RunAction is undefined`

Nguyên nhân:

- HTA dùng `onclick="RunAction(...)"`, nhưng hàm lại nằm trong khối `VBScript`.
- MSHTA gọi theo JScript nên không tìm thấy hàm.

Cách sửa:

- Chuyển toàn bộ script trong `server_menu.hta` sang `JScript`.

### 2. Lỗi `Input past end of file`

Nguyên nhân:

- Dashboard đọc log bằng `ReadAll()` khi file log đang rỗng hoặc đang được Java ghi.

Cách sửa:

- Đổi sang đọc file an toàn bằng `ADODB.Stream`.
- Nếu file rỗng hoặc đang bị khóa thì trả về chuỗi rỗng, không bật popup lỗi.

### 3. Lỗi `Stream was not readable`

Nguyên nhân:

- Nhiều tiến trình PowerShell/HTA cùng đọc ghi `menu_status.txt`, `server.pid`, `control.log`.

Cách sửa:

- Thêm mutex `Global\NRO_SERVER_DASHBOARD_CONTROL`.
- Thêm retry khi đọc/ghi file.
- Ghi file kiểu atomic qua file tạm rồi move vào vị trí thật.

### 4. Lỗi `file is being used by another process`

Nguyên nhân:

- Dashboard auto-refresh gọi `run.bat status` liên tục, trong khi các nút khác cũng đang ghi status/PID.

Cách sửa:

- Tất cả action đi qua cùng một khóa điều khiển.
- Đọc file bằng FileShare `ReadWrite`.
- Không ghi trực tiếp đè file đang được đọc.

### 5. Start server mở trùng nhiều tiến trình

Nguyên nhân:

- Ban đầu chỉ dựa vào `server.pid`, nếu PID file lệch thì dashboard tưởng server chưa chạy.

Cách sửa:

- Tìm tiến trình Java theo command line chứa `20.jar`.
- Status hiển thị PID đang thật sự listen port `server.port`.
- Start bỏ qua nếu server đã chạy.

### 6. Lỗi port `14445` đã được dùng

Nguyên nhân:

- Có nhiều tiến trình Java cùng chạy `20.jar`, tiến trình sau không bind được port.

Cách sửa:

- Restart bằng script mới dừng toàn bộ tiến trình `20.jar` liên quan rồi khởi lại.
- Status hiện PID thật đang listen port.

### 7. Bấm sự kiện/x2/x3 thấy không thay đổi ngay

Nguyên nhân:

- Dashboard gọi restart bất đồng bộ rồi refresh status ngay, lúc đó restart chưa xong.

Cách sửa:

- Các nút sự kiện và TNSM chạy đồng bộ, chờ restart hoàn tất rồi mới refresh.
- Log điều khiển ghi rõ quá trình áp dụng.

## Mặc định hiện tại

Server mặc định khi mở là:

- Không sự kiện: `server.event=none`
- Tiềm năng/sức mạnh x1: `server.expserver=1`

## Cơ chế sự kiện

Dashboard chỉ thay đổi cấu hình trong `Config.properties`, sau đó restart server để Java đọc lại cấu hình.

Ví dụ:

```properties
server.event=none
server.event=hungvuong,topup
server.event=lunar_new_year,topup
server.event=all
```

Code áp dụng sự kiện nằm ở:

- `src/nro/models/event/EventManager.java`

## Cơ chế x1/x2/x3 tiềm năng/sức mạnh

Config:

```properties
server.expserver=1
server.expserver=2
server.expserver=3
```

Code đọc config:

- `src/nro/models/server/Manager.java`
  - `RATE_EXP_SERVER`
  - đọc từ key `server.expserver`

Code nhân TNSM:

- `src/nro/models/player/NPoint.java`
  - hàm `calSucManhTiemNang`
  - cuối hàm có `tiemNang *= Manager.RATE_EXP_SERVER`

## Cách dùng nhanh

1. Chạy `run.bat`.
2. Dashboard sẽ mở.
3. Bấm `Khởi chạy server` nếu server chưa chạy.
4. Bấm sự kiện muốn bật, xác nhận, dashboard sẽ restart server.
5. Bấm `x1`, `x2`, hoặc `x3`, xác nhận, dashboard sẽ restart server.
6. Xem log ở các tab:
   - `Log server`
   - `Log lỗi`
   - `Log điều khiển`

## Lưu ý

- Sau khi sửa code Java, cần build/cập nhật `20.jar` để thay đổi Java có hiệu lực.
- Khi server đang chạy, nút build sẽ không ghi đè `20.jar`; cần dừng server trước.
- Nếu dashboard cũ đang mở, hãy đóng rồi chạy lại `run.bat` để dùng bản mới nhất.
