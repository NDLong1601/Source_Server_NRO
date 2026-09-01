# Kế hoạch triển khai hệ thống Điểm Năng Động và tab Admin

> Trạng thái: Giai đoạn 0–6 đã triển khai; Giai đoạn 7 (QA/phát hành) còn lại.  
> Ngày lập kế hoạch: 01/09/2026.  
> Server: `C:\Users\PC\Music\Teamobi2026\SRC`.  
> Client: `C:\Users\PC\Music\PRJ_2Tab_550K`.

## 1. Mục tiêu

Xây dựng hệ thống **Điểm Năng Động** theo ngày và tuần để khuyến khích người chơi tham gia nhiều loại hoạt động, nhưng không tạo vòng lặp cày vô hạn hoặc gây lạm phát vật phẩm.

Phạm vi gồm:

- ghi nhận điểm từ đăng nhập, nhiệm vụ, bang hội, câu cá, PvP, phó bản và boss;
- giới hạn điểm theo nguồn và tổng điểm ngày;
- tự chuyển ngày, chuyển tuần theo múi giờ Việt Nam;
- nhận quà theo các mốc ngày và tuần;
- lưu tiến trình, chống nhận trùng và chống cộng trùng;
- đồng bộ điểm với client cũ qua packet `-97`;
- cung cấp tab **Năng Động** trong `admin_data_menu.hta` để quản lý toàn bộ cấu hình;
- cho phép xem, điều chỉnh hoặc reset dữ liệu người chơi theo quy trình có kiểm soát;
- có bản nháp, kiểm tra, xuất bản, lịch sử phiên bản, audit và rollback cấu hình.

Điểm Năng Động là **điểm tiến trình**, không phải tiền tệ. Nhận quà không trừ điểm.

## 2. Hiện trạng đã kiểm tra

### 2.1 Server đã dành sẵn vị trí nhưng đang để trống

`data_point` hiện có 15 phần tử. Chỉ số `11` đã được ghi chú là Năng Động nhưng chưa nối vào model:

- `src/nro/models/database/PlayerDAO.java`: nhân vật mới được khởi tạo `data_point[11] = 0`;
- `src/nro/models/database/MrFinn.java`: đọc `data_point[11]` nhưng không gán vào trường nào;
- `src/nro/models/database/PlayerDAO.java`: khi lưu luôn ghi số `0` vào vị trí `11`;
- `src/nro/models/services/Service.java`: `sendNangDong(Player)` gửi command `-97` với `writeInt(0)`;
- `src/nro/models/server/Controller.java`: đã gọi `sendNangDong(player)` khi gửi thông tin đăng nhập.

Như vậy protocol và vị trí lưu tương thích đã tồn tại, nhưng server chưa có state, rule cộng điểm, reset hoặc phần thưởng.

### 2.2 Client đã nhận và hiển thị điểm

Cả hai namespace `Game1` và `Game2` đã có sẵn:

- `Assets/Scripts/Assembly-CSharp/Game1/Char.cs` và `Game2/Char.cs`: trường `cNangdong`;
- `Game1/Controller.cs` và `Game2/Controller.cs`: command `-97` đọc một `int`;
- `Game1/Panel.cs` và `Game2/Panel.cs`: hiển thị `Năng động: <giá trị>`;
- `Game1/T1.cs` và `Game2/T1.cs`: chuỗi tiếng Việt `Năng động`.

MVP có thể hoạt động với client hiện tại. Client chỉ cần sửa nếu muốn màn hình mốc quà riêng, hiệu ứng tăng điểm hoặc nút nhận quà trực quan.

### 2.3 Dữ liệu và runtime tại thời điểm khảo sát

- Database local có 3 bản ghi nhân vật được kiểm tra; cả 3 có `data_point` đủ 15 phần tử và chỉ số `11` bằng `0`.
- `20.jar` đang triển khai cũng được kiểm tra và vẫn mang hành vi placeholder: packet `-97` gửi `0`.
- Chưa có bảng cấu hình Năng Động, metadata ngày/tuần, mask nhận quà hoặc lịch sử cộng điểm.

Đây chỉ là ảnh chụp tại ngày lập kế hoạch; trước khi migration vẫn phải chạy lại truy vấn kiểm tra toàn bộ dữ liệu thực tế.

### 2.4 Lỗi liên quan phải sửa trước

Bốn manager đang đọc nhầm `data_point[11]` như sức mạnh. Sức mạnh đúng nằm ở `data_point[1]`:

- `src/nro/models/managers/TopKhiGasHuyDiet.java`;
- `src/nro/models/managers/TopConDuongRanDoc.java`;
- `src/nro/models/managers/TopBanDoKhoBau.java`;
- `src/nro/models/managers/MyClanTopBanDoKhoBau.java`.

Hiện lỗi này chưa lộ rõ vì dữ liệu sức mạnh đọc ra chưa được serialize ở một số nhánh. Khi `data_point[11]` bắt đầu có giá trị thật, lỗi có thể làm dữ liệu top sai. Đây là hạng mục bắt buộc của Giai đoạn 0.

## 3. Nguyên tắc thiết kế

1. **Server là nguồn sự thật.** Client chỉ hiển thị và gửi yêu cầu nhận quà.
2. **Không trừ điểm khi nhận quà.** Trạng thái nhận quà được giữ bằng mask riêng.
3. **Mọi nguồn đều có trần.** Không hoạt động nào được một mình lấp đầy cả ngày.
4. **Ưu tiên đa dạng.** Có thưởng thêm khi hoàn thành đủ nhóm hoạt động khác nhau.
5. **Reset lười và an toàn.** Bất kỳ thao tác đọc/cộng/nhận nào cũng kiểm tra kỳ hiện tại trước.
6. **Không tin thời gian client.** Ngày và tuần lấy từ server với `Asia/Ho_Chi_Minh`.
7. **Idempotent.** Cùng một trận, phó bản, lượt điểm danh hoặc mốc quà không được ghi nhận hai lần.
8. **Cấu hình phát hành theo phiên bản.** Admin không sửa trực tiếp snapshot đang chạy.
9. **Tương thích client cũ.** Command `-97` tiếp tục mang một số nguyên 32-bit.
10. **Có đường tắt khẩn cấp.** Có thể tắt cộng điểm hoặc tắt phát quà mà không làm mất tiến trình.

## 4. Vòng lặp sản phẩm đề xuất

### 4.1 Chu kỳ ngày

- Mỗi ngày người chơi có tối đa `100` điểm.
- Các mốc mặc định: `20`, `40`, `60`, `80`, `100`.
- Người chơi đạt mốc nào thì mở khóa quà mốc đó.
- Quà chưa nhận không tự động bị coi là đã nhận.
- Chính sách hết hạn quà ngày phải được cấu hình rõ. Khuyến nghị MVP: quà ngày phải nhận trước khi sang ngày mới; UI luôn cảnh báo thời gian còn lại.

### 4.2 Chu kỳ tuần

- Tuần bắt đầu lúc `00:00` thứ Hai theo `Asia/Ho_Chi_Minh`.
- `weeklyPoints` cộng đúng số điểm thực nhận trong ngày, không vượt quá tổng điểm ngày.
- Một ngày đạt từ `80` điểm được tính là một `qualifiedDay`.
- Mốc tuần đề xuất: `300` điểm và `500` điểm.
- Mốc cuối tuần đề xuất yêu cầu đồng thời `weeklyPoints >= 500` và `qualifiedDays >= 5`.
- Admin được phép đổi ngưỡng điểm và số ngày đủ chuẩn cho từng mốc.

### 4.3 Thưởng đa dạng

Nguồn điểm được gắn vào các nhóm:

- `LOGIN`: đăng nhập và điểm danh;
- `PVE`: nhiệm vụ, phó bản, boss;
- `PVP`: thách đấu hợp lệ;
- `SOCIAL`: bang hội;
- `LIFE`: câu cá, thu hoạch.

Khi trong ngày người chơi đã có điểm ở ít nhất 4 nhóm, cộng `10` điểm `DIVERSITY_BONUS` một lần. Điểm này vẫn chịu trần tổng ngày.

## 5. Bảng nguồn điểm mặc định

Đây là bộ số khởi đầu để chạy shadow mode. Admin phải có thể chỉnh sau khi đo dữ liệu thật.

