-- Explicit per-account repair for the old cutover script's NULL sequence.
-- Installing this procedure does not execute a repair. Stop writers and verify
-- backup before CALL on a dedicated maintenance connection. Metadata only:
-- this neither rebaselines nor certifies historical balances.
DROP PROCEDURE IF EXISTS sec07_repair_baseline_sequence;
DELIMITER //
CREATE PROCEDURE sec07_repair_baseline_sequence(IN p_account_id INT, IN p_ack VARCHAR(64))
BEGIN
    DECLARE baseline_id BIGINT;
    DECLARE owner_id INT DEFAULT NULL;
    DECLARE baseline_count INT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    IF p_account_id IS NULL OR p_account_id<=0
       OR COALESCE(p_ack,'') <> 'STOPPED_WRITERS_AND_VERIFIED_BACKUP' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'SEC-07 repair acknowledgement/account required';
    END IF;
    START TRANSACTION;
    SELECT id INTO owner_id FROM account WHERE id=p_account_id FOR UPDATE;
    IF owner_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'SEC-07 repair owner missing';
    END IF;
    SELECT COUNT(*), MAX(id) INTO baseline_count, baseline_id
      FROM vnd_transaction_ledger WHERE account_id=p_account_id
       AND transaction_type='BASELINE_OPENING' AND schema_version='SEC-07-BASELINE';
    IF baseline_count<>1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'SEC-07 repair requires exactly one baseline';
    END IF;
    -- Never read today's account.vnd to reconstruct an opening balance.
    IF NOT EXISTS (SELECT 1 FROM vnd_transaction_ledger WHERE id=baseline_id
        AND owner_type='ACCOUNT' AND currency='VND' AND player_id=0 AND leg_index=0
        AND purchase_key=CONCAT('sec07_baseline_account_',p_account_id)
        AND operation_id=CONCAT('sec07_baseline_account_',p_account_id)
        AND business_key=CONCAT('sec07_baseline_account_',p_account_id)
        AND policy_version='SEC-07-BASELINE' AND `source`='BASELINE_OPENING'
        AND committed_at IS NOT NULL AND amount>=0 AND balance_before=0
        AND balance_after=amount AND requested_amount=amount AND applied_delta=amount
        AND (durable_version IS NULL OR durable_version=id)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'SEC-07 repair refuses conflicting baseline evidence';
    END IF;
    UPDATE vnd_transaction_ledger SET durable_version=id WHERE id=baseline_id AND durable_version IS NULL;
    COMMIT;
END//
DELIMITER ;
-- CALL sec07_repair_baseline_sequence(<account_id>, 'STOPPED_WRITERS_AND_VERIFIED_BACKUP');
