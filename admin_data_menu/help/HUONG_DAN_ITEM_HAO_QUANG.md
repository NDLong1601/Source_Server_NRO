# Ghi chú triển khai item hào quang nhân vật

Tài liệu này ghi lại phân tích và hướng triển khai để biến các asset `aura_*` trong `data/img_by_name` thành vật phẩm đổi hào quang nhân vật.

## Trạng thái triển khai hiện tại

Đã triển khai 64 item trang bị aura, có tên tuần tự từ **Hào Quang 1** đến **Hào Quang 64** (Item ID `2154..2217`). Mỗi item TYPE `11` lưu Aura Asset ID trong `item_template.part`; server ưu tiên aura đang mặc ở slot `12`, sau đó mới fallback về aura của radar card cũ.

Tab **Hào quang** trong `admin_data_menu` cho phép:

- Khởi tạo an toàn các item/metadata còn thiếu vào database.
- Đổi tên, mô tả, icon, Aura Asset ID và số frame của item đang chọn.
- Chuyển đến Giftcode hoặc Hộp quà để phát item.
- Chọn nhiều aura và thêm/bán hàng loạt trong một tab shop.

Các Aura Asset ID được chọn là các bộ có đủ cả hai layer `_0`, `_1` ở `x1..x4`. Những asset thiếu layer hoặc thiếu zoom không được tạo item.

## 1. Kết luận kỹ thuật

Có thể làm item đổi hào quang, nhưng source hiện tại chưa hỗ trợ chỉ bằng cách thêm PNG vào `data/img_by_name/x4`.

Server gửi hào quang cho client qua `pl.getAura()`. Hàm này hiện đã đọc item aura đang mặc trước, sau đó fallback về các thẻ radar hard-code cũ.

## 2. Các file source liên quan

### Packet gửi aura cho client

- `src/nro/models/services/Service.java`
  - Gửi `pl.getAura()` khi trả thông tin nhân vật lúc login.
  - Gửi `pl.getAura()` khi người chơi xuất hiện trong map.

- `src/nro/models/map/Zone.java`
  - Gửi `plInfo.getAura()` khi load player khác trong map.

### Logic aura hiện tại

- `src/nro/models/player/Player.java`
  - Hàm cần sửa: `getAura()`.
  - Hiện tại chỉ xét một số radar card:
    - `956`
    - `1791`
    - `1792`
    - `1793`
    - `1204`
    - `1142`
  - Nếu card đủ level thì lấy `RadarCard.AuraId`.

### Slot item aura đã có sẵn

- `src/nro/models/services/InventoryService.java`
  - Có sẵn:
    - `PLAYER_AURA_SLOT = 12`
    - `PLAYER_TITLE_SLOT = 13`
  - Hàm `isAuraItem()` hiện chỉ nhận item `1230`.
  - TYPE `11` nếu là aura item sẽ được mặc vào slot 12.

### Asset aura

- `data/img_by_name/x1`
- `data/img_by_name/x2`
- `data/img_by_name/x3`
- `data/img_by_name/x4`

File aura có dạng:

```text
aura_<auraId>_0.png
aura_<auraId>_1.png
```

Ví dụ aura ID `3`:

```text
data/img_by_name/x4/aura_3_0.png
data/img_by_name/x4/aura_3_1.png
```

### Bảng dữ liệu liên quan

- `sql/team2026.sql`
  - `item_template`: khai báo item.
  - `img_by_name`: khai báo tên ảnh và số frame.
  - `radar`: có cột `aura_id`, dùng cho cơ chế thẻ radar.
  - `player.items_body`: lưu item đang mặc, bao gồm slot aura nếu đã trang bị.

## 3. Item hào quang hiện có

Trong `item_template` đã có item:

```text
id: 1230
type: 11
name: Hào Quang Rực Rỡ
icon_id: 11239
part: 205
```

Item legacy `1230` vẫn được giữ tương thích và ánh xạ đến asset `3`. Dải item mới là `2154..2217` (Hào Quang 1..64), TYPE `11`, trong đó `part` là Aura Asset ID.

Source nhận item `1230` và dải item mới là aura item:

```java
return item.template.id == 1230
    || (item.template.id >= 2154 && item.template.id <= 2217);
```

`Player.getAura()` hiện đọc slot 12, kiểm tra metadata `aura_<id>_0` và `aura_<id>_1`, rồi trả `template.part` làm Aura Asset ID. Nếu dữ liệu không hợp lệ, server fallback về radar/card cũ.

