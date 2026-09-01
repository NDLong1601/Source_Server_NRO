-- Activity Points Phase 2: aggregated source telemetry and shadow-mode rollout.
-- Apply while the server is stopped through tools/apply_activity_phase2.ps1.

CREATE TABLE IF NOT EXISTS activity_source_metric (
  metric_day VARCHAR(10) NOT NULL,
  mode VARCHAR(16) NOT NULL,
  config_revision BIGINT NOT NULL,
  source_key VARCHAR(64) NOT NULL,
  result_code VARCHAR(40) NOT NULL,
  event_count BIGINT NOT NULL DEFAULT 0,
  requested_points BIGINT NOT NULL DEFAULT 0,
  awarded_points BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (metric_day, mode, config_revision, source_key, result_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Version 2 enables state calculation and telemetry, but stays in shadow mode
-- and keeps rewards disabled. Base64 avoids PowerShell removing JSON quotes
-- when it passes this migration to mysql.exe.
INSERT IGNORE INTO activity_config_version
  (version_no, status, schema_version, config_json, note, published_at)
VALUES
  (2, 'PUBLISHED', 1,
   CONVERT(FROM_BASE64('eyJzY2hlbWFWZXJzaW9uIjoxLCJnbG9iYWwiOnsiZW5hYmxlZCI6dHJ1ZSwic2hhZG93TW9kZSI6dHJ1ZSwicmV3YXJkRW5hYmxlZCI6ZmFsc2UsInRpbWV6b25lIjoiQXNpYS9Ib19DaGlfTWluaCIsImRhaWx5UmVzZXRIb3VyIjowLCJ3ZWVrbHlSZXNldERheSI6Ik1PTkRBWSIsImRhaWx5TWF4IjoxMDAsInF1YWxpZmllZERhaWx5UG9pbnRzIjo4MCwiZGl2ZXJzaXR5Q2F0ZWdvcnlDb3VudCI6NCwiZGl2ZXJzaXR5Qm9udXMiOjEwfX0=') USING utf8mb4),
   'Phase 2: gameplay hooks enabled in shadow mode; rewards remain disabled.', NOW());

-- Promote only the original Phase 1 baseline. A later operator-published
-- revision is deliberately never replaced by re-running this script.
UPDATE activity_runtime_config runtime
JOIN activity_config_version current_version ON current_version.id = runtime.published_config_id
JOIN activity_config_version phase2_version ON phase2_version.version_no = 2
SET runtime.published_config_id = phase2_version.id,
    runtime.revision = runtime.revision + 1
WHERE runtime.id = 1
  AND runtime.revision = 1
  AND current_version.version_no = 1;
