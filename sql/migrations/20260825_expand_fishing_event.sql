-- Fishing Event v2: detailed items, tabbed shops and lossless map asset reuse.
-- Requires fishing item templates 2083..2153 from 20260825_add_fishing_event.sql.

START TRANSACTION;

UPDATE item_template
SET description = CASE id
    WHEN 2083 THEN 'Cá cấp 1-4; không cộng tỉ lệ kéo.'
    WHEN 2084 THEN 'Cá cấp 1-7; cộng 5% tỉ lệ kéo.'
    WHEN 2085 THEN 'Cá cấp 1-10; cộng 10% tỉ lệ kéo.'
    WHEN 2086 THEN 'Cá cấp 1-13; cộng 15% tỉ lệ kéo.'
    WHEN 2087 THEN 'Cá cấp 1-15; cộng 20% tỉ lệ kéo.'
    WHEN 2088 THEN 'Chọn mồi cá cấp 1-4.'
    WHEN 2089 THEN 'Chọn mồi cá cấp 1-6.'
    WHEN 2090 THEN 'Chọn mồi cá cấp 1-8.'
    WHEN 2091 THEN 'Chọn mồi cá cấp 2-10; giảm nhẹ rác.'
    WHEN 2092 THEN 'Chọn mồi cá cấp 3-12; giảm rác.'
    WHEN 2093 THEN 'Mồi cá cấp 5-14; tăng vật phẩm.'
    WHEN 2094 THEN 'Mồi cá cấp 7-15; tăng cá hiếm.'
    WHEN 2095 THEN 'Dây câu cơ bản.'
    WHEN 2096 THEN 'Dây giảm nguy cơ đứt.'
    WHEN 2097 THEN 'Dây tốt nhất; chỉ 1% đứt.'
    WHEN 2098 THEN 'Phao phản ứng 1,5 giây.'
    WHEN 2099 THEN 'Phao: thêm 0,5 giây; tăng cá cấp 8.'
    WHEN 2100 THEN 'Phao: thêm 1 giây; tăng cá cấp 10.'
    WHEN 2101 THEN 'Máy câu: cộng 5% tỉ lệ kéo.'
    WHEN 2102 THEN 'Máy câu: cộng 10% tỉ lệ kéo.'
    WHEN 2103 THEN 'Máy câu: cộng 15% tỉ lệ kéo.'
    WHEN 2104 THEN 'Lưỡi chùm: 10% nhận thêm cá.'
    WHEN 2105 THEN 'Lưỡi chùm: 20% nhận thêm cá.'
    WHEN 2106 THEN 'Lưỡi chùm: 30% thêm 1, 5% thêm 2 cá.'
    WHEN 2107 THEN 'Tự cứu lượt câu khi dây đứt, sau đó tiêu hao 1 Bộ Sửa.'
    WHEN 2108 THEN 'Dùng: tăng cá hiếm và vật phẩm đặc biệt trong 15 phút.'
    WHEN 2109 THEN 'Mang theo: tăng 3% Xu Ngư Phủ khi đổi toàn bộ cá.'
    WHEN 2110 THEN 'Mang theo: mỗi lượt có 10% không tiêu hao mồi.'
    WHEN 2111 THEN 'Huy hiệu hiếm dùng trong chế tạo và đổi thưởng.'
    WHEN 2112 THEN 'Dùng: tăng cơ hội gặp cá hiếm trong 15 phút.'
    WHEN 2113 THEN 'Dùng để mở ngẫu nhiên Xu, mồi, trang bị hoặc đồ hỗ trợ.'
    WHEN 2114 THEN 'Mang theo: tăng 5% Xu Ngư Phủ khi đổi toàn bộ cá.'
    WHEN 2115 THEN 'Xu sự kiện câu cá.'
    WHEN 2116 THEN 'Vật phẩm từ biển.'
    WHEN 2117 THEN 'Vật phẩm từ biển.'
    WHEN 2118 THEN 'Vật phẩm từ biển.'
    WHEN 2119 THEN 'Vật phẩm từ biển.'
    WHEN 2120 THEN 'Vật phẩm từ biển.'
    WHEN 2121 THEN 'Vật phẩm từ biển.'
    WHEN 2122 THEN 'Vật phẩm từ biển.'
    WHEN 2123 THEN 'Vật phẩm từ biển.'
    ELSE CASE
        WHEN id BETWEEN 2124 AND 2138 THEN CONCAT('Cá cấp ', id - 2123, '.')
        WHEN id BETWEEN 2139 AND 2153 THEN 'Cá khổng lồ, đổi x3 Xu.'
        ELSE description
    END
END
WHERE id BETWEEN 2083 AND 2153;

-- Recreate only the IDs reserved by this migration, making it safe to rerun.
DELETE FROM item_shop_option WHERE item_shop_id BETWEEN 100200 AND 100239;
DELETE FROM item_shop WHERE id BETWEEN 100200 AND 100239 OR tab_id BETWEEN 1130 AND 1135;
DELETE FROM tab_shop WHERE id BETWEEN 1130 AND 1135;
DELETE FROM shop WHERE id IN (113, 114) OR tag_name IN ('FISHING_SHOP', 'FISHING_POINT_SHOP');

INSERT INTO shop (id, npc_id, tag_name, type_shop) VALUES
    (113, 113, 'FISHING_SHOP', 0),
    (114, 113, 'FISHING_POINT_SHOP', 3);

