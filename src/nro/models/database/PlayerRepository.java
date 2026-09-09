package nro.models.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import nro.models.data.LocalManager;
import nro.models.player.PlayerRankSnapshot;
import nro.models.player.PlayerSnapshot;
import nro.models.player.PlayerWalletSnapshot;

/** JDBC repository that only accepts immutable snapshots, never live Player objects. */
public final class PlayerRepository {

    public enum SaveStatus {
        SAVED,
        OPTIMISTIC_CONFLICT
    }

    public SaveStatus save(PlayerSnapshot snapshot) throws SQLException {
        PlayerPersistenceSchema.ensureReady();
        try (Connection connection = LocalManager.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                int updated = updatePlayer(connection, snapshot);
                if (updated != 1) {
                    connection.rollback();
                    return SaveStatus.OPTIMISTIC_CONFLICT;
                }
                upsertWallet(connection, snapshot);
                updateSuperRank(connection, snapshot);
                connection.commit();
                return SaveStatus.SAVED;
            } catch (SQLException error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    private static int updatePlayer(Connection connection, PlayerSnapshot snapshot) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(snapshot.updateSql())) {
            Object[] parameters = snapshot.parameters();
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            return statement.executeUpdate();
        }
    }

    private static void upsertWallet(Connection connection, PlayerSnapshot snapshot) throws SQLException {
        PlayerWalletSnapshot wallet = snapshot.wallet();
        String sql = "INSERT INTO player_wallet (player_id,gold,gem,ruby,coupon,save_version) "
                + "VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE gold=VALUES(gold),gem=VALUES(gem),"
                + "ruby=VALUES(ruby),coupon=VALUES(coupon),save_version=VALUES(save_version)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, snapshot.playerId());
            statement.setLong(2, wallet.gold());
            statement.setInt(3, wallet.gem());
            statement.setInt(4, wallet.ruby());
            statement.setInt(5, wallet.coupon());
            statement.setLong(6, snapshot.committedSaveVersion());
            statement.executeUpdate();
        }
    }

    private static void updateSuperRank(Connection connection, PlayerSnapshot snapshot) throws SQLException {
        PlayerRankSnapshot rank = snapshot.superRank();
        if (rank == null) {
            return;
        }
        String sql = "UPDATE super_rank SET rank=?,name=?,info=?,last_pk_time=?,last_reward_time=?,"
                + "ticket=?,win=?,lose=?,history=? WHERE player_id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, rank.rank());
            statement.setString(2, rank.name());
            statement.setString(3, rank.infoJson());
            statement.setLong(4, rank.lastPkTime());
            statement.setLong(5, rank.lastRewardTime());
            statement.setInt(6, rank.ticket());
            statement.setInt(7, rank.win());
            statement.setInt(8, rank.lose());
            statement.setString(9, rank.historyJson());
            statement.setLong(10, snapshot.playerId());
            statement.executeUpdate();
        }
    }

    public void recordDeadLetter(long playerId, String playerName, int attempts, String error)
            throws SQLException {
        PlayerPersistenceSchema.ensureReady();
        String safeError = error == null ? "unknown" : error.substring(0, Math.min(512, error.length()));
        String sql = "INSERT INTO player_save_dead_letter (player_id,player_name,attempts,last_error) "
                + "VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE player_name=VALUES(player_name),"
                + "attempts=VALUES(attempts),last_error=VALUES(last_error),failed_at=CURRENT_TIMESTAMP";
        try (Connection connection = LocalManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, playerId);
            statement.setString(2, playerName);
            statement.setInt(3, attempts);
            statement.setString(4, safeError);
            statement.executeUpdate();
        }
    }
}