| Mã nguồn | Hoạt động | Điểm/lần | Trần/ngày | Nhóm | Chống trùng đề xuất |
| --- | --- | ---: | ---: | --- | --- |
| `DAILY_LOGIN` | Đăng nhập ngày mới | 5 | 5 | LOGIN | một lần theo `dayKey` |
| `DAILY_CHECKIN` | Điểm danh Bò Mộng | 5 | 5 | LOGIN | một lần theo `dayKey` |
| `PEA_HARVEST` | Thu hoạch đậu | 5 | 5 | LIFE | chỉ khi thu hoạch thành công |
| `SIDE_TASK_EASY` | Nhiệm vụ phụ dễ | 5 | gộp 30 | PVE | ID/lần hoàn thành nhiệm vụ |
| `SIDE_TASK_NORMAL` | Nhiệm vụ phụ thường | 8 | gộp 30 | PVE | ID/lần hoàn thành nhiệm vụ |
| `SIDE_TASK_HARD` | Nhiệm vụ phụ khó | 12 | gộp 30 | PVE | ID/lần hoàn thành nhiệm vụ |
| `SIDE_TASK_VERY_HARD` | Nhiệm vụ phụ rất khó | 15 | gộp 30 | PVE | ID/lần hoàn thành nhiệm vụ |
| `SIDE_TASK_SPECIAL` | Nhiệm vụ phụ đặc biệt | 20 | gộp 30 | PVE | ID/lần hoàn thành nhiệm vụ |
| `CLAN_CHECKIN` | Điểm danh bang | 5 | 5 | SOCIAL | một lần theo `dayKey` |
| `FISH_CATCH` | Câu được cá hợp lệ | 2 | 10 | LIFE | chỉ tại `resolveFish` thành công |
| `PVP_WIN` | Thắng thách đấu | 5 | 10 | PVP | trận + đối thủ duy nhất/ngày |
| `DUNGEON_CLEAR` | Hoàn thành phó bản | 10 | 20 | PVE | instance/phó bản duy nhất |
| `BOSS_KILL` | Hạ boss hợp lệ | 5 | 10 | PVE | spawn boss duy nhất |
| `DIVERSITY_BONUS` | Có điểm ở 4 nhóm | 10 | 10 | BONUS | một lần theo `dayKey` |

### 5.1 Quy tắc PvP

- Chỉ tính khi kết quả là thắng thật, không tính hòa, thoát trận hoặc timeout.
- Một đối thủ chỉ cho điểm một lần mỗi ngày.
- Mỗi trận phải có `matchId` hoặc dedupe key ổn định.
- Có thể thêm thời gian trận tối thiểu, ví dụ 30 giây.
- Không nên chặn cứng theo IP vì có thể ảnh hưởng quán net hoặc người cùng nhà; dùng IP/tài khoản liên quan như tín hiệu cảnh báo, không phải điều kiện duy nhất.

### 5.2 Quy tắc phó bản

`finish()` hiện có thể chạy khi hết thời gian hoặc bị đá khỏi phó bản. Không đặt lệnh cộng điểm trực tiếp ở đầu `finish()`.

Chỉ cộng khi cờ hoàn thành thành công của từng phó bản đã được xác nhận, ví dụ:

- Bản Đồ Kho Báu;
- Khí Gas Hủy Diệt;
- Con Đường Rắn Độc.

Dedupe key nên gồm loại phó bản và ID instance/lượt mở.

### 5.3 Quy tắc boss

MVP chỉ có thể ghi nhận người kết liễu tại `Boss.die(Player plKill)`. Cách này đơn giản nhưng thiên vị last-hit.

Lộ trình hợp lý:

- MVP: last-hit, giới hạn 2 boss/ngày;
- bản nâng cao: ghi damage contribution theo spawn;
- chỉ thưởng người đạt ngưỡng đóng góp, còn ở map và không AFK;
- xóa ledger khi boss chết hoặc despawn để tránh rò bộ nhớ.

## 6. Phần thưởng đề xuất

### 6.1 Nguyên tắc cân bằng

- Quà mốc là **gói cố định**, không phải rút ngẫu nhiên theo trọng số ở MVP.
- Quà quan trọng nên là vật phẩm khóa hoặc không giao dịch để giảm lạm phát.
- Mốc thấp ưu tiên vàng, ngọc khóa, vật phẩm hồi phục hoặc nguyên liệu thông dụng.
- Mốc cao ưu tiên nguyên liệu nâng cấp, vé hoạt động, hộp sự kiện hoặc vật phẩm sưu tầm.
- Không đưa vật phẩm end-game vĩnh viễn vào mốc ngày trước khi có dữ liệu shadow mode.
- Phần thưởng tuần phải tốt hơn tổng quà ngày nhưng không bắt buộc người chơi online đủ 7 ngày.

### 6.2 Khung mốc mặc định

| Chu kỳ | Mốc | Vai trò phần thưởng |
| --- | ---: | --- |
| Ngày | 20 | quà khởi động nhỏ |
| Ngày | 40 | tiền tệ khóa hoặc consumable |
| Ngày | 60 | nguyên liệu nâng cấp phổ thông |
| Ngày | 80 | hộp/vé hoạt động |
| Ngày | 100 | quà ngày chính, ưu tiên khóa |
| Tuần | 300 | gói duy trì giữa tuần |
| Tuần | 500 + 5 ngày chuẩn | quà tuần chính |

ID vật phẩm và số lượng không nên hard-code trong Java. Tất cả được cấu hình tại tab Admin.

### 6.3 Thuộc tính một phần quà

Mỗi reward hỗ trợ:

- `kind`: `ITEM`, `GOLD`, `GEM` hoặc `RUBY`/ngọc khóa theo model tiền tệ server;
- `itemId` khi `kind = ITEM`;
- `quantityMin`, `quantityMax`;
- `gender`: Nam, Nữ, Namek, mọi giới tính hoặc bỏ lọc;
- `initBaseOptions`: khởi tạo chỉ số gốc cho trang bị;
- `useDefaultOptions`: dùng option mặc định của item;
- danh sách option `{ id, paramMin, paramMax }`;
- hạn sử dụng vĩnh viễn hoặc số ngày min–max;
- `bindMode`: mặc định khóa, hoặc theo hành vi gốc của item;
- `deliveryMode`: MVP chỉ dùng `BAG_ONLY`.

Chỉ đánh dấu mốc đã nhận **sau khi toàn bộ gói quà được tạo và thêm thành công**. Nếu hành trang thiếu chỗ, không trao một phần và không set claimed mask.

`MAIL_FALLBACK` chỉ nên triển khai khi server có cơ chế thư ổn định và idempotent; không dùng như giả định trong MVP.

## 7. Mô hình dữ liệu người chơi

### 7.1 Trường tương thích

`data_point[11]` tiếp tục lưu `dailyPoints` để:

- giữ tương thích với cấu trúc cũ;
- gửi nhanh qua packet `-97`;
- cho phép các tool cũ đọc giá trị hiện tại.

Server cần thêm `player.activityState` và ghi giá trị thật trở lại chỉ số `11`, thay vì số `0`.

### 7.2 Metadata mới

Thêm cột `player.data_activity LONGTEXT NULL` chứa JSON có version:

```json
{
  "schemaVersion": 1,
  "dayKey": "2026-09-01",
  "dailyPoints": 55,
  "dailyClaimMask": 3,
  "dailyCounters": {
    "DAILY_LOGIN": 5,
    "SIDE_TASK": 20,
    "FISH_CATCH": 10,
    "DIVERSITY_BONUS": 10
  },
  "dailyCategories": ["LOGIN", "PVE", "LIFE", "SOCIAL"],
  "dailyUnique": {
    "PVP_WIN": ["player:102"],
    "DUNGEON_CLEAR": ["BDKB:8872"]
  },
  "weekKey": "2026-W36",
  "weeklyPoints": 355,
  "qualifiedDays": 4,
  "qualifiedDayKeys": ["2026-08-28", "2026-08-29", "2026-08-31", "2026-09-01"],
  "weeklyClaimMask": 1,
  "lastAwardAt": 1788249600000
}
```

Lưu ý:

- `dailyPoints` trong JSON và `data_point[11]` phải luôn được đồng bộ;
- khi lệch, `ActivityState.dailyPoints` là nguồn chính và lần save kế tiếp sửa mirror;
- parser phải chấp nhận cột `NULL`, JSON trống, thiếu field hoặc schema cũ;
- tập `dailyUnique` phải có giới hạn kích thước;
- không lưu object Java tùy ý vào JSON.

### 7.3 Chuyển ngày

`ensureCurrentPeriod(player, now)` thực hiện theo thứ tự:

1. tính `currentDayKey` và `currentWeekKey` bằng `ZoneId.of("Asia/Ho_Chi_Minh")`;
2. nếu tuần đã đổi, reset dữ liệu tuần;
3. nếu ngày đã đổi:
   - chốt `qualifiedDay` của ngày cũ nếu đạt ngưỡng;
   - reset điểm, mask, counter, category và unique ngày;
   - đổi `dayKey`;
4. đồng bộ `data_point[11]`;
5. nếu người chơi online, gửi lại packet `-97`.

Hàm này phải được gọi khi load nhân vật, trước cộng điểm, trước xem tiến trình và trước nhận quà. Ngoài reset lười, một tick nhẹ cần kiểm tra người chơi online qua nửa đêm để UI cập nhật ngay.

## 8. Cấu hình runtime và phiên bản

### 8.1 Nguồn cấu hình

Khuyến nghị chia thành hai lớp:

- `activity.properties`: chỉ chứa kill switch và tham số hạ tầng, ví dụ tần suất poll; dùng được ngay cả khi database config lỗi;
- database: nguồn sự thật của rule gameplay, nguồn điểm, mốc và quà.

Ví dụ `activity.properties`:

```properties
activity.enabled=false
activity.shadow.mode=true
activity.rewards.enabled=false
activity.emergency.disable=false
activity.config.poll.seconds=5
activity.config.fail.closed=true
```

Không để điểm, trần hoặc item reward trong properties nếu Admin cần chỉnh thường xuyên.

### 8.2 Cấu hình version hóa

Tạo bảng snapshot bất biến và một con trỏ runtime:

```sql
CREATE TABLE activity_config_version (
  id BIGINT NOT NULL AUTO_INCREMENT,
  version_no BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL,
  schema_version INT NOT NULL,
  config_json LONGTEXT NOT NULL,
  note VARCHAR(500) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  published_at TIMESTAMP NULL DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_activity_config_version (version_no)
);

CREATE TABLE activity_runtime_config (
  id TINYINT NOT NULL,
  published_config_id BIGINT NULL,
  revision BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
```

Quy trình:

1. **Lưu bản nháp** tạo/cập nhật một version `DRAFT`.
2. **Kiểm tra** chạy toàn bộ validation mà không đổi runtime.
3. **Xuất bản** chuyển bản nháp thành `PUBLISHED`, archive bản cũ và cập nhật con trỏ trong một transaction.
4. Transaction tăng `activity_runtime_config.revision`.
5. Java poll revision mỗi 5 giây, parse snapshot mới và swap bằng `AtomicReference`.
6. Nếu parse/validate runtime thất bại, server giữ snapshot tốt gần nhất và phát cảnh báo.
7. **Rollback** chỉ đổi con trỏ về version cũ và tăng revision; không chỉnh sửa snapshot cũ.

Thiết kế này tránh trạng thái nửa cũ nửa mới khi admin đang sửa nhiều mốc.

### 8.3 JSON cấu hình mẫu

```json
{
  "schemaVersion": 1,
  "global": {
    "enabled": false,
    "shadowMode": true,
    "rewardEnabled": false,
    "timezone": "Asia/Ho_Chi_Minh",
    "dailyResetHour": 0,
    "weeklyResetDay": "MONDAY",
    "dailyMax": 100,
    "qualifiedDailyPoints": 80,
    "diversityCategoryCount": 4,
    "diversityBonus": 10
  },
  "sources": [
    {
      "key": "FISH_CATCH",
      "label": "Câu được cá",
      "enabled": true,
      "category": "LIFE",
      "points": 2,
      "dailyCap": 10,
      "dedupePolicy": "NONE"
    }
  ],
  "dailyTiers": [
    {
      "id": "DAILY_20",
      "claimBit": 0,
      "enabled": true,
      "threshold": 20,
      "minQualifiedDays": 0,
      "rewards": [
        {
          "kind": "ITEM",
          "itemId": 457,
          "quantityMin": 1,
          "quantityMax": 1,
          "gender": 3,
          "bindMode": "BOUND",
          "initBaseOptions": false,
          "useDefaultOptions": true,
          "options": [],
          "expiry": [{"mode": "days", "weight": 1, "daysMin": 7, "daysMax": 7}]
        }
      ]
    }
  ],
  "weeklyTiers": []
}
```

ID `457` chỉ là ví dụ định dạng, không phải đề xuất cân bằng chính thức.

## 9. Kiến trúc Java đề xuất

Tạo package `src/nro/models/activity/`:

| File | Trách nhiệm |
| --- | --- |
| `ActivityType.java` | enum/mã nguồn điểm ổn định |
| `ActivityCategory.java` | nhóm LOGIN/PVE/PVP/SOCIAL/LIFE/BONUS |
| `ActivityState.java` | state ngày/tuần của một player |
| `ActivityConfig.java` | model snapshot config bất biến |
| `ActivityConfigService.java` | load, validate, poll revision, atomic swap |
| `ActivityService.java` | reset kỳ, cộng điểm, cap, dedupe, đồng bộ client |
| `ActivityRewardService.java` | kiểm tra mốc, tạo bundle, nhận quà idempotent |
| `ActivityRepository.java` | parse/save `data_activity`, truy vấn config nếu cần |
| `ActivityResult.java` | kết quả cộng/nhận với reason code rõ ràng |

### 9.1 API cốt lõi

```java
ActivityResult award(Player player, ActivityType type);

ActivityResult award(Player player, ActivityType type, int occurrences);

ActivityResult awardUnique(
    Player player,
    ActivityType type,
    String dedupeKey
);

void ensureCurrentPeriod(Player player);

ActivityProgress getProgress(Player player);

ClaimResult claimDaily(Player player, String tierId);

ClaimResult claimWeekly(Player player, String tierId);
```

### 9.2 Trình tự cộng điểm

```text
hook gameplay
  -> ensureCurrentPeriod
  -> kiểm tra feature flag/shadow mode
  -> lấy source config từ snapshot hiện hành
  -> kiểm tra source enabled + điều kiện + dedupe
  -> tính remainingSourceCap
  -> tính remainingDailyCap
  -> awarded = min(requested, source cap, daily cap)
  -> cập nhật counter/category/weeklyPoints
  -> xét diversity bonus một lần
  -> đánh dấu state dirty
  -> gửi packet -97 nếu điểm thay đổi
  -> ghi log/metric với reason code
```

Toàn bộ thao tác trên state một player phải nằm trong cùng lock hoặc cơ chế tuần tự hiện có của player để tránh hai event đồng thời vượt cap.

### 9.3 Reason code cần có

- `AWARDED`;
- `FEATURE_DISABLED`;
- `SHADOW_ONLY`;
- `SOURCE_DISABLED`;
- `SOURCE_CAP_REACHED`;
- `DAILY_CAP_REACHED`;
- `DUPLICATE_EVENT`;
- `INVALID_PLAYER`;
- `INVALID_DEDUPE_KEY`;
- `CONFIG_MISSING`;
- `NOT_ELIGIBLE`.

Reason code giúp log và tab Admin giải thích vì sao người chơi không được cộng điểm.

## 10. Điểm tích hợp gameplay

### 10.1 Đăng nhập và reset

- `src/nro/models/services/PlayerService.java`, `dailyLogin(Player)`:
  - gọi `ensureCurrentPeriod`;
  - cộng `DAILY_LOGIN` đúng một lần/ngày.
- `src/nro/models/database/MrFinn.java`:
  - parse `data_activity` sau khi tạo player model;
  - gọi migration mặc định nếu trống;
  - không bỏ qua `data_point[11]`.
- `src/nro/models/services/Service.java`:
  - `sendNangDong` gửi `player.activityState.dailyPoints`.

### 10.2 Điểm danh Bò Mộng

Gắn `DAILY_CHECKIN` vào nhánh xác nhận điểm danh thành công trong `src/nro/models/npc_list/BoMong.java`, không gắn vào lúc mở menu hoặc nhánh nhận nhiệm vụ phụ.

### 10.3 Thu hoạch đậu

Gắn `PEA_HARVEST` trong `src/nro/models/npc/MagicTree.java::harvestPea()` sau khi xác nhận có đậu và cập nhật thu hoạch thành công.

### 10.4 Nhiệm vụ phụ

Gắn tại `src/nro/models/services/TaskService.java::paySideTask(Player)` sau khi `sideTask.isDone()` và trước/sau trao thưởng theo một thứ tự nhất quán. Map độ khó nhiệm vụ sang mã nguồn tương ứng, nhưng dùng chung trần nhóm `SIDE_TASK = 30`.

### 10.5 Bang hội

Gắn `CLAN_CHECKIN` tại `src/nro/models/npc_list/GiuMaDauBo.java::checkInClan(Player)` sau khi check-in được ghi nhận thành công.

### 10.6 Câu cá

Gắn `FISH_CATCH` tại `src/nro/models/fishing/FishingService.java::resolveFish(...)` sau khi vật phẩm cá được tạo/thêm thành công. Không cộng khi cá thoát, hủy phiên hoặc hành trang thất bại.

### 10.7 PvP

Gắn `PVP_WIN` tại `src/nro/models/matches/ThachDau.java::sendResult(...)` sau khi xác định `plWin`, nhưng chỉ với `TYPE_LOSE_PVP` hợp lệ. Dedupe key gồm `matchId`; unique rule theo `plLose.id` trong ngày.

### 10.8 Phó bản

Các file cần rà soát:

- `src/nro/models/map/phoban/BanDoKhoBau.java`;
- `src/nro/models/map/phoban/DestronGas.java`;
- `src/nro/models/map/phoban/SnakeWay.java`.

Mỗi phó bản nên phát một domain event `DUNGEON_COMPLETED` tại đúng nhánh thành công. `finish()` chỉ dọn tài nguyên, không tự đồng nghĩa với hoàn thành.

### 10.9 Boss

Gắn MVP tại `src/nro/models/boss/Boss.java::die(Player plKill)` sau khi xác nhận `plKill` là người chơi hợp lệ. Dedupe key phải dùng ID spawn/runtime của boss, không chỉ template boss.

## 11. Logic nhận quà

### 11.1 Điều kiện

Một mốc ngày được nhận khi:

- feature và reward đang bật;
- mốc tồn tại trong snapshot config đã publish;
- `dailyPoints >= threshold`;
- bit tương ứng trong `dailyClaimMask` chưa bật.

Mốc tuần kiểm tra thêm `weeklyPoints` và `minQualifiedDays`.

### 11.2 Giao dịch logic

1. Lock activity state của player.
2. Chạy `ensureCurrentPeriod`.
3. Đọc lại tier từ snapshot đang chạy.
4. Kiểm tra eligibility và claimed mask.
5. Tính chính xác số ô hành trang cần dùng.
6. Tạo toàn bộ item/reward trong bộ nhớ.
7. Thêm toàn bộ reward; nếu một bước thất bại, rollback phần đã thêm hoặc không bắt đầu thêm.
8. Bật claimed bit.
9. Lưu player ngay sau claim.
10. Gửi cập nhật hành trang, tiền tệ, điểm và thông báo.
11. Ghi audit nhận quà với config version và tier ID.

