-- ============================================================================
-- SEC-07: VND audit ledger metadata and reconciliation findings
--
-- Additive and rerunnable. This file must be reviewed and run by the DB owner
-- in maintenance mode; it is intentionally NOT executed by the server.
-- It does not create a second VND ledger, invent historical baselines, repair
-- balances, or delete legacy rows.
--
-- Key contract:
--   operation_id/business_key are ASCII, case-sensitive identifiers.
--   VND ledger rows use one leg (leg_index=0); durable order is id/
--   durable_version. Legacy rows are retained and reported as incomplete when
--   they lack SEC-07 durable evidence.
-- ============================================================================

SET SESSION sql_mode = CONCAT_WS(',', @@sql_mode, 'STRICT_ALL_TABLES');

DROP PROCEDURE IF EXISTS sec07_assert_preflight;
DELIMITER //
CREATE PROCEDURE sec07_assert_preflight()
BEGIN
    DECLARE account_engine VARCHAR(32);
    DECLARE ledger_engine VARCHAR(32);
    DECLARE outbox_engine VARCHAR(32);
    DECLARE vnd_data_type VARCHAR(32);
    DECLARE vnd_column_type VARCHAR(128);
    DECLARE vnd_nullable VARCHAR(3);
    DECLARE invalid_balance_count BIGINT DEFAULT 0;
    SELECT UPPER(MAX(CASE WHEN TABLE_NAME='account' THEN ENGINE END)),
           UPPER(MAX(CASE WHEN TABLE_NAME='vnd_transaction_ledger' THEN ENGINE END)),
           UPPER(MAX(CASE WHEN TABLE_NAME='vnd_delivery_outbox' THEN ENGINE END))
      INTO account_engine, ledger_engine, outbox_engine
      FROM information_schema.TABLES
     WHERE TABLE_SCHEMA=DATABASE()
       AND TABLE_NAME IN ('account','vnd_transaction_ledger','vnd_delivery_outbox');
    IF account_engine IS NULL OR account_engine <> 'INNODB'
       OR ledger_engine IS NULL OR ledger_engine <> 'INNODB'
       OR outbox_engine IS NULL OR outbox_engine <> 'INNODB' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'SEC-07 requires account, vnd_transaction_ledger, and vnd_delivery_outbox to exist and use InnoDB';
    END IF;
    SELECT DATA_TYPE, COLUMN_TYPE, IS_NULLABLE
      INTO vnd_data_type, vnd_column_type, vnd_nullable
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='account' AND COLUMN_NAME='vnd';
    IF vnd_data_type IS NULL OR LOWER(vnd_data_type) <> 'int'
       OR LOWER(vnd_column_type) LIKE '%unsigned%'
       OR vnd_nullable <> 'NO' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'SEC-07 requires account.vnd to be a signed NOT NULL INT';
    END IF;
    SELECT COUNT(*) INTO invalid_balance_count FROM account
     WHERE vnd IS NULL OR vnd < 0;
    IF invalid_balance_count > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'SEC-07 refuses to proceed while account.vnd contains NULL or negative balances';
    END IF;
END//
DELIMITER ;
CALL sec07_assert_preflight();
DROP PROCEDURE IF EXISTS sec07_assert_preflight;

-- Add a column only when it is absent. Table/column names below are constants
-- from this migration, never client or operator input.
DROP PROCEDURE IF EXISTS sec07_add_column;
DELIMITER //
CREATE PROCEDURE sec07_add_column(IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_definition TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=p_table AND COLUMN_NAME=p_column
    ) THEN
        SET @sec07_ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition);
        PREPARE sec07_stmt FROM @sec07_ddl;
        EXECUTE sec07_stmt;
        DEALLOCATE PREPARE sec07_stmt;
    END IF;
END//
DELIMITER ;

CALL sec07_add_column('vnd_transaction_ledger','operation_id',
    'VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL');
CALL sec07_add_column('vnd_transaction_ledger','business_key',
    'VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL');
CALL sec07_add_column('vnd_transaction_ledger','owner_type',
    'VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL');
