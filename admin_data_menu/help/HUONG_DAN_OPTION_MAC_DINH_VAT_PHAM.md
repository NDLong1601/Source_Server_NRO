# Hướng dẫn cấu hình option mặc định cho vật phẩm

## 1. Mục đích

Tính năng này cho phép cấu hình option một lần cho từng `item_template`. Sau đó vật phẩm có thể được sử dụng lại ở nhiều nơi mà không phải nhập lại option:

- SHOP.
- Phần thưởng nhiệm vụ.
- Các luồng tạo vật phẩm sử dụng factory có hỗ trợ option mặc định.
- Một số luồng drop đang dùng option fallback từ SHOP.

Mô hình được sử dụng:

```text
item_template
    |
    +-- item_default_option  (cấu hình dùng chung)
    |
    +-- item_shop.option_mode = 0  -> kế thừa mặc định
    |
    +-- item_shop.option_mode = 1  -> option riêng của SHOP
```

## 2. Các file chính

### Giao diện quản trị

- `admin_data_menu/views/item-options.html`: giao diện tab Option mặc định.
- `admin_data_menu/js/tabs/item-options.js`: xử lý chọn vật phẩm, chọn option và lưu dữ liệu.
- `admin_data_menu.hta`: đăng ký nút điều hướng và script của tab.

### Backend quản trị

- `tools/admin_data.ps1`: các action đọc/lưu preset, validate dữ liệu và audit.

Các action chính:

```text
listitemdefaultoptionitems
listitemdefaultoptions
saveitemdefaultoptions
saveitemdefaultoptionsbulk
```

### Server Java

- `src/nro/models/server/Manager.java`: load cache preset khi server khởi động.
- `src/nro/models/services/ItemService.java`: factory tạo item có option mặc định.
- `src/nro/models/database/ShopDAO.java`: load option theo chế độ của SHOP.
- `src/nro/models/shop/ItemShop.java`: lưu `optionMode`.
- `src/nro/models/task/TaskRewardService.java`: phát phần thưởng nhiệm vụ có preset.

### Database

- `sql/item_default_option_migration.sql`: migration độc lập.
- `sql/team2026.sql`: schema đầy đủ cho database mới.

## 3. Cấu trúc database

### Bảng `item_default_option`

| Cột | Ý nghĩa |
| `item_template_id` | ID vật phẩm trong `item_template` |
| `option_id` | ID option trong `item_option_template` |
| `param` | Giá trị của option |
| `sort_order` | Thứ tự hiển thị/gắn vào item |

Khóa chính là cặp `(item_template_id, option_id)`, vì vậy cùng một vật phẩm không thể có trùng `option_id`.

### Cột `item_shop.option_mode`

| Giá trị | Ý nghĩa |
| `0` | Kế thừa option từ `item_default_option` |
| `1` | Dùng option riêng trong `item_shop_option` |

Các dòng SHOP cũ được giữ ở chế độ `1` khi migration để không thay đổi gameplay hiện tại.

## 4. Cấu hình option mặc định

1. Mở `admin_data_menu.hta`.
2. Chọn tab **Option mặc định**.
3. Tìm vật phẩm theo ID hoặc tên.
4. Chọn một dòng vật phẩm trong bảng bên trái.
5. Tích các option cần gắn.
6. Nhập `Param` cho từng option.
7. Bấm **Lưu option mặc định**.
8. Restart server để cache Java nạp dữ liệu mới.

Khi lưu danh sách rỗng, preset của vật phẩm sẽ được xóa. Vật phẩm khi đó không có option mặc định.

## 5. Quy tắc nhập dữ liệu

### Option ID

- Phải tồn tại trong `item_option_template`.
- Phải là số nguyên từ `0` đến `255`.
- Không được chọn trùng trong cùng một vật phẩm.

### Param

`param` phải nằm trong khoảng:

```text
-32768 .. 32767
```

Giới hạn này tương ứng với cách server gửi option cho client bằng `writeShort`.

### Ví dụ

```json
[
  {"id": 50, "param": 20},
  {"id": 77, "param": 20},
  {"id": 103, "param": 20},
  {"id": 93, "param": 30}
]
```

Ý nghĩa phụ thuộc vào tên option trong `item_option_template`.

## 6. Thêm vật phẩm vào SHOP

Khi thêm vật phẩm mới vào SHOP, trường **Option** có hai lựa chọn:

### Kế thừa mặc định

Chọn chế độ này khi SHOP sử dụng đúng preset đã cấu hình trong tab **Option mặc định**.

Ưu điểm:

- Không phải nhập lại option.
- Cùng một item dùng được ở nhiều SHOP.
- Thay đổi preset tập trung tại một nơi.

### Tùy chỉnh riêng

Chọn chế độ này khi dòng SHOP cần option khác với preset chung.

Khi lưu option trực tiếp trong khu vực **Option item shop**, hệ thống tự chuyển dòng SHOP sang chế độ tùy chỉnh riêng.

## 7. Phần thưởng nhiệm vụ

