package nro.models.social;

import java.sql.SQLException;
import java.time.Instant;

/** Atomic persistence boundary shared by JDBC production code and deterministic tests. */
public interface SocialRelationshipRepository {

    <T> T inTransaction(TransactionWork<T> work) throws SQLException;

    @FunctionalInterface
    interface TransactionWork<T> {
        T execute(Transaction transaction) throws SQLException;
    }

    interface Transaction {
        boolean lockExistingPlayers(SocialFriendPolicy.FriendshipPair pair) throws SQLException;
        boolean hasFriendship(SocialFriendPolicy.FriendshipPair pair) throws SQLException;
        int friendshipCount(long playerId) throws SQLException;
        void addFriendship(SocialFriendPolicy.FriendshipPair pair, Instant createdAt) throws SQLException;
        boolean removeFriendship(SocialFriendPolicy.FriendshipPair pair) throws SQLException;
        PendingFriendRequest findPendingByPairForUpdate(SocialFriendPolicy.FriendshipPair pair)
                throws SQLException;
        PendingFriendRequest findPendingById(long requestId) throws SQLException;
        PendingFriendRequest findPendingByIdForUpdate(long requestId) throws SQLException;
        long insertPending(SocialFriendPolicy.FriendshipPair pair, long senderId, long receiverId,
                Instant createdAt, Instant expiresAt) throws SQLException;
        boolean deletePending(long requestId) throws SQLException;
        int deleteExpiredForPair(SocialFriendPolicy.FriendshipPair pair, Instant now) throws SQLException;
        int deleteExpired(Instant now) throws SQLException;
    }
}
