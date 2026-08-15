# Hướng dẫn thiết kế và triển khai hệ thống nâng cấp ngoại trang

Tài liệu này ghi lại toàn bộ phân tích và phương án triển khai chức năng nâng cấp cho:

- Cải trang.
- Ván bay hoặc thú cưỡi.
- Vật phẩm đeo lưng.
- Pet đi theo nhân vật.

Mục tiêu là để lần sau có thể mở tài liệu, rà lại source và triển khai theo từng bước mà không phải phân tích lại từ đầu.

> Trạng thái: đây là tài liệu thiết kế. Chức năng nâng cấp ngoại trang chưa được triển khai trong source tại thời điểm viết tài liệu.

---

## 1. Kết luận kỹ thuật

Chức năng có độ khả thi cao vì server đã có sẵn phần lớn nền móng cần thiết:

- Giao diện chọn nhiều vật phẩm trong tab Combine.
- Điều phối `showInfoCombine()` và `startCombine()`.
- Hiệu ứng thành công/thất bại.
- Cơ chế lưu option theo từng vật phẩm.
- Option `72 - Cấp #` dùng để lưu cấp cường hóa.
- Option `209 - Bị rớt cấp # lần` dùng để lưu lịch sử tụt cấp.
- Cấu hình runtime qua `combine.properties`.
- Vật phẩm `987 - Đá bảo vệ` đã có và đang được dùng trong cường hóa trang bị thường.
- Các option của mọi vật phẩm đang mặc đều được cộng vào chỉ số nhân vật trong `NPoint`.

Không cần thêm cột mới vào bảng `player`, không cần đổi định dạng lưu item và không cần sửa client nếu tiếp tục dùng option `72` để lưu cấp.

Phương án khuyến nghị là tạo **một chức năng Combine chung** tên `Nâng cấp ngoại trang`, tự nhận diện nhóm vật phẩm từ item mục tiêu. Không nên tạo bốn hệ thống độc lập vì sẽ lặp lại phần kiểm tra nguyên liệu, tỉ lệ, may mắn, bảo hộ, tụt cấp và gửi packet.

---

## 2. Phạm vi chức năng đề xuất

Một lần nâng cấp cho phép người chơi chọn tối đa bốn vai trò vật phẩm:

1. Một ngoại trang cần nâng cấp.
2. Một loại đá nâng cấp bắt buộc, đúng với nhóm ngoại trang.
3. Một vật phẩm may mắn tùy chọn: Cỏ 3 lá hoặc Cỏ 4 lá.
4. Một vật phẩm bảo hộ tùy chọn.

Khi thành công:

- Cấp ngoại trang tăng `1`.
- Các option chỉ số được cho phép tăng theo bước cấu hình.
- Gửi hiệu ứng Combine thành công.

Khi thất bại:

- Luôn tiêu hao đá nâng cấp và chi phí tiền.
- Luôn tiêu hao vật phẩm may mắn nếu đã sử dụng.
- Chỉ tụt cấp tại các mốc được cấu hình.
- Nếu đáng lẽ tụt cấp nhưng có bảo hộ thì giữ nguyên cấp.
- Có thể cấu hình bảo hộ chỉ bị tiêu hao khi thực sự ngăn tụt cấp.

---

## 3. Những phần source hiện có cần tham khảo

### 3.1. Cường hóa trang bị thường

File:

```text
src/nro/models/combine/NangCapVatPham.java
```

Chức năng hiện tại:

- Chỉ chấp nhận trang bị có `template.type < 5`.
- Chỉ chấp nhận đá nâng cấp `template.type == 14`.
- Dùng item ID `987` làm đá bảo vệ.
- Đọc cấp từ option `72`.
- Tăng một option chính thuộc nhóm `47, 6, 0, 7, 14, 22, 23`.
- Tăng thêm một option phụ nếu là `27` hoặc `28`.
- Thất bại ở cấp `2, 4, 6` có thể tụt một cấp.
- Khi tụt cấp sẽ thêm hoặc tăng option `209`.

Không nên sao chép nguyên class này rồi thay điều kiện type vì:

- Code hiện tại giả định item luôn có ít nhất một option chính hợp lệ.
- Nếu ngoại trang không có option phù hợp, biến option có thể là `null`.
- Code phân loại vật phẩm dựa nhiều vào số lượng item được chọn.
- Chỉ hỗ trợ hai hoặc ba item, chưa hỗ trợ đồng thời may mắn và bảo hộ.
- Tỉ lệ và chi phí khi thực hiện đang lấy từ trạng thái Combine đã tính ở bước xem trước.
- Đá bảo vệ đang bị tiêu hao trong mọi lần thử nếu được chọn, kể cả khi thành công.
- Không đáp ứng yêu cầu tăng nhiều dòng option của ngoại trang.

### 3.2. Nâng cấp nhẫn

File:

```text
src/nro/models/combine/NangCapNhan.java
```

Nhẫn được nâng bằng cách đổi `item.template` sang template ID kế tiếp, sau đó xóa option hiện tại và nạp lại option mặc định.

Cơ chế này không phù hợp với ngoại trang vì:

- Mỗi cấp phải có một item template riêng.
- Option ngẫu nhiên hoặc option riêng của item có thể bị mất.
- Số lượng template tăng rất nhanh.
- Khó áp dụng chung cho cải trang, ván bay, đeo lưng và pet.

Ngoại trang nên giữ nguyên template, chỉ thay đổi option cấp và option chỉ số.

### 3.3. Điều phối Combine

File:

```text
src/nro/models/combine/CombineService.java
```

Các vị trí cần sửa khi triển khai:

- Khai báo hằng `NANG_CAP_NGOAI_TRANG`.
- Điều hướng trong `showInfoCombine()`.
- Điều hướng trong `startCombine()`.
- Nội dung tiêu đề tại `getTextTopTabCombine()`.
- Nội dung hướng dẫn tại `getTextInfoTabCombine()`.

### 3.4. Trạng thái phiên Combine

File:

```text
src/nro/models/combine/Combine.java
```

Các trường đang có:

```java
public List<Item> itemsCombine;
public int typeCombine;
public int goldCombine;
public int gemCombine;
public float ratioCombine;
public int countDaNangCap;
public short countDaBaoVe;
public long lastTimeCombine;
```

Không bắt buộc thêm trường mới. Mọi giá trị quan trọng phải được tính lại lúc người chơi bấm xác nhận, không được tin hoàn toàn dữ liệu đã lưu trong các trường này từ bước xem trước.

### 3.5. Cấu hình runtime

File:

```text
src/nro/models/combine/CombineConfig.java
combine.properties
```

`CombineConfig` kiểm tra thay đổi của `combine.properties` tối đa mỗi giây. Vì vậy sau khi code Java đã được build và deploy một lần, admin có thể điều chỉnh:

- Tỉ lệ.
- Số đá.
- Giá vàng/ngọc.
- Mốc tụt cấp.
- Bonus may mắn.
- Cấp tối đa.

mà không phải restart server.

### 3.6. Slot trang bị

File:

```text
src/nro/models/services/InventoryService.java
```

Các slot hiện tại:

| Nhóm | Type thường gặp | Slot |
|---|---:|---:|
| Cải trang | `5` | `5` |
| Pet đi theo | `27` | `7` |
| Đeo lưng/cờ | `11` | `8` |
| Ván bay/thú cưỡi | `23`, `24` | `9` |
| Hào quang | `11`, item được nhận diện là aura | `12` |

`type == 11` không đồng nghĩa tuyệt đối với đeo lưng vì item hào quang `1230` cũng có type 11 nhưng được đưa vào slot 12.

`type == 27` cũng không đồng nghĩa tuyệt đối với pet. Type 27 đang được dùng cho rất nhiều nguyên liệu, vật phẩm nhiệm vụ, đá bảo vệ và đá may mắn.

### 3.7. Cộng option vào chỉ số nhân vật

File:

```text
src/nro/models/player/NPoint.java
```

`NPoint` duyệt toàn bộ `player.inventory.itemsBody` và gọi `addOption()` cho các option của từng item đang mặc. Vì vậy option được tăng trên cải trang, ván bay, đeo lưng hoặc pet sẽ có hiệu lực khi item được trang bị.

### 3.8. Pet đi theo

Các item pet hiện đang được xử lý bằng danh sách `case` dài tại:

