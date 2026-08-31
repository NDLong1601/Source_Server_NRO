# Đặc tả backend: 4 skill mở rộng Dragon Ball

Tài liệu này mô tả bốn skill đã được chọn để phát triển trong source NRO hiện tại. Phạm vi hiện tại là dữ liệu và cơ chế server; hình ảnh chiêu, animation, icon và sách học skill được để riêng cho bước tích hợp client sau.

## 1. Trạng thái tích hợp hiện tại

| Skill | Tộc | `skill_template.id` | `TYPE` | ID cấp 1–7 | Trạng thái |
|---|---|---:|---:|---|---|
| Hellzone Grenade | Namếc | 28 | 4 | 193–199 | Backend đã có, client VFX chưa gắn |
| Barrier Prison | Trái Đất | 29 | 1 | 200–206 | Backend đã có, client VFX chưa gắn |
| Energy Absorption | Trái Đất/Namếc/Xayda | 30 | 3 | 207–213 | Backend đã có, client VFX chưa gắn |
| Super Ghost Kamikaze | Xayda | 31 | 4 | 214–220 | Backend và client VFX đã gắn |

Dữ liệu được triển khai bởi [`20260830_add_custom_skills_backend.sql`](../sql/migrations/20260830_add_custom_skills_backend.sql). Các điểm vào logic tập trung trong [`CustomSkillService.java`](../src/nro/models/services/CustomSkillService.java), còn sát thương và luồng thi triển đi qua [`SkillService.java`](../src/nro/models/services/SkillService.java).

## 2. Quy ước tham số chung

Mỗi cấp skill trong cột `skills` của `skill_template` là một JSON object. Source hiện tại sử dụng các trường sau:

| Trường | Ý nghĩa backend |
|---|---|
| `damage` | Tỉ lệ sát thương hoặc tỉ lệ hiệu ứng, tùy skill |
| `dx` | Tầm thi triển hoặc dung lượng hiệu ứng |
| `dy` | Bán kính hiệu ứng hoặc thời gian hiệu lực, tùy skill |
| `max_fight` | Số mục tiêu, số hồn ma hoặc số lượt hấp thụ |
| `mana_use` | Lượng KI hoặc phần trăm KI theo `mana_use_type` |
| `cool_down` | Hồi chiêu tính bằng mili giây |
| `power_require` | Ngưỡng sức mạnh để học cấp đó |
| `price` | Chi phí học/nâng cấp |
| `id` | ID cấp chiêu gửi cho client và lưu trong player skill |
| `point` | Cấp hiển thị 1–7 |

`DataGame.vsSkill` đã tăng lên `4` để client nhận bộ dữ liệu skill mới sau khi xóa cache skill cũ. Các icon đang dùng ID placeholder hợp lệ của source (`540`, `3784`, `720`, `722`) và sẽ thay khi hoàn thiện asset.

## 3. Luồng xử lý dùng chung

1. Player chọn skill và server kiểm tra trạng thái, tộc, mana, cooldown, bản đồ và mục tiêu.
2. Server gọi `affterUseSkill`, trừ KI, ghi cooldown và độ bền sách.
3. Skill có hiệu ứng trễ lưu một bản sao `Skill`, vì player có thể đổi skill trước khi đòn hoàn tất.
4. Sát thương đi qua `NPoint.getDameAttack` và `Player.injured`, do đó vẫn áp dụng né, giáp, Giáp Xên, khiên, sát thương thật và mastery.
5. Khi đòn thực sự trúng mục tiêu, `SkillMasteryService.recordValidUse` ghi tiến trình thành thạo.
6. Server là nguồn sự thật cho vị trí, mục tiêu, sát thương, thời gian tồn tại và giới hạn số lần đánh. Client chỉ cần hiển thị animation/effect ở bước tiếp theo.

---

## 4. Hellzone Grenade

### 4.1. Ý tưởng gameplay

