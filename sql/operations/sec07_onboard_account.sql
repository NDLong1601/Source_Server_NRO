-- SEC-07 controlled onboarding entry point for an account created by an
-- external account system.
--
-- The external creator MUST execute INSERT account (...) and CALL
-- sec07_onboard_account(account_id, initial_vnd) on the SAME connection and
-- inside the SAME transaction, then commit both together. This procedure does
-- not START/COMMIT a transaction because doing so would break that contract.
-- If the external creator cannot provide that boundary, the account remains
-- fail-closed for VND purchases until onboarding succeeds.
--
-- Do not call this for a legacy account merely because its baseline is absent.
-- The expected initial_vnd must be the value selected by the account-creation
-- workflow, not a value read from account.vnd after the fact.

DROP PROCEDURE IF EXISTS sec07_onboard_account;
DELIMITER //
CREATE PROCEDURE sec07_onboard_account(IN p_account_id INT, IN p_initial_vnd INT)
BEGIN
    DECLARE current_vnd INT DEFAULT NULL;
    DECLARE baseline_count INT DEFAULT 0;
    DECLARE existing_amount INT DEFAULT NULL;
    DECLARE existing_before INT DEFAULT NULL;
    DECLARE existing_after INT DEFAULT NULL;
    DECLARE existing_requested BIGINT DEFAULT NULL;
    DECLARE existing_delta BIGINT DEFAULT NULL;
    DECLARE existing_operation_id VARCHAR(128) DEFAULT NULL;
    DECLARE existing_business_key VARCHAR(128) DEFAULT NULL;
    DECLARE baseline_id BIGINT DEFAULT NULL;

    IF p_account_id IS NULL OR p_initial_vnd IS NULL
       OR p_account_id <= 0 OR p_initial_vnd < 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'SEC-07 account onboarding input is invalid';
    END IF;

    SELECT vnd INTO current_vnd
      FROM account
     WHERE id=p_account_id
     FOR UPDATE;
    IF current_vnd IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'SEC-07 account is missing or account.vnd is NULL';
    END IF;
    IF current_vnd <> p_initial_vnd THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'SEC-07 initial VND does not match the account creation value';
    END IF;

    SELECT COUNT(*) INTO baseline_count
      FROM vnd_transaction_ledger
     WHERE account_id=p_account_id
       AND owner_type='ACCOUNT'
       AND currency='VND'
       AND transaction_type='BASELINE_OPENING'
       AND schema_version='SEC-07-BASELINE';

    IF baseline_count > 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'SEC-07 account has duplicate cutover/onboarding baselines';
    ELSEIF baseline_count = 1 THEN
        SELECT operation_id, business_key, amount, balance_before, balance_after,
               requested_amount, applied_delta
          INTO existing_operation_id, existing_business_key, existing_amount,
               existing_before, existing_after, existing_requested, existing_delta
          FROM vnd_transaction_ledger
         WHERE account_id=p_account_id
           AND owner_type='ACCOUNT'
           AND currency='VND'
           AND transaction_type='BASELINE_OPENING'
           AND schema_version='SEC-07-BASELINE'
         LIMIT 1;
        IF existing_operation_id IS NULL OR existing_business_key IS NULL
           OR existing_operation_id <> CONCAT('sec07_baseline_account_', p_account_id)
           OR existing_business_key <> CONCAT('sec07_baseline_account_', p_account_id)
           OR existing_amount IS NULL OR existing_before IS NULL OR existing_after IS NULL
           OR existing_requested IS NULL OR existing_delta IS NULL
           OR existing_amount <> p_initial_vnd
           OR existing_before <> 0
           OR existing_after <> p_initial_vnd
           OR existing_requested <> p_initial_vnd
           OR existing_delta <> p_initial_vnd THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
                'SEC-07 existing onboarding baseline conflicts; no reset is allowed';
        END IF;
    ELSE
        INSERT INTO vnd_transaction_ledger (
            purchase_key, account_id, player_id, transaction_type, amount,
            balance_before, balance_after, policy_version, operation_id, business_key,
            owner_type, currency, requested_amount, applied_delta, leg_index,
            durable_version, `source`, payload_fingerprint, committed_at, schema_version
        ) VALUES (
            CONCAT('sec07_baseline_account_', p_account_id), p_account_id, 0,
            'BASELINE_OPENING', p_initial_vnd, 0, p_initial_vnd, 'SEC-07-BASELINE',
            CONCAT('sec07_baseline_account_', p_account_id),
            CONCAT('sec07_baseline_account_', p_account_id), 'ACCOUNT', 'VND',
            p_initial_vnd, p_initial_vnd, 0, NULL, 'BASELINE_OPENING', NULL,
            CURRENT_TIMESTAMP, 'SEC-07-BASELINE'
        );
        SET baseline_id = LAST_INSERT_ID();
        UPDATE vnd_transaction_ledger
           SET durable_version=baseline_id
         WHERE id=baseline_id;
        IF ROW_COUNT() <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
                'SEC-07 onboarding baseline sequence was not made durable';
        END IF;
    END IF;
END//
DELIMITER ;

-- Example only; the account system supplies the values in its own transaction:
-- START TRANSACTION;
-- INSERT INTO account (...) VALUES (...);
-- CALL sec07_onboard_account(<new_account_id>, <declared_initial_vnd>);
-- COMMIT;