```text
src/nro/models/services_func/UseItem.java
src/nro/models/player/Player.java, hàm sendNewPet()
```

Đây là phần cần chú ý nhất khi phân loại mục tiêu nâng cấp.

---

## 4. Phân loại ngoại trang

Nên tạo enum hoặc class cấu hình nhóm vật phẩm, ví dụ:

```java
public enum AppearanceUpgradeCategory {
    COSTUME("costume", "Cải trang"),
    MOUNT("mount", "Ván bay"),
    BACK("back", "Đeo lưng"),
    FOLLOW_PET("followPet", "Pet");

    private final String configKey;
    private final String displayName;
}
```

### 4.1. Cải trang

Điều kiện cơ bản:

```java
item.template.type == 5
```

Quyết định cần chốt trước khi triển khai:

- Có cho nâng tất cả cải trang không?
- Có cho nâng cải trang dành riêng cho đệ tử không?
- Có cho nâng cải trang có hạn sử dụng không?
- Có loại trừ cải trang nhiệm vụ hoặc cải trang đặc biệt không?

Nên hỗ trợ danh sách loại trừ trong cấu hình.

### 4.2. Ván bay hoặc thú cưỡi

Điều kiện:

```java
item.template.type == 23 || item.template.type == 24
```

Nhóm này có thể nhận diện bằng type tương đối an toàn.

### 4.3. Đeo lưng

Điều kiện:

```java
item.template.type == 11 && !InventoryService.isAuraItem(item)
```

Cần cân nhắc:

- Cờ bang hoặc cờ nhiệm vụ có được nâng không?
- Item đeo lưng có thời hạn có được nâng không?
- Có item type 11 nào chỉ dùng làm hiệu ứng, không có option chiến đấu không?

Nếu dữ liệu type 11 quá hỗn tạp, chuyển sang allowlist ID tương tự pet.

### 4.4. Pet đi theo

Không được dùng điều kiện sau:

```java
item.template.type == 27
```

Lý do: các item sau cũng có thể là type 27 nhưng không phải pet:

- Đá bảo vệ ID `987`.
- Đá may mắn `1079..1083`.
- Mảnh vật phẩm.
- Vật phẩm nhiệm vụ.
- Nguyên liệu Combine.

Giải pháp giai đoạn đầu:

```properties
appearance.followPet.templateIds=892,893,908,909,910,...
```

Giải pháp lâu dài tốt hơn:

```text
FollowPetRegistry
    templateId
    headPart
    bodyPart
    legPart
```

Sau đó dùng registry chung cho:

- `UseItem` khi mặc pet.
- `Player.sendNewPet()` khi đăng nhập hoặc load lại map.
- `InventoryService` khi xác định slot.
- Hệ thống nâng cấp ngoại trang.

Việc này loại bỏ hai danh sách switch pet đang bị trùng giữa `UseItem` và `Player`.

---

## 5. Cách lưu cấp nâng cấp

Khuyến nghị tiếp tục dùng:

```text
Option 72: Cấp #
```

Ví dụ item chưa nâng cấp:

```text
Sức đánh +10%
HP +15%
KI +15%
```

Sau khi nâng cấp thành công lần đầu:

```text
Sức đánh +11%
HP +16%
KI +16%
Cấp 1
```

Ưu điểm của option 72:

- Đã tồn tại trong client và database.
- Đã được dùng bởi cường hóa trang bị thường.
- Tự được lưu trong JSON option của item.
- Không cần migration bảng `player`.
- Không cần tạo item option template mới.

Không nên tạo option mới chỉ để lưu cấp ngoại trang nếu chưa thật sự cần. Bảng `item_option_template` hiện đã có ID từ `0` đến `250`, trong khi protocol gửi ID và số lượng option bằng byte. Dùng lại option 72 an toàn hơn và tránh chạm giới hạn protocol.

---

## 6. Hiểu đúng yêu cầu “mỗi lần thành công tăng 1% option”

`ItemOption.param` là số nguyên và khi gửi client được ghi bằng `writeShort`.

Với option:

```text
Sức đánh +10%
```

Nếu tính đúng toán học “tăng 1% của 10” thì kết quả là `10.1`, nhưng source không lưu số thập phân cho param.

Vì vậy nên định nghĩa rõ:

> Mỗi lần nâng thành công, các option phần trăm hợp lệ tăng 1 điểm phần trăm, tức param tăng 1.

Ví dụ:

```text
10% -> 11% -> 12% -> 13%
```

Đây là cách dễ hiểu với người chơi, hiển thị đúng trên client và có thể hoàn tác chính xác khi tụt cấp.

### 6.1. Không được tăng mọi option có param

Các option sau đều có param nhưng không nên được tăng:

- `1`: Thời gian sử dụng.
- `9`: Hiệu lực trong số phút.
- `12`: Số lần sử dụng còn lại.
- `13`: Số quái đã hạ.
- `20`: PIN.
- `21`: Yêu cầu sức mạnh.
- `31`: Số lượng.
- `37`: Số lần ký gửi.
- `40`: Số đá ngũ sắc.
- `63, 64, 65`: Thời gian còn lại.
- `72`: Cấp.
- `86, 87`: Ký gửi vàng/ngọc.
- `93`: Hạn sử dụng.
- `102, 107`: Số sao pha lê.
- `154`: Không thể bán lại.
- `164`: Điểm sự kiện.
- `171`: Số lượng.
- `174`: Năm sự kiện.
- `185`: Số giờ sử dụng.
- `208`: Đã chuyển hóa.
- `209`: Số lần tụt cấp.
- `210`: Số dòng chỉ số ẩn.
- `211`: Số lần giám định.
- `212`: Độ bền.
- `213`: Giá bán.
- `219`: Số lần tẩy.
- `228`: Cường hóa lỗ sao.
- `231`: Hạn sử dụng hoặc vĩnh viễn.
- `232`: Số lần giao dịch.
- `235`: Số lần ký gửi.
- `250`: Kilis.

Danh sách trên chỉ là cảnh báo. Chính sách đúng phải là **allowlist**, không phải blacklist.

### 6.2. Allowlist option chỉ số

Nên cấu hình các option được phép tăng. Ví dụ ban đầu thận trọng:

```properties
appearance.upgrade.optionSteps=5:1,14:1,16:1,18:1,19:1,49:1,50:1,77:1,80:1,81:1,88:1,94:1,95:1,96:1,97:1,98:1,99:1,100:1,101:1,103:1,104:1,108:1,111:1,117:1,147:1,160:1,162:1,173:1,226:1
```

Ý nghĩa:

```text
optionId:bước_tăng_mỗi_cấp
```

Ví dụ:

```text
50:1   -> Sức đánh tăng 1 điểm phần trăm mỗi cấp
77:1   -> HP tăng 1 điểm phần trăm mỗi cấp
103:1  -> KI tăng 1 điểm phần trăm mỗi cấp
94:1   -> Giảm sát thương tăng 1 điểm phần trăm mỗi cấp
```

Không nên đưa toàn bộ danh sách trên vào production ngay. Trước tiên cần kiểm tra option thực tế của các ngoại trang đang lưu trong SHOP, giftcode, drop và dữ liệu người chơi.

### 6.3. Giới hạn option

Nên có giới hạn theo option để tránh chỉ số vô hạn:

```properties
appearance.upgrade.optionCaps=5:100,14:100,16:100,18:100,19:100,49:100,50:100,77:100,80:100,81:100,94:100,95:100,96:100,97:100,98:100,99:100,103:100,104:100,108:100,111:100,117:100,147:100,162:100,173:100,226:100
```

Mọi param gửi client phải nằm trong khoảng:

```text
-32768 .. 32767
```

### 6.4. Option chỉ số phẳng

Các option như:

- `0`: Tấn công +#.
- `6`: HP +#.
- `7`: KI +#.
- `22`: HP +#K.
- `23`: KI +#K.
- `27`: Hồi HP.
- `28`: Hồi KI.
- `47`: Giáp +#.
- `48`: HP, KI +#.

không nên mặc định tăng `1`, vì hiệu quả có thể quá nhỏ hoặc không đúng ý nghĩa “1%”. Nếu muốn hỗ trợ, cấu hình bước tăng riêng, ví dụ:

```properties
appearance.upgrade.optionSteps=0:100,6:1000,7:1000,22:1,23:1,47:10,50:1,77:1,103:1
```

