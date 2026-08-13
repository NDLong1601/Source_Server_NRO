# Hướng dẫn cấu hình Hộp quà / Rương

## Phạm vi

Tab **Hộp quà / Rương** dùng cho các vật phẩm mở hộp theo cơ chế chọn phần
thưởng có trọng số. Khi người chơi sử dụng hộp, server sẽ:

1. Kiểm tra cấu hình và số lượng hộp trong hành trang.
2. Kiểm tra số ô trống tối thiểu.
3. Chọn phần thưởng theo trọng số (chỉ tính các phần thưởng đúng giới tính).
4. Tạo vật phẩm, random số lượng và các option trong khoảng min–max.
5. Random hạn sử dụng, thêm vào hành trang rồi mới trừ hộp.

Cấu hình được lưu trong bảng `gift_box_config` (cột `config_json`). Server đọc
cấu hình lúc khởi động; sau khi lưu trên admin cần restart server để áp dụng.

## Thao tác trên admin

- Mở `admin_data_menu.hta`, chọn **Hộp quà / Rương**.
- Dùng ô tìm kiếm hoặc chọn **Tạo mới** để nhập ID template của hộp.
- Trong **Danh sách vật phẩm**, tick các vật phẩm có thể rơi. Mỗi vật phẩm
  được chọn một lần và xuất hiện trong bảng phần thưởng.
- Chọn một dòng để chỉnh trọng số, số lượng, giới tính, option mặc định và
  option riêng.
- Tick option cần random rồi nhập `min` và `max`. Nếu min = max thì giá trị
  cố định.
- Ở phần HSD, nhập trọng số cho `Vĩnh viễn` và/hoặc `Có hạn`; với có hạn nhập
  số ngày min–max (ví dụ 5–10).
- Bấm **Lưu cấu hình**. Nút **Khóa** chỉ đặt `enabled = 0`, không xóa dữ liệu;
  có thể mở lại bằng cách bật checkbox và lưu.

## Ý nghĩa các trường

| Trường | Ý nghĩa |
| --- | --- |
| Ô trống tối thiểu | Số ô trống bắt buộc trước khi mở. Với nhiều lượt rút, server cộng thêm `drawCount - 1`. |
| Số hộp tiêu hao | Số hộp bị trừ sau khi tạo phần thưởng thành công. |
| Số lượt rút | Số vật phẩm được chọn trong một lần mở. |
| Trọng số | Tỷ lệ tương đối, không cần cộng đúng 100. Ví dụ 1 và 9 tương đương 10% và 90%. |
| Giới tính | `0` Nam, `1` Nữ, `2` Namek, `3` mọi giới tính. Có thể dùng `-1` để bỏ lọc. |
| Khởi tạo option gốc | Gọi logic chỉ số gốc của `RewardService` cho template trang bị. |
| Dùng option mặc định | Trộn option mặc định trong `ItemService` trước option cấu hình riêng. |
| Option riêng | Các option được thêm bằng ID và giá trị random min–max. |

## Ví dụ JSON

Ví dụ một hộp có hai cải trang, cải trang thứ hai hiếm gấp 4 lần; mỗi phần
thưởng có option 50 random 10–20 và 1% vĩnh viễn, 99% có hạn 5–10 ngày:

```json
{
  "boxTemplateId": 1776,
  "enabled": true,
  "minEmptySlots": 2,
  "consumeQuantity": 1,
  "drawCount": 1,
  "rewards": [
    {
      "itemId": 1772,
      "weight": 1,
      "quantityMin": 1,
      "quantityMax": 1,
      "gender": 3,
      "initBaseOptions": true,
      "useDefaultOptions": false,
      "options": [{ "id": 50, "paramMin": 10, "paramMax": 20 }],
      "expiry": [
        { "mode": "permanent", "weight": 1 },
        { "mode": "days", "weight": 99, "daysMin": 5, "daysMax": 10 }
      ]
    },
    {
      "itemId": 1773,
      "weight": 4,
      "quantityMin": 1,
      "quantityMax": 1,
      "gender": 3,
      "initBaseOptions": true,
      "useDefaultOptions": false,
      "options": [{ "id": 50, "paramMin": 10, "paramMax": 20 }],
      "expiry": [{ "mode": "days", "weight": 100, "daysMin": 5, "daysMax": 10 }]
    }
  ]
}
```

HSD vĩnh viễn được biểu diễn bằng option `73:0`; HSD theo ngày dùng option
`93:<số ngày>`. Không cấu hình `93:0` cho vật phẩm vĩnh viễn vì option này bị
coi là đã hết hạn ở lần kiểm tra kế tiếp.

## Các profile đã seed sẵn

Lần đầu tạo bảng, server/admin seed các hộp sự kiện: `1755`, `1756`, `1757`
(Cadic), `1776`, `1777`, `1591`, `1592`, `1594`, `1840` (Goku/event). Có thể
chỉnh hoặc tắt từng profile trong tab mà không sửa Java.

## Rương gỗ Đại hội võ thuật (ID 570)

ID 570 hiện vẫn đi qua hàm `UseItem.openRuongGo`, vì đây là phần thưởng đặc biệt
phụ thuộc cấp rương: vàng, số món cải trang, item hỗ trợ, sao pha lê và đá nâng
cấp được tạo theo level. Engine mới không tự thay thế nhánh level-based này để
tránh làm thay đổi cân bằng cũ. Khi cần đưa rương gỗ vào cùng tab, nên bổ sung
profile dạng `mode = wood_chest` với các rule theo level (0–12), sau đó chuyển
`calculateRequiredEmptySlots` và `openRuongGo` sang đọc rule đó.

## Kiểm tra và xử lý lỗi

- Admin từ chối ID hộp/phần thưởng không tồn tại trong `item_template`.
- Trọng số phải lớn hơn 0 ở ít nhất một phần thưởng; min không được lớn hơn
  max; HSD ngày nằm trong 1–3650.
- Nếu tạo phần thưởng hoặc thêm hành trang thất bại, hộp không bị trừ.
- Nếu profile bị lỗi JSON, server bỏ qua profile đó và giữ handler legacy; xem
  log khởi động để sửa.

DDL thủ công (khi muốn tạo trước khi restart) nằm ở
`sql/gift_box_config.sql`.

