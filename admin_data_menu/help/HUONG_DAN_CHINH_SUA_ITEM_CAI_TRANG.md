# Hướng Dẫn Chỉnh Sửa Item, Cải Trang Và Chỉ Số

Tài liệu này tóm tắt các phần quan trọng trong source để bạn có thể sửa item, cải trang, chỉ số và cách hoạt động của một số chức năng trong server.

## 1. Tổng Quan Luồng Dữ Liệu

Khi server khởi động bằng `run.bat`, file `20.jar` sẽ đọc cấu hình database trong `Config.properties`, sau đó load dữ liệu từ MySQL.

Luồng chính:

```text
Config.properties
  -> database team2026
  -> Manager.loadDatabase()
  -> item_template, item_option_template, shop, item_shop, item_shop_option
  -> Player / Item / NPoint tính chỉ số khi mặc đồ
```

Lưu ý rất quan trọng:

- Nếu sửa file `.sql` trong thư mục `sql/`, đó chỉ là sửa file dump. Server không tự đọc lại file `.sql`.
- Muốn thay đổi có tác dụng khi server chạy, cần sửa trực tiếp trong MySQL database `team2026`, hoặc import lại file SQL vào database.
- Nếu sửa file `.java`, cần build lại JAR rồi chạy lại `run.bat`.

## 2. Các File Và Bảng Quan Trọng

### Cấu Hình Database

File:

```text
Config.properties
```

Phần cần chú ý:

```properties
database.host=localhost
database.port=3307
database.name=team2026
database.user=root
database.pass=
```

Máy bạn đang dùng MySQL cổng `3307`, vì vậy `database.port` phải là `3307`.

### Bảng `item_template`

Nằm trong:

```text
sql/database team2026.sql
```

Bảng này chứa thông tin gốc của item/cải trang:

```sql
CREATE TABLE `item_template` (
  `id` int(11) NOT NULL,
  `TYPE` int(11) NOT NULL,
  `gender` smallint(6) NOT NULL,
  `NAME` varchar(255) NOT NULL,
  `description` varchar(75) DEFAULT NULL,
  `level` int(11) NOT NULL DEFAULT 0,
  `icon_id` int(11) NOT NULL,
  `part` int(11) NOT NULL,
  `is_up_to_up` tinyint(1) NOT NULL,
  `power_require` int(11) NOT NULL,
  `gold` int(11) NOT NULL DEFAULT 0,
  `gem` int(11) NOT NULL DEFAULT 0,
  `head` int(11) NOT NULL DEFAULT -1,
  `body` int(11) NOT NULL DEFAULT -1,
  `leg` int(11) NOT NULL DEFAULT -1
);
```

Ý nghĩa các cột hay sửa:

```text
id             ID item
TYPE           Loại item, đồng thời là vị trí mặc trên người
gender         0 Trái Đất, 1 Namec, 2 Xayda, 3 tất cả
NAME           Tên hiển thị
description    Mô tả item
level          Cấp item
icon_id        Icon trong hành trang/shop
part           Part hiển thị trên người với đồ thường
power_require  Yêu cầu sức mạnh
gold           Giá vàng
gem            Giá ngọc
head/body/leg  Part hiển thị của cải trang
```

`TYPE` thường gặp:

```text
0  = Áo
1  = Quần
2  = Găng
3  = Giày
4  = Rada / nhẫn theo source này
5  = Cải trang
32 = Phụ kiện / item đặc biệt mặc ở slot riêng
```

### Bảng `item_option_template`

Bảng này chứa tên option:

```sql
CREATE TABLE `item_option_template` (
  `id` int(11) NOT NULL,
  `NAME` varchar(255) NOT NULL
);
```

Ví dụ:

```sql
(50, 'Sức đánh+#%')
(77, 'HP+#%')
(103, 'KI+#%')
```

Lưu ý: thêm option vào bảng này chỉ giúp server/client biết tên hiển thị. Muốn option có tác dụng thật, source Java phải xử lý option đó trong `NPoint.java`.

### Bảng `item_shop`

Bảng này quy định item bán trong shop:

```text
item_shop.id        ID dòng item trong shop
item_shop.tab_id    Tab shop
item_shop.temp_id   ID item trong item_template
item_shop.cost      Giá
item_shop.type_sell Loại tiền / cách bán
```

### Bảng `item_shop_option`

Bảng này là nơi thêm chỉ số cho item trong shop:

```sql
CREATE TABLE `item_shop_option` (
  `id` int(11) NOT NULL,
  `item_shop_id` int(11) NOT NULL,
  `option_id` int(11) NOT NULL,
  `param` int(11) NOT NULL
);
```