INSERT INTO tab_shop (id, shop_id, `NAME`) VALUES
    (1130, 113, 'Cần<>Câu'),
    (1131, 113, 'Mồi<>Câu'),
    (1132, 113, 'Phụ<>kiện'),
    (1133, 113, 'Hỗ<>trợ'),
    (1134, 114, 'Cải<>trang'),
    (1135, 114, 'Vật<>phẩm');

INSERT INTO item_shop
    (id, tab_id, temp_id, is_new, is_sell, type_sell, cost, icon_spec, option_mode, create_time)
VALUES
    (100200,1130,2083,0,1,0,50000,0,0,'2026-08-25 20:00:00'),
    (100201,1130,2084,0,1,0,250000,0,0,'2026-08-25 20:00:01'),
    (100202,1130,2085,0,1,0,1000000,0,0,'2026-08-25 20:00:02'),
    (100203,1130,2086,1,1,0,5000000,0,0,'2026-08-25 20:00:03'),
    (100204,1131,2088,0,1,0,1000,0,0,'2026-08-25 20:01:00'),
    (100205,1131,2089,0,1,0,2500,0,0,'2026-08-25 20:01:01'),
    (100206,1131,2090,0,1,0,5000,0,0,'2026-08-25 20:01:02'),
    (100207,1131,2091,0,1,0,10000,0,0,'2026-08-25 20:01:03'),
    (100208,1131,2092,0,1,0,25000,0,0,'2026-08-25 20:01:04'),
    (100209,1131,2093,1,1,0,60000,0,0,'2026-08-25 20:01:05'),
    (100210,1132,2095,0,1,0,50000,0,0,'2026-08-25 20:02:00'),
    (100211,1132,2096,0,1,0,500000,0,0,'2026-08-25 20:02:01'),
    (100212,1132,2098,0,1,0,50000,0,0,'2026-08-25 20:02:02'),
    (100213,1132,2099,0,1,0,750000,0,0,'2026-08-25 20:02:03'),
    (100214,1132,2101,0,1,0,100000,0,0,'2026-08-25 20:02:04'),
    (100215,1132,2102,0,1,0,1000000,0,0,'2026-08-25 20:02:05'),
    (100216,1132,2104,0,1,0,250000,0,0,'2026-08-25 20:02:06'),
    (100217,1132,2105,1,1,0,1500000,0,0,'2026-08-25 20:02:07'),
    (100218,1133,2107,0,1,0,100000,0,0,'2026-08-25 20:03:00'),
    (100219,1133,2108,0,1,0,500000,0,0,'2026-08-25 20:03:01'),
    (100220,1133,2109,0,1,0,1000000,0,0,'2026-08-25 20:03:02'),
    (100221,1133,2110,0,1,0,750000,0,0,'2026-08-25 20:03:03'),
    (100222,1133,2112,0,1,0,600000,0,0,'2026-08-25 20:03:04'),
    (100223,1133,2113,1,1,0,300000,0,0,'2026-08-25 20:03:05'),
    (100224,1133,2114,1,1,0,3000000,0,0,'2026-08-25 20:03:06'),
    (100225,1134,2001,0,1,0,2500,26432,0,'2026-08-25 20:04:00'),
    (100226,1134,2002,0,1,0,2500,26432,0,'2026-08-25 20:04:01'),
    (100227,1134,2003,0,1,0,2500,26432,0,'2026-08-25 20:04:02'),
    (100228,1134,2004,0,1,0,2500,26432,0,'2026-08-25 20:04:03'),
    (100229,1134,2005,0,1,0,2500,26432,0,'2026-08-25 20:04:04'),
    (100230,1134,2006,0,1,0,2500,26432,0,'2026-08-25 20:04:05'),
    (100231,1134,2007,0,1,0,2500,26432,0,'2026-08-25 20:04:06'),
    (100232,1134,2008,0,1,0,2500,26432,0,'2026-08-25 20:04:07'),
    (100233,1134,2009,0,1,0,2500,26432,0,'2026-08-25 20:04:08'),
    (100234,1135,2087,1,1,0,4000,26432,0,'2026-08-25 20:05:00'),
    (100235,1135,2094,1,1,0,35,26432,0,'2026-08-25 20:05:01'),
    (100236,1135,2097,1,1,0,1200,26432,0,'2026-08-25 20:05:02'),
    (100237,1135,2100,1,1,0,1400,26432,0,'2026-08-25 20:05:03'),
    (100238,1135,2103,1,1,0,1800,26432,0,'2026-08-25 20:05:04'),
    (100239,1135,2106,1,1,0,2500,26432,0,'2026-08-25 20:05:05');

-- These PNG pairs are byte-identical at x1..x4. Reusing their canonical IDs
-- avoids downloading the same large panorama again on consecutive maps.
UPDATE bg_item_template
SET image_id = CASE image_id
    WHEN 773 THEN 620 WHEN 781 THEN 627 WHEN 856 THEN 855
    WHEN 987 THEN 919 WHEN 988 THEN 920 WHEN 1018 THEN 919 WHEN 1019 THEN 920
    WHEN 1020 THEN 989 WHEN 1051 THEN 919 WHEN 1052 THEN 920 WHEN 1053 THEN 989
    WHEN 1064 THEN 1001 WHEN 1070 THEN 1008 WHEN 1074 THEN 1010
    WHEN 1097 THEN 1089 WHEN 1108 THEN 1100 WHEN 1110 THEN 1093 WHEN 1111 THEN 1099
    ELSE image_id
END
WHERE image_id IN (773,781,856,987,988,1018,1019,1020,1051,1052,1053,1064,1070,1074,1097,1108,1110,1111);

COMMIT;
