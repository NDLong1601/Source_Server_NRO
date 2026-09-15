package nro.models.social;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nro.models.network.Message;
import nro.models.player.Player;

/**
 * Online-only reverse presence index. It performs one narrow friendship query
 * at publication time and never scans all players or account records.
 */
public final class SocialPresenceService {

    private static final int MAX_REMOVED_FRIENDSHIP_TOMBSTONES = 10_000;

    @FunctionalInterface
    public interface FriendLookup {
        List<Long> findFriendIds(long playerId) throws SQLException;
    }

    @FunctionalInterface
    public interface PresenceDelivery {
        void deliver(Player recipient, long friendId, boolean online);
    }

    @FunctionalInterface
    public interface OnlineTargetAction {
        void deliver(Player sender, Player target) throws Exception;
    }

    private static final SocialPresenceService INSTANCE = new SocialPresenceService(
            playerId -> new JdbcSocialDirectoryRepository().friends(playerId).stream()
                    .map(SocialDirectoryRepository.PlayerSummary::playerId).toList(),
            SocialPresenceService::sendWireEvent);

    private final FriendLookup friendLookup;
    private final PresenceDelivery delivery;
    private final Object lock = new Object();
    private final Map<Long, Player> onlinePlayers = new HashMap<>();
    /** friend id -> online friend ids that currently receive that friend's presence. */
    private final Map<Long, Set<Long>> followersByFriendId = new HashMap<>();
    /** Bounded LRU tombstones guard recent publish results racing a committed friendship removal. */
    private final LinkedHashMap<FriendshipKey, Boolean> removedFriendships = new LinkedHashMap<>(16, 0.75F, true);

    public static SocialPresenceService gI() {
        return INSTANCE;
    }

    public SocialPresenceService(FriendLookup friendLookup, PresenceDelivery delivery) {
        if (friendLookup == null || delivery == null) {
            throw new IllegalArgumentException("Social presence collaborators are required");
        }
        this.friendLookup = friendLookup;
        this.delivery = delivery;
    }

    /** Publishes only after the login lifecycle made the Player visible and ready. */
    public void publish(Player player) throws SQLException {
        validatePlayer(player);
        List<Long> friendIds = friendLookup.findFriendIds(player.id);
        synchronized (lock) {
            Player previouslyPublished = onlinePlayers.get(player.id);
            if (previouslyPublished == player) {
                return;
            }
            if (previouslyPublished != null) {
                // A replacement session may publish before its duplicate-login
                // close callback runs. Remove the old identity first so its
                // eventual teardown cannot remove the replacement.
                unpublish(previouslyPublished);
            }
            onlinePlayers.put(player.id, player);
            if (friendIds == null) {
                return;
            }
            for (Long friendId : friendIds) {
                if (friendId == null || friendId <= 0L || friendId == player.id
                        || removedFriendships.get(FriendshipKey.of(player.id, friendId)) != null) {
                    continue;
                }
                Player friend = onlinePlayers.get(friendId);
                if (friend == null) {
                    continue;
                }
                followEachOther(player.id, friendId);
                delivery.deliver(friend, player.id, true);
            }
        }
    }

    /**
     * Removes the sender before delivering offline events. Callers must invoke
     * this at session teardown before the registry removal completes.
     */
    public void unpublish(Player player) {
        if (player == null || player.id <= 0L) {
            return;
        }
        synchronized (lock) {
            if (onlinePlayers.get(player.id) != player) {
                return;
            }
            onlinePlayers.remove(player.id);
            Set<Long> followers = followersByFriendId.remove(player.id);
            if (followers == null) {
                return;
            }
            for (Long followerId : new ArrayList<>(followers)) {
                Set<Long> watchedByFollower = followersByFriendId.get(followerId);
                if (watchedByFollower != null) {
                    watchedByFollower.remove(player.id);
                    if (watchedByFollower.isEmpty()) {
                        followersByFriendId.remove(followerId);
                    }
                }
                Player follower = onlinePlayers.get(followerId);
                if (follower != null) {
                    delivery.deliver(follower, player.id, false);
                }
            }
        }
    }

    public boolean isOnline(long playerId) {
        synchronized (lock) {
            return onlinePlayers.containsKey(playerId);
        }
    }

    public Player onlinePlayer(long playerId) {
        synchronized (lock) {
            return onlinePlayers.get(playerId);
        }
    }