Nếu item được mua từ shop, source load option tại:

```text
src/nro/models/database/ShopDAO.java
```

Hàm liên quan:

```java
private static void loadItemShopOption(Connection con, ItemShop itemShop)
```

## 3. Source Code Liên Quan Đến Item

### `Item.java`

File:

```text
src/nro/models/item/Item.java
```

Class này đại diện cho một item thực tế trong game.

Các thuộc tính quan trọng:

```java
public ItemTemplate template;
public int quantity;
public List<ItemOption> itemOptions;
public long createTime;
```

Mỗi option của item có dạng:

```java
new Item.ItemOption(optionId, param)
```

Ví dụ:

```java
item.itemOptions.add(new Item.ItemOption(50, 25));
```

Nghĩa là item có option ID `50`, tham số `25`. Nếu option 50 là `Sức đánh +#%`, item sẽ hiển thị và có tác dụng là `Sức đánh +25%`.

### `ItemService.java`

File:

```text
src/nro/models/services/ItemService.java
```

Đây là nơi tạo item, random item, random option và tạo item từ shop/map.

Các hàm hay gặp:

```java
public Item createNewItem(short tempId)
public Item createNewItem(short tempId, int quantity)
public Item createItemFromItemShop(ItemShop itemShop)
public Item createItemFromItemMap(ItemMap itemMap)
public List<Item.ItemOption> getListOptionItemShop(short id)
```

Nếu thấy code kiểu này:

```java
Item item = ItemService.gI().createNewItem((short) 584);
item.itemOptions.add(new ItemOption(50, 24));
item.itemOptions.add(new ItemOption(77, 24));
```

Thì item đó được tạo trực tiếp bằng code. Muốn sửa chỉ số, sửa tại chính file Java đó.

### `NPoint.java`

File:

```text
src/nro/models/player/NPoint.java
```

Đây là nơi tính chỉ số nhân vật khi mặc item.

Hàm cần quan tâm:

```java
private void addOption(ItemOption io)
```

Nếu một option không có trong `switch (io.optionTemplate.id)`, option đó có thể vẫn hiện trên item nhưng không cộng chỉ số thật.

Ví dụ:

```java
case 50:
    this.tlDame.add(io.param);
    break;
case 77:
    this.tlHp.add(io.param);
    break;
case 103:
    this.tlMp.add(io.param);
    break;
```

### `InventoryService.java`

File:

```text
src/nro/models/services/InventoryService.java
```

Đây là nơi xử lý mặc item từ túi/ruồng lên người.

Đoạn liên quan:

```java
if (item.template.type >= 0 && item.template.type <= 5 || item.template.type == 32) {
    Item itemBody = player.inventory.itemsBody.get(item.template.type == 32 ? 6 : item.template.type);
}
```

Nghĩa là:

```text
TYPE 0  -> slot áo
TYPE 1  -> slot quần
TYPE 2  -> slot găng
TYPE 3  -> slot giày
TYPE 4  -> slot rada/nhẫn
TYPE 5  -> slot cải trang
TYPE 32 -> slot đặc biệt, source map vào index 6
```

Sau khi mặc, source gọi:

```java
Service.gI().point(player);
Service.gI().Send_Caitrang(player);
```

`point(player)` gửi lại chỉ số, `Send_Caitrang(player)` gửi lại hình dáng cải trang.

## 4. Danh Sách Option Thường Dùng

Những option dưới đây đã được xử lý trong `NPoint.java`, nên có tác dụng thật khi item được mặc.

```text
0   Tấn công + số cố định
2   HP, KI + #000
3   Vô hiệu và biến sát thương chưởng thành KI
5   Sức đánh chí mạng + %
6   HP + số cố định
7   KI + số cố định
8   Hút % HP, KI xung quanh mỗi 5 giây
14  Chí mạng + %
16  Tốc độ di chuyển + %
18  Chính xác + %
19  Tấn công + % khi đánh quái
21  Yêu cầu sức mạnh # tỉ
22  HP + #K
23  KI + #K
24  Làm chậm
25  Tàng hình
26  Hóa đá
27  Hồi HP / 30s
28  Hồi KI / 30s
30  Không thể giao dịch
33  Dịch chuyển tức thời
47  Giáp + số cố định
48  HP, KI + số cố định
49  Tấn công + %
50  Sức đánh + %
77  HP + %
80  HP + % / 30s
81  KI + % / 30s
88  Cộng % EXP / tiềm năng sức mạnh khi đánh quái
94  Giáp + %
95  Biến % tấn công thành HP
96  Biến % tấn công thành KI
97  Phản % sát thương
98  Xuyên giáp chưởng
99  Xuyên giáp cận chiến
100 Cộng % vàng từ quái
101 Cộng % tiềm năng, sức mạnh
103 KI + %
104 Biến % tấn công quái thành HP
105 Vô hình khi không đánh quái và boss
106 Không ảnh hưởng bởi cái lạnh
108 Né đòn + %
111 Né đòn + %
116 Kháng Thái Dương Hạ San
117 Đẹp + % sức đánh cho mình và người xung quanh
147 Sức đánh + %
156 Giảm 50% SD/HP/KI và cộng % TN/SM/vàng
159 X chưởng
160 Tiềm năng sức mạnh pet
162 Cute hồi % KI/s
173 Phục hồi % HP/KI cho đồng đội
211 Set linh thú đánh bạc
226 Tương tự option 117 trong source
```

