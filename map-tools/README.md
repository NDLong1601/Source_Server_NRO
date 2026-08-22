# Teamobi Map Tools

`map-tools` là lõi project/biên dịch map độc lập dành cho admin map editor. Compiler luôn tạo bundle staging trước; Giai đoạn 2 bổ sung project draft, import map runtime, preview trong tab **Tạo Map** và publish có kiểm tra/rollback.

Dependency Python được khóa trong `requirements.txt` (`jsonschema` và `Pillow`).

## Biên dịch map

```powershell
python .\map-tools\tools\compile_map_plan.py .\map-tools\examples\stage1-demo\teamobi-map-plan.json
```

Kết quả mặc định nằm tại `map-tools/output/map-<id>`:

- `compiled-tile-map.json`: ma trận tile đã resolve.
- `compiled-bg-map.json`: background item và thông tin layer/asset.
- `compiled-map-template.json`: metadata sẵn sàng cho bước publish database.
- `runtime/tile_map_data/<id>`: binary canonical `[W][H][W*H tiles]`.
- `runtime/item_bg_map_data/<id>`: binary canonical Big-Endian.
- `map-template.sql`: SQL preview, không tự thực thi.
- `preview_clean.png` và `preview_debug.png`.
- `validation-report.json`: lỗi/cảnh báo có mã ổn định.
- `manifest.json`: SHA-256 của mọi artifact để kiểm tra trước publish.

Khi output đã tồn tại, compiler dừng để tránh ghi đè. Dùng `--force` để thay thế atomically; bản trước được giữ ở thư mục `.previous` cạnh output.

```powershell
python .\map-tools\tools\compile_map_plan.py .\map-tools\examples\stage1-demo\teamobi-map-plan.json --force
```

## Project Map và tab Tạo Map (Giai đoạn 2)

Mở `admin_menu.bat`, chọn tab **Tạo Map**. Tab này hỗ trợ:

- tạo draft mới hoặc import tile/background runtime và `map_template` hiện có;
- lưu plan JSON theo revision, giữ bản trước để phục hồi;
- compile, validate manifest và xem `preview_clean.png`/`preview_debug.png` trực tiếp;
- lập release plan, hiển thị blocker và chỉ cho publish khi server đã dừng;
- publish tile/background + `map_template` + tăng `data/map/version.txt`, đồng thời lưu snapshot rollback trong `map-tools/releases`.

Draft nằm trong `map-tools/projects` và build nằm trong `map-tools/output/projects`. Hai vùng này tách biệt hoàn toàn khỏi `data/map` cho tới khi người quản trị bấm **Publish** và xác nhận.

CLI tương ứng:

```powershell
python .\map-tools\tools\manage_map_projects.py --repo-root . list
python .\map-tools\tools\manage_map_projects.py --repo-root . create --map-id 186 --name "Map mới" --width 40 --height 20 --tile-set-id 2
python .\map-tools\tools\manage_map_projects.py --repo-root . compile --project-id map-186
python .\map-tools\tools\manage_map_projects.py --repo-root . release-plan --project-id map-186
```

`publish-runtime`, `rollback-runtime` và `finalize-release` là primitive giao dịch cho bridge PowerShell. Khi publish từ admin, bridge snapshot dòng DB cũ, cài runtime, chạy UPSERT trong transaction và tự rollback runtime nếu DB thất bại. Công cụ không tự restart server.

## Canvas và custom asset (Giai đoạn 3)

Tab **Tạo Map** có ba chế độ làm việc:

- **Canvas:** vẽ/xóa tile theo ô 24px; kéo asset; đặt hoặc kéo Player, NPC, Mob; kéo chuột tạo vùng điểm chạm.
- **JSON:** chỉnh toàn bộ map plan khi cần cấu hình chi tiết.
- **Compiled Preview:** xem ảnh clean/debug được render bởi compiler và release plan.

Khi canvas thay đổi terrain semantic, editor materialize kết quả thành `terrain.matrix`. Điều này đảm bảo những gì lưu trong draft là ma trận chính xác đang nhìn thấy trên canvas.

### Upload asset x4

Trong palette **Asset Layer 1–4**, chọn PNG nguồn và cấu hình `layer`, `dx`, `dy`. PNG nguồn được xem là chuẩn x4; có thể nhập W/H x4 mới hoặc để `0/0` để giữ nguyên. Project service tự sinh:

```text
assets/x4/<imageId>.png  100%
assets/x3/<imageId>.png   75%
assets/x2/<imageId>.png   50%
assets/x1/<imageId>.png   25%
```

Asset chỉ nằm trong project staging cho tới khi được đặt lên map, compile và publish. Bundle có custom asset sẽ chứa:

- `runtime/item_bg_temp/x1..x4/<imageId>.png`;
- `bg-item-template.sql` cho Layer/offset;
- checksum của từng PNG trong manifest.

Publish snapshot cả PNG runtime và các dòng `bg_item_template`. Nếu database thất bại, PNG/tile/background/version được rollback. Nếu publish thành công, restart server sẽ nạp template và client nhận version ảnh mới theo CRC.

## Kiểm thử

```powershell
python -m unittest discover -s .\map-tools\tests -p "test_*.py" -v
```

## Hoàn thiện editor production (Giai đoạn 4)

### Tile client thật

Repository có tile PNG client dưới dạng resource không có phần mở rộng:

```text
data/res/x2/<tileset>$<tileId>  48x48
data/res/x3/<tileset>$<tileId>  72x72
data/res/x4/<tileset>$<tileId>  96x96
```

Editor ưu tiên x2 và scale về ô logic 24px. Compiler dùng cùng asset này khi tạo `preview_clean.png` và `preview_debug.png`, đồng thời đưa checksum tile đã dùng vào `manifest.json`. `compiled-tile-map.json` ghi `resolvedTileIds` và `missingTileIds`; tile thiếu PNG vẫn có màu deterministic fallback và không làm thay đổi binary runtime.

### Lịch sử thao tác và autosave

- Undo/redo tối đa 80 snapshot theo từng nét vẽ, lần kéo, đặt/xóa entity, paste hoặc resize.
- Phím tắt: `Ctrl+Z`, `Ctrl+Y`/`Ctrl+Shift+Z`, `Ctrl+C`, `Ctrl+V`, `Delete` và `Ctrl+S`.
- Copy/paste hỗ trợ asset, NPC, Mob và waypoint; bản dán lệch một ô 24px.
- Autosave mặc định bật, chạy sau 1,8 giây không có thao tác và tạo revision project mới. JSON chưa hợp lệ không được autosave.

### Resize map có kiểm soát

Canvas hỗ trợ kích thước `1..127` tile và 9 anchor. Trước khi áp dụng, backend materialize terrain semantic, dịch toàn bộ matrix/asset/entity/waypoint đúng hệ tọa độ, rồi báo:

- số ô và tile đặc bị cắt;
- entity/waypoint bị clamp vào biên map;
- background item bị clamp vào vùng cho phép `-5..W/H+5`;
- offset tile được dùng để người quản trị kiểm tra.

Resize chỉ cập nhật draft trên màn hình và có thể `Ctrl+Z`; vẫn phải Compile & Preview để kiểm tra ground-alignment trước publish.

## Vận hành release an toàn (Giai đoạn 5)

Mỗi lần publish thành công tạo một release v2 bất biến trong `map-tools/releases/<projectId>/<releaseId>` gồm:

- `deployed/`: đúng payload tile, background và asset x1–x4 đã được cài vào runtime;
- `backup/`: trạng thái runtime và `version.txt` ngay trước publish;
- `manifest.json`, `publish.sql`, `rollback-database.sql` và `database-before.json`;
- `release.json`: checksum payload, checksum gói release, map version trước/sau publish và dữ liệu DB mong đợi.

Tab **Releases** cho phép xem lịch sử, chọn một release và chạy **Verify Runtime + DB** sau khi restart. Việc verify đối chiếu đồng thời:

- checksum file runtime và payload lưu trong release;
- checksum các file manifest/SQL/snapshot của chính gói release;
- `data/map/version.txt`;
- dòng `map_template` và các `bg_item_template` custom.

Release plan còn kiểm tra trước các phụ thuộc gameplay. Waypoint được chấp nhận khi map đích đã có trong database hoặc có project local đã Compile và Verify để triển khai trong cùng đợt dừng server. Publish vẫn bị khóa nếu không có cả hai nguồn này, hoặc NPC/Mob dùng template ID không có trong database. Khi dùng project local làm dependency, phải Publish đủ cả nhóm trước khi khởi động lại server.

### Rollback chủ động