    /** Atomically verifies both session publications around a server delivery. */
    public boolean withOnlineTarget(Player sender, long targetId, OnlineTargetAction action) throws Exception {
        validatePlayer(sender);
        if (targetId <= 0L || action == null) {
            return false;
        }
        synchronized (lock) {
            if (onlinePlayers.get(sender.id) != sender || !isSessionDeliverable(sender)) {
                return false;
            }
            Player target = onlinePlayers.get(targetId);
            if (target == null || !isSessionDeliverable(target)) {
                return false;
            }
            action.deliver(sender, target);
            return true;
        }
    }

    /** Called after a friendship commits so online users have a reverse edge for later logout. */
    public void linkFriendship(long firstPlayerId, long secondPlayerId) {
        FriendshipKey key = FriendshipKey.of(firstPlayerId, secondPlayerId);
        synchronized (lock) {
            removedFriendships.remove(key);
            Player first = onlinePlayers.get(firstPlayerId);
            Player second = onlinePlayers.get(secondPlayerId);
            if (first == null || second == null) {
                return;
            }
            followEachOther(firstPlayerId, secondPlayerId);
            delivery.deliver(first, secondPlayerId, true);
            delivery.deliver(second, firstPlayerId, true);
        }
    }

    /** Called after a friendship removal commits; it blocks stale publish results as well. */
    public void unlinkFriendship(long firstPlayerId, long secondPlayerId) {
        FriendshipKey key = FriendshipKey.of(firstPlayerId, secondPlayerId);
        synchronized (lock) {
            removedFriendships.put(key, Boolean.TRUE);
            while (removedFriendships.size() > MAX_REMOVED_FRIENDSHIP_TOMBSTONES) {
                java.util.Iterator<FriendshipKey> entries = removedFriendships.keySet().iterator();
                if (!entries.hasNext()) {
                    break;
                }
                entries.next();
                entries.remove();
            }
            unfollow(firstPlayerId, secondPlayerId);
            unfollow(secondPlayerId, firstPlayerId);
        }
    }

    /** Exposes only the bounded aggregate count for package-level diagnostics. */
    int removedFriendshipTombstoneCount() {
        synchronized (lock) {
            return removedFriendships.size();
        }
    }

    private void followEachOther(long firstPlayerId, long secondPlayerId) {
        followersByFriendId.computeIfAbsent(firstPlayerId, ignored -> new HashSet<>()).add(secondPlayerId);
        followersByFriendId.computeIfAbsent(secondPlayerId, ignored -> new HashSet<>()).add(firstPlayerId);
    }

    private void unfollow(long followerId, long friendId) {
        Set<Long> followers = followersByFriendId.get(friendId);
        if (followers == null) {
            return;
        }
        followers.remove(followerId);
        if (followers.isEmpty()) {
            followersByFriendId.remove(friendId);
        }
    }

    private static void validatePlayer(Player player) {
        if (player == null || player.id <= 0L) {
            throw new IllegalArgumentException("Published social player is invalid");
        }
    }

    private static boolean isSessionDeliverable(Player player) {
        return player != null && player.getSession() != null && player.getSession().isConnected()
                && !player.getSession().isClosed();
    }

    private static void sendWireEvent(Player recipient, long friendId, boolean online) {
        if (recipient == null || recipient.getSession() == null
                || !recipient.getSession().isConnected() || recipient.getSession().isClosed()
                || !SocialV2FeatureFlags.gI().allowsClientVersion(recipient.getSession().version)) {
            return;
        }
        try {
            Message response = new Message(SocialV2Protocol.COMMAND_SOCIAL);
            response.writer().writeByte(SocialV2Protocol.PRESENCE);
            response.writer().writeInt(asWirePlayerId(friendId));
            response.writer().writeBoolean(online);
            try {
                SocialV2Protocol.requirePacketPayloadLength(response.getData().length);
                recipient.sendMessage(response);
            } finally {
                response.cleanup();
            }
        } catch (IOException | IllegalArgumentException ignored) {
            // Presence is best-effort and its fixed-size event must never take down a session.
        }
    }

    private static int asWirePlayerId(long playerId) {
        if (playerId <= 0L || playerId > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Player id is outside the social-v2 i32 wire range");
        }
        return (int) playerId;
    }

    private record FriendshipKey(long lowPlayerId, long highPlayerId) {
        private static FriendshipKey of(long firstPlayerId, long secondPlayerId) {
            if (firstPlayerId <= 0L || secondPlayerId <= 0L || firstPlayerId == secondPlayerId) {
                throw new IllegalArgumentException("Friendship key requires distinct positive ids");
            }
            return firstPlayerId < secondPlayerId
                    ? new FriendshipKey(firstPlayerId, secondPlayerId)
                    : new FriendshipKey(secondPlayerId, firstPlayerId);
        }
    }
}
