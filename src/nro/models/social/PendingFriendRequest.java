package nro.models.social;

import java.time.Instant;

/** Pending-only request row. Accepted, rejected, and expired requests are deleted. */
public record PendingFriendRequest(long id, SocialFriendPolicy.FriendshipPair pair,
        long senderId, long receiverId, Instant createdAt, Instant expiresAt) {

    public PendingFriendRequest {
        if (id <= 0L || pair == null || senderId <= 0L || receiverId <= 0L
                || senderId == receiverId || createdAt == null || expiresAt == null) {
            throw new IllegalArgumentException("Invalid pending friend request");
        }
        if ((senderId != pair.lowPlayerId() && senderId != pair.highPlayerId())
                || (receiverId != pair.lowPlayerId() && receiverId != pair.highPlayerId())) {
            throw new IllegalArgumentException("Request actors must belong to its canonical pair");
        }
    }
}