Chỉ release `published` mới nhất của project được rollback và server phải dừng. Trước khi thay đổi dữ liệu, bridge yêu cầu runtime, database và toàn bộ checksum gói release vẫn khớp; phát hiện drift sẽ khóa thao tác. Database được rollback trong transaction, sau đó runtime/version được khôi phục từ backup đã xác minh. Nếu bước runtime lỗi, bridge chạy lại `publish.sql` để compensation database về release đang published; trạng thái sau rollback luôn được verify lại và không báo thành công nếu còn sai lệch.

Công cụ không tự dừng hoặc restart server. Quy trình vận hành khuyến nghị:

1. Compile & Preview, kiểm tra release plan và các dependency blocker.
2. Dừng server, Publish, rồi khởi động lại server.
3. Mở **Releases**, chọn release vừa publish và chạy **Verify Runtime + DB**.
4. Nếu cần rollback: dừng server, verify, rollback, khởi động lại và verify lần nữa.

CLI đọc release tương ứng:

```powershell
python .\map-tools\tools\manage_map_projects.py --repo-root . list-releases --project-id map-186
python .\map-tools\tools\manage_map_projects.py --repo-root . get-release --project-id map-186 --release-id <release-id>
python .\map-tools\tools\manage_map_projects.py --repo-root . verify-release --project-id map-186 --release-id <release-id>
```

## Nghiệm thu production và hướng dẫn tại chỗ (Giai đoạn 6)

Mode **Nghiệm thu** nối kiểm tra máy chủ với việc test thực tế trong client. Mỗi release có thể có một file `acceptance.json` sidecar; file này chỉ ghi người kiểm tra, ghi chú và checklist, không sửa payload hoặc checksum release.

Trạng thái **SẴN SÀNG NGHIỆM THU** chỉ xuất hiện khi tất cả điều kiện sau cùng đạt:

- release vẫn ở trạng thái `published`;
- tiến trình Java `20.jar` đang chạy và Java đang lắng nghe `server.port` (mặc định `14445`);
- Runtime + map version + gói release + database đều khớp release;
- người kiểm tra đã nhập tên và hoàn thành các mục bắt buộc trong client.

Checklist luôn yêu cầu đăng nhập, vào map, đối chiếu preview, layer, collision, asset x1–x4 và client log. Điểm chạm/NPC/Mob chỉ trở thành mục bắt buộc khi release thật sự có loại đối tượng đó.

CLI sidecar nghiệm thu:

```powershell
python .\map-tools\tools\manage_map_projects.py --repo-root . get-acceptance --project-id map-186 --release-id <release-id>
python .\map-tools\tools\manage_map_projects.py --repo-root . save-acceptance --project-id map-186 --release-id <release-id> --acceptance-json <qa.json>
```

Mode **Hướng dẫn** trong chính tab **Tạo Map** trình bày trọn quy trình Draft → Canvas → Preview → Publish → Restart → Verify → Nghiệm thu, các phím tắt và quy tắc tile-space/pixel-space. Đây là hướng dẫn vận hành chính; không cần mở tài liệu ngoài khi đang thao tác.

### Quy trình thao tác ngắn

1. Tạo draft mới hoặc Import map runtime.
2. Vẽ tile, upload/đặt asset, đặt Player/NPC/Mob/Điểm chạm và lưu draft.
3. Compile & Preview; sửa hết lỗi ground/toạ độ.
4. Kiểm tra Publish; xử lý toàn bộ blocker dependency.
5. Dừng server và Publish.
6. Khởi động server, vào **Releases** và Verify Runtime + DB.
7. Vào **Nghiệm thu**, chạy Kiểm tra lại, test map trong client, điền người kiểm tra và lưu checklist.

## Giới hạn sau Giai đoạn 6

- Player/NPC/Mob trên canvas vẫn dùng marker hình học, chưa ghép sprite animation thật của client.
- Import runtime suy ra `playerSpawn` từ tile không rỗng đầu tiên; cần kiểm tra và chỉnh lại trước publish.
- Autosave chỉ chạy với trạng thái canvas/plan JSON hợp lệ; nội dung JSON đang gõ dở được giữ trên màn hình cho tới khi người dùng lưu hoặc quay lại Canvas.
- Publish vẫn yêu cầu server dừng và không tự restart server.
- Checklist ghi nhận kết quả test thực tế nhưng không tự điều khiển client; người vận hành vẫn phải đăng nhập và đi qua map sau khi server khởi động.
