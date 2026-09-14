package nro.models.social;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

/** Narrow prepared-statement access to the normalized two-way friendship table. */
final class FriendshipRepository {

    boolean exists(Connection connection, SocialFriendPolicy.FriendshipPair pair) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM player_friendship WHERE player_low_id=? AND player_high_id=?")) {
            statement.setLong(1, pair.lowPlayerId());
            statement.setLong(2, pair.highPlayerId());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    int countForPlayer(Connection connection, long playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM player_friendship WHERE player_low_id=? OR player_high_id=?")) {
            statement.setLong(1, playerId);
            statement.setLong(2, playerId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt(1) : 0;
            }
        }
    }

    void insert(Connection connection, SocialFriendPolicy.FriendshipPair pair, Instant createdAt)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO player_friendship (player_low_id,player_high_id,created_at) VALUES (?,?,?)")) {
            statement.setLong(1, pair.lowPlayerId());
            statement.setLong(2, pair.highPlayerId());
            statement.setTimestamp(3, Timestamp.from(createdAt));
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Could not insert friendship");
            }
        }
    }

    boolean delete(Connection connection, SocialFriendPolicy.FriendshipPair pair) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM player_friendship WHERE player_low_id=? AND player_high_id=?")) {
            statement.setLong(1, pair.lowPlayerId());
            statement.setLong(2, pair.highPlayerId());
            return statement.executeUpdate() == 1;
        }
    }
}