Piccolo tạo các quả cầu năng lượng bao quanh điểm chỉ định. Sau thời gian chuẩn bị, các quả cầu hội tụ và gây sát thương diện rộng lên các mục tiêu hợp lệ gần điểm nổ. Đây là tuyệt kỹ định vị (`TYPE=4`), không đánh ngay tại thời điểm nhận packet.

### 4.2. Cấu hình cấp độ

| Cấp | Skill ID | ST (%) | Tầm chọn `dx` | Bán kính nổ `dy` | Tối đa mục tiêu | KI (%) | Cooldown (ms) |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 193 | 200 | 280 | 80 | 2 | 12 | 26.000 |
| 2 | 194 | 240 | 310 | 90 | 2 | 12 | 24.500 |
| 3 | 195 | 280 | 340 | 100 | 3 | 11 | 23.000 |
| 4 | 196 | 320 | 370 | 110 | 3 | 11 | 21.500 |
| 5 | 197 | 360 | 400 | 120 | 4 | 10 | 20.000 |
| 6 | 198 | 400 | 430 | 130 | 4 | 10 | 18.500 |
| 7 | 199 | 440 | 460 | 140 | 5 | 9 | 17.000 |

`mana_use_type=1`, vì vậy KI được tính theo phần trăm KI tối đa của player. `damage` được đưa vào công thức sát thương skill hiện có, không tạo một loại sát thương bỏ qua giáp riêng.

### 4.3. Cơ chế backend

- Packet thi triển được nhận qua nhánh skill đặc biệt `TYPE=4`.
- Tọa độ chọn mục tiêu bị giới hạn trong `dx` tính từ caster.
- Server tạo `HellzoneCast` chứa caster, zone, tọa độ nổ và bản sao skill.
- Sau 1.600 ms, server quét player/quái còn sống trong bán kính `dy`, loại caster và mục tiêu không được phép PK.
- Danh sách được sắp theo khoảng cách và cắt ở `max_fight` mục tiêu.
- Mỗi mục tiêu bị đánh bằng bản sao skill gốc; skill được khôi phục về lựa chọn cũ sau khi xử lý để không làm hỏng shortcut/skill đang cầm.
- Mastery chỉ tăng khi có ít nhất một mục tiêu hợp lệ được đánh trúng.

### 4.4. Quy tắc và trường hợp biên

- Caster chết, rời zone hoặc đổi map trước lúc nổ: cast bị hủy, không gây sát thương.
- Không có mục tiêu tại thời điểm nổ: không có damage và không ghi mastery hợp lệ.
- Không cho đánh đồng minh, NPC không chiến đấu hoặc mục tiêu ngoài zone.
- Sát thương vẫn chịu né, giáp, Giáp Xên, khiên và giảm sát thương hiện có.
- Không gửi VFX giả từ server; packet effect sẽ được bổ sung khi chốt giao thức client.

### 4.5. Asset/client cần bổ sung sau

- Animation tụ cầu, quỹ đạo bao vây, frame hội tụ và frame nổ.
- Effect ID/command packet để mọi client trong map thấy cùng một tâm nổ.
- Icon skill và sách cấp 1–7; thay icon placeholder 540 nếu cần.
- Âm thanh charge/explosion và giới hạn hiển thị khi nhiều cast đồng thời.

### 4.6. Tiêu chí kiểm thử

- Chọn điểm trong tầm: caster mất đúng KI, cooldown chạy, sau 1,6 giây mục tiêu trong vùng nhận damage.
- Chọn điểm ngoài tầm: skill bị từ chối, không trừ KI và không tạo scheduler task.
- Caster đổi skill trong 1,6 giây: Hellzone vẫn dùng đúng cấp đã bấm.
- Đủ hơn `max_fight` mục tiêu: chỉ số lượng mục tiêu theo cấp bị đánh.
- Caster chết/rời map trước thời điểm nổ: không có hit trễ.

---

## 5. Barrier Prison

