# Gate 4 — Manager runtime ownership

## Mục tiêu

Gate 4 loại bỏ `Manager` khỏi vai trò global state container. `Manager` chỉ còn là
compatibility shell nhỏ; production source bị dependency gate chặn nếu gọi trực tiếp
`Manager.*`.

## Ownership sau migration

| Owner | Trách nhiệm |
| --- | --- |
| `ServerConfig` | Parse và validate một snapshot cấu hình server immutable. |
| `TemplateDataLoader` | Đọc DB/file vào staging data và chỉ trả `DataBundle` hoàn chỉnh. |
| `TemplateRegistry` | Validate catalog theo ID và publish snapshot read-only bằng một atomic swap. |
| `WorldFactory` | Dựng Map/Zone/NPC off-side trước khi đưa vào runtime. |
| `WorldRegistry` | Atomic state gồm snapshot compact để duyệt/tick và index immutable theo map ID để hỗ trợ ID thưa. |
| `ClanRegistry` | Add/remove/find thread-safe; caller chỉ nhận immutable snapshot. |
| `NpcRegistry` | Staging NPC khi dựng world và publish nguyên khối; mutation runtime dùng CAS. |
| `LeaderboardService` | Sở hữu query, cache và versioned invalidation; không làm mất dirty event khi refresh đua với writer. |
| `WorldTickEngine` | Sở hữu scheduler/worker, deadline metrics, health và shutdown. |
| `GameRuntime` | Composition root điều phối thứ tự bootstrap; không chứa domain collection công khai. |

Shop và item-option loading nhận catalog staging qua tham số. Vì vậy bootstrap không
cần publish một registry tạm để các DAO phụ thuộc đọc được template; lỗi foreign ID
trong shop/option làm hủy candidate thay vì để live runtime thấy dữ liệu một phần.

## Invariant phát hành

- Không có public mutable registry trên compatibility facade hoặc owner mới.
- Không có production caller dùng `Manager.*`.
- Reader chỉ thấy toàn bộ revision cũ hoặc toàn bộ revision mới.
- Candidate lỗi không thay thế live snapshot.
- Map/NPC được dựng trong staging scope; failed world build không publish NPC dở dang.
- Map ID có thể thưa trong phạm vi packet `0..254`; lookup theo ID không phụ thuộc vị trí trong list.
- Consignment option được defensive-copy từ template staging, không kích hoạt đọc registry live trong bootstrap.
- Tick engine start idempotent, có health snapshot và shutdown chờ worker kết thúc.
- Leaderboard invalidation phát sinh trong lúc refresh không bị xóa nhầm.

## Gate tự động

Chạy từ repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\tests\Test-Gate4RegressionSuite.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\tests\Test-Gate3RegressionSuite.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\tests\Test-SEC07ProductionCompile.ps1
```

Gate 4 full-compiles production source bằng Java 17, chạy characterization tests cho
config/registry/reload/clan/tick/leaderboard, rồi kiểm tra dependency và kích thước
compatibility facade. Release JAR vẫn phải được tạo bằng
`tools/server_control.ps1 -Action build`, sau khi server đã dừng.

## Rollback

Nếu startup, protocol probe hoặc test A → logout → B thất bại, dừng server, khôi phục
đúng backup `20.jar.bak_<timestamp>` do build controller tạo và khởi động lại. Không
rollback source bằng Git destructive command; giữ worktree để điều tra.