Cách dùng bước cố định giúp tụt cấp có thể trừ lại đúng giá trị đã cộng.

---

## 7. Vật phẩm nguyên liệu cần chuẩn bị

Nếu tạo đầy đủ hệ thống độc lập, dự kiến cần:

| Vật phẩm | Vai trò | Có thể dùng lại item hiện có? |
|---|---|---|
| Đá nâng cấp cải trang | Nguyên liệu bắt buộc | Nên tạo mới |
| Đá nâng cấp ván bay | Nguyên liệu bắt buộc | Nên tạo mới |
| Đá nâng cấp đeo lưng | Nguyên liệu bắt buộc | Nên tạo mới |
| Đá nâng cấp pet | Nguyên liệu bắt buộc | Nên tạo mới |
| Cỏ 3 lá | Tăng tỉ lệ mức thấp | Cần tạo mới |
| Cỏ 4 lá | Tăng tỉ lệ mức cao | Cần tạo mới |
| Khiên bảo hộ | Chống tụt cấp | Có thể dùng lại ID `987` |

### 7.1. Type đề xuất

- Đá nâng cấp bắt buộc: có thể dùng type `14`, cùng nhóm đá nâng cấp trang bị hiện tại.
- Cỏ may mắn và bảo hộ: có thể dùng type `27`, cùng quy ước với đá bảo vệ và đá may mắn hiện tại.

Luồng Combine phải nhận diện nguyên liệu bằng **template ID**, không được chỉ dựa vào type.

### 7.2. Có cần code UseItem cho nguyên liệu không?

Không bắt buộc. Đây là vật phẩm được chọn trong tab Combine, không phải vật phẩm tiêu hao bằng cách bấm sử dụng trực tiếp.

Nếu muốn trải nghiệm tốt hơn, có thể thêm nhánh trong `UseItem` chỉ để thông báo:

```text
Vật phẩm này được dùng trong chức năng Nâng cấp ngoại trang tại Bà Hạt Mít.
```

Không được trừ item khi người chơi bấm sử dụng theo cách này.

---

## 8. Cấu hình `combine.properties` đề xuất

Các ID trong ví dụ dưới đây là placeholder. Phải thay bằng ID thật sau khi kiểm tra SQL dump và database đang chạy.

```properties
# ============================================================
# NÂNG CẤP NGOẠI TRANG - THIẾT LẬP CHUNG
# ============================================================

appearance.upgrade.enabled=true
appearance.upgrade.maxLevel=10
appearance.upgrade.rateCap=95

# Cấp hiện tại mà nếu thử lên cấp tiếp theo thất bại sẽ bị tụt 1 cấp.
appearance.upgrade.downgradeLevels=2,4,6,8

# Có cho nâng item có hạn sử dụng hay không.
appearance.upgrade.allowTimedItems=false

# Vật phẩm bảo hộ. Có thể dùng lại Đá bảo vệ ID 987.
appearance.upgrade.protectionItemId=987
appearance.upgrade.protectionQuantity=1

# true: chỉ trừ bảo hộ nếu thất bại và thực sự ngăn tụt cấp.
# false: trừ bảo hộ trong mọi lần thử nếu người chơi đã chọn.
appearance.upgrade.protectionConsumeOnPreventOnly=true

# Cỏ may mắn. Thay ID placeholder bằng ID item thật.
appearance.upgrade.clover3ItemId=<ID_CO_3_LA>
appearance.upgrade.clover3BonusRate=5
appearance.upgrade.clover3Quantity=1

appearance.upgrade.clover4ItemId=<ID_CO_4_LA>
appearance.upgrade.clover4BonusRate=10
appearance.upgrade.clover4Quantity=1

# Không cho dùng đồng thời hai loại cỏ.
appearance.upgrade.allowMultipleLuckItems=false

# Option được phép tăng và bước tăng mỗi cấp.
appearance.upgrade.optionSteps=50:1,77:1,94:1,103:1,147:1

# Trần param của từng option.
appearance.upgrade.optionCaps=50:100,77:100,94:100,103:100,147:100

# ============================================================
# CẢI TRANG
# ============================================================

appearance.costume.enabled=true
appearance.costume.stoneId=<ID_DA_CAI_TRANG>
appearance.costume.stoneCosts=1,2,3,5,8,12,18,25,35,50
appearance.costume.goldCosts=1000000,2000000,5000000,10000000,20000000,40000000,80000000,150000000,250000000,400000000
appearance.costume.gemCosts=0,0,0,0,0,0,0,0,0,0
appearance.costume.successRates=100,80,60,40,25,15,10,5,3,1
appearance.costume.excludeTemplateIds=

# ============================================================
# VÁN BAY / THÚ CƯỠI
# ============================================================

appearance.mount.enabled=true
appearance.mount.stoneId=<ID_DA_VAN_BAY>
appearance.mount.stoneCosts=1,2,3,5,8,12,18,25,35,50
appearance.mount.goldCosts=1000000,2000000,5000000,10000000,20000000,40000000,80000000,150000000,250000000,400000000
appearance.mount.gemCosts=0,0,0,0,0,0,0,0,0,0
appearance.mount.successRates=100,80,60,40,25,15,10,5,3,1
appearance.mount.excludeTemplateIds=

# ============================================================
# ĐEO LƯNG
# ============================================================

appearance.back.enabled=true
appearance.back.stoneId=<ID_DA_DEO_LUNG>
appearance.back.stoneCosts=1,2,3,5,8,12,18,25,35,50
appearance.back.goldCosts=1000000,2000000,5000000,10000000,20000000,40000000,80000000,150000000,250000000,400000000
appearance.back.gemCosts=0,0,0,0,0,0,0,0,0,0
appearance.back.successRates=100,80,60,40,25,15,10,5,3,1
appearance.back.excludeTemplateIds=1230

# ============================================================
# PET ĐI THEO
# ============================================================

appearance.followPet.enabled=true
appearance.followPet.stoneId=<ID_DA_PET>
appearance.followPet.stoneCosts=1,2,3,5,8,12,18,25,35,50
appearance.followPet.goldCosts=1000000,2000000,5000000,10000000,20000000,40000000,80000000,150000000,250000000,400000000
appearance.followPet.gemCosts=0,0,0,0,0,0,0,0,0,0
appearance.followPet.successRates=100,80,60,40,25,15,10,5,3,1

# Phải điền đầy đủ danh sách ID pet thật. Dấu ... không phải cấu hình hợp lệ.
appearance.followPet.templateIds=892,893,908,909,910
```

### 8.1. Quy ước mảng theo cấp

Mỗi vị trí mảng tương ứng với cấp hiện tại trước khi nâng:

| Vị trí | Lần nâng |
|---:|---|
| `0` | `+0 -> +1` |
| `1` | `+1 -> +2` |
| `2` | `+2 -> +3` |
| `3` | `+3 -> +4` |
| ... | ... |

Nếu `maxLevel=10`, các mảng chi phí và tỉ lệ nên có 10 phần tử.

Nếu cấu hình thiếu phần tử, code cần fallback an toàn hoặc từ chối nâng tại cấp chưa được cấu hình. Không nên trả chi phí `0` một cách im lặng.

### 8.2. Nhiều loại đá theo giai đoạn

Nếu muốn cấp cao dùng đá khác, có thể mở rộng thành:

```properties
appearance.costume.stoneIds=<DA_1>,<DA_1>,<DA_1>,<DA_2>,<DA_2>,<DA_2>,<DA_3>,<DA_3>,<DA_4>,<DA_4>
```

Khi đó vị trí trong `stoneIds` cũng được đọc theo cấp hiện tại.

---

## 9. Kiến trúc class đề xuất

### 9.1. Class chính

Tạo file:

```text
src/nro/models/combine/NangCapNgoaiTrang.java
```

Trách nhiệm:

- Phân tích danh sách item được chọn.
- Xác định nhóm ngoại trang.
- Đọc cấu hình nhóm.
- Tính preview.
- Kiểm tra lại toàn bộ dữ liệu khi xác nhận.
- Roll tỉ lệ đúng một lần.
- Cập nhật cấp và option.
- Trừ nguyên liệu.
- Gửi hiệu ứng và refresh túi.

### 9.2. Class hoặc enum nhóm

Có thể đặt tại:

```text
src/nro/models/combine/AppearanceUpgradeCategory.java
```

Trách nhiệm:

- Tên hiển thị.
- Prefix cấu hình.
- Kiểm tra item mục tiêu.
- Lấy ID đá cần dùng.

### 9.3. Selection parser

Nên có object riêng, ví dụ:

```java
private static final class UpgradeSelection {
    Item target;
    Item stone;
    Item luck;
    Item protection;
    AppearanceUpgradeCategory category;
}
```

Parser phải đảm bảo:

- Chính xác một target.
- Chính xác một đá bắt buộc.
- Tối đa một luck item.
- Tối đa một protection item.
- Không có item lạ.
- Không dùng cùng object cho hai vai trò.
- Không phụ thuộc vào thứ tự người chơi chọn item.

### 9.4. Upgrade plan

Nên tính ra một object chỉ đọc:

```java
private static final class UpgradePlan {
    int currentLevel;
    int nextLevel;
    int stoneId;
    int stoneCost;
    int goldCost;
    int gemCost;
    double baseRate;
    double luckBonus;
    double finalRate;
    boolean downgradeRisk;
}
```

`showInfoCombine()` và `nangCapNgoaiTrang()` đều gọi cùng một hàm tạo plan để tránh màn hình hiển thị một kiểu nhưng phép quay dùng giá trị khác.

---

## 10. Skeleton code minh họa

Đoạn dưới đây chỉ mô tả cấu trúc, chưa phải code có thể copy nguyên vào project.

```java
public final class NangCapNgoaiTrang {

    private static final int OPTION_UPGRADE_LEVEL = 72;
    private static final int OPTION_DOWNGRADE_COUNT = 209;

    private NangCapNgoaiTrang() {
    }

    public static void showInfoCombine(Player player) {
        UpgradeSelection selection = parseSelection(player);
        if (!selection.valid()) {
            showInvalidSelection(player, selection.errorMessage);
            return;
        }

        UpgradePlan plan = buildPlan(player, selection);
        if (!plan.valid()) {
            showInvalidSelection(player, plan.errorMessage);
            return;
        }

        String text = buildPreviewText(player, selection, plan);
        if (!hasEnoughResources(player, selection, plan)) {
            showMissingResourceMenu(player, selection, plan, text);
            return;
        }

        CombineService.gI().baHatMit.createOtherMenu(
                player,
                ConstNpc.MENU_START_COMBINE,
                text,
                buildConfirmButton(plan),
                "Từ chối");
    }

    public static void nangCapNgoaiTrang(Player player) {
        synchronized (player.combineNew) {
            if (!canAttemptNow(player)) {
                return;
            }

            UpgradeSelection selection = parseSelection(player);
            if (!selection.valid()) {
                Service.gI().sendThongBao(player, selection.errorMessage);
                return;
            }

            if (!selectedItemsStillInBag(player, selection)) {
                Service.gI().sendThongBao(player, "Vật phẩm đã thay đổi, vui lòng chọn lại");
                return;
            }

            UpgradePlan plan = buildPlan(player, selection);
            if (!plan.valid() || !hasEnoughResources(player, selection, plan)) {
                Service.gI().sendThongBao(player, "Không đủ điều kiện nâng cấp");
                return;
            }

            boolean success = Util.isTrue((float) plan.finalRate, 100);
            boolean preventedDowngrade = false;

            if (success) {
                applyUpgradeSuccess(selection.target);
            } else if (plan.downgradeRisk) {
                if (selection.protection != null) {
                    preventedDowngrade = true;
                } else {
                    applyDowngrade(selection.target);
                }
            }

            consumeAttemptCosts(player, selection, plan, preventedDowngrade);
            sendResultAndRefresh(player, success, preventedDowngrade);
            writeAuditLog(player, selection, plan, success, preventedDowngrade);
        }
    }
}
```

### 10.1. Đọc cấp

```java
private static int getUpgradeLevel(Item item) {
    Item.ItemOption levelOption = item.getOptionById(OPTION_UPGRADE_LEVEL);
    return levelOption == null ? 0 : Math.max(0, levelOption.param);
}
```

### 10.2. Tăng cấp

```java
private static void increaseUpgradeLevel(Item item) {
    Item.ItemOption levelOption = item.getOptionById(OPTION_UPGRADE_LEVEL);
    if (levelOption == null) {
        item.itemOptions.add(new Item.ItemOption(OPTION_UPGRADE_LEVEL, 1));
    } else {
        levelOption.param++;
    }
}
```

### 10.3. Tăng option theo allowlist

```java
private static void applyConfiguredStatSteps(Item item, boolean increase) {
    Map<Integer, Integer> steps = getConfiguredOptionSteps();
    Map<Integer, Integer> caps = getConfiguredOptionCaps();

    for (Item.ItemOption option : item.itemOptions) {
        if (option == null || option.optionTemplate == null) {
            continue;
        }
        if (option.optionTemplate.id == OPTION_UPGRADE_LEVEL
                || option.optionTemplate.id == OPTION_DOWNGRADE_COUNT) {
            continue;
        }

        Integer step = steps.get(option.optionTemplate.id);
        if (step == null || step <= 0) {
            continue;
        }

        if (increase) {
            int cap = caps.getOrDefault(option.optionTemplate.id, Short.MAX_VALUE);
            option.param = Math.min(cap, option.param + step);
        } else {
            option.param = Math.max(0, option.param - step);
        }
    }
}
```

Lưu ý: nếu một option đã chạm cap thì nâng cấp vẫn có thể tăng cấp item nhưng option đó không tăng thêm. Preview phải báo rõ điều này hoặc từ chối nâng nếu không còn option nào có thể tăng.

### 10.4. Tụt cấp

```java
private static void applyDowngrade(Item item) {
    Item.ItemOption levelOption = item.getOptionById(OPTION_UPGRADE_LEVEL);
    if (levelOption == null || levelOption.param <= 0) {
        return;
    }

    applyConfiguredStatSteps(item, false);
    levelOption.param--;

    Item.ItemOption history = item.getOptionById(OPTION_DOWNGRADE_COUNT);
    if (history == null) {
        item.itemOptions.add(new Item.ItemOption(OPTION_DOWNGRADE_COUNT, 1));
    } else {
        history.param++;
    }

    if (levelOption.param <= 0) {
        // Có thể giữ "Cấp 0" hoặc xóa option 72 tùy quy ước hiển thị.
    }
}
```

---

## 11. Quy tắc phân tích item được chọn

Không phân loại theo vị trí trong `itemsCombine`. Người chơi có thể chọn nguyên liệu theo bất kỳ thứ tự nào.

Thứ tự phân loại đề xuất:

1. Nếu ID là protection ID thì nhận là bảo hộ.
2. Nếu ID là một trong các luck item ID thì nhận là may mắn.
3. Nếu ID khớp đá nâng cấp của bất kỳ category nào thì nhận là đá.
4. Nếu item khớp một category ngoại trang thì nhận là target.
5. Còn lại là item không hợp lệ.

Phải kiểm tra trùng vai trò:

- Hai target: từ chối.
- Hai loại đá: từ chối.
- Hai cỏ: từ chối nếu `allowMultipleLuckItems=false`.
- Hai bảo hộ: từ chối.
- Item lạ: từ chối.

Sau khi xác định target và category, kiểm tra đá đã chọn có đúng `stoneId` của category đó hay không.

---

## 12. Công thức tỉ lệ thành công

Công thức đề xuất:

```text
finalRate = min(rateCap, baseRate + luckBonus)
```

Ví dụ:

```text
Tỉ lệ cơ bản: 20%
Cỏ 3 lá: +5 điểm phần trăm
Tỉ lệ cuối: 25%
```

Nếu base rate đã là `100%`, không áp `rateCap=95`. Cần giữ khả năng nâng chắc chắn ở các cấp đầu:

```java
double finalRate = baseRate >= 100
        ? 100
        : Math.min(rateCap, baseRate + luckBonus);
```

Không nên roll nhiều lần hoặc dùng nhiều lời gọi random cho một lần bấm. Kết quả phải được xác định bởi đúng một lần roll.

---

## 13. Quy tắc tụt cấp và bảo hộ

Ví dụ:

```properties
appearance.upgrade.downgradeLevels=2,4,6,8
```

Ý nghĩa:

- Đang `+2`, thử lên `+3`, thất bại thì có nguy cơ tụt về `+1`.
- Đang `+4`, thử lên `+5`, thất bại thì có nguy cơ tụt về `+3`.
- Đang `+6`, thử lên `+7`, thất bại thì có nguy cơ tụt về `+5`.
- Đang `+8`, thử lên `+9`, thất bại thì có nguy cơ tụt về `+7`.

### 13.1. Có bảo hộ

Nếu `protectionConsumeOnPreventOnly=true`:

| Kết quả | Có nguy cơ tụt | Xử lý bảo hộ |
|---|---|---|
| Thành công | Bất kỳ | Không trừ |
| Thất bại | Không | Không trừ |
| Thất bại | Có | Trừ và giữ cấp |

Nếu `false`, bảo hộ bị trừ trong mọi lần thử có chọn bảo hộ.

### 13.2. Không cho lãng phí bảo hộ

Ở bước preview, nếu cấp hiện tại không nằm trong danh sách tụt cấp nhưng người chơi chọn bảo hộ, nên:

- Từ chối và yêu cầu bỏ bảo hộ; hoặc
- Hiển thị rõ bảo hộ không cần thiết và sẽ không bị tiêu hao.

Phương án đầu an toàn hơn, tránh tranh chấp với người chơi.

---

## 14. Quy tắc tiêu hao

Chỉ tiêu hao sau khi toàn bộ điều kiện đã được kiểm tra thành công.

| Tài nguyên | Thành công | Thất bại |
|---|---:|---:|
| Đá nâng cấp | Có | Có |
| Vàng | Có | Có |
| Ngọc | Có | Có |
| Cỏ may mắn | Có | Có |
| Bảo hộ, chế độ mọi lần thử | Có | Có |
| Bảo hộ, chế độ chỉ khi ngăn tụt | Không | Chỉ khi ngăn tụt |

Không được tiêu hao bất kỳ thứ gì nếu:

- Chọn sai target.
- Chọn sai đá.
- Thiếu số lượng đá.
- Thiếu vàng/ngọc.
- Item đã đạt cấp tối đa.
- Item đã bị di chuyển khỏi túi.
- Cấu hình bị lỗi.
- Request bị gửi lặp quá nhanh.

---

## 15. Preview tại NPC

Nội dung nên hiển thị đầy đủ:

```text
|2|Cải trang Goku [+4]
|0|Sức đánh +14%
|0|HP +19%
|0|KI +19%

|2|Sau nâng cấp [+5]
|7|Sức đánh +15%
|7|HP +20%
|7|KI +20%

|1|Tỉ lệ cơ bản: 25%
|1|Cỏ 3 lá: +5%
|1|Tỉ lệ cuối: 30%
|1|Cần 8 Đá nâng cấp cải trang
|1|Cần 20.000.000 vàng
|1|Thất bại sẽ tụt xuống +3
|1|Khiên bảo hộ sẽ ngăn tụt cấp
```

Preview chỉ hiển thị các option trong allowlist. Các option thời hạn, giao dịch hoặc metadata vẫn có thể hiện ở phần thông tin hiện tại nhưng không được đưa vào phần “sau nâng cấp”.

Phải xử lý trường hợp target không có option nào được phép tăng:

```text
Vật phẩm này không có chỉ số phù hợp để nâng cấp.
```

Không được để xảy ra `NullPointerException` như khi code giả định luôn tìm thấy option chính.

---

## 16. Tích hợp vào `CombineService`

### 16.1. Hằng combine type

Chọn một giá trị chưa sử dụng:

```java
public static final int NANG_CAP_NGOAI_TRANG = <TYPE_CHUA_DUNG>;
```

Trước khi chọn phải tìm toàn bộ hằng trong `CombineService` để tránh trùng.

### 16.2. Show info

```java
case NANG_CAP_NGOAI_TRANG:
    NangCapNgoaiTrang.showInfoCombine(player);
    break;
```

### 16.3. Start combine

```java
case NANG_CAP_NGOAI_TRANG:
    NangCapNgoaiTrang.nangCapNgoaiTrang(player);
    break;
```

### 16.4. Nội dung tab

Tiêu đề:

```text
Ta sẽ cường hóa ngoại trang
để gia tăng sức mạnh
```

Hướng dẫn:

```text
Chọn cải trang, ván bay, đeo lưng hoặc pet
Chọn đúng loại đá nâng cấp
Có thể chọn thêm cỏ may mắn và khiên bảo hộ
Sau đó chọn 'Nâng cấp'
```

---

## 17. Tích hợp menu Bà Hạt Mít

File:

```text
src/nro/models/npc_list/BaHatMit.java
```

Source hiện có menu `ConstNpc.MENU_NANG_CAP_TRANG_BI` với các lựa chọn cường hóa trang bị và nâng cấp nhẫn.

Có thể thêm lựa chọn thứ ba:

```text
Nâng cấp
ngoại trang
```

Trong handler:

```java
if (select == 0) {
    CombineService.gI().openTabCombine(player, CombineService.NANG_CAP_VAT_PHAM);
} else if (select == 1) {
    CombineService.gI().openTabCombine(player, CombineService.NANG_CAP_NHAN);
} else if (select == 2) {
    CombineService.gI().openTabCombine(player, CombineService.NANG_CAP_NGOAI_TRANG);
}
```

Đồng thời thêm `NANG_CAP_NGOAI_TRANG` vào nhánh xử lý `ConstNpc.MENU_START_COMBINE` để nút xác nhận gọi `CombineService.startCombine(player)`.

Nếu Bà Hạt Mít xuất hiện ở nhiều map, phải kiểm tra cả handler theo từng `mapId`, không chỉ sửa một menu.

---

## 18. Tạo item template nguyên liệu

File SQL:

```text
sql/team2026.sql
```

### 18.1. Quy tắc ID

`Manager.ITEM_TEMPLATES` được load vào list và `ItemService.getTemplate(id)` truy cập trực tiếp theo index. Vì vậy:

- Không tạo khoảng trống ID.
- Không chọn ID chỉ dựa vào SQL dump nếu database live có dữ liệu mới hơn.
- Kiểm tra ID ở cả `sql/team2026.sql` và database đang chạy.
- Thêm tuần tự ở cuối danh sách item template.

### 18.2. SQL mẫu

Các ID và icon bên dưới chỉ là placeholder:

```sql
INSERT INTO item_template
(`id`, `TYPE`, `gender`, `NAME`, `description`, `level`, `icon_id`, `part`,
 `is_up_to_up`, `power_require`, `gold`, `gem`, `head`, `body`, `leg`)
VALUES
(<ID_DA_CAI_TRANG>, 14, 3, 'Đá nâng cấp cải trang',
 'Dùng để nâng cấp cải trang tại Bà Hạt Mít', 0, <ICON_DA_CAI_TRANG>, -1,
 1, 0, 0, 0, -1, -1, -1),

(<ID_DA_VAN_BAY>, 14, 3, 'Đá nâng cấp ván bay',
 'Dùng để nâng cấp ván bay và thú cưỡi tại Bà Hạt Mít', 0, <ICON_DA_VAN_BAY>, -1,
 1, 0, 0, 0, -1, -1, -1),

(<ID_DA_DEO_LUNG>, 14, 3, 'Đá nâng cấp đeo lưng',
 'Dùng để nâng cấp vật phẩm đeo lưng tại Bà Hạt Mít', 0, <ICON_DA_DEO_LUNG>, -1,
 1, 0, 0, 0, -1, -1, -1),

(<ID_DA_PET>, 14, 3, 'Đá nâng cấp pet',
 'Dùng để nâng cấp pet đi theo tại Bà Hạt Mít', 0, <ICON_DA_PET>, -1,
 1, 0, 0, 0, -1, -1, -1),

(<ID_CO_3_LA>, 27, 3, 'Cỏ 3 lá',
 'Tăng tỉ lệ thành công khi nâng cấp ngoại trang', 0, <ICON_CO_3_LA>, -1,
 1, 0, 0, 0, -1, -1, -1),

(<ID_CO_4_LA>, 27, 3, 'Cỏ 4 lá',
 'Tăng mạnh tỉ lệ thành công khi nâng cấp ngoại trang', 0, <ICON_CO_4_LA>, -1,
 1, 0, 0, 0, -1, -1, -1);
```

Trước khi dùng SQL mẫu phải đối chiếu lại tên và thứ tự cột của schema hiện tại.

