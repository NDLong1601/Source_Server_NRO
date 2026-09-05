# Kế Hoạch Tổng Thể Nâng Cấp Source Code NRO Server — Audit, Kiến Trúc Và Triển Khai

## 0. Trạng thái tài liệu

- Repository được đối chiếu: C:/Users/PC/Music/Teamobi2026/SRC
- Thời điểm rà soát: 05/09/2026
- Git HEAD tại thời điểm rà soát: b8190e4cf
- Working tree đang có thay đổi chưa commit, gồm cả các thay đổi về clan và PlayerAppearanceService.java. Vì vậy line number chỉ đúng với snapshot hiện tại.
- Java source/target: 17.
- Quy mô: 594 file Java; khoảng 126.947 physical line.
- Mục tiêu tài liệu: xác định lỗi thật theo call path, kiến trúc cần sửa, phương án tách Player.java, Manager.java, Controller.java, và kế hoạch nâng cấp có thể triển khai từng bước mà không phải viết lại toàn bộ server.
- Phân loại:
  - Đã xác nhận: có call path và hậu quả nhìn thấy trực tiếp trong source.
  - Rủi ro có điều kiện: code có lỗi, nhưng cần trạng thái hoặc tải đồng thời cụ thể.
  - Nợ kỹ thuật: chưa phải exploit trực tiếp nhưng làm tăng xác suất phát sinh lỗi và kéo dài thời gian sửa.
  - Cần đo: không chốt cấu hình bằng cảm tính; phải benchmark theo CCU mục tiêu.

Tài liệu này thay thế phần kết luận và roadmap của bản audit trước. Các nhận định cũ không còn đúng đã được loại bỏ hoặc hạ mức rõ ràng.

---

## 1. Kết luận điều hành

Source không nên rewrite toàn bộ và cũng chưa nên tách microservice. Phương án phù hợp nhất là modular monolith theo lộ trình “stabilize → characterize → extract → encapsulate → optimize”.

Ba quyết định kiến trúc chính:

1. Có, cần tách Player.java, nhưng Player vẫn phải là aggregate root. Không chia toàn bộ field thành nhiều object tùy ý trong một lần. Tách lifecycle/tick, appearance, event state, persistence mapping và các phép đổi tiền trước; sau đó mới đóng dần public field.
2. Cần giảm mạnh phụ thuộc trong Manager.java. Manager hiện là bootstrap, config, registry, loader, scheduler, leaderboard và global mutable state cùng lúc. Nên biến Manager thành compatibility facade tạm thời rồi xóa dần.
3. Cần tách Controller.java. Đây phải là protocol router mỏng, không phải nơi decode packet, kiểm tra quyền, gọi nghiệp vụ và xử lý mọi command trong một switch hơn 700 dòng.

Trước refactor lớn phải sửa các lỗi P0/P1 sau:

| Mức | Phát hiện | Trạng thái |
|---|---|---|
| P0 | Nhận thưởng thành tựu không kiểm tra đủ điều kiện hoặc đã nhận; có thể nhận ngọc lặp lại | Đã xác nhận |
| P0 | LuckyRound nhận count âm và phép nhân giá vàng bằng int có thể overflow | Đã xác nhận |
| P0 | Trade dùng accept++ không gắn actor; một phía có thể gửi accept hai lần | Đã xác nhận |
| P0 | Shop ký gửi mua hàng không atomic; có race double-buy và trừ tiền dù add bag thất bại | Đã xác nhận |
| P0 | Luồng VND/VIP không bảo toàn tiền thật; có nhánh chỉ trừ VND trong RAM | Đã xác nhận |
| P1 | Mỗi lần Player.start tạo executor bị mất reference và không shutdown; thread tồn tại sau logout | Đã xác nhận |
| P1 | Player vừa tự tick vừa được Zone tick; cùng object bị update từ hai thread | Đã xác nhận |
| P1 | Disconnect trừ bộ đếm IP hai lần qua hai call path | Đã xác nhận |
| P1 | Sender có queue vô hạn; client chậm có thể làm tăng heap không giới hạn | Đã xác nhận |
| P1 | State và collection quan trọng được truy cập từ nhiều thread nhưng thiếu ownership/lock | Đã xác nhận |
| P1 | Dữ liệu player chủ yếu save lúc logout; crash có thể mất tiến trình từ lần save gần nhất | Đã xác nhận theo call site |
| P1 | Mật khẩu plaintext và SQL dump có dữ liệu tài khoản/secret được track | Đã xác nhận |
| P1/P2 | Lỗi trong Zone task có thể bị Future giữ lại nhưng Manager chỉ take, không get nên không log nguyên nhân | Đã xác nhận |
| P2 | Controller chỉ log năm exception đầu tiên cho toàn server do errors là field singleton | Đã xác nhận |
| P2 | PlayerAppearanceService mới tách có lỗi ưu tiên toán tử tại ba điều kiện fusion | Đã xác nhận |
| P2 | Command 42 fall-through sang LuckyRound không có chủ đích được thể hiện | Đã xác nhận về control flow |

Không nên bắt đầu bằng đổi framework, nâng pool DB lên một con số tùy ý, hoặc chuyển mọi List sang CopyOnWriteArrayList. Những thay đổi đó không giải quyết invariant kinh tế và có thể tạo regression mới.

---

## 2. Kiến trúc hiện tại

Server là stateful Java monolith dùng giao thức TCP nhị phân.

~~~text
Client
  ↓ TCP
Network accept loop
  ↓
MySession
  ├─ Collector thread: đọc packet
  └─ Sender thread: ghi packet qua queue
  ↓
Controller.onMessage
  ↓
Service / UseItem / Input / Trade / Shop / Clan / Map services
  ↓
Player / Inventory / NPoint / Zone / Item / Mob
  ↓
PlayerDAO / LocalManager / MySQL
~~~

Song song với packet thread:

- Manager dùng ScheduledExecutorService và fixed worker pool để tick toàn bộ Zone mỗi giây.
- Player.start tạo thêm một executor cho từng player online và cũng gọi Player.update mỗi giây.
- ServerManager khởi động nhiều thread riêng cho boss, event, minigame và dịch vụ nền.
- Một số dungeon/service tự tạo executor nhưng không giữ reference và không shutdown.
- Map.java có executor riêng nhưng không tìm thấy call site chạy Map.run trong snapshot này; không được tính thành một thread hoạt động cho mỗi map.

Hệ quả kiến trúc chính là không có một mô hình ownership nhất quán cho mutable state. Packet thread, player loop, zone worker, event thread và disconnect thread có thể cùng sửa Player, Inventory, Zone hoặc registry.

---

## 3. Audit lỗi và rủi ro đã cập nhật

### 3.1. P0 — Nhận thành tựu lặp vô hạn

Call path:

~~~text
packet command -76
  → Controller.java:782-783
  → AchievementService.confirmAchievement
  → cộng gem
~~~

Bằng chứng:

- Controller.java:782-783 gọi confirmAchievement với select đọc trực tiếp từ packet.
- AchievementService.java:51-61 lấy money từ Manager.ACHIEVEMENT_TEMPLATE, gọi reward rồi cộng gem.
- Achievement.java:60-63 có method canReward kiểm tra đủ tiến độ và chưa nhận, nhưng confirmAchievement không gọi method này.
- Achievement.reward chỉ đặt trạng thái nhận thành true; packet lặp lại vẫn tiếp tục cộng gem.
- Không có lock/idempotency để chặn hai packet đồng thời.

Hậu quả:

- Với một index hợp lệ và có ô trống bag, client có thể gửi lại command để cộng ngọc nhiều lần.
- Index âm hoặc vượt range gây exception, nhưng exception bị Controller nuốt sau cơ chế log giới hạn; đây còn là nguồn log/noise.

Sửa ngắn hạn:

1. Kiểm tra player, achievement, index và template range trước mọi truy cập.
2. Bắt buộc achievement.canReward(index) trả true.
3. Trong critical section theo player, đánh dấu đã nhận đúng một lần và cộng thưởng đúng một lần.
4. Dùng phép cộng có cap, không cộng int trực tiếp.
5. Save trạng thái thưởng và số dư trong cùng transaction/persistence unit.
6. Thêm audit ledger với playerId, achievementId, requestId, before, delta, after.

Acceptance test:

- Chưa hoàn thành: không nhận thưởng.
- Đã hoàn thành: nhận đúng một lần.
- Gửi cùng packet 100 lần tuần tự hoặc đồng thời: tổng thưởng vẫn chỉ một lần.
- Index -1, 127 và vượt danh sách: trả lỗi protocol, không ném exception.
- Crash giữa mark và credit không làm mất hoặc nhân đôi thưởng.

### 3.2. P0 — LuckyRound count âm và overflow

Call path:

~~~text
packet -127 hoặc fall-through từ command 42
  → LuckyRound.readOpenBall
  → openBallByGem / openBallByGold / openBallByTicket
~~~

Bằng chứng:

- LuckyRound.java:83 đọc count bằng signed byte.
- LuckyRound.java:108 tính gemNeed = count * PRICE_GEM.
- LuckyRound.java:125 tính goldNeed bằng int; giá vàng lớn nên có thể overflow.
- LuckyRound.java:130 trừ goldNeed; nếu goldNeed âm thì phép trừ làm tăng vàng.
- LuckyRound.java:142 và 150 có hành vi tương tự với ticket.
- RewardService lặp theo count; count âm có thể không sinh item nhưng số dư đã bị tăng.

Không nên chỉ sửa thành count > 0 && count <= 100. Server phải whitelist đúng các lựa chọn mà client hỗ trợ, ví dụ 1 hoặc 10 nếu protocol chỉ có hai lựa chọn.

Sửa:

- Decode sang int không dấu nếu wire contract yêu cầu, sau đó whitelist.
- Tính chi phí bằng Math.multiplyExact trên long.
- Từ chối cost <= 0 hoặc vượt cap.
- Gọi Wallet.tryDebit trước khi tạo reward.
- Reward phải có requestId/idempotency key.
- Command 42 phải break rõ ràng hoặc được khai báo alias bằng test protocol; không để fall-through ngầm.

### 3.3. P0 — Trade thiếu actor-specific state machine

Bằng chứng:

- Trade.java:349-353 chỉ tăng accept và commit khi accept == 2.
- Method acceptTrade không nhận Player, nên không biết ai vừa đồng ý.
- TransactionService.java:153-164 gọi acceptTrade từ packet của bất kỳ một bên.
- Cùng một người có thể gửi ACCEPT hai lần.
- lockTran tại Trade.java:293-347 chỉ gửi packet cho đối phương, không đóng băng offer.
- startTrade thay toàn bộ itemsBag của cả hai player tại Trade.java:394-395.
- PLAYER_TRADE là HashMap dùng chung giữa Collector thread và timer thread.
- Cùng một Trade được lưu bằng hai key, nên vòng update có thể gọi cùng trade hai lần trong một lượt quét.

Thiết kế bắt buộc:

~~~text
INVITED
  → OPEN
  → P1_LOCKED / P2_LOCKED
  → BOTH_LOCKED
  → P1_ACCEPTED / P2_ACCEPTED
  → COMMITTING
  → COMMITTED
hoặc CANCELLED / EXPIRED
~~~

Quy tắc:

- lock(player) và accept(player) phải actor-specific, idempotent.
- Sau khi một bên lock, mọi thay đổi offer của bên đó phải bị từ chối hoặc reset lock/accept của cả hai.
- Commit chỉ chạy một lần bằng compare-and-set hoặc lock của Trade.
- Revalidate item identity, quantity, gold, capacity và option ngay trước commit.
- Không thay nguyên bag snapshot. Áp dụng delta/reservation trên inventory hiện tại.
- Lưu ledger giao dịch cùng trạng thái commit.
- Timeout, disconnect và maintenance đi qua cùng một cancel path idempotent.

### 3.4. P0/P1 — Shop ký gửi

#### Validation

- Controller.java:117 chỉ gọi KiGui khi quantity > 0, nên nhận định cũ “client hiện tại gửi quantity âm để khai thác trực tiếp” không đúng.
- ConsignShopService.java:426 vẫn thiếu quantity <= 0 ở tầng service.
- Các điều kiện tại 458, 467 và 487 dùng dạng lớn hơn max đồng thời nhỏ hơn 0, nên luôn false.
- Phí đăng bán bị trừ trước validation max.
- getMaxId() + 1 không an toàn khi có hai listing tạo đồng thời.

#### Double-buy và mất tiền

- buyItem tại ConsignShopService.java:109-157 đọc it.isBuy, trừ tiền, đặt isBuy rồi add item mà không có lock/transaction.
- Hai buyer có thể cùng thấy isBuy == false và cùng mua.
- Kết quả InventoryService.addItemBag bị bỏ qua; bag đầy vẫn có thể bị trừ tiền và listing bị bán.

#### Persistence

- ConsignShopManager.java:27-40 TRUNCATE toàn bảng rồi INSERT từng dòng.
- Không transaction, không staging table, không rollback.
- Crash hoặc SQL error giữa chừng có thể để bảng rỗng hoặc thiếu dữ liệu.

Thiết kế sửa:

- Bảng listing là source of truth; có status ACTIVE, RESERVED, SOLD, CLAIMED, CANCELLED và version.
- Mua bằng một transaction DB có conditional update:
  - UPDATE listing SET status = SOLD, buyer_id = ?, version = version + 1
  - WHERE id = ? AND status = ACTIVE AND version = ?
- affectedRows phải bằng 1.
- Debit, grant hoặc pending grant và ledger cùng transaction.
- Nếu inventory đầy, tạo pending delivery/mailbox thay vì làm mất item.
- Không dùng TRUNCATE để save runtime state.
- Listing ID do DB AUTO_INCREMENT hoặc sequence cấp.

### 3.5. P0 — VND và VIP không atomic

Bằng chứng:

- PlayerDAO.subvnd tại 1173-1190 chỉ kiểm tra số dư trong session.
- SQL update không có điều kiện AND vnd >= ?.
- Không kiểm tra affectedRows.
- Input.java gọi subvnd nhưng bỏ qua kết quả ở các luồng đổi.
- ToriBot.java:125-146 chỉ trừ session.vnd trong RAM rồi cấp VIP/reward; số dư account trong DB không được debit bền vững.

Yêu cầu sửa:

- Chỉ một MoneyLedgerService được phép thay đổi VND.
- Debit dùng conditional SQL và affectedRows == 1.
- Mọi purchase có requestId duy nhất.
- Cùng transaction phải ghi purchase ledger và trạng thái pending reward.
- Reward delivery idempotent; retry không debit lần hai.
- Không log password, token, raw request hoặc số dư nhạy cảm.
- Viết reconciliation job so sánh ledger với account balance.

### 3.6. P1 — Rò thread theo lần đăng nhập và double-tick

Bằng chứng:

- Controller.java:1055 gọi player.start.
- Player.java:458 tạo Executors.newSingleThreadExecutor().submit rồi bỏ reference.
- Đối số chuỗi của submit là giá trị result của Future, không phải tên thread.
- Executor không shutdown. Khi Player.run kết thúc, worker core vẫn chờ queue và tồn tại.
- Zone.java:258-264 cũng gọi update cho player.
- Manager.java:284-324 tick mọi Zone mỗi giây.

Hậu quả:

- Mỗi login tạo ít nhất một executor worker bị rò sau logout.
- Login churn làm thread count tăng không giới hạn.
- Player.update chạy đồng thời từ player worker và zone worker.
- HashMap activeEffects, state item, badge, quest, HP/MP và dispose có race.
- Duplicate work đã thấy trong Player.update: autoSendBadges và BadgesTaskService.updateDoneTask được gọi ở cả 571-572 và 636-637 trong cùng nhánh.

Sửa ưu tiên:

1. Chọn một owner duy nhất cho Player online. Khuyến nghị Zone/WorldTickEngine là owner.
2. Xóa Player implements Runnable, Player.start và executor theo player sau khi có test tương đương.
3. Packet thread không sửa Player trực tiếp; enqueue command vào mailbox của Zone hoặc PlayerActor.
4. Tách tick thành các bước nhỏ, có budget và metric.
5. Dùng monotonic clock abstraction cho cooldown/test; không rải System.currentTimeMillis.
6. Dispose phải là state transition idempotent, không phải chuỗi null field có thể chạy đồng thời với update.

Mục tiêu ngắn hạn nếu chưa làm mailbox:

- Dừng một trong hai tick path.
- Giữ ScheduledFuture/ExecutorService bằng field có tên, shutdownNow khi disconnect.
- Dùng AtomicBoolean bảo vệ started và disposed.
- Đây chỉ là bước cầu nối, không phải kiến trúc đích.

### 3.7. P1 — Disconnect không idempotent và bộ đếm IP bị trừ hai lần

Call path:

~~~text
Collector kết thúc
  → ServerManager.sessionDisconnect
      → Client.kickSession
          → Client.remove
              → ServerManager.disconnect
          → Session.disconnect
      → ServerManager.disconnect lần nữa
  → Session.disconnect lần nữa
~~~

Bằng chứng:

- Collector.java:46 gọi sessionDisconnect, sau đó line 50 gọi session.disconnect.
- ServerManager.java:230-233 gọi Client.kickSession rồi gọi disconnect thêm lần nữa.
- Client.java:81-95 remove session và gọi ServerManager.disconnect.
- Client.java:143-147 lại gọi Session.disconnect.
- Session.connected, beforeDispose, ServerManager.isRunning và Maintenance.isRunning không phải volatile/atomic.

Hậu quả:

- CLIENTS theo IP bị decrement hai lần, làm connection limit sai và dễ bị bypass dưới login churn.
- Save/dispose có thể chạy lặp hoặc đua với update.
- Collection registry có thể bị remove không đồng bộ.
- Exception trong teardown dễ bị nuốt.

Thiết kế sửa:

- Session có state NEW, ACTIVE, CLOSING, CLOSED bằng AtomicReference.
- close(cause) chỉ người thắng CAS thực hiện cleanup.
- Một SessionLifecycleService duy nhất chịu trách nhiệm:
  - dừng receive/send;
  - remove registry;
  - decrement IP đúng một lần;
  - enqueue player logout/save;
  - close socket;
  - phát metric.
- Client, Collector và ServerManager chỉ yêu cầu close, không tự lặp cleanup.
- Bộ đếm IP dùng ConcurrentHashMap.compute hoặc LongAdder có remove khi về 0.

### 3.8. P1 — Network backpressure và flood control

- Sender.java dùng LinkedBlockingDeque không giới hạn.
- Sender busy-poll queue và sleep 10 ms.
- Exception send bị nuốt; session có thể còn trạng thái connected sai.
- Không thấy command budget/rate limit tổng quát cho packet gameplay.
- Mỗi session hiện dùng Collector và Sender thread; với 500 CCU đã khoảng 1.000 network thread, chưa tính các thread khác.
- Socket buffer được request 1 MiB send và 1 MiB receive, nhưng không được suy ra máy chắc chắn cấp đúng 2 MiB cho mỗi socket. Phải đo kernel memory và throughput thực tế.
- Selector hiện chỉ dùng accept; IO sau accept vẫn là blocking stream.

Sửa theo hai bước:

Bước 1:

- Queue bounded theo số message và tổng byte.
- Chính sách rõ: drop packet không quan trọng, coalesce state update, hoặc disconnect slow consumer.
- Dùng blocking take thay busy-poll.
- Per-command rate limit và packet budget theo session/IP/account.
- Metric queue depth, bytes queued, dropped, disconnect cause.

Bước 2 chỉ khi benchmark chứng minh cần:

- Chuyển IO sang event loop/NIO/Netty.
- Không đổi network stack cùng lúc với refactor protocol handler.

### 3.9. P1 — Collection và memory visibility

Các mutable collection quan trọng:

- Client: ba HashMap và một ArrayList cho player online.
- ServerManager.CLIENTS: HashMap.
- TransactionService.PLAYER_TRADE: HashMap.
- Zone: ArrayList cho player, mob, item, boss, pet.
- Boss manager và event manager: nhiều ArrayList.
- MySession.ANTILOGIN: HashMap.

Chúng được truy cập từ accept, collector, sender, zone worker, event worker và disconnect path. Giải pháp không phải đổi toàn bộ sang CopyOnWriteArrayList.

Chọn theo semantics:

- Lookup registry: ConcurrentHashMap và method atomic add/remove.
- Aggregate nhiều index như Client: một lock hoặc một owner event-loop cho toàn bộ bốn cấu trúc.
- Zone: owner-thread + mailbox; reader khác nhận snapshot.
- Danh sách đọc nhiều, ghi rất ít và nhỏ mới cân nhắc CopyOnWriteArrayList.
- Trade/inventory: lock theo aggregate và thứ tự lock ổn định.
- Lifecycle flag: AtomicBoolean/AtomicReference hoặc volatile khi chỉ cần visibility.

### 3.10. P1 — Persistence và crash consistency

- PlayerDAO.updatePlayer là một method rất lớn, serialize nhiều JSON và update khoảng 63 cột.
- MrFinn deserialize hơn 120 member của Player; PlayerDAO truy cập trực tiếp hơn 110 member.
- SuperRank được update riêng sau player update, nên không cùng transaction.
- Scheduler trong PlayerDAO được khai báo nhưng không có call site.
- Call site save player tập trung ở logout và một số nghiệp vụ đặc biệt; không thấy autosave tổng quát.
- Server shutdown có save có kiểm soát, nhưng kill -9, crash JVM hoặc mất điện có thể mất tiến trình từ lần save gần nhất.
- Positional JSON phụ thuộc index, có một số guard tương thích nhưng chưa có schemaVersion/migration rõ ràng.

Hướng sửa:

- PlayerSnapshot immutable tách khỏi live Player.
- PlayerRepository chỉ nhận snapshot, không đọc hơn 100 public field trong khi object đang bị mutate.
- Autosave theo dirty component, phân tán theo thời gian để tránh burst.
- Snapshot được tạo trên owner thread.
- Có save version/optimistic lock để tránh save cũ ghi đè save mới.
- Dữ liệu giá trị cao dùng bảng chuẩn hóa và ledger.
- JSON còn lại phải có schemaVersion, parser tolerant và migration test.
- Save queue có retry, dead-letter và metric backlog.

### 3.11. P1/P2 — Scheduler và lỗi task bị ẩn

- Manager.java tạo task cho các batch Zone, submit qua ExecutorCompletionService.
- Vòng chờ chỉ gọi completionService.take tại 317-319, không gọi Future.get.
- Exception bên trong Callable được giữ trong Future nhưng không được đọc, nên nguyên nhân có thể bị mất.
- Một Zone treo làm cả tick đợi vô hạn.
- mapUpdater tại Manager.java:98 được tạo nhưng không dùng.
- BossManager dùng sleep dựa trên 1500 - elapsed; nếu elapsed vượt ngưỡng, sleep âm ném exception và bị bỏ qua, làm loop có thể chạy sát CPU khi quá tải.

Sửa:

- Future.get với logging theo zoneId/mapId.
- Deadline cho tick; task quá hạn phải có metric và circuit-breaker phù hợp.
- Không được chạy tick kế tiếp chồng lên mutable state của tick trước.
- Xóa executor không dùng.
- Mọi sleep dùng Math.max hoặc scheduler fixed-delay.
- ThreadFactory đặt tên, uncaught exception handler và daemon policy rõ ràng.

### 3.12. P2 — Controller che mất lỗi sau năm exception

- Controller là singleton.
- Field errors tại Controller.java:72 dùng chung cho mọi session.
- Catch ở 794-803 chỉ log nếu errors < 5.
- Sau năm lỗi đầu tiên của toàn server, mọi lỗi onMessage tiếp theo im lặng.

