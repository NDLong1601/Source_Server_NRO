# Workflow repository Teamobi2026/SRC

## Điểm tích hợp

| Thành phần | Vị trí |
|---|---|
| Mob template dump | `sql/team2026.sql`, bảng `mob_template` |
| Migration | `sql/migrations/<date>_add_<slug>.sql` |
| Ảnh/manifest/preview | `assets/mob/<slug>/` |
| Payload client | `data/mob/x1..x4/<mobId>` |
| Load template | `src/nro/models/server/Manager.java` |
| Gửi command 11 | `src/nro/models/data/DataGame.java` |
| Spawn map | `map_template.mobs` trong DB/release map |
| Probe giao thức | `tools/codex_protocol_probe.ps1` |
| Build/runtime | `tools/server_control.ps1`, `20.jar` |

## ID và template

`Manager` phải nạp `mob_template ORDER BY id ASC`. Client tạo mảng template và truy cập bằng ID, vì vậy không để hổng, trùng hoặc sắp sai thứ tự. Kiểm tra tối thiểu:

```sql
SELECT id,TYPE,NAME,hp,range_move,speed,dart_Type,percent_dame,percent_tiem_nang
FROM mob_template ORDER BY id;

SELECT COUNT(*) AS total_templates,
       MIN(id) AS min_id,
       MAX(id) AS max_id,
       COUNT(DISTINCT id) AS distinct_ids,
       COUNT(*) <= 127 AS signed_byte_safe,
       MIN(id) = 0 AND MAX(id) = COUNT(*) - 1 AS contiguous
FROM mob_template;
```

Schema dòng chuẩn:

```sql
INSERT INTO mob_template
  (id,TYPE,NAME,hp,range_move,speed,dart_Type,percent_dame,percent_tiem_nang)
VALUES
  (?, ?, ?, ?, ?, ?, ?, ?, ?)
ON DUPLICATE KEY UPDATE
  TYPE=VALUES(TYPE), NAME=VALUES(NAME), hp=VALUES(hp),
  range_move=VALUES(range_move), speed=VALUES(speed),
  dart_Type=VALUES(dart_Type), percent_dame=VALUES(percent_dame),
  percent_tiem_nang=VALUES(percent_tiem_nang);
```

Các loại movement theo client:

| TYPE | Ý nghĩa |
|---:|---|
| 0 | đứng |
| 1 | đi mặt đất |
| 2 | nhảy/đi |
| 3 | lết/di chuyển đặc biệt nhưng dùng bảng đi thường |
| 4 | bay |
| 5 | bay/đậu |

Mob ID đi qua byte ở nhiều nơi (`getByte`, command 11). Quan trọng hơn, `updateMap` cũng ghi **số lượng** template bằng `writeByte`; client mục tiêu đọc số lượng này có dấu. Vì bảng bắt đầu từ ID `0` và phải liên tục, cấu hình an toàn có tối đa 127 dòng với `MAX(id) <= 126`. Mob mới theo bảng 12 frame chỉ dùng `73..126`, không dùng ID đặc biệt 76. Không thêm ID 127 nếu việc đó tạo dòng thứ 128; hãy tái sử dụng một placeholder đã audit hoặc dừng để mở rộng client/protocol.

## Spawn map

Mỗi phần tử `map_template.mobs` có hợp đồng:

```text
[mobTemplateId, level, hp, x, y]
```

- Giữ mọi phần tử hiện có, chỉ append/thay đúng mob trong phạm vi.
- HP spawn và `mob_template.hp` nên khớp yêu cầu, trừ khi thiết kế map cố ý override.
- `x/y` là pixel. Map chuẩn dùng tile 24px; mob đất cần `y` trùng mặt collision.
- Với map thuộc hệ release/editor, sửa plan rồi compile/verify/publish qua workflow map của repository; không sửa một mình file binary hoặc một mình live DB.
- Re-query JSON sau cập nhật và xác nhận số lượng, ID, HP, từng tọa độ.

## DataGame và cache

`DataGame.requestMobTemplate` phải:

1. đọc `data/mob/x<zoom>/<id>`;
2. tạo message `11`;
3. ghi một byte ID;
4. ghi nguyên file;
5. gửi và cleanup.

Không bọc file thêm một byte-array length ở server vì file đã chứa hai length mà client cần. Không nhúng ID vào file.

Khi thêm hoặc sửa `mob_template`, tăng số trong `data/map/version.txt` một đơn
vị. `DataGame.vsMap` đọc file này lúc khởi động và `DataGame.updateMap` mới là
gói chứa toàn bộ danh sách `MobTemplate`. Không dùng riêng `vsData` cho thay đổi
mob: `vsData` chỉ làm mới `data/update_data/*` và khiến client vẫn giữ mảng mob
cũ, có thể treo khi map tham chiếu ID mới. Sau khi client từng nhận payload
hỏng, bắt buộc thoát hẳn tiến trình game; reconnect trong cùng process có thể
giữ `Mob.arrMobTemplate[id].data`.

## Build và kiểm tra

Trước build/restart:

- kiểm tra server đang chạy và người chơi/kết nối nếu môi trường có người dùng thật;
- kiểm tra `git diff --check` cho file đã sửa;
- chạy builder `verify`.

Quy trình repository:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/server_control.ps1 -Action stop
powershell -NoProfile -ExecutionPolicy Bypass -File tools/server_control.ps1 -Action build
powershell -NoProfile -ExecutionPolicy Bypass -File tools/server_control.ps1 -Action start
```

Chỉ chạy stop/start khi yêu cầu cho phép triển khai. Xác nhận `logs/menu_status.txt`, `logs/server.log`, `logs/server-error.log` và cổng cấu hình.

Probe từng zoom:

```powershell
1..4 | ForEach-Object {
  powershell -NoProfile -ExecutionPolicy Bypass -File tools/codex_protocol_probe.ps1 -Zoom $_ -MobId <id>
}
```

Probe phải so khớp SHA-256 file đã deploy và giải mã được outer payload. Đây là kiểm tra server gửi đúng byte; vẫn cần test hình/animation trong client.

## Checklist nghiệm thu

- ID liên tục, có đúng một dòng template, tổng số dòng không quá 127 và `MAX(id) = COUNT(*) - 1`.
- Năm source pose có alpha; preview đã quan sát.
- Bốn payload qua verify; metadata giống nhau, PNG scale đúng.
- Spawn tồn tại đúng map/toạ độ và không phá mob cũ.
- `vsMap` đã tăng khi template mob đổi; `vsData` chỉ tăng nếu raw update-data đổi; JAR mới chứa class đã build.
- Startup sạch; probe x1–x4 đạt.
- Client được đóng hoàn toàn rồi test đứng/đi/đánh/trúng đòn/chết/respawn và trái/phải.
