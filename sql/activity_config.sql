-- Activity Points Phase 1 schema. Apply while the server is stopped.
ALTER TABLE player ADD COLUMN IF NOT EXISTS data_activity LONGTEXT NULL AFTER data_point;

CREATE TABLE IF NOT EXISTS activity_config_version (
  id BIGINT NOT NULL AUTO_INCREMENT,
  version_no BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL,
  schema_version INT NOT NULL,
  config_json LONGTEXT NOT NULL,
  note VARCHAR(500) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  published_at TIMESTAMP NULL DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_activity_config_version (version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS activity_runtime_config (
  id TINYINT NOT NULL,
  published_config_id BIGINT NULL,
  revision BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO activity_config_version
  (version_no, status, schema_version, config_json, note, published_at)
VALUES
  (1, 'PUBLISHED', 1,
   CONVERT(FROM_BASE64('eyJzY2hlbWFWZXJzaW9uIjoxLCJnbG9iYWwiOnsiZW5hYmxlZCI6ZmFsc2UsInNoYWRvd01vZGUiOnRydWUsInJld2FyZEVuYWJsZWQiOmZhbHNlLCJ0aW1lem9uZSI6IkFzaWEvSG9fQ2hpX01pbmgiLCJkYWlseVJlc2V0SG91ciI6MCwid2Vla2x5UmVzZXREYXkiOiJNT05EQVkiLCJkYWlseU1heCI6MTAwLCJxdWFsaWZpZWREYWlseVBvaW50cyI6ODAsImRpdmVyc2l0eUNhdGVnb3J5Q291bnQiOjQsImRpdmVyc2l0eUJvbnVzIjoxMH19') USING utf8mb4),
   'Phase 1 baseline: feature disabled, shadow mode enabled, rewards disabled.', NOW());

-- Windows PowerShell can remove double quotes embedded in a native
-- --execute argument. Repair only an invalid initial baseline, never a valid
-- administrator-published configuration.
UPDATE activity_config_version
SET config_json = CONVERT(FROM_BASE64('eyJzY2hlbWFWZXJzaW9uIjoxLCJnbG9iYWwiOnsiZW5hYmxlZCI6ZmFsc2UsInNoYWRvd01vZGUiOnRydWUsInJld2FyZEVuYWJsZWQiOmZhbHNlLCJ0aW1lem9uZSI6IkFzaWEvSG9fQ2hpX01pbmgiLCJkYWlseVJlc2V0SG91ciI6MCwid2Vla2x5UmVzZXREYXkiOiJNT05EQVkiLCJkYWlseU1heCI6MTAwLCJxdWFsaWZpZWREYWlseVBvaW50cyI6ODAsImRpdmVyc2l0eUNhdGVnb3J5Q291bnQiOjQsImRpdmVyc2l0eUJvbnVzIjoxMH19') USING utf8mb4)
WHERE version_no = 1 AND JSON_VALID(config_json) = 0;

INSERT IGNORE INTO activity_runtime_config (id, published_config_id, revision)
SELECT 1, id, 1 FROM activity_config_version WHERE version_no = 1;
