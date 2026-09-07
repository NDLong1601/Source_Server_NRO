# Quy trình build Java chuẩn cho NRO Server

Tài liệu này là tiêu chuẩn bắt buộc khi sửa Java, thêm tính năng, thay đổi dữ liệu vật phẩm/assets hoặc tạo lại `20.jar` trong repository server này.

## 1. Phạm vi và nguyên tắc

- Chỉ thao tác trong repository server hiện tại: `source-server-nro/source-server`.
- Chế độ đang sử dụng là local mode. Không sửa, build, clean hoặc phụ thuộc vào `client-nro-unity`.
- Xem source Java, cấu hình, dữ liệu database, assets và `20.jar` là một đơn vị phát hành. Một phần thay đổi mà các phần còn lại chưa đồng bộ có thể làm client lỗi dù Java compile thành công.
- Bản phát hành phải được full compile. Compile riêng vài file chỉ được dùng để chẩn đoán nhanh.
- Dừng server trước khi thay `20.jar`; giữ một bản JAR tốt để rollback.
- Không xóa, ghi đè hoặc hoàn tác thay đổi đang có của người dùng.
- Không kết luận build thành công chỉ vì tài khoản đầu tiên hoạt động.

## 2. Các nguyên nhân đã gặp thực tế

### 2.1 Lombok không tương thích JDK

Khi dùng JDK 23 với Lombok cũ, compiler có thể báo hàng loạt lỗi giả như thiếu getter, setter, builder hoặc biến log ở nhiều class không liên quan. Đây thường là annotation processor không chạy đúng, không phải tất cả model cùng hỏng.

Tiêu chuẩn hiện tại:

- bytecode mục tiêu: Java 17;
- compile bằng `javac --release 17`;
- Lombok nằm trên cả classpath và annotation processor path;
- với JDK 23, dùng `lib/lombok.jar` phiên bản `1.18.36` trở lên.

Không chữa lỗi kiểu này bằng cách tự viết lại hàng loạt getter/setter trước khi kiểm tra Lombok.

### 2.2 Cập nhật JAR bằng `jar uf` làm hỏng ZIP

Triệu chứng thường gặp:

```text
java.util.zip.ZipException: invalid entry size
```

Không tiếp tục vá class vào một archive đang bị nghi hỏng. Khôi phục JAR tốt rồi build lại qua `tools/server_control.ps1`. Controller sử dụng cập nhật ZIP an toàn, tạo backup, kiểm tra kết quả và rollback khi thất bại.

### 2.3 Partial build làm source và runtime khác nhau

Source mới không bảo đảm server đang chạy code mới. Nếu chỉ copy một số `.class`, các inner class, router, enum, dependency hoặc class phát sinh khác có thể vẫn là bản cũ. Tính năng có thể hiện một phần, chạy nhánh cũ hoặc chỉ lỗi ở tài khoản thứ hai.

### 2.4 PowerShell coi warning của `javac` là lỗi dừng

`javac` có thể ghi warning ra stderr nhưng vẫn trả exit code `0`. Với `$ErrorActionPreference = 'Stop'`, PowerShell có thể dừng sai. Build script phải:

1. lưu error preference hiện tại;
2. cho native compiler tiếp tục ghi output;
3. lưu `$LASTEXITCODE` ngay sau `javac`;
4. khôi phục preference;
5. quyết định thành công/thất bại bằng exit code.

## 3. Kiểm tra trước khi build

Chạy từ thư mục gốc repository:

```powershell
git status --short
java -version
javac -version
java -jar .\lib\lombok.jar --version
Get-FileHash -Algorithm SHA256 -LiteralPath .\20.jar
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\server_control.ps1 -Action status
```

Ghi lại:

- các file người dùng đang sửa;
- phiên bản Java, `javac`, Lombok;
- SHA-256 của `20.jar` hiện tại;
- trạng thái server và cổng `14445`;
- vị trí backup tốt gần nhất.

Không in file cấu hình chứa mật khẩu database ra terminal hoặc báo cáo.

