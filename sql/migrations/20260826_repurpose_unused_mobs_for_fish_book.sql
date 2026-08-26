-- The client accepts at most 127 contiguous normal mob templates (0..126).
-- IDs 86..100 have no map spawn, admin spawn configuration, or gameplay use.
-- Reuse them for the 15 fish-book preview sprites instead of adding templates.
INSERT INTO mob_template
    (id, TYPE, NAME, hp, range_move, speed, dart_Type, percent_dame, percent_tiem_nang)
VALUES
    (86, 1, 'Cá Bạc Nhỏ', 20, 0, 1, 25, 5, 10),
    (87, 1, 'Cá Rô Đá', 20, 0, 1, 25, 5, 10),
    (88, 1, 'Cá Chép Vàng', 20, 0, 1, 25, 5, 10),
    (89, 1, 'Cá Lóc Săn Mồi', 20, 0, 1, 25, 5, 10),
    (90, 1, 'Cá Trê Khổng Lồ', 20, 0, 1, 25, 5, 10),
    (91, 1, 'Cá Hồi Bạc', 20, 0, 1, 25, 5, 10),
    (92, 1, 'Cá Tầm Thiết Giáp', 20, 0, 1, 25, 5, 10),
    (93, 1, 'Cá Ngừ Đại Dương', 20, 0, 1, 25, 5, 10),
    (94, 1, 'Cá Kiếm Lam', 20, 0, 1, 25, 5, 10),
    (95, 1, 'Cá Mập Trắng', 20, 0, 1, 25, 5, 10),
    (96, 1, 'Cá Mập Đầu Búa', 20, 0, 1, 25, 5, 10),
    (97, 1, 'Cá Mập Voi Khổng Lồ', 20, 0, 1, 25, 5, 10),
    (98, 1, 'Quỷ Ngư Biển Sâu', 20, 0, 1, 25, 5, 10),
    (99, 1, 'Hải Long Lam Ngọc', 20, 0, 1, 25, 5, 10),
    (100, 1, 'Kim Long Hải Thần', 20, 0, 1, 25, 5, 10)
ON DUPLICATE KEY UPDATE
    TYPE = VALUES(TYPE),
    NAME = VALUES(NAME),
    hp = VALUES(hp),
    range_move = VALUES(range_move),
    speed = VALUES(speed),
    dart_Type = VALUES(dart_Type),
    percent_dame = VALUES(percent_dame),
    percent_tiem_nang = VALUES(percent_tiem_nang);
