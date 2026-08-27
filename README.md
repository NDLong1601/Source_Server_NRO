# Teamobi 2026 – NRO Server Source

Đây là mã nguồn máy chủ Java cho dự án **Ngọc Rồng Online / Teamobi 2026**. Repository bao gồm source server, cấu hình vận hành, dữ liệu và asset client, script build/khởi động, cơ sở dữ liệu SQL, cùng bộ công cụ quản trị dành cho game master.

Mục tiêu của dự án là phát triển nội dung NRO theo hướng có thể cấu hình, phát hành và kiểm tra đồng bộ: thay đổi Java, database, item, map và asset đều được quản lý trong cùng một source code.

## Thành phần chính

| Khu vực | Vai trò |
| --- | --- |
| `src/` | Mã nguồn Java của server: nhân vật, map, NPC, quái, boss, item, nhiệm vụ, sự kiện và network. |
| `data/` | Dữ liệu runtime và asset x1–x4 cho icon, mob, map, background và part nhân vật. |
| `sql/` | Database gốc, migration và script rollback cho các nội dung mới. |
| `admin_data_menu.hta` + `admin_data_menu/` | Giao diện quản trị dữ liệu game: item, shop, boss, mob, event, nhiệm vụ, map, NPC, cải trang… |
| `server_menu.hta`, `run.bat`, `tools/server_control.ps1` | Dashboard, script build, start/stop/restart, theo dõi trạng thái và log server. |
| `map-tools/` | Công cụ tạo, kiểm tra, preview và phát hành map có snapshot/rollback. |
| `costume-tools/` | Workspace lưu draft và mapping asset cho cải trang. |
| `assets/` | Asset nguồn, atlas và ảnh preview phục vụ quy trình tạo mob/codex. |

## Các hệ thống đã phát triển

### Vận hành server và quản trị dữ liệu

- Dashboard điều khiển server với thao tác start, stop, restart, build, xem log và báo trạng thái/PID rõ ràng.
- Quy trình full build Java 17 an toàn: tạo backup `20.jar`, kiểm tra archive, rollback khi đóng gói thất bại và tránh cập nhật class rời rạc.
- Theo dõi log vận hành, log quản trị và lịch sử snapshot khi thay đổi dữ liệu từ menu admin.
- Menu quản trị dữ liệu được tách theo module, có các tab cho người chơi, pet, radar, item, shop, boss, mob, event, gift code, ghép đồ, nhiệm vụ, NPC, map, cải trang và kỹ năng.
- Cải thiện quản lý item: tra cứu/preview icon, lọc option, dùng bộ option mặc định tập trung và hiển thị thông báo thao tác rõ ràng hơn.
- Cấu hình quà hộp trực tiếp trong admin, bao gồm danh sách phần thưởng và logic mở hộp phía server.

### Gameplay, nhân vật và vật phẩm

- Hoàn thiện cấu hình pet và AI pet; bổ sung/sửa hiển thị chỉ số, đổi tên pet, radar và các giới hạn có thể điều chỉnh qua file cấu hình.
- Hệ thống event mùa vụ với cấu hình tập trung, NPC/event boss, phần thưởng, shop và điều khiển từ menu admin.
- Nhiệm vụ được tách thành cấu hình riêng; hỗ trợ phần thưởng tùy biến, nhiệm vụ bang, nhiệm vụ phụ và quản lý qua giao diện admin.
- Bổ sung vật phẩm, icon đủ bốn tỉ lệ x1–x4, trang bị nhẫn/nhẫn đệ tử, nâng cấp nhẫn và các công thức ghép liên quan.
- Thêm ghép mảnh trang bị Hủy Diệt và cơ chế mở rương/phần thưởng tương ứng.
- Hỗ trợ thẻ đổi tên nhân vật có kiểm tra Unicode, trùng tên, độ dài cấu hình được, cập nhật dữ liệu quan hệ xã hội và không trừ thẻ khi thao tác thất bại.
- Nâng chất lượng dữ liệu PK với lịch sử PK và các điều chỉnh hiển thị/chỉ số nhân vật, đệ tử.

### Bang hội và tính năng xã hội

- Bang chủ có thể đổi tên bang với kiểm tra quyền, tên trùng, dữ liệu hợp lệ và chi phí ngọc.
- Thành viên bang có thể chia sẻ vị trí map/khu vực/tọa độ trong chat bang; server tự lấy vị trí thực và áp dụng thời gian chờ chống spam.
- Làm mới tên trong danh sách bạn bè/kẻ thù sau khi nhân vật đổi tên.