Nếu dùng lại `987 - Đá bảo vệ` thì không cần tạo Khiên bảo hộ mới. Nếu muốn kinh tế riêng cho ngoại trang, tạo template mới và đổi `appearance.upgrade.protectionItemId`.

### 18.3. Option mặc định

Nguyên liệu Combine thường không cần option mặc định. Không thêm option chỉ để mô tả công dụng; phần mô tả đã nằm trong `item_template.description`.

---

## 19. Icon vật phẩm

Mỗi icon mới phải có đủ:

```text
data/icon/x1/<icon-id>.png
data/icon/x2/<icon-id>.png
data/icon/x3/<icon-id>.png
data/icon/x4/<icon-id>.png
```

Nên tạo từ ảnh x4 hoặc ảnh nguồn chất lượng cao, không phóng lớn từ x1.

Script có sẵn:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts/resize_item_icon.ps1 `
  -Root . `
  -IconId <ICON_ID> `
  -ReferenceId 15002
```

Kích thước tham khảo icon vuông `15002`:

| Zoom | Kích thước |
|---|---|
| x1 | 24x24 |
| x2 | 48x48 |
| x3 | 72x72 |
| x4 | 96x96 |

Sau khi tạo:

- Kiểm tra alpha trong suốt.
- Kiểm tra icon x1 không bị nhòe hoặc mất chi tiết.
- Kiểm tra icon x4 không bị cắt viền.
- Nếu ghi đè icon ID đã tồn tại, tăng version riêng của icon trong `DataGame.sendSmallVersion`.

---

## 20. Cache client

File:

```text
src/nro/models/data/DataGame.java
src/nro/models/data/ItemData.java
```

Khi thêm item template mới:

1. Tăng `DataGame.vsItem`.
2. Build lại JAR.
3. Khởi động lại server khi an toàn.
4. Yêu cầu client thoát hoàn toàn và đăng nhập lại.

Nếu không tăng version item, client cũ có thể:

- Không thấy item mới.
- Hiển thị sai tên/icon.
- Không mở được SHOP có item ID mới dù server không báo exception rõ ràng.

Nếu chỉ thay nội dung `combine.properties` mà không thêm template hoặc sửa icon thì không cần tăng cache version.

---

## 21. Đưa nguyên liệu vào SHOP hoặc admin

Tùy thiết kế kinh tế, nguyên liệu có thể đến từ:

- SHOP NPC.
- Giftcode.
- Drop Mob/Boss.
- Sự kiện.
- Nhiệm vụ ngày.
- Vòng quay.
- Phần thưởng phó bản.

Trước khi thêm SHOP, dùng admin tool để kiểm tra shop/tag/tab:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\admin_data.ps1 `
  -Action listshops `
  -Output <FILE_KET_QUA>
```

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\admin_data.ps1 `
  -Action listtabs `
  -ShopId <SHOP_ID> `
  -Output <FILE_KET_QUA>
```

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\admin_data.ps1 `
  -Action listshopitems `
  -TabId <TAB_ID> `
  -Output <FILE_KET_QUA>
```

Không tự đoán shop ID hoặc tab ID.

---

## 22. Kiểm tra item vẫn còn trong túi khi xác nhận

`itemsCombine` giữ tham chiếu tới object item trong túi. Khi bấm nâng cấp phải xác nhận mỗi item vẫn còn nằm trong `player.inventory.itemsBag` bằng identity, không chỉ tìm theo template ID.

Ví dụ dùng helper hiện có:

```java
InventoryService.gI().getIndexItemBag(player, item)
```

Nếu trả về `-1`, dừng và yêu cầu chọn lại.

Điều này ngăn các trường hợp:

- Người chơi di chuyển item sau khi mở preview.
- Một request khác đã tiêu hao nguyên liệu.
- Client gửi lại nút xác nhận cũ.
- Config thay đổi trong lúc cửa sổ vẫn mở.

---

## 23. Chống double click và request lặp

`Combine` có `lastTimeCombine`, nhưng cần kiểm tra chứ không chỉ cập nhật.

Khuyến nghị:

```text
Cooldown tối thiểu: 300-500 ms mỗi lần xác nhận.
```

Toàn bộ đoạn:

```text
validate -> roll -> mutate item -> trừ nguyên liệu -> refresh
```

nên nằm trong cùng một vùng đồng bộ theo phiên Combine của người chơi.

Không giữ lock trong lúc thực hiện thao tác database hoặc tác vụ chậm.

---

## 24. Log audit đề xuất

Mỗi lần thử nên ghi tối thiểu:

```text
time
playerId
playerName
targetTemplateId
targetCreateTime
category
levelBefore
levelAfter
baseRate
luckItemId
luckBonus
finalRate
success
downgraded
protectionItemId
preventedDowngrade
stoneItemId
stoneCost
goldCost
gemCost
```

Log giúp xử lý khiếu nại và phát hiện:

- Tỉ lệ cấu hình sai.
- Item bị tăng hai lần.
- Bảo hộ bị trừ sai.
- Người chơi khai thác request lặp.
- Một option không mong muốn bị tăng.

Không ghi toàn bộ dữ liệu nhạy cảm của tài khoản vào log.

---

## 25. Xử lý item có hạn sử dụng

Ngoại trang có thể mang các option thời gian như `93 - Hạn sử dụng # ngày` hoặc dữ liệu thời hạn dựa trên `createTime`.

Khuyến nghị mặc định:

```properties
appearance.upgrade.allowTimedItems=false
```

Lý do:

- Người chơi có thể tốn nhiều nguyên liệu rồi item hết hạn.
- Khó xử lý tranh chấp.
- Có thể tạo lợi thế kinh tế không dự kiến nếu cấp được chuyển sang item khác.

Nếu cho phép:

- Không được tăng option thời hạn.
- Preview phải cảnh báo item vẫn hết hạn như bình thường.
- Cấp nâng cấp không được ngăn `ItemService.isOutOfDateTime()` thu hồi item.

---

## 26. Đồng bộ chỉ số sau nâng cấp

Chức năng Combine chỉ chọn item trong túi nên thông thường item mục tiêu không được mặc tại thời điểm nâng.

Sau khi nâng:

- Gọi `InventoryService.sendItemBags(player)`.
- Gọi `Service.sendMoney(player)` nếu có trừ vàng/ngọc.
- Gửi effect thành công/thất bại.
- Xóa hoặc reopen danh sách item Combine theo UX mong muốn.

Khi người chơi mặc item đã nâng, `InventoryService.itemBagToBody()` sẽ gửi lại body và gọi `Service.point(player)`, làm `NPoint` tính lại option.

Nếu sau này cho phép nâng item đang mặc thì phải:

- Gửi lại item body.
- Gọi `Service.point(player)`.
- Refresh ngoại hình nếu item là cải trang, đeo lưng, pet hoặc mount.

Phiên bản đầu nên yêu cầu người chơi tháo item về túi trước khi nâng.

---

## 27. Kiểm thử đơn vị logic

Nếu project chưa có framework test phù hợp, có thể tách các hàm thuần để test độc lập.

### 27.1. Phân loại category

- Type 5 nhận là costume.
- Type 23 nhận là mount.
- Type 24 nhận là mount.
- Type 11 bình thường nhận là back.
- Item aura `1230` không nhận là back.
- ID pet có trong allowlist nhận là follow pet.
- Đá bảo vệ type 27 không nhận là pet.
- Đá may mắn type 27 không nhận là pet.
- Mảnh vật phẩm type 27 không nhận là pet.

### 27.2. Parse selection

- Target + đá: hợp lệ.
- Target + đá + cỏ: hợp lệ.
- Target + đá + bảo hộ: hợp lệ nếu có nguy cơ tụt.
- Target + đá + cỏ + bảo hộ: hợp lệ.
- Hai target: từ chối.
- Hai đá: từ chối.
- Hai cỏ: từ chối theo cấu hình mặc định.
- Item lạ: từ chối.
- Sai đá của category: từ chối.

### 27.3. Option

- Thành công thêm option 72 nếu chưa có.
- Thành công tăng option 72 nếu đã có.
- Chỉ option trong allowlist được tăng.
- Option 93 hạn sử dụng không đổi.
- Option 209 lịch sử tụt cấp không bị coi là stat.
- Option đã chạm cap không vượt cap.
- Param không vượt giới hạn short.
- Tụt cấp trừ đúng step đã cộng.

### 27.4. Tỉ lệ

