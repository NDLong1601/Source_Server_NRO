package nro.models.social;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import nro.models.data.LocalManager;

/**
 * Owns the only JDBC transaction boundary for social relationship mutations.
 * Player rows are locked low-to-high before friendship/request rows so every
 * transition for either player serializes with the same lock order.
 */
public final class JdbcSocialRelationshipRepository implements SocialRelationshipRepository {

    @FunctionalInterface
    public interface ConnectionFactory {
        Connection open() throws SQLException;
    }

    private final ConnectionFactory connections;
    private final FriendshipRepository friendships = new FriendshipRepository();
    private final FriendRequestRepository requests = new FriendRequestRepository();

    public JdbcSocialRelationshipRepository() {
        this(LocalManager::getConnection);
    }

    public JdbcSocialRelationshipRepository(ConnectionFactory connections) {
        if (connections == null) {
            throw new IllegalArgumentException("Connection factory is required");
        }
        this.connections = connections;
    }

    @Override
    public <T> T inTransaction(TransactionWork<T> work) throws SQLException {
        if (work == null) {
            throw new IllegalArgumentException("Transaction work is required");
        }
        try (Connection connection = connections.open()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = work.execute(new JdbcTransaction(connection));
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException error) {
                rollbackQuietly(connection);
                throw error;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    private final class JdbcTransaction implements Transaction {
        private final Connection connection;

        private JdbcTransaction(Connection connection) {
            this.connection = connection;
        }

        @Override
        public boolean lockExistingPlayers(SocialFriendPolicy.FriendshipPair pair) throws SQLException {
            return lockPlayer(pair.lowPlayerId()) && lockPlayer(pair.highPlayerId());
        }

        @Override
        public boolean hasFriendship(SocialFriendPolicy.FriendshipPair pair) throws SQLException {
            return friendships.exists(connection, pair);
        }

        @Override
        public int friendshipCount(long playerId) throws SQLException {
            return friendships.countForPlayer(connection, playerId);
        }

        @Override
        public void addFriendship(SocialFriendPolicy.FriendshipPair pair, Instant createdAt) throws SQLException {
            friendships.insert(connection, pair, createdAt);
        }

        @Override
        public boolean removeFriendship(SocialFriendPolicy.FriendshipPair pair) throws SQLException {
            return friendships.delete(connection, pair);
        }

        @Override
        public PendingFriendRequest findPendingByPairForUpdate(SocialFriendPolicy.FriendshipPair pair)
                throws SQLException {
            return requests.findByPairForUpdate(connection, pair);
        }

        @Override
        public PendingFriendRequest findPendingById(long requestId) throws SQLException {
            return requests.findById(connection, requestId);
        }

        @Override
        public PendingFriendRequest findPendingByIdForUpdate(long requestId) throws SQLException {
            return requests.findByIdForUpdate(connection, requestId);
        }

        @Override
        public long insertPending(SocialFriendPolicy.FriendshipPair pair, long senderId, long receiverId,
                Instant createdAt, Instant expiresAt) throws SQLException {
            return requests.insert(connection, pair, senderId, receiverId, createdAt, expiresAt);
        }

        @Override
        public boolean deletePending(long requestId) throws SQLException {
            return requests.delete(connection, requestId);
        }

        @Override
        public int deleteExpiredForPair(SocialFriendPolicy.FriendshipPair pair, Instant now) throws SQLException {
            return requests.deleteExpiredForPair(connection, pair, now);
        }

        @Override
        public int deleteExpired(Instant now) throws SQLException {
            return requests.deleteExpired(connection, now);
        }

        private boolean lockPlayer(long playerId) throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT id FROM player WHERE id=? FOR UPDATE")) {
                statement.setLong(1, playerId);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next();
                }
            }
        }
    }

    private static void rollbackQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Original database error remains authoritative.
        }
    }
}
