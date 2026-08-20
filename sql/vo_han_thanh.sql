-- ==========================================================
-- SQL CẤU HÌNH BẢN ĐỒ VÔ HẠN THÀNH 1 (MAP 186) TÁI TẠO TỪ ĐẦU
-- ==========================================================

-- 1. Cấu hình phân lớp Layer:
-- Layer 1: Background và Nhà Shōji cảnh quan
-- Layer 3: Toàn bộ Lối đi cầu gỗ (Asset 355), Cầu thang dốc (Asset 359), Chùa nổi (Asset 360)
INSERT INTO `bg_item_template` (`id`, `image_id`, `layer`, `dx`, `dy`) VALUES
(597, 354, 3, 0, 0),
(598, 355, 3, 0, 0),
(599, 356, 3, 0, 0),
(600, 357, 1, 0, 0),
(601, 358, 1, 0, 0),
(602, 359, 3, 0, 0),
(603, 360, 3, 0, 0),
(604, 361, 1, 0, 0),
(605, 362, 3, 0, 0),
(606, 363, 1, 0, 0),
(607, 364, 3, 0, 0),
(608, 365, 1, 0, 0),
(609, 366, 3, 0, 0),
(610, 367, 1, 0, 0),
(611, 368, 1, 0, 0)
ON DUPLICATE KEY UPDATE `image_id` = VALUES(`image_id`), `layer` = VALUES(`layer`), `dx` = VALUES(`dx`), `dy` = VALUES(`dy`);

-- 2. Thêm cấu hình NPC Kanao
INSERT INTO `npc_template` (`id`, `NAME`, `head`, `body`, `leg`, `avatar`) VALUES
(112, 'Kanao', 2132, 2133, 2134, 25009)
ON DUPLICATE KEY UPDATE `NAME` = VALUES(`NAME`), `head` = VALUES(`head`), `body` = VALUES(`body`), `leg` = VALUES(`leg`), `avatar` = VALUES(`avatar`);

-- 3. Cập nhật Map 186 vào map_template
DELETE FROM `map_template` WHERE `id` IN (186, 187, 188, 189);

INSERT INTO `map_template` (`id`, `NAME`, `zones`, `max_player`, `data`, `type`, `planet_id`, `bg_type`, `tile_id`, `bg_id`, `waypoints`, `mobs`, `npcs`, `is_map_double`) VALUES
(186, 'Vô Hạn Thành 1', 15, 15, '[0,0,0,1,0]', 0, 0, 0, 15, 17, '[\"[\"Vô Hạn Thành 2\",900,120,1056,168,0,0,187,60,336]\"]', '[]', '[[112,60,432]]', 0),
(187, 'Vô Hạn Thành 2', 15, 15, '[0,0,0,1,0]', 0, 0, 0, 15, 17, '[\"[\"Vô Hạn Thành 1\",0,288,48,336,0,0,186,960,168]\",\"[\"Vô Hạn Thành 3\",1392,288,1440,336,0,0,188,60,336]\"]', '[]', '[]', 0),
(188, 'Vô Hạn Thành 3', 15, 15, '[0,0,0,1,0]', 0, 0, 0, 15, 17, '[\"[\"Vô Hạn Thành 2\",0,288,48,336,0,0,187,1360,336]\",\"[\"Vô Hạn Thành 4\",1392,288,1440,336,0,0,189,60,336]\"]', '[]', '[]', 0),
(189, 'Vô Hạn Thành 4', 15, 15, '[0,0,0,1,0]', 0, 0, 0, 15, 17, '[\"[\"Vô Hạn Thành 3\",0,288,48,336,0,0,188,1360,336]\"]', '[]', '[]', 0);