### Kỹ năng, NPC, map và nội dung Demon Slayer

- Hệ thống **tinh thông kỹ năng**: tích lũy khi dùng kỹ năng hợp lệ, nâng cấp sau cấp 7, cấu hình ngưỡng/hiệu ứng/chỉ số và màn hình quản trị riêng.
- Tab tạo NPC, tài sản NPC nhiều lớp chuẩn hóa và các nội dung NPC/nhiệm vụ Kanao.
- Công cụ tạo map tích hợp: tạo/import draft, biên dịch map plan, kiểm tra hợp đồng dữ liệu, preview sạch/debug, release plan, publish có transaction và rollback.
- Thêm chuỗi map **Vô Hạn Thành** cùng dữ liệu tile/background runtime.
- Thêm mob quỷ tùy biến cho Vô Hạn Thành, asset nguồn, atlas x1–x4, preview hành động, migration SQL và kiểm tra tương thích client.
- Workspace tạo cải trang với draft local, mapping HEAD/BODY/LEG, offset, kiểm tra asset và quy trình QA sprite trước khi phát hành.

### Sự kiện câu cá

- Hệ thống câu cá chạy thuần phía server trên 5 map, có NPC Ngư Dân, cần câu, mồi, dây, phao, máy câu, lưỡi câu và vật phẩm hỗ trợ.
- Có cơ chế chọn/trang bị ngư cụ, độ bền/sửa chữa, tỷ lệ câu, cá thường/cá khổng lồ, đổi cá, cửa hàng, chế tạo và rương Ngư Phủ.
- Bổ sung dọn rác biển, Điểm Làm Sạch, phần thưởng theo ngày và nhiệm vụ câu cá nhiều cấp độ.
- Sổ tay cá (fishing codex) với tiến độ bắt cá, ảnh minh họa mob và asset atlas riêng cho từng loài.
- Buff Máy dò cá/Bùa May mắn có thời hạn hiển thị phía client; trạng thái ngư cụ đang dùng được đánh dấu trực quan trong túi đồ.
- Có migration, cập nhật cache item/mob và kiểm tra kích thước packet để tương thích client cũ.

## Mốc phát triển

| Thời gian | Nội dung nổi bật |
| --- | --- |
| 08–11/08/2026 | Khởi tạo source; hoàn thiện dashboard, log, build/backup JAR, menu admin, pet/radar và event mùa vụ. |
| 12–16/08/2026 | Tái cấu trúc nhiệm vụ, phần thưởng tùy biến, quản lý/preview item, option mặc định, hộp quà, nhẫn và ghép Hủy Diệt. |
| 17–24/08/2026 | Chuẩn hóa quy trình build Java; tạo NPC, Vô Hạn Thành, mob quỷ, tinh thông kỹ năng, công cụ cải trang và QA sprite. |
| 26/08/2026 | Phát hành hệ thống câu cá mở rộng: ngư cụ, buff, nhiệm vụ, sổ tay cá, asset/codex và migration dữ liệu. |
| 27/08/2026 | Bổ sung đổi tên nhân vật, đổi tên bang, chia sẻ vị trí bang và lịch sử PK. |

## Chạy và build nhanh

Yêu cầu chính: Windows, PowerShell, Java/JDK 17, dependency trong `lib/` và database đã được cấu hình trong `Config.properties`.

```powershell
# Mở dashboard điều khiển server
.\run.bat

# Build đầy đủ source Java và cập nhật 20.jar an toàn
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\server_control.ps1 -Action build

# Khởi động / xem trạng thái server
.\run.bat start
.\run.bat status

# Mở menu quản trị dữ liệu
.\admin_menu.bat
```

Trước khi thay đổi Java, asset hoặc dữ liệu runtime, đọc [BUILD_JAVA_STANDARD.md](BUILD_JAVA_STANDARD.md). Tài liệu này quy định quy trình build, kiểm tra packet/cache, kiểm thử hai tài khoản và rollback `20.jar`.

## Tài liệu liên quan

- [Quy trình build Java](BUILD_JAVA_STANDARD.md)
- [Cấu trúc menu quản trị](admin_data_menu/README.md)
- [Công cụ tạo map](map-tools/README.md)
- [Workspace cải trang](costume-tools/README.md)

---

README được tổng hợp theo lịch sử từ commit khởi tạo `f06e6bdb4` đến nhánh `main` hiện tại (27/08/2026).