Phần thưởng nhiệm vụ vẫn lưu danh sách ID vật phẩm trong `task.properties`, ví dụ:

```properties
task.reward.main.1.items=14,15,16,17,18,19,20
```

Khi phát thưởng, server gọi factory:

```java
ItemService.gI().createNewItemWithDefaultOptions(itemId);
```

Do đó mỗi item trong phần thưởng sẽ tự lấy option từ `item_default_option`. Không cần lưu lại danh sách option riêng trong từng phần thưởng nhiệm vụ.

## 8. Migration dữ liệu hiện có

File migration:

```text
sql/item_default_option_migration.sql
```

Migration thực hiện:

1. Tạo bảng `item_default_option`.
2. Thêm cột `item_shop.option_mode`.
3. Giữ các dòng SHOP hiện tại ở chế độ tùy chỉnh riêng.
4. Import các bộ option SHOP không xung đột thành preset mặc định.

Các item có nhiều bộ option khác nhau giữa các SHOP không được tự động chọn. Admin cần tự quyết định preset trong tab **Option mặc định**.

Trước khi chạy migration nên tạo backup các bảng:

```text
item_template
item_option_template
item_shop
item_shop_option
```

## 9. Kiểm tra sau khi cấu hình

### Kiểm tra bằng admin action

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\admin_data.ps1 `
  -Action listitemdefaultoptionitems
```

Đọc preset của một item:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\admin_data.ps1 `
  -Action listitemdefaultoptions `
  -Id 14
```

### Kiểm tra trực tiếp database

```sql
SELECT item_template_id, option_id, param, sort_order
FROM item_default_option
WHERE item_template_id = 14
ORDER BY sort_order, option_id;
```

Kiểm tra chế độ SHOP:

```sql
SELECT id, temp_id, option_mode
FROM item_shop
WHERE temp_id = 14;
```

## 10. Cache và restart server

Preset được load vào cache khi `Manager` khởi động. Sau khi lưu preset mới:

1. Lưu trong admin.
2. Dừng hoặc restart server.
3. Mở lại SHOP hoặc nhận phần thưởng để kiểm tra.

Nếu chưa restart, database đã có dữ liệu mới nhưng server đang chạy vẫn có thể dùng cache cũ.

## 11. Các trường hợp đặc biệt

### Vật phẩm có option riêng theo từng SHOP

Giữ `option_mode = 1` và tiếp tục dùng `item_shop_option`.

### Vật phẩm không muốn có option mặc định

Xóa toàn bộ option trong tab **Option mặc định** hoặc chọn SHOP tùy chỉnh với danh sách option rỗng.

### Vật phẩm có option động/random

Không nên đưa option động vào preset nếu giá trị phải random theo lần tạo. Hãy để code hiện tại xử lý random sau khi tạo item.

### Item có option hard-code trong code

Preset mặc định không tự động ghi đè các option được thêm thủ công bởi các luồng đặc biệt. Cần kiểm tra từng luồng nếu muốn chuyển sang dùng preset.

## 12. Lỗi thường gặp

### Tab không xuất hiện

Chạy:

```powershell
node tools/check_admin_data_menu.js
```

Kiểm tra các file:

- `admin_data_menu.hta`
- `admin_data_menu/views/item-options.html`
- `admin_data_menu/js/tabs/item-options.js`

### Không tìm thấy bảng `item_default_option`

Chạy migration `sql/item_default_option_migration.sql`. Backend cũng có cơ chế tự tạo schema khi gọi action liên quan preset.

### SHOP vẫn không có option

Kiểm tra:

1. Item đã có dòng trong `item_default_option` chưa.
2. `item_shop.option_mode` có bằng `0` không.
3. Server đã restart chưa.
4. `option_id` có tồn tại trong `item_option_template` không.

### Option bị thay đổi ngoài ý muốn

Đảm bảo code đang dùng `getDefaultItemOptions(...)` hoặc `createNewItemWithDefaultOptions(...)`. Hai hàm này trả về option đã clone, không dùng trực tiếp object trong cache.

## 13. Checklist triển khai production

- [ ] Backup database.
- [ ] Chạy migration.
- [ ] Kiểm tra số dòng trong `item_default_option`.
- [ ] Kiểm tra các item có option xung đột.
- [ ] Cấu hình preset cho các item cần dùng.
- [ ] Kiểm tra SHOP ở chế độ kế thừa.
- [ ] Kiểm tra SHOP ở chế độ tùy chỉnh.
- [ ] Kiểm tra một phần thưởng nhiệm vụ.
- [ ] Build thành công source Java.
- [ ] Restart server.
- [ ] Kiểm tra log load `default item options`.
- [ ] Kiểm tra option thực tế trong client.

## 14. Lưu ý build hiện tại

Tính năng đã được thêm vào source và database. Khi build toàn bộ project, nếu gặp lỗi ở các module Radar hoặc `PlayerDAO`, cần xử lý các lỗi compile nền đó trước khi tạo JAR mới.

Sau khi build thành công, cần cập nhật JAR runtime theo quy trình build hiện tại của dự án rồi restart server để áp dụng code mới.