Sửa:

- Không dùng global error cap.
- Rate-limit log theo command + exception class + time window.
- Gắn sessionId, playerId, command, subcommand và correlationId.
- Không log password/token/payload nhạy cảm.
- Metric counter luôn tăng kể cả log bị sampling.

### 3.13. P2 — PlayerAppearanceService vừa tách có guard sai

PlayerAppearanceService.java hiện là file chưa track và Player.java đã delegate các method appearance sang đây. Đây là hướng tách đúng, nhưng ba điều kiện tại khoảng line 190, 248 và 312 có dạng:

~~~text
A && B && fusion == X || fusion == Y || fusion == Z ...
~~~

Do && có ưu tiên cao hơn ||, guard isPl và pet != null chỉ áp dụng cho X. Với các fusion type còn lại, code có thể vào nhánh rồi dereference pet dù pet null.

Sửa:

- Tính boolean isSupportedFusion bằng một method riêng.
- Condition phải là player.isPl() && player.pet != null && isSupportedFusion.
- Guard fusion, inventory, body slot và pet inventory trước truy cập.
- Thêm test cho mọi fusion type, player thường, pet null, fusion null, body thiếu slot.

Đây là ví dụ cho lý do phải có characterization test trước khi di chuyển code ra khỏi Player.

### 3.14. P2 — Protocol fall-through và validation không đồng nhất

- Controller.java:257-264 để command 42 fall-through sang -127/LuckyRound vì break đã bị comment.
- Command -76 không check player trước khi gọi AchievementService.
- Nhiều service tự đọc tiếp Message, làm decode và mutation trộn với nhau.
- Kiểm tra maintenance, trade, account protection và player null được lặp không đồng nhất giữa command.
- Nhiều index đọc từ byte/short rồi truy cập List trực tiếp; exception thường bị catch ở tầng cao thay vì trả lỗi protocol.

Sửa bằng command policy và DTO decode tập trung, trình bày ở phần Controller.

### 3.15. Bảo mật tài khoản, secret và transport

- Password được so sánh/lưu plaintext trong luồng hiện tại.
- SQL dump được Git track có dữ liệu account/admin, password và các trường session/device/token. Không liệt kê giá trị trong tài liệu này.
- XOR session key là obfuscation, không phải transport security.
- AntiLogin runtime có giới hạn, nhưng map/counter update không atomic và không có TTL eviction đầy đủ.
- server.maxperip rất cao trong cấu hình hiện tại, nên không phải lớp bảo vệ hữu hiệu.

Kế hoạch:

1. Rotate toàn bộ secret đã từng commit.
2. Thay dump thật bằng fixture đã khử dữ liệu.
3. Purge secret khỏi Git history theo quy trình có backup và phối hợp tất cả clone.
4. Password chuyển sang Argon2id hoặc bcrypt; login cũ được rehash khi xác thực thành công.
5. Nếu game protocol không thể TLS trực tiếp, đặt TLS gateway/tunnel phía trước và ký request quan trọng ở application layer.
6. Rate limit theo IP, account, device và command; không dựa duy nhất vào client.

### 3.16. Các nhận định cũ đã hiệu chỉnh

Không giữ nguyên các kết luận sau:

- Negative quantity ở ký gửi: service validation sai, nhưng Controller hiện chặn quantity <= 0 trước call site duy nhất đã tìm thấy.
- SQL injection: có dynamic SQL cần chuẩn hóa, nhưng các ví dụ IP/playerId/admin column đã rà không chứng minh được input client điều khiển trực tiếp.
- AntiLogin vô hiệu: file models/AntiLogin.java trả true là code cũ không dùng; runtime import player_system.AntiLogin có giới hạn, vấn đề thật là thread-safety và eviction.
- Trade dup do throw/use item trong lúc trade: nhiều action đã cancel/block trade. Lỗi P0 thật là accept không gắn actor và offer không bị khóa.
- NPoint overflow toàn diện: HP/MP/damage chính đã dùng long và clamp ở nhiều điểm. Vẫn cần audit có mục tiêu cho defense và cast.
- Mỗi Map tạo một thread: Map.run không có call site trong snapshot. Executor field là nợ kỹ thuật nhưng chưa tạo 169 thread hoạt động.
- Hikari pool 8 chắc chắn quá nhỏ: chưa có benchmark. Phải đo acquisition latency, active/idle, query latency và tải thực tế.
- Socket buffer chắc chắn tiêu tốn 2 GiB cho 1.000 client: request buffer không đồng nghĩa kernel cấp ngay toàn bộ. Phải đo.
- Ant là lỗi P3: Ant không tự tạo lỗi runtime. Vấn đề là dependency lock/checksum, reproducible build và CI.

---

## 4. Có cần tách Player.java không?

Có, nhưng không tách Player thành một “túi DTO” rời rạc và không chuyển toàn bộ field trong một pull request.

### 4.1. Bằng chứng cần tách

Snapshot hiện tại:

- Player.java: 1.706 dòng.
- 76 import.
- Khoảng 235 khai báo public field theo phép đếm heuristic.
- Khoảng 694 phép gán trực tiếp dạng player.field từ các file khác.
- MrFinn và PlayerDAO mỗi file phụ thuộc trực tiếp hơn 110 member của Player.
- Player vừa chứa identity, session, position, combat, inventory reference, clan, event, quest, appearance, tick loop, damage, movement và dispose.
- update dài khoảng 200 dòng và gọi nhiều singleton service.
- dispose null hàng chục component; không phải lifecycle state machine.
- Player implements Runnable tạo runtime thread riêng, trong khi Zone cũng tick.

Đây là God Object và shared mutable aggregate. Tuy nhiên Player vẫn là đối tượng trung tâm của domain, nên loại bỏ Player hoàn toàn sẽ làm code khó hiểu hơn.

### 4.2. Vai trò Player nên giữ

Player sau refactor nên giữ:

- playerId, accountId/name/gender và identity ổn định;
- reference đến các component domain chính;
- invariant cấp aggregate;
- method có ý nghĩa domain, ví dụ trySpend, receiveReward, moveTo, applyDamage;
- lifecycle state tổng quát;
- facade tương thích tạm thời cho code cũ.

Mục tiêu thực tế: đưa Player xuống khoảng 300–500 dòng sau vài phase, không phải ép số dòng ngay lập tức.

### 4.3. Phần cần tách khỏi Player

| Phần hiện tại | Đích đề xuất | Ghi chú |
|---|---|---|
| run/start/update scheduling | WorldTickEngine + PlayerTickPipeline | Một owner, không executor/player |
| update từng feature | PlayerTickStep implementations | Cooldown, effects, quest, badge, event |
| appearance getters | PlayerAppearanceService | Đã bắt đầu; phải sửa guard và thêm test |
| session/zone/location/dispose | PlayerRuntimeContext + PlayerLifecycleService | Tách transient state khỏi persisted state |
| event/minigame flags | PlayerEventState / ActivityState | Gom theo bounded context, không một object “flags” khổng lồ |
| wallet/gold/gem/ruby/VND | Wallet + MoneyLedgerService | Mọi debit/credit qua API có reason/requestId |
| save/load mapping | PlayerSnapshotMapper + PlayerRepository | Không để DAO đọc trực tiếp hàng trăm public field |
| active effects | ActiveEffectState | Owner-thread hoặc collection có lock rõ |
| badge/collection state | Component hiện có hoặc module riêng | Không tick lặp |
| damage logic lớn | CombatService/CombatResolver | Player giữ applyDamage facade |
| hardcoded outfit tables | AppearanceCatalog/TemplateRegistry | Dữ liệu immutable |

### 4.4. Thứ tự tách Player an toàn