Option đặc biệt hay dùng:

```text
93 = Hạn sử dụng theo ngày
30 = Không thể giao dịch
```

## 5. Thêm Chỉ Số Cho Cải Trang Trong Shop

Ví dụ: thêm chỉ số cho cải trang item template ID `584`.

Bước 1: tìm item shop nào đang bán item `584`.

```sql
SELECT id, tab_id, temp_id, cost
FROM item_shop
WHERE temp_id = 584;
```

Giả sử kết quả là:

```text
id = 467
```

Bước 2: thêm option vào `item_shop_option`.

```sql
INSERT INTO item_shop_option (item_shop_id, option_id, param)
VALUES
(467, 50, 24),
(467, 77, 24),
(467, 103, 24),
(467, 117, 15),
(467, 93, 30);
```

Ý nghĩa:

```text
50, 24  -> Sức đánh +24%
77, 24  -> HP +24%
103, 24 -> KI +24%
117, 15 -> Đẹp +15% sức đánh cho mình và người xung quanh
93, 30  -> Hạn sử dụng 30 ngày
```

Nếu muốn vĩnh viễn, không thêm option `93`.

Bước 3: khởi động lại server.

Server load shop lúc khởi động, nên sửa shop option xong nên restart server.

## 6. Sửa Chỉ Số Item Được Tạo Bằng Code

Nhiều item không lấy option từ shop, mà được tạo trực tiếp trong Java.

Ví dụ trong:

```text
src/nro/models/npc_list/ToriBot.java
```

Có đoạn:

```java
Item CaiTrang = ItemService.gI().createNewItem((short) 584);
CaiTrang.itemOptions.add(new ItemOption(50, 24));
CaiTrang.itemOptions.add(new ItemOption(77, 24));
CaiTrang.itemOptions.add(new ItemOption(117, 15));
CaiTrang.itemOptions.add(new ItemOption(93, 30));
```

Muốn tăng thành:

```text
Sức đánh +35%
HP +35%
KI +35%
Đẹp +20%
Vĩnh viễn
```

Sửa thành:

```java
Item CaiTrang = ItemService.gI().createNewItem((short) 584);
CaiTrang.itemOptions.add(new ItemOption(50, 35));
CaiTrang.itemOptions.add(new ItemOption(77, 35));
CaiTrang.itemOptions.add(new ItemOption(103, 35));
CaiTrang.itemOptions.add(new ItemOption(117, 20));
```

Không thêm `93` thì item không có hạn sử dụng.

Sau khi sửa Java, cần build lại JAR.

## 7. Thêm Một Cải Trang Mới

Để thêm cải trang mới cần có 3 nhóm dữ liệu:

1. `item_template`: khai báo item.
2. `part`: dữ liệu hình ảnh part nếu part mới chưa tồn tại.
3. Option của item: qua `item_shop_option` hoặc qua Java.

Ví dụ thêm template:

```sql
INSERT INTO item_template
(id, TYPE, gender, NAME, description, level, icon_id, part, is_up_to_up, power_require, gold, gem, head, body, leg)
VALUES
(2100, 5, 3, 'Cải trang Test', 'Cải trang thành nhân vật Test', 1, 14000, -1, 0, 0, 0, 0, 1000, 1001, 1002);
```

Ý nghĩa:

```text
2100 = ID item mới
TYPE 5 = cải trang
gender 3 = ai cũng mặc được
icon_id 14000 = icon trong túi/shop
head/body/leg = part hiện trên nhân vật khi mặc
```

Nếu `head/body/leg` chưa có trong bảng `part`, client/server có thể không hiện đúng. Bạn cần dùng part đã có sẵn hoặc thêm dữ liệu part mới.

Thêm vào shop:

```sql
INSERT INTO item_shop (id, tab_id, temp_id, is_new, is_sell, type_sell, cost, icon_spec)
VALUES (9999, 52, 2100, 1, 1, 1, 300, 0);
```

