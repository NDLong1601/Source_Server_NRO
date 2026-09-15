package nro.models.social;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Small, deterministic checks for search, paging, inbox, and list summaries. */
public final class SocialDirectoryRegressionTest {

    private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");

    private SocialDirectoryRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        verifySearchRanking();
        verifySearchPagingAndValidation();
        verifyInboxAndFriendSummary();
        System.out.println("SocialDirectoryRegressionTest: PASS");
    }

    private static void verifySearchRanking() {
        SocialFriendPolicy policy = new SocialFriendPolicy();
        List<SocialFriendPolicy.SearchCandidate> ranked = policy.rankSearch("an", List.of(
                new SocialFriendPolicy.SearchCandidate(7L, "Banan", false),
                new SocialFriendPolicy.SearchCandidate(8L, "An", false),
                new SocialFriendPolicy.SearchCandidate(9L, "Anh", false),
                new SocialFriendPolicy.SearchCandidate(10L, "An", true),
                new SocialFriendPolicy.SearchCandidate(8L, "An duplicate", false)));
        check(ids(ranked).equals(List.of(8L, 9L, 7L)),
                "search order must be exact name, prefix, contains with self/duplicate excluded");

        ranked = policy.rankSearch("42", List.of(
                new SocialFriendPolicy.SearchCandidate(9L, "42", false),
                new SocialFriendPolicy.SearchCandidate(42L, "Other", false),
                new SocialFriendPolicy.SearchCandidate(43L, "x42", false)));
        check(ids(ranked).equals(List.of(42L, 9L, 43L)), "exact numeric id must rank first");
    }

    private static void verifySearchPagingAndValidation() throws Exception {
        FakeDirectory repository = new FakeDirectory();
        for (long id = 100L; id < 125L; id++) {
            repository.searchEntries.add(new SocialDirectoryRepository.SearchEntry(
                    new SocialDirectoryRepository.PlayerSummary(id, "An" + id, (short) id),
                    SocialDirectoryRepository.Relationship.CAN_ADD));
        }
        SocialDirectoryService service = new SocialDirectoryService(repository, id -> id == 101L);
        SocialDirectoryService.Page<SocialDirectoryRepository.SearchEntry> first =
                service.search(1L, 77, 0, "  an  ");
        check(first.requestToken() == 77 && first.entries().size() == 20 && first.hasMore(),
                "first search page must carry token, page size and hasMore");
        check(first.nextCursor() == 20, "first search cursor must advance by page size");
        SocialDirectoryService.Page<SocialDirectoryRepository.SearchEntry> second =
                service.search(1L, 77, first.nextCursor(), "an");
        check(second.entries().size() == 5 && !second.hasMore() && second.nextCursor() == 0,
                "last search page must not emit a stale cursor");
        Set<Long> unique = new LinkedHashSet<>();
        first.entries().forEach(entry -> unique.add(entry.player().playerId()));
        second.entries().forEach(entry -> unique.add(entry.player().playerId()));
        check(unique.size() == 25, "search paging must not duplicate entries");
        check("an".equals(repository.lastQuery), "search query must be trimmed before repository access");
        expectFailure(() -> service.search(1L, 77, 0, "a"));
        expectFailure(() -> service.search(1L, 0, 0, "an"));
        expectFailure(() -> service.search(1L, 77, -1, "an"));
    }

    private static void verifyInboxAndFriendSummary() throws Exception {
        FakeDirectory repository = new FakeDirectory();
        repository.inboxEntries.add(new SocialDirectoryRepository.InboxEntry(11L,
                new SocialDirectoryRepository.PlayerSummary(2L, "Sender", (short) 12), NOW.plusSeconds(60L)));
        repository.inboxEntries.add(new SocialDirectoryRepository.InboxEntry(12L,
                new SocialDirectoryRepository.PlayerSummary(3L, "Second", (short) 13), NOW.plusSeconds(120L)));
        repository.friends.add(new SocialDirectoryRepository.PlayerSummary(11L, "Online", (short) 21));
        repository.friends.add(new SocialDirectoryRepository.PlayerSummary(12L, "Offline", (short) 22));
        repository.pendingCount = 3;
        SocialDirectoryService service = new SocialDirectoryService(repository, id -> id == 11L);

        SocialDirectoryService.Page<SocialDirectoryRepository.InboxEntry> inbox =
                service.inbox(1L, 9, 0, NOW);
        check(inbox.entries().size() == 2 && !inbox.hasMore(), "inbox must expose pending receiver requests");
        SocialDirectoryService.FriendList list = service.friendList(1L, NOW);
        check(list.friends().size() == 2 && list.onlineFriendCount() == 1 && list.pendingRequestCount() == 3,
                "friend list summary must count normalized friendships, online state and pending requests");

        for (long id = 100L; id <= 200L; id++) {
            repository.friends.add(new SocialDirectoryRepository.PlayerSummary(id, "Friend" + id, (short) id));
        }
        SocialDirectoryService.FriendList capped = service.friendList(1L, NOW);
        check(capped.friends().size() == SocialV2Protocol.MAX_FRIENDS,
                "legacy-corrupt over-limit friendship data must not overflow action-0's u8 count");
    }

    private static List<Long> ids(List<SocialFriendPolicy.SearchCandidate> candidates) {
        return candidates.stream().map(SocialFriendPolicy.SearchCandidate::playerId).toList();
    }

    private static void expectFailure(ThrowingRunnable action) {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        } catch (Exception exception) {
            throw new AssertionError("unexpected exception", exception);
        }
        check(rejected, "input must be rejected");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class FakeDirectory implements SocialDirectoryRepository {
        private final List<SearchEntry> searchEntries = new ArrayList<>();
        private final List<InboxEntry> inboxEntries = new ArrayList<>();
        private final List<PlayerSummary> friends = new ArrayList<>();
        private int pendingCount;
        private String lastQuery;

        @Override
        public List<SearchEntry> search(long viewerId, String query, int offset, int limit) {
            lastQuery = query;
            return slice(searchEntries, offset, limit);
        }

        @Override
        public List<InboxEntry> inbox(long receiverId, int offset, int limit, Instant now) {
            return slice(inboxEntries, offset, limit);
        }

        @Override
        public List<PlayerSummary> friends(long playerId) {
            return List.copyOf(friends);
        }

        @Override
        public int pendingCount(long receiverId, Instant now) {
            return pendingCount;
        }

        private static <T> List<T> slice(List<T> values, int offset, int limit) {
            if (offset >= values.size()) {
                return List.of();
            }
            return List.copyOf(values.subList(offset, Math.min(values.size(), offset + limit)));
        }
    }
}
