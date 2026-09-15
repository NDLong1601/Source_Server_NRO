package nro.models.social;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pure friendship, search, chat, and location policy rules. */
public final class SocialFriendPolicy {

    public record FriendshipPair(long lowPlayerId, long highPlayerId) {
        public FriendshipPair {
            if (lowPlayerId <= 0L || highPlayerId <= lowPlayerId) {
                throw new IllegalArgumentException("Friendship pair must be positive and canonical");
            }
        }
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
        COOLDOWN,
        RATE_LIMITED
    }

    public FriendshipPair normalizePair(long firstPlayerId, long secondPlayerId) {
        if (firstPlayerId <= 0L || secondPlayerId <= 0L || firstPlayerId == secondPlayerId) {
            throw new IllegalArgumentException("Friendship requires two distinct positive player IDs");
        }
        return firstPlayerId < secondPlayerId
                ? new FriendshipPair(firstPlayerId, secondPlayerId)
                : new FriendshipPair(secondPlayerId, firstPlayerId);
    }

    public boolean canCreateFriendship(int currentFriendCount) {
        return currentFriendCount >= 0 && currentFriendCount < SocialV2Protocol.MAX_FRIENDS;
    }

    public RequestResolution resolveRequest(boolean hasOppositePendingRequest, long senderId, long receiverId) {
        normalizePair(senderId, receiverId);
        return hasOppositePendingRequest ? RequestResolution.AUTO_ACCEPT : RequestResolution.CREATE_PENDING;
    }

    public boolean isRequestExpired(Instant expiresAt, Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("Current time is required");
        }
        return expiresAt == null || !expiresAt.isAfter(now);
    }

    public List<SearchCandidate> rankSearch(String query, List<SearchCandidate> candidates) {
        String normalizedQuery = normalizeSearchQuery(query);
        Long numericId = positiveNumericId(normalizedQuery);
        Map<Long, RankedCandidate> bestByPlayerId = new HashMap<>();
        if (candidates != null) {
            for (SearchCandidate candidate : candidates) {
                if (candidate == null || candidate.self() || candidate.playerId() <= 0L
                        || candidate.name() == null) {
                    continue;
                }
                int rank = searchRank(normalizedQuery, numericId, candidate);
                if (rank < 0) {
                    continue;
                }
                RankedCandidate ranked = new RankedCandidate(candidate, rank);
                RankedCandidate existing = bestByPlayerId.get(candidate.playerId());
                if (existing == null || ranked.comparator().compare(ranked, existing) < 0) {
                    bestByPlayerId.put(candidate.playerId(), ranked);
                }
            }
        }
        List<RankedCandidate> ordered = new ArrayList<>(bestByPlayerId.values());
        ordered.sort(RankedCandidate::compareTo);
        return ordered.stream().map(RankedCandidate::candidate).toList();
    }

    /** Trims and validates the only free-text input admitted by social search. */
    public String normalizeSearchQuery(String query) {
        if (query == null) {
            throw new IllegalArgumentException("Search query is required");
        }
        String normalized = query.strip();
        if (!SocialV2Protocol.fitsModifiedUtf(normalized, SocialV2Protocol.SEARCH_MAX_CODE_POINTS)
                || normalized.codePointCount(0, normalized.length()) < SocialV2Protocol.SEARCH_MIN_CODE_POINTS) {
            throw new IllegalArgumentException("Search query must contain "
                    + SocialV2Protocol.SEARCH_MIN_CODE_POINTS + ".."
                    + SocialV2Protocol.SEARCH_MAX_CODE_POINTS + " code points");
        }
        return normalized;
    }

    public Authorization authorizeChat(boolean mutualFriends, boolean targetOnline, String text) {
        if (!mutualFriends) {
            return Authorization.NOT_FRIENDS;
        }
        if (!targetOnline) {
            return Authorization.OFFLINE;
        }
        try {
            normalizeChatText(text);
            return Authorization.ALLOWED;
        } catch (IllegalArgumentException invalidText) {
            return Authorization.INVALID_TEXT;
        }
    }

    public Authorization authorizeLocation(boolean mutualFriends, boolean targetOnline,
            Instant lastSharedAt, Instant now) {
        if (!mutualFriends) {
            return Authorization.NOT_FRIENDS;
        }
        if (!targetOnline) {
            return Authorization.OFFLINE;
        }
        if (now == null) {
            throw new IllegalArgumentException("Current time is required");
        }
        if (lastSharedAt != null
                && lastSharedAt.plusMillis(SocialV2Protocol.LOCATION_COOLDOWN_MILLIS).isAfter(now)) {
            return Authorization.COOLDOWN;
        }
        return Authorization.ALLOWED;
    }

    /**
     * Returns the only server-approved chat representation. It deliberately
     * rejects controls so packet readers, logs, and the legacy renderer never
     * receive invisible line or terminal controls.
     */
    public String normalizeChatText(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Chat text is required");
        }
        String normalized = text.strip();
        if (!SocialV2Protocol.fitsModifiedUtf(normalized, SocialV2Protocol.MAX_CHAT_CODE_POINTS)
                || normalized.isEmpty()) {
            throw new IllegalArgumentException("Chat text must contain 1.."
                    + SocialV2Protocol.MAX_CHAT_CODE_POINTS + " code points");
        }
        if (normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Chat text contains a control character");
        }
        return normalized;
    }

    private static Long positiveNumericId(String value) {
        if (!value.chars().allMatch(Character::isDigit)) {
            return null;
        }
        try {
            long id = Long.parseLong(value);
            return id > 0L ? id : null;
        } catch (NumberFormatException overflow) {
            return null;
        }
    }

    private static int searchRank(String normalizedQuery, Long numericId, SearchCandidate candidate) {
        String normalizedName = candidate.name().toLowerCase(Locale.ROOT);
        String lowerQuery = normalizedQuery.toLowerCase(Locale.ROOT);
        if (numericId != null && candidate.playerId() == numericId) {
            return 0;
        }
        if (normalizedName.equals(lowerQuery)) {
            return 1;
        }
        if (normalizedName.startsWith(lowerQuery)) {
            return 2;
        }
        return normalizedName.contains(lowerQuery) ? 3 : -1;
    }

    private record RankedCandidate(SearchCandidate candidate, int rank) implements Comparable<RankedCandidate> {
        @Override
        public int compareTo(RankedCandidate other) {
            return comparator().compare(this, other);
        }

        private Comparator<RankedCandidate> comparator() {
            return Comparator.comparingInt(RankedCandidate::rank)
                    .thenComparingLong(value -> value.candidate().playerId());
        }
    }
}
