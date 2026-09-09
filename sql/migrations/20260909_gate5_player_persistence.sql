-- Gate 5: additive player persistence boundary (MySQL 8+).
-- Existing positional JSON remains readable; online saves migrate event JSON
-- and populate the normalized wallet row at the same optimistic revision.

ALTER TABLE player
    ADD COLUMN IF NOT EXISTS save_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS player_wallet (
    player_id BIGINT NOT NULL PRIMARY KEY,
    gold BIGINT NOT NULL,
    gem INT NOT NULL,
    ruby INT NOT NULL,
    coupon INT NOT NULL,
    save_version BIGINT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS player_save_dead_letter (
    player_id BIGINT NOT NULL PRIMARY KEY,
    player_name VARCHAR(255) NULL,
    attempts INT NOT NULL,
    last_error VARCHAR(512) NOT NULL,
    failed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