CALL sec07_add_column('vnd_transaction_ledger','currency',
    'VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL');
CALL sec07_add_column('vnd_transaction_ledger','requested_amount',
    'BIGINT NULL');
CALL sec07_add_column('vnd_transaction_ledger','applied_delta',
    'BIGINT NULL');
CALL sec07_add_column('vnd_transaction_ledger','leg_index',
    'SMALLINT NOT NULL DEFAULT 0');
CALL sec07_add_column('vnd_transaction_ledger','durable_version',
    'BIGINT NULL');
CALL sec07_add_column('vnd_transaction_ledger','source',
    'VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL');
CALL sec07_add_column('vnd_transaction_ledger','payload_fingerprint',
    'CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL');
CALL sec07_add_column('vnd_transaction_ledger','committed_at',
    'TIMESTAMP NULL DEFAULT NULL');
CALL sec07_add_column('vnd_transaction_ledger','schema_version',
    'VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL');

CALL sec07_add_column('vnd_delivery_outbox','operation_id',
    'VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL');
CALL sec07_add_column('vnd_delivery_outbox','business_key',
    'VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL');
CALL sec07_add_column('vnd_delivery_outbox','owner_type',
    'VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL');
CALL sec07_add_column('vnd_delivery_outbox','currency',
    'VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL');
CALL sec07_add_column('vnd_delivery_outbox','requested_amount',
    'BIGINT NULL');
CALL sec07_add_column('vnd_delivery_outbox','schema_version',
    'VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL');
DROP PROCEDURE IF EXISTS sec07_add_column;

-- Backfill only structural facts already present in the old rows. In
-- particular, no current account balance is copied into a newly fabricated
-- baseline here. Unknown legacy types keep applied_delta NULL and therefore
-- cannot silently become part of an OK result.
UPDATE vnd_transaction_ledger
   SET operation_id=COALESCE(operation_id,purchase_key),
       business_key=COALESCE(business_key,purchase_key),
       owner_type=COALESCE(owner_type,'ACCOUNT'),
       currency=COALESCE(currency,'VND'),
       requested_amount=COALESCE(requested_amount,amount),
       applied_delta=COALESCE(applied_delta,
           CASE
             WHEN transaction_type='BASELINE_OPENING' THEN amount
             WHEN transaction_type LIKE 'DEBIT%' THEN -amount
             WHEN transaction_type LIKE 'CREDIT%' THEN amount
             ELSE NULL
           END),
       durable_version=COALESCE(durable_version,id),
       `source`=COALESCE(`source`,transaction_type),
       committed_at=COALESCE(committed_at,created_at),
       schema_version=COALESCE(schema_version,
           CASE WHEN transaction_type='BASELINE_OPENING' THEN 'SEC-05-BASELINE'
                ELSE 'SEC-05-LEGACY' END)
 WHERE operation_id IS NULL
    OR business_key IS NULL
    OR owner_type IS NULL
    OR currency IS NULL
    OR requested_amount IS NULL
    OR applied_delta IS NULL
    OR durable_version IS NULL
    OR `source` IS NULL
    OR committed_at IS NULL
    OR schema_version IS NULL;

UPDATE vnd_delivery_outbox
   SET operation_id=COALESCE(operation_id,purchase_key),
       business_key=COALESCE(business_key,purchase_key),
       owner_type=COALESCE(owner_type,'ACCOUNT'),
       currency=COALESCE(currency,'VND'),
       requested_amount=COALESCE(requested_amount,amount),
       schema_version=COALESCE(schema_version,'SEC-05-LEGACY')
 WHERE operation_id IS NULL
    OR business_key IS NULL
    OR owner_type IS NULL
    OR currency IS NULL
    OR requested_amount IS NULL
    OR schema_version IS NULL;

-- Fail closed if an old row cannot be assigned the required identity. The
-- existing purchase_key unique constraint prevents duplicate backfilled keys.
ALTER TABLE vnd_transaction_ledger
    MODIFY operation_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    MODIFY business_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    MODIFY owner_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    MODIFY currency VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    MODIFY requested_amount BIGINT NOT NULL,
    MODIFY `source` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    MODIFY committed_at TIMESTAMP NULL DEFAULT NULL,
    MODIFY schema_version VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL;