1. Viết Player characterization test: load fixture → snapshot → save → reload phải tương đương.
2. Tạo PlayerLifecycleState và làm disconnect/dispose idempotent.
3. Loại bỏ executor theo player và chọn owner tick.
4. Tách PlayerTickPipeline nhưng chưa đổi thứ tự step.
5. Hoàn thiện PlayerAppearanceService và test wire output head/body/leg.
6. Tạo Wallet, chuyển tất cả luồng P0 trước; public currency field chỉ đọc trong giai đoạn cầu nối.
7. Tạo PlayerSnapshotMapper; DAO không đọc live object trực tiếp.
8. Gom persisted event fields theo schemaVersion.
9. Đóng dần public field; mỗi nhóm field có API domain thay thế.
10. Sau khi caller cũ về 0 mới xóa compatibility getter/field.

### 4.5. Những cách tách không nên làm

- Không tạo 200 getter/setter rồi coi là encapsulation.
- Không chuyển từng nhóm field sang class mới nhưng vẫn public và mutate từ mọi thread.
- Không tách Combat/Inventory/Quest thành microservice.
- Không sửa Player, protocol và DB schema trong cùng một bước không có golden test.
- Không thay đổi thứ tự tick vô tình; nhiều cooldown đang phụ thuộc thời gian và thứ tự side effect.
- Không giữ cả Player loop và Zone loop “cho chắc”.

---

## 5. Giảm phụ thuộc trong Manager.java

### 5.1. Vấn đề hiện tại

Manager.java hiện có:

- 1.374 dòng, 73 import.
- config mutable global;
- load Properties;
- load hàng chục bảng DB;
- ghi asset file;
- tạo NPC;
- init Map;
- schedule activity config;
- schedule Zone tick;
- giữ template registry;
- giữ clan/NPC runtime registry;
- leaderboard query và flags;
- helper lookup;
- 37 file gọi trực tiếp Manager static member theo phép đếm hiện tại.

Static final List chỉ làm reference final, không làm nội dung immutable. CLANS và NPCS còn bị sửa từ file khác. Template và runtime entity đang bị trộn trong cùng class.

### 5.2. Kiến trúc đích cho Manager

~~~text
GameBootstrap
  ├─ ServerConfig
  ├─ DatabaseMigrator
  ├─ TemplateDataLoader
  ├─ TemplateRegistry          immutable sau bootstrap
  ├─ WorldFactory
  ├─ WorldRegistry            Map/Zone runtime
  ├─ ClanRegistry             dynamic, API atomic
  ├─ NpcRegistry              dynamic, API atomic
  ├─ NotificationCatalog
  ├─ LeaderboardService
  ├─ ActivityConfigProvider
  └─ WorldTickEngine
~~~

Trách nhiệm:

- ServerConfig: record immutable; parse, validate và fail với thông báo rõ.
- TemplateDataLoader: chỉ đọc DB/file và trả DataBundle, không ghi global state giữa chừng.
- TemplateRegistry: lookup typed theo ID; expose Optional hoặc fail-fast có context.
- WorldFactory: tạo Map/Zone từ template.
- WorldRegistry: quản lý world runtime; không chứa item template.
- ClanRegistry/NpcRegistry: method add/remove/find atomic, không public List.
- LeaderboardRepository/Service: query và cache top.
- WorldTickEngine: scheduler, deadline, metrics và shutdown.
- GameBootstrap: điều phối thứ tự startup; không trở thành Manager mới.

### 5.3. Cách migration không làm vỡ 37 caller

Phase cầu nối:

- Giữ Manager nhưng đánh dấu compatibility facade.
- Manager.ITEM_TEMPLATES chuyển thành view read-only trỏ sang TemplateRegistry.
- Method Manager.getX delegate sang registry mới.
- Không cho code mới dùng Manager static field.
- Viết rule CI/ArchUnit hoặc script rg để chặn dependency mới.

Migration theo cụm:

1. Config constants.
2. Item/Mob/NPC/Achievement template.
3. Map/world.
4. Clan runtime.
5. Leaderboard.
6. Notification.
7. Scheduler.
8. Xóa loadDatabase và constructor side-effect cuối cùng.

### 5.4. Điều kiện nghiệm thu Manager

- Không public mutable collection.
- Template registry immutable sau bootstrap.
- Runtime registry có API add/remove/find và test concurrent.
- Loader trả lỗi có tên bảng/record; không System.exit sâu trong loader.
- Startup build toàn bộ snapshot trước rồi publish atomically.
- Reload config nếu có phải versioned; reader luôn thấy snapshot hoàn chỉnh.
- Scheduler có shutdown và health metric.
- Manager compatibility caller bằng 0 trước khi xóa class.

---

## 6. Tách Controller.java

### 6.1. Vấn đề hiện tại

Controller.java hiện có:

- 1.065 dòng.
- 66 import, khoảng 39 import service.
- 130 case label tính cả nested switch.
- Khoảng 312 biểu thức gọi method theo phép đếm heuristic.
- Một switch chính từ line 92 đến 793.
- Vừa decode Message, vừa validate state, vừa gọi domain service, vừa gửi lỗi.
- Protocol command, subcommand clan/radar/fishing và nghiệp vụ bị trộn.
- Một số service tự đọc tiếp cùng Message, nên ownership cursor không rõ.
- Chính sách maintenance/trade/protection/player-null lặp và lệch nhau.
- Global exception cap làm mất observability.

Controller nên còn khoảng 50–100 dòng: nhận packet, tạo context, route handler, ghi metric và xử lý protocol exception.

### 6.2. Kiến trúc protocol đích

~~~text
Collector
  → PacketDecoder
  → CommandDispatcher
      → CommandPolicy
      → CommandHandler
          → Application Use Case
              → Domain
              → Repository / OutputPort
  → PacketEncoder
~~~

Các type đề xuất:

~~~text
CommandKey(command, optionalSubcommand)
CommandContext(session, player, protocolVersion, correlationId)
CommandPolicy(requiresLogin, requiresPlayer, allowMaintenance,
              allowDuringTrade, requiresAccountActive, rateLimit)
CommandHandler.decode(reader) → RequestDTO
CommandHandler.handle(context, requestDTO) → Result
ResponseWriter.write(context, result)
~~~

Handler không được giữ Message sau khi return và không được mutate DB trước khi decode/validate xong.

### 6.3. Nhóm handler đề xuất

| Nhóm | Command ví dụ | Package đích |
|---|---|---|
| Handshake/login | -27, -29, create/login | protocol.auth |
| Asset/data | -74, -87, -67, 66, -111, -28 | protocol.asset |
| Movement/map | move, change zone, home, revive | protocol.world |
| Combat | attack mob/player, skill | protocol.combat |
| Inventory/item | use, throw, bag/body/box | protocol.inventory |
| Economy/shop | 6, 7, -100, -127, -76 | protocol.economy |
| Trade | -86 | protocol.trade |
| NPC/menu/input | menu, confirm, -125 | protocol.npc |
| Social/clan | friend, enemy, chat, command 127 subcommands | protocol.social |
| Activity/event | activity, fishing, event | protocol.activity |
| Admin | boss teleport/admin command | protocol.admin |

Command 127 đang chứa nhiều subcommand không liên quan. Tạm thời dùng SubcommandDispatcher riêng; về dài hạn nên cấp namespace/command riêng nếu client protocol có thể nâng version.

### 6.4. Thứ tự tách Controller

1. Lập wire_contracts cho mọi command: byte order, signedness, version gate, max length.
2. Viết golden tests ghi request bytes và response bytes.
3. Tạo Dispatcher nhưng default vẫn gọi LegacyControllerHandler.
4. Tách command P0 trước: achievement, lucky round, trade, consignment, VND input.
5. Tách auth/asset vì ít phụ thuộc Player hơn.
6. Tách inventory/shop.
7. Tách map/combat.
8. Tách social/clan/activity.
9. Xóa switch cũ khi registry cover 100%.
10. Bật CI check: mỗi command key duy nhất, policy bắt buộc, unknown command có metric.

Rollout:

- Feature flag theo command, không chạy song song hai handler có mutation.
- Có thể shadow-decode để so DTO, nhưng chỉ một phía được execute.
- Rollback bằng route command về legacy handler.
- Mỗi PR chỉ tách một nhóm command và giữ wire output tương đương.

