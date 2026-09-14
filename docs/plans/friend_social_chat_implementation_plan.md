# Kế hoạch triển khai chức năng Bạn bè và Chat riêng

## 1. Thông tin kế hoạch

- Trạng thái tổng thể: `PLANNED`
- Ngày lập: `2026-09-14`
- Server: `C:\Users\PC\Music\source-server-nro\source-server`
- Client: `C:\Users\PC\Music\client-nro-unity`
- Nguồn icon đã kiểm kê: `C:\Users\PC\Downloads\icon_chat_friend`
- Thiết kế UI: các mockup Bạn bè, Tìm kiếm, Hộp thư, panel chat hai cột và trạng thái offline đã được người dùng duyệt trong task hiện tại.
- Workflow server/release: `nro-java-build` và `BUILD_JAVA_STANDARD.md`.
- Workflow client UI: giữ nguyên hệ thống `Panel`/`mGraphics`/`TField` hiện có; không chuyển sang uGUI, UI Toolkit hoặc tạo `SocialScreen` mới.
- Ràng buộc bảo mật: mọi tên, ID, nội dung chat và action từ client là dữ liệu không tin cậy; kiểm tra quyền và dữ liệu lại ở server.

## 2. Mục tiêu và phạm vi

### 2.1 Mục tiêu

Triển khai một hệ thống bạn bè hai chiều có tìm kiếm, lời mời kết bạn offline, hộp thư lời mời, danh sách bạn bè và chat realtime trong phiên đăng nhập. Trên màn hình đủ rộng, danh sách bạn bè nằm ở panel trái và cuộc trò chuyện nằm ở `GameCanvas.panel2` bên phải. Người chơi có thể đổi cuộc trò chuyện trực tiếp từ danh sách trái.

### 2.2 Phạm vi bao gồm

- Tìm player theo ID chính xác hoặc tên gần đúng.
- Phân trang 20 kết quả và tải trang tiếp theo khi cuộn tới cuối.
- Gửi lời mời, trạng thái đang chờ, nhận lời mời khi offline, chấp nhận hoặc xóa lời mời.
- Quan hệ bạn bè hai chiều, giới hạn 100 bạn, miễn phí.
- Hiển thị tổng số bạn và số bạn online.
- Presence realtime khi bạn đăng nhập hoặc logout.
- Chat chữ chỉ khi hai người đang online và vẫn còn là bạn bè.
- Lịch sử chat chỉ nằm trong bộ nhớ client của phiên đăng nhập.
- Tin nhắn chưa đọc theo từng người và badge phong bì chỉ đếm lời mời kết bạn.
- Gửi vị trí hiện tại do server xác định.
- Panel chat bên phải, đổi người chat nhanh, đóng bằng click vùng ngoài.
- Trạng thái đang chat nhưng người kia offline: giữ lịch sử, ẩn composer và hiện thông báo offline.
- Đồng bộ thay đổi cho cả `Game1` và `Game2`.

### 2.3 Ngoài phạm vi

- Không lưu nội dung chat vào database hoặc file log.
- Không gửi tin nhắn cho người offline.
- Không có chat nhóm, gọi thoại, gửi ảnh hoặc tệp.
- Không tạo panel Social tổng hợp mới.
- Không thay framework UI của client.
- Không sửa hoặc tăng data/icon cache version của server nếu asset chỉ được đóng trong Unity client.

## 3. Quyết định đã chốt

### 3.1 Nghiệp vụ

- Quan hệ bạn bè là hai chiều; xóa ở một bên sẽ xóa quan hệ cho cả hai.
- Dữ liệu bạn bè cũ được hợp nhất theo phép `union`: chỉ cần A hoặc B đang lưu người kia thì tạo một quan hệ chuẩn hóa A–B.
- Tìm kiếm yêu cầu tối thiểu 2 ký tự; ID được ưu tiên khớp chính xác, sau đó tên chính xác, tiền tố rồi chứa chuỗi.
- Mỗi trang có 20 kết quả.
- Lời mời hết hạn sau 30 ngày.
- Hai người gửi lời mời chéo sẽ tự động trở thành bạn nếu cả hai chưa đạt giới hạn.
- Xóa thư lời mời được hiểu là từ chối và xóa lời mời.
- Tối đa 100 bạn, không trừ ngọc.
- Chat client-memory: tối đa 100 message mỗi conversation và tối đa 20 conversation trong phiên.
- Logout xóa conversation, unread, bản nháp, active friend và mọi cache social theo tài khoản.
- Chat rate limit mặc định: token bucket dung lượng 3, hồi 1 token/giây; giới hạn độ dài vẫn áp dụng riêng.
- Gửi vị trí có cooldown 2 phút; client chỉ gửi ID người nhận, server tự lấy map, khu và tọa độ.
- Thời gian hiển thị: `Vừa xong`, `1 phút trước` … `59 phút trước`, sau đó `1 giờ trước` và tăng theo giờ.
- Tin nhắn mới từ người khác không tự đổi panel chat đang mở.

### 3.2 UI và tương tác