### 5.1. Ý tưởng gameplay

Skill của Android 17 được chuyển thành một vùng nhà tù năng lượng. Mục tiêu không bị hard-stun; thay vào đó server giữ tâm nhà tù, giới hạn bán kính di chuyển và gây sát thương theo nhịp. Cách này tránh phá các trạng thái hành động hiện có của NRO.

### 5.2. Cấu hình cấp độ

| Cấp | Skill ID | ST mỗi nhịp (%) | Tầm chọn `dx` | Thời gian `dy` (ms) | KI (%) | Cooldown (ms) |
|---:|---:|---:|---:|---:|---:|---:|
| 1 | 200 | 25 | 180 | 2.500 | 10 | 30.000 |
| 2 | 201 | 30 | 200 | 3.000 | 10 | 28.500 |
| 3 | 202 | 35 | 220 | 3.500 | 9 | 27.000 |
| 4 | 203 | 40 | 240 | 4.000 | 9 | 25.500 |
| 5 | 204 | 45 | 260 | 4.500 | 8 | 24.000 |
| 6 | 205 | 50 | 280 | 5.000 | 8 | 22.500 |
| 7 | 206 | 55 | 300 | 5.500 | 7 | 21.000 |

`max_fight=1`. Bán kính nhà tù runtime hiện là `90 px` (`BARRIER_RADIUS`), còn `dx` chỉ là tầm chọn mục tiêu. `dy` là thời gian tồn tại, không phải bán kính.

### 5.3. Cơ chế backend

- Skill đi qua nhánh đánh mục tiêu `TYPE=1`, nên có thể chọn player hoặc mob.
- Server kiểm tra cùng zone, khoảng cách, PK và trạng thái sống trước khi tạo prison.
- `EffectSkill` của player hoặc `MobEffectSkill` của mob lưu owner, skill snapshot, tâm, bán kính, thời điểm bắt đầu và thời điểm pulse.
- Mỗi 1.000 ms, owner dùng snapshot skill để gây một hit lên mục tiêu đang bị nhốt.
- `PlayerService.playerMove` và `Service.setPos/setPos2/setPos0` đều gọi `clampBarrierPosition`; mọi vị trí vượt biên được kéo về mép hình tròn.
- Prison tự hủy khi hết `dy`, mục tiêu chết, owner chết, owner rời zone hoặc target mất zone.
- Pulse dùng skill snapshot nên đổi skill sau khi đặt nhà tù không thể đổi damage/cooldown của nhà tù đang tồn tại.

### 5.4. Quy tắc và trường hợp biên

- Nhà tù không thêm cờ vào `isHaveEffectSkill`, do đó không chặn toàn bộ hành động như Thôi miên/Trói.
- Dịch chuyển map, hồi sinh hoặc chết luôn giải phóng state.
- Nếu mục tiêu đang có Barrier Prison, prison cũ được thay thế bằng prison mới.
- Tọa độ server gửi sau các kỹ năng dịch chuyển cũng bị clamp, tránh bypass bằng teleport nội bộ.
- Sát thương pulse được phân loại là sát thương năng lượng, do đó Energy Absorption có thể hấp thụ pulse nếu mục tiêu đang có buff.

### 5.5. Asset/client cần bổ sung sau

- Vòng/tường năng lượng cố định quanh target, hiệu ứng pulse và hiệu ứng khi chạm biên.
- Packet bật/tắt prison cho player và mob; client phải đồng bộ tâm và thời gian còn lại.
- Icon và sách học theo cấp; hiện icon template đang dùng placeholder 3784.
- UI timer tùy chọn, không dùng chung item-time của Khiên năng lượng nếu client không hỗ trợ.

### 5.6. Tiêu chí kiểm thử

- Target bị giới hạn trong bán kính 90 px khi gửi move bình thường.
- Gọi `setPos`/teleport nội bộ vượt biên vẫn bị clamp.
- Pulse xảy ra khoảng mỗi giây và dừng ngay sau khi hết thời gian.
- Owner đổi map/chết: target được giải phóng, không còn damage trễ.
- Mob nhận prison vẫn bị pulse và tự hủy đúng hạn.

