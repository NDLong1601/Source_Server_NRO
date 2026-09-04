-- Giai đoạn 1: Kho bang, quỹ tiền tệ và sổ cái giao dịch.
-- Server cũng tự kiểm tra/tạo schema này khi khởi động.

ALTER TABLE clan ADD COLUMN clan_gold BIGINT NOT NULL DEFAULT 0;
ALTER TABLE clan ADD COLUMN clan_gem BIGINT NOT NULL DEFAULT 0;
ALTER TABLE clan ADD COLUMN treasury_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE clan_member_contribution (
    clan_id INT NOT NULL,
    player_id BIGINT NOT NULL,
    lifetime_score BIGINT NOT NULL DEFAULT 0,
    donated_gold BIGINT NOT NULL DEFAULT 0,
    donated_gem BIGINT NOT NULL DEFAULT 0,
    last_contribution_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (clan_id, player_id),
    KEY idx_contribution_player (player_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE clan_ledger (
    id BIGINT NOT NULL AUTO_INCREMENT,
    clan_id INT NOT NULL,
    actor_id BIGINT NOT NULL,
    actor_name VARCHAR(64) NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    currency_type TINYINT NOT NULL,
    amount BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    target_id BIGINT NULL,
    metadata_json TEXT NULL,
    request_id VARCHAR(80) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_clan_ledger_request (clan_id, request_id),
    KEY idx_clan_ledger_page (clan_id, id),
    KEY idx_clan_ledger_actor (actor_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
