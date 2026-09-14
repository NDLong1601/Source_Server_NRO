-- Social V2 phase 2: additive, normalized friendship and pending-request persistence.
-- Do not apply to production until a database backup and a successful LegacyFriendBackfill --dry-run
-- report have been recorded. This migration intentionally does not modify player.friends JSON.

-- Preconditions: every table participating in the service transaction must be InnoDB.
SELECT IF(COUNT(*) = 1 AND SUM(UPPER(ENGINE) = 'INNODB') = 1,
          'OK', 'ABORT: player must exist and use InnoDB') AS social_v2_engine_preflight
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'player';

CREATE TABLE IF NOT EXISTS player_friendship (
    player_low_id BIGINT NOT NULL,
    player_high_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (player_low_id, player_high_id),
    KEY idx_player_friendship_high (player_high_id, player_low_id),
    CONSTRAINT chk_player_friendship_pair CHECK (player_low_id > 0 AND player_low_id < player_high_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- A row exists only while the invitation remains pending. The unique canonical pair
-- blocks duplicate and cross requests; accept/reject/expiry delete the row atomically.
CREATE TABLE IF NOT EXISTS friend_request (
    id BIGINT NOT NULL AUTO_INCREMENT,
    pair_low_id BIGINT NOT NULL,
    pair_high_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- DATETIME avoids the legacy single-automatic-TIMESTAMP rule on MariaDB 10.4
    -- while retaining the same UTC instant semantics through JDBC Timestamp.
    expires_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_friend_request_pair (pair_low_id, pair_high_id),
    KEY idx_friend_request_receiver_created (receiver_id, created_at, id),
    KEY idx_friend_request_sender_created (sender_id, created_at),
    KEY idx_friend_request_expiry (expires_at, id),
    CONSTRAINT chk_friend_request_pair CHECK (pair_low_id > 0 AND pair_low_id < pair_high_id),
    CONSTRAINT chk_friend_request_actor CHECK (
        sender_id > 0 AND receiver_id > 0 AND sender_id <> receiver_id
        AND sender_id IN (pair_low_id, pair_high_id)
        AND receiver_id IN (pair_low_id, pair_high_id)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Verification only; no player identity or friend name is returned.
SELECT table_name, engine
  FROM information_schema.TABLES
 WHERE table_schema = DATABASE() AND table_name IN ('player_friendship', 'friend_request');

SELECT table_name, index_name, column_name, seq_in_index
  FROM information_schema.STATISTICS
 WHERE table_schema = DATABASE() AND table_name IN ('player_friendship', 'friend_request')
 ORDER BY table_name, index_name, seq_in_index;

-- Rollback guidance: do not drop either table once social-v2 writes have been enabled.
-- Restore the pre-migration database backup instead; legacy player.friends remains untouched.