## 4. So sánh các phiên bản Git khi có regression

So sánh cả source và file build:

```powershell
git log --date=iso --stat -- .\20.jar
git log --date=iso --stat -- .\src .\tools .\lib\lombok.jar
git log -p -- .\tools\server_control.ps1
```

Với file nghi vấn cụ thể:

```powershell
git log --follow -p -- .\src\duong-dan\FileNghiVan.java
git diff <commit-tot>..<commit-loi> -- .\src\duong-dan\FileNghiVan.java
```

Nguyên tắc phân tích:

- xác định commit cuối cùng đã kiểm thử cả hai tài khoản;
- không coi commit mới nhất có `20.jar` là bằng chứng JAR được build từ đúng source;
- kiểm tra thay đổi ở session, phiên bản dữ liệu, packet item, router/menu và icon cùng thời điểm;
- ưu tiên tìm thay đổi có thể giữ state của tài khoản A sang tài khoản B.

## 5. Quy trình build chính thức

### Bước 1: Dừng server

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\server_control.ps1 -Action stop
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\server_control.ps1 -Action status
```

Xác nhận cổng `14445` đã được giải phóng trước khi thay JAR.

### Bước 2: Full build

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\server_control.ps1 -Action build
```

Build hợp lệ phải thực hiện đầy đủ:

1. tìm toàn bộ production source Java;
2. compile với `--release 17`;
3. nạp dependency và Lombok đúng cách;
4. kiểm tra exit code compiler;
5. tạo backup `20.jar` có timestamp;
6. cập nhật toàn bộ class vào archive bằng ZIP API an toàn; loại inner class cũ của outer class vừa compile, giữ nguyên dependency/resource không thuộc bản build;
7. kiểm tra JAR sau cập nhật;
8. tự rollback nếu đóng gói hoặc kiểm tra thất bại;
9. dọn thư mục build tạm.

Không thay lệnh này bằng `jar uf` hoặc copy vài class cho bản bàn giao.

### Bước 3: Kiểm tra class trong JAR

```powershell
jar tf .\20.jar | Select-String 'NangCapNgoaiTrang|ItemData|MySession|DataGame'
Get-FileHash -Algorithm SHA256 -LiteralPath .\20.jar
```

Thêm tên tất cả class vừa sửa vào biểu thức kiểm tra. Với inner class, xác nhận cả các entry có ký tự `$` nếu có.

### Bước 4: Khởi động và kiểm tra log

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\server_control.ps1 -Action start
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\server_control.ps1 -Action status
```

Yêu cầu tối thiểu:

- process server đang chạy đúng `20.jar` vừa tạo;
- cổng `14445` đang listen;
- log lỗi hiện tại không có exception mới;
- không còn process server cũ chạy song song.

## 6. Kiểm tra protocol và cache assets

### 6.1 Giới hạn packet

Các payload được ghi với độ dài 16-bit phải nhỏ hơn hoặc bằng `65535` byte. Không gộp dữ liệu chỉ vì tổng dữ liệu Java vẫn nằm trong memory.

Luồng item-template hiện tại phải có:

- đúng 1 packet reload;
- tiếp theo là một hoặc nhiều packet append có range liên tục;
- kết thúc bằng đúng 1 packet completion xác nhận tổng số template;
- mỗi packet không vượt `65535` byte.

Kích thước và số packet có thể thay đổi khi thêm dữ liệu, nhưng không được vượt giới hạn, trùng range, bỏ hổng ID hoặc thay đổi thứ tự.

### 6.2 Protocol probe

Kiểm tra kết nối thông thường:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\codex_protocol_probe.ps1 -Zoom 1
```