- Panel trái là danh sách Bạn bè; panel phải là cuộc trò chuyện.
- Chọn dòng bạn bè hoặc icon chat sẽ mở/chuyển panel phải nếu người đó online.
- Khi người đó offline, icon chat ở danh sách bị ẩn. Nếu cuộc trò chuyện đã mở thì vẫn xem được lịch sử ở chế độ chỉ đọc.
- Dòng đang chat dùng màu chọn theo mockup mới nhất. Màu nền tham chiếu lấy từ ảnh là `#F9F803`; phải visual-QA trước khi khóa asset cuối.
- Không dùng biến `selected` làm nguồn sự thật cho dòng đang chat; dùng `activeChatFriendId` riêng.
- Click vùng trống giữa hai panel lần đầu chỉ đóng panel chat phải và giữ danh sách trái.
- Khi panel phải đã đóng, click ngoài panel trái tiếp tục dùng chuỗi Back hiện có.
- Nếu emoji picker hoặc popup phụ đang mở, click ngoài đóng popup phụ trước; lần click tiếp theo mới đóng panel chat.
- Khi bạn đang chat chuyển offline: hủy focus, đóng bàn phím, ẩn emoji/vị trí/input/send, mở rộng viewport chat và render `-----Bạn bè đã offline-----` ở cuối nội dung.
- Dòng offline là trạng thái render động, không được ghi vào conversation.
- Khi bạn online lại: bỏ dòng offline và khôi phục composer cùng bản nháp chưa gửi.
- Màn hình hẹp không đủ hai panel dùng fallback một panel; Back/click ngoài phải đưa từ chat về danh sách trước khi thoát Bạn bè.

### 3.3 Màu được duyệt

| Vai trò | Mã màu | Cách dùng |
| --- | --- | --- |
| Xóa, từ chối, badge có thư | `#CF3B2E` | Nền nút tròn và chấm badge |
| Chấp nhận, phong bì đang mở | `#57AA05` | Nền nút tròn/trạng thái active |
| Tìm kiếm, thêm bạn | `#5170FF` | Nền nút tròn |
| Dòng hội thoại đang chọn | `#F9F803` | Giá trị lấy mẫu từ mockup mới; xác nhận bằng visual QA |

Các màu nền panel, bubble, chữ và viền còn lại ưu tiên palette hiện có của client và căn theo mockup; không lấy màu ngẫu nhiên từ icon nguồn.

### 3.4 Asset đã nhận

Tất cả asset nguồn hiện là PNG ARGB `1254 × 1254`, cần cắt padding trong suốt và thu nhỏ trước khi đưa vào Unity.

| Asset nguồn | Tên runtime dự kiến | Vai trò |
| --- | --- | --- |
| `accept.png` | `social_accept.png` | Chấp nhận lời mời |
| `add.png` | `social_add.png` | Gửi lời mời |
| `chat.png` | `social_chat.png` | Mở chat |
| `emoji.png` | `social_emoji.png` | Nút emoji/native keyboard |
| `Ghim vị trí.png` | `social_location.png` | Gửi vị trí |
| `hộp thư.png` | `social_mail.png` | Hộp thư |
| `remove.png` | `social_remove.png` | Xóa bạn/từ chối |
| `search.png` | `social_search.png` | Tìm kiếm |
| `send.png` | `social_send.png` | Gửi tin nhắn |

Lưu ý asset:

- Tên file runtime dùng ASCII, chữ thường, không khoảng trắng để tránh sai khác đường dẫn giữa nền tảng.
- Màu nền nút phải được chuẩn hóa đúng mã màu đã duyệt; nguồn có gradient không được coi là màu chuẩn.
- `GameCanvas.loadImage()` nạp nguồn `x4` rồi resize theo zoom. Chỉ lưu bản nguồn x4 trong client nhưng phải kiểm tra kết quả runtime ở zoom 1, 2, 3 và 4.
- Icon gửi máy bay giấy đã có trong bộ nguồn, là PNG ARGB `1254 × 1254`, SHA-256 `59A159C8D8D77BF7A2FF4CE62B1EF790D2939BF1B86F1910ED89F42A8B80D257`; vẫn phải trim/resize và visual-QA cùng các icon còn lại.
- `emoji.png` mới là icon nút, chưa phải bộ emoji. Mặc định giai đoạn đầu cho phép mở/focus bàn phím native; Unicode emoji phải được test theo từng zoom. Nếu cần picker đồng nhất mọi nền tảng thì bổ sung sprite sheet và bảng token ở một phạm vi riêng.

## 4. Hướng kiến trúc

| Tầng | Trách nhiệm |
| --- | --- |
| Database | Quan hệ bạn bè chuẩn hóa, lời mời pending và thời hạn; tuyệt đối không lưu chat |
| Server repository/service | Tìm kiếm, transaction lời mời/kết bạn/xóa, profile tối thiểu, authorization chat và location |
| Presence | Phát online/offline cho những người thực sự là bạn; cập nhật số online |
| Protocol | Mở rộng sub-action của command social hiện có, giữ command ID và tương thích client cũ |
| Client protocol/model | Parse page/request/profile/presence, quản lý state theo account/session |
| Panel trái | Search, inbox, friend list, unread, active selection, infinite scroll |
| Panel phải | Header profile, bubble chat, timestamp, composer, offline state |
| Release | Server additive trước, client sau, feature flag cuối; có rollback JAR và migration an toàn |

Luồng chính:

```text
Client input
  -> command social/chat
  -> server validate session + friendship + limits
  -> transaction/query parameterized
  -> response DTO tối thiểu
  -> client session store
  -> Panel trái / Panel phải
```

### 4.1 Biên module server dự kiến

- `SocialFriendService`: orchestration danh sách, tìm kiếm, lời mời, accept/reject/remove.
- `FriendshipRepository`: query/transaction quan hệ bạn bè.
- `FriendRequestRepository`: request pending, expiry và badge count.
- `SocialProfileRepository`: query tối thiểu theo player ID; không load full offline `Player` nếu không cần.
- `SocialPresenceService`: phát action online/offline và duy trì reverse index cho player online.
- `PrivateChatPolicy`: kiểm tra hai chiều, online, độ dài, rate limit và message kind.
- `FriendAndEnemyService`: facade tương thích cho command `-80`; logic mới được đẩy vào các lớp hẹp.

Không dồn toàn bộ tính năng vào `Manager`, `Controller` hoặc `Player`.

### 4.2 Biên module client dự kiến

