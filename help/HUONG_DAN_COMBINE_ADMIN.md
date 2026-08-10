# Phân tích và quản trị hệ thống Combine

## Cách mở và áp dụng

Chạy `admin_menu.bat`, chọn tab **Combine**, chọn chức năng ở bảng bên trái rồi sửa **Giá trị đang dùng** và bấm **Lưu & áp dụng**.

Với pool option của Bông tai cấp 2, Bông tai cấp 3 và trang bị Thiên Sứ, giao diện hiển thị danh sách dạng `50 - Sức đánh +#%`. Tick trực tiếp các option mong muốn, nhập Param nhỏ nhất/lớn nhất trong cùng khối rồi lưu. Không cần nhập danh sách ID bằng tay. Bông tai cấp 3 có thêm checkbox cho phép hoặc cấm hai dòng option trùng nhau.

Bộ chọn trực quan này cũng được dùng lại trong tab **Chỉ số trang bị**. Khi thêm hoặc sửa một dòng option shop, tìm theo ID/tên rồi tick một option; bộ chọn chỉ giữ một option vì mỗi dòng trong `item_shop_option` có Param riêng.

Các thay đổi trong `combine.properties` được server kiểm tra lại tối đa mỗi 1 giây. Sau khi bản Java mới đã được build/deploy một lần, các lần chỉnh tỉ lệ tiếp theo không cần restart server.

Quy ước nhập:

- `rate`: phần trăm từ 0 đến 100; số lẻ dùng dấu chấm, ví dụ `0.3`.
- `rate-list`: danh sách tỉ lệ theo cấp, phân cách bằng dấu phẩy.
- `option-list`: được quản lý bằng checklist trên giao diện; các option đã tick có xác suất được chọn như nhau.
- `int`: số nguyên không âm.
- `bool`: `true` hoặc `false`.

Nút **Về mặc định** xóa giá trị override của khóa đã chọn; Java sẽ dùng giá trị mặc định trong mã.

## Tỉ lệ và chỉ số mặc định

| Nhóm | Chức năng | Giá trị mặc định |
| Cường hóa | Nâng trang bị +0 đến +7 | `80,50,20,10,7,5,1,0.3%` |
| Cường hóa | Chỉ số tăng khi thành công | `10%`, tối thiểu 1 nếu phần trăm lớn hơn 0 |
| Cường hóa | Chỉ số giảm khi rớt cấp | `11%`, tối thiểu 1 nếu phần trăm lớn hơn 0 |
| Đục lỗ | Đục lỗ sao 0 đến 8 | `50,20,10,5,1,0.7,0.5,0.1,0.1%` |
| Đục lỗ | Cường hóa lỗ 8-9 | `25%` |
| Bông tai | Cấp 1 lên cấp 2 | `50%` |
| Bông tai | Cấp 2 lên cấp 3 | `50%` |
| Bông tai | Random 1 option cấp 2 | `45%`; option `77,80,81,103,50,94,5`; param `5-15` |
| Bông tai | Random 2 option cấp 3 | `30%`; cùng danh sách; param `5-15`; mặc định cho phép trùng |
| Sao pha lê | Nâng lên cấp 2 / đánh bóng | `50% / 100%` |
| Ghép vật liệu | Nhập đá / phân rã KH / Capsule / Hematite / dùi / đá mài | `80 / 100 / 100 / 100 / 100 / 100%` |
| Sách | Cuốn sách cũ / đổi sách / option phụ / nâng sách | `20 / 20 / 20 / 10%` |
| Thiên Sứ | Chế tạo cơ bản | `90%` cộng cấp đá nâng cấp |
| Thiên Sứ | Chỉ số gốc / may mắn cơ bản | `+100% / 5%`; option phụ `50,77,103,94,5`, param `1-3` |

Trước thay đổi này, `PhaLeHoaTrangBi` hiển thị một bộ tỉ lệ cao hơn tỉ lệ thực tế. Luồng mới dùng cùng một giá trị cấu hình cho cả thông báo và phép quay, nên người chơi luôn nhìn đúng tỉ lệ thật.

## Vai trò các lớp trong folder `combine`

### Lõi điều phối

- `Combine`: trạng thái phiên ghép của người chơi: item đang chọn, vàng/ngọc, tỉ lệ và nguyên liệu.
- `CombineService`: mở tab, điều hướng `showInfo/startCombine`, gửi hiệu ứng và nội dung NPC.
- `CombineSystem`: công thức giá, nguyên liệu, tỉ lệ theo cấp và ánh xạ option đá pha lê.
- `CombineConfig`: đọc `combine.properties`, fallback an toàn và tự reload.
- `RandomCollection`: chọn phần tử theo trọng số; hiện không được các luồng chính gọi trực tiếp.

### Trang bị và sao pha lê

- `NangCapVatPham`: nâng cấp `+0` đến `+8`, cộng chỉ số; thất bại ở mốc 2/4/6 có thể rớt cấp nếu không dùng đá bảo vệ.
- `PhaLeHoaTrangBi`: đục lỗ sao pha lê, hỗ trợ thử 1/10/100 lần.
- `EpSaoTrangBi`: ép đá vào lỗ; lỗ 8-9 giữ option thành dòng riêng.
- `CuongHoaLoSaoPhaLe`: mở khả năng dùng lỗ 8-9.
- `NangCapSaoPhaLe`, `DanhBongSaoPhaLe`: nâng và đánh bóng sao pha lê cấp 2.
- `ChuyenHoaTrangBi_Vang`, `ChuyenHoaTrangBi_Ngoc`: chuyển cấp và option từ đồ gốc sang đồ mới.
- `CheTaoTrangBiThienSu`: chế tạo đồ Thiên Sứ và random option phụ.

### Bông tai

- `NangCapBongTai`, `NangCapBongTai3`: nâng template bông tai lên cấp 2/3.
- `NangChiSoBongTai`: quay một option cấp 2.
- `NangChiSoBongTai3`: quay hai option cấp 3; có thể cấm trùng bằng `earring.level3.allowDuplicate=false`.

### Ghép, tái tạo và phân rã

- `LamPhepNhapDa`: ghép mảnh đá vụn thành đá nâng cấp ngẫu nhiên.
- `NhapNgocRong`: ghép 7 viên ngọc rồng cùng sao thành viên cấp cao hơn; không có phép quay thất bại.
- `PhanRaTrangBiKichHoat`: phân rã trang bị kích hoạt.
- `TaiTaoCapsuleKichHoat`: tái tạo Capsule kích hoạt.
- `TaoDaHematite`, `CheTaoDuiDuc`, `TaoDaMai`: chuỗi chế tạo vật liệu cho lỗ sao cao cấp.
- `PhanRaSach`, `HoiPhucSach`, `TaySach`: phân rã, phục hồi và tẩy sách.

### Sách tuyệt kỹ

- `CheTaoCuonSachCu`: đóng trang và bìa thành cuốn sách cũ.
- `DoiSachTuyetKy`: đổi sách; con dấu đảm bảo nhánh thành công và ảnh hưởng option phụ.
- `GiamDinhSach`: random option khi giám định.
- `NangCapSachTuyetKy`: nâng Sách Tuyệt Kỹ 1.
- `HocTuyetKy`: học kỹ năng theo hành tinh.

Các giá trị item ID, số lượng nguyên liệu, giá vàng/ngọc và ánh xạ template vẫn nằm trong từng lớp. Tab Combine hiện tập trung vào những biến cân bằng dễ gây ảnh hưởng nhất: tỉ lệ, phần trăm chỉ số và pool option random.
