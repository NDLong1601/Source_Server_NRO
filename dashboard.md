# Dashboard thay doi

## 10/08/2026

### Phan tich item 555 - Ao Than Linh

- Da kiem tra item `555` trong database:
  - `item_template`: `555 - Ao Than Linh`, `type = 0`, `gender = 0`, `level = 13`.
  - `item_shop`: item shop ID `570`, temp ID `555`, gia `100`.
  - Option shop hien co:
    - `141`: dong set kich hoat Songoku/Kamejoko, `param = 0`.
    - `47`: giap cong thang, `param = 500`.
    - `94`: giam sat thuong theo %, `param = 10`.

### Noi co the nhan item 555

- `UseItem.OpenHopThanlinh`: mo item `1775 - Hop than linh`, co the nhan do Than Linh Trai Dat gom `555, 556, 562, 563, 561`.
- `Mob.getItemMobReward`: quai o map lanh co the roi do Than Linh thong qua `ItemService.randDoTL`.
- Boss dung `ItemService.randDoTLBoss`: Black Goku, Baby, Cooler, Cumber, Sieu Bo Hung, cac boss MajinBuu 12h.
- `Hirudegarn`: co random truc tiep cac ao Than Linh `555, 557, 559`.

### Sua co che random set kich hoat cho do Than Linh

- Them cau hinh ti le trong `src/nro/models/services/ItemService.java`:
  - `DROP_ACTIVATION_RATE = 5`: do roi tu quai/boss co 5% thanh do theo set kich hoat.
  - `ACTIVITY_ACTIVATION_RATE = 10`: do nhan tu hoat dong/hop co 10% thanh do theo set kich hoat.
- Them helper trong `ItemService`:
  - `addDropActivationOption(ItemMap item, int gender)`.
  - `addActivityActivationOption(Item item, int gender)`.
  - `randomActivationSetByGender(int gender)`.
  - `hasActivationOption(List<ItemOption> options)`.
  - `addOptionIfAbsent(List<ItemOption> options, int optionId, int param)`.
- Khi trung ti le, item duoc gan them cap option set theo hanh tinh nhan vat:
  - Trai Dat: random `128`, `127`, `129`.
  - Namek: random `130`, `131`, `132`.
  - Xayda: random `133`, `134`, `135`.
- Helper tu dong them option phu tuong ung qua ham `ID(skhId)`:
  - Vi du `129` se them kem `141`.
- Helper khong gan trung set neu item da co option set kich hoat.
- Helper them option `30` neu item trung set, va tranh them trung option neu da co san.

### File da sua

- `src/nro/models/services/ItemService.java`
  - Doi chu ky `randDoTL(..., long id)` thanh `randDoTL(..., long id, int gender)`.
  - Doi chu ky `randDoTLBoss(..., long id)` thanh `randDoTLBoss(..., long id, int gender)`.
  - Them logic random set kich hoat voi ti le 5%/10%.
- `src/nro/models/mob/Mob.java`
  - Truyen them `player.gender` vao `randDoTL`.
- `src/nro/models/services_func/UseItem.java`
  - Trong `OpenHopThanlinh`, sau khi random item Than Linh thi goi `addActivityActivationOption(chosenItem, player.gender)`.
- `src/nro/models/mob_bigboss/Hirudegarn.java`
  - Truyen gender theo item template khi tao option cho item `555/557/559`.
- Cac boss dang dung `randDoTLBoss` da duoc truyen them `plKill.gender`.

### Ghi chu kiem tra

- Da ra soat khong con call site dung chu ky cu `randDoTL` / `randDoTLBoss`.
- Da thu compile bang `javac`, nhung project hien khong compile don le duoc trong moi truong nay vi thieu `ant` va nhieu getter/setter sinh boi Lombok/NetBeans khong duoc xu ly khi goi `javac` truc tiep.
- Loi compile con lai khong phai loi truc tiep tu logic random set moi.
