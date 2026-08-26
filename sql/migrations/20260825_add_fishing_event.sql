-- Fishing Event v1
-- Preconditions checked against the live database on 2026-08-25:
-- item_template is contiguous through ID 2082. This migration owns 2083..2153.
-- Do not run against a database that has assigned any of those IDs differently.

START TRANSACTION;

INSERT INTO item_template
    (id, `TYPE`, gender, `NAME`, description, level, icon_id, part, is_up_to_up, power_require, gold, gem, head, body, leg)
VALUES
    (2083, 29, 3, 'Cần Câu Cùi', 'Dùng tại Khu Câu cá để thả câu. Bắt được cá cấp 1-4.', 1, 26400, -1, 0, 0, 0, 0, -1, -1, -1),
    (2084, 29, 3, 'Cần Câu Thường', 'Dùng tại Khu Câu cá để thả câu. Bắt được cá cấp 1-7.', 1, 26401, -1, 0, 0, 0, 0, -1, -1, -1),
    (2085, 29, 3, 'Cần Câu Cao Cấp', 'Dùng tại Khu Câu cá để thả câu. Bắt được cá cấp 1-10.', 1, 26402, -1, 0, 0, 0, 0, -1, -1, -1),
    (2086, 29, 3, 'Cần Câu Hoàng Gia', 'Dùng tại Khu Câu cá để thả câu. Bắt được cá cấp 1-13.', 1, 26403, -1, 0, 0, 0, 0, -1, -1, -1),
    (2087, 29, 3, 'Cần Câu Hải Vương', 'Dùng tại Khu Câu cá để thả câu. Bắt được cá cấp 1-15.', 1, 26404, -1, 0, 0, 0, 0, -1, -1, -1),
    (2088, 29, 3, 'Mồi Giun', 'Dùng để chọn mồi câu. Phù hợp cá cấp 1-4.', 1, 26405, -1, 1, 0, 0, 0, -1, -1, -1),
    (2089, 29, 3, 'Mồi Bột Thơm', 'Dùng để chọn mồi câu. Phù hợp cá cấp 1-6.', 1, 26406, -1, 1, 0, 0, 0, -1, -1, -1),
    (2090, 29, 3, 'Ấu Trùng Béo', 'Dùng để chọn mồi câu. Phù hợp cá cấp 1-8.', 1, 26407, -1, 1, 0, 0, 0, -1, -1, -1),
    (2091, 29, 3, 'Mồi Tôm Đỏ', 'Dùng để chọn mồi câu. Phù hợp cá cấp 2-10.', 1, 26408, -1, 1, 0, 0, 0, -1, -1, -1),
    (2092, 29, 3, 'Cá Mồi Lam Ngọc', 'Dùng để chọn mồi câu. Phù hợp cá cấp 3-12.', 1, 26409, -1, 1, 0, 0, 0, -1, -1, -1),
    (2093, 29, 3, 'Mồi Pha Lê Đại Dương', 'Dùng để chọn mồi câu. Phù hợp cá cấp 5-14.', 1, 26410, -1, 1, 0, 0, 0, -1, -1, -1),
    (2094, 29, 3, 'Mồi Cá Chép Hoàng Kim', 'Dùng để chọn mồi câu. Phù hợp cá cấp 7-15.', 1, 26411, -1, 1, 0, 0, 0, -1, -1, -1),
    (2095, 29, 3, 'Dây Cước Thường', 'Dùng để trang bị dây câu cơ bản.', 1, 26412, -1, 0, 0, 0, 0, -1, -1, -1),
    (2096, 29, 3, 'Dây Thép Gia Cố', 'Dùng để trang bị dây câu giảm tỉ lệ đứt.', 1, 26413, -1, 0, 0, 0, 0, -1, -1, -1),
    (2097, 29, 3, 'Dây Long Cân Hải Thần', 'Dùng để trang bị dây câu cao cấp.', 1, 26414, -1, 0, 0, 0, 0, -1, -1, -1),
    (2098, 29, 3, 'Phao Gỗ Cũ', 'Dùng để trang bị phao câu.', 1, 26415, -1, 0, 0, 0, 0, -1, -1, -1),
    (2099, 29, 3, 'Phao Lam Ngọc', 'Dùng để trang bị phao câu; tăng thời gian phản ứng.', 1, 26416, -1, 0, 0, 0, 0, -1, -1, -1),
    (2100, 29, 3, 'Phao Hải Thần', 'Dùng để trang bị phao câu cao cấp.', 1, 26417, -1, 0, 0, 0, 0, -1, -1, -1),
    (2101, 29, 3, 'Máy Câu Cũ', 'Dùng để trang bị máy câu.', 1, 26418, -1, 0, 0, 0, 0, -1, -1, -1),
    (2102, 29, 3, 'Máy Câu Cao Cấp', 'Dùng để trang bị máy câu; tăng tỉ lệ kéo cá.', 1, 26419, -1, 0, 0, 0, 0, -1, -1, -1),
    (2103, 29, 3, 'Máy Câu Hải Vương', 'Dùng để trang bị máy câu cao cấp.', 1, 26420, -1, 0, 0, 0, 0, -1, -1, -1),
    (2104, 29, 3, 'Lưỡi Câu Chùm Sắt Cũ', 'Dùng để trang bị lưỡi câu chùm.', 1, 26421, -1, 0, 0, 0, 0, -1, -1, -1),
    (2105, 29, 3, 'Lưỡi Câu Chùm Lam Ngọc', 'Dùng để trang bị lưỡi câu chùm.', 1, 26422, -1, 0, 0, 0, 0, -1, -1, -1),
    (2106, 29, 3, 'Lưỡi Câu Chùm Hoàng Kim', 'Dùng để trang bị lưỡi câu chùm cao cấp.', 1, 26423, -1, 0, 0, 0, 0, -1, -1, -1),
    (2107, 27, 3, 'Bộ Sửa Cần Câu', 'Tự động cứu lượt câu khi dây bị đứt.', 1, 26424, -1, 1, 0, 0, 0, -1, -1, -1),
    (2108, 29, 3, 'Bùa May Mắn Ngư Phủ', 'Dùng để nhận may mắn câu cá trong 15 phút.', 1, 26425, -1, 1, 0, 0, 0, -1, -1, -1),
    (2109, 27, 3, 'Giỏ Cá Ngư Phủ', 'Vật phẩm hỗ trợ của ngư phủ.', 1, 26426, -1, 0, 0, 0, 0, -1, -1, -1),
    (2110, 27, 3, 'Hộp Mồi Đa Năng', 'Có 10% cơ hội không tiêu hao mồi khi câu.', 1, 26427, -1, 1, 0, 0, 0, -1, -1, -1),
    (2111, 27, 3, 'Huy Hiệu Ngư Phủ', 'Tiền tệ hiếm của sự kiện Câu Cá.', 1, 26428, -1, 1, 0, 0, 0, -1, -1, -1),
    (2112, 29, 3, 'Máy Dò Cá Sonar', 'Dùng để tăng cơ hội cá hiếm trong 15 phút.', 1, 26429, -1, 1, 0, 0, 0, -1, -1, -1),
    (2113, 29, 3, 'Rương Ngư Phủ', 'Dùng để mở phần thưởng của ngư phủ.', 1, 26430, -1, 1, 0, 0, 0, -1, -1, -1),
    (2114, 27, 3, 'Thùng Cá Đại Dương', 'Vật phẩm hỗ trợ của ngư phủ.', 1, 26431, -1, 0, 0, 0, 0, -1, -1, -1),
    (2115, 27, 3, 'Xu Ngư Phủ', 'Tiền tệ phổ thông của sự kiện Câu Cá.', 1, 26432, -1, 1, 0, 0, 0, -1, -1, -1),
    (2116, 27, 3, 'Đá', 'Vật phẩm phụ câu được từ biển.', 1, 26433, -1, 1, 0, 0, 0, -1, -1, -1),
    (2117, 27, 3, 'Lưới Rách', 'Rác biển, có thể dùng chế tạo.', 1, 26434, -1, 1, 0, 0, 0, -1, -1, -1),
    (2118, 27, 3, 'Rong Biển', 'Nguyên liệu dùng chế tạo mồi.', 1, 26435, -1, 1, 0, 0, 0, -1, -1, -1),
    (2119, 27, 3, 'San Hô', 'Nguyên liệu quý từ biển.', 1, 26436, -1, 1, 0, 0, 0, -1, -1, -1),
    (2120, 27, 3, 'Sắt Vụn', 'Nguyên liệu dùng chế Bộ Sửa.', 1, 26437, -1, 1, 0, 0, 0, -1, -1, -1),
    (2121, 27, 3, 'Túi Rác To', 'Rác biển cần được thu gom.', 1, 26438, -1, 1, 0, 0, 0, -1, -1, -1),
    (2122, 27, 3, 'Túi Rác Nhỏ', 'Rác biển cần được thu gom.', 1, 26439, -1, 1, 0, 0, 0, -1, -1, -1),
    (2123, 27, 3, 'Vỏ Chuối', 'Rác biển cần được thu gom.', 1, 26440, -1, 1, 0, 0, 0, -1, -1, -1),
    (2124, 27, 3, 'Cá Bạc Nhỏ', 'Cá cấp 1. Đổi tại Ngư dân lấy Xu Ngư Phủ.', 1, 26441, -1, 1, 0, 0, 0, -1, -1, -1),
    (2125, 27, 3, 'Cá Rô Đá', 'Cá cấp 2. Đổi tại Ngư dân lấy Xu Ngư Phủ.', 1, 26442, -1, 1, 0, 0, 0, -1, -1, -1),
    (2126, 27, 3, 'Cá Chép Vàng', 'Cá cấp 3. Đổi tại Ngư dân lấy Xu Ngư Phủ.', 1, 26443, -1, 1, 0, 0, 0, -1, -1, -1),
    (2127, 27, 3, 'Cá Lóc Săn Mồi', 'Cá cấp 4. Đổi tại Ngư dân lấy Xu Ngư Phủ.', 1, 26444, -1, 1, 0, 0, 0, -1, -1, -1),
    (2128, 27, 3, 'Cá Trê Khổng Lồ', 'Cá cấp 5. Đổi tại Ngư dân lấy Xu Ngư Phủ.', 1, 26445, -1, 1, 0, 0, 0, -1, -1, -1),
    (2129, 27, 3, 'Cá Hồi Bạc', 'Cá cấp 6. Đổi tại Ngư dân lấy Xu Ngư Phủ.', 1, 26446, -1, 1, 0, 0, 0, -1, -1, -1),
    (2130, 27, 3, 'Cá Tầm Thiết Giáp', 'Cá cấp 7. Đổi tại Ngư dân lấy Xu Ngư Phủ.', 1, 26447, -1, 1, 0, 0, 0, -1, -1, -1),
    (2131, 27, 3, 'Cá Ngừ Đại Dương', 'Cá cấp 8. Đổi tại Ngư dân lấy Xu Ngư Phủ.', 1, 26448, -1, 1, 0, 0, 0, -1, -1, -1),
    (2132, 27, 3, 'Cá Kiếm Lam', 'Cá cấp 9. Đổi tại Ngư dân lấy Xu Ngư Phủ.', 1, 26449, -1, 1, 0, 0, 0, -1, -1, -1),
    (2133, 27, 3, 'Cá Mập Trắng', 'Cá cấp 10. Đổi tại Ngư dân lấy Xu Ngư Phủ.', 1, 26450, -1, 1, 0, 0, 0, -1, -1, -1),
    (2134, 27, 3, 'Cá Mập Đầu Búa', 'Cá cấp 11. Đổi tại Ngư dân lấy Xu Ngư Phủ.', 1, 26451, -1, 1, 0, 0, 0, -1, -1, -1),
    (2135, 27, 3, 'Cá Mập Voi Khổng Lồ', 'Cá cấp 12. Đổi tại Ngư dân lấy Xu Ngư Phủ.', 1, 26452, -1, 1, 0, 0, 0, -1, -1, -1),
    (2136, 27, 3, 'Quỷ Ngư Biển Sâu', 'Cá cấp 13. Đổi tại Ngư dân lấy Xu Ngư Phủ.', 1, 26453, -1, 1, 0, 0, 0, -1, -1, -1),
    (2137, 27, 3, 'Hải Long Lam Ngọc', 'Cá cấp 14. Đổi tại Ngư dân lấy Xu Ngư Phủ.', 1, 26454, -1, 1, 0, 0, 0, -1, -1, -1),
    (2138, 27, 3, 'Kim Long Hải Thần', 'Cá cấp 15. Đổi tại Ngư dân lấy Xu Ngư Phủ.', 1, 26455, -1, 1, 0, 0, 0, -1, -1, -1),
    (2139, 27, 3, 'Khổng Lồ - Cá Bạc Nhỏ', 'Phiên bản Khổng Lồ. Đổi tại Ngư dân nhận gấp 3 Xu Ngư Phủ.', 1, 26441, -1, 1, 0, 0, 0, -1, -1, -1),
    (2140, 27, 3, 'Khổng Lồ - Cá Rô Đá', 'Phiên bản Khổng Lồ. Đổi tại Ngư dân nhận gấp 3 Xu Ngư Phủ.', 1, 26442, -1, 1, 0, 0, 0, -1, -1, -1),
    (2141, 27, 3, 'Khổng Lồ - Cá Chép Vàng', 'Phiên bản Khổng Lồ. Đổi tại Ngư dân nhận gấp 3 Xu Ngư Phủ.', 1, 26443, -1, 1, 0, 0, 0, -1, -1, -1),
    (2142, 27, 3, 'Khổng Lồ - Cá Lóc Săn Mồi', 'Phiên bản Khổng Lồ. Đổi tại Ngư dân nhận gấp 3 Xu Ngư Phủ.', 1, 26444, -1, 1, 0, 0, 0, -1, -1, -1),
    (2143, 27, 3, 'Khổng Lồ - Cá Trê Khổng Lồ', 'Phiên bản Khổng Lồ. Đổi tại Ngư dân nhận gấp 3 Xu Ngư Phủ.', 1, 26445, -1, 1, 0, 0, 0, -1, -1, -1),
    (2144, 27, 3, 'Khổng Lồ - Cá Hồi Bạc', 'Phiên bản Khổng Lồ. Đổi tại Ngư dân nhận gấp 3 Xu Ngư Phủ.', 1, 26446, -1, 1, 0, 0, 0, -1, -1, -1),
    (2145, 27, 3, 'Khổng Lồ - Cá Tầm Thiết Giáp', 'Phiên bản Khổng Lồ. Đổi tại Ngư dân nhận gấp 3 Xu Ngư Phủ.', 1, 26447, -1, 1, 0, 0, 0, -1, -1, -1),
    (2146, 27, 3, 'Khổng Lồ - Cá Ngừ Đại Dương', 'Phiên bản Khổng Lồ. Đổi tại Ngư dân nhận gấp 3 Xu Ngư Phủ.', 1, 26448, -1, 1, 0, 0, 0, -1, -1, -1),
    (2147, 27, 3, 'Khổng Lồ - Cá Kiếm Lam', 'Phiên bản Khổng Lồ. Đổi tại Ngư dân nhận gấp 3 Xu Ngư Phủ.', 1, 26449, -1, 1, 0, 0, 0, -1, -1, -1),
    (2148, 27, 3, 'Khổng Lồ - Cá Mập Trắng', 'Phiên bản Khổng Lồ. Đổi tại Ngư dân nhận gấp 3 Xu Ngư Phủ.', 1, 26450, -1, 1, 0, 0, 0, -1, -1, -1),
    (2149, 27, 3, 'Khổng Lồ - Cá Mập Đầu Búa', 'Phiên bản Khổng Lồ. Đổi tại Ngư dân nhận gấp 3 Xu Ngư Phủ.', 1, 26451, -1, 1, 0, 0, 0, -1, -1, -1),
    (2150, 27, 3, 'Khổng Lồ - Cá Mập Voi', 'Phiên bản Khổng Lồ. Đổi tại Ngư dân nhận gấp 3 Xu Ngư Phủ.', 1, 26452, -1, 1, 0, 0, 0, -1, -1, -1),
    (2151, 27, 3, 'Khổng Lồ - Quỷ Ngư Biển Sâu', 'Phiên bản Khổng Lồ. Đổi tại Ngư dân nhận gấp 3 Xu Ngư Phủ.', 1, 26453, -1, 1, 0, 0, 0, -1, -1, -1),
    (2152, 27, 3, 'Khổng Lồ - Hải Long Lam Ngọc', 'Phiên bản Khổng Lồ. Đổi tại Ngư dân nhận gấp 3 Xu Ngư Phủ.', 1, 26454, -1, 1, 0, 0, 0, -1, -1, -1),
    (2153, 27, 3, 'Khổng Lồ - Kim Long Hải Thần', 'Phiên bản Khổng Lồ. Đổi tại Ngư dân nhận gấp 3 Xu Ngư Phủ.', 1, 26455, -1, 1, 0, 0, 0, -1, -1, -1)
