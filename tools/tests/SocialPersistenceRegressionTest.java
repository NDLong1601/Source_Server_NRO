package nro.models.social;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Stage-2 regression suite using an in-memory transaction double. */
public final class SocialPersistenceRegressionTest {

    private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");

    private SocialPersistenceRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        verifyCanonicalPairAndLimitPolicy();
        verifyIdempotentAndCrossRequests();
        verifyAcceptRejectExpiryAndMissingPlayers();
        verifyConcurrentAcceptAndRemoveRemainTwoSided();
        verifyLegacyBackfillUnionAndMalformedRows();
        System.out.println("SocialPersistenceRegressionTest: PASS");
    }

    private static void verifyCanonicalPairAndLimitPolicy() {
        SocialFriendPolicy policy = new SocialFriendPolicy();
        SocialFriendPolicy.FriendshipPair pair = policy.normalizePair(90L, 12L);
        check(pair.lowPlayerId() == 12L && pair.highPlayerId() == 90L,
                "pair must normalize low-to-high");
        expectFailure(() -> policy.normalizePair(12L, 12L));
        check(policy.canCreateFriendship(99), "99 friends must be allowed");
        check(!policy.canCreateFriendship(100), "100 friends must be rejected");
    }

    private static void verifyIdempotentAndCrossRequests() {
        InMemoryRepository store = new InMemoryRepository(1L, 2L);
        SocialRelationshipService service = new SocialRelationshipService(store, new SocialFriendPolicy());
        SocialRelationshipService.Result first = service.sendRequest(1L, 2L, NOW);
        check(first.status() == SocialRelationshipService.Status.REQUEST_SENT
                && first.affectedPlayerId() == 2L,
                "first request must retain the server-derived receiving player for a pending-count push");
        check(service.sendRequest(1L, 2L, NOW).status() == SocialRelationshipService.Status.ALREADY_PENDING,
                "same request must be idempotent");
        SocialRelationshipService.Result cross = service.sendRequest(2L, 1L, NOW);
        check(cross.status() == SocialRelationshipService.Status.AUTO_ACCEPTED && cross.affectedPlayerId() == 1L,
                "cross request must create a friendship and identify the other affected player");
        check(store.hasFriendship(1L, 2L), "cross request must create one canonical friendship");
        check(store.pendingCount() == 0, "cross request must consume pending request");
    }

    private static void verifyAcceptRejectExpiryAndMissingPlayers() {
        InMemoryRepository store = new InMemoryRepository(1L, 2L, 3L);
        SocialRelationshipService service = new SocialRelationshipService(store, new SocialFriendPolicy());

        SocialRelationshipService.Result request = service.sendRequest(1L, 2L, NOW);
        check(request.status() == SocialRelationshipService.Status.REQUEST_SENT, "request must be stored");
        SocialRelationshipService.Result rejected = service.rejectRequest(2L, request.requestId());
        check(rejected.status() == SocialRelationshipService.Status.REQUEST_REJECTED
                && rejected.affectedPlayerId() == 1L, "receiver may reject request");
        check(store.pendingCount() == 0, "rejected request must be deleted");

        SocialRelationshipService.Result acceptedRequest = service.sendRequest(1L, 2L, NOW);
        SocialRelationshipService.Result accepted = service.acceptRequest(2L, acceptedRequest.requestId(), NOW);
        check(accepted.status() == SocialRelationshipService.Status.REQUEST_ACCEPTED
                && accepted.affectedPlayerId() == 1L,
                "accept must identify the request sender for a server-derived peer refresh");
        SocialRelationshipService.Result removed = service.removeFriendship(1L, 2L);
        check(removed.status() == SocialRelationshipService.Status.FRIENDSHIP_REMOVED
                && removed.affectedPlayerId() == 2L,
                "remove must identify the other endpoint for a server-derived peer refresh");

        SocialRelationshipService.Result expiring = service.sendRequest(1L, 2L, NOW);
        check(service.acceptRequest(2L, expiring.requestId(), NOW.plus(31L, ChronoUnit.DAYS)).status()
                == SocialRelationshipService.Status.REQUEST_EXPIRED, "expired request must not create friendship");
        check(store.pendingCount() == 0, "expired request must be deleted");
        check(!store.hasFriendship(1L, 2L), "expiry must not create friendship");

        check(service.sendRequest(1L, 1L, NOW).status()
                == SocialRelationshipService.Status.INVALID_TARGET, "self request must be rejected");
        check(service.sendRequest(1L, 999L, NOW).status()
                == SocialRelationshipService.Status.TARGET_NOT_FOUND, "missing target must be rejected");

        for (long playerId = 10L; playerId < 110L; playerId++) {
            store.addPlayer(playerId);
            store.addFriendship(1L, playerId, NOW);
        }
        check(service.sendRequest(1L, 3L, NOW).status()
                == SocialRelationshipService.Status.FRIEND_LIMIT_REACHED,
                "sender with 100 friends must not create a pending request");

        InMemoryRepository fullReceiver = new InMemoryRepository(1L, 2L);
        SocialRelationshipService receiverService = new SocialRelationshipService(fullReceiver,
                new SocialFriendPolicy());
        long fullRequestId = receiverService.sendRequest(1L, 2L, NOW).requestId();
        for (long playerId = 10L; playerId < 110L; playerId++) {
            fullReceiver.addPlayer(playerId);
            fullReceiver.addFriendship(2L, playerId, NOW);
        }
        check(receiverService.acceptRequest(2L, fullRequestId, NOW).status()
                == SocialRelationshipService.Status.FRIEND_LIMIT_REACHED,
                "full receiver must not accept a request");
        check(fullReceiver.pendingCount() == 1 && !fullReceiver.hasFriendship(1L, 2L),
                "failed accept must preserve pending state and avoid a one-sided friendship");
    }

    private static void verifyConcurrentAcceptAndRemoveRemainTwoSided() throws Exception {
        InMemoryRepository store = new InMemoryRepository(1L, 2L);
        SocialRelationshipService service = new SocialRelationshipService(store, new SocialFriendPolicy());
        long requestId = service.sendRequest(1L, 2L, NOW).requestId();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<SocialRelationshipService.Result>> accepted = executor.invokeAll(List.of(
                    () -> service.acceptRequest(2L, requestId, NOW),
                    () -> service.acceptRequest(2L, requestId, NOW)));
            int success = 0;
            for (Future<SocialRelationshipService.Result> result : accepted) {
                if (result.get().status() == SocialRelationshipService.Status.REQUEST_ACCEPTED) {
                    success++;
                }
            }
            check(success == 1, "exactly one concurrent accept may create the friendship");
            check(store.hasFriendship(1L, 2L) && store.pendingCount() == 0,
                    "accept race must leave one friendship and no request");

            List<Callable<SocialRelationshipService.Result>> removes = List.of(
                    () -> service.removeFriendship(1L, 2L),
                    () -> service.removeFriendship(2L, 1L));
            int removed = 0;
            for (Future<SocialRelationshipService.Result> result : executor.invokeAll(removes)) {
                if (result.get().status() == SocialRelationshipService.Status.FRIENDSHIP_REMOVED) {
                    removed++;
                }
            }
            check(removed == 1 && !store.hasFriendship(1L, 2L),
                    "remove race must not leave a one-sided relationship");

            InMemoryRepository acceptRemoveStore = new InMemoryRepository(3L, 4L);
            SocialRelationshipService acceptRemoveService = new SocialRelationshipService(acceptRemoveStore,
                    new SocialFriendPolicy());
            long pendingId = acceptRemoveService.sendRequest(3L, 4L, NOW).requestId();
            List<Future<SocialRelationshipService.Result>> mixed = executor.invokeAll(List.of(
                    () -> acceptRemoveService.acceptRequest(4L, pendingId, NOW),
                    () -> acceptRemoveService.removeFriendship(3L, 4L)));
            SocialRelationshipService.Result accept = mixed.get(0).get();
            SocialRelationshipService.Result remove = mixed.get(1).get();
            check(accept.status() == SocialRelationshipService.Status.REQUEST_ACCEPTED,
                    "concurrent remove may not invalidate a pending request");
            check(remove.status() == SocialRelationshipService.Status.NOT_FRIENDS
                    || remove.status() == SocialRelationshipService.Status.FRIENDSHIP_REMOVED,
                    "remove outcome must reflect one canonical relationship only");
            check(acceptRemoveStore.pendingCount() == 0,
                    "accept/remove race must not retain a stale pending request");
        } finally {
            executor.shutdownNow();
        }
    }

    private static void verifyLegacyBackfillUnionAndMalformedRows() {
        LegacyFriendBackfill.Analysis analysis = LegacyFriendBackfill.analyze(List.of(
                new LegacyFriendBackfill.LegacyRow(1L,
                        "[\"[2,\\\"Two\\\",0,0,0,0,0]\",\"[1,\\\"Self\\\",0,0,0,0,0]\",\"bad\"]"),
                new LegacyFriendBackfill.LegacyRow(2L,
                        "[\"[1,\\\"One\\\",0,0,0,0,0]\",\"[99,\\\"Missing\\\",0,0,0,0,0]\"]"),
                new LegacyFriendBackfill.LegacyRow(3L, null)), Set.of(1L, 2L, 3L));
        check(analysis.sourceRows() == 3L, "all legacy player rows must be counted");
        check(analysis.sourceLinks() == 5L, "all legacy links must be counted");
        check(analysis.uniquePairs() == 1L, "one-sided and two-sided links must union to one pair");
        check(analysis.skippedSelfLinks() == 1L, "self link must be skipped");
        check(analysis.skippedMalformedLinks() == 1L, "malformed link must be skipped");
        check(analysis.skippedMissingPlayers() == 1L, "missing player link must be skipped");
        LegacyFriendBackfill.Analysis repeated = LegacyFriendBackfill.analyze(List.of(
                new LegacyFriendBackfill.LegacyRow(1L, "[\"[2]\"]"),
                new LegacyFriendBackfill.LegacyRow(2L, "[\"[1]\"]")), Set.of(1L, 2L));
        check(repeated.uniquePairs() == 1L && repeated.pairs().equals(analysis.pairs()),
                "repeated union analysis must remain idempotent");
    }

    private static void expectFailure(Runnable action) {
        boolean failed = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            failed = true;
        }
        check(failed, "invalid input must fail closed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class InMemoryRepository implements SocialRelationshipRepository {
        private final Set<Long> players = new HashSet<>();
        private final Map<SocialFriendPolicy.FriendshipPair, Instant> friendships = new HashMap<>();
        private final Map<Long, PendingFriendRequest> requests = new HashMap<>();
        private long nextRequestId = 1L;

        InMemoryRepository(long... playerIds) {
            for (long playerId : playerIds) {
                addPlayer(playerId);
            }
        }

        synchronized void addPlayer(long playerId) {
            players.add(playerId);
        }

        synchronized void addFriendship(long first, long second, Instant createdAt) {
            friendships.put(new SocialFriendPolicy().normalizePair(first, second), createdAt);
        }

        synchronized boolean hasFriendship(long first, long second) {
            return friendships.containsKey(new SocialFriendPolicy().normalizePair(first, second));
        }

        synchronized int pendingCount() {
            return requests.size();
        }

        @Override
        public synchronized <T> T inTransaction(TransactionWork<T> work) throws SQLException {
            return work.execute(new Transaction() {
                @Override
                public boolean lockExistingPlayers(SocialFriendPolicy.FriendshipPair pair) {
                    return players.contains(pair.lowPlayerId()) && players.contains(pair.highPlayerId());
                }

                @Override
                public boolean hasFriendship(SocialFriendPolicy.FriendshipPair pair) {
                    return friendships.containsKey(pair);
                }

                @Override
                public int friendshipCount(long playerId) {
                    int count = 0;
                    for (SocialFriendPolicy.FriendshipPair pair : friendships.keySet()) {
                        if (pair.lowPlayerId() == playerId || pair.highPlayerId() == playerId) {
                            count++;
                        }
                    }
                    return count;
                }

                @Override
                public void addFriendship(SocialFriendPolicy.FriendshipPair pair, Instant createdAt) {
                    friendships.put(pair, createdAt);
                }

                @Override
                public boolean removeFriendship(SocialFriendPolicy.FriendshipPair pair) {
                    return friendships.remove(pair) != null;
                }

                @Override
                public PendingFriendRequest findPendingByPairForUpdate(SocialFriendPolicy.FriendshipPair pair) {
                    return requests.values().stream().filter(request -> request.pair().equals(pair)).findFirst().orElse(null);
                }

                @Override
                public PendingFriendRequest findPendingById(long requestId) {
                    return requests.get(requestId);
                }

                @Override
                public PendingFriendRequest findPendingByIdForUpdate(long requestId) {
                    return requests.get(requestId);
                }

                @Override
                public long insertPending(SocialFriendPolicy.FriendshipPair pair, long senderId,
                        long receiverId, Instant createdAt, Instant expiresAt) {
                    long id = nextRequestId++;
                    requests.put(id, new PendingFriendRequest(id, pair, senderId, receiverId, createdAt, expiresAt));
                    return id;
                }

                @Override
                public boolean deletePending(long requestId) {
                    return requests.remove(requestId) != null;
                }

                @Override
                public int deleteExpiredForPair(SocialFriendPolicy.FriendshipPair pair, Instant now) {
                    return deleteWhere(request -> request.pair().equals(pair) && !request.expiresAt().isAfter(now));
                }

                @Override
                public int deleteExpired(Instant now) {
                    return deleteWhere(request -> !request.expiresAt().isAfter(now));
                }

                private int deleteWhere(java.util.function.Predicate<PendingFriendRequest> predicate) {
                    List<Long> ids = new ArrayList<>();
                    for (PendingFriendRequest request : requests.values()) {
                        if (predicate.test(request)) {
                            ids.add(request.id());
                        }
                    }
                    ids.forEach(requests::remove);
                    return ids.size();
                }
            });
        }
    }
}
