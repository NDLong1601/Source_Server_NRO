-- Đồng bộ nội dung tab Thông báo với bảng tỉ lệ rơi boss/train hiện hành.
-- Có thể chạy lại an toàn: mọi bản ghi cùng tên đều nhận cùng một nội dung.

UPDATE `notify`
SET `text` = CONCAT(
    '--- TÍNH NĂNG GHÉP MẢNH HỦY DIỆT ---', CHAR(10),
    'Thu thập đủ 4 mảnh cùng loại để ghép Rương Trang Bị Hủy Diệt tại NPC Bà Hạt Mít.', CHAR(10), CHAR(10),
    '[1] CÔNG THỨC GHÉP MẢNH:', CHAR(10),
    '- Mảnh Áo 1, 2, 3, 4 -> Rương Áo Hủy Diệt', CHAR(10),
    '- Mảnh Quần 1, 2, 3, 4 -> Rương Quần Hủy Diệt', CHAR(10),
    '- Mảnh Giày 1, 2, 3, 4 -> Rương Giày Hủy Diệt', CHAR(10),
    '- Mảnh Găng 1, 2, 3, 4 -> Rương Găng Hủy Diệt', CHAR(10),
    '- Mảnh Nhẫn 1, 2, 3, 4 -> Rương Nhẫn Hủy Diệt', CHAR(10), CHAR(10),
    '[2] QUY TẮC GHÉP:', CHAR(10),
    '- Đặt đủ 4 mảnh khác nhau của cùng một loại vào ô ghép.', CHAR(10),
    '- Tỉ lệ, chi phí và số mảnh mất khi thất bại áp dụng theo cấu hình hiện hành tại Bà Hạt Mít.', CHAR(10), CHAR(10),
    '[3] CÁCH SỞ HỮU MẢNH HỦY DIỆT:', CHAR(10),
    '- Boss Hành Tinh Tương Lai (Black Goku): 12% rơi 1 Mảnh Áo hoặc Quần Hủy Diệt ngẫu nhiên.', CHAR(10),
    '- Boss Hành Tinh Lạnh (Cooler): 12% rơi 1 Mảnh Giày, Găng hoặc Nhẫn Hủy Diệt ngẫu nhiên.'
)
WHERE `name` = 'Ghép Mảnh Hủy Diệt';

INSERT INTO `notify` (`name`, `text`)
SELECT
    'Ghép Mảnh Hủy Diệt',
    CONCAT(
        '--- TÍNH NĂNG GHÉP MẢNH HỦY DIỆT ---', CHAR(10),
        'Thu thập đủ 4 mảnh cùng loại để ghép Rương Trang Bị Hủy Diệt tại NPC Bà Hạt Mít.', CHAR(10), CHAR(10),
        '[1] CÔNG THỨC GHÉP MẢNH:', CHAR(10),
        '- Mảnh Áo 1, 2, 3, 4 -> Rương Áo Hủy Diệt', CHAR(10),
        '- Mảnh Quần 1, 2, 3, 4 -> Rương Quần Hủy Diệt', CHAR(10),
        '- Mảnh Giày 1, 2, 3, 4 -> Rương Giày Hủy Diệt', CHAR(10),
        '- Mảnh Găng 1, 2, 3, 4 -> Rương Găng Hủy Diệt', CHAR(10),
        '- Mảnh Nhẫn 1, 2, 3, 4 -> Rương Nhẫn Hủy Diệt', CHAR(10), CHAR(10),
        '[2] QUY TẮC GHÉP:', CHAR(10),
        '- Đặt đủ 4 mảnh khác nhau của cùng một loại vào ô ghép.', CHAR(10),
        '- Tỉ lệ, chi phí và số mảnh mất khi thất bại áp dụng theo cấu hình hiện hành tại Bà Hạt Mít.', CHAR(10), CHAR(10),
        '[3] CÁCH SỞ HỮU MẢNH HỦY DIỆT:', CHAR(10),
        '- Boss Hành Tinh Tương Lai (Black Goku): 12% rơi 1 Mảnh Áo hoặc Quần Hủy Diệt ngẫu nhiên.', CHAR(10),
        '- Boss Hành Tinh Lạnh (Cooler): 12% rơi 1 Mảnh Giày, Găng hoặc Nhẫn Hủy Diệt ngẫu nhiên.'
    )
WHERE NOT EXISTS (SELECT 1 FROM `notify` WHERE `name` = 'Ghép Mảnh Hủy Diệt');

UPDATE `notify`
SET `text` = CONCAT(
    '--- TÍNH NĂNG NÂNG CẤP NHẪN THỜI KHÔNG ---', CHAR(10),
    'Nâng Nhẫn Thời Không / Nhẫn Hủy Diệt tại NPC Bà Hạt Mít để tăng thuộc tính.', CHAR(10), CHAR(10),
    '[1] NGUYÊN LIỆU:', CHAR(10),
    '- Nhẫn cần nâng cấp.', CHAR(10),
    '- Đá hoàng kim theo số lượng yêu cầu của bậc nhẫn.', CHAR(10),
    '- Vàng hoặc Ngọc xanh nếu cấu hình bậc nâng cấp đang yêu cầu.', CHAR(10), CHAR(10),
    '[2] CÁCH SỞ HỮU ĐÁ HOÀNG KIM:', CHAR(10),
    '- Boss Hành Tinh Tương Lai (Black Goku): 5%, rơi x1.', CHAR(10),
    '- Boss Hành Tinh Lạnh (Cooler): 5%, rơi x1.', CHAR(10),
    '- Khi May mắn đạt từ 100%, số Đá hoàng kim rơi từ boss được nhân đôi.'
)
WHERE `name` = 'Nâng Cấp Nhẫn';

