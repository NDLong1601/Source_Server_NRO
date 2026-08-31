# Siêu Ma Cảm Tử v2 — bàn giao 31/08/2026

## Đã triển khai

- Thay đúng 8 PNG được cung cấp: 3 triệu hồi, 1 chờ, 3 kiểu bay ngẫu nhiên, 1 nổ. Giữ nền alpha và dùng pivot theo mặt hồn ma.
- Tích hợp ở cả Game1/Game2 trong `C:/Users/PC/Music/PRJ_2Tab_550K`.
- Cấp 1–7 có 1–7 hồn, thời gian tương ứng **20/25/30/35/40/45/50 giây**, tính sau khi triệu hồi hoàn tất. Lệnh tấn công không làm mới thời gian.
- Phóng từng hồn, đợi va chạm trước khi phóng tiếp. Mục tiêu chết thì dừng; ví dụ 7 hồn dùng 2, còn 5 hồn để đánh mục tiêu khác. Mục tiêu chết/mất hiệu lực trước va chạm thì hồn đang bay quay lại, không bị tiêu hao.
- Vòng bay mở rộng theo số hồn hiện có, xếp lại đều trong 250 ms khi số lượng thay đổi. Sprite bay lật theo mục tiêu, vị trí theo server; không dùng hướng đứng của caster để quyết định hướng bay.
- Server quản lý hit, deadline, trạng thái, target ID và cast ID; snapshot cho người mới vào map, chống packet cũ/trùng. Timer trên wire dùng int32.
- Giữ icon kỹ năng `26554` hiện có. Không thay KI/cooldown hay cân bằng sát thương gốc trong lần triển khai này.

Đặc tả chi tiết và bảng cấp độ: `docs/custom-skills-backend-spec.md`, mục 7.

## Kết quả xác minh

| Hạng mục | Kết quả |
|---|---|
| Java state machine | PASS — 824 assertions |
| Java → C# wire replay | PASS — 88 packet thật, 1.253 assertions, cả Game1/Game2 |
| Full server compile | PASS — javac 23.0.2, `--release 17`, Lombok 1.18.36, cập nhật 668 class |
| Full client compile | PASS — compiler đi kèm Unity 2022.3.62f2 và response file `Assembly-CSharp.rsp`, output vào thư mục temp; không lỗi C# |
| Protocol tải item | PASS — 2.247 template, reload 65.495 byte; append 65.469 và 387 byte, thứ tự liên tục, dưới 65.535 byte |
| Reconnect bỏ clientType | PASS — dùng cùng zoom x1 với phiên trước; icon 16187 khớp PNG |
| Icon 26554 x1/x2/x3/x4 | PASS — 20/40/60/80 px, SHA-256 payload khớp file server |
| Runtime | Sau cleanup: server khởi động lại lúc 22:11, lắng nghe 14445; `logs/server-error.log` rỗng khi kiểm tra |
| Unity import / playtest trực tiếp | Người dùng xác nhận skill tạm ổn trước cleanup; không chạy lại playtest trực tiếp trong lượt cleanup |
| Hai tài khoản / A → logout → B | **Chưa kiểm thử trực tiếp** |

Các warning C# hiện có của dự án vẫn xuất hiện; không có lỗi compiler. Compile C# bên ngoài Editor chỉ xác nhận source; không thay thế bước Unity import và build executable.

JAR đang triển khai: `C:/Users/PC/Music/Teamobi2026/SRC/20.jar`

SHA-256 sau cleanup: `C2203B92E5C874C343AAC6714AEA0DFAFD28BBC94573B3E3BBB0A9BFD117B545`

Backup JAR trước cleanup: `20.jar.bak_20260831_221053`; trước triển khai v2: `20.jar.bak_20260831_182727`. Bản sao source/frame trước thay đổi: `backups/super_ghost_v2_20260831/`. Không xóa thay đổi có sẵn của người dùng.

Build tool đã được sửa để cập nhật class trên JAR staging, kiểm tra rồi thay thế nguyên tử bằng `File.Replace` có backup. Lý do: Windows giữ memory map khiến cập nhật ZIP trực tiếp thất bại; nếu bước staging lỗi thì giữ nguyên JAR runtime. File staging của lần thử thất bại được giữ tại `backups/super_ghost_v2_20260831/staged_1826.jar` để có thể kiểm tra, không dùng để chạy server.

## Cleanup sau khi người dùng xác nhận skill tạm ổn

- Bỏ field/constant không sử dụng ở client, đặt tên cho các mốc frame/timing; sắp xếp import Java, bỏ kiểm tra effect trùng. Không thay số lượng, damage, thời gian, quỹ đạo hoặc wire format.
- Chuẩn hóa whitespace effect và test C#; hai effect Game1/Game2 giống nhau ngoài namespace.
- Chuyển spritesheet/preview v1 sang `backups/super_ghost_v2_20260831/legacy_assets/` để khôi phục khi cần. Preview v2 nằm trong `output/super_ghost_kamikaze/`; ảnh nguồn v2 và icon vẫn giữ trong `assets/`.
- Git bỏ qua backup riêng của skill, JAR staging và output phát sinh; không stage hoặc tạo commit tự động.
- Script asset đọc bộ nguồn trong repo thay vì phụ thuộc Downloads. Dựng lại cả 8 frame vào output thử nghiệm: byte-identical với PNG client đã được người dùng test.
- Full build dọn đúng một entry lỗi thời: `CustomSkillService$GhostProjectile.class`. So sánh JAR trước/sau xác nhận không xóa entry nào khác; backup vẫn giữ bản cũ. Có regression test packaging riêng.
- Chạy lại 824 Java + 1.253 C# assertions, wire replay 88 packet, full compiler Unity và full server build: PASS. Normal/reconnect sau restart: PASS trên PowerShell 7.6.4, đủ 2.247 item template và icon đúng SHA-256. Lần probe đầu bằng Windows PowerShell 5.1 chưa nhận đủ packet cuối trước khi dừng; đã chạy lại thành công, không thay protocol để bỏ qua kiểm tra.

## Checklist kiểm thử trực tiếp trước phát hành

1. Mở đúng Unity project `PRJ_2Tab_550K`, refresh/import asset và đợi compile xong. Thoát Play Mode cũ trước khi chạy lại để nạp đủ 8 texture.
2. Cấp 1 và 7: kiểm tra ba frame triệu hồi, kích thước hồn, alpha không có nền đen, số hồn đúng. Kiểm tra timer đủ 20/50 giây sau triệu hồi.
3. Triệu hồi 7 hồn; đánh mục tiêu chết sau 2 hit: phải còn 5. Chọn mục tiêu bên trái rồi bên phải để xác nhận hướng bay và hit/nổ trùng mục tiêu; kiểm tra PvP và PvE.
4. Cho mục tiêu chết do nguồn khác hoặc rời map trong lúc hồn bay: hồn quay lại, không mất hồn. Thử spam lệnh và mục tiêu di chuyển.
5. Caster chết/đổi map/disconnect/hết hạn khi đang tấn công: không có damage muộn hoặc hồn treo. Người khác vào map phải thấy đúng số hồn còn lại.
6. Hai client cùng map; đăng nhập A → logout → B trong cùng cửa sổ; kiểm tra icon, sprite x1–x4 và không rò trạng thái giữa tài khoản.

Server/client cùng dùng protocol v2; client cũ không tương thích với lệnh skill31 mới. Khi tạo bản client phát hành, phải build lại từ source đã cập nhật, không dùng executable cũ.