- Thêm type mới, dự kiến `Panel.TYPE_FRIEND_CHAT = 30` sau khi xác nhận không trùng.
- `FriendUiState`: tab hiện tại, page/cursor, request badge, friend counts và `activeChatFriendId`.
- `FriendConversationStore`: dictionary theo friend ID, giới hạn 20 conversation × 100 message, draft, unread và scroll offset.
- `FriendChatMessage`: sender, kind, text/location, receive time và trạng thái gửi.
- Composer dùng `TField` đặt theo bounds của `panel2`; không dùng nguyên trạng `ChatTextField` đang căn giữa toàn màn hình.
- Logic thuần được tách khỏi paint/input để có thể kiểm tra giới hạn, unread, relative time và eviction mà không cần dựng scene.
- Mọi thay đổi client phải được phản chiếu có chủ ý cho `Game1` và `Game2`; không copy mù nếu hai file đang khác biệt.

## 5. Dữ liệu và migration

### 5.1 Bảng quan hệ dự kiến

`player_friendship`:

- `player_low_id BIGINT NOT NULL`
- `player_high_id BIGINT NOT NULL`
- `created_at TIMESTAMP NOT NULL`
- Primary key `(player_low_id, player_high_id)`.
- Check/validation trong Java: `player_low_id < player_high_id`, không self-friend.
- Index đảo chiều phù hợp cho truy vấn danh sách của cả hai đầu.

`friend_request`:

- `id BIGINT AUTO_INCREMENT`
- `pair_low_id`, `pair_high_id`
- `sender_id`, `receiver_id`
- `created_at`, `expires_at`
- Chỉ giữ request pending; accept/reject/expire xóa hoặc chuyển sang audit tối thiểu nếu thực sự cần vận hành.
- Unique theo cặp pending để chống gửi trùng và xử lý lời mời chéo trong một transaction.

Không thêm bảng chat.

### 5.2 Migration dữ liệu cũ

1. Dry-run đọc JSON `player.friends`, kiểm tra JSON lỗi, self-link và ID player không tồn tại.
2. Chuẩn hóa mỗi cặp bằng `min(idA,idB)` và `max(idA,idB)`.
3. Insert idempotent một friendship nếu A chứa B hoặc B chứa A.
4. Báo cáo tổng số record nguồn, số cặp unique, số record bỏ qua và lý do; không in dữ liệu nhạy cảm.
5. Không xóa cột JSON trong release đầu.
6. Trong giai đoạn chuyển tiếp, bảng chuẩn hóa là nguồn sự thật; JSON được giữ làm compatibility snapshot và rollback aid.
7. Chạy migration staging/dry-run trước, backup database theo quy trình vận hành rồi mới apply production.

### 5.3 Profile chat

- Online: lấy name, head/avatar, power, clan và presence từ player đang sở hữu session.
- Offline: query tối thiểu `player` + `account` + clan bằng prepared statement; chỉ parse trường JSON cần thiết.
- Không gửi email, username, IP, account ID hoặc dữ liệu inventory cho client.
- Default `activityLabel`: online hoặc last activity không quá 7 ngày = `Thường xuyên`; 8–30 ngày = `Gần đây`; trên 30 ngày/không có dữ liệu = `Ít hoạt động`. Policy phải nằm ở server và có test riêng để có thể thay đổi mà không sửa UI.

## 6. Dự thảo protocol cần khóa ở Giai đoạn 1

Không thêm top-level command nếu sub-action hiện có đáp ứng được. Giữ nguyên:

- `-80`: bạn bè/social.
- `-72`: client gửi chat chữ hiện tại.
- `92`: server echo/forward chat chữ hiện tại.

Sub-action mới dự kiến của `-80`:

| Action | Hướng | Payload chính | Kết quả |
| ---: | --- | --- | --- |
| `0` | C↔S | Mở/list bạn bè | Giữ tương thích hiện tại, bổ sung capability/count theo version gate |
| `1` | C→S | Legacy make friend | Giữ cho client cũ; client mới không dùng |
| `2` | C↔S | Xóa bạn | Transaction hai chiều, sync cả hai client online |
| `3` | S→C | `friendId:int, online:boolean` | Presence realtime |
| `4` | C↔S | query + page/cursor | Search page 20 kết quả |
| `5` | C↔S | `targetId:int` | Gửi request/trạng thái pending/auto-accept |
| `6` | C↔S | page/cursor | Inbox request page |
| `7` | C↔S | `requestId:long` | Accept request |
| `8` | C↔S | `requestId:long` | Reject/delete request |
| `9` | C↔S | `friendId:int` | Header profile tối thiểu |
| `10` | C→S | `friendId:int` | Gửi vị trí server-derived |
| `11` | S→C | sender + location DTO | Event vị trí đưa vào conversation |
| `12` | S→C | pending count | Cập nhật badge phong bì |

Trước khi code phải khóa:

- Byte order, signed/unsigned width, giới hạn từng UTF và version gate.
- Capability để client mới không gửi action mới cho server cũ.
- Payload cũ của `0/1/2`, `-72` và `92` không được đổi thứ tự cho client cũ.
- Mọi response page phải có `requestToken`, `hasMore` và cursor/page để bỏ response cũ hoặc duplicate.
- Packet phải dưới `65535` byte; test phải đo serialized size ở dữ liệu biên.

## 7. Threat model và giới hạn bắt buộc

