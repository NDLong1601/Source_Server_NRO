package nro.models.social;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;

/** Prepared-statement access to pending friend requests only. */
final class FriendRequestRepository {

    PendingFriendRequest findByPairForUpdate(Connection connection,
            SocialFriendPolicy.FriendshipPair pair) throws SQLException {
        String sql = "SELECT id,pair_low_id,pair_high_id,sender_id,receiver_id,created_at,expires_at "
                + "FROM friend_request WHERE pair_low_id=? AND pair_high_id=? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, pair.lowPlayerId());
            statement.setLong(2, pair.highPlayerId());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? map(rows) : null;
            }
        }
    }

    PendingFriendRequest findById(Connection connection, long requestId) throws SQLException {
        String sql = "SELECT id,pair_low_id,pair_high_id,sender_id,receiver_id,created_at,expires_at "
                + "FROM friend_request WHERE id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, requestId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? map(rows) : null;
            }
        }
    }

    PendingFriendRequest findByIdForUpdate(Connection connection, long requestId) throws SQLException {
        String sql = "SELECT id,pair_low_id,pair_high_id,sender_id,receiver_id,created_at,expires_at "
                + "FROM friend_request WHERE id=? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, requestId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? map(rows) : null;
            }
        }
    }

    long insert(Connection connection, SocialFriendPolicy.FriendshipPair pair, long senderId,
            long receiverId, Instant createdAt, Instant expiresAt) throws SQLException {
        String sql = "INSERT INTO friend_request (pair_low_id,pair_high_id,sender_id,receiver_id,created_at,expires_at) "
                + "VALUES (?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, pair.lowPlayerId());
            statement.setLong(2, pair.highPlayerId());
            statement.setLong(3, senderId);
            statement.setLong(4, receiverId);
            statement.setTimestamp(5, Timestamp.from(createdAt));
            statement.setTimestamp(6, Timestamp.from(expiresAt));
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Could not insert friend request");
            }
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next() || keys.getLong(1) <= 0L) {
                    throw new SQLException("Friend request did not return a generated ID");
                }
                return keys.getLong(1);
            }
        }
    }

    boolean delete(Connection connection, long requestId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM friend_request WHERE id=?")) {
            statement.setLong(1, requestId);
            return statement.executeUpdate() == 1;
        }
    }

    int deleteExpiredForPair(Connection connection, SocialFriendPolicy.FriendshipPair pair, Instant now)
            throws SQLException {
        String sql = "DELETE FROM friend_request WHERE pair_low_id=? AND pair_high_id=? AND expires_at<=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, pair.lowPlayerId());
            statement.setLong(2, pair.highPlayerId());
            statement.setTimestamp(3, Timestamp.from(now));
            return statement.executeUpdate();
        }
    }

    int deleteExpired(Connection connection, Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM friend_request WHERE expires_at<=?")) {
            statement.setTimestamp(1, Timestamp.from(now));
            return statement.executeUpdate();
        }
    }

    private static PendingFriendRequest map(ResultSet rows) throws SQLException {
        long lowId = rows.getLong("pair_low_id");
        long highId = rows.getLong("pair_high_id");
        Timestamp created = rows.getTimestamp("created_at");
        Timestamp expires = rows.getTimestamp("expires_at");
        return new PendingFriendRequest(rows.getLong("id"),
                new SocialFriendPolicy.FriendshipPair(lowId, highId), rows.getLong("sender_id"),
                rows.getLong("receiver_id"), created == null ? null : created.toInstant(),
                expires == null ? null : expires.toInstant());
    }
}