Nếu kiến trúc inventory hiện tại không hỗ trợ rollback nhiều item, MVP phải kiểm tra đủ ô và validate toàn bộ item trước khi thêm, sau đó xử lý trong một lock không có nhánh thất bại dự kiến.

### 11.3 Không dùng vị trí mảng làm danh tính lâu dài

Không gắn bit theo vị trí mốc vì admin có thể sắp xếp lại. Mỗi tier có `id` bất biến như `DAILY_20` và một `claimBit` được cấp rõ trong config. `claimBit` không được tính lại từ thứ tự danh sách, không được đổi sau lần publish đầu và không được tái sử dụng cho tier khác. Muốn thay ý nghĩa một tier thì tạo tier ID/claim bit mới; tier cũ chỉ được disable.

## 12. Protocol và client

### 12.1 Tương thích bắt buộc

Giữ command `-97`:

```java
msg.writer().writeInt(player.activityState.getDailyPoints());
```

Gửi lại khi:

- đăng nhập xong;
- cộng điểm thành công;
- chuyển ngày;
- admin điều chỉnh điểm của nhân vật online qua kênh server hợp lệ.

### 12.2 MVP không sửa protocol

MVP có thể dùng NPC/menu server để:

- xem điểm ngày, tuần và số ngày đủ chuẩn;
- xem trạng thái từng mốc;
- nhận quà theo mốc.

Client cũ vẫn hiển thị tổng điểm trên panel hiện có.

### 12.3 UI client nâng cao

Giai đoạn sau có thể thêm command riêng để trả:

- danh sách source và tiến độ cap;
- mốc ngày/tuần;
- trạng thái khóa, có thể nhận, đã nhận;
- thời gian còn lại đến reset;
- config display version.

Phải sửa song song cả `Game1` và `Game2`, đồng thời có giới hạn payload rõ ràng. Không nhồi JSON tùy ý vào packet nếu client dùng độ dài 16-bit.

## 13. Thiết kế tab Admin Năng Động

### 13.1 Vị trí và cấu trúc file

Thêm nút điều hướng **Năng Động** vào `admin_data_menu.hta` và đăng ký tab:

```javascript
RegisterTab({
  id: "activity",
  view: "activity.html",
  panelId: "panelActivity",
  navId: "navActivity",
  title: "Cấu hình Năng Động",
  subtitle: "Nguồn điểm, mốc quà, tiến trình người chơi và phát hành cấu hình",
  onOpen: function () { LoadActivityTab(); },
  onRefresh: function () { RefreshActivityTab(); }
});
```

Các file dự kiến:

- `admin_data_menu/views/activity.html`;
- `admin_data_menu/js/tabs/activity.js`;
- `admin_data_menu/js/components/reward-editor.js` để tách trình chỉnh reward dùng chung;
- `admin_data_menu/css/activity.css` chỉ khi class chung chưa đủ;
- `tools/admin_data.ps1` cho backend;
- `sql/activity_config.sql` cho DDL chính thức.

Script tab phải được load trước `admin_data_menu/js/core/app.js`. View có đúng một root:

```html
<div id="panelActivity" class="panel">...</div>
```

### 13.2 Sơ đồ màn hình

```text
Năng Động
├─ Tổng quan
├─ Nguồn điểm
├─ Mốc quà ngày
├─ Mốc quà tuần
├─ Người chơi
└─ Phát hành & lịch sử
```

Thanh trạng thái cố định phía trên hiển thị:

- feature bật/tắt;
- shadow mode;
- phát quà bật/tắt;
- version đã publish;
- revision database;
- revision server đã nạp;
- thời điểm reload gần nhất;
- cảnh báo khi draft chưa publish hoặc server đang lệch revision.

### 13.3 Mục Tổng quan

Trường cấu hình:

- bật hệ thống;
- shadow mode;
- bật phát quà;
- múi giờ;
- giờ reset ngày;
- ngày reset tuần;
- trần điểm ngày;
- ngưỡng ngày đủ chuẩn;
- số nhóm cần cho diversity bonus;
- điểm diversity bonus;
- chính sách quà chưa nhận khi reset;
- mức log chi tiết.

Nút thao tác:

- `Lưu bản nháp`;
- `Kiểm tra cấu hình`;
- `So sánh với bản đang chạy`;
- `Xuất bản`;
- `Tắt khẩn cấp`;
- `Nạp lại trạng thái`.

`Tắt khẩn cấp` phải tắt cộng mới nhưng không xóa điểm hoặc claimed mask.

### 13.4 Mục Nguồn điểm

Danh sách bên trái, editor bên phải theo mẫu các tab config hiện có.

Mỗi nguồn có:

- mã nguồn bất biến;
- tên hiển thị;
- mô tả;
- bật/tắt;
- nhóm hoạt động;
- điểm mỗi lần;
- trần riêng theo ngày;
- trần nhóm nếu có;
- dedupe policy;
- số đối tượng unique tối đa;
- điều kiện tối thiểu như thời gian PvP;
- cờ ghi log chi tiết.

UI hiển thị ngay số lần tối đa cần làm, ví dụ `2 điểm/lần, cap 10 = tối đa 5 lần có điểm`.

Mã nguồn đã publish không cho đổi tên vì Java hook phụ thuộc vào mã này. Chỉ label và rule được chỉnh.

### 13.5 Mục Mốc quà ngày và tuần

Tái sử dụng trải nghiệm của tab **Hộp quà / Rương**:

- catalog item có tìm kiếm;
- icon preview qua `ItemImageHtml`;
- catalog option qua `LoadOptions`;
- số lượng min–max;
- giới tính;
- option gốc/default;
- option riêng với param min–max;
- hạn dùng vĩnh viễn hoặc theo ngày.

Không copy nguyên các hàm `GiftBox...` sang tab mới. Tách phần dùng chung thành `reward-editor.js`, truyền namespace/state/callback để tránh trùng global và lệch validation giữa hai tab.

Mỗi tier có:

- tier ID bất biến;
- `claimBit` bất biến, được cấp tự động và không phụ thuộc thứ tự hiển thị;
- loại `DAILY` hoặc `WEEKLY`;
- ngưỡng điểm;
- số ngày đủ chuẩn tối thiểu;
- bật/tắt;
- danh sách reward;
- chế độ khóa vật phẩm;
- ghi chú vận hành.

Nút `Xem trước` phải hiển thị chính xác bundle sẽ được trao cho từng giới tính và số ô túi cần thiết.

### 13.6 Mục Người chơi

Tìm theo ID hoặc tên và hiển thị:

- trạng thái online từ `admin_player_online`;
- `dayKey`, `dailyPoints`, từng counter, category và unique;
- mốc ngày đã nhận/chưa nhận;
- `weekKey`, `weeklyPoints`, `qualifiedDays`;
- mốc tuần đã nhận/chưa nhận;
- config version gần nhất dùng khi cộng/claim;
- thời điểm award gần nhất;
- JSON lỗi hoặc cảnh báo mirror lệch.

Thao tác hỗ trợ:

- cộng/trừ điểm có lý do;
- đặt lại điểm ngày;
- đặt lại counter một nguồn;
- mở lại/đánh dấu một mốc quà;
- reset tuần;
- sửa JSON nâng cao chỉ dành cho tình huống cứu dữ liệu.

Ràng buộc:

- nhân vật `ONLINE` hoặc `ONLINE?` chỉ được xem;
- chỉnh offline phải kèm `expectedVersion` SHA-256 như tab Player hiện tại;
- mọi thay đổi yêu cầu nhập lý do;
- thao tác reset/claimed mask cần xác nhận hai bước;
- không cho điểm âm hoặc vượt trần nếu không chọn rõ `supportOverride`;
- audit phải lưu ảnh trước thay đổi và kết quả sau thay đổi.

### 13.7 Mục Phát hành và lịch sử

Hiển thị các version:

- version number;
- trạng thái draft/published/archived;
- người vận hành hoặc nguồn thao tác nếu có;
- ghi chú;
- thời gian tạo/publish;
- hash config;
- kết quả validation;
- số server đã áp dụng revision.

Cho phép:

- xem diff với version hiện tại;
- clone một version thành draft mới;
- publish draft;
- rollback về version tốt trước;
- xem audit liên quan.

Rollback không xóa version mới; chỉ đổi con trỏ runtime và tạo audit mới.

## 14. API HTA → PowerShell

HTA hiện gọi `tools/admin_data.ps1` qua `RunAdmin(action, params)`, encode tham số rồi nhận TSV. Giữ đúng mô hình này.

### 14.1 Action đọc

| Action | Mục đích |
| --- | --- |
| `getactivityoverview` | trạng thái runtime, revision, config summary |
| `getactivitydraft` | lấy draft đang sửa |
| `listactivityversions` | lịch sử version |
| `getactivityversion` | lấy một config version |
| `validateactivityconfig` | validate payload nhưng không lưu/publish |
| `listactivityplayers` | tìm player và trạng thái ngắn |
| `getactivityplayer` | chi tiết state một player |
| `listactivitylogs` | log cộng/claim gần đây nếu bật ledger |

### 14.2 Action ghi

