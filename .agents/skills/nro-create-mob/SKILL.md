---
name: nro-create-mob
description: Create, install, validate, or repair normal Teamobi NRO Java server mobs from action images, including command-11 EffectData/PNG payloads, x1-x4 atlases, mob_template rows, map spawns, client data refresh, build/restart, and protocol checks. Use for requests to tạo/thêm mob mới, đưa mob vào map, tạo animation mob, or fix an invisible/shadow-only mob. Do not use for bosses that require typeData 1/2 and custom frame tables.
---

# NRO Create Mob

Tạo mob thường hoàn chỉnh từ ảnh nguồn tới client. Coi sprite payload, `mob_template`, cấu hình spawn, cache client và JAR runtime là một tính năng duy nhất; không kết luận hoàn tất chỉ vì client đã nhận vị trí hoặc vẽ bóng.

## Tài liệu và công cụ bắt buộc

- Đọc [references/client-protocol.md](references/client-protocol.md) trước khi tạo hoặc sửa sprite. Đây là hợp đồng byte không được suy đoán.
- Đọc [references/repository-workflow.md](references/repository-workflow.md) trước khi chọn ID, sửa SQL/map hoặc triển khai.
- Dùng `scripts/build_mob_payload.py` cho mob thường mới thay vì tự ghép payload bằng mã dùng một lần.

## Xác định phạm vi

1. Nếu người dùng chỉ hỏi cần gì hoặc muốn đánh giá bộ ảnh: kiểm kê ảnh, đề xuất mapping và các dữ liệu còn thiếu; không sửa DB/server.
2. Nếu người dùng yêu cầu tạo tài nguyên nhưng chưa yêu cầu cài đặt: tạo manifest, atlas, preview và payload có thể review; không nhập live DB hoặc restart.
3. Nếu người dùng yêu cầu thêm mob vào game/map: thực hiện toàn bộ workflow bên dưới trong phạm vi server/map được nêu.
4. Nếu mob cần nhiều bộ phận ghép, frame table động, nhiều phase hoặc `typeData=1/2`: dừng pipeline mob thường và phân tích client protocol riêng. Không ép vào mẫu 12 frame.

## Đầu vào

Thu thập hoặc suy ra từ yêu cầu và mob gần nhất:

- Tên, loại di chuyển `TYPE` 0..5, HP, level, tầm đi, tốc độ, loại đạn và tỷ lệ sát thương/tiềm năng.
- Map và số lượng/vị trí spawn. Nếu người dùng nói “random”, chọn nhiều điểm hợp lệ phân bố trên các mặt đất thay vì random từng pixel lúc runtime, trừ khi họ yêu cầu cơ chế động.
- Năm pose PNG nền trong suốt: `stand`, `walk`, `attack`, `hurt`, `die`. Có thể tái dùng một ảnh cho pose thiếu nếu người dùng chấp nhận; không bịa pose mà không ghi rõ.
- Kích thước cell logic x1 và điểm neo chân. Mặc định an toàn cho mob cỡ vừa là `56x56`, `dx=-28`, `dy=-54`.

Mở và quan sát tất cả ảnh. Xác nhận alpha, hướng nhìn, khoảng trống, điểm chân và hiệu ứng tấn công. Giữ nguyên ảnh nguồn; mọi crop/scale runtime phải tái tạo được từ manifest.

## Workflow

### 1. Kiểm kê và chọn ID

- Đọc `AGENTS.md` áp dụng, kiểm tra `git status --short` và bảo toàn thay đổi có sẵn.
- Kiểm tra cả live DB và `sql/team2026.sql`; hai nguồn không tự đồng bộ.
- Giữ bảng ID liên tục từ `0` và kiểm tra **tổng số template sau thay đổi không vượt 127**. `updateMap` ghi số lượng bằng một byte mà client này đọc có dấu; 128 template làm client bỏ dở gói map, dẫn tới nền mặc định và toàn bộ mob không hiện. Vì vậy mob thường mới chỉ dùng `73..126`, không dùng `76`. Khi hết chỗ, audit một template placeholder thực sự không dùng để tái sử dụng; nếu không có, báo cần mở rộng client/protocol.
- Kiểm tra `data/mob/x1..x4/<id>` chưa thuộc mob khác.

