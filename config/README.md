# Cấu hình runtime và Admin Menu

Đây là vị trí duy nhất cho các file `.properties` mà server và **NRO Admin Data**
đọc hoặc chỉnh sửa. Không đặt bản sao cấu hình ở thư mục gốc hoặc `data/`: server
không còn đọc các vị trí cũ.

| Nhóm | Vị trí |
| --- | --- |
| Máy chủ, database và sự kiện | `Config.properties` |
| Player, đệ tử, nhiệm vụ, Combine, thành thạo kỹ năng, Năng động | Các file `.properties` trực tiếp trong `config/` |
| Toàn bộ cấu hình bang | `clan/clan_*.properties` |
| Ảnh, icon, map và dữ liệu client | Vẫn ở `data/`; không phải cấu hình runtime |

Mã Java dùng `nro.config.ConfigPaths`; PowerShell của Admin Menu dùng whitelist
đường dẫn tương ứng. Khi thêm một file cấu hình có thể chỉnh từ admin menu, hãy
thêm nó vào cả hai registry và test đường dẫn trước khi đưa vào catalog UI.

Một số file được poll nóng (ví dụ Player, Combine, Task, Activity). Những cấu
hình bang và cấu hình server khác vẫn cần restart theo phạm vi hiển thị trong
Admin Menu. Việc di chuyển thư mục không tự restart server.
