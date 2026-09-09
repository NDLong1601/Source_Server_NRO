-- Phase 5E: privacy-minimized daily clan economy and operational metrics.
CREATE TABLE IF NOT EXISTS clan_economy_metric (
  metric_day DATE NOT NULL,
  signal_key VARCHAR(48) NOT NULL,
  event_count BIGINT NOT NULL DEFAULT 0,
  amount_total BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (metric_day, signal_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS clan_economy_active_clan (
  metric_day DATE NOT NULL,
  clan_id INT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (metric_day, clan_id),
  KEY idx_clan_economy_active_clan (clan_id, metric_day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- The admin report filters by date before grouping currency/action. Keep this
-- migration idempotent for existing databases where clan_ledger predates 5E.
SET @clan_economy_index_exists := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'clan_ledger'
    AND index_name = 'idx_clan_ledger_economy'
);
SET @clan_economy_index_sql := IF(
  @clan_economy_index_exists = 0,
  'ALTER TABLE clan_ledger ADD KEY idx_clan_ledger_economy (created_at,currency_type,action_type)',
  'SELECT 1'
);
PREPARE clan_economy_index_statement FROM @clan_economy_index_sql;
EXECUTE clan_economy_index_statement;
DEALLOCATE PREPARE clan_economy_index_statement;
