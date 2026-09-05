-- Giai đoạn 4: Cửa hàng bang, Phiếu quà bang và bộ vật phẩm hỗ trợ.
-- Server tự tạo bảng runtime; migration này là nguồn triển khai/review cho DB mới.

CREATE TABLE IF NOT EXISTS clan_shop_stock (
  clan_id INT NOT NULL,
  item_template_id INT NOT NULL,
  stock INT NOT NULL DEFAULT 0,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (clan_id,item_template_id), KEY idx_clan_shop_stock_clan (clan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS clan_shop_purchase (
  id BIGINT NOT NULL AUTO_INCREMENT,
  clan_id INT NOT NULL, player_id BIGINT NOT NULL, item_template_id INT NOT NULL,
  day_key INT NOT NULL, quantity INT NOT NULL DEFAULT 0, request_id VARCHAR(80) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_clan_shop_request (clan_id,request_id),
  UNIQUE KEY uk_clan_shop_daily (clan_id,player_id,item_template_id,day_key),
  KEY idx_clan_shop_player_day (clan_id,player_id,day_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS clan_shop_restock (
  id BIGINT NOT NULL AUTO_INCREMENT,
  clan_id INT NOT NULL, item_template_id INT NOT NULL, request_id VARCHAR(80) NOT NULL,
  stock_before INT NOT NULL, stock_after INT NOT NULL, actor_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_clan_shop_restock_request (clan_id,request_id),
  KEY idx_clan_shop_restock_clan (clan_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS clan_gift_daily (
  clan_id INT NOT NULL, player_id BIGINT NOT NULL, day_key INT NOT NULL,
  sent_count INT NOT NULL DEFAULT 0, received_count INT NOT NULL DEFAULT 0,
  PRIMARY KEY (clan_id,player_id,day_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS clan_gift_pair (
  clan_id INT NOT NULL, sender_id BIGINT NOT NULL, receiver_id BIGINT NOT NULL, day_key INT NOT NULL,
  PRIMARY KEY (clan_id,sender_id,receiver_id,day_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS clan_pending_reward (
  id BIGINT NOT NULL AUTO_INCREMENT, clan_id INT NOT NULL, player_id BIGINT NOT NULL,
  source_type VARCHAR(32) NOT NULL, gold_amount BIGINT NOT NULL DEFAULT 0, ruby_amount INT NOT NULL DEFAULT 0,
  request_id VARCHAR(80) NOT NULL, status TINYINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, claimed_at TIMESTAMP NULL DEFAULT NULL,
  PRIMARY KEY (id), UNIQUE KEY uk_clan_pending_request (request_id), KEY idx_clan_pending_player (player_id,status,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS clan_gift_request (
  request_id VARCHAR(80) NOT NULL, clan_id INT NOT NULL, sender_id BIGINT NOT NULL, receiver_id BIGINT NOT NULL,
  gold_amount BIGINT NOT NULL DEFAULT 0, ruby_amount INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (request_id), KEY idx_clan_gift_sender_day (clan_id,sender_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS clan_item_storage (
  clan_id INT NOT NULL,
  slot_index INT NOT NULL,
  item_template_id INT NOT NULL,
  quantity INT NOT NULL DEFAULT 0,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (clan_id,slot_index),
  UNIQUE KEY uk_clan_storage_item (clan_id,item_template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS clan_item_storage_audit (
  id BIGINT NOT NULL AUTO_INCREMENT,
  clan_id INT NOT NULL,
  actor_id BIGINT NOT NULL,
  action_type VARCHAR(24) NOT NULL,
  item_template_id INT NOT NULL,
  quantity_delta INT NOT NULL,
  quantity_after INT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_clan_storage_audit (clan_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT INTO item_template
(`id`,`TYPE`,`gender`,`NAME`,`description`,`level`,`icon_id`,`part`,`is_up_to_up`,`power_require`,`gold`,`gem`,`head`,`body`,`leg`) VALUES
(2252,29,3,'Vàng từ quái bang 1 ngày','Tăng 20% Vàng nhận được từ quái cho tất cả thành viên bang trong 1 ngày.',1,26643,-1,1,0,0,0,-1,-1,-1),
(2253,29,3,'Vàng từ quái bang 3 ngày','Tăng 20% Vàng nhận được từ quái cho tất cả thành viên bang trong 3 ngày.',1,26644,-1,1,0,0,0,-1,-1,-1),
(2254,29,3,'Vàng từ quái bang 7 ngày','Tăng 20% Vàng nhận được từ quái cho tất cả thành viên bang trong 7 ngày.',1,26645,-1,1,0,0,0,-1,-1,-1),
(2255,29,3,'Tiềm năng bang 1 ngày','Tăng 15% Tiềm năng, sức mạnh cho tất cả thành viên bang trong 1 ngày.',1,26646,-1,1,0,0,0,-1,-1,-1),
(2256,29,3,'Tiềm năng bang 3 ngày','Tăng 15% Tiềm năng, sức mạnh cho tất cả thành viên bang trong 3 ngày.',1,26647,-1,1,0,0,0,-1,-1,-1),
(2257,29,3,'Tiềm năng bang 7 ngày','Tăng 15% Tiềm năng, sức mạnh cho tất cả thành viên bang trong 7 ngày.',1,26648,-1,1,0,0,0,-1,-1,-1),
(2258,29,3,'Hồi HP bang 1 ngày','Hồi 1% HP mỗi 5 giây cho tất cả thành viên bang trong 1 ngày.',1,26649,-1,1,0,0,0,-1,-1,-1),
(2259,29,3,'Hồi HP bang 3 ngày','Hồi 1% HP mỗi 5 giây cho tất cả thành viên bang trong 3 ngày.',1,26650,-1,1,0,0,0,-1,-1,-1),
(2260,29,3,'Hồi HP bang 7 ngày','Hồi 1% HP mỗi 5 giây cho tất cả thành viên bang trong 7 ngày.',1,26651,-1,1,0,0,0,-1,-1,-1),
(2261,29,3,'Hồi KI bang 1 ngày','Hồi 1% KI mỗi 5 giây cho tất cả thành viên bang trong 1 ngày.',1,26652,-1,1,0,0,0,-1,-1,-1),
(2262,29,3,'Hồi KI bang 3 ngày','Hồi 1% KI mỗi 5 giây cho tất cả thành viên bang trong 3 ngày.',1,26653,-1,1,0,0,0,-1,-1,-1),
(2263,29,3,'Hồi KI bang 7 ngày','Hồi 1% KI mỗi 5 giây cho tất cả thành viên bang trong 7 ngày.',1,26654,-1,1,0,0,0,-1,-1,-1),
(2264,29,3,'May mắn bang 1 ngày','Tăng 10% May mắn cho tất cả thành viên bang trong 1 ngày.',1,26655,-1,1,0,0,0,-1,-1,-1),
(2265,29,3,'May mắn bang 3 ngày','Tăng 10% May mắn cho tất cả thành viên bang trong 3 ngày.',1,26656,-1,1,0,0,0,-1,-1,-1),
(2266,29,3,'May mắn bang 7 ngày','Tăng 10% May mắn cho tất cả thành viên bang trong 7 ngày.',1,26657,-1,1,0,0,0,-1,-1,-1),
(2267,29,3,'Sức đánh bang 1 ngày','Tăng 10% Sức đánh cho tất cả thành viên bang trong 1 ngày.',1,26658,-1,1,0,0,0,-1,-1,-1),
(2268,29,3,'Sức đánh bang 3 ngày','Tăng 10% Sức đánh cho tất cả thành viên bang trong 3 ngày.',1,26659,-1,1,0,0,0,-1,-1,-1),
(2269,29,3,'Sức đánh bang 7 ngày','Tăng 10% Sức đánh cho tất cả thành viên bang trong 7 ngày.',1,26660,-1,1,0,0,0,-1,-1,-1),
(2270,29,3,'Giảm 12 giờ nâng Cây bang','Giảm 12 giờ khi Cây bang đang nâng cấp.',1,26661,-1,1,0,0,0,-1,-1,-1),
(2271,29,3,'Giảm 24 giờ nâng Cây bang','Giảm 24 giờ khi Cây bang đang nâng cấp.',1,26662,-1,1,0,0,0,-1,-1,-1),
(2272,29,3,'Vé đổi tên bang','Chủ bang dùng để đổi tên bang hội.',1,26663,-1,1,0,0,0,-1,-1,-1),
(2273,29,3,'Hộp quà bang','Nhận khi thu hoạch Cây bang; mở nhận ngẫu nhiên vật phẩm hỗ trợ buff bang.',1,26664,-1,1,0,0,0,-1,-1,-1),
(2274,29,3,'Phiếu quà bang','Nhận khi điểm danh bang; tiêu hao khi tặng quà cho một thành viên khác.',1,26665,-1,1,0,0,0,-1,-1,-1)
ON DUPLICATE KEY UPDATE `TYPE`=VALUES(`TYPE`),`gender`=VALUES(`gender`),`NAME`=VALUES(`NAME`),`description`=VALUES(`description`),`level`=VALUES(`level`),`icon_id`=VALUES(`icon_id`),`part`=VALUES(`part`),`is_up_to_up`=VALUES(`is_up_to_up`),`power_require`=VALUES(`power_require`),`gold`=VALUES(`gold`),`gem`=VALUES(`gem`),`head`=VALUES(`head`),`body`=VALUES(`body`),`leg`=VALUES(`leg`);

INSERT INTO gift_box_config (`box_template_id`,`enabled`,`min_empty_slots`,`consume_quantity`,`draw_count`,`config_json`) VALUES
(2273,1,1,1,1,'{"boxTemplateId":2273,"enabled":true,"minEmptySlots":1,"consumeQuantity":1,"drawCount":1,"rewards":[{"itemId":2252,"weight":20,"quantityMin":1,"quantityMax":1,"gender":3,"initBaseOptions":false,"useDefaultOptions":true,"options":[],"expiry":[{"mode":"permanent","weight":1,"daysMin":0,"daysMax":0}]},{"itemId":2255,"weight":20,"quantityMin":1,"quantityMax":1,"gender":3,"initBaseOptions":false,"useDefaultOptions":true,"options":[],"expiry":[{"mode":"permanent","weight":1,"daysMin":0,"daysMax":0}]},{"itemId":2258,"weight":15,"quantityMin":1,"quantityMax":1,"gender":3,"initBaseOptions":false,"useDefaultOptions":true,"options":[],"expiry":[{"mode":"permanent","weight":1,"daysMin":0,"daysMax":0}]},{"itemId":2261,"weight":15,"quantityMin":1,"quantityMax":1,"gender":3,"initBaseOptions":false,"useDefaultOptions":true,"options":[],"expiry":[{"mode":"permanent","weight":1,"daysMin":0,"daysMax":0}]},{"itemId":2264,"weight":10,"quantityMin":1,"quantityMax":1,"gender":3,"initBaseOptions":false,"useDefaultOptions":true,"options":[],"expiry":[{"mode":"permanent","weight":1,"daysMin":0,"daysMax":0}]},{"itemId":2274,"weight":20,"quantityMin":1,"quantityMax":1,"gender":3,"initBaseOptions":false,"useDefaultOptions":true,"options":[],"expiry":[{"mode":"permanent","weight":1,"daysMin":0,"daysMax":0}]}]}')
ON DUPLICATE KEY UPDATE `enabled`=VALUES(`enabled`),`min_empty_slots`=VALUES(`min_empty_slots`),`consume_quantity`=VALUES(`consume_quantity`),`draw_count`=VALUES(`draw_count`),`config_json`=VALUES(`config_json`);

-- Cửa hàng riêng của Chủ bang tại NPC Dr. Drief. type_sell: 0=Vàng bang, 1=Ngọc bang, 2=Capsule bang.
INSERT INTO shop (npc_id,tag_name,type_shop)
SELECT 10,'SHOP_CLAN_OWNER',3
WHERE NOT EXISTS (SELECT 1 FROM shop WHERE tag_name='SHOP_CLAN_OWNER');
SET @clan_owner_shop_id=(SELECT id FROM shop WHERE tag_name='SHOP_CLAN_OWNER' ORDER BY id LIMIT 1);
INSERT INTO tab_shop (shop_id,NAME)
SELECT @clan_owner_shop_id,'Vật phẩm<>kho bang'
WHERE NOT EXISTS (SELECT 1 FROM tab_shop WHERE shop_id=@clan_owner_shop_id AND NAME='Vật phẩm<>kho bang');
SET @clan_owner_tab_id=(SELECT id FROM tab_shop WHERE shop_id=@clan_owner_shop_id AND NAME='Vật phẩm<>kho bang' ORDER BY id LIMIT 1);

INSERT INTO item_shop (tab_id,temp_id,is_new,is_sell,type_sell,cost,icon_spec,option_mode,create_time)
SELECT @clan_owner_tab_id,seed.temp_id,1,1,seed.type_sell,seed.cost,seed.icon_spec,0,CURRENT_TIMESTAMP
FROM (
  SELECT 2252 temp_id,2 type_sell,4 cost,7223 icon_spec UNION ALL SELECT 2253,2,4,7223 UNION ALL SELECT 2254,2,4,7223
  UNION ALL SELECT 2255,2,4,7223 UNION ALL SELECT 2256,2,4,7223 UNION ALL SELECT 2257,2,4,7223
  UNION ALL SELECT 2258,2,4,7223 UNION ALL SELECT 2259,2,4,7223 UNION ALL SELECT 2260,2,4,7223
  UNION ALL SELECT 2261,0,500000,927 UNION ALL SELECT 2262,0,500000,927 UNION ALL SELECT 2263,0,500000,927
  UNION ALL SELECT 2264,0,500000,927 UNION ALL SELECT 2265,0,500000,927 UNION ALL SELECT 2266,0,500000,927
  UNION ALL SELECT 2267,0,500000,927
  UNION ALL SELECT 2268,1,15,932 UNION ALL SELECT 2269,1,15,932 UNION ALL SELECT 2270,1,15,932 UNION ALL SELECT 2271,1,15,932
  UNION ALL SELECT 2272,1,20,932 UNION ALL SELECT 2273,1,20,932 UNION ALL SELECT 2274,1,20,932
) seed
WHERE NOT EXISTS (SELECT 1 FROM item_shop current_item WHERE current_item.tab_id=@clan_owner_tab_id AND current_item.temp_id=seed.temp_id);

UPDATE item_shop SET
  is_new=1,is_sell=1,option_mode=0,
  type_sell=CASE WHEN temp_id BETWEEN 2252 AND 2260 THEN 2 WHEN temp_id BETWEEN 2261 AND 2267 THEN 0 ELSE 1 END,
  cost=CASE WHEN temp_id BETWEEN 2252 AND 2260 THEN 4 WHEN temp_id BETWEEN 2261 AND 2267 THEN 500000 WHEN temp_id BETWEEN 2268 AND 2271 THEN 15 ELSE 20 END,
  icon_spec=CASE WHEN temp_id BETWEEN 2252 AND 2260 THEN 7223 WHEN temp_id BETWEEN 2261 AND 2267 THEN 927 ELSE 932 END
WHERE tab_id=@clan_owner_tab_id AND temp_id BETWEEN 2252 AND 2274;

-- Buff dùng chung: cùng loại chỉ nối dài expires_at, effect_percent không cộng chồng.
CREATE TABLE IF NOT EXISTS clan_active_buff (
  clan_id INT NOT NULL, buff_type TINYINT NOT NULL, effect_percent INT NOT NULL,
  expires_at BIGINT NOT NULL, version BIGINT NOT NULL DEFAULT 1,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (clan_id,buff_type), KEY idx_clan_buff_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE IF NOT EXISTS clan_active_buff_audit (
  id BIGINT NOT NULL AUTO_INCREMENT, clan_id INT NOT NULL, actor_id BIGINT NOT NULL,
  item_template_id INT NOT NULL, buff_type TINYINT NOT NULL, days_added INT NOT NULL,
  expires_before BIGINT NOT NULL, expires_after BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id), KEY idx_clan_buff_audit (clan_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 2273 nhận khi thu hoạch và 2274 nhận khi điểm danh, không bán trong shop.
DELETE FROM item_shop_option WHERE item_shop_id IN (
  SELECT id FROM item_shop WHERE tab_id=@clan_owner_tab_id AND temp_id IN (2273,2274)
);
DELETE FROM item_shop WHERE tab_id=@clan_owner_tab_id AND temp_id IN (2273,2274);
UPDATE item_shop SET is_new=1,is_sell=1,option_mode=0,type_sell=
  CASE WHEN temp_id BETWEEN 2258 AND 2263 THEN 0 ELSE 1 END,
  cost=CASE temp_id
    WHEN 2272 THEN 1000 WHEN 2270 THEN 200 WHEN 2271 THEN 350
    WHEN 2258 THEN 10000000 WHEN 2259 THEN 25000000 WHEN 2260 THEN 50000000
    WHEN 2261 THEN 10000000 WHEN 2262 THEN 25000000 WHEN 2263 THEN 50000000
    WHEN 2267 THEN 100 WHEN 2268 THEN 250 WHEN 2269 THEN 500
    WHEN 2264 THEN 500 WHEN 2265 THEN 1000 WHEN 2266 THEN 2500
    WHEN 2255 THEN 50 WHEN 2256 THEN 100 WHEN 2257 THEN 250
    WHEN 2252 THEN 50 WHEN 2253 THEN 100 WHEN 2254 THEN 250 END,
  icon_spec=CASE WHEN temp_id BETWEEN 2258 AND 2263 THEN 927 ELSE 932 END,
  create_time=TIMESTAMPADD(SECOND,-(CASE temp_id
    WHEN 2272 THEN 0 WHEN 2270 THEN 1 WHEN 2271 THEN 2
    WHEN 2258 THEN 3 WHEN 2259 THEN 4 WHEN 2260 THEN 5
    WHEN 2261 THEN 6 WHEN 2262 THEN 7 WHEN 2263 THEN 8
    WHEN 2267 THEN 9 WHEN 2268 THEN 10 WHEN 2269 THEN 11
    WHEN 2264 THEN 12 WHEN 2265 THEN 13 WHEN 2266 THEN 14
    WHEN 2255 THEN 15 WHEN 2256 THEN 16 WHEN 2257 THEN 17
    WHEN 2252 THEN 18 WHEN 2253 THEN 19 WHEN 2254 THEN 20 END),CURRENT_TIMESTAMP)
WHERE tab_id=@clan_owner_tab_id AND temp_id BETWEEN 2252 AND 2272;

-- Mặc định Hộp quà bang bốc đều một trong 18 vật phẩm buff; Admin Data có thể mở rộng.
UPDATE gift_box_config SET enabled=1,min_empty_slots=1,consume_quantity=1,draw_count=1,
config_json='{"boxTemplateId":2273,"enabled":true,"minEmptySlots":1,"consumeQuantity":1,"drawCount":1,"rewards":[{"itemId":2252,"weight":1,"quantityMin":1,"quantityMax":1,"gender":3,"useDefaultOptions":true,"options":[],"expiry":[{"mode":"permanent","weight":1,"daysMin":0,"daysMax":0}]},{"itemId":2253,"weight":1,"quantityMin":1,"quantityMax":1,"gender":3,"useDefaultOptions":true,"options":[],"expiry":[{"mode":"permanent","weight":1,"daysMin":0,"daysMax":0}]},{"itemId":2254,"weight":1,"quantityMin":1,"quantityMax":1,"gender":3,"useDefaultOptions":true,"options":[],"expiry":[{"mode":"permanent","weight":1,"daysMin":0,"daysMax":0}]},{"itemId":2255,"weight":1,"quantityMin":1,"quantityMax":1,"gender":3,"useDefaultOptions":true,"options":[],"expiry":[{"mode":"permanent","weight":1,"daysMin":0,"daysMax":0}]},{"itemId":2256,"weight":1,"quantityMin":1,"quantityMax":1,"gender":3,"useDefaultOptions":true,"options":[],"expiry":[{"mode":"permanent","weight":1,"daysMin":0,"daysMax":0}]},{"itemId":2257,"weight":1,"quantityMin":1,"quantityMax":1,"gender":3,"useDefaultOptions":true,"options":[],"expiry":[{"mode":"permanent","weight":1,"daysMin":0,"daysMax":0}]},{"itemId":2258,"weight":1,"quantityMin":1,"quantityMax":1,"gender":3,"useDefaultOptions":true,"options":[],"expiry":[{"mode":"permanent","weight":1,"daysMin":0,"daysMax":0}]},{"itemId":2259,"weight":1,"quantityMin":1,"quantityMax":1,"gender":3,"useDefaultOptions":true,"options":[],"expiry":[{"mode":"permanent","weight":1,"daysMin":0,"daysMax":0}]},{"itemId":2260,"weight":1,"quantityMin":1,"quantityMax":1,"gender":3,"useDefaultOptions":true,"options":[],"expiry":[{"mode":"permanent","weight":1,"daysMin":0,"daysMax":0}]},{"itemId":2261,"weight":1,"quantityMin":1,"quantityMax":1,"gender":3,"useDefaultOptions":true,"options":[],"expiry":[{"mode":"permanent","weight":1,"daysMin":0,"daysMax":0}]},{"itemId":2262,"weight":1,"quantityMin":1,"quantityMax":1,"gender":3,"useDefaultOptions":true,"options":[],"expiry":[{"mode":"permanent","weight":1,"daysMin":0,"daysMax":0}]},{"itemId":2263,"weight":1,"quantityMin":1,"quantityMax":1,"gender":3,"useDefaultOptions":true,"options":[],"expiry":[{"mode":"permanent","weight":1,"daysMin":0,"daysMax":0}]},{"itemId":2264,"weight":1,"quantityMin":1,"quantityMax":1,"gender":3,"useDefaultOptions":true,"options":[],"expiry":[{"mode":"permanent","weight":1,"daysMin":0,"daysMax":0}]},{"itemId":2265,"weight":1,"quantityMin":1,"quantityMax":1,"gender":3,"useDefaultOptions":true,"options":[],"expiry":[{"mode":"permanent","weight":1,"daysMin":0,"daysMax":0}]},{"itemId":2266,"weight":1,"quantityMin":1,"quantityMax":1,"gender":3,"useDefaultOptions":true,"options":[],"expiry":[{"mode":"permanent","weight":1,"daysMin":0,"daysMax":0}]},{"itemId":2267,"weight":1,"quantityMin":1,"quantityMax":1,"gender":3,"useDefaultOptions":true,"options":[],"expiry":[{"mode":"permanent","weight":1,"daysMin":0,"daysMax":0}]},{"itemId":2268,"weight":1,"quantityMin":1,"quantityMax":1,"gender":3,"useDefaultOptions":true,"options":[],"expiry":[{"mode":"permanent","weight":1,"daysMin":0,"daysMax":0}]},{"itemId":2269,"weight":1,"quantityMin":1,"quantityMax":1,"gender":3,"useDefaultOptions":true,"options":[],"expiry":[{"mode":"permanent","weight":1,"daysMin":0,"daysMax":0}]}]}'
WHERE box_template_id=2273;