Kiểm tra reconnect khi client không gửi lại loại/phiên bản theo nhánh cũ:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\codex_protocol_probe.ps1 -SkipClientType -Zoom 1 -IconId 16187
```

Probe phải xác nhận packet item, thứ tự reload/append và khả năng lấy icon. Nếu probe lỗi, không bàn giao JAR dù server vẫn mở cổng.

### 6.3 Tăng phiên bản cache đúng lúc

Tăng version dữ liệu tương ứng khi thay:

- item template hoặc cách serialize item;
- mapping icon;
- asset mà client có thể đã cache;
- cấu trúc packet cần client tải lại.

Không tăng version tùy tiện cho thay đổi thuần logic server. Version sai gây tải lại thừa; không tăng khi cần gây client giữ dữ liệu cũ.

### 6.4 Kiểm tra đủ tỷ lệ ảnh

Với icon/assets mới hoặc được sửa, kiểm tra đầy đủ `x1`, `x2`, `x3`, `x4`:

- đúng icon ID và tên file;
- đúng chữ hoa/thường;
- ảnh đọc được, không rỗng;
- kích thước hợp lý với UI;
- client nhận đúng tỷ lệ theo zoom đã thương lượng.

Một ảnh hiển thị ở tài khoản đầu tiên hoặc zoom đang dùng không chứng minh các tỷ lệ khác hợp lệ.

## 7. Checklist thêm hoặc nâng tính năng Java

Áp dụng cho `NangCapNgoaiTrang` và các tính năng tương tự:

- [ ] Source chính đã có và full compile.
- [ ] Constant/action ID không trùng.
- [ ] Router/controller gọi đúng nhánh.
- [ ] NPC/menu mở được màn hình tính năng.
- [ ] Điều kiện đầu vào và thông báo lỗi đầy đủ.
- [ ] Nhánh thành công cập nhật dữ liệu đúng.
- [ ] Database/config cần thiết đã tồn tại.
- [ ] Item template và option cần thiết đã tồn tại.
- [ ] Icon/assets có đủ tỷ lệ.
- [ ] Class chính và inner class đều nằm trong `20.jar`.
- [ ] Dữ liệu được lưu và đọc lại sau relogin.
- [ ] Hai tài khoản có trạng thái khác nhau không làm rò state.

Với cấu hình `NangCapNgoaiTrang` hiện tại, cần đặc biệt xác minh nhóm template ID `2052` đến `2061` và các item option liên quan như `72`, `209` vẫn khớp dữ liệu đang chạy. Không tự động sửa database chỉ dựa trên tài liệu này; phải kiểm tra schema và dữ liệu thực tế trước.

## 8. Kiểm thử bắt buộc hai tài khoản

Đây là test phát hành, không phải test tùy chọn.

1. Mở một cửa sổ client.
2. Đăng nhập tài khoản A.
3. Chờ tải xong dữ liệu, map, inventory, icon và menu.
4. Mở tính năng vừa sửa và ít nhất một panel dùng item/icon.
5. Đăng xuất nhưng không đóng cửa sổ.
6. Đăng nhập tài khoản B có dữ liệu nhân vật khác A.
7. Kiểm tra lại map, inventory, icon, NPC/menu và tính năng.
8. Xác nhận không có mảng màu be, ảnh mất, menu trống hoặc dữ liệu của A còn sót.
9. Kiểm tra log server ngay sau bước lỗi hoặc thành công.

Nếu chỉ tài khoản B lỗi, kiểm tra theo thứ tự:

1. state session/player có được reset hoàn chỉnh không;
2. version, zoom và client type có bị mất hoặc dùng lại sai không;
3. packet tải dữ liệu có gửi đủ và đúng thứ tự không;
4. cache version của item/icon có khiến client bỏ qua dữ liệu mới không;
5. icon tại zoom đang dùng có tồn tại không;
6. menu/panel có exception trong lúc dựng dữ liệu riêng của B không;
7. có static/global state giữ player, inventory, menu hoặc service của A không.

## 9. Lỗi nhanh và cách sửa

### Hàng loạt `cannot find symbol` cho getter/setter/log

**Nguyên nhân ưu tiên:** Lombok hoặc annotation processing.

**Cách sửa:** kiểm tra phiên bản Lombok, classpath, `-processorpath`, dùng `--release 17`, rồi full compile. Chỉ sửa source model khi đã chứng minh annotation processing hoạt động đúng.

### `ZipException: invalid entry size`

**Nguyên nhân ưu tiên:** JAR đã bị cập nhật không an toàn hoặc bị ngắt giữa chừng.

**Cách sửa:** dừng server, khôi phục backup tốt, build lại qua controller, kiểm tra `jar tf` và hash. Không vá tiếp archive lỗi.

### Build báo thất bại nhưng chỉ có warning compiler

**Nguyên nhân ưu tiên:** PowerShell dừng vì stderr.

**Cách sửa:** bắt exit code native đúng cách trong script. Không bỏ qua exit code thật và không tắt toàn bộ kiểm tra lỗi của build.

### Source đã sửa nhưng tính năng không xuất hiện

**Nguyên nhân ưu tiên:** partial build, class/inner class thiếu, router chưa nối hoặc server chạy JAR cũ.

**Cách sửa:** full build, kiểm tra entry trong JAR, hash JAR runtime, router/menu/config và restart server.

### Tài khoản thứ hai mất ảnh hoặc màn hình màu be

**Nguyên nhân ưu tiên:** session/cache/protocol/zoom còn state cũ hoặc packet item bị thiếu/quá dài.

**Cách sửa:** chạy probe thường và reconnect; kiểm tra giới hạn packet, thứ tự reload/append, version cache, profile phiên kết nối, x1-x4 và static state.

### `UnsupportedClassVersionError`

**Nguyên nhân ưu tiên:** class được build cho Java mới hơn runtime.

**Cách sửa:** build toàn bộ bằng `--release 17`, xác nhận runtime mục tiêu, không trộn class từ build cũ.

## 10. Rollback an toàn

Nếu JAR mới không khởi động hoặc test A → B thất bại:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\server_control.ps1 -Action stop
Copy-Item -LiteralPath '.\20.jar.bak_<timestamp>' -Destination '.\20.jar' -Force
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\server_control.ps1 -Action start
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\server_control.ps1 -Action status
```

