package nro.models.social;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Server-side relationship transitions. It has no packet or Player dependency. */
public final class SocialRelationshipService {

    public enum Status {
        REQUEST_SENT,
        ALREADY_PENDING,
        AUTO_ACCEPTED,
        ALREADY_FRIENDS,
        FRIEND_LIMIT_REACHED,
        TARGET_FRIEND_LIMIT_REACHED,
        INVALID_TARGET,
        TARGET_NOT_FOUND,
        REQUEST_NOT_FOUND,
        NOT_REQUEST_RECIPIENT,
        REQUEST_EXPIRED,
        REQUEST_ACCEPTED,
        REQUEST_REJECTED,
        FRIENDSHIP_REMOVED,
        NOT_FRIENDS,
        DATABASE_FAILURE
    }

    /**
     * The affected player is server-derived from the locked relationship, never a
     * client-supplied target. The facade uses it only for an online state push.
     */
    public record Result(Status status, long requestId, long affectedPlayerId) {
        public Result {
            if (status == null || requestId < 0L || affectedPlayerId < 0L) {
                throw new IllegalArgumentException("Invalid social relationship result");
            }
        }

        public Result(Status status, long requestId) {
            this(status, requestId, 0L);
        }
    }

    private final SocialRelationshipRepository repository;
    private final SocialFriendPolicy policy;

    public SocialRelationshipService(SocialRelationshipRepository repository, SocialFriendPolicy policy) {
        if (repository == null || policy == null) {
            throw new IllegalArgumentException("Social repository and policy are required");
        }
        this.repository = repository;
        this.policy = policy;
    }

    public Result sendRequest(long senderId, long receiverId, Instant now) {
        SocialFriendPolicy.FriendshipPair pair = pairOrNull(senderId, receiverId);
        if (pair == null || now == null) {
            return result(Status.INVALID_TARGET);
        }
        try {
            Result transition = repository.inTransaction(transaction -> {
                if (!transaction.lockExistingPlayers(pair)) {
                    return result(Status.TARGET_NOT_FOUND);
                }
                transaction.deleteExpiredForPair(pair, now);
                if (transaction.hasFriendship(pair)) {
                    return result(Status.ALREADY_FRIENDS);
                }
                if (!policy.canCreateFriendship(transaction.friendshipCount(senderId))) {
                    return result(Status.FRIEND_LIMIT_REACHED);
                }
                if (!policy.canCreateFriendship(transaction.friendshipCount(receiverId))) {
                    return result(Status.TARGET_FRIEND_LIMIT_REACHED);
                }
                PendingFriendRequest pending = transaction.findPendingByPairForUpdate(pair);
                if (pending == null) {
                    long requestId = transaction.insertPending(pair, senderId, receiverId, now,
                            now.plus(SocialV2Protocol.REQUEST_EXPIRY_DAYS, ChronoUnit.DAYS));
                    return new Result(Status.REQUEST_SENT, requestId);
                }
                if (pending.senderId() == senderId) {
                    return new Result(Status.ALREADY_PENDING, pending.id());
                }
                if (policy.resolveRequest(true, senderId, receiverId)
                        != SocialFriendPolicy.RequestResolution.AUTO_ACCEPT) {
                    return result(Status.DATABASE_FAILURE);
                }
                transaction.addFriendship(pair, now);
                transaction.deletePending(pending.id());
                return result(Status.AUTO_ACCEPTED);
            });
            return withAffectedPlayer(transition, receiverId);
        } catch (SQLException error) {
            return result(Status.DATABASE_FAILURE);
        }
    }

