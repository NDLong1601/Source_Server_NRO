# Đặc tả backend: 4 skill mở rộng Dragon Ball

Tài liệu này mô tả bốn skill đã được chọn để phát triển trong source NRO hiện tại. Phạm vi hiện tại là dữ liệu và cơ chế server; hình ảnh chiêu, animation, icon và sách học skill được để riêng cho bước tích hợp client sau.

## 1. Trạng thái tích hợp hiện tại

| Skill | Tộc | `skill_template.id` | `TYPE` | ID cấp 1–7 | Trạng thái |
|---|---|---:|---:|---|---|
| Hellzone Grenade | Namếc | 28 | 4 | 193–199 | Backend đã có, client VFX chưa gắn |
| Barrier Prison | Trái Đất | 29 | 1 | 200–206 | Backend đã có, client VFX chưa gắn |
| Energy Absorption | Trái Đất/Namếc/Xayda | 30 | 3 | 207–213 | Backend đã có, client VFX chưa gắn |
| Super Ghost Kamikaze | Xayda | 31 | 4 | 214–220 | Backend đã có, client VFX chưa gắn |

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

Gotenks triệu hồi nhiều hồn ma năng lượng bay tới mục tiêu gần nhất và phát nổ khi chạm. Mỗi hồn ma có thể chọn mục tiêu riêng; vụ nổ có sát thương chính lên mục tiêu chạm và sát thương lan thấp hơn lên mục tiêu xung quanh.

### 7.2. Cấu hình cấp độ

| Cấp | Skill ID | ST gốc (%) | Tầm chọn `dx` | Bán kính nổ `dy` | Số hồn ma | KI (%) | Cooldown (ms) |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 214 | 180 | 280 | 70 | 1 | 12 | 38.000 |
| 2 | 215 | 220 | 310 | 75 | 2 | 12 | 36.000 |
| 3 | 216 | 260 | 340 | 80 | 2 | 11 | 34.000 |
| 4 | 217 | 310 | 370 | 85 | 3 | 11 | 32.000 |
| 5 | 218 | 360 | 400 | 90 | 3 | 10 | 30.000 |
| 6 | 219 | 410 | 430 | 95 | 4 | 9 | 28.000 |
| 7 | 220 | 460 | 460 | 100 | 5 | 8 | 26.000 |

`damage` là phần trăm sức đánh gốc của caster. Runtime chia damage chính theo số hồn ma (`100 / max_fight`), sau đó dùng 35% phần đó cho mục tiêu phụ trong vùng nổ.

### 7.3. Cơ chế backend

- Skill đi qua nhánh `TYPE=4`; tọa độ đích phải nằm trong `dx`.
- Server tạo `GhostCast`, sinh `max_fight` projectile tại vị trí caster và chạy tick mỗi 100 ms.
- Mỗi tick, hồn ma tìm mục tiêu hợp lệ gần vị trí hiện tại, di chuyển tối đa 42 px và dừng sau 4 giây.
- Khi khoảng cách còn tối đa 36 px, hồn ma phát nổ một lần rồi đánh mục tiêu chính.
- Player có thể nhận tối đa hai hit chính từ cùng một cast; mục tiêu phụ vẫn nhận sát thương lan theo vùng.
- Nếu không tìm thấy mục tiêu, hồn ma bay về tọa độ đã chọn rồi tự biến mất khi hết lifetime.
- Mastery ghi một lần ở lần nổ đầu tiên thực sự có mục tiêu.

### 7.4. Quy tắc và trường hợp biên

- Mục tiêu chết, rời zone hoặc không còn hợp lệ sẽ bị bỏ qua ở tick tiếp theo.
- Hồn ma không đánh caster và không đánh player không vượt qua `canAttackPlayer`.
- Scheduler dùng thread daemon, tự hủy future khi cast hoàn tất hoặc có exception.
- Logic vị trí hiện là server authoritative; chưa phát packet sprite nên client chưa thấy hồn ma cho tới bước VFX.
- Không cho phép một hồn ma nổ lặp lại hoặc vượt số hit đã cấu hình.

### 7.5. Asset/client cần bổ sung sau

- Sprite hồn ma bay, trail, frame đổi hướng và frame phát nổ.
- Packet spawn/update/despawn hoặc một protocol effect đủ để các client thấy cùng vị trí.
- Icon template placeholder 722 và sách cấp 1–7.
- Âm thanh tiếng cười/nổ; giới hạn số sprite để không gây quá tải ở cấp 7.

### 7.6. Tiêu chí kiểm thử

- Cấp 1 sinh một hồn ma; cấp 7 sinh năm hồn ma.
- Hồn ma tự đổi mục tiêu khi mục tiêu cũ chết hoặc rời zone.
- Mỗi hồn ma chỉ gây một vụ nổ; player không nhận quá hai hit chính/cast.
- Mục tiêu phụ trong `dy` nhận khoảng 35% damage chính.
- Caster chết hoặc đổi map thì scheduler dừng và không có damage trễ.

---

## 8. Checklist tích hợp client về sau

- [ ] Chốt protocol effect cho từng skill, không tái sử dụng packet có cấu trúc khác.
- [ ] Tạo sprite/atlas x1–x4 và đăng ký effect ID.
- [ ] Thay icon placeholder và bump `vsSkill` khi dữ liệu client thay đổi.
- [ ] Tạo item sách cấp 1–7, option chỉ đọc và cách học skill theo hệ thống sách hiện tại.
- [ ] Thêm packet timer/trạng thái cho Barrier Prison và Energy Absorption nếu UI cần.
- [ ] Test hai client trong cùng map để xác nhận animation không lệch vị trí server.
- [ ] Chạy regression cho skill cũ: Kamehameha, Ma phong ba, Khiên năng lượng, Khí Nguyên Trảm.