Thêm option:

```sql
INSERT INTO item_shop_option (item_shop_id, option_id, param)
VALUES
(9999, 50, 20),
(9999, 77, 20),
(9999, 103, 20),
(9999, 30, 0);
```

Restart server để load lại shop/template.

## 8. Sửa Đồ Rơi Từ Quái, Boss, Hộp Quà

Tìm ID item hoặc tên hàm trong source:

```powershell
rg -n "createNewItem\\(\\(short\\) 584|new ItemOption\\(50|caitrang|randDoTL|DoThienSu" src
```

Một số vị trí hay sửa:

```text
src/nro/models/services/ItemService.java
  caitrang2011()
  caitrangChristmas()
  vatphamsk()
  randDoTL()
  randDoTLBoss()
  DoThienSu()

src/nro/models/npc_list/ToriBot.java
  Quà VIP

src/nro/models/boss/**
  Drop boss

src/nro/models/event/**
  Quà sự kiện
```

Ví dụ sửa đồ thiên sứ:

```java
public Item DoThienSu(int itemId, int gender) {
    Item dots = createItemSetKichHoat(itemId, 1);
    ...
    dots.itemOptions.add(new ItemOption(21, 30));
    dots.itemOptions.add(new ItemOption(30, 1));
    return dots;
}
```

Muốn đồ thiên sứ luôn có thêm HP +10%:

```java
dots.itemOptions.add(new ItemOption(77, 10));
```

Đặt trước `return dots;`.

## 9. Sửa Option Mới Để Có Tác Dụng Thật

Nếu bạn thêm option mới vào `item_option_template`, ví dụ:

```sql
INSERT INTO item_option_template (id, NAME)
VALUES (250, 'Kháng tất cả +#%');
```

Nếu chỉ thêm SQL, item có thể hiện dòng `Kháng tất cả +15%`, nhưng nhân vật chưa chắc được cộng chỉ số.

Cần vào:

```text
src/nro/models/player/NPoint.java
```

Tìm:

```java
private void addOption(ItemOption io)
```

Thêm case:

```java
case 250:
    this.khangTatCa += io.param;
    break;
```

Nhưng trước khi thêm, phải kiểm tra trong `NPoint.java` đã có biến nào phù hợp chưa. Nếu chưa có biến, cần thêm biến và sửa cả nơi tính damage/khống chế liên quan. Đây là dạng sửa logic, không chỉ là sửa item.

## 10. Sửa Giá, Tên, Icon, Hình Dáng Cải Trang

### Sửa tên/mô tả

```sql
UPDATE item_template
SET NAME = 'Cải trang Mới', description = 'Mô tả mới'
WHERE id = 584;
```

### Sửa icon

```sql
UPDATE item_template
SET icon_id = 14000
WHERE id = 584;
```

### Sửa hình dáng khi mặc cải trang

```sql
UPDATE item_template
SET head = 1000, body = 1001, leg = 1002
WHERE id = 584;
```

Sau đó restart server. Nếu part không tồn tại, hình có thể lỗi.

## 11. Sửa Yêu Cầu Sức Mạnh

Có 2 cách:

### Sửa trong `item_template.power_require`

```sql
UPDATE item_template
SET power_require = 1000000
WHERE id = 584;
```

### Sửa bằng option 21

Option `21` được source đọc như số tỉ sức mạnh:

```java
powerRequire = io.param * 1000000000L;
```

Ví dụ:

```sql
INSERT INTO item_shop_option (item_shop_id, option_id, param)
VALUES (467, 21, 80);
```

Nghĩa là yêu cầu `80 tỉ` sức mạnh.

## 12. Sửa Hạn Sử Dụng

Option `93` là hạn sử dụng theo ngày.

```java
new ItemOption(93, 30)
```

Nghĩa là hạn sử dụng 30 ngày.

Hàm xử lý hết hạn:

```text
src/nro/models/services/ItemService.java
```

Hàm:

```java
public boolean isOutOfDateTime(Item item)
```

Muốn item vĩnh viễn thì không thêm option `93`.

## 13. Thêm Item Vào Player Đang Có

Dữ liệu item của player được lưu trong bảng `player`, các cột:

```text
items_body
items_bag
items_box
```

Source đọc item player tại:

```text
src/nro/models/database/MrFinn.java
```

Source save item player tại:

```text
src/nro/models/database/PlayerDAO.java
```

Format mỗi item thường có dạng JSON array:

```json
[item_template_id, quantity, [[option_id, param], [option_id, param]], create_time]
```

