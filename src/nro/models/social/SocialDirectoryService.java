package nro.models.social;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongPredicate;

/** Input validation and stable page construction over the read-only directory boundary. */
public final class SocialDirectoryService {

    private static final int MAX_CURSOR = 1_000_000;

    public record Page<T>(int requestToken, int nextCursor, boolean hasMore, List<T> entries) {
        public Page {
            if (requestToken <= 0 || nextCursor < 0 || entries == null
                    || entries.size() > SocialV2Protocol.PAGE_SIZE) {
                throw new IllegalArgumentException("Social page is invalid");
            }
            if ((!hasMore && nextCursor != 0) || (hasMore && nextCursor <= 0)) {
                throw new IllegalArgumentException("Social page cursor is invalid");
            }
            entries = List.copyOf(entries);
        }
    }

    public record FriendList(List<SocialDirectoryRepository.PlayerSummary> friends,
            int onlineFriendCount, int pendingRequestCount) {
        public FriendList {
            if (friends == null || onlineFriendCount < 0 || pendingRequestCount < 0
                    || onlineFriendCount > friends.size()) {
                throw new IllegalArgumentException("Social friend list is invalid");
            }
            friends = List.copyOf(friends);
        }
    }

    private final SocialDirectoryRepository repository;
    private final LongPredicate onlinePlayerIds;
    private final SocialFriendPolicy policy;

    public SocialDirectoryService(SocialDirectoryRepository repository, LongPredicate onlinePlayerIds) {
        if (repository == null || onlinePlayerIds == null) {
            throw new IllegalArgumentException("Directory repository and online lookup are required");
        }
        this.repository = repository;
        this.onlinePlayerIds = onlinePlayerIds;
        this.policy = new SocialFriendPolicy();
    }

    public Page<SocialDirectoryRepository.SearchEntry> search(long viewerId, int requestToken,
            int cursor, String query) throws SQLException {
        validatePageRequest(viewerId, requestToken, cursor);
        String normalizedQuery = policy.normalizeSearchQuery(query);
        List<SocialDirectoryRepository.SearchEntry> raw = repository.search(viewerId, normalizedQuery,
                cursor, SocialV2Protocol.PAGE_SIZE + 1);
        return page(requestToken, cursor, deduplicateSearch(viewerId, raw));
    }

    public Page<SocialDirectoryRepository.InboxEntry> inbox(long receiverId, int requestToken,
            int cursor, Instant now) throws SQLException {
        validatePageRequest(receiverId, requestToken, cursor);
        if (now == null) {
            throw new IllegalArgumentException("Current time is required");
        }
        List<SocialDirectoryRepository.InboxEntry> raw = repository.inbox(receiverId, cursor,
                SocialV2Protocol.PAGE_SIZE + 1, now);
        List<SocialDirectoryRepository.InboxEntry> valid = new ArrayList<>();
        if (raw != null) {
            for (SocialDirectoryRepository.InboxEntry entry : raw) {
                if (entry != null && entry.expiresAt().isAfter(now)) {
                    valid.add(entry);
                }
            }
        }
        return page(requestToken, cursor, valid);
    }

    public FriendList friendList(long playerId, Instant now) throws SQLException {
        if (playerId <= 0L || now == null) {
            throw new IllegalArgumentException("Player and current time are required");
        }
        Map<Long, SocialDirectoryRepository.PlayerSummary> unique = new LinkedHashMap<>();
        List<SocialDirectoryRepository.PlayerSummary> raw = repository.friends(playerId);
        if (raw != null) {
            for (SocialDirectoryRepository.PlayerSummary friend : raw) {
                if (friend != null && friend.playerId() != playerId) {
                    unique.putIfAbsent(friend.playerId(), friend);
                    if (unique.size() == SocialV2Protocol.MAX_FRIENDS) {
                        break;
                    }
                }
            }
        }
        List<SocialDirectoryRepository.PlayerSummary> friends = List.copyOf(unique.values());
        int online = 0;
        for (SocialDirectoryRepository.PlayerSummary friend : friends) {
            if (onlinePlayerIds.test(friend.playerId())) {
                online++;
            }
        }
        return new FriendList(friends, online, Math.max(0, repository.pendingCount(playerId, now)));
    }

    private static void validatePageRequest(long playerId, int requestToken, int cursor) {
        if (playerId <= 0L || requestToken <= 0 || cursor < 0 || cursor > MAX_CURSOR) {
            throw new IllegalArgumentException("Social page request is invalid");
        }
    }

    private static List<SocialDirectoryRepository.SearchEntry> deduplicateSearch(long viewerId,
            List<SocialDirectoryRepository.SearchEntry> raw) {
        Map<Long, SocialDirectoryRepository.SearchEntry> unique = new LinkedHashMap<>();
        if (raw != null) {
            for (SocialDirectoryRepository.SearchEntry entry : raw) {
                if (entry != null && entry.player().playerId() != viewerId) {
                    unique.putIfAbsent(entry.player().playerId(), entry);
                }
            }
        }
        return List.copyOf(unique.values());
    }

    private static <T> Page<T> page(int requestToken, int cursor, List<T> raw) {
        List<T> safe = raw == null ? List.of() : raw;
        boolean hasMore = safe.size() > SocialV2Protocol.PAGE_SIZE;
        List<T> entries = hasMore ? safe.subList(0, SocialV2Protocol.PAGE_SIZE) : safe;
        int nextCursor = hasMore ? cursor + SocialV2Protocol.PAGE_SIZE : 0;
        if (nextCursor < 0 || nextCursor > MAX_CURSOR) {
            throw new IllegalArgumentException("Social page cursor exceeds limit");
        }
        return new Page<>(requestToken, nextCursor, hasMore, entries);
    }
}
