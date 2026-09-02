-- Equippable aura items for the Teamobi NRO server.
-- Preconditions: 20260825 fishing migrations were applied and own IDs through 2153.
-- This migration owns item_template IDs 2154..2217. It is safe to rerun:
-- missing items/images are inserted, while existing aura items receive the
-- canonical display text (prefixed "Hào quang ") and their dedicated icon
-- defined below.

START TRANSACTION;

SET @aura_item_start = 2154;
-- One icon per aura: 26576..26639. These PNGs are installed in data/icon/x1..x4.
-- 26555..26575 are already allocated to existing custom item icons.
SET @aura_icon_start = 26576;

DROP TEMPORARY TABLE IF EXISTS `tmp_aura_catalog`;
CREATE TEMPORARY TABLE `tmp_aura_catalog` (
    `item_index` TINYINT UNSIGNED NOT NULL,
    `asset_id` SMALLINT UNSIGNED NOT NULL,
    `item_name` VARCHAR(255) NOT NULL,
    `item_description` VARCHAR(255) NOT NULL,
    PRIMARY KEY (`item_index`),
    UNIQUE KEY `uk_tmp_aura_asset` (`asset_id`)
) ENGINE=MEMORY DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT INTO `tmp_aura_catalog`
    (`item_index`, `asset_id`, `item_name`, `item_description`)