---

## 6. Energy Absorption

### 6.1. Ý tưởng gameplay

Android 19/Dr. Gero mở một lớp hấp thụ tạm thời. Một phần sát thương năng lượng thực tế sau giáp được chuyển thành KI hồi lại cho người dùng. Buff có thời gian, dung lượng hấp thụ và số lượt tối đa để tránh biến thành khiên vô hạn.

### 6.2. Cấu hình cấp độ

| Cấp | Skill ID | Hấp thụ `damage` | Dung lượng `dx` (% HP max) | Thời gian `dy` (ms) | Lượt `max_fight` | KI (%) | Cooldown (ms) |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 207 | 20% | 15% | 3.000 | 1 | 8 | 45.000 |
| 2 | 208 | 25% | 20% | 3.500 | 1 | 8 | 42.500 |
| 3 | 209 | 30% | 25% | 4.000 | 2 | 7 | 40.000 |
| 4 | 210 | 35% | 30% | 4.500 | 2 | 7 | 37.500 |
| 5 | 211 | 40% | 35% | 5.000 | 3 | 6 | 35.000 |
| 6 | 212 | 45% | 40% | 5.500 | 3 | 6 | 32.500 |
| 7 | 213 | 50% | 45% | 6.000 | 4 | 5 | 30.000 |

Skill được tạo cho cả ba `nclass_id`, cùng bộ ID cấp độ. Cơ chế hiện tại từ chối kích hoạt nếu player đang bật Khiên năng lượng hoặc đã có Energy Absorption.

### 6.3. Cơ chế backend

- Skill là loại tự dùng `TYPE=3`, không cần mục tiêu.
- Khi kích hoạt, server lưu trong `EffectSkill`: phần trăm hấp thụ, thời gian, dung lượng tối đa (`hpMax * dx / 100`), số lượt còn lại và dung lượng đã dùng.
- Trong `Player.injured`, sau giảm giáp/giáp Xên và trước Khiên năng lượng, server kiểm tra `SkillUtil.isEnergySkill(attacker)`.
- Phần hấp thụ = `damage * absorptionPercent / 100`, bị giới hạn bởi dung lượng còn lại.
- Phần hấp thụ được trừ khỏi damage HP; 50% phần hấp thụ được hồi KI, không vượt giới hạn KI của player.
- Mỗi hit hợp lệ giảm một lượt. Hết lượt, hết dung lượng hoặc hết thời gian thì buff bị gỡ.
- Các đòn vật lý/mob attack không đi qua nhánh hấp thụ năng lượng.

### 6.4. Phân loại đòn năng lượng

`SkillUtil.isEnergySkill` hiện bao gồm Kamejoko, Masenko, Antomic, Quả cầu kênh khí, Makankosappo, Dịch chuyển tức thời, Super Kamejoko, Liên hoàn chưởng, Ma phong ba, Khí Nguyên Trảm, Barrier Prison, Hellzone Grenade và Super Ghost Kamikaze. Danh sách này là điểm cần cập nhật khi thêm skill năng lượng mới.

### 6.5. Quy tắc và trường hợp biên

- Lớp `voHieuChuong` cũ vẫn được xử lý trước cho Kamejoko/Masenko/Antomic; Energy Absorption không phá logic cũ.
- Sát thương được hấp thụ sau giáp, nên chỉ số giáp vẫn có giá trị.
- Không hồi HP; chỉ hồi KI, nhằm giữ vai trò khác với Khiên năng lượng và Trị thương.
- Dung lượng dùng `long` để tránh tràn khi HP lớn.
- Buff được gỡ khi chết và khi player dispose.

### 6.6. Asset/client cần bổ sung sau