Trước khi copy:

- thay `<timestamp>` bằng đường dẫn backup chính xác đã xác minh;
- xác nhận target là `20.jar` trong repository này;
- ghi lại hash JAR lỗi và JAR được khôi phục;
- giữ source lỗi để phân tích, không `git reset --hard`.

## 11. Checklist trước commit hoặc bàn giao

- [ ] `git status --short` không có file lạ do build sinh ra.
- [ ] Đã xem diff và không chạm `client-nro-unity`.
- [ ] Full compile thành công.
- [ ] Compiler dùng Java 17 target và Lombok tương thích.
- [ ] `20.jar` mở được và chứa đủ class thay đổi.
- [ ] Đã ghi SHA-256 của JAR.
- [ ] Server chạy đúng cổng `14445`.
- [ ] Log không có exception mới.
- [ ] Protocol probe thường thành công.
- [ ] Protocol probe reconnect thành công.
- [ ] Packet không vượt `65535` byte.
- [ ] Assets có đủ tỷ lệ cần thiết.
- [ ] Tính năng mới chạy cả nhánh lỗi, thành công và lưu dữ liệu.
- [ ] Test tài khoản A → logout → B trong cùng cửa sổ thành công.
- [ ] Không stage log, temp, backup JAR hoặc thay đổi không liên quan.

## 12. Mẫu báo cáo build

```text
JAR: <đường dẫn tuyệt đối>
SHA-256: <hash>
Java / javac / Lombok: <versions>
Full compile: <thành công/thất bại>, <số class>
Class quan trọng trong JAR: <danh sách>
Packet item: reload=<bytes>, append=<bytes>, thứ tự=<kết quả>
Protocol probe: normal=<kết quả>, reconnect=<kết quả>
Server: port 14445=<kết quả>, error log=<kết quả>
Feature: <nhánh đã test>
Relogin A -> B cùng cửa sổ: <kết quả>
Backup/rollback: <đường dẫn và trạng thái>
Chưa kiểm thử: <nếu có>
```
