-- Fishing book entries used by NPC 113 (Ngư Dân).
-- This migration is idempotent and keeps the database ready before the
-- first player opens the book; the server also has the same lazy safeguard.

CREATE TABLE IF NOT EXISTS fishing_book_entry (
    fish_item_id SMALLINT NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    rarity VARCHAR(80) NOT NULL,
    mob_template_id SMALLINT NOT NULL DEFAULT -1,
    book_rank TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (fish_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT INTO fishing_book_entry
    (fish_item_id, display_name, rarity, mob_template_id, book_rank)
VALUES
    (2124, 'Cá Bạc Nhỏ', 'Phổ thông', 86, 0),
    (2125, 'Cá Rô Đá', 'Phổ thông', 87, 0),
    (2126, 'Cá Chép Vàng', 'Phổ thông', 88, 0),
    (2127, 'Cá Lóc Săn Mồi', 'Không phổ thông', 89, 0),
    (2128, 'Cá Trê Khổng Lồ', 'Không phổ thông', 90, 1),
    (2129, 'Cá Hồi Bạc', 'Không phổ thông', 91, 1),
    (2130, 'Cá Tầm Thiết Giáp', 'Hiếm', 92, 1),
    (2131, 'Cá Ngừ Đại Dương', 'Hiếm', 93, 1),
    (2132, 'Cá Kiếm Lam', 'Hiếm', 94, 2),
    (2133, 'Cá Mập Trắng', 'Cao cấp', 95, 2),
    (2134, 'Cá Mập Đầu Búa', 'Cao cấp', 96, 2),
    (2135, 'Cá Mập Voi Khổng Lồ', 'Siêu hiếm', 97, 2),
    (2136, 'Quỷ Ngư Biển Sâu', 'Sử thi', 98, 3),
    (2137, 'Hải Long Lam Ngọc', 'Huyền thoại', 99, 3),
    (2138, 'Kim Long Hải Thần', 'Cực hiếm', 100, 3)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    rarity = VALUES(rarity),
    mob_template_id = VALUES(mob_template_id),
    book_rank = VALUES(book_rank);