- Animation mở lòng bàn tay/hút năng lượng, tia năng lượng chạy về caster và indicator thời gian.
- Hiệu ứng riêng khi đạt giới hạn hấp thụ hoặc bị phá.
- Icon template hiện dùng placeholder 720; sách cấp 1–7 chưa có.
- Có thể thêm packet hiển thị `absorbed`, `mpRestored` nếu client cần số damage màu riêng.

### 6.7. Tiêu chí kiểm thử

- Khi nhận Kamehameha, HP chỉ giảm phần damage còn lại và KI tăng đúng 50% phần hấp thụ.
- Đòn vật lý không kích hoạt hấp thụ.
- Nhiều hit liên tiếp dừng đúng khi hết `max_fight` hoặc dung lượng.
- Hết timer thì hit tiếp theo gây đủ damage bình thường.
- Khiên năng lượng đang bật thì không thể bật Energy Absorption đồng thời.

---

## 7. Super Ghost Kamikaze

### 7.1. Ý tưởng gameplay

Gotenks triệu hồi nhiều hồn ma năng lượng bay quanh người. Lần dùng tiếp theo khóa đúng mục tiêu đang chọn. Hồn ma bay **lần lượt từng con**, chờ va chạm trước khi phóng con tiếp theo. Khi mục tiêu chết, các hồn chưa dùng vẫn bay quanh caster và có thể nhận lệnh tấn công mục tiêu khác.

### 7.2. Cấu hình cấp độ

| Cấp | Skill ID | ST gốc (%) | Tầm chọn `dx` | Bán kính nổ `dy` | Số hồn ma | Tồn tại (giây) | KI (%) | Cooldown (ms) |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 214 | 180 | 280 | 70 | 1 | 20 | 12 | 38.000 |
| 2 | 215 | 220 | 310 | 75 | 2 | 25 | 12 | 36.000 |
| 3 | 216 | 260 | 340 | 80 | 3 | 30 | 11 | 34.000 |
| 4 | 217 | 310 | 370 | 85 | 4 | 35 | 11 | 32.000 |
| 5 | 218 | 360 | 400 | 90 | 5 | 40 | 10 | 30.000 |
| 6 | 219 | 410 | 430 | 95 | 6 | 45 | 9 | 28.000 |
| 7 | 220 | 460 | 460 | 100 | 7 | 50 | 8 | 26.000 |

`damage` là phần trăm sức đánh gốc của caster. Ngân sách damage chia cố định lúc triệu hồi, phân bổ phần dư để tổng bằng 100% (cấp 7: 15/15/14/14/14/14/14). Không chia lại theo số hồn còn sống. Mục tiêu phụ nhận 35% phần damage của hồn đó, có làm tròn số nguyên như hệ thống chiến đấu hiện tại.

Thời gian tồn tại do `SuperGhostFormation.lifetimeForLevel()` quản lý: `20.000 + (cấp - 1) * 5.000 ms`. Bắt đầu tính sau khi mọi hồn chạy xong ba frame triệu hồi; nhận lệnh, đổi mục tiêu và nhận snapshot không gia hạn. Không cần migration DB riêng cho timer này; giữ nguyên KI/cooldown hiện tại.

### 7.3. Cơ chế backend