ON DUPLICATE KEY UPDATE
    `TYPE` = VALUES(`TYPE`), gender = VALUES(gender), `NAME` = VALUES(`NAME`),
    description = VALUES(description), level = VALUES(level), icon_id = VALUES(icon_id),
    part = VALUES(part), is_up_to_up = VALUES(is_up_to_up), power_require = VALUES(power_require),
    gold = VALUES(gold), gem = VALUES(gem), head = VALUES(head), body = VALUES(body), leg = VALUES(leg);

-- Keep the legacy client's single append packet below its unsigned 16-bit limit.
UPDATE item_template
SET description = CASE
    WHEN id BETWEEN 2083 AND 2087 THEN CONCAT('Cần câu cấp ', id - 2082, '.')
    WHEN id BETWEEN 2088 AND 2094 THEN CONCAT('Mồi câu cấp ', id - 2087, '.')
    WHEN id BETWEEN 2095 AND 2106 THEN 'Trang bị câu cá.'
    WHEN id BETWEEN 2107 AND 2114 THEN 'Vật phẩm câu cá.'
    WHEN id = 2115 THEN 'Xu sự kiện câu cá.'
    WHEN id BETWEEN 2116 AND 2123 THEN 'Vật phẩm từ biển.'
    WHEN id BETWEEN 2124 AND 2138 THEN CONCAT('Cá cấp ', id - 2123, '.')
    WHEN id BETWEEN 2139 AND 2153 THEN 'Cá khổng lồ, đổi x3 Xu.'
    ELSE description
END
WHERE id BETWEEN 2083 AND 2153;

-- Put the existing Ngư dân in the published hub as well as Đảo Kamê.
UPDATE map_template
SET npcs = CASE
    WHEN npcs IS NULL OR TRIM(npcs) = '' OR TRIM(npcs) = '[]' THEN '[[113,288,648]]'
    ELSE CONCAT(LEFT(TRIM(npcs), CHAR_LENGTH(TRIM(npcs)) - 1), ',[113,288,648]]')
END
WHERE id = 186 AND npcs NOT REGEXP '\\[[[:space:]]*113[[:space:]]*,';

COMMIT;