ALTER TABLE vnd_delivery_outbox
    MODIFY operation_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    MODIFY business_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    MODIFY owner_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    MODIFY currency VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    MODIFY requested_amount BIGINT NOT NULL,
    MODIFY schema_version VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL;

-- A legacy baseline is outside SEC-07 coverage and may coexist with the one
-- cutover baseline. Reject only duplicate SEC-07 baselines for the same owner
-- and currency; reconciliation reports a missing baseline after cutover.
DROP PROCEDURE IF EXISTS sec07_assert_baseline_preflight;
DELIMITER //
CREATE PROCEDURE sec07_assert_baseline_preflight()
BEGIN
    DECLARE duplicate_baseline_count BIGINT DEFAULT 0;
    SELECT COUNT(*) INTO duplicate_baseline_count
      FROM (
          SELECT owner_type, account_id, currency
            FROM vnd_transaction_ledger
           WHERE transaction_type='BASELINE_OPENING'
             AND schema_version='SEC-07-BASELINE'
           GROUP BY owner_type, account_id, currency
          HAVING COUNT(*) > 1
      ) duplicate_baselines;
    IF duplicate_baseline_count > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'SEC-07 found duplicate cutover baselines for an owner/currency';
    END IF;
END//
DELIMITER ;
CALL sec07_assert_baseline_preflight();
DROP PROCEDURE IF EXISTS sec07_assert_baseline_preflight;

DROP PROCEDURE IF EXISTS sec07_add_index;
DELIMITER //
CREATE PROCEDURE sec07_add_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_definition TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=p_table AND INDEX_NAME=p_index
    ) THEN
        SET @sec07_index_ddl = CONCAT('ALTER TABLE `', p_table, '` ADD ', p_definition);
        PREPARE sec07_index_stmt FROM @sec07_index_ddl;
        EXECUTE sec07_index_stmt;
        DEALLOCATE PREPARE sec07_index_stmt;
    END IF;
END//
DELIMITER ;
CALL sec07_add_index('vnd_transaction_ledger','uk_vnd_ledger_operation_leg',
    'UNIQUE KEY `uk_vnd_ledger_operation_leg` (`operation_id`,`leg_index`)');
CALL sec07_add_index('vnd_delivery_outbox','uk_vnd_outbox_operation',
    'UNIQUE KEY `uk_vnd_outbox_operation` (`operation_id`)');
DROP PROCEDURE IF EXISTS sec07_add_index;

-- Findings and run metadata are separate from the committed movement ledger.
-- The current CLI is read-only and does not populate these tables.
CREATE TABLE IF NOT EXISTS sec07_reconciliation_run (
    run_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    schema_version VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP NULL,
    cutoff_ledger_id BIGINT NOT NULL DEFAULT 0,
    cutoff_at TIMESTAMP NULL,
    consistency_model VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_filter BIGINT NULL,
    currency_filter VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    after_owner_id BIGINT NOT NULL DEFAULT 0,
    next_cursor BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    coverage_complete TINYINT(1) NOT NULL DEFAULT 0,
    owners_audited INT NOT NULL DEFAULT 0,
    findings_count INT NOT NULL DEFAULT 0,
    PRIMARY KEY (run_id),
    KEY ix_sec07_run_started (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS sec07_reconciliation_finding (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id BIGINT NOT NULL,
    currency VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    detail_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    operation_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '',
    persisted_balance BIGINT NULL,
    expected_balance BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sec07_finding_once (run_id,owner_type,owner_id,currency,code,detail_code,operation_id),
    KEY ix_sec07_finding_owner (owner_type,owner_id,currency,created_at),
    CONSTRAINT fk_sec07_finding_run FOREIGN KEY (run_id) REFERENCES sec07_reconciliation_run(run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- No baseline is inserted by this migration. Capture baselines at a verified
-- cutover with sql/operations/sec07_capture_vnd_baseline.sql only after all
-- writers are stopped and the operator has checked its preflight output.
