SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO `item_template`
(`id`, `TYPE`, `gender`, `NAME`, `description`, `level`, `icon_id`, `part`, `is_up_to_up`, `power_require`, `gold`, `gem`, `head`, `body`, `leg`)
VALUES
(2017, 25, 3, 'Nhẫn Sơ Cấp', 'Sự khởi đầu của 1 huyền thoại', 0, 19045, -1, 0, 0, 0, 0, -1, -1, -1),
(2018, 25, 3, 'Nhẫn Quang Minh', 'Chiếc nhẫn được rèn từ vàng nguyên chất, tỏa ra ánh sáng dịu nhẹ của bình minh. Nó xua tan bóng tối và mang lại hy vọng cho người đeo.', 1, 19046, -1, 0, 0, 0, 0, -1, -1, -1),
(2019, 25, 3, 'Nhẫn Băng Tinh', 'Một khối băng vĩnh cửu được nén lại thành hình chiếc nhẫn. Hơi lạnh tỏa ra từ nó có thể làm chậm kẻ thù và bảo vệ chủ nhân khỏi hỏa công.', 2, 19047, -1, 0, 0, 0, 0, -1, -1, -1),
(2020, 25, 3, 'Nhẫn Lục Diệp', 'Được cho là chế tác từ cành cây của Cây Thế giới. Nó tràn đầy sức sống và kết nối người đeo với thiên nhiên.', 3, 19048, -1, 0, 0, 0, 0, -1, -1, -1),
(2021, 25, 3, 'Nhẫn Hắc Ám', 'Ánh sáng tím sẫm bao quanh chiếc nhẫn như một màn đêm vô tận. Nó chứa đựng sức mạnh của hư không và bóng đêm.', 4, 19049, -1, 0, 0, 0, 0, -1, -1, -1),
(2022, 25, 3, 'Nhẫn Dung Nham', 'Vòng kim loại nóng chảy đang sôi sục, mang trong mình sức nóng của lõi trái đất. Sức mạnh của nó có thể đốt cháy mọi thứ.', 5, 19050, -1, 0, 0, 0, 0, -1, -1, -1),
(2023, 25, 3, 'Nhẫn Huyết Nguyệt', 'Chiếc nhẫn đỏ rực như máu, được cho là hấp thụ sức mạnh của mặt trăng đỏ. Nó mang lại sức mạnh cuồng bạo cho người sở hữu.', 6, 19051, -1, 0, 0, 0, 0, -1, -1, -1),
(2024, 25, 3, 'Nhẫn Kim Quang', 'Một chiếc nhẫn vàng ròng, phát ra luồng hào quang chói lóa, như thể được các vị thần ban phước. Nó tượng trưng cho sự giàu có và quyền lực.', 7, 19052, -1, 0, 0, 0, 0, -1, -1, -1),
(2025, 25, 3, 'Nhẫn Thiên Thạch', 'Viên ngọc trai phát sáng lấp lánh được đính trên một dải kim loại óng ánh. Nó mang trong mình sức mạnh của các vì sao.', 8, 19053, -1, 0, 0, 0, 0, -1, -1, -1),
(2026, 14, 3, 'Đá hoàng kim', 'Sử dụng để nâng cấp nhẫn', 0, 19054, -1, 1, 0, 0, 0, -1, -1, -1)
ON DUPLICATE KEY UPDATE
`TYPE` = VALUES(`TYPE`),
`gender` = VALUES(`gender`),
`NAME` = VALUES(`NAME`),
`description` = VALUES(`description`),
`level` = VALUES(`level`),
`icon_id` = VALUES(`icon_id`),
`part` = VALUES(`part`),
`is_up_to_up` = VALUES(`is_up_to_up`),
`power_require` = VALUES(`power_require`),
`gold` = VALUES(`gold`),
`gem` = VALUES(`gem`),
`head` = VALUES(`head`),
`body` = VALUES(`body`),
`leg` = VALUES(`leg`);

COMMIT;