### 6.5. Central policy cần có

- Session state.
- Player presence và lifecycle ACTIVE.
- Maintenance mode.
- Transaction/trade state.
- Account protection.
- Account activation.
- Rate limit.
- Payload length/range.
- Protocol version.
- Permission/admin role.
- Idempotency requirement cho command có thưởng/tiền.

Policy giúp tránh tình trạng command này check trade nhưng command gần giống lại quên.

---

## 7. Kiến trúc đích khuyến nghị: Modular Monolith

### 7.1. Dependency direction

~~~text
adapter.in.protocol
        ↓
application.usecase
        ↓
domain
        ↑
application.port
        ↑
adapter.out.persistence / network / metrics
~~~

Quy tắc:

- Domain không import Controller, Message, MySession, JDBC hoặc Manager.
- Handler protocol không chứa SQL.
- Repository không gửi packet.
- Service gửi packet hiện tại được bọc qua output port khi tách dần.
- Module chỉ chia sẻ ID/value object và interface được cho phép.
- Mutable world state có owner; không global static public.

### 7.2. Cấu trúc package gợi ý

~~~text
nro.bootstrap
nro.config
nro.protocol
  auth
  asset
  world
  combat
  inventory
  economy
  social
  activity
nro.application
  command
  player
  trade
  shop
  reward
nro.domain
  player
  inventory
  wallet
  trade
  consign
  achievement
  world
nro.runtime
  tick
  mailbox
  lifecycle
nro.infrastructure
  persistence
  network
  metrics
  logging
nro.legacy
  compatibility facades trong giai đoạn migration
~~~

Không bắt buộc di chuyển package hàng loạt ngay. Dependency direction quan trọng hơn tên folder.

### 7.3. Invariant trung tâm

Mọi thay đổi giá trị phải tuân thủ:

- Input amount > 0.
- Phép nhân/cộng dùng exact arithmetic hoặc saturating policy rõ.
- Không vượt cap.
- Một command logic có requestId.
- Debit và grant không được tách thành hai thao tác không liên kết.
- Mỗi reward chỉ có một ledger entry unique.
- Inventory capacity được reserve trước commit hoặc có pending delivery.
- Mutable aggregate chỉ có một owner hoặc một lock protocol được tài liệu hóa.

---

## 8. Ba phương án triển khai

### 8.1. Phương án A — Chỉ vá lỗi

Phạm vi:

- Vá P0.
- Chặn negative/overflow.
- Thêm lock ở trade/consign.
- Sửa double disconnect.
- Dừng player executor leak.
- Thêm một số test.

Ưu điểm:

- Nhanh, ít thay đổi.
- Hợp lý nếu cần cứu production ngay.

Nhược điểm:

- Controller, Manager và public Player state vẫn tiếp tục sinh lỗi.
- Mỗi feature mới vẫn chạm nhiều global singleton.
- Lock chắp vá dễ deadlock/race.

Thời gian tham khảo: 2–4 tuần với 2 developer có hiểu source.

### 8.2. Phương án B — Modular monolith tăng dần, khuyến nghị

Phạm vi:

- Vá toàn bộ P0/P1.
- Thiết lập test/CI/metrics.
- Tách Controller theo command.
- Tách Manager thành registry/bootstrap/runtime.
- Thu nhỏ Player theo component và owner tick.
- Cải thiện persistence/ledger.

Ưu điểm:

- Giữ protocol và gameplay hiện tại.
- Rollback theo command/module.
- Giảm rủi ro hơn rewrite.
- Tạo nền cho scaling sau này.

Nhược điểm:

- Cần kỷ luật dependency và feature flag.
- Trong thời gian migration sẽ có compatibility facade.

Thời gian tham khảo: 10–16 tuần với 2–3 developer và 1 QA bán thời gian, tùy mức test hiện có và CCU mục tiêu.

### 8.3. Phương án C — Rewrite hoặc microservice

Không khuyến nghị ở thời điểm này.

Lý do:

- Domain rule nằm rải trong Player, Service, UseItem, NPoint, NPC và event.
- Wire protocol chưa có golden suite hoàn chỉnh.
- Persistence dùng nhiều positional JSON.
- Rewrite dễ mất các behavior ẩn và tạo rollback rất khó.
- Microservice không giải quyết race bên trong Player/Inventory; chỉ thêm distributed transaction.

Chỉ xem xét sau khi modular monolith ổn định, có ledger, observability, load test và ranh giới module đã rõ.

---

## 9. Roadmap chi tiết theo gate

Các ước lượng dưới đây là effort tham khảo, không phải cam kết lịch. Không merge phase sau khi gate trước chưa đạt.

### Gate 0 — Đóng băng behavior và bằng chứng, 3–5 ngày

Deliverable:

- Build sạch từ source trên Java 17.
- Chụp dependency checksum và build artifact checksum.
- Bộ fixture DB đã khử dữ liệu.
- Danh mục command/subcommand và signedness.
- Baseline: CCU, thread count, heap, GC, DB pool wait, zone tick, outbound queue.
- Test tái hiện P0 nhưng chạy trên fixture.
- Backup DB và quy trình restore đã thử.
- Ghi lại working-tree changes hiện có trước khi chia nhánh.

Exit criteria:

- Một lệnh build lặp lại được.
- Test P0 đỏ trước fix.
- Không dùng production secret trong test.
- Có mốc performance baseline.

### Gate 1 — Chặn thất thoát kinh tế, 5–8 ngày

Work item:

- SEC-01 Achievement claim validation + idempotency.
- SEC-02 LuckyRound whitelist + exact arithmetic.
- SEC-03 Trade actor state machine.
- SEC-04 Consign conditional purchase + capacity.
- SEC-05 VND MoneyLedgerService.
- SEC-06 Currency cap và common validation.
- SEC-07 Audit ledger/reconciliation tối thiểu.

Exit criteria:

- Concurrency test 100–1.000 request không nhân đôi reward/debit.
- Không có negative currency/item quantity.
- Mọi P0 có regression test.
- Có script kiểm tra ledger lệch.

Rollback:

- Feature flag từng luồng economy.
- DB migration additive; không drop cột/bảng cũ trong gate này.
- Pending reward có thể replay.

### Gate 2 — Session lifecycle và runtime ownership, 5–10 ngày

Work item:

- RUN-01 Session state machine, close idempotent.
- RUN-02 Sửa IP counter decrement đúng một lần.
- RUN-03 Bounded sender queue và slow-client policy.
- RUN-04 Loại executor leak của Player.
- RUN-05 Chọn Zone/WorldTickEngine làm owner Player.
- RUN-06 Atomic registry hoặc mailbox.
- RUN-07 Volatile/atomic cho lifecycle flag.
- RUN-08 Log disconnect cause và tick exception.

Exit criteria:

- Login/logout soak test không làm thread count tăng tuyến tính.
- Một session chỉ phát một close event và một IP decrement.
- Player.update không chạy đồng thời cho cùng player.
- Queue vượt limit có hành vi xác định.
- 24 giờ soak không tăng heap/thread ngoài biên cho phép.

Rollback:

- Giữ flag chọn legacy tick trong thời gian canary, nhưng không bật đồng thời hai tick.
- Có metric xác nhận route hiện tại.

### Gate 3 — Tách Controller, 2–3 tuần

Work item:

- ARC-C01 CommandKey/Context/Policy/Handler.
- ARC-C02 Legacy fallback.
- ARC-C03 Economy handlers.
- ARC-C04 Auth/asset handlers.
- ARC-C05 Inventory/shop handlers.
- ARC-C06 World/combat handlers.
- ARC-C07 Social/clan/activity handlers.
- ARC-C08 Protocol error taxonomy và rate limit.

Exit criteria:

- 100% command có wire contract.
- Mỗi command có owner handler.
- Controller cũ không còn business mutation.
- Golden packet tests pass cho client version đang hỗ trợ.
- Unknown/malformed packet không disconnect nhầm server-wide và có metric.

### Gate 4 — Tách Manager, 2–3 tuần