INSERT INTO `notify` (`name`, `text`)
SELECT
    'Nâng Cấp Nhẫn',
    CONCAT(
        '--- TÍNH NĂNG NÂNG CẤP NHẪN THỜI KHÔNG ---', CHAR(10),
        'Nâng Nhẫn Thời Không / Nhẫn Hủy Diệt tại NPC Bà Hạt Mít để tăng thuộc tính.', CHAR(10), CHAR(10),
        '[1] NGUYÊN LIỆU:', CHAR(10),
        '- Nhẫn cần nâng cấp.', CHAR(10),
        '- Đá hoàng kim theo số lượng yêu cầu của bậc nhẫn.', CHAR(10),
        '- Vàng hoặc Ngọc xanh nếu cấu hình bậc nâng cấp đang yêu cầu.', CHAR(10), CHAR(10),
        '[2] CÁCH SỞ HỮU ĐÁ HOÀNG KIM:', CHAR(10),
        '- Boss Hành Tinh Tương Lai (Black Goku): 5%, rơi x1.', CHAR(10),
        '- Boss Hành Tinh Lạnh (Cooler): 5%, rơi x1.', CHAR(10),
        '- Khi May mắn đạt từ 100%, số Đá hoàng kim rơi từ boss được nhân đôi.'
    )
WHERE NOT EXISTS (SELECT 1 FROM `notify` WHERE `name` = 'Nâng Cấp Nhẫn');

UPDATE `notify`
SET `text` = CONCAT(
    '--- TÍNH NĂNG NÂNG CẤP NGOẠI TRANG ---', CHAR(10),
    'Nâng Cải trang, Pet/Linh thú, Cánh/Đeo lưng và Ván bay tại NPC Bà Hạt Mít.', CHAR(10), CHAR(10),
    '[1] ĐÁ NÂNG CẤP VÀ NGUỒN RƠI:', CHAR(10),
    '- Đá Thiên Vũ: Boss Fide, 10%, rơi x2-5; dùng cho Cánh/Đeo lưng.', CHAR(10),
    '- Đá Phi Hành: Boss Hành Tinh Tương Lai (Black Goku), 10%, rơi x2-5; dùng cho Ván bay.', CHAR(10),
    '- Đá Huyễn Trang: Boss Hành Tinh Lạnh (Cooler), 8%, rơi x2-5; dùng cho Cải trang.', CHAR(10),
    '- Đá Linh Thú: Boss Hành Tinh Lạnh (Cooler), 10%, rơi x2-5; dùng cho Pet/Linh thú.', CHAR(10), CHAR(10),
    '[2] MAY MẮN:', CHAR(10),
    '- Khi May mắn đạt từ 100%, số đá rơi từ boss được nhân đôi.', CHAR(10), CHAR(10),
    '[3] VẬT PHẨM HỖ TRỢ:', CHAR(10),
    '- Cỏ 3 lá/Cỏ 4 lá tăng tỉ lệ thành công.', CHAR(10),
    '- Sao May Mắn/Sao Ngũ Sắc tăng cơ hội thêm dòng chỉ số.', CHAR(10),
    '- Khiên Cường Hóa bảo hộ không tụt cấp khi thất bại.'
)
WHERE `name` = 'Nâng Cấp Ngoại Trang';

INSERT INTO `notify` (`name`, `text`)
SELECT
    'Nâng Cấp Ngoại Trang',
    CONCAT(
        '--- TÍNH NĂNG NÂNG CẤP NGOẠI TRANG ---', CHAR(10),
        'Nâng Cải trang, Pet/Linh thú, Cánh/Đeo lưng và Ván bay tại NPC Bà Hạt Mít.', CHAR(10), CHAR(10),
        '[1] ĐÁ NÂNG CẤP VÀ NGUỒN RƠI:', CHAR(10),
        '- Đá Thiên Vũ: Boss Fide, 10%, rơi x2-5; dùng cho Cánh/Đeo lưng.', CHAR(10),
        '- Đá Phi Hành: Boss Hành Tinh Tương Lai (Black Goku), 10%, rơi x2-5; dùng cho Ván bay.', CHAR(10),
        '- Đá Huyễn Trang: Boss Hành Tinh Lạnh (Cooler), 8%, rơi x2-5; dùng cho Cải trang.', CHAR(10),
        '- Đá Linh Thú: Boss Hành Tinh Lạnh (Cooler), 10%, rơi x2-5; dùng cho Pet/Linh thú.', CHAR(10), CHAR(10),
        '[2] MAY MẮN:', CHAR(10),
        '- Khi May mắn đạt từ 100%, số đá rơi từ boss được nhân đôi.', CHAR(10), CHAR(10),
        '[3] VẬT PHẨM HỖ TRỢ:', CHAR(10),
        '- Cỏ 3 lá/Cỏ 4 lá tăng tỉ lệ thành công.', CHAR(10),
        '- Sao May Mắn/Sao Ngũ Sắc tăng cơ hội thêm dòng chỉ số.', CHAR(10),
        '- Khiên Cường Hóa bảo hộ không tụt cấp khi thất bại.'
    )
WHERE NOT EXISTS (SELECT 1 FROM `notify` WHERE `name` = 'Nâng Cấp Ngoại Trang');
