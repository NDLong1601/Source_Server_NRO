-- Restore only the Tokitou BODY DATA used before the direction correction.

START TRANSACTION;

UPDATE part
SET `TYPE` = 1,
    `DATA` = '[[25043,-9,-36],[25044,-20,-33],[25045,-9,-24],[25046,-14,-17],[25047,-15,-24],[25048,-8,-27],[25049,-14,-36],[25050,-11,-18],[25051,-11,-33],[25052,-11,-33],[25053,-11,-26],[25054,-10,-34],[25055,-11,-36],[25056,-12,-25],[25057,-11,-31],[25058,-19,-36],[25059,-15,-22]]'
WHERE id = 2139
  AND `TYPE` = 1
  AND EXISTS (
      SELECT 1 FROM item_template
      WHERE id = 2063 AND body = 2139
  );

COMMIT;
