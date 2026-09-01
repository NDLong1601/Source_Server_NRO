# Hộp quà Levi Ackerman

Gói này triển khai hộp quà ID `2248` sử dụng icon do người dùng cung cấp. Khi mở, hộp luôn trao đúng một `Cải trang Levi Ackerman` ID `2247`, vĩnh viễn, không có quay ngẫu nhiên.

## Cấu hình phát hành

- Hộp quà: `2248` — `Hộp quà Levi Ackerman`, TYPE `29`, icon `26575`.
- Phần thưởng: `2247` — `Cải trang Levi Ackerman`, TYPE `5`, dùng part `2198/2199/2200` và ảnh `26555–26574`.
- Luật mở hộp: cần tối thiểu một ô hành trang trống; tiêu hao một hộp; trao một cải trang vĩnh viễn.
- Không thêm vào shop công khai.
- Mã thử nghiệm một lượt: `LEVIA7Q9T2`, hết hạn `2026-09-08 23:59:59` (giờ máy chủ). Mã trao một hộp, để kiểm tra luồng mở hộp trên nhân vật của bạn.

`install.sql` là migration triển khai đầy đủ (bao gồm cả mã thử); `rollback.sql` chỉ dùng trước khi đã phát vật phẩm cho người chơi. Icon nguồn được lưu ở `source/19024-reference.png`; các biến thể runtime đã nằm trong `data/icon/x1..x4/26575.png`.