## 4. Cách triển khai đang dùng

Nên triển khai theo hướng item trang bị aura:

1. Tạo item TYPE `11` cho từng hào quang.
2. Khi dùng item, item được mặc vào `PLAYER_AURA_SLOT = 12`.
3. Sửa `Player.getAura()` để ưu tiên đọc item đang mặc ở slot 12.
4. Aura ID được lưu trong `item_template.part` của dải ID chuyên dụng `2154..2217`.
5. Nếu không có item aura đang mặc, fallback về cơ chế radar/card cũ.

## 5. Cách lưu auraId trên item

Có 2 cách khả thi.

### Cách A: dùng option riêng (phương án mở rộng, chưa dùng)

Khuyến nghị dùng cách này.

Ví dụ tạo một option template mới:

```text
id: tùy chọn, ví dụ 250
name: Aura ID #
```

Item aura sẽ có option:

```text
option 250, param = auraId
```

Ưu điểm:

- Không lạm dụng `template.part`.
- Một template item có thể linh hoạt đổi aura bằng option.
- Dễ mở rộng thêm item đổi aura theo thời hạn, random, nâng cấp, v.v.

Nhược điểm:

- Cần thêm option template nếu chưa có option phù hợp.

### Cách B: dùng `item.template.part` (đang dùng)

Ví dụ:

```text
item_template.part = auraId
```

Ưu điểm:

- Dễ cấu hình.
- Không cần thêm item option.

Lưu ý: chỉ dùng `part` cho dải item aura chuyên dụng `2154..2217`; không áp dụng quy ước này cho TYPE `11` khác.

## 6. Logic `Player.getAura()` đề xuất

Ý tưởng:

```java
public byte getAura() {
    byte auraFromItem = getAuraFromEquippedItem();
    if (auraFromItem >= 0) {
        return auraFromItem;
    }

    return getAuraFromRadarCard();
}
```

Nếu dùng option riêng:

```java
private static final int OPTION_AURA_ID = 250;

private byte getAuraFromEquippedItem() {
    if (!isPl() || this.inventory == null || this.inventory.itemsBody == null) {
        return -1;
    }
    if (this.inventory.itemsBody.size() <= InventoryService.PLAYER_AURA_SLOT) {
        return -1;
    }
    Item item = this.inventory.itemsBody.get(InventoryService.PLAYER_AURA_SLOT);
    if (item == null || !item.isNotNullItem()) {
        return -1;
    }
    for (Item.ItemOption io : item.itemOptions) {
        if (io.optionTemplate.id == OPTION_AURA_ID) {
            return (byte) io.param;
        }
    }
    return -1;
}
```

Lưu ý khi thêm code thật:

- Cần import `InventoryService` nếu dùng hằng `PLAYER_AURA_SLOT`.
- Cần kiểm tra kiểu `Item` import đúng package.
- Nên giới hạn `auraId` trong khoảng hợp lệ, ví dụ `0..255` hoặc theo asset hiện có.

## 7. Sửa `InventoryService.isAuraItem()`

Hiện tại chỉ nhận item `1230`.

Nếu tạo nhiều item aura, nên đổi sang một trong các cách:

### Theo danh sách ID

```java
public static boolean isAuraItem(Item item) {
    if (item == null || !item.isNotNullItem()) {
        return false;
    }
    return switch (item.template.id) {
        case 1230, 2100, 2101, 2102 -> true;
        default -> false;
    };
}
```

### Theo option aura

```java
public static boolean isAuraItem(Item item) {
    if (item == null || !item.isNotNullItem()) {
        return false;
    }
    if (item.template.type != 11) {
        return false;
    }
    for (Item.ItemOption io : item.itemOptions) {
        if (io.optionTemplate.id == OPTION_AURA_ID) {
            return true;
        }
    }
    return item.template.id == 1230;
}
```

Nếu dùng option, nên đặt hằng `OPTION_AURA_ID` ở nơi dùng chung, tránh khai báo trùng nhiều file.

## 8. Cập nhật tức thời sau khi mặc/tháo item

Hiện `itemBagToBody()` gọi:

```java
sendItemBags(player);
sendItemBody(player);
Service.gI().point(player);
Service.gI().Send_Caitrang(player);
```

