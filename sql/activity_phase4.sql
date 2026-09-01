-- Activity Points Phase 4: durable admin-operation audit.
-- Runtime configuration remains versioned in activity_config_version.
CREATE TABLE IF NOT EXISTS activity_admin_audit (
  id BIGINT NOT NULL AUTO_INCREMENT,
  action_name VARCHAR(64) NOT NULL,
  actor_note VARCHAR(500) NOT NULL,
  expected_revision BIGINT NULL,
  config_version_no BIGINT NULL,
  player_id BIGINT NULL,
  before_json LONGTEXT NULL,
  after_json LONGTEXT NULL,
  result_code VARCHAR(24) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY ix_activity_admin_audit_created (created_at),
  KEY ix_activity_admin_audit_player (player_id, created_at),
  KEY ix_activity_admin_audit_config (config_version_no, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