    public Result acceptRequest(long receiverId, long requestId, Instant now) {
        if (receiverId <= 0L || requestId <= 0L || now == null) {
            return result(Status.INVALID_TARGET);
        }
        try {
            return repository.inTransaction(transaction -> {
                PendingFriendRequest candidate = transaction.findPendingById(requestId);
                if (candidate == null) {
                    return result(Status.REQUEST_NOT_FOUND);
                }
                if (!transaction.lockExistingPlayers(candidate.pair())) {
                    return result(Status.TARGET_NOT_FOUND, candidate.senderId());
                }
                PendingFriendRequest pending = transaction.findPendingByIdForUpdate(requestId);
                if (pending == null) {
                    return result(Status.REQUEST_NOT_FOUND, candidate.senderId());
                }
                if (pending.receiverId() != receiverId) {
                    return result(Status.NOT_REQUEST_RECIPIENT, pending.senderId());
                }
                if (policy.isRequestExpired(pending.expiresAt(), now)) {
                    transaction.deletePending(pending.id());
                    return result(Status.REQUEST_EXPIRED, pending.senderId());
                }
                if (transaction.hasFriendship(pending.pair())) {
                    transaction.deletePending(pending.id());
                    return result(Status.ALREADY_FRIENDS, pending.senderId());
                }
                if (!policy.canCreateFriendship(transaction.friendshipCount(pending.pair().lowPlayerId()))
                        || !policy.canCreateFriendship(transaction.friendshipCount(pending.pair().highPlayerId()))) {
                    return result(Status.FRIEND_LIMIT_REACHED, pending.senderId());
                }
                transaction.addFriendship(pending.pair(), now);
                transaction.deletePending(pending.id());
                return result(Status.REQUEST_ACCEPTED, pending.senderId());
            });
        } catch (SQLException error) {
            return result(Status.DATABASE_FAILURE);
        }
    }

    public Result rejectRequest(long receiverId, long requestId) {
        if (receiverId <= 0L || requestId <= 0L) {
            return result(Status.INVALID_TARGET);
        }
        try {
            return repository.inTransaction(transaction -> {
                PendingFriendRequest candidate = transaction.findPendingById(requestId);
                if (candidate == null) {
                    return result(Status.REQUEST_NOT_FOUND);
                }
                if (!transaction.lockExistingPlayers(candidate.pair())) {
                    return result(Status.TARGET_NOT_FOUND, candidate.senderId());
                }
                PendingFriendRequest pending = transaction.findPendingByIdForUpdate(requestId);
                if (pending == null) {
                    return result(Status.REQUEST_NOT_FOUND, candidate.senderId());
                }
                if (pending.receiverId() != receiverId) {
                    return result(Status.NOT_REQUEST_RECIPIENT, pending.senderId());
                }
                transaction.deletePending(pending.id());
                return result(Status.REQUEST_REJECTED, pending.senderId());
            });
        } catch (SQLException error) {
            return result(Status.DATABASE_FAILURE);
        }
    }

    public Result removeFriendship(long actorId, long friendId) {
        SocialFriendPolicy.FriendshipPair pair = pairOrNull(actorId, friendId);
        if (pair == null) {
            return result(Status.INVALID_TARGET);
        }
        try {
            return repository.inTransaction(transaction -> {
                if (!transaction.lockExistingPlayers(pair)) {
                    return result(Status.TARGET_NOT_FOUND);
                }
                return result(transaction.removeFriendship(pair)
                        ? Status.FRIENDSHIP_REMOVED : Status.NOT_FRIENDS, friendId);
            });
        } catch (SQLException error) {
            return result(Status.DATABASE_FAILURE);
        }
    }

    public int expireRequests(Instant now) {
        if (now == null) {
            return 0;
        }
        try {
            return repository.inTransaction(transaction -> transaction.deleteExpired(now));
        } catch (SQLException error) {
            return 0;
        }
    }

    private SocialFriendPolicy.FriendshipPair pairOrNull(long firstPlayerId, long secondPlayerId) {
        try {
            return policy.normalizePair(firstPlayerId, secondPlayerId);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private static Result result(Status status) {
        return new Result(status, 0L);
    }

    private static Result result(Status status, long affectedPlayerId) {
        return new Result(status, 0L, affectedPlayerId);
    }

    private static Result withAffectedPlayer(Result result, long affectedPlayerId) {
        return new Result(result.status(), result.requestId(), affectedPlayerId);
    }
}