| Action | Mục đích |
| --- | --- |
| `saveactivitydraft` | lưu snapshot draft |
| `publishactivityconfig` | publish transaction và tăng revision |
| `rollbackactivityconfig` | trỏ về version cũ và tăng revision |
| `disableactivityemergency` | kill switch có audit |
| `adjustactivityplayer` | điều chỉnh có lý do và expectedVersion |
| `resetactivityplayer` | reset phạm vi ngày/tuần/source |
| `setactivityclaim` | sửa trạng thái một tier theo hỗ trợ |

Các action ghi được allow-list trong `$mutationActions` và `Get-AuditSummary` ở `tools/admin_data.ps1`. Năng Động dùng audit riêng `activity_admin_audit`, thay vì cơ chế `Get-AuditContext`/undo chung, để không có thao tác hoàn tác mù trên state player.

### 14.3 Payload

Tận dụng `PayloadJson` hiện có để tránh thêm quá nhiều tham số PowerShell. Payload ghi nên chứa:

```json
{
  "expectedRevision": 12,
  "reason": "Điều chỉnh theo ticket CS-1024",
  "config": {}
}
```

Backend không được tin validation JavaScript. PowerShell phải parse và validate lại trước SQL.

## 15. Validation bắt buộc

### 15.1 Global

- timezone thuộc allow-list, mặc định `Asia/Ho_Chi_Minh`;
- `dailyMax` từ 1 đến 1000;
- `qualifiedDailyPoints` từ 1 đến `dailyMax`;
- diversity category count không vượt số category đang hỗ trợ;
- bonus không âm và không vượt daily max;
- không publish khi không có nguồn điểm bật nhưng reward đang bật.

### 15.2 Nguồn điểm

- key khớp enum/hook server;
- key không trùng;
- point và cap là số nguyên không âm;
- source bật với point > 0 phải có cap >= point;
- cap nguồn và cap nhóm không vượt daily max nếu không có lý do rõ;
- nguồn yêu cầu unique phải khai báo dedupe policy;
- không cho xóa key đã có trong version published, chỉ disable.

### 15.3 Tier

- tier ID duy nhất và hợp lệ;
- `claimBit` là số nguyên duy nhất trong từng chu kỳ, nằm trong giới hạn kiểu mask và không được đổi/tái sử dụng sau publish;
- threshold dương, không trùng và tăng dần trong mỗi chu kỳ;
- threshold ngày không vượt daily max;
- threshold tuần không vượt daily max × số ngày;
- tier bật phải có ít nhất một reward;
- `minQualifiedDays` từ 0 đến 7;
- tier ID đã publish không được tái sử dụng cho ý nghĩa khác.

### 15.4 Reward

- item tồn tại trong `item_template`;
- số lượng min/max từ 1 đến giới hạn an toàn và min <= max;
- option tồn tại, không trùng, param nằm trong `short` và min <= max;
- HSD chỉ nhận `permanent` hoặc `days`;
- số ngày từ 1 đến 3650;
- item trang bị phải tính đủ slot;
- tiền tệ không được mang option/item fields;
- tổng JSON có giới hạn kích thước;
- không cho reward rỗng hoặc kind không biết.

Có thể tái sử dụng và tổng quát hóa validation từ `Convert-GiftBoxPayload` trong `tools/admin_data.ps1`.

### 15.5 Publish

- payload validate ở PowerShell;
- Java validator chạy cùng schema/rule; nên có test fixture chung;
- `expectedRevision` phải khớp để tránh ghi đè cấu hình vừa được người khác publish;
- publish và đổi con trỏ nằm trong một transaction;
- config JSON lưu dạng normalized để hash ổn định.

## 16. Audit, bảo mật và tính an toàn

Tab Admin là HTA local nhưng vẫn phải coi input là không tin cậy.

- Chỉ chấp nhận action nằm trong switch allow-list.
- Dùng parser số và `SqlString` hiện có; không ghép trực tiếp JSON chưa chuẩn hóa.
- Không trả mật khẩu database hoặc nội dung `Config.properties` ra UI.
- Snapshot các bảng config trước publish/rollback.
- Snapshot `player.data_point` và `player.data_activity` trước chỉnh người chơi.
- Audit ghi action, summary, status, reason, version, player ID và result.
- Lịch sử không được cho phép undo claim đã thực sự phát item bằng cách chỉ lật mask; trường hợp đó cần quy trình bồi hoàn riêng.
- Tắt/xóa tier là soft-disable, không xóa lịch sử.
- Emergency disable không reset dữ liệu.

## 17. Log và quan sát vận hành

### 17.1 Log tối thiểu

Mỗi log cộng điểm gồm:

- player ID;
- source key;
- requested/awarded;
- daily total trước/sau;
- config version;
- dedupe key đã băm hoặc rút gọn;
- reason code;
- thời gian server.

Không bắt buộc lưu vĩnh viễn từng lần câu cá. Có thể:

- log chi tiết trong shadow mode;
- sau ổn định chỉ log claim, lỗi, duplicate đáng ngờ và aggregate theo nguồn;
- giữ log chi tiết 7–14 ngày rồi dọn.

### 17.2 Chỉ số cần xem

- số player có điểm mỗi ngày;
- phân bố điểm P50/P75/P90/P99;
- tỷ lệ đạt 20/40/60/80/100;
- điểm trung bình theo từng nguồn;
- tỷ lệ chạm cap từng nguồn;
- tỷ lệ nhận quà và lỗi túi đầy;
- số duplicate bị chặn;
- số lần admin điều chỉnh;
- revision config database so với revision server.

### 17.3 Cảnh báo

- config publish nhưng server không reload sau 30 giây;
- một nguồn tăng đột biến so với baseline;
- nhiều PvP unique giữa cùng cụm tài khoản;
- tỷ lệ 100 điểm quá cao hoặc quá thấp;
- claim lỗi liên tục cho cùng tier/item;
- JSON player lỗi parse;
- `data_point[11]` lệch `data_activity.dailyPoints`.

## 18. Danh sách file dự kiến thay đổi

### 18.1 Server Java

Tạo mới:

- `src/nro/models/activity/ActivityType.java`;
- `src/nro/models/activity/ActivityCategory.java`;
- `src/nro/models/activity/ActivityState.java`;
- `src/nro/models/activity/ActivityConfig.java`;
- `src/nro/models/activity/ActivityConfigService.java`;
- `src/nro/models/activity/ActivityService.java`;
- `src/nro/models/activity/ActivityRewardService.java`;
- `src/nro/models/activity/ActivityRepository.java`;
- `src/nro/models/activity/ActivityResult.java`.

Sửa:

- `src/nro/models/player/Player.java`;
- `src/nro/models/database/PlayerDAO.java`;
- `src/nro/models/database/MrFinn.java`;
- `src/nro/models/services/Service.java`;
- `src/nro/models/server/Controller.java` nếu cần thêm menu/protocol;
- các hook gameplay liệt kê tại Mục 10;
- bốn manager top liệt kê tại Mục 2.4;
- `src/nro/models/server/Manager.java` để load config service lúc khởi động.

### 18.2 Database và cấu hình

- `sql/activity_config.sql`;
- migration thêm `player.data_activity`;
- seed version cấu hình mặc định ở trạng thái disabled + shadow;
- `activity.properties`.

Không chỉnh hàng loạt `data_point` bằng thao tác mù. Migration cần kiểm tra JSON hợp lệ và backup trước.

### 18.3 Admin

- `admin_data_menu.hta`;
- `admin_data_menu/views/activity.html`;
- `admin_data_menu/js/tabs/activity.js`;
- `admin_data_menu/js/components/reward-editor.js`;
- `admin_data_menu/css/activity.css` nếu cần;
- `tools/admin_data.ps1`;
- `admin_data_menu/help/HUONG_DAN_HE_THONG_NANG_DONG.md`.

### 18.4 Client nâng cao, không bắt buộc cho MVP

- `Assets/Scripts/Assembly-CSharp/Game1/...`;
- `Assets/Scripts/Assembly-CSharp/Game2/...`.

Mọi thay đổi phải thực hiện đối xứng cho hai namespace.

## 19. Các giai đoạn triển khai tổng thể

### Giai đoạn 0 — Khóa đặc tả và làm sạch nền, 0.5–1 ngày

Mục tiêu:

- backup database và ghi hash `20.jar`;
- kiểm tra toàn bộ `data_point` đủ 15 phần tử;
- sửa bốn manager đọc nhầm index `11` thành `1`;
- chốt timezone, reset policy, bộ source key và tier ID;
- tạo feature flag mặc định `disabled`, `shadowMode = true`, `rewardEnabled = false`.