Work item:

- ARC-M01 ServerConfig immutable.
- ARC-M02 TemplateDataLoader trả DataBundle.
- ARC-M03 TemplateRegistry.
- ARC-M04 WorldRegistry/WorldFactory.
- ARC-M05 ClanRegistry/NpcRegistry.
- ARC-M06 LeaderboardService.
- ARC-M07 WorldTickEngine.
- ARC-M08 Manager compatibility facade và dependency gate.

Exit criteria:

- Không public mutable registry.
- Manager direct static caller về 0.
- Startup publish registry atomically.
- Reload test không lộ partial state.
- Scheduler có shutdown/health.

### Gate 5 — Thu nhỏ Player và persistence, 3–5 tuần

Work item:

- ARC-P01 PlayerLifecycleState.
- ARC-P02 PlayerTickPipeline.
- ARC-P03 hoàn thiện AppearanceService.
- ARC-P04 Wallet/Inventory transaction boundary.
- ARC-P05 PlayerRuntimeContext.
- ARC-P06 PlayerEventState migration.
- ARC-P07 PlayerSnapshot/Mapper.
- DB-P01 optimistic save version.
- DB-P02 dirty component autosave.
- DB-P03 schemaVersion cho JSON.
- DB-P04 normalize dữ liệu giá trị cao.

Exit criteria:

- Player còn aggregate API rõ, không implements Runnable.
- DAO không truy cập live public state.
- Save/reload round-trip không đổi gameplay state.
- Crash/restart test mất dữ liệu không vượt RPO đã chọn.
- Public field mutation ngoài module giảm về danh sách ngoại lệ đã phê duyệt.

### Gate 6 — Tối ưu và hardening, 1–3 tuần

- Load test theo CCU mục tiêu.
- Tune Hikari từ metric, không dùng con số mặc định tùy ý.
- Kiểm tra GC, allocation, packet throughput.
- Cân nhắc NIO/Netty nếu network thread thật sự là bottleneck.
- Upgrade MySQL Connector/J sau compatibility test.
- Password migration và secret history cleanup.
- Loại dead code/duplicate package.
- Tạo runbook vận hành, backup, restore, incident response.

---

## 10. Backlog triển khai ưu tiên

| ID | Mức | Hạng mục | Phụ thuộc | Effort tương đối | Nghiệm thu chính |
|---|---|---|---|---:|---|
| SEC-01 | P0 | Achievement claim once | Gate 0 fixture | M | 100 concurrent claim chỉ nhận 1 lần |
| SEC-02 | P0 | LuckyRound validation | Gate 0 wire contract | S | Âm/overflow/unsupported count đều bị từ chối |
| SEC-03 | P0 | Trade state machine | Characterization inventory | L | Actor-specific, commit exactly once |
| SEC-04 | P0 | Consign atomic buy | DB migration additive | L | 2 buyer chỉ 1 thành công |
| SEC-05 | P0 | VND ledger | DB migration | L | Debit/grant idempotent, reconcile được |
| RUN-01 | P1 | Session close state machine | Không | M | Cleanup đúng một lần |
| RUN-02 | P1 | IP counter atomic | RUN-01 | S | Soak login/logout không undercount |
| RUN-03 | P1 | Bounded sender queue | Metric bytes/message | M | Slow client không tăng heap vô hạn |
| RUN-04 | P1 | Xóa player executor leak | Tick baseline | S/M | Logout trả thread về baseline |
| RUN-05 | P1 | Single Player owner | RUN-04 | L | Không concurrent update |
| DB-01 | P1 | Autosave + save version | Snapshot | L | Crash test đạt RPO |
| OBS-01 | P1 | Structured log/metrics | Không | M | Không mất exception sau 5 lỗi |
| ARC-C01 | P1 | Command dispatcher/policy | Wire tests | L | Legacy fallback hoạt động |
| ARC-M01 | P2 | Config/registry split | Startup tests | L | Registry immutable/atomic |
| ARC-P01 | P1 | Lifecycle/tick split | RUN-05 | L | Dispose/update không race |
| ARC-P02 | P2 | Snapshot mapper | Round-trip fixtures | L | DAO không đọc live object |
| CODE-01 | P2 | Sửa appearance precedence | Characterization tests | S | Pet/fusion matrix pass |
| CODE-02 | P2 | Xóa fall-through command 42 | Wire contract | S | Command behavior rõ |
| BUILD-01 | P2 | Reproducible build + CI | Gate 0 | M | Build/test chạy trên clean checkout |
| SEC-06 | P1 | Password hash/secret rotation | Backup/migration | L | Không còn plaintext mới |
| PERF-01 | P2 | Load test/tune pool | Metrics/runtime stable | L | Đạt SLO CCU mục tiêu |

S = dưới 1 ngày hoặc thay đổi nhỏ có test; M = 1–3 ngày; L = 3–8 ngày. Các task concurrency/economy phải có review bởi ít nhất một người không viết code chính.

---

## 11. Kế hoạch kiểm thử bắt buộc

Repo hiện có một số test Java thủ công trong tools/tests và ghost-client fixture. Thư mục .github hiện có artifact modernization nhưng chưa có workflow CI. Chưa có một test runner chung bảo vệ toàn server.

### 11.1. Unit test

- Arithmetic: negative, zero, max, overflow.
- Wallet debit/credit/cap.
- Achievement canReward/claim once.
- Trade transition table.
- Consign status transition.
- Player lifecycle transition.
- Parser từng RequestDTO.
- Appearance fusion/pet/body slot.
- JSON migration theo schemaVersion.

### 11.2. Concurrency test

- Hai buyer mua cùng listing.
- Một actor accept trade lặp.
- Hai actor accept cùng lúc.
- Claim reward lặp 100–1.000 request.
- Logout đồng thời với packet/tick/save.
- Change zone trong lúc tick.
- Slow sender và queue overflow.
- Registry add/remove/find đồng thời.

Không dùng Thread.sleep làm điều kiện duy nhất; dùng barrier/latch để mở race window có chủ đích.

### 11.3. Wire/golden test

Mỗi command cần:

- input bytes;
- protocol version;
- decoded DTO;
- policy;
- output bytes hoặc semantic response;
- malformed/truncated packet;
- signed/unsigned boundary;
- unknown subcommand.

Đặc biệt khóa behavior của -29, -28, -30, -86, -100, -127, -125, -76 và command 127.

### 11.4. Persistence test

- Load fixture cũ.
- Migrate.
- Save.
- Reload.
- So sánh snapshot có loại trừ field transient.
- Crash injection sau debit, sau mark, trước grant, sau grant.
- Optimistic version conflict.
- Retry save không ghi đè state mới.

### 11.5. Soak/load test

Kịch bản tối thiểu:

- login/logout churn;
- đứng map, di chuyển, đánh mob;
- shop/trade/consign;
- claim reward;
- client đọc chậm;
- DB chậm hoặc pool cạn;
- maintenance và graceful shutdown.

Đo:

- live thread theo loại;
- heap và allocation;
- GC pause;
- outbound queue depth/bytes;
- zone tick p50/p95/p99/max;
- command latency/error;
- DB acquisition/query latency;
- save backlog/failure;
- duplicate ledger violation;
- disconnect cause.

---

## 12. CI và quality gate

Pipeline tối thiểu:

1. Verify clean dependency checksum.
2. Compile Java 17.
3. Run unit test.
4. Run wire test.
5. Run migration test trên MySQL container/fixture.
6. Scan secret.
7. Static checks:
   - empty catch mới;
   - System.out/printStackTrace mới;
   - public mutable static collection mới;
   - Manager static dependency mới;
   - direct currency mutation mới;
   - raw newSingleThreadExecutor không shutdown;
   - protocol handler thiếu policy.
8. Package artifact.
9. Sinh checksum và provenance.
10. Smoke test server start/login fixture.

Rule “không làm xấu thêm” nên áp dụng trước khi dọn hết nợ cũ: CI chặn vi phạm mới, còn baseline cũ được burn down theo danh sách.