VALUES
(0, 0, 'Hắc Giáp Ma Tôn', 'Linh ảnh Ma Tôn hắc giáp trỗi dậy sau lưng, tỏa ra uy áp nặng nề và bí hiểm.'),
(1, 1, 'Thanh Long Hộ Thể', 'Thanh Long lục ngọc quấn quanh chiến thể, cuộn lên luồng long khí cổ xưa.'),
(2, 3, 'Cuồng Viên Kim Cang', 'Linh thể Cự Viên màu vàng gầm vang, phô bày sức mạnh cuồng bạo như kim cang.'),
(3, 4, 'Lôi Thần Kim Cang', 'Kim Cang Cự Viên rực sáng giữa những tia chớp, mang khí thế của lôi thần giáng thế.'),
(4, 5, 'Bạch Diễm Vô Tướng', 'Ngọn lửa trắng xám vô hình bao lấy cơ thể, chập chờn như một bóng ma thức tỉnh.'),
(5, 6, 'Lam Diễm Cuồng Phong', 'Lam hỏa bùng lên thành từng lớp sắc nhọn, cuốn quanh người như một cơn cuồng phong.'),
(6, 7, 'Hoàng Diễm Thiên Uy', 'Hoàng diễm chói lọi dựng thành vầng khí mạnh mẽ, biểu trưng cho ý chí không khuất phục.'),
(7, 8, 'Bạch Quang Bạo Khí', 'Bạch khí tinh khiết bùng nổ dữ dội, tạo nên quầng sáng sắc bén quanh chiến binh.'),
(8, 10, 'Huyết Ấn Ma Viêm', 'Ma viêm đỏ thẫm hòa trong bóng đen, kết thành huyết ấn tà dị dưới chân.'),
(9, 11, 'U Linh Tử Khí', 'Tử khí mờ ảo khép thành kén năng lượng, rung động như hơi thở của u linh.'),
(10, 12, 'Băng Tâm Huyết Diễm', 'Lõi băng lam được bao bọc bởi huyết diễm đỏ rực, dung hợp hai nguồn sức mạnh đối nghịch.'),
(11, 13, 'Xích Diễm Sát Khí', 'Xích diễm dựng đứng thành gai lửa, phóng ra sát khí nóng rực và quyết liệt.'),
(12, 14, 'Tử Diễm Cực Quang', 'Tử diễm rực trắng bung nở như cực quang, soi sáng toàn thân bằng nguồn khí áp đảo.'),
(13, 15, 'Hàn Băng Thánh Khí', 'Thánh khí băng trắng vươn cao thành nhiều tầng, lạnh lẽo nhưng tinh khiết tuyệt đối.'),
(14, 16, 'Ma Đằng Tử Diễm', 'Những nhánh tử lôi và ma đằng đan vào nhau, ôm trọn một lõi hắc khí sâu thẳm.'),
(15, 17, 'Lôi Cầu Tử Điện', 'Tử điện xoáy tròn thành lôi cầu, liên tục nứt sáng quanh người sử dụng.'),
(16, 18, 'Thiên Thanh Lôi Ngục', 'Lam quang khép kín như lôi ngục, những tia điện xanh liên tục chạy dọc vầng khí.'),
(17, 19, 'Tử Hỏa U Minh', 'Tử hỏa u minh bốc cao thành ngọn, tỏa ánh sáng ma mị giữa màn đêm.'),
(18, 20, 'Lôi Hỏa Lam Tinh', 'Lam hỏa pha bạch quang cuộn quanh các hạt sáng, tựa một cơn bão điện trong suốt.'),
(19, 21, 'Mộc Linh Lục Viêm', 'Lục viêm tràn đầy sinh khí quấn quanh cơ thể, lóe lên những đường sáng màu ngọc bích.'),
(20, 22, 'Nhật Viêm Kim Hỏa', 'Kim hỏa vàng cam bùng cháy như mặt trời nhỏ, tung ra vô số đốm sáng rực rỡ.'),
(21, 23, 'Huyết Ngục Tử Viêm', 'Tử viêm nhuốm đỏ như huyết ngục, cuộn chặt quanh một lõi năng lượng tà dị.'),
(22, 24, 'Ma Diễm Huyết Tâm', 'Viền ma diễm tím đỏ ôm lấy huyết tâm nứt sáng, tạo cảm giác nguy hiểm và cuồng loạn.'),
(23, 25, 'U Lam Quỷ Hỏa', 'Quỷ hỏa xanh thẫm cháy quanh bóng người, để lại những vệt điện lạnh giữa hư không.'),
(24, 26, 'Sinh Mệnh Lục Hỏa', 'Lục hỏa rực lên như nguồn sinh mệnh bất tận, bao phủ cơ thể bằng ánh xanh huyền bí.'),
(25, 27, 'Liệt Dương Cuồng Viêm', 'Cuồng viêm vàng cam cuộn thành vòng lửa lớn, nóng rực như liệt dương giáng xuống.'),
(26, 28, 'Hàn Lang Khiếu Nguyệt', 'Linh ảnh Hàn Lang xanh bạc gầm lên trong băng vụ, kéo theo từng dải khí lạnh dữ dội.'),
(27, 29, 'Lam Thiên Bạo Khí', 'Lam khí vút cao thành hàng trăm mũi sáng, bùng nổ như sức mạnh xé toạc bầu trời.'),
(28, 30, 'Tử Lôi Bạo Khí', 'Tử khí chói lòa dựng thành vầng lớn, được viền bởi những tia lôi điện sắc bén.'),
(29, 31, 'Thái Dương Bạo Khí', 'Kim quang rực lửa bắn vọt lên cao, mang vẻ chói lọi của thái dương giữa chiến trường.'),
(30, 32, 'Pha Lê Tử Giới', 'Các mảnh pha lê tím xoay quanh trụ sáng hồng, dựng nên một kết giới huyền ảo.'),
(31, 33, 'Lôi Viêm Hổ Phách', 'Khí diễm màu hổ phách bùng lên giữa bạch lôi, vừa nóng rực vừa uy nghiêm.'),
(32, 34, 'Bạch Kim Thần Khí', 'Thần khí bạc trắng lan rộng thành đôi cánh lửa, thanh khiết và lạnh lùng.'),
(33, 35, 'Hồng Liên Lôi Khí', 'Hồng quang rực tím nở như liên hoa, bao quanh bởi những nhánh lôi điện trắng.'),
(34, 36, 'Huyết Ảnh Lôi Khí', 'Huyết khí đỏ sẫm dựng cao, những tia lôi trắng xé ngang bóng tối đầy sát ý.'),
(35, 37, 'Xích Hồng Thần Khí', 'Xích hồng quang cháy rực thành nhiều tầng, phô bày khí thế kiêu hãnh của chiến thần.'),
(36, 38, 'Hoàng Kim Thánh Khí', 'Thánh khí vàng trắng trào dâng như biển lửa, tỏa ánh kim quang trang nghiêm.'),
(37, 39, 'Hắc Bạch Vô Cực', 'Hắc khí và bạch quang đối nghịch cùng bùng nổ, tạo thành vầng sức mạnh vô cực.'),
(38, 50, 'Ngũ Luân Nhật Quang', 'Năm quang luân vàng rực nối thành một trục sáng, luân chuyển như những tiểu nhật luân.'),
(39, 56, 'Cực Quang Băng Tinh', 'Băng tinh xanh hồng vươn thành gai sáng, lấp lánh như cực quang trong đêm lạnh.'),
(40, 57, 'Kim Diễm Tinh Thạch', 'Những mũi tinh thạch vàng cam rực cháy, phóng ánh lôi quang lên khắp vầng khí.'),
(41, 58, 'Thánh Quang Bạch Kim', 'Bạch kim quang cô đọng thành kết giới sáng chói, điểm xuyết các tia vàng tinh khiết.'),
(42, 59, 'Ngọc Bích Thiên Quang', 'Thiên quang xanh ngọc bùng lên từ mặt đất, trong trẻo như năng lượng của thiên nhiên.'),
(43, 60, 'Thâm Lam Tinh Vực', 'Lam quang sâu thẳm hòa cùng bụi sao, mở ra một vùng tinh vực thu nhỏ quanh cơ thể.'),
(44, 61, 'Huyết Tinh Diệt Quang', 'Huyết quang đỏ hồng xuyên qua làn khói tối, kết thành những mũi năng lượng hủy diệt.'),
(45, 63, 'Huyết Lang Phá Giới', 'Huyết Lang đỏ rực gầm vang trong ma vụ, như muốn xé nát ranh giới của chiến trường.'),
(46, 64, 'Tử Lôi Thiên Nộ', 'Tử khí vút cao cùng những tia sét trắng, cuồng nộ như lôi đình giáng thế.'),
(47, 65, 'Xích Lôi Thiên Phạt', 'Xích diễm đỏ máu bùng nổ dưới lôi quang, mang khí thế của một đòn thiên phạt.'),
(48, 66, 'Hồng Liên Hồn Châu', 'Một hồn châu hồng tím cháy trong linh hỏa, lơ lửng và tỏa sáng đầy mê hoặc.'),
(49, 77, 'Lục Trọng Ma Ấn', 'Sáu ma ấn đỏ đen xếp thành trụ, lần lượt thức tỉnh bằng ánh lửa u tối.'),
(50, 80, 'Tinh Vân Luân Hồi', 'Những vòng tinh vân hồng tím xoáy dọc không gian, tan hợp theo nhịp luân hồi.'),
(51, 81, 'Tử Tinh Pháp Luân', 'Các pháp luân tử tinh liên tiếp mở ra, biến đổi từ vòng xoáy thành tinh trận rực sáng.'),
(52, 82, 'Lục Nguyên Sinh Khí', 'Sinh khí lục sắc trào dâng thành ngọn, mềm mại nhưng ẩn chứa sức sống mãnh liệt.'),
(53, 83, 'Tử Hồng Mộng Diễm', 'Mộng diễm tím hồng lay động quanh bóng người, vừa rực rỡ vừa ma mị.'),
(54, 84, 'Huyết Hồng Mê Diễm', 'Huyết diễm đỏ hồng bùng thành từng nhánh, phủ lên cơ thể một màn khí mê hoặc.'),
(55, 86, 'Hỏa Ngục Cam Viêm', 'Cam viêm rực cháy như cửa ngục mở ra, kéo theo những hỏa cầu nhỏ xoay quanh.'),
(56, 87, 'Huyết Diễm Tu La', 'Huyết diễm đỏ thẫm quấn lấy chiến thể, tỏa ra uy thế lạnh lẽo của Tu La.'),
(57, 88, 'Hoàng Lôi Vương Khí', 'Hoàng khí sáng chói hòa cùng lôi quang, tạo nên phong thái uy nghi của bậc vương giả.'),
(58, 89, 'Thâm Lam Chiến Khí', 'Chiến khí xanh thẫm bốc cao thành sóng lửa, trầm lặng nhưng đầy sức ép.'),
(59, 90, 'Thiên Thanh Linh Khí', 'Linh khí xanh thiên thanh cuộn quanh cơ thể, phát sáng trong trẻo như bầu trời sau bão.'),
(60, 91, 'Bích Hải Linh Khí', 'Sắc xanh ngọc hòa cùng lam quang, dâng lên như thủy triều của biển sâu.'),
(61, 92, 'Lục Quang Thiên Mệnh', 'Lục quang vàng óng dựng thành vầng khí, rực sáng như dấu ấn của thiên mệnh.'),
(62, 93, 'Hoàng Lục Long Khí', 'Long khí hoàng lục mở rộng quanh người, tỏa ra những hỏa cầu và vệt sáng sắc bén.'),
(63, 94, 'Ngọc Lam Long Khí', 'Long khí xanh ngọc bao phủ chiến thể, thanh thoát mà vẫn mang uy lực mạnh mẽ.');