`Send_Caitrang()` chỉ gửi head/body/leg, không gửi aura.

Sau khi mặc/tháo aura, `itemBagToBody()` gọi lại flow load nhân vật trong zone để gửi aura mới ngay cho bản thân và người chơi xung quanh.

Các hướng xử lý:

Không cần sửa format `Message(-90)`; flow load player đang có đã truyền `getAura()` đúng vị trí.

## 9. Thêm asset aura mới

Để aura ID mới hoạt động:

1. Chuẩn bị file cho đủ 4 zoom:

```text
data/img_by_name/x1/aura_<id>_0.png
data/img_by_name/x1/aura_<id>_1.png
data/img_by_name/x2/aura_<id>_0.png
data/img_by_name/x2/aura_<id>_1.png
data/img_by_name/x3/aura_<id>_0.png
data/img_by_name/x3/aura_<id>_1.png
data/img_by_name/x4/aura_<id>_0.png
data/img_by_name/x4/aura_<id>_1.png
```

2. Thêm row vào bảng `img_by_name`:

```sql
INSERT INTO img_by_name (id, name, n_frame)
VALUES
  (<new_id_1>, 'aura_<id>_0', <n_frame_0>),
  (<new_id_2>, 'aura_<id>_1', <n_frame_1>);
```

3. Restart server để `Manager.IMAGES_BY_NAME` load lại.

4. Bắt client thoát hẳn và vào lại nếu bị cache asset.

## 10. Ví dụ item aura theo option

Ví dụ tạo item `Hào Quang Xanh`:

```sql
INSERT INTO item_template
(`id`, `TYPE`, `gender`, `NAME`, `description`, `level`, `icon_id`, `part`, `is_up_to_up`, `power_require`, `gold`, `gem`, `head`, `body`, `leg`)
VALUES
(2100, 11, 3, 'Hào Quang Xanh', 'Trang bị để đổi hào quang xanh quanh nhân vật', 0, 19070, -1, 0, 0, 0, 0, -1, -1, -1);
```

Sau đó item sinh ra trong bag/shop/giftcode cần có option:

```text
option OPTION_AURA_ID, param = auraId
```

Ví dụ:

```text
option 250, param 3
```

## 11. Checklist triển khai

- [x] Chọn 64 Aura Asset ID có đủ hai layer trong `x1..x4`.
- [x] Thêm metadata `img_by_name` và migration item liên tục `2154..2217`.
- [x] Dùng `template.part` trong dải item chuyên dụng.
- [x] Sửa `Player.getAura()` và `InventoryService.isAuraItem()`.
- [x] Tạo tab admin để khởi tạo/cấu hình/phát/bán.
- [ ] Khởi tạo dữ liệu trong database đang chạy, build lại `20.jar`, restart server và test client.

## 12. Rủi ro cần chú ý

- `Manager.ITEM_TEMPLATES` được load dạng list và `ItemService.getTemplate(id)` index trực tiếp, tránh tạo khoảng trống ID item.
- Nếu thêm item template mới, cần tăng version item/cache nếu client không thấy item hoặc shop không mở.
- Nếu sửa PNG/icon đã tồn tại, cần xử lý cache client.
- Không sửa format packet nếu chưa biết client đọc packet đó thế nào.
- `data/img_by_name/x4` một mình không đủ; phải đủ các zoom mà client dùng.

## 13. Ghi chú kiểm thử legacy (không dùng cho dải item mới)

1. Dùng lại item `1230` để thử.
2. Sửa `Player.getAura()` đọc slot `PLAYER_AURA_SLOT`.
3. Tạm lấy `auraId` từ `item.template.part` hoặc hard-code item `1230 -> auraId`.
4. Build và test nhanh.
5. Nếu chạy ổn, mới mở rộng sang nhiều item aura và option riêng.

Ví dụ test nhanh:

```java
if (this.inventory != null
        && this.inventory.itemsBody.size() > InventoryService.PLAYER_AURA_SLOT
        && this.inventory.itemsBody.get(InventoryService.PLAYER_AURA_SLOT).isNotNullItem()) {
    Item auraItem = this.inventory.itemsBody.get(InventoryService.PLAYER_AURA_SLOT);
    if (auraItem.template.id == 1230) {
        return 3; // test aura_3
    }
}
```

Sau khi xác nhận client render được, refactor lại thành cấu hình option sạch hơn.