Chạy kiểm tra trước migration (có thể thêm `-CreateBackup` để tạo snapshot local):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\check_activity_phase0.ps1 -FailOnDataIssue
```

Điều kiện qua cổng:

- top vẫn hoạt động đúng;
- không có migration dữ liệu hỏng;
- source key/tier ID được duyệt và đóng băng.

### Giai đoạn 1 — Core state, persistence và config, 1.5–2 ngày

Mục tiêu:

- model `ActivityState`;
- parse/save `data_activity`;
- mirror `data_point[11]`;
- reset ngày/tuần;
- cap, counter, unique, category;
- config snapshot + revision poll;
- packet `-97` gửi điểm thật.

Kiểm thử:

- JSON thiếu/hỏng;
- login trước và sau 00:00;
- đổi tuần;
- cap đồng thời;
- restart/relog không mất dữ liệu.

### Giai đoạn 2 — Hook gameplay và shadow mode, 2 ngày

Mục tiêu:

- nối từng hook theo Mục 10;
- bật shadow mode: tính/log điểm nhưng chưa mở quà;
- ghi metric theo nguồn;
- xác minh phó bản chỉ cộng khi hoàn thành thật;
- PvP/boss có dedupe.

Chạy shadow ít nhất 3–7 ngày nếu có lượng người chơi thật trước khi chốt cân bằng.

### Giai đoạn 3 — Reward engine và menu nhận quà, 1–1.5 ngày

Mục tiêu:

- daily/weekly tiers;
- eligibility + claimed mask;
- reward bundle và kiểm tra ô túi;
- menu NPC/server để xem và nhận;
- log claim idempotent.

Không bật reward production khi chưa qua test túi đầy, reconnect và double-click claim.

#### Trạng thái triển khai hiện tại

Đã triển khai server-side với revision cấu hình `3`:

- mốc ngày `DAILY_20` đến `DAILY_100` và mốc tuần `WEEKLY_300`, `WEEKLY_500` có `claimBit` ổn định;
- engine hỗ trợ `ITEM`, `GOLD`, `GEM`, `RUBY`, option, bind, HSD và kiểm tra giới tính; cấu hình Phase 3 chỉ seed vàng để không tự chọn ID item/cân bằng chưa duyệt;
- NPC **Bò Mộng** (map 47/84) có mục **Năng động → Nhận quà** để xem mốc ngày/tuần và gửi yêu cầu nhận;
- yêu cầu claim lấy lock state, kiểm tra threshold/mask/sức chứa, reserve khóa unique tại `activity_claim_audit`, giao trọn bundle, lưu player ngay rồi đánh dấu audit `SUCCESS`;
- request lặp hoặc reconnect sau claim bị chặn bởi cả claimed mask và unique audit key. Bản ghi `PENDING` sau sự cố được giữ để ưu tiên không phát trùng và cần đối soát hỗ trợ;
- migration/kiểm tra: `tools/apply_activity_phase3.ps1 -Apply` và `tools/check_activity_phase3.ps1 -FailOnIssue`.

Runtime đang triển khai an toàn: `enabled=true`, `shadowMode=true`, `rewardEnabled=false`. Vì vậy người chơi xem được mốc nhưng chưa thể nhận quà. Chỉ chuyển sang live sau checklist túi đầy, reconnect và double-click claim có người kiểm thử duyệt.

### Giai đoạn 4 — Backend Admin, 1.5–2 ngày

Mục tiêu:

- DDL config version/runtime;
- action đọc/ghi trong `admin_data.ps1`;
- validation server-side;
- audit snapshot;
- optimistic revision;
- thao tác player offline an toàn.

#### Trạng thái triển khai hiện tại

Đã hoàn thành backend Admin và publish snapshot cấu hình revision `4` (01/09/2026). Snapshot này bổ sung 14 source rule có thể cấu hình nhưng giữ nguyên cân bằng mặc định và an toàn: `enabled=true`, `shadowMode=true`, `rewardEnabled=false`.

- `tools/activity_admin_backend.ps1` được dot-source bởi `tools/admin_data.ps1`; các action đọc/ghi ở Mục 14 đã có trong switch allow-list.
- Source rule được runtime Java đọc từ `sources`: bật/tắt nguồn, category, điểm/lần, cap nguồn và cap nhóm. `key` và `dedupePolicy` là bất biến để hook server không bị lệch hợp đồng.
- Config được normalize trước khi lưu: kiểm tra timezone/reset/cap, 14 source key, tier/`claimBit` đã từng publish, reward item/option, ngưỡng và dữ liệu JSON. Version cũ chưa có `sources` vẫn tự dùng default để tương thích.
- `saveactivitydraft`, `publishactivityconfig` và `rollbackactivityconfig` dùng `expectedRevision`; publish/rollback đổi con trỏ runtime trong transaction. Mở `rewardEnabled=true` chỉ hợp lệ từ runtime shadow an toàn, khi draft đồng thời tắt `shadowMode`, emergency disable tắt và request có xác nhận `ENABLE_ACTIVITY_REWARDS`.
- Mọi mutation Năng Động ghi before/after JSON, revision, lý do và kết quả vào `activity_admin_audit`. Không cung cấp generic undo cho dữ liệu player.
- Player tool chỉ ghi khi player được xác nhận `OFFLINE`, hash `expectedVersion` còn khớp và SQL kiểm tra lại trạng thái online. Reset và đổi claim cần confirmation token; bỏ `claimed` bị chặn khi `activity_claim_audit` đã có `PENDING`/`SUCCESS`.
- `disableactivityemergency` chỉ đổi kill switch `activity.emergency.disable`, không xóa điểm hay mốc; server poll lại file trong tối đa 5 giây.

Kiểm tra triển khai hiện tại:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\check_activity_phase4.ps1 -FailOnIssue
```

Kết quả đã xác minh: build Java 17 thành công, `ActivityCoreTest` pass, server mở cổng `14445`, log xác nhận `Loaded Activity Points config revision 4`; save draft/publish/list version/player/log đều đi qua backend thành công. Chưa chạy mutation vào player thật và chưa bật quà.

### Giai đoạn 5 — UI tab Admin, 2–2.5 ngày

Mục tiêu:

- sáu mục theo Mục 13;
- reward editor dùng chung;
- draft/validate/diff/publish/rollback;
- trạng thái revision;
- preview reward;
- màn hình hỗ trợ player.

Chạy `node tools/check_admin_data_menu.js` sau mọi thay đổi view/script.

#### Trạng thái triển khai hiện tại

Đã thêm tab **Năng Động** vào `admin_data_menu.hta` với sáu mục đúng theo sơ đồ Mục 13:

- **Tổng quan:** runtime/revision/emergency, global config, validate, lưu draft, publish, so sánh và kill switch;
- **Nguồn điểm:** 14 source key, toggle, category, điểm, cap source/cap nhóm, hiển thị số lượt tối đa;
- **Mốc ngày / tuần:** tier ID, claim bit, ngưỡng, ngày chuẩn, trạng thái và bundle reward;
- **Reward editor:** `ITEM`, `GOLD`, `GEM`, `RUBY`, catalog/icon item, bind, số lượng, HSD, option min–max và option mặc định;
- **Người chơi:** tìm/read state, counter/mask, điều chỉnh/reset/claim với lý do và xác nhận;
- **Phát hành & lịch sử:** xem version, clone thành draft, publish/rollback và audit Năng Động.

Các file UI mới là `admin_data_menu/views/activity.html`, `admin_data_menu/js/tabs/activity.js`, `admin_data_menu/js/components/reward-editor.js` và `admin_data_menu/css/activity.css`. Tab truyền `PayloadJson` qua `RunAdmin(..., Encoded=1)`, vì vậy dùng đúng API validation/optimistic revision của Giai đoạn 4, không có validation chỉ ở JavaScript.

Đã kiểm tra cú pháp JScript, script order, registry, nav, DOM ID và request JSON encoded. `node tools/check_admin_data_menu.js` hiện còn dừng ở inline style đã tồn tại trong `admin_data_menu/views/npc-creator.html`; tab Năng Động không có inline style và vượt qua kiểm tra tích hợp độc lập. Không sửa file ngoài phạm vi Năng Động trong giai đoạn này.

### Giai đoạn 6 — Client nâng cao, 0.5–1.5 ngày, tùy chọn

Mục tiêu:

- màn hình tiến trình trực quan;
- trạng thái từng mốc;
- hiệu ứng khi tăng điểm;
- countdown reset;
- đồng bộ Game1/Game2.

MVP có thể bỏ giai đoạn này và dùng UI hiện có + menu server.

#### Kết quả triển khai (01/09/2026)

- Thêm mục **Năng động** ngay dưới **Thông báo** trong tab **Chức Năng** của panel người chơi ở cả `Game1` và `Game2`; đã bỏ lối tắt cũ để không bị trùng mục.
- Khi chọn mục, nội dung được hiển thị **ngay trong panel người chơi**, không mở màn overlay riêng. Header gọn hiển thị điểm ngày, điểm tuần/ngày chuẩn và trạng thái runtime để chữ không bị đè lên nhau.
- Trang đầu của Năng động dùng bốn hàng chọn căn giữa, cùng kiểu với danh sách **Tài khoản**: **Mốc ngày**, **Mốc tuần**, **Nguồn điểm**, **Tải lại**. Danh sách mốc/nguồn dùng hàng cao, nhãn màu rõ ràng và thanh tiến độ riêng; trạng thái thiếu chuyển sang đỏ đậm để không chìm trên nền.
- Điều hướng có hai cấp: khi đang xem mốc hoặc nguồn, bấm ra ngoài panel (hoặc phím Back) trở về bốn hàng Năng động; khi đang ở bốn hàng này, thao tác đó trở về danh sách **Chức Năng**. Nút đóng vẫn đóng panel như bình thường.
- Người chơi mở Mốc ngày hoặc Mốc tuần, chọn mốc có trạng thái `Có thể nhận` để gửi yêu cầu nhận quà; server vẫn kiểm tra toàn bộ điều kiện, túi đồ, trạng thái shadow và kill switch. Client không tự đánh dấu đã nhận quà.
- Packet cũ `-97` vẫn chỉ mang `int dailyPoints` cho client cũ. Với client mới, lần đồng bộ tăng điểm sau lần nhận dữ liệu đầu tiên sẽ hiển thị thông báo nổi `+N Năng động`.
- Packet mới `-58` có version `1`, payload giới hạn tối đa 16 mốc mỗi kỳ và 32 nguồn; client cũ không có handler sẽ bỏ qua packet này và vẫn dùng NPC/menu cũ.