INSERT INTO `item_template`
    (`id`, `TYPE`, `gender`, `NAME`, `description`, `level`, `icon_id`, `part`,
     `is_up_to_up`, `power_require`, `gold`, `gem`, `head`, `body`, `leg`)
SELECT
    @aura_item_start + catalog.item_index,
    11,
    3,
    CONCAT('Hào quang ', catalog.item_name),
    catalog.item_description,
    0,
    @aura_icon_start + catalog.item_index,
    catalog.asset_id,
    0, 0, 0, 0, -1, -1, -1
FROM `tmp_aura_catalog` catalog
WHERE NOT EXISTS (
    SELECT 1
    FROM `item_template` existing
    WHERE existing.id = @aura_item_start + catalog.item_index
)
ORDER BY catalog.item_index;

-- Reruns update the user-facing text and dedicated icon of rows whose ID/asset
-- pair belongs to this catalog. Other properties and unrelated colliding IDs
-- are kept.
UPDATE `item_template` item
JOIN `tmp_aura_catalog` catalog
  ON item.id = @aura_item_start + catalog.item_index
 AND item.part = catalog.asset_id
SET item.`NAME` = CONCAT('Hào quang ', catalog.item_name),
    item.`description` = catalog.item_description,
    item.icon_id = @aura_icon_start + catalog.item_index;

-- Add missing metadata for both aura layers. The IDs are allocated after the
-- current maximum so this remains safe for a database with custom image rows.
SET @aura_next_image_id = (SELECT COALESCE(MAX(id), 0) FROM `img_by_name`);
INSERT INTO `img_by_name` (`id`, `NAME`, `n_frame`)
SELECT
    @aura_next_image_id := @aura_next_image_id + 1,
    CONCAT('aura_', catalog.asset_id, '_', layer.layer_id),
    CASE WHEN catalog.asset_id IN (3, 4) THEN 8 ELSE 4 END
FROM `tmp_aura_catalog` catalog
CROSS JOIN (SELECT 0 AS layer_id UNION ALL SELECT 1) layer
WHERE NOT EXISTS (
    SELECT 1
    FROM `img_by_name` existing
    WHERE existing.`NAME` = CONCAT('aura_', catalog.asset_id, '_', layer.layer_id)
)
ORDER BY catalog.item_index, layer.layer_id;

DROP TEMPORARY TABLE `tmp_aura_catalog`;

COMMIT;
