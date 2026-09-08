-- SEC-07 cutover: stopped writers and verified backup required.
-- Only newly captured baselines receive sequence metadata in this transaction.
-- Existing baselines are never reset; use the separate per-account metadata repair.
-- The acknowledgement guards DML itself even if a SQL client continues after errors.
SET @sec07_cutover_ack = NULL;
DROP PROCEDURE IF EXISTS sec07_capture_vnd_baseline;
DELIMITER //
CREATE PROCEDURE sec07_capture_vnd_baseline(IN p_ack VARCHAR(64))
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        DROP TEMPORARY TABLE IF EXISTS sec07_new_baseline_accounts;
        RESIGNAL;
    END;
    IF COALESCE(p_ack,'') <> 'STOPPED_WRITERS_AND_VERIFIED_BACKUP' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'SEC-07 cutover acknowledgement required';
    END IF;
START TRANSACTION;
    IF EXISTS (SELECT 1 FROM account WHERE vnd IS NULL OR vnd < 0) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'SEC-07 invalid opening balance';
    END IF;
    CREATE TEMPORARY TABLE sec07_new_baseline_accounts (account_id INT PRIMARY KEY);
    INSERT INTO sec07_new_baseline_accounts
    SELECT a.id FROM account a WHERE NOT EXISTS (
        SELECT 1 FROM vnd_transaction_ledger l WHERE l.account_id=a.id
          AND l.transaction_type='BASELINE_OPENING' AND l.schema_version='SEC-07-BASELINE');
INSERT INTO vnd_transaction_ledger (
    purchase_key, account_id, player_id, transaction_type, amount,
    balance_before, balance_after, policy_version, operation_id, business_key,
    owner_type, currency, requested_amount, applied_delta, leg_index,
    durable_version, `source`, payload_fingerprint, committed_at, schema_version
)
SELECT CONCAT('sec07_baseline_account_', a.id), a.id, 0, 'BASELINE_OPENING', a.vnd,
       0, a.vnd, 'SEC-07-BASELINE', CONCAT('sec07_baseline_account_', a.id),
       CONCAT('sec07_baseline_account_', a.id), 'ACCOUNT', 'VND', a.vnd, a.vnd, 0,
       NULL, 'BASELINE_OPENING', NULL, CURRENT_TIMESTAMP, 'SEC-07-BASELINE'
  FROM account a JOIN sec07_new_baseline_accounts n ON n.account_id=a.id;

    UPDATE vnd_transaction_ledger l JOIN sec07_new_baseline_accounts n ON n.account_id=l.account_id
       SET l.durable_version=l.id
     WHERE l.operation_id=CONCAT('sec07_baseline_account_',l.account_id)
       AND l.schema_version='SEC-07-BASELINE' AND l.transaction_type='BASELINE_OPENING'
       AND l.durable_version IS NULL;
    IF EXISTS (
        SELECT 1 FROM vnd_transaction_ledger l JOIN sec07_new_baseline_accounts n ON n.account_id=l.account_id
         WHERE l.schema_version='SEC-07-BASELINE' AND l.transaction_type='BASELINE_OPENING'
           AND (l.durable_version IS NULL OR l.durable_version<>l.id)
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'SEC-07 baseline sequence validation failed';
    END IF;
    COMMIT;
    DROP TEMPORARY TABLE sec07_new_baseline_accounts;
END//
DELIMITER ;
CALL sec07_capture_vnd_baseline(@sec07_cutover_ack);
DROP PROCEDURE IF EXISTS sec07_capture_vnd_baseline;
