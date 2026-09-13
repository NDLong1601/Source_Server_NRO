-- Install Cải trang Tengen Uzui; IDs were allocated contiguously from the live team2026 database.
START TRANSACTION;

INSERT INTO part (id, `TYPE`, `DATA`) VALUES
(2150, 0, '[[25146,0,0],[25146,0,0],[25146,0,0]]'),
(2151, 1, '[[25129,-17,-28],[25219,-4,-38],[25220,-18,-34],[25132,-13,-22],[25133,-15,-28],[25134,-18,-30],[25135,-13,-26],[26009,-18,-34],[25137,-16,-40],[25138,-11,-33],[25139,-14,-30],[25140,-6,-34],[25141,-16,-40],[25221,-13,-40],[25143,-17,-34],[25144,-14,-31],[25145,-15,-28]]'),
(2152, 2, '[[25146,0,0],[25146,0,0],[25146,0,0],[25146,0,0],[25146,0,0],[25146,0,0],[25146,0,0],[25146,0,0],[25146,0,0],[25146,0,0],[25146,0,0],[25146,0,0],[25146,0,0],[25146,0,0]]')
ON DUPLICATE KEY UPDATE `TYPE`=VALUES(`TYPE`), `DATA`=VALUES(`DATA`);

INSERT INTO head_avatar (head_id, avatar_id) VALUES (2150, 26015)
ON DUPLICATE KEY UPDATE avatar_id=VALUES(avatar_id);

INSERT INTO item_template
(id, `TYPE`, gender, `NAME`, description, level, icon_id, part, is_up_to_up, power_require, gold, gem, head, body, leg)
VALUES
(2067, 5, 3, 'Cải trang Tengen Uzui', 'Cải trang thành Tengen Uzui', 1, 25147, -1, 0, 0, 0, 0, 2150, 2151, 2152)
ON DUPLICATE KEY UPDATE
`TYPE`=VALUES(`TYPE`), gender=VALUES(gender), `NAME`=VALUES(`NAME`),
description=VALUES(description), level=VALUES(level), icon_id=VALUES(icon_id),
part=VALUES(part), is_up_to_up=VALUES(is_up_to_up), power_require=VALUES(power_require),
gold=VALUES(gold), gem=VALUES(gem), head=VALUES(head), body=VALUES(body), leg=VALUES(leg);

DELETE FROM item_default_option WHERE item_template_id = 2067;

INSERT INTO item_shop
(tab_id, temp_id, is_new, is_sell, type_sell, cost, icon_spec, option_mode, create_time)
SELECT 1120, 2067, 1, 1, 1, 2000, 0, 0, NOW()
WHERE EXISTS (SELECT 1 FROM tab_shop WHERE id = 1120 AND shop_id = 112)
  AND NOT EXISTS (SELECT 1 FROM item_shop WHERE tab_id = 1120 AND temp_id = 2067);

COMMIT;
