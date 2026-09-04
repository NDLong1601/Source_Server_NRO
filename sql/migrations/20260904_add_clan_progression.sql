-- Giai đoạn 3: tiến trình EXP và tiềm năng bang.
ALTER TABLE clan ADD COLUMN clan_exp BIGINT NOT NULL DEFAULT 0;
ALTER TABLE clan ADD COLUMN potential_total INT NOT NULL DEFAULT 0;
ALTER TABLE clan ADD COLUMN potential_unspent INT NOT NULL DEFAULT 0;
ALTER TABLE clan ADD COLUMN progression_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE clan_potential (
    clan_id INT NOT NULL,
    branch_key VARCHAR(32) NOT NULL,
    rank_value INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (clan_id, branch_key),
    KEY idx_clan_potential_clan (clan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE clan_potential_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    clan_id INT NOT NULL,
    actor_id BIGINT NOT NULL,
    branch_key VARCHAR(32) NOT NULL,
    rank_before INT NOT NULL,
    rank_after INT NOT NULL,
    unspent_before INT NOT NULL,
    unspent_after INT NOT NULL,
    point_cost INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_clan_potential_audit_clan (clan_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