| Rủi ro | Kiểm soát |
| --- | --- |
| Giả mạo player ID | Lấy sender từ session; client chỉ được chỉ định target ID |
| Chat người không phải bạn | Server kiểm tra friendship ở thời điểm gửi, không tin danh sách client |
| Gửi cho người offline/race logout | Kiểm tra target session còn active ngay trước forward; chỉ append phía gửi sau server echo |
| SQL injection từ tên | Prepared statement; escape wildcard `%`/`_`; giới hạn 2–32 ký tự sau trim |
| Search enumeration/DoS | Page 20, max query length, rate limit, index phù hợp, không trả account data |
| Spam lời mời | Unique pending pair, cooldown và giới hạn số request trong cửa sổ thời gian |
| Spam chat | Text 1–80 ký tự, token bucket 3/1 giây, reject control characters không cần thiết |
| Giả tọa độ | Client không gửi map/x/y; server đọc từ player hiện tại |
| Lộ lịch sử | Không persistence, không ghi message text/location vào log; clear khi logout |
| Deadlock hai chiều | Canonical pair low/high, lock/update theo một thứ tự cố định |
| Packet quá lớn | Page cố định, giới hạn UTF và regression đo byte |
| State tài khoản A rò sang B | Không dùng static store theo account; reset rõ trong `doResetToLoginScr` của cả Game1/Game2 |

## 8. Bảng giai đoạn

| Giai đoạn | Nội dung | Trạng thái | Phụ thuộc |
| ---: | --- | --- | --- |
| 0 | Khảo sát và khóa thiết kế | `COMPLETE` | — |
| 1 | Baseline, contract, feature flag và test RED | `COMPLETE` | 0 |
| 2 | Schema, migration và repository persistence | `COMPLETE` | 1 |
| 3 | Search, request, friendship và inbox server | `COMPLETE` | 2 |
| 4 | Presence, profile, chat policy và location server | `PLANNED` | 3 |
| 5 | Asset pipeline, client models và protocol parser | `PLANNED` | 1, 4 |
| 6 | Panel trái Bạn bè/Tìm kiếm/Hộp thư | `PLANNED` | 3, 5 |
| 7 | Panel phải Chat, composer và offline state | `PLANNED` | 4, 5, 6 |
| 8 | Tích hợp, security, performance và responsive QA | `PLANNED` | 2–7 |
| 9 | Full build, deploy, protocol probe và release verification | `PLANNED` | 8 |

## 9. Chi tiết từng giai đoạn

### Giai đoạn 0 — Khảo sát và khóa thiết kế

Mục tiêu: chứng minh tính khả thi và ghi lại quyết định trước khi sửa code.

Đã hoàn thành:

- [x] Xác định client dùng custom immediate renderer `Panel`/`mGraphics`, không dùng prefab Canvas.
- [x] Xác định `GameCanvas.panel2` đã có update, focus, input và paint độc lập.
- [x] Xác định breakpoint hai panel hiện tại là `GameCanvas.w > 2 * Panel.WIDTH_PANEL` với `WIDTH_PANEL = 245`.
- [x] Xác định click vùng giữa hiện gọi `panel.hide()` và cần special-case cho social pair.
- [x] Xác định `TYPE_FRIEND = 11`, chat cũ là log chung tối đa 20 và không phù hợp conversation per friend.
- [x] Xác định server đã parse presence action `3` ở client nhưng chưa phát presence từ login/logout.
- [x] Xác định friend packet hiện không có clan/activity profile.
- [x] Xác định logout hiện chưa clear đầy đủ static chat log mới cần thay thế bằng session store rõ ràng.
- [x] Kiểm kê 9 icon nguồn, kích thước, alpha và ba mã màu được duyệt.
- [x] Xác định icon send đã đủ; chưa có emoji sprite set.

Tiêu chí thoát: yêu cầu, kiến trúc hai panel, giới hạn session và nguồn asset được ghi trong tài liệu này.

#### Nhật ký triển khai Giai đoạn 0

- Trạng thái: `IN PROGRESS` — phần server đã hoàn thành; còn một kiểm tra client ngoài phạm vi repository hiện tại.
- Ngày: `2026-09-14`
- Nội dung: khảo sát read-only server, client, protocol, lifecycle, mockup và asset.
- File thay đổi: chỉ tạo tài liệu kế hoạch này.
- Kiểm tra: `git status --short` ở hai repository đều sạch trước khi tạo kế hoạch; asset nguồn có 9 PNG ARGB 1254×1254.
- Build/JAR/database/runtime: không thay đổi, không build, không restart, không mutation database.
- Còn lại: quyết định emoji đồng nhất đa zoom được xử lý ở Giai đoạn 5, không chặn phần core.

### Giai đoạn 1 — Baseline, contract, feature flag và test RED

Mục tiêu: khóa compatibility boundary trước khi tạo schema hay handler.

Các bước:

1. Ghi `git status --short` của server và client; lưu hash `20.jar`, Java/Javac/Lombok và trạng thái server.
2. Kiểm tra version/client capability hiện có; chọn `SOCIAL_V2_CLIENT_VERSION` hoặc capability bit rõ ràng.
3. Khóa action table ở Mục 6, byte order, max length và error code.
4. Xác nhận action/type `30` không trùng ở cả Game1/Game2.
5. Thêm config/feature flag mặc định tắt cho social v2 và migration switch nếu repository đã có pattern tương ứng.
6. Viết regression test RED cho normalization cặp, limit 100, request chéo, expiry, search ranking, authorization chat/location và packet size.
7. Viết test compatibility bảo đảm action `0/1/2`, `-72` và packet `92` cũ không đổi.

File dự kiến:

- Server: constants/policy DTO hẹp trong `src/nro/models/services` hoặc package social mới.
- Tests: `tools/tests/Social*RegressionTest.java` và runner PowerShell theo pattern hiện có.
- Config/migration manifest nếu cần.
- Client: chỉ constants/capability tối thiểu, chưa paint UI.

Tiêu chí thoát:

- Protocol contract được ghi bằng test và không trùng command/action.
- Test RED thất bại vì behavior chưa tồn tại, không vì test/bộ build hỏng.
- Không có build release hay database mutation trong giai đoạn này.

#### Nhật ký triển khai Giai đoạn 1