##### Hợp đồng packet `-58`

Client gửi:

| Request | Payload | Mục đích |
| --- | --- | --- |
| `0` | không có | tải snapshot Năng động |
| `1` | `period: byte` (`0` ngày, `1` tuần), `tierId: UTF` tối đa 64 ký tự | yêu cầu nhận mốc |

Server trả `response = 0`, `version = 1`, tiếp theo là revision cấu hình, cờ feature/shadow/reward, điểm ngày/tuần, số ngày chuẩn, epoch reset ngày/tuần, danh sách trạng thái tier và tiến độ từng nguồn. Trạng thái tier: `0` khóa, `1` có thể nhận, `2` đã nhận, `3` tạm đóng.

Các điểm an toàn của contract:

- Server chỉ xử lý packet khi người chơi đã đăng nhập; `tierId` rỗng/quá 64 ký tự và period sai bị từ chối.
- Snapshot không chứa JSON tự do và không vượt giới hạn byte/count đã quy định.
- Lệnh claim luôn gửi snapshot mới sau khi xử lý, kể cả khi không thành công; thông báo kết quả vẫn do server phát.
- Giai đoạn này không thay đổi `rewardEnabled`; shadow mode tiếp tục ngăn phát quà production.

##### Kiểm tra build

- `ActivityClientService` và `Controller` đã được biên dịch riêng bằng Java 17, sau đó full build đã cập nhật 702 class vào `20.jar`; server được khởi động lại và nghe cổng `14445`.
- Đã sửa lỗi Unity trong `ActivityScreen.cs` ở cả `Game1` và `Game2`: lớp `Math` nội bộ che `System.Math`, đồng thời biến cục bộ `statusFont` che hàm cùng tên. Hai bản vẫn mirror; Unity đã chạy lại compile (khoảng 16 giây) và log không còn lỗi C# nào.

### Giai đoạn 7 — QA, phát hành và theo dõi, 1.5–2 ngày

Mục tiêu:

- test tích hợp server/client/admin;
- full build Java 17;
- staging/smoke test;
- publish config ở shadow;
- bật cộng điểm;
- bật reward sau cùng;
- theo dõi metric và rollback drill.

#### Kết quả triển khai (01/09/2026)

- Đã bổ sung QA gate chỉ đọc `tools/check_activity_phase7.ps1`. Gate gọi lại các kiểm tra Phase 3/4, kiểm cổng server, runtime Admin, emergency flag, JAR, protocol/client hai tab và các lỗi `ActivityScreen` trong lần Unity import mới nhất.
- Gate đã đạt với revision runtime `4`: `enabled=true`, `shadowMode=true`, `rewardEnabled=false`, `emergencyDisabled=false`; server Java nghe cổng `14445`, có 14 nguồn, 5 mốc ngày và 2 mốc tuần.
- `Game1` và `Game2` vẫn mirror; Unity log không còn lỗi `ActivityScreen` sau lần import gần nhất.
- Theo yêu cầu, **không build/export client** trong Giai đoạn 7. Test được thực hiện trực tiếp trong Unity Editor.
- `tools/check_admin_data_menu.js` tổng thể hiện vẫn dừng ở inline style có sẵn của `npc-creator.html`; đây là lỗi ngoài phạm vi tab Năng động. QA gate mới kiểm trực tiếp backend/runtime của Năng động để không bỏ sót phần cần test.

Chạy QA gate bất cứ lúc nào trước khi test Unity:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\check_activity_phase7.ps1 -FailOnIssue
```

Kết quả `ready-for-unity-editor-qa` là điều kiện để bắt đầu test tay. Lệnh không publish config, không cộng/trừ dữ liệu người chơi, không bật quà và không build client.

##### Kịch bản test Unity Editor

1. Mở project `C:\Users\PC\Music\PRJ_2Tab_550K` trong Unity; chờ Unity compile xong rồi chạy Play Mode bằng tài khoản test.
2. Mở panel người chơi, vào tab **Chức Năng**, chọn **Năng động** ngay dưới **Thông báo**. Panel phải giữ nguyên khung người chơi (không bật overlay), hiển thị header điểm/trạng thái và bốn hàng căn giữa **Mốc ngày / Mốc tuần / Nguồn điểm / Tải lại** theo kiểu Tài khoản. Mở từng mục để kiểm thanh tiến độ, trạng thái mốc và 14 nguồn/cap từ snapshot `-58`; bấm ngoài panel để lùi một cấp trước khi trở về danh sách Chức Năng.
3. Relog một lần: kiểm điểm từ packet cũ `-97` và snapshot `-58` không bị mất, không bị nhân đôi điểm login, hai client `Game1` và `Game2` cho cùng kết quả.
4. Thực hiện một nguồn test an toàn (ví dụ login, điểm danh hoặc thu hoạch) bằng nhân vật test. Điểm/thanh tiến độ cập nhật; nguồn đã chạm cap không tăng thêm.
5. Chọn một mốc đủ điều kiện và thử **Nhận**. Vì còn `shadowMode=true` và `rewardEnabled=false`, server phải từ chối an toàn: không phát vàng/item, không đánh dấu claim thành công.
6. Trong Admin mở **Năng Động → Tổng quan**, bấm **Nạp lại runtime**; kiểm revision 4, `shadow=true`, `reward=false`, `emergency=false`. Chỉ xem/audit, không lưu draft hoặc publish khi đang QA.
7. Nếu xảy ra sai điểm, packet lỗi hoặc UI không tải: dừng test, dùng nút **Tắt / mở khẩn cấp** trong Admin để bật emergency disable, lưu log server/client và không bật `rewardEnabled`.

Sau khi các kịch bản trên được duyệt, hệ thống vẫn chỉ nên ở shadow mode để quan sát dữ liệu. Việc tắt shadow hoặc mở phát quà là một đợt thay đổi production riêng, có xác nhận rõ ràng.

### Mở phát quà production (01/09/2026)

- Đã publish snapshot `version 5` / `revision 5`: `enabled=true`, `shadowMode=false`, `rewardEnabled=true`, `emergencyDisabled=false`. Điểm Năng động tích lũy trước khi mở được giữ nguyên và có thể dùng để nhận các mốc đã đủ điều kiện.
- Server đã restart có kiểm soát và log xác nhận `Loaded Activity Points config revision 5`. `Manager` poll config độc lập với phiên player, nên những lần publish sau được nạp cả khi server chưa có người chơi online.
- Dùng gate production sau mỗi thay đổi runtime:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\check_activity_live.ps1 -FailOnIssue
```

- Trong Admin, để mở quà ở các đợt sau: tắt **Shadow mode**, bật **Cho phép phát quà**, lưu draft, sau đó publish và nhập chính xác `ENABLE_ACTIVITY_REWARDS`. Nút **Tắt / mở khẩn cấp** vẫn dừng ngay tính năng mà không xóa tiến trình.

### Ước lượng

- Hệ thống server + reward MVP không có Admin đầy đủ: khoảng 7–9 ngày công.
- Tab Admin an toàn với draft/version/player tools: thêm khoảng 5–7 ngày công.
- Tổng bản production đề xuất: khoảng 12–16 ngày công.
- Boss contribution ledger, UI client đẹp và analytics nâng cao: thêm 3–5 ngày công.

Ước lượng giả định một lập trình viên đã quen codebase, không tính thời gian chờ shadow data hoặc duyệt cân bằng.

## 20. Kế hoạch kiểm thử

### 20.1 Unit test core

- award trong cap;
- award bị cắt bởi source cap;
- award bị cắt bởi daily cap;
- source disabled;
- duplicate dedupe key;
- diversity đúng một lần;
- reset ngày;
- reset tuần;
- qualified day không đếm hai lần;
- JSON schema cũ/mất field/hỏng;
- tier mapping ổn định.

### 20.2 Test tích hợp gameplay

- login hai lần cùng ngày;
- điểm danh thành công/thất bại;
- thu hoạch có đậu/không có đậu;
- nhiệm vụ phụ theo từng độ khó;
- check-in bang không có bang/đã check-in;
- câu cá thành công/thất bại/túi đầy;
- PvP chết thật/thoát/hòa/cùng đối thủ;
- phó bản clear/timeout/kick;
- boss last-hit trùng spawn.

### 20.3 Test nhận quà

- chưa đủ điểm;
- đủ điểm;
- nhận hai lần liên tiếp;
- hai request đồng thời;
- túi thiếu chỗ;
- item template bị tắt/sai;
- option min=max và min<max;
- HSD vĩnh viễn/có ngày;
- đúng giới tính/sai giới tính;
- restart sau claim;
- reset qua ngày khi chưa nhận.

### 20.4 Test Admin

- view/script đăng ký đúng;
- không trùng DOM ID hoặc global JS;
- save draft không ảnh hưởng runtime;
- validation chặn payload lỗi;
- revision conflict;
- publish transaction;
- server reload revision;
- rollback;
- audit success/failure;
- player online bị chặn;
- expectedVersion lệch bị chặn;
- Unicode tiếng Việt và JSON qua HTA → PowerShell → MySQL không lỗi.