---

## 13. Database và migration strategy

- Mọi migration có số version, checksum và trạng thái applied.
- Ưu tiên expand/contract:
  1. Add bảng/cột/index mới.
  2. Dual-read hoặc backfill.
  3. Chuyển write sang model mới.
  4. Verify/reconcile.
  5. Ngừng read cũ.
  6. Drop ở release riêng sau thời gian rollback.
- Không TRUNCATE state table trong save path.
- Không nhúng DB URL/user/password hardcode. ServerManager.resetNhanQuaHangNgay hiện hardcode localhost:3306/ngocrong nhưng không có caller; nên xóa dead code hoặc chuyển thành repository dùng config trước khi kích hoạt.
- MySQL connector file hiện là nhánh 5.1.23 cũ. Upgrade riêng, có test timezone, Unicode, JSON/text, reconnect, generated key và transaction behavior.
- Pool size được chọn từ:
  - số query đồng thời thực;
  - query latency;
  - DB max_connections;
  - CPU DB;
  - acquisition timeout.
- Không mặc định 20–32 chỉ vì pool 8 “có vẻ nhỏ”.

Ledger schema tối thiểu:

- id/request_id unique;
- player/account;
- currency/item;
- reason/source;
- before/delta/after;
- status;
- reference entity;
- created_at/committed_at;
- checksum hoặc metadata cần thiết cho reconciliation.

---

## 14. Logging, metric và vận hành

Structured log field:

- timestamp;
- level;
- service/module;
- sessionId;
- playerId/accountId khi có;
- command/subcommand;
- correlationId/requestId;
- mapId/zoneId;
- errorCode/exceptionClass;
- durationMs.

Không log:

- password;
- session key;
- token;
- raw SQL dump;
- toàn bộ packet;
- secret config.

Dashboard tối thiểu:

- CCU/session;
- login success/fail/rate-limit;
- thread count;
- tick lag;
- command latency/error;
- sender queue;
- DB pool;
- autosave queue;
- economy ledger mismatch;
- trade/consign conflict;
- JVM heap/GC.

Alert:

- thread count tăng liên tục sau CCU giảm;
- tick p99 vượt một chu kỳ;
- sender queue quá ngưỡng;
- DB acquisition timeout;
- save backlog tăng;
- duplicate key/idempotency conflict bất thường;
- negative balance invariant;
- ledger reconciliation lệch;
- exception rate tăng theo command.

---

## 15. Triển khai production, canary và rollback

Mỗi release:

1. Backup và thử restore trên môi trường staging.
2. Chạy migration additive.
3. Deploy một shard/canary hoặc giới hạn account test.
4. Bật feature flag theo command/module.
5. Theo dõi metric tối thiểu một chu kỳ gameplay quan trọng.
6. Mở dần 5% → 25% → 50% → 100% nếu kiến trúc deploy hỗ trợ.
7. Không xóa schema/legacy path trong cùng release bật route mới.

Rollback phải xác định trước:

- Code rollback có đọc được schema mới.
- Ledger/pending reward replay được.
- Không hoàn tác transaction đã commit bằng cách “trừ bù” không có ledger.
- Command route quay về legacy chỉ khi không làm cùng mutation lần nữa.
- World tick chỉ có đúng một owner ở mọi thời điểm.
- Có runbook xử lý listing/trade ở trạng thái COMMITTING khi crash.

---

## 16. SLO và tiêu chí hoàn thành

Cần chốt CCU mục tiêu trước load test. Bộ mục tiêu khởi đầu đề xuất:

- Không có số dư hoặc item quantity âm.
- Không có duplicate reward/trade/consign purchase trong concurrency suite.
- Thread count trở về gần baseline sau khi toàn bộ test client logout; không tăng theo tổng số lần login.
- Cùng một Player không có hai update đang chạy đồng thời.
- 100% command có policy và metric.
- Zone tick p99 nhỏ hơn chu kỳ tick tại CCU mục tiêu.
- Không có outbound queue vô hạn.
- Save RPO được công bố và crash test đạt RPO đó.
- Restore backup được thử định kỳ.
- Không secret mới trong Git.
- Build từ clean checkout cho artifact checksum lặp lại được trong cùng toolchain.
- Không merge P0/P1 nếu thiếu regression test và rollback note.

Definition of Done cho một module tách:

- Behavior test pass.
- Dependency direction pass.
- Metric và structured log có đủ context.
- Không public mutable state mới.
- Không executor/thread không quản lý lifecycle.
- DB mutation có transaction/idempotency phù hợp.
- Tài liệu wire/schema cập nhật.
- Canary và rollback đã mô tả.

---

## 17. Tổ chức thực hiện

Nhóm tối thiểu khuyến nghị:

- Runtime/architecture owner: session, tick, registry, Controller/Manager/Player migration.
- Economy/persistence owner: wallet, trade, consign, reward, VND, ledger.
- QA/automation: wire, concurrency, migration, load.
- Ops/DB review bán thời gian: backup, migration, dashboard, rollout.

Nhịp làm việc:

- PR nhỏ theo một invariant hoặc một nhóm command.
- Không PR “refactor 50 file” nếu không có test behavior.
- Mỗi tuần cập nhật:
  - P0/P1 còn lại;
  - direct Manager caller;
  - public Player mutation;
  - legacy Controller command;
  - thread/heap/tick baseline;
  - flaky test;
  - migration/reconciliation status.

ADR cần lập:

- ADR-001 Player ownership model.
- ADR-002 Command dispatcher và policy.
- ADR-003 Wallet/ledger/idempotency.
- ADR-004 Registry immutability.
- ADR-005 Player persistence snapshot/version.
- ADR-006 Network backpressure.
- ADR-007 JSON migration.
- ADR-008 NIO/Netty decision sau benchmark.

---

## 18. Thứ tự hành động đề xuất ngay

Trong 48 giờ đầu:

1. Đóng đường claim thành tựu lặp.
2. Chặn LuckyRound count không hợp lệ và overflow.
3. Tạm khóa/disable trade nếu chưa kịp sửa actor state.
4. Tạm serialize mua ký gửi và bắt buộc add bag thành công trước finalize; chuẩn bị DB transaction.
5. Disable nhánh VIP chỉ trừ RAM cho đến khi có durable debit.
6. Bỏ một tick path của Player và dừng executor leak.
7. Làm Session.close idempotent, sửa double IP decrement.
8. Bật metric thread count, queue depth, tick duration, economy mutation.
9. Rotate secret/dữ liệu tài khoản từng commit.
10. Viết test tái hiện cho từng lỗi trên trước khi merge fix.

Trong hai tuần đầu:

- Hoàn tất Gate 0–2.
- Tạo command dispatcher với legacy fallback.
- Tách nhóm economy ra khỏi Controller.
- Tạo compatibility facade cho Manager nhưng cấm dependency mới.
- Tạo PlayerSnapshot và lifecycle state.
- Có CI compile/test/secret scan.

Sau khi production ổn định:

- Tách Manager, Controller, Player theo Gate 3–5.
- Sau đó mới benchmark để quyết định Netty, pool DB và thay đổi kiến trúc hạ tầng.

---

## 19. Kết luận

Kiến trúc cần sửa, nhưng trọng tâm không phải chỉ “chia file lớn thành nhiều file nhỏ”. Vấn đề gốc là mutable state không có owner, nghiệp vụ tiền/thưởng không có idempotency/transaction, protocol validation phân tán và global registry công khai.

Player.java cần tách theo lifecycle, tick, appearance, event state, wallet và persistence, đồng thời vẫn giữ vai trò aggregate root. Manager.java cần tách thành bootstrap, config, loader, registry, leaderboard và tick engine. Controller.java cần trở thành dispatcher mỏng với handler/policy/DTO rõ ràng.

Phương án modular monolith tăng dần là cân bằng tốt nhất giữa tốc độ, rủi ro và khả năng rollback. Chỉ tối ưu framework/network sau khi P0/P1 đã đóng, test và metric đủ để chứng minh bottleneck thật.
