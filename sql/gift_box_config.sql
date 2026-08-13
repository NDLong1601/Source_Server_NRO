-- Cấu hình hộp quà dùng bởi nro.models.admin.GiftBoxConfigService.
-- Service cũng tự tạo bảng và nạp profile mặc định khi server khởi động.
CREATE TABLE IF NOT EXISTS gift_box_config (
  box_template_id INT NOT NULL PRIMARY KEY,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  min_empty_slots INT NOT NULL DEFAULT 1,
  consume_quantity INT NOT NULL DEFAULT 1,
  draw_count INT NOT NULL DEFAULT 1,
  config_json TEXT NOT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- config_json mẫu:
-- {
--   "boxTemplateId":1757,"enabled":true,"minEmptySlots":2,
--   "consumeQuantity":1,"drawCount":1,
--   "rewards":[{
--     "itemId":1741,"weight":10,"quantityMin":1,"quantityMax":1,
--     "gender":3,"initBaseOptions":true,"useDefaultOptions":false,
--     "options":[{"id":50,"paramMin":20,"paramMax":27}],
--     "expiry":[
--       {"mode":"permanent","weight":1,"daysMin":0,"daysMax":0},
--       {"mode":"days","weight":99,"daysMin":5,"daysMax":10}
--     ]
--   }]
-- }