Lệnh kiểm tra cấu trúc Admin:

```powershell
node .\tools\check_admin_data_menu.js
```

### 20.5 Test client

- Game1 và Game2 nhận `-97`;
- đăng nhập thấy đúng điểm;
- điểm cập nhật ngay sau award;
- số `0` sau reset ngày;
- relog giữ đúng điểm;
- client cũ không có UI mới vẫn chơi được.

### 20.6 Build và runtime

Theo `BUILD_JAVA_STANDARD.md`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\server_control.ps1 -Action stop
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\server_control.ps1 -Action build
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\server_control.ps1 -Action start
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\server_control.ps1 -Action status
```

Phải full compile Java 17, kiểm tra class activity trong `20.jar`, giữ backup JAR và không dùng `jar uf` thủ công.

## 21. Quy trình phát hành

### Bước 1 — Chuẩn bị

- backup database;
- lưu hash và backup `20.jar`;
- lưu config version hiện tại;
- xác nhận kill switch hoạt động;
- chạy migration trong thời gian bảo trì nếu cần.

### Bước 2 — Deploy code, feature tắt

- deploy server có core và admin;
- config `enabled = false`, `rewardEnabled = false`;
- login thử nhân vật mới/cũ;
- xác nhận load/save không làm đổi dữ liệu ngoài ý muốn.

### Bước 3 — Shadow mode

- `enabled = true`;
- `shadowMode = true`;
- `rewardEnabled = false`;
- thu thập 3–7 ngày dữ liệu;
- điều chỉnh point/cap nếu phân bố quá lệch.

Shadow mode nên tính state riêng hoặc có cờ rõ ràng. Khi chuyển sang live cần quyết định có giữ shadow points hay reset đồng loạt; khuyến nghị reset ngày vào đúng mốc 00:00 rồi mới live để công bằng.

### Bước 4 — Live điểm, khóa quà

- `shadowMode = false`;
- vẫn để `rewardEnabled = false` trong vài giờ đầu;
- xác minh packet, save, reset và cap production.

### Bước 5 — Bật quà

- publish reward config đã duyệt;
- bật `rewardEnabled = true`;
- theo dõi claim, túi đầy, item/option và lạm phát.

### Bước 6 — Mở rộng

- thêm UI client;
- thêm boss contribution;
- điều chỉnh mốc tuần;
- mở event/season nếu metric ổn định.

## 22. Kế hoạch rollback

### 22.1 Rollback cấu hình

- tắt reward ngay;
- nếu cần, tắt cộng điểm;
- trỏ runtime về config version tốt trước;
- tăng revision và xác nhận server đã reload;
- không xóa state player.

### 22.2 Rollback code

- dừng server;
- khôi phục `20.jar` backup đã kiểm tra;
- chỉ rollback schema nếu code cũ không chịu được cột mới; thêm cột nullable thường có thể giữ nguyên;
- khởi động và smoke test hai tài khoản;
- giữ `data_activity` để có thể điều tra/khôi phục sau.

### 22.3 Sự cố trao sai quà

- tắt `rewardEnabled` trước;
- khoanh config version, tier, khoảng thời gian và player đã claim;
- không chỉ lật claimed mask vì item đã được phát;
- tạo script thống kê/bồi hoàn hoặc thu hồi riêng sau khi duyệt;
- ghi audit đầy đủ.

## 23. Hướng dẫn vận hành tab Admin

### 23.1 Tạo hoặc chỉnh nguồn điểm

1. Mở `admin_data_menu.hta`.
2. Chọn **Năng Động → Nguồn điểm**.
3. Chọn source key có sẵn.
4. Chỉnh điểm, cap, category hoặc điều kiện.
5. Bấm **Lưu bản nháp**.
6. Bấm **Kiểm tra cấu hình** và xử lý toàn bộ lỗi.
7. Xem diff với bản chạy.
8. Nhập ghi chú phát hành.
9. Bấm **Xuất bản**.
10. Xác nhận revision server đã bằng revision database.

Không đổi source key đã publish. Muốn ngừng một nguồn, tắt `enabled`.

### 23.2 Tạo mốc quà

1. Chọn **Mốc quà ngày** hoặc **Mốc quà tuần**.
2. Tạo tier ID mới, ví dụ `DAILY_60_V1`.
3. Nhập ngưỡng và số ngày đủ chuẩn nếu là tuần.
4. Thêm reward từ catalog.
5. Chỉnh số lượng, giới tính, bind, option và HSD.
6. Bấm **Xem trước** để kiểm tra bundle và số ô túi.
7. Lưu draft, validate, xem diff rồi publish.

Không tái sử dụng tier ID cũ cho phần thưởng hoàn toàn khác nếu player có thể đã nhận tier đó.

### 23.3 Điều chỉnh người chơi

1. Tìm đúng ID/tên.
2. Xác nhận trạng thái `OFFLINE`.
3. Đọc `dayKey`, `weekKey`, điểm, counters và claimed mask.
4. Chọn đúng phạm vi thay đổi.
5. Nhập lý do/ticket hỗ trợ.
6. Xem bản xem trước trước/sau.
7. Xác nhận thao tác.
8. Reload lại player để chắc chắn dữ liệu đã lưu.
9. Kiểm tra audit.

Nếu player online hoặc version đã đổi, yêu cầu người chơi thoát và tải lại; không ép ghi đè.

### 23.4 Tắt khẩn cấp

1. Bấm **Tắt khẩn cấp**.
2. Chọn tắt reward trước; chỉ tắt cả cộng điểm khi sự cố liên quan point.
3. Nhập lý do.
4. Xác nhận server đã nhận revision/kill switch.
5. Giữ nguyên dữ liệu để điều tra.

## 24. Đề xuất sáng tạo cho giai đoạn sau

### 24.1 Nhiệm vụ Năng Động xoay vòng

Mỗi ngày chọn 3–5 nhiệm vụ từ pool theo level, nhưng điểm cốt lõi vẫn có thể đạt bằng nhiều đường khác nhau. Tránh khóa người chơi vào một nội dung không phù hợp sức mạnh.

### 24.2 Hệ số bắt kịp

Người bỏ lỡ vài ngày có thể nhận một lượng bonus tuần nhỏ khi quay lại. Không cho bù đủ 100% vì sẽ làm mất ý nghĩa duy trì hoạt động.

### 24.3 Streak mềm

Thưởng cosmetic, danh hiệu hoặc hộp nhỏ ở chuỗi 3/5/7 ngày đủ chuẩn. Chuỗi không nên chứa sức mạnh quá lớn và có thể có một “vé bảo lưu” để giảm áp lực đăng nhập.

### 24.4 Community goal

Tổng điểm Năng Động toàn server mở buff hoặc boss cộng đồng cuối tuần. Chỉ dùng tổng điểm đã cap để bot/farm không chi phối.

### 24.5 Cá nhân hóa theo level

Nguồn và phần thưởng có thể chia bracket level/power. Cần giữ cùng daily max để UI dễ hiểu, chỉ thay nội dung phù hợp.

### 24.6 A/B cân bằng

Config version hỗ trợ thử hai nhóm nhỏ với point/cap khác nhau. Chỉ triển khai khi có assignment ổn định theo account và dashboard so sánh; không thay đổi nhóm giữa tuần.

### 24.7 Season Năng Động

Khi daily/weekly đã ổn định, weekly points có thể đóng góp vào season track. Season phải là lớp riêng, không nhồi thêm field không giới hạn vào `data_activity` v1.

## 25. Definition of Done

Hệ thống chỉ được coi là hoàn thành khi đáp ứng tất cả:

- `data_point[11]` lưu và gửi điểm thật;
- player cũ/mới load/save/relog không mất dữ liệu;
- reset ngày/tuần đúng múi giờ Việt Nam;
- toàn bộ source cap và dedupe đúng;
- phó bản timeout không được tính clear;
- claim không trùng khi double-click hoặc request đồng thời;
- túi đầy không mất mốc hoặc phát nửa gói;
- reward item/option/HSD đúng cấu hình;
- tab Admin qua `check_admin_data_menu.js`;
- save draft không ảnh hưởng runtime;
- publish/rollback tăng revision và server reload được;
- player online không bị admin ghi đè;
- mọi mutation có audit;
- bốn manager top không còn đọc `data_point[11]` như power;
- Game1 và Game2 nhận command `-97` đúng;
- full build Java 17 thành công và `20.jar` được kiểm tra;
- có backup database, JAR, config và quy trình rollback đã diễn tập;
- shadow mode cho thấy phân bố điểm/phần thưởng nằm trong ngưỡng chấp nhận.

## 26. Thứ tự ưu tiên đề xuất

Nếu cần chia nhỏ để giảm rủi ro, triển khai theo thứ tự:

1. sửa index top + persistence + packet điểm thật;
2. core cap/reset/dedupe;
3. hook gameplay ở shadow mode;
4. reward engine và menu nhận quà;
5. backend Admin + audit;
6. UI Admin draft/publish/reward editor;
7. live điểm;
8. live reward;
9. UI client, boss contribution và cơ chế sáng tạo.

Không nên bắt đầu bằng UI đẹp hoặc phần thưởng lớn khi state, idempotency, reset và rollback chưa được chứng minh.