- Không cỏ: final bằng base.
- Cỏ 3 lá: cộng đúng bonus.
- Cỏ 4 lá: cộng đúng bonus.
- Final không vượt rate cap.
- Base 100 vẫn giữ 100.
- Roll chỉ diễn ra một lần.

### 27.5. Bảo hộ

- Thành công không trừ bảo hộ ở chế độ `onPreventOnly`.
- Thất bại cấp an toàn không trừ bảo hộ.
- Thất bại cấp nguy hiểm có bảo hộ: không tụt, trừ bảo hộ.
- Thất bại cấp nguy hiểm không bảo hộ: tụt một cấp.
- Option 209 chỉ tăng khi thật sự tụt, không tăng khi được bảo hộ.

---

## 28. Test tích hợp trên server test

Chuẩn bị ít nhất một item của mỗi nhóm với option rõ ràng:

```text
50 - Sức đánh +10%
77 - HP +10%
103 - KI +10%
93 - Hạn sử dụng 30 ngày
```

Test từng bước:

1. Mở menu Nâng cấp ngoại trang.
2. Chọn item và sai đá, xác nhận bị từ chối.
3. Chọn đúng đá nhưng thiếu số lượng.
4. Chọn đủ đá nhưng thiếu vàng.
5. Nâng thành công ép tỉ lệ test về 100%.
6. Kiểm tra ba stat thành 11 nhưng HSD vẫn 30.
7. Kiểm tra có option Cấp 1.
8. Tháo/mặc item và kiểm tra chỉ số nhân vật.
9. Đăng xuất/đăng nhập và kiểm tra dữ liệu còn nguyên.
10. Ép tỉ lệ test về 0% tại cấp an toàn.
11. Ép tỉ lệ test về 0% tại cấp tụt.
12. Lặp lại với bảo hộ.
13. Lặp lại với Cỏ 3 lá.
14. Lặp lại với Cỏ 4 lá.
15. Double click nút xác nhận.
16. Di chuyển nguyên liệu sau preview rồi gửi xác nhận cũ.
17. Kiểm tra log audit.

Sau đó test lại với tỉ lệ production.

---

## 29. Ma trận test tối thiểu

| Nhóm | Không cỏ | Cỏ 3 lá | Cỏ 4 lá | Không bảo hộ | Có bảo hộ | Relog |
|---|---:|---:|---:|---:|---:|---:|
| Cải trang | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Ván bay | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Đeo lưng | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Pet | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |

Ngoài ra phải test:

- Item vĩnh viễn.
- Item có hạn sử dụng nếu chức năng cho phép.
- Item chỉ có một option.
- Item có nhiều option.
- Item không có option hợp lệ.
- Item có option đã đạt cap.

---

## 30. Lỗi thường gặp và nguyên nhân

### 30.1. Chọn pet nhưng hệ thống nhận là nguyên liệu

Nguyên nhân:

- Danh sách `appearance.followPet.templateIds` thiếu ID.
- Logic phân loại vai trò chạy sai thứ tự.

### 30.2. Đá bảo vệ bị nhận là pet

Nguyên nhân:

- Dùng `type == 27` để nhận diện pet.

Cách sửa:

- Dùng allowlist pet hoặc `FollowPetRegistry`.

### 30.3. Hào quang bị nhận là đeo lưng

Nguyên nhân:

- Dùng `type == 11` mà không gọi `InventoryService.isAuraItem()`.

### 30.4. Hạn sử dụng tăng theo cấp

Nguyên nhân:

- Tăng tất cả option có param hoặc tất cả tên option có ký tự `#`.

Cách sửa:

- Chỉ tăng option trong allowlist.

### 30.5. Preview đúng nhưng kết quả dùng tỉ lệ cũ

Nguyên nhân:

- `startCombine()` dùng `player.combineNew.ratioCombine` đã lưu trước đó.

Cách sửa:

- Tạo lại `UpgradePlan` tại thời điểm xác nhận.

### 30.6. Bảo hộ mất khi thành công

Nguyên nhân:

- Trừ bảo hộ dựa trên việc item có mặt trong danh sách thay vì kết quả `preventedDowngrade`.

### 30.7. SHOP không mở sau khi thêm nguyên liệu

Nguyên nhân phổ biến:

- Client đang dùng item template cache cũ.

Cách sửa:

- Tăng `DataGame.vsItem`.
- Build và deploy lại.
- Thoát hẳn client rồi đăng nhập lại.

### 30.8. Item mới trả về sai template

Nguyên nhân:

- Tạo khoảng trống ID item.
- Database live và SQL dump không đồng bộ.

### 30.9. Option hiển thị số âm hoặc sai lớn

Nguyên nhân:

- Param vượt giới hạn short.
- Không áp cap.
- Trừ step khi item chưa từng được cộng step tương ứng.

---

## 31. Cân bằng kinh tế

Trước production cần chốt:

- Cấp tối đa.
- Tỉ lệ theo cấp.
- Số đá theo cấp.
- Giá vàng/ngọc theo cấp.
- Các cấp có nguy cơ tụt.
- Mức bonus của Cỏ 3 lá và Cỏ 4 lá.
- Tỉ lệ tối đa sau may mắn.
- Bảo hộ tiêu hao theo lần thử hay chỉ khi ngăn tụt.
- Nguồn cung từng loại đá.
- Có cho giao dịch/ký gửi nguyên liệu không.
- Có cho nâng item HSD không.
- Option nào được tăng và cap từng option.

Khuyến nghị rollout:

1. Cấp tối đa ban đầu thấp, ví dụ +5.
2. Chỉ cho tăng các option phổ biến `50, 77, 103`.
3. Chỉ mở một nhóm item test trước.
4. Theo dõi log và kinh tế vài ngày.
5. Sau đó mới mở +10, thêm option hoặc thêm nhóm item.

Không nên mở toàn bộ bốn nhóm và tất cả option cùng lúc trên production.

---

## 32. Kế hoạch triển khai theo giai đoạn

### Giai đoạn 1: lõi kỹ thuật

- Tạo `NangCapNgoaiTrang`.
- Tạo category detector.
- Dùng option 72 lưu cấp.
- Chỉ hỗ trợ target + đá.
- Chỉ bật cải trang.
- Tỉ lệ test 100%.
- Chỉ tăng option `50, 77, 103`.

### Giai đoạn 2: thất bại và bảo hộ

- Thêm mốc tụt cấp.
- Dùng lại item 987.
- Thêm option 209 khi tụt.
- Hoàn thiện quy tắc tiêu hao bảo hộ.

### Giai đoạn 3: may mắn

- Tạo Cỏ 3 lá và Cỏ 4 lá.
- Thêm bonus tỉ lệ và rate cap.
- Thêm preview tỉ lệ cơ bản/bonus/tỉ lệ cuối.

### Giai đoạn 4: mở các category còn lại

- Ván bay.
- Đeo lưng.
- Pet theo allowlist.
- Test từng category độc lập.

### Giai đoạn 5: admin và kinh tế

- Thêm item vào SHOP/drop/giftcode.
- Chuẩn bị icon x1-x4.
- Tăng cache version.
- Bổ sung cấu hình vào tab Combine admin nếu cần chỉnh bằng giao diện.

### Giai đoạn 6: production

- Build đầy đủ source.
- Kiểm tra JAR.
- Không restart khi còn người chơi online.
- Backup database và JAR hiện tại.
- Deploy theo quy trình `tools/server_control.ps1` của repository.
- Theo dõi log khởi động và log nâng cấp.

---

## 33. Build và deploy

Sau khi sửa code:

1. Kiểm tra `git status --short` và không ghi đè thay đổi không liên quan.
2. Compile toàn bộ source liên quan.
3. Kiểm tra phiên bản `java`, `javac`, `jar`, `javap` tương thích.
4. Nếu server đang chạy, tạo JAR pending theo quy trình hiện có thay vì ghi đè JAR runtime tùy tiện.
5. Kiểm tra bytecode của `NangCapNgoaiTrang` và `CombineService` trong JAR.
6. Khởi động server khi an toàn.
7. Chờ log tới trạng thái `Server initialized`.
8. Kiểm tra `server-error.log`.
9. Kiểm tra port server đang listen.
10. Thoát hoàn toàn client và đăng nhập lại nếu đã đổi item/cache/icon.

