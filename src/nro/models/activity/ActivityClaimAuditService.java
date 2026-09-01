package nro.models.activity;

import com.google.gson.Gson;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import nro.models.data.LocalManager;
import nro.models.player.Player;
import nro.models.utils.Logger;

/** Durable, one-row-per-tier claim trail used for incident review and reconciliation. */
public final class ActivityClaimAuditService {

    private static final ActivityClaimAuditService INSTANCE = new ActivityClaimAuditService();
    private static final Gson GSON = new Gson();

    private ActivityClaimAuditService() {
    }

    public static ActivityClaimAuditService gI() {
        return INSTANCE;
    }

    public void ensureSchema(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("CREATE TABLE IF NOT EXISTS activity_claim_audit ("
                + "claim_id BIGINT NOT NULL AUTO_INCREMENT,"
                + "player_id BIGINT NOT NULL,"
                + "period_type VARCHAR(12) NOT NULL,"
                + "period_key VARCHAR(10) NOT NULL,"
                + "tier_id VARCHAR(64) NOT NULL,"
                + "claim_bit INT NOT NULL,"
                + "config_revision BIGINT NOT NULL,"
                + "reward_json LONGTEXT NOT NULL,"
                + "result_code VARCHAR(24) NOT NULL,"
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "PRIMARY KEY (claim_id),"
                + "UNIQUE KEY uk_activity_claim_once (player_id,period_type,period_key,tier_id),"
                + "KEY ix_activity_claim_tier_time (tier_id,created_at)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci")) {
            ps.executeUpdate();
        }
    }

    /**
     * Atomically reserves the durable idempotency key before delivery. A stale
     * PENDING row is intentionally safer than duplicating a production reward
     * and can be reconciled from the audit table by support staff.
     */
    public boolean reserve(Player player, ActivityPeriodType period, ActivityTier tier,
            ActivityConfig config, ActivityState state, List<String> deliveredRewards) {
        if (player == null || period == null || tier == null || config == null || state == null) {
            return false;
        }
        String periodKey = period == ActivityPeriodType.DAILY ? state.dayKey : state.weekKey;
        try (Connection connection = LocalManager.getConnection();
                PreparedStatement ps = connection.prepareStatement("INSERT IGNORE INTO activity_claim_audit "
                        + "(player_id,period_type,period_key,tier_id,claim_bit,config_revision,reward_json,result_code) "
                        + "VALUES (?,?,?,?,?,?,?,?)")) {
            ps.setLong(1, player.id);
            ps.setString(2, period.name());
            ps.setString(3, periodKey == null ? "" : periodKey);
            ps.setString(4, tier.getId());
            ps.setInt(5, tier.getClaimBit());
            ps.setLong(6, config.getRevision());
            ps.setString(7, GSON.toJson(deliveredRewards));
            ps.setString(8, "PENDING");
            return ps.executeUpdate() == 1;
        } catch (Exception e) {
            Logger.error("Cannot reserve Activity Points claim audit for player " + player.id + ".\n");
            return false;
        }
    }

    public void complete(Player player, ActivityPeriodType period, ActivityTier tier, ActivityState state) {
        updateResult(player, period, tier, state, "SUCCESS");
    }

    public void release(Player player, ActivityPeriodType period, ActivityTier tier, ActivityState state) {
        if (player == null || period == null || tier == null || state == null) {
            return;
        }
        String periodKey = period == ActivityPeriodType.DAILY ? state.dayKey : state.weekKey;
        try (Connection connection = LocalManager.getConnection();
                PreparedStatement ps = connection.prepareStatement("DELETE FROM activity_claim_audit "
                        + "WHERE player_id=? AND period_type=? AND period_key=? AND tier_id=? AND result_code='PENDING'")) {
            ps.setLong(1, player.id);
            ps.setString(2, period.name());
            ps.setString(3, periodKey == null ? "" : periodKey);
            ps.setString(4, tier.getId());
            ps.executeUpdate();
        } catch (Exception e) {
            Logger.error("Cannot release Activity Points claim reservation for player " + player.id + ".\n");
        }
    }

    private void updateResult(Player player, ActivityPeriodType period, ActivityTier tier,
            ActivityState state, String result) {
        if (player == null || period == null || tier == null || state == null) {
            return;
        }
        String periodKey = period == ActivityPeriodType.DAILY ? state.dayKey : state.weekKey;
        try (Connection connection = LocalManager.getConnection();
                PreparedStatement ps = connection.prepareStatement("UPDATE activity_claim_audit SET result_code=? "
                        + "WHERE player_id=? AND period_type=? AND period_key=? AND tier_id=? AND result_code='PENDING'")) {
            ps.setString(1, result);
            ps.setLong(2, player.id);
            ps.setString(3, period.name());
            ps.setString(4, periodKey == null ? "" : periodKey);
            ps.setString(5, tier.getId());
            int changed = ps.executeUpdate();
            if (changed != 1) {
                Logger.error("Activity Points claim reservation was missing for player " + player.id + ".\n");
            }
        } catch (Exception e) {
            Logger.error("Cannot complete Activity Points claim audit for player " + player.id + ".\n");
        }
    }
}