- Skill đi qua nhánh `TYPE=4`: lần bấm đầu triệu hồi, lần bấm tiếp theo chọn mục tiêu trong `dx` và ra lệnh tấn công.
- `GhostCast` là adapter chiến đấu/network; `SuperGhostFormation` giữ state độc lập và có unit test. Tick mỗi 100 ms.
- Mỗi hồn có index ổn định, trạng thái `SUMMONING → ORBITING → FLYING → SPENT`; nhánh hủy mục tiêu là `FLYING → RETURNING → ORBITING`.
- Ba frame triệu hồi, mỗi frame 180 ms; từng hồn xuất hiện lệch nhau 55 ms. Lệnh trong lúc triệu hồi được xếp chờ.
- Chỉ một hồn được phóng tại một thời điểm. Chờ hit được server xử lý, nghỉ tối thiểu 180 ms rồi mới phóng tiếp; tốc độ 420 px/s, tối đa 42 px mỗi tick.
- Quỹ đạo theo **số hồn đang ở vòng**, không theo cấp ban đầu: bán kính ngang 42/50/58/66/74/82/90 px, bán kính dọc 18/22/26/30/34/38/42 px; tâm vòng nâng dần. Các hồn còn lại được xếp đều và chuyển đội hình êm trong 250 ms khi số lượng thay đổi.
- Kiểm tra va chạm bằng quãng đường đi trong tick cộng ngưỡng 12 px; phát nổ tại thân mục tiêu. Đánh dấu hồn đã tiêu hao trước callback damage để không nổ lặp.
- Mục tiêu chết sau 2 hit của 7 hồn: dừng lệnh, giữ 5 hồn; lần bấm tiếp có thể chọn player hoặc mob khác. Không tự chọn mục tiêu mới.
- Nếu mục tiêu chết do nguồn khác, rời zone hoặc không còn hợp lệ khi đang bay: hồn đó quay lại vòng, không tiêu hao. Nếu bay quá 4 giây chưa tới mục tiêu thì cũng thu hồi.
- Mastery ghi một lần ở lần nổ đầu tiên thực sự có mục tiêu.

### 7.4. Quy tắc và trường hợp biên

- Chỉ lần triệu hồi trừ KI và bắt đầu cooldown. Ra lệnh cho đội hình đang tồn tại không trừ thêm KI/cooldown, kể cả cooldown cũ chưa kết thúc.
- Lệnh có `castId` và `targetType/targetId`; không chọn lại mục tiêu gần tọa độ chuột. Lệnh của cast đã hết hạn không được biến thành lần triệu hồi mới.
- Chặn spam/đổi mục tiêu trong khi chuỗi tấn công hoặc thu hồi chưa hoàn tất. Sau khi mục tiêu chết và hồn bay đã về, được ra lệnh mới.
- Hồn ma không đánh caster và không đánh player không vượt qua `canAttackPlayer`.
- Kiểm tra lại trạng thái PvP, zone, chết/hồi sinh trước khi đánh. Caster chết, đổi map hoặc disconnect thì toàn bộ đội hình bị hủy, không có damage trễ.
- Scheduler dùng thread daemon, tự hủy future khi hết thời gian/hết hồn, caster rời map, dispose hoặc có exception.
- Logic chiến đấu và số hồn còn lại do server quyết định; client không tự gây damage hay tự tiêu hao hồn.
- Không cho phép một hồn ma nổ lặp lại hoặc vượt số hit đã cấu hình.

### 7.5. Asset/client

- Client Game1/Game2 dùng cùng logic. Bộ nguồn chính xác tại `assets/super_ghost_kamikaze_v2/`; dựng lại bằng `tools/build_super_ghost_assets.ps1` (mặc định dùng bộ nguồn trong repo, có thể truyền `-SourceDirectory` và `-ClientRoot`). Preview sinh vào `output/super_ghost_kamikaze/frames_preview.png`, không đưa vào Git. Giữ alpha, cắt lề trong suốt, thống nhất scale và pivot khuôn mặt; không vẽ lại ảnh gốc.

| Frame | File nguồn | Vai trò |
|---:|---|---|
| 0–2 | `Triệu hồi 1.png` / `Triệu hồi 2.png` / `Triệu hồi 3.png` | Chạy lần lượt khi khởi tạo |
| 3 | `Hoàn chỉnh sau triệu hồi.png` | Đứng chờ, bay vòng |
| 4–6 | `Bay tấn công 1.png` / `Bay tấn công 2.png` / `Bay tấn công 3.png` | Server chọn ngẫu nhiên một kiểu mỗi lượt phóng |
| 7 | `Vụ nổ.png` | Va chạm, hiển thị 450 ms |