Không nên sửa trực tiếp cột `items_body/items_bag` nếu chưa quen format, vì sai JSON có thể làm player không load được. Cách an toàn hơn là tạo item qua NPC/giftcode/shop/code.

## 14. Quy Trình Sửa An Toàn

1. Backup database trước khi sửa.
2. Xác định item ID trong `item_template`.
3. Xác định item đó đến từ shop, boss, NPC, event hay giftcode.
4. Nếu đến từ shop: sửa `item_shop_option`.
5. Nếu đến từ code: sửa file Java tạo item.
6. Nếu thêm option mới: kiểm tra `NPoint.java` có xử lý option đó chưa.
7. Restart server nếu sửa database.
8. Build lại JAR và restart server nếu sửa Java.
9. Test bằng tài khoản phụ trước khi cấp cho người chơi thật.

## 15. Lệnh Tìm Kiếm Nhanh

Tìm item ID trong source:

```powershell
rg -n "584" src
```

Tìm đoạn tạo item:

```powershell
rg -n "createNewItem|new ItemOption" src/nro/models
```

Tìm option ID:

```powershell
rg -n "case 50|case 77|case 103|optionTemplate.id == 50" src/nro/models
```

Tìm trong SQL:

```powershell
Select-String -Path "sql\database team2026.sql" -Pattern "584|Cải trang|item_shop_option"
```

## 16. Build Lại Source

Project này là NetBeans/Ant project, có file:

```text
build.xml
nbproject/project.properties
```

Main class:

```text
nro.models.server.ServerManager
```

Build bằng Ant nếu máy có Ant:

```powershell
ant clean jar
```

Hoặc build trong NetBeans.

Sau khi build, cần đảm bảo `run.bat` chạy đúng JAR mới. Hiện tại `run.bat` đang chạy:

```bat
java -server -Dfile.encoding=UTF-8 -jar 20.jar
```

Nếu NetBeans build ra:

```text
dist/NgocRongOnline.jar
```

Thì có 2 cách:

```bat
java -server -Dfile.encoding=UTF-8 -jar dist/NgocRongOnline.jar
```

Hoặc copy JAR mới thành `20.jar`.

## 17. Ví Dụ Hoàn Chỉnh

Mục tiêu: cải trang ID `584` có chỉ số:

```text
Sức đánh +30%
HP +30%
KI +30%
Chí mạng +10%
Không giao dịch
Vĩnh viễn
```

### Nếu là item trong shop

```sql
SELECT id FROM item_shop WHERE temp_id = 584;
```

Giả sử `item_shop.id = 467`:

```sql
DELETE FROM item_shop_option
WHERE item_shop_id = 467;

INSERT INTO item_shop_option (item_shop_id, option_id, param)
VALUES
(467, 50, 30),
(467, 77, 30),
(467, 103, 30),
(467, 14, 10),
(467, 30, 0);
```

Restart server.

### Nếu là item tạo trong Java

Sửa code:

```java
Item CaiTrang = ItemService.gI().createNewItem((short) 584);
CaiTrang.itemOptions.add(new ItemOption(50, 30));
CaiTrang.itemOptions.add(new ItemOption(77, 30));
CaiTrang.itemOptions.add(new ItemOption(103, 30));
CaiTrang.itemOptions.add(new ItemOption(14, 10));
CaiTrang.itemOptions.add(new ItemOption(30, 0));
```

Build lại JAR và restart server.

## 18. Lỗi Thường Gặp

### Sửa SQL file nhưng vào game không thay đổi

Nguyên nhân: server đọc MySQL, không đọc file `.sql`.

Cách xử lý: sửa trực tiếp trong MySQL database `team2026`, hoặc import lại SQL.

### Item hiện option nhưng không cộng chỉ số

Nguyên nhân: option chưa được xử lý trong `NPoint.java`.

Cách xử lý: thêm logic vào `NPoint.addOption(...)`.

### Sửa Java nhưng server không thay đổi

Nguyên nhân: `run.bat` đang chạy `20.jar`, còn source Java mới sửa chưa build vào JAR.

Cách xử lý: build lại và chạy đúng JAR mới.

### Cải trang bị lỗi hình

Nguyên nhân: `head/body/leg` trong `item_template` trỏ đến part không tồn tại hoặc client không có asset.

Cách xử lý: dùng part đã có sẵn, hoặc thêm dữ liệu vào bảng `part` và đảm bảo client có asset tương ứng.

### Không kết nối được MySQL

Kiểm tra:

```properties
database.port=3307
database.name=team2026
database.user=root
database.pass=
```

Nếu MySQL của bạn đổi port/user/pass thì sửa lại `Config.properties`.
