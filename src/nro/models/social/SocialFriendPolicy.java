package nro.models.social;

import java.time.Instant;
import java.util.List;

/**
 * Phase-1 API reservation for the policy tests. Its operations deliberately
 * remain unimplemented until the persistence, search, and chat phases.
 */
public final class SocialFriendPolicy {

    public record FriendshipPair(long lowPlayerId, long highPlayerId) {
    }

    public record SearchCandidate(long playerId, String name, boolean self) {
    }

    public enum RequestResolution {
        CREATE_PENDING,
        AUTO_ACCEPT
    }

    public enum Authorization {
        ALLOWED,
        NOT_FRIENDS,
        OFFLINE,
        INVALID_TEXT,
        COOLDOWN
    }

    public FriendshipPair normalizePair(long firstPlayerId, long secondPlayerId) {
        throw unavailable("normalization requires phase-2 friendship persistence");
    }

    public boolean canCreateFriendship(int currentFriendCount) {
        throw unavailable("friend limits require phase-2 friendship persistence");
    }

    public RequestResolution resolveRequest(boolean hasOppositePendingRequest, long senderId, long receiverId) {
        throw unavailable("request resolution requires phase-2 request persistence");
    }

    public boolean isRequestExpired(Instant expiresAt, Instant now) {
        throw unavailable("request expiry requires phase-2 request persistence");
    }

    public List<SearchCandidate> rankSearch(String query, List<SearchCandidate> candidates) {
        throw unavailable("search ranking requires phase-3 search service");
    }

    public Authorization authorizeChat(boolean mutualFriends, boolean targetOnline, String text) {
        throw unavailable("chat authorization requires phase-4 presence service");
    }

    public Authorization authorizeLocation(boolean mutualFriends, boolean targetOnline,
            Instant lastSharedAt, Instant now) {
        throw unavailable("location authorization requires phase-4 presence service");
    }

    private UnsupportedOperationException unavailable(String detail) {
        return new UnsupportedOperationException("Social V2 behavior is not implemented: " + detail);
    }
}