- Texture x4: frame 0–6 là 264×264, frame 7 là 288×288. Client vẽ bằng kích thước logic 66×66 và 72×72, không phóng to theo kích thước PNG thô; alpha bật, mipmap tắt, không nén mất nét.
- Sprite bay lật theo vị trí mục tiêu hiện tại; tọa độ lấy từ server và nội suy 90 ms giữa các tick. Pivot giữ nguyên vị trí khi mirror, tránh bay ngược hoặc nhảy hình lúc chuyển frame.
- Protocol v2 `-45/25`, template 31: `SPAWN=10, LAUNCH=11, IMPACT=12, RECALL=13, END=14, SNAPSHOT=15, MOVE=16, READY=17`. Không dùng lại payload legacy 0–3.
- Payload đầy đủ: casterId, templateId, event, castId, sequence, count, summonMs, lifetimeMs, elapsedMs, remainingMs, ownerXY, targetType/Id/XY, changedIndex và state/variant/XY/stateAge cho từng hồn. Các timer dài dùng **int32**, tránh tràn 32.767 ms. Tổng payload `45 + count × 10` byte.
- Snapshot khi vào map chứa đúng hồn còn lại và thời gian còn lại; client bỏ packet cũ/trùng sequence và không chạy lại cooldown. Server và client v2 phải cập nhật cùng nhau.
- Icon skill dùng template ảnh `26554`; sách cấp 1–7 dùng dải icon `26547–26553`.
- Số sprite bị giới hạn tối đa bảy, đúng theo cấp skill.

### 7.6. Tiêu chí kiểm thử

- Cấp 1–7 sinh đúng 1–7 hồn, tồn tại 20–50 giây sau khi triệu hồi; thử cả biên vượt 32.767 ms.
- 7 hồn, mục tiêu thứ nhất chết sau 2 hit: còn đúng 5 hồn; đổi mục tiêu phía đối diện dùng được 5 hồn còn lại, không gia hạn thời gian.
- Tối đa một hồn bay; đủ ba kiểu bay, đúng hướng trái/phải, hiệu ứng nổ đúng mục tiêu.
- Mục tiêu chết do nguồn khác trước va chạm: thu hồi, không có damage hoặc tiêu hao. Từ chối lệnh trùng/không hợp lệ.
- Mỗi hồn ma chỉ gây một vụ nổ.
- Mục tiêu phụ trong `dy` nhận khoảng 35% damage chính.
- Caster chết hoặc đổi map thì scheduler dừng và không có damage trễ.
- Late observer thấy đúng số hồn còn lại, không làm sống lại hồn đã nổ. Game1/Game2 và hai caster độc lập.
- Test tự động: `SuperGhostFormationTest.java`, `SuperGhostWireFixture.java` (serializer thật) và `tools/tests/ghost-client/` (replay qua hai file effect C# thật).
- Trước phát hành: test trực tiếp Unity hai tài khoản cùng map, A → logout → B cùng cửa sổ, zoom x1–x4, PvP/PvE/di chuyển mục tiêu và hết thời gian trong lúc bay.

---

## 8. Checklist tích hợp client về sau

- [ ] Chốt protocol effect cho từng skill, không tái sử dụng packet có cấu trúc khác.
- [ ] Tạo sprite/atlas x1–x4 và đăng ký effect ID.
- [ ] Thay icon placeholder và bump `vsSkill` khi dữ liệu client thay đổi.
- [ ] Tạo item sách cấp 1–7, option chỉ đọc và cách học skill theo hệ thống sách hiện tại.
- [ ] Thêm packet timer/trạng thái cho Barrier Prison và Energy Absorption nếu UI cần.
- [ ] Test hai client trong cùng map để xác nhận animation không lệch vị trí server.
- [ ] Chạy regression cho skill cũ: Kamehameha, Ma phong ba, Khiên năng lượng, Khí Nguyên Trảm.
