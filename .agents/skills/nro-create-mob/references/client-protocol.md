# Client protocol cho mob thường

## Phạm vi

Tài liệu này áp dụng cho mob thường dùng `EffectData.readData`, `readType=0`, `typeData=0` và bảng hành động 12 frame cố định của `Mob`. Không áp dụng cho new boss dùng `readDataNewBoss` hoặc frame table gửi thêm.

Nguồn đối chiếu client:

- `Controller.cs`, nhánh command `11`.
- `NinjaUtil.readByteArray`, đọc độ dài `int32` rồi đọc đúng số byte.
- `EffectData.readData`, đọc image infos, frames và `arrFrame`.
- `Mob.cs`, các mảng `stand`, `move`, `moveFast`, `attack1`, `attack2` và frame thương/vong 10–11.

## Gói server gửi

Client yêu cầu mob bằng command `11` với một byte `mobId`. Server trả command `11`:

```text
uint8 mobId
raw bytes từ data/mob/x<zoom>/<mobId>
```

File runtime không chứa `mobId`. Nội dung file phải là:

```text
uint8  readType                 = 0
int32  effectDataLength         big-endian
byte[] effectData               đúng effectDataLength
int32  pngLength                big-endian
byte[] png                      bắt đầu 89 50 4E 47 0D 0A 1A 0A
uint8  typeData                 = 0
```

Sai lệch một byte đầu hoặc một trường length khiến client lấy PNG làm metadata, thường chỉ còn bóng đen của mob.

## EffectData thường

```text
uint8 imageInfoCount
repeat imageInfoCount:
    uint8 imageId
    uint8 x
    uint8 y
    uint8 width
    uint8 height
int16 frameCount                big-endian
repeat frameCount:
    uint8 partCount
    repeat partCount:
        int16 dx                big-endian, signed
        int16 dy                big-endian, signed
        uint8 imageId
int16 arrFrameCount             big-endian
repeat arrFrameCount:
    int16 frameIndex            big-endian
```

Mob thường mới không dùng `arrFrame`; ghi `arrFrameCount=0`. Không thêm các byte shadow, type, số frame hoặc delay ở đầu/cuối EffectData nếu client parser không đọc chúng.

## Bảng 12 frame

Với mob ID `>=73`, trừ ID 76, `Mob.cs` dùng trực tiếp:

| Frame | Hành động | Pose atlas |
|---:|---|---|
| 0 | đứng A | stand |
| 1 | đứng B/bò | walk |
| 2 | bước A | stand |
| 3 | bước B | walk |
| 4 | lấy đà attack 1 | stand |
| 5 | attack 1 | attack |
| 6 | attack 1 | attack |
| 7 | lấy đà attack 2 | stand |
| 8 | attack 2 | attack |
| 9 | attack 2 | attack |
| 10 | bị thương | hurt |
| 11 | chết | die |

Mỗi frame của pipeline chuẩn dùng một part là toàn cell. Điểm neo mặc định:

```text
dx = -(cellWidth / 2)
dy = -(cellHeight - 2)
```

Có thể chỉnh `anchor.dx/dy` trong manifest khi chân sprite lệch, nhưng dùng cùng giá trị cho x1–x4; client tự xử lý zoom.

## Atlas và zoom

- Thứ tự cell cố định: `stand`, `walk`, `attack`, `hurt`, `die` trên lưới 3 cột × 2 hàng.
- Metadata giống hệt ở x1–x4.
- PNG x2/x3/x4 phải có kích thước chính xác 2/3/4 lần x1.
- `x`, `y`, `width`, `height` trong image info là byte. Với lưới ba cột, `cellWidth` không vượt 127; pipeline mặc định giới hạn cả hai chiều trong `16..127`.
- Ảnh nguồn phải có alpha thật. Nền đen/trắng opaque sẽ trở thành hình chữ nhật trong game.
- Sinh từng zoom trực tiếp từ ảnh nguồn chất lượng cao; không phóng nối tiếp x1 → x2 → x3 → x4.

## Manifest mẫu

Đường dẫn pose tính tương đối từ thư mục chứa manifest:

```json
{
  "mobId": 120,
  "cell": { "width": 56, "height": 56 },
  "anchor": { "dx": -28, "dy": -54 },
  "poses": {
    "stand": "source/stand.png",
    "walk": "source/walk.png",
    "attack": "source/attack.png",
    "hurt": "source/hurt.png",
    "die": "source/die.png"
  }
}
```

Khi ảnh nguồn chứa nhiều frame hoặc mảnh thừa, giữ nguyên ảnh gốc và khai báo
crop tái tạo được cho riêng pose đó. `x`, `y`, `width`, `height` dùng pixel ảnh
nguồn; crop phải nằm hoàn toàn trong ảnh:

```json
"die": {
  "source": "source/die.png",
  "crop": { "x": 0, "y": 0, "width": 512, "height": 360 }
}
```

## Chẩn đoán nhanh

- Có vị trí/bóng, mất hình: `data` tồn tại nhưng command-11 payload, PNG hoặc cache RAM sai.
- Chỉ sai một zoom: file `data/mob/xN/<id>` hoặc kích thước atlas zoom đó sai.
- Đứng được nhưng attack/hurt gây mất hình: thiếu frame 4..11 hoặc image ID ngoài danh sách.
- Lệch khỏi mặt đất: `dy`/padding cell sai; không sửa tọa độ spawn để che lỗi sprite.
- Không request lại sau khi sửa: client đang giữ `MobTemplate.data`; thoát hẳn tiến trình.