- Trạng thái: `COMPLETE`
- Bắt đầu/kết thúc: `2026-09-14` / `2026-09-14`
- Nội dung đã làm: ghi baseline server, khóa contract `-80`/`-72`/`92`, action 0–12, gate client version 223, giới hạn wire/UTF/error và feature flag fail-closed. Chưa nối handler, schema, migration hay database.
- File thay đổi: `src/nro/models/social/SocialV2Protocol.java`, `SocialV2FeatureFlags.java`, `SocialFriendPolicy.java` (API placeholder có chủ đích), `src/nro/config/ConfigPaths.java`, `config/social/social_features.properties`, `docs/protocol/social_v2_protocol_contract.md`, các test/runner Social V2 dưới `tools/tests`.
- Lệnh/test/evidence: baseline và hash cuối `20.jar` SHA-256 `EFFDDB61C6C7C6A84A19FE9DD9421D0288DAA5E971E667117239609BE20396B6`; Java/Javac `17.0.20.1`, Lombok `1.18.36`. `Test-SocialV2Phase1.ps1 -Mode Contract` PASS với protocol và compatibility contract. Sau khi người dùng mở phạm vi kiểm tra read-only, scan Game1/Game2 xác nhận không có khai báo `TYPE_FRIEND_CHAT = 30`, `setType(30)`, so sánh/gán type 30, hoặc `case 30` trong các surface Panel/GameCanvas/GameScr/Service/Controller; command `-80` và `-72` cũ vẫn tồn tại độc lập.
- Sai lệch so với kế hoạch: không sửa, build hoặc thay đổi file trong `client-nro-unity`; kiểm tra collision action/type 30 chỉ đọc mã và đã đủ điều kiện đóng Giai đoạn 1.
- Việc còn lại/rủi ro: không còn công việc Giai đoạn 1. Feature và migration flag vẫn mặc định tắt, chỉ được bật trong rollout được kiểm soát ở các giai đoạn sau.

### Giai đoạn 2 — Schema, migration và repository persistence

Mục tiêu: tạo nguồn sự thật hai chiều và lời mời offline an toàn.

Các bước:

1. Viết migration additive cho `player_friendship` và `friend_request`, gồm index và ràng buộc cần thiết.
2. Viết repository bằng prepared statement; transaction theo canonical pair và kiểm soát isolation/rollback.
3. Implement send request idempotent, cross-request auto-accept, accept/reject/expire và remove friendship.
4. Khi accept, kiểm tra limit của cả hai người trong cùng transaction; không tạo friendship một phía.
5. Viết công cụ/backfill idempotent đọc legacy JSON theo phép union, có dry-run và báo cáo count.
6. Giữ legacy JSON trong release đầu; không drop cột và không phá loader cũ.
7. Test concurrent accept/remove, duplicate request, expired request, nonexistent/self target và malformed legacy JSON.

Tiêu chí thoát:

- Tất cả transaction đạt tính hai chiều hoặc rollback toàn bộ.
- Backfill chạy lại không tạo duplicate.
- Chưa apply production; chỉ migration/test database được phép theo môi trường đã chỉ định.

#### Nhật ký triển khai Giai đoạn 2

- Trạng thái: `COMPLETE`
- Bắt đầu/kết thúc: `2026-09-14` / `2026-09-14`
- Nội dung đã làm: bổ sung repository JDBC dùng prepared statement và transaction rollback toàn phần; chuẩn hóa cặp `low/high`, khóa hai hàng `player` theo đúng thứ tự, xử lý idempotent request/cross-request auto-accept/accept/reject/expire/remove. Limit 100 của cả hai phía được kiểm tra trong cùng transaction trước khi tạo friendship.
- File/schema thay đổi: thêm migration additive `sql/migrations/20260914_add_social_v2_friendship.sql` cho `player_friendship` và `friend_request`; `expires_at` dùng `DATETIME` để tương thích MariaDB 10.4 strict mode. Thêm service/repository social và `LegacyFriendBackfill`. Legacy `player.friends` không bị sửa, drop hoặc thay loader.
- Dry-run/apply/evidence: `LegacyFriendBackfill` mặc định dry-run, hợp nhất quan hệ legacy JSON theo union, bỏ qua input malformed/self/missing và cho phép chạy lại không tạo duplicate. `--apply` production bị chặn trừ khi đồng thời bật social v2 và migration flag. `Test-SocialV2Phase2.ps1` PASS: persistence regression, schema contract và protocol contract đều PASS; chỉ còn đúng ba RED dành cho Giai đoạn 3–4 (search, chat authorization, location authorization). `Test-SocialV2Phase2Database.ps1` PASS trên MariaDB `10.4.25`, dùng schema local có prefix ngẫu nhiên, chạy đúng file migration, test JDBC concurrency/limit/expiry và backfill dry-run/apply/re-run, sau đó tự xóa schema.
- Rollback/backup: migration không có lệnh destructive. Schema test đã tự xóa và không có database ứng dụng nào bị áp migration. Khi rollout staging/production phải tạo backup đã xác minh trước, chạy dry-run, review count rồi mới apply; rollback là restore backup và tắt feature/migration flag.
- Sai lệch/rủi ro: handler runtime, search/inbox, presence/chat/location và client parser/UI vẫn thuộc Giai đoạn 3–7. Không build/restart/release JAR ở Giai đoạn 2; hash `20.jar` giữ nguyên.

### Giai đoạn 3 — Search, request, friendship và inbox server

Mục tiêu: hoàn thiện nghiệp vụ social không phụ thuộc UI.

Các bước:

1. Tách facade `FriendAndEnemyService` khỏi repository/policy; giữ enemy flow nguyên trạng.
2. Implement open friend list với tổng bạn, tổng online, pending count và capability.
3. Implement search ID/tên: exact ID, exact name, prefix, contains; exclude self và trả trạng thái `FRIEND/PENDING/CAN_ADD`.
4. Implement paging 20 kết quả với token/cursor và `hasMore`.
5. Implement inbox page, badge count, send/accept/reject/remove và push cập nhật cho client online liên quan.
6. Không trừ gem ở bất kỳ nhánh nào; xóa menu xác nhận phí 5 ngọc khỏi client v2 path.
7. Thêm generic user messages cho full list, duplicate, expired, not found và rate-limited.
8. Đo packet worst-case với tên UTF dài nhất hợp lệ.

Tiêu chí thoát:

- Search ổn định, không duplicate giữa trang và không leak trường account.
- Offline receiver nhìn thấy request sau lần đăng nhập tiếp theo.
- Accept/remove cập nhật hai phía và limit 100 được bảo toàn.

#### Nhật ký triển khai Giai đoạn 3

- Trạng thái: `COMPLETE`
- Bắt đầu/kết thúc: `2026-09-14` / `2026-09-14`
- Nội dung đã làm: thêm biên đọc `SocialDirectoryRepository` và JDBC projection tối thiểu; tìm exact ID/tên, prefix, contains, escape literal `%`/`_`, loại self, trạng thái `FRIEND`/`PENDING`/`CAN_ADD`, phân trang 20. Thêm inbox, tổng pending/online và facade protocol cho action `0`, `2`, `4`–`8`, `12`; action legacy `1` và toàn bộ enemy flow không đổi. Các transition thành công mang theo peer ID được khóa từ database để action `0`/`12` chỉ push snapshot/badge cho peer online v2 liên quan. Không có nhánh v2 nào trừ ngọc.
- Chống abuse/message: áp dụng 30 search/phút; lời mời có cooldown 5 giây và tối đa 10/10 phút theo player ID từ session. Limiter synchronized, bounded 10,000 entry và tự dọn entry idle 15 phút; `RATE_LIMITED` và `DUPLICATE` trả envelope chuẩn cùng generic user message. Đầu vào packet bị kiểm tra hết dữ liệu dư; SQL chỉ dùng prepared statement và projection `id/name/head`, không truy vấn account/inventory.
- File thay đổi: `FriendAndEnemyService.java`; `SocialDirectoryRepository.java`, `JdbcSocialDirectoryRepository.java`, `SocialDirectoryService.java`, `SocialActionRateLimiter.java`, `SocialV2ServerFacade.java`, cập nhật `SocialRelationshipService.java`, `SocialFriendPolicy.java`; protocol contract và các test/runner Giai đoạn 3 dưới `tools/tests`.
- Test/evidence/packet size: `Test-SocialV2Phase3.ps1` PASS, compile toàn bộ source Java 17 và chạy persistence/schema/protocol/search/paging/inbox/rate-limit/wire facade. Chỉ còn đúng hai RED có chủ đích của Giai đoạn 4 (chat authorization và location). `Test-SocialV2Phase3Database.ps1` PASS trên MariaDB local cổng 3307 với schema tên ngẫu nhiên, disposable: xác nhận query JDBC, escape wildcard literal, thứ tự search, pages không duplicate, relationship/pending/expired/inbox/friend summary; schema tự xóa sau test. Wire test xác nhận envelope/page/action-0 tail; 20 tên Unicode tối đa hợp lệ vẫn dưới packet cap. Bound action-0 với 100 tên `VARCHAR(20)` Unicode và power format cực đại là 16,311 bytes, dưới 65,535 bytes.
- Sai lệch/rủi ro còn lại: limiter chỉ dành cho local single-process mode; rollout multi-process phải dùng shared limiter trước khi bật Social V2. Không build/restart/release JAR, không apply migration production và feature flag vẫn tắt; các bước này thuộc rollout/release sau. Presence/profile/chat/location và client parser/UI vẫn thuộc Giai đoạn 4–7.

### Giai đoạn 4 — Presence, profile, chat policy và location server

Mục tiêu: cung cấp dữ liệu realtime và enforcement server-side cho panel chat.

Các bước:

1. Phát presence `true` sau khi player/session được publish hoàn chỉnh.
2. Khi logout, chặn gửi mới, unpublish player rồi phát presence `false`; tránh cửa sổ race nhận message.
3. Duy trì reverse index chỉ cho player online hoặc query hẹp; không quét toàn bộ player mỗi lần presence đổi.
4. Implement profile action: name, avatar/head, clan, activity label, formatted/raw power cần thiết và online.
5. Sửa private chat: chỉ cho mutual friends, target online, text 1–80, token bucket đã chốt; không log nội dung.
6. Giữ server echo làm xác nhận message gửi thành công; target offline trả failure/presence, không tạo local sent message giả.
7. Implement share location: kiểm tra friendship, online, cooldown; server lấy map/khu/x/y và phát typed location event.
8. Test login/logout lặp lại, reconnect, remove while chatting, simultaneous send/logout và location spoof attempt.

Tiêu chí thoát:

- Presence tới đúng online friends, không phát cho người không phải bạn.
- Không chat/location khi offline hoặc sau remove.
- Profile offline không load full player ngoài ý muốn và không lộ account fields.

#### Nhật ký triển khai Giai đoạn 4

- Trạng thái: `NOT STARTED`
- Bắt đầu/kết thúc: —
- Nội dung đã làm: —
- File thay đổi: —
- Test/evidence/race cases: —
- Sai lệch/rủi ro: —

### Giai đoạn 5 — Asset pipeline, client models và protocol parser

Mục tiêu: chuẩn bị asset/state/parser trước khi paint màn hình.

Các bước:

1. Copy asset nguồn vào vùng staging tạm có kiểm soát; giữ nguyên file gốc và hash nguồn.
2. Trim transparent padding, cân tâm glyph, đổi tên ASCII và export x4 theo kích thước logic đã đo từ mockup.
3. Chuẩn hóa nền nút theo `#CF3B2E`, `#57AA05`, `#5170FF`; ưu tiên nền do code vẽ nếu cần nhiều trạng thái từ một glyph.
4. Chuẩn hóa và duyệt `send.png` thành `social_send.png` cùng kích thước/hitbox của composer.
5. Thêm asset vào `Assets/Resources/res/x4/mainimage` hoặc thư mục con phù hợp và để Unity tạo `.meta` ổn định.
6. Tạo client DTO/state/store thuần và giới hạn eviction.
7. Mở rộng `Service` gửi action mới và `Controller` parse response mới với version/capability gate.
8. Thêm reset rõ ràng vào logout/account switch ở Game1 và Game2.
9. Test zoom 1–4: kích thước, alpha, không mờ/nhoè, không mất file do case/path.

Kích thước mục tiêu ban đầu để visual-QA, tính theo nguồn x4:

- Nút tròn nhỏ 20 logical: export 80×80.
- Icon 24 logical: export 96×96.
- Composer button 26 logical: export 104×104.
- Điều chỉnh sau screenshot runtime, nhưng giữ logical hitbox tối thiểu 20–24.

Tiêu chí thoát:

- Asset load ở cả Game1/Game2 và zoom 1–4.
- Parser chịu được response cũ/mới đúng capability.
- Logout A → B xóa sạch client social state.

#### Nhật ký triển khai Giai đoạn 5

- Trạng thái: `NOT STARTED`
- Bắt đầu/kết thúc: —
- Nội dung đã làm: —
- Asset/file/hash/dimension: —
- Compile/zoom evidence: —
- Sai lệch/rủi ro: —

### Giai đoạn 6 — Panel trái Bạn bè, Tìm kiếm và Hộp thư

Mục tiêu: hoàn thiện panel trái theo mockup và toàn bộ state list/request.

Các bước:

1. Giữ `TYPE_FRIEND`, thêm mode `FRIENDS/SEARCH/INBOX` thay vì tạo screen mới.
2. Paint header số bạn `x/100` và online `y/x`.
3. Thêm search field, nút search và mail; badge đỏ chỉ lấy pending request count.
4. Friend row: avatar, `name (id)`, online/offline, unread label, chat icon chỉ online và remove.
5. Active row dựa trên `activeChatFriendId`, dùng màu chọn đã duyệt.
6. Search row hiển thị `+`, `Đang chờ` hoặc trạng thái đã là bạn; click add không được gửi trùng.
7. Inbox row có accept/remove; accept thành công chuyển state sang friend list một cách nhất quán.
8. Infinite scroll: chỉ request trang sau khi chạm đáy, nhả tay rồi cuộn xuống lần nữa; dùng `isLoading/hasMore/requestToken` chống gọi lặp.
9. Keyboard/controller navigation không làm active conversation đổi ngoài ý muốn.

Tiêu chí thoát:

- Ba mode paint đúng, cuộn đúng, hitbox không chồng.
- Badge và unread độc lập.
- Friend/search/request updates không reset scroll không cần thiết.

#### Nhật ký triển khai Giai đoạn 6

- Trạng thái: `NOT STARTED`
- Bắt đầu/kết thúc: —
- Nội dung đã làm: —
- File thay đổi: —
- Screenshot/input/paging evidence: —
- Sai lệch/rủi ro: —

### Giai đoạn 7 — Panel phải Chat, composer và offline state

Mục tiêu: hoàn thiện trải nghiệm chat hai cột và fallback màn hẹp.

Các bước:

1. Tạo `TYPE_FRIEND_CHAT` bằng `setType(1)` cho panel phải.
2. Header render friend name, clan, activity, power và avatar; có loading/unknown fallback.
3. Conversation layout tính chiều cao bubble theo text wrap, sender direction, avatar và timestamp.
4. Scroll dùng tổng chiều cao thực, không giả định `ITEM_HEIGHT` cố định; mở conversation mới auto-scroll bottom, còn conversation cũ phục hồi offset.
5. Composer panel-bound gồm emoji, location, `TField` và send; max 80 ký tự.
6. Incoming message: append đúng conversation, active thì read; inactive thì tăng unread và cập nhật row trái.
7. Relative-time update theo clock client mà không rebuild toàn bộ layout mỗi frame.
8. Presence false: lưu draft, hủy focus/keyboard, ẩn composer, mở viewport và render offline footer động.
9. Presence true: bỏ footer, phục hồi composer/draft; không tự gửi draft.
10. Click vùng giữa social pair: đóng popup phụ trước, sau đó chỉ dispose panel2 và clear active selection; không gọi generic `panel.hide()`.
11. Fallback màn hẹp: cùng dữ liệu nhưng chuyển panel trái sang chat; click ngoài/Back quay về danh sách trước.
12. Remove active friend: đóng chat, xóa conversation của friend và hiển thị trạng thái phù hợp.

Tiêu chí thoát:

- Switch friend không trộn lịch sử, unread, draft hoặc scroll.
- Offline transition khớp mockup và không để keyboard/input vô hình vẫn nhận phím.
- Click ngoài đóng đúng một tầng.

#### Nhật ký triển khai Giai đoạn 7

- Trạng thái: `NOT STARTED`
- Bắt đầu/kết thúc: —
- Nội dung đã làm: —
- File thay đổi: —
- Screenshot/input/offline evidence: —
- Sai lệch/rủi ro: —

### Giai đoạn 8 — Tích hợp, security, performance và responsive QA

Mục tiêu: kiểm tra toàn hệ thống trước khi tạo release artifact.

Ma trận bắt buộc:

- Search: ID, exact name, prefix, contains, Unicode, 2 ký tự, max length, wildcard, không có kết quả và nhiều trang.
- Request: online/offline, duplicate, cross-request, expire, reject, accept khi một/both side full.
- Friendship: migration one-sided, two-sided, self/corrupt IDs, remove khi hai phía online/offline.
- Presence: login, logout chuẩn, mất mạng, reconnect, account switch.
- Chat: normal, spam, 80 ký tự, rỗng, friend offline, remove giữa lúc gửi, 20 conversation/100 message eviction.
- Unread: active/inactive panel, switch friend, close/reopen panel và logout clear.
- Location: normal, cooldown, fake target, non-friend, offline, map transition race.
- UI: touch/mouse/keyboard, popup precedence, click gap, narrow/wide, zoom 1–4, Game1/Game2.
- Privacy: không có message text/location trong server logs hoặc database.
- Packet: malformed/truncated, response out-of-order, duplicate page, payload biên dưới 65535.

Performance gate:

- Search query dùng index/EXPLAIN phù hợp và giới hạn page.
- Presence không quét toàn bộ user database/player list.
- Paint chat chỉ layout lại khi conversation/width thay đổi, không split toàn bộ text mỗi frame.
- Asset sau resize không giữ texture 1254×1254 không cần thiết trong runtime.

Tiêu chí thoát:

- Không còn lỗi correctness/security mức cao.
- Client compile sạch và screenshot so sánh đạt mockup ở độ phân giải mục tiêu.
- Regression server từ source đã qua trước full release build.

#### Nhật ký triển khai Giai đoạn 8

- Trạng thái: `NOT STARTED`
- Bắt đầu/kết thúc: —
- Nội dung đã làm: —
- Test matrix/evidence: —
- Performance/security findings: —
- Sai lệch/rủi ro: —

### Giai đoạn 9 — Full build, deploy và release verification

Mục tiêu: tạo release unit có thể rollback và thu evidence runtime.

Thứ tự:

1. Kiểm tra working tree hai repository; review diff, không chứa file build/temp/log ngoài ý muốn.
2. Ghi Java/Javac/Lombok, hash `20.jar`, server PID/status và cổng 14445.
3. Dừng server trước khi thay JAR.
4. Chạy canonical full build: `tools/server/server_control.ps1 -Action build`.
5. Kiểm tra mọi class mới/sửa, gồm inner class, tồn tại trong `20.jar`; ghi SHA-256 và backup path.
6. Apply migration theo backup/rollback plan nếu chưa apply; chạy backfill dry-run rồi mới apply.
7. Start server; kiểm tra cổng 14445 và log lỗi mới.
8. Chạy protocol probe thường và reconnect/skip-client-type; bổ sung social protocol probe cho action/page/malformed/packet size.
9. Deploy client build tương thích hoặc chạy trong Unity 2022.3.62f2; bật feature flag sau khi server additive đã sẵn sàng.
10. Test vật lý hai client A/B cho request, accept, presence, chat, offline và location.
11. Trong cùng một cửa sổ client: A → logout → B; xác nhận không còn lịch sử/unread/profile của A.
12. Canary và rollback nếu startup, protocol, migration hoặc A→B thất bại.

Tiêu chí hoàn thành:

- Server/client behavior đạt toàn bộ acceptance matrix.
- `20.jar` có hash, backup và runtime evidence.
- Normal/reconnect/social probes pass.
- Database migration/backfill có count và rollback plan.
- Manual two-client và one-window A→B pass hoặc được ghi rõ là chưa test; không tuyên bố hoàn tất nếu còn bước bắt buộc.

#### Nhật ký triển khai Giai đoạn 9

- Trạng thái: `NOT STARTED`
- Bắt đầu/kết thúc: —
- JAR/SHA-256/backup: —
- Java/Javac/Lombok/classes: —
- Migration/backfill: —
- Protocol probes: —
- Server PID/port/log: —
- Client build/zoom/Game1/Game2: —
- Manual A/B và A→logout→B: —
- Rollback/canary/rủi ro còn lại: —

## 10. Quy tắc cập nhật kế hoạch khi triển khai

Sau mỗi giai đoạn, người triển khai bắt buộc:

1. Chuyển trạng thái trong Bảng giai đoạn thành `IN PROGRESS`, `COMPLETE` hoặc `BLOCKED`.
2. Điền đúng mục `Nhật ký triển khai` của giai đoạn đó; không dồn toàn bộ evidence vào cuối tài liệu.
3. Ghi file thực tế, migration, command/test, kết quả, packet size, screenshot và hash artifact khi có.
4. Nếu thay đổi thiết kế/protocol/schema, ghi lý do và ảnh hưởng tương thích tại chính giai đoạn phát sinh.
5. Không đánh dấu `COMPLETE` dựa trên suy đoán hoặc source review; cần fresh evidence tương ứng.
6. Không ghi secret, credential, nội dung chat thật, IP đầy đủ hoặc dữ liệu account vào tài liệu/log.
7. Sau giai đoạn cuối, cập nhật trạng thái tổng thể và tóm tắt phần chưa kiểm thử/rủi ro còn lại.

## 11. Điều kiện bắt đầu triển khai

- Working tree hiện tại được kiểm tra lại và thay đổi mới của người dùng được bảo toàn.
- Protocol/capability/version gate ở Giai đoạn 1 được khóa trước khi server/client code song song.
- Xác nhận asset source vẫn tồn tại tại đường dẫn đã ghi; ghi hash trước khi xử lý.
- Bộ 9 icon nguồn, gồm `send.png`, phải được hash và chuẩn hóa trước visual gate Giai đoạn 7.
- Có database backup và môi trường dry-run trước migration.
- Không build/restart/mutate database cho đến đúng giai đoạn và đúng phạm vi đã được người dùng yêu cầu.
