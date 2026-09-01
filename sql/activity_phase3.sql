-- Activity Points Phase 3: published tiers, claim audit, and still-safe shadow rollout.
-- Apply while the server is stopped through tools/apply_activity_phase3.ps1.

CREATE TABLE IF NOT EXISTS activity_claim_audit (
  claim_id BIGINT NOT NULL AUTO_INCREMENT,
  player_id BIGINT NOT NULL,
  period_type VARCHAR(12) NOT NULL,
  period_key VARCHAR(10) NOT NULL,
  tier_id VARCHAR(64) NOT NULL,
  claim_bit INT NOT NULL,
  config_revision BIGINT NOT NULL,
  reward_json LONGTEXT NOT NULL,
  result_code VARCHAR(24) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (claim_id),
  UNIQUE KEY uk_activity_claim_once (player_id, period_type, period_key, tier_id),
  KEY ix_activity_claim_tier_time (tier_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Starter tiers are deliberately currency-only and rewardEnabled remains false.
-- Admin configuration in Phase 4 will provide the reviewed item bundles.
INSERT IGNORE INTO activity_config_version
  (version_no, status, schema_version, config_json, note, published_at)
VALUES
  (3, 'PUBLISHED', 1,
   CONVERT(FROM_BASE64('eyJzY2hlbWFWZXJzaW9uIjoxLCJnbG9iYWwiOnsiZW5hYmxlZCI6dHJ1ZSwic2hhZG93TW9kZSI6dHJ1ZSwicmV3YXJkRW5hYmxlZCI6ZmFsc2UsInRpbWV6b25lIjoiQXNpYS9Ib19DaGlfTWluaCIsImRhaWx5UmVzZXRIb3VyIjowLCJ3ZWVrbHlSZXNldERheSI6Ik1PTkRBWSIsImRhaWx5TWF4IjoxMDAsInF1YWxpZmllZERhaWx5UG9pbnRzIjo4MCwiZGl2ZXJzaXR5Q2F0ZWdvcnlDb3VudCI6NCwiZGl2ZXJzaXR5Qm9udXMiOjEwfSwiZGFpbHlUaWVycyI6W3siaWQiOiJEQUlMWV8yMCIsImNsYWltQml0IjowLCJlbmFibGVkIjp0cnVlLCJ0aHJlc2hvbGQiOjIwLCJtaW5RdWFsaWZpZWREYXlzIjowLCJyZXdhcmRzIjpbeyJraW5kIjoiR09MRCIsInF1YW50aXR5TWluIjoxMDAwMCwicXVhbnRpdHlNYXgiOjEwMDAwfV19LHsiaWQiOiJEQUlMWV80MCIsImNsYWltQml0IjoxLCJlbmFibGVkIjp0cnVlLCJ0aHJlc2hvbGQiOjQwLCJtaW5RdWFsaWZpZWREYXlzIjowLCJyZXdhcmRzIjpbeyJraW5kIjoiR09MRCIsInF1YW50aXR5TWluIjoyNTAwMCwicXVhbnRpdHlNYXgiOjI1MDAwfV19LHsiaWQiOiJEQUlMWV82MCIsImNsYWltQml0IjoyLCJlbmFibGVkIjp0cnVlLCJ0aHJlc2hvbGQiOjYwLCJtaW5RdWFsaWZpZWREYXlzIjowLCJyZXdhcmRzIjpbeyJraW5kIjoiR09MRCIsInF1YW50aXR5TWluIjo1MDAwMCwicXVhbnRpdHlNYXgiOjUwMDAwfV19LHsiaWQiOiJEQUlMWV84MCIsImNsYWltQml0IjozLCJlbmFibGVkIjp0cnVlLCJ0aHJlc2hvbGQiOjgwLCJtaW5RdWFsaWZpZWREYXlzIjowLCJyZXdhcmRzIjpbeyJraW5kIjoiR09MRCIsInF1YW50aXR5TWluIjoxMDAwMDAsInF1YW50aXR5TWF4IjoxMDAwMDB9XX0seyJpZCI6IkRBSUxZXzEwMCIsImNsYWltQml0Ijo0LCJlbmFibGVkIjp0cnVlLCJ0aHJlc2hvbGQiOjEwMCwibWluUXVhbGlmaWVkRGF5cyI6MCwicmV3YXJkcyI6W3sia2luZCI6IkdPTEQiLCJxdWFudGl0eU1pbiI6MjAwMDAwLCJxdWFudGl0eU1heCI6MjAwMDAwfV19XSwid2Vla2x5VGllcnMiOlt7ImlkIjoiV0VFS0xZXzMwMCIsImNsYWltQml0IjowLCJlbmFibGVkIjp0cnVlLCJ0aHJlc2hvbGQiOjMwMCwibWluUXVhbGlmaWVkRGF5cyI6MCwicmV3YXJkcyI6W3sia2luZCI6IkdPTEQiLCJxdWFudGl0eU1pbiI6MzAwMDAwLCJxdWFudGl0eU1heCI6MzAwMDAwfV19LHsiaWQiOiJXRUVLTFlfNTAwIiwiY2xhaW1CaXQiOjEsImVuYWJsZWQiOnRydWUsInRocmVzaG9sZCI6NTAwLCJtaW5RdWFsaWZpZWREYXlzIjo1LCJyZXdhcmRzIjpbeyJraW5kIjoiR09MRCIsInF1YW50aXR5TWluIjo3NTAwMDAsInF1YW50aXR5TWF4Ijo3NTAwMDB9XX1dfQ==') USING utf8mb4),
   'Phase 3: daily/weekly reward tiers, claim audit, and NPC menu; rewards remain disabled.', NOW());

-- Promote only the exact Phase 2 shadow revision. Re-running this migration
-- never overwrites an operator-published config.
UPDATE activity_runtime_config runtime
JOIN activity_config_version current_version ON current_version.id = runtime.published_config_id
JOIN activity_config_version phase3_version ON phase3_version.version_no = 3
SET runtime.published_config_id = phase3_version.id,
    runtime.revision = runtime.revision + 1
WHERE runtime.id = 1
  AND runtime.revision = 2
  AND current_version.version_no = 2;