### 2. Lập manifest và build sprite

Tạo `assets/mob/<slug>/mob.json` theo mẫu trong `client-protocol.md`, đặt ảnh trong `source/`, rồi chạy:

```powershell
python ".agents\skills\nro-create-mob\scripts\build_mob_payload.py" build `
  --manifest "assets/mob/<slug>/mob.json" --project-root .
```

Script phải tạo:

- `assets/mob/<slug>/atlases/atlas_x1.png` đến `atlas_x4.png`;
- `assets/mob/<slug>/preview-actions-x3.png`;
- `data/mob/x1/<id>` đến `data/mob/x4/<id>`.

Quan sát preview trước khi tiếp tục. Sửa manifest/ảnh nếu sprite bị nhỏ, cắt biên, lệch chân hoặc attack không đọc được. Không sửa trực tiếp atlas sinh ra.

### 3. Tạo template và spawn

- Thêm migration idempotent cho `mob_template`; đồng bộ dòng tương ứng vào dump nếu dump là nguồn phát hành.
- Giữ ID liên tục vì client/server dùng ID như chỉ số danh sách; xác nhận `COUNT(*) <= 127`, `MIN(id) = 0`, `MAX(id) = COUNT(*) - 1` trước khi triển khai.
- Thêm từng spawn vào `map_template.mobs` theo `[mobId, level, hp, x, y]`, không thay thế các mob hiện hữu.
- Với mob mặt đất, đặt `y` đúng mặt collision; dùng bội số tile 24px khi map theo chuẩn đó. Kiểm tra điểm không nằm trong đá, background hoặc ngoài vùng camera.
- Cập nhật live DB/map chỉ khi yêu cầu bao gồm triển khai. Re-query để xác nhận tên, HP và toàn bộ spawn.

### 4. Làm mới dữ liệu và triển khai

- Xác nhận `Manager` nạp `mob_template ORDER BY id ASC` và `DataGame.requestMobTemplate` gửi nguyên file sau byte ID; không thêm ID vào file payload.
- Tăng `data/map/version.txt` (được nạp thành `DataGame.vsMap`) khi thêm hoặc
  sửa `mob_template`, vì danh sách mob template nằm trong gói `updateMap`.
  Chỉ tăng `DataGame.vsData` khi nội dung `data/update_data/*` thực sự thay đổi.
- Build bằng `tools/server_control.ps1 -Action build` sau khi server dừng, rồi start/restart theo quy trình dự án. Không restart ngoài phạm vi người dùng đã cho phép.
- Xác nhận startup log nạp đủ template, map và mở cổng; `server-error.log` không có lỗi liên quan.

### 5. Kiểm tra bắt buộc

Kiểm tra offline:

```powershell
python ".agents\skills\nro-create-mob\scripts\build_mob_payload.py" verify `
  --manifest "assets/mob/<slug>/mob.json" --project-root .
```

Khi server chạy, kiểm tra cả bốn zoom:

```powershell
1..4 | ForEach-Object {
  powershell -NoProfile -ExecutionPolicy Bypass -File tools/codex_protocol_probe.ps1 -Zoom $_ -MobId <id>
}
```

Mỗi kết quả phải có `mobReadType=0`, EffectData hợp lệ, PNG hợp lệ và `typeData=0`. Sau đó yêu cầu thoát hoàn toàn tiến trình client rồi mở lại; đăng xuất hoặc đổi map không xóa `MobTemplate.data` lỗi trong RAM.

Kiểm thử thực tế: đứng, đi, attack, nhận đòn, chết/respawn, hướng trái/phải, thanh HP và mọi vị trí spawn. “Có bóng nhưng không có hình” là thất bại giải mã hoặc ảnh, không phải bằng chứng mob đã hoàn tất.

## Tiêu chuẩn bàn giao

Nêu rõ:

- Mob ID, thông số template, map và danh sách spawn.
- Manifest, ảnh nguồn, atlas/preview và bốn payload runtime.
- Phiên bản map-data trước/sau, migration/live DB đã áp dụng hay chưa.
- Kết quả verify offline, probe x1-x4, build/JAR và trạng thái server.
- Bất kỳ pose nào đang tái dùng hoặc hành vi nào chỉ là suy luận.