Không coi việc source compile thành công là hoàn tất nếu `20.jar` runtime chưa chứa code mới.

---

## 34. Phương án rollback

Trước production:

- Backup `20.jar`.
- Backup `combine.properties`.
- Backup các bảng item/shop đã sửa.
- Lưu danh sách item ID và icon ID mới.

Nếu chức năng lỗi:

1. Đặt `appearance.upgrade.enabled=false` để chặn lần thử mới nếu code hỗ trợ cờ này.
2. Gỡ nút menu hoặc để handler thông báo bảo trì.
3. Không xóa item người chơi đã nâng.
4. Nếu cần rollback JAR, dùng bản backup.
5. Giữ nguyên option 72 trên item; code cũ sẽ bỏ qua option này đối với ngoại trang.
6. Phân tích audit log trước khi hoàn trả nguyên liệu.

Không chạy SQL xóa hàng loạt option 72 nếu chưa phân biệt được option cấp của trang bị thường, bông tai và ngoại trang.

---

## 35. Checklist trước khi bắt đầu code

- [ ] Chốt tên chức năng hiển thị.
- [ ] Chốt cấp tối đa.
- [ ] Chốt bốn item đá nâng cấp.
- [ ] Chốt dùng lại ID 987 hay tạo Khiên bảo hộ mới.
- [ ] Chốt ID Cỏ 3 lá và Cỏ 4 lá.
- [ ] Chốt bonus may mắn.
- [ ] Chốt mốc tụt cấp.
- [ ] Chốt quy tắc tiêu hao bảo hộ.
- [ ] Chốt cho phép hay cấm item HSD.
- [ ] Chốt allowlist option được tăng.
- [ ] Chốt cap option.
- [ ] Xuất danh sách pet item ID hiện có.
- [ ] Kiểm tra item aura type 11 phải bị loại khỏi đeo lưng.
- [ ] Kiểm tra Combine type ID chưa sử dụng.
- [ ] Kiểm tra item template ID ở SQL và database live.
- [ ] Chuẩn bị icon ID không trùng.
- [ ] Chọn SHOP hoặc nguồn phát nguyên liệu.

---

## 36. Checklist triển khai source

- [ ] Tạo `AppearanceUpgradeCategory` hoặc policy tương đương.
- [ ] Tạo `NangCapNgoaiTrang`.
- [ ] Viết selection parser không phụ thuộc thứ tự.
- [ ] Viết `UpgradePlan` dùng chung cho preview và execution.
- [ ] Viết đọc option 72.
- [ ] Viết option step allowlist.
- [ ] Viết option cap.
- [ ] Viết tụt cấp và option 209.
- [ ] Viết luck bonus và rate cap.
- [ ] Viết protection consume mode.
- [ ] Revalidate item trong bag bằng identity.
- [ ] Chống double click.
- [ ] Thêm audit log.
- [ ] Thêm dispatch vào `CombineService`.
- [ ] Thêm menu Bà Hạt Mít ở mọi map liên quan.
- [ ] Bổ sung cấu hình `combine.properties`.
- [ ] Nếu cần, mở rộng `CombineConfig` để đọc map `id:value`.

---

## 37. Checklist dữ liệu và client

- [ ] Thêm item template theo ID liên tục.
- [ ] Đồng bộ SQL dump và database live.
- [ ] Thêm item vào SHOP/drop/giftcode theo thiết kế.
- [ ] Chuẩn bị icon x1.
- [ ] Chuẩn bị icon x2.
- [ ] Chuẩn bị icon x3.
- [ ] Chuẩn bị icon x4.
- [ ] Kiểm tra alpha và kích thước icon.
- [ ] Tăng `DataGame.vsItem` nếu thêm template.
- [ ] Tăng small icon version nếu ghi đè icon cũ.
- [ ] Build JAR.
- [ ] Kiểm tra client tải lại dữ liệu sau reconnect.

---

## 38. Checklist nghiệm thu

- [ ] Đúng đá cho đúng category.
- [ ] Sai đá không tiêu hao vật phẩm.
- [ ] Thiếu tiền không tiêu hao vật phẩm.
- [ ] Thành công tăng đúng một cấp.
- [ ] Thành công chỉ tăng option allowlist.
- [ ] Option thời hạn không đổi.
- [ ] Thất bại cấp an toàn không tụt.
- [ ] Thất bại cấp nguy hiểm tụt đúng một cấp.
- [ ] Bảo hộ ngăn tụt đúng quy tắc.
- [ ] Cỏ tăng đúng tỉ lệ hiển thị và tỉ lệ roll.
- [ ] Không dùng đồng thời hai loại cỏ nếu bị cấm.
- [ ] Pet không nhận nhầm nguyên liệu type 27.
- [ ] Hào quang không nhận nhầm thành đeo lưng.
- [ ] Relog vẫn giữ cấp và option.
- [ ] Mặc item sau nâng có cộng đúng chỉ số.
- [ ] Double click không nâng hai lần.
- [ ] SHOP vẫn mở và hiện item mới.
- [ ] Icon hiển thị đúng ở x1-x4.
- [ ] Runtime JAR chứa code mới.
- [ ] Server khởi động không có lỗi.

---

## 39. Các quyết định cần điền khi triển khai thật

Sao chép bảng này và điền trước khi bắt đầu sửa code:

| Quyết định | Giá trị đã chốt |
|---|---|
| Combine type ID | Chưa chốt |
| Cấp tối đa | Chưa chốt |
| ID đá cải trang | Chưa chốt |
| ID đá ván bay | Chưa chốt |
| ID đá đeo lưng | Chưa chốt |
| ID đá pet | Chưa chốt |
| ID Cỏ 3 lá | Chưa chốt |
| ID Cỏ 4 lá | Chưa chốt |
| ID bảo hộ | `987` hoặc item mới |
| Bonus Cỏ 3 lá | Chưa chốt |
| Bonus Cỏ 4 lá | Chưa chốt |
| Mốc tụt cấp | Chưa chốt |
| Item HSD | Cho phép / Không cho phép |
| Bảo hộ tiêu hao | Mọi lần thử / Chỉ khi ngăn tụt |
| Option allowlist | Chưa chốt |
| Option cap | Chưa chốt |
| SHOP/nguồn nguyên liệu | Chưa chốt |
| Danh sách pet ID | Chưa chốt |

---

## 40. Tài liệu liên quan trong repository

- `admin_data_menu/help/HUONG_DAN_COMBINE_ADMIN.md`
- `admin_data_menu/help/HUONG_DAN_OPTION_MAC_DINH_VAT_PHAM.md`
- `admin_data_menu/help/HUONG_DAN_CHINH_SUA_ITEM_CAI_TRANG.md`
- `admin_data_menu/help/HUONG_DAN_ITEM_HAO_QUANG.md`

Khi triển khai thật, nên đọc lại các tài liệu trên để đồng bộ với cơ chế Combine, option mặc định, item cải trang và slot hào quang hiện tại.

---

## 41. Tóm tắt phương án khuyến nghị

Phương án nên chọn cho phiên bản đầu:

1. Một Combine type duy nhất: `NANG_CAP_NGOAI_TRANG`.
2. Một class xử lý chung: `NangCapNgoaiTrang`.
3. Tự nhận diện category từ target.
4. Cải trang dùng type 5.
5. Mount dùng type 23/24.
6. Đeo lưng dùng type 11 nhưng loại aura.
7. Pet dùng allowlist ID, tuyệt đối không dùng toàn bộ type 27.
8. Dùng option 72 lưu cấp.
9. Chỉ tăng option trong allowlist.
10. Mỗi option phần trăm hợp lệ tăng param 1 mỗi cấp.
11. Dùng bước tăng cố định để tụt cấp hoàn tác chính xác.
12. Cỏ may mắn cộng điểm phần trăm vào tỉ lệ và luôn bị tiêu hao sau lần thử.
13. Dùng lại item 987 làm bảo hộ nếu không cần kinh tế riêng.
14. Bảo hộ chỉ tiêu hao khi thực sự ngăn tụt cấp.
15. Preview và execution dùng chung một `UpgradePlan`.
16. Execution luôn revalidate item, chi phí, config và tỉ lệ.
17. Thêm cooldown, lock theo phiên Combine và audit log.
18. Thêm template theo ID liên tục, icon đủ x1-x4 và tăng cache version.
19. Rollout từng category, không bật toàn bộ ngay trên production.

