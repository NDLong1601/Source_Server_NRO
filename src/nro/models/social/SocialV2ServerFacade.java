package nro.models.social;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import nro.models.network.Message;
import nro.models.player.Player;
import nro.models.server.Client;
import nro.models.services.Service;
import nro.models.utils.Logger;
import nro.models.utils.Util;

/**
 * Social V2 protocol facade. It owns Player/Message integration while keeping
 * persistence and policy independently testable.
 */
public final class SocialV2ServerFacade {

    private static final SocialV2ServerFacade INSTANCE = createDefault();

    private final SocialRelationshipService relationships;
    private final SocialDirectoryService directory;
    private final SocialV2FeatureFlags flags;
    private final SocialActionRateLimiter rateLimiter;
    private final SocialFriendPolicy policy;
    private final SocialPresenceService presence;
    private final SocialProfileService profiles;
    private final SocialChatRateLimiter chatRateLimiter;
    private final SocialLocationCooldown locationCooldown;
    /** Serializes relationship changes against realtime chat/location delivery and logout unpublish. */
    private final Object interactionLock = new Object();

    private static SocialV2ServerFacade createDefault() {
        SocialFriendPolicy defaultPolicy = new SocialFriendPolicy();
        return new SocialV2ServerFacade(
                new SocialRelationshipService(new JdbcSocialRelationshipRepository(), defaultPolicy),
                new SocialDirectoryService(new JdbcSocialDirectoryRepository(),
                        playerId -> Client.gI().getPlayer(playerId) != null),
                SocialV2FeatureFlags.gI(), new SocialActionRateLimiter(), defaultPolicy,
                SocialPresenceService.gI(), new SocialProfileService(new JdbcSocialProfileRepository()),
                new SocialChatRateLimiter(), new SocialLocationCooldown());
    }

    public static SocialV2ServerFacade gI() {
        return INSTANCE;
    }

    SocialV2ServerFacade(SocialRelationshipService relationships, SocialDirectoryService directory,
            SocialV2FeatureFlags flags) {
        this(relationships, directory, flags, new SocialActionRateLimiter());
    }

    SocialV2ServerFacade(SocialRelationshipService relationships, SocialDirectoryService directory,
            SocialV2FeatureFlags flags, SocialActionRateLimiter rateLimiter) {
        this(relationships, directory, flags, rateLimiter, new SocialFriendPolicy(), SocialPresenceService.gI(),
                new SocialProfileService(new JdbcSocialProfileRepository()), new SocialChatRateLimiter(),
                new SocialLocationCooldown());
    }

    SocialV2ServerFacade(SocialRelationshipService relationships, SocialDirectoryService directory,
            SocialV2FeatureFlags flags, SocialActionRateLimiter rateLimiter, SocialFriendPolicy policy,
            SocialPresenceService presence, SocialProfileService profiles, SocialChatRateLimiter chatRateLimiter,
            SocialLocationCooldown locationCooldown) {
        if (relationships == null || directory == null || flags == null || rateLimiter == null) {
            throw new IllegalArgumentException("Social v2 dependencies are required");
        }
        if (policy == null || presence == null || profiles == null || chatRateLimiter == null
                || locationCooldown == null) {
            throw new IllegalArgumentException("Social v2 realtime dependencies are required");
        }
        this.relationships = relationships;
        this.directory = directory;
        this.flags = flags;
        this.rateLimiter = rateLimiter;
        this.policy = policy;
        this.presence = presence;
        this.profiles = profiles;
        this.chatRateLimiter = chatRateLimiter;
        this.locationCooldown = locationCooldown;
    }

    public boolean isEnabledFor(Player player) {
        return player != null && player.getSession() != null
                && flags.allowsClientVersion(player.getSession().version);
    }

    /** Called only after login made the Player, session and map publication complete. */
    public void onPlayerPublished(Player player) {
        if (player == null || !flags.isEnabled()) {
            return;
        }
        try {
            synchronized (interactionLock) {
                presence.publish(player);
            }
        } catch (SQLException databaseError) {
            Logger.logException(SocialV2ServerFacade.class, databaseError);
        }
    }

    /**
     * Called before session detachment. Acquiring the same lock as chat/location
     * makes every later delivery observe this player as offline.
     */
    public void onPlayerUnpublishing(Player player) {
        if (player == null || !flags.isEnabled()) {
            return;
        }
        synchronized (interactionLock) {
            presence.unpublish(player);
        }
    }

    /** Enforces the v2 private-chat policy while retaining legacy -72/92 wire order. */
    public void handlePrivateChat(Player player, Message request) {
        if (player == null || request == null) {
            return;
        }
        try {
            int targetPlayerId = request.reader().readInt();
            String submittedText = request.reader().readUTF();
            requireNoTrailingRequestData(request);
            handlePrivateChat(player, targetPlayerId, submittedText, Instant.now());
        } catch (IOException | IllegalArgumentException malformed) {
            notifySuccess(player, "Tin nhắn không hợp lệ");
        }
    }

    void handlePrivateChat(Player player, int targetPlayerId, String submittedText, Instant now) {
        if (!isEnabledFor(player) || now == null) {
            return;
        }
        SocialFriendPolicy.Authorization authorization = SocialFriendPolicy.Authorization.INVALID_TEXT;
        try {
            if (player.id <= 0L || targetPlayerId <= 0 || targetPlayerId == player.id) {
                authorization = SocialFriendPolicy.Authorization.NOT_FRIENDS;
            } else {
                String approvedText = policy.normalizeChatText(submittedText);
                // Consume a token before JDBC friendship lookup so valid spam to arbitrary
                // targets cannot turn this authorization boundary into an unbounded query path.
                if (!chatRateLimiter.tryConsume(player.id, now)) {
                    authorization = SocialFriendPolicy.Authorization.RATE_LIMITED;
                } else {
                    synchronized (interactionLock) {
                        boolean mutualFriends = relationships.areFriends(player.id, targetPlayerId);
                        boolean targetOnline = presence.isOnline(targetPlayerId);
                        authorization = policy.authorizeChat(mutualFriends, targetOnline, approvedText);
                        if (authorization == SocialFriendPolicy.Authorization.ALLOWED) {
                            boolean[] echoed = { false };
                            boolean targetStillOnline = presence.withOnlineTarget(player, targetPlayerId,
                                    (sender, target) -> echoed[0] = Service.gI()
                                            .tryChatPrivate(sender, target, approvedText));
                            if (!targetStillOnline) {
                                authorization = SocialFriendPolicy.Authorization.OFFLINE;
                            } else if (!echoed[0]) {
                                authorization = SocialFriendPolicy.Authorization.INVALID_TEXT;
                            }
                        }
                    }
                }
            }
        } catch (Exception unexpected) {
            // Never log text or targets here; a failed delivery is indistinguishable from offline to the client.
            authorization = SocialFriendPolicy.Authorization.OFFLINE;
        }
        if (authorization != SocialFriendPolicy.Authorization.ALLOWED) {
            notifyChatRejection(player, targetPlayerId, authorization);
        }
    }

    /** Handles only actions 3..12 after FriendAndEnemyService consumed their action byte. */
    public void handleV2Action(Player player, int action, Message request) {
        if (player == null || request == null) {
            return;
        }
        if (!flags.isEnabled()) {
            sendError(player, action, SocialV2Protocol.ErrorCode.FEATURE_DISABLED);
            return;
        }
        if (!isEnabledFor(player)) {
            sendError(player, action, SocialV2Protocol.ErrorCode.CLIENT_TOO_OLD);
            return;
        }
        if (!SocialV2Protocol.isV2Action(action)) {
            sendError(player, action, SocialV2Protocol.ErrorCode.UNSUPPORTED_ACTION);
            return;
        }
        try {
            switch (action) {
                case SocialV2Protocol.SEARCH -> {
                    int requestToken = request.reader().readInt();
                    int cursor = request.reader().readInt();
                    String query = request.reader().readUTF();
                    requireNoTrailingRequestData(request);
                    if (!rateLimiter.allowSearch(player.id, Instant.now())) {
                        sendRateLimited(player, SocialV2Protocol.SEARCH);
                        return;
                    }
                    sendSearchPage(player, directory.search(player.id, requestToken, cursor, query));
                }
                case SocialV2Protocol.SEND_REQUEST -> {
                    int targetPlayerId = readTargetPlayerId(request);
                    if (!rateLimiter.allowFriendRequest(player.id, Instant.now())) {
                        sendRateLimited(player, SocialV2Protocol.SEND_REQUEST);
                        return;
                    }
                    sendRequest(player, targetPlayerId, null);
                }
                case SocialV2Protocol.INBOX -> {
                    int requestToken = request.reader().readInt();
                    int cursor = request.reader().readInt();
                    requireNoTrailingRequestData(request);
                    sendInboxPage(player, directory.inbox(player.id, requestToken, cursor, Instant.now()));
                }
                case SocialV2Protocol.ACCEPT_REQUEST -> acceptRequest(player, readRequestId(request));
                case SocialV2Protocol.REJECT_REQUEST -> rejectRequest(player, readRequestId(request));
                case SocialV2Protocol.PROFILE -> sendProfile(player, readTargetPlayerId(request));
                case SocialV2Protocol.SHARE_LOCATION -> shareLocation(player, readTargetPlayerId(request));
                default -> sendError(player, action, SocialV2Protocol.ErrorCode.UNSUPPORTED_ACTION);
            }
        } catch (IllegalArgumentException | IOException malformed) {
            sendError(player, action, SocialV2Protocol.ErrorCode.MALFORMED);
        } catch (SQLException databaseError) {
            Logger.logException(SocialV2ServerFacade.class, databaseError);
            Service.gI().sendThongBao(player, "Không thể tải dữ liệu bạn bè, vui lòng thử lại sau");
        }
    }

    /** Writes the action-0 legacy prefix followed by the gated v2 capability tail. */
    public void sendFriendList(Player player) {
        if (!isEnabledFor(player)) {
            return;
        }
        try {
            SocialDirectoryService.FriendList snapshot = directory.friendList(player.id, Instant.now());
            Message response = new Message(SocialV2Protocol.COMMAND_SOCIAL);
            response.writer().writeByte(SocialV2Protocol.OPEN_LIST);
            response.writer().writeByte(snapshot.friends().size());
            for (SocialDirectoryRepository.PlayerSummary friend : snapshot.friends()) {
                writeLegacyFriendPrefixEntry(response, friend);
            }
            response.writer().writeByte(SocialV2Protocol.PROTOCOL_VERSION);
            response.writer().writeInt(SocialV2Protocol.CAPABILITY_SOCIAL_V2);
            response.writer().writeByte(SocialV2Protocol.MAX_FRIENDS);
            response.writer().writeByte(snapshot.onlineFriendCount());
            response.writer().writeShort(Math.min(0xFFFF, snapshot.pendingRequestCount()));
            deliver(player, response);
        } catch (SQLException databaseError) {
            Logger.logException(SocialV2ServerFacade.class, databaseError);
            Service.gI().sendThongBao(player, "Không thể tải danh sách bạn bè, vui lòng thử lại sau");
        } catch (IOException | IllegalArgumentException packetError) {
            sendError(player, SocialV2Protocol.OPEN_LIST, SocialV2Protocol.ErrorCode.PACKET_TOO_LARGE);
        }
    }

    /** Keeps action 2's legacy request/acknowledgement shape while using normalized persistence for v2. */
    public void removeFriend(Player player, int friendId) {
        if (!isEnabledFor(player)) {
            sendError(player, SocialV2Protocol.REMOVE_FRIEND, flags.isEnabled()
                    ? SocialV2Protocol.ErrorCode.CLIENT_TOO_OLD : SocialV2Protocol.ErrorCode.FEATURE_DISABLED);
            return;
        }
        SocialRelationshipService.Result result;
        synchronized (interactionLock) {
            result = relationships.removeFriendship(player.id, friendId);
            if (result.status() == SocialRelationshipService.Status.FRIENDSHIP_REMOVED) {
                presence.unlinkFriendship(player.id, result.affectedPlayerId());
            }
        }
        if (result.status() != SocialRelationshipService.Status.FRIENDSHIP_REMOVED) {
            sendRelationshipError(player, SocialV2Protocol.REMOVE_FRIEND, result, friendId);
            return;
        }
        try {
            Message response = new Message(SocialV2Protocol.COMMAND_SOCIAL);
            response.writer().writeByte(SocialV2Protocol.REMOVE_FRIEND);
            response.writer().writeInt(friendId);
            deliver(player, response);
            notifySuccess(player, "Đã xóa bạn bè");
            pushFriendListForOnline(result.affectedPlayerId());
        } catch (IOException | IllegalArgumentException packetError) {
            sendError(player, SocialV2Protocol.REMOVE_FRIEND, SocialV2Protocol.ErrorCode.PACKET_TOO_LARGE);
        }
    }

    /** Bridges the confirmed legacy player-menu action into the normalized request state machine. */
    public void requestFriendFromLegacyFlow(Player player, int targetPlayerId) {
        if (!isEnabledFor(player)) {
            return;
        }
        if (!rateLimiter.allowFriendRequest(player.id, Instant.now())) {
            sendRateLimited(player, SocialV2Protocol.SEND_REQUEST);
            return;
        }
        sendRequest(player, targetPlayerId, LegacyPendingTarget.from(Client.gI().getPlayer(targetPlayerId)));
    }

    private void sendRequest(Player player, int targetPlayerId, LegacyPendingTarget legacyPendingTarget) {
        SocialRelationshipService.Result result;
        synchronized (interactionLock) {
            result = relationships.sendRequest(player.id, targetPlayerId, Instant.now());
            if (result.status() == SocialRelationshipService.Status.AUTO_ACCEPTED) {
                presence.linkFriendship(player.id, result.affectedPlayerId());
            }
        }
        if (result.status() == SocialRelationshipService.Status.ALREADY_PENDING
                || result.status() == SocialRelationshipService.Status.ALREADY_FRIENDS) {
            sendError(player, SocialV2Protocol.SEND_REQUEST, SocialV2Protocol.ErrorCode.DUPLICATE);
            notifySuccess(player, result.status() == SocialRelationshipService.Status.ALREADY_PENDING
                    ? "Lời mời kết bạn đang chờ phản hồi" : "Đã là bạn bè");
            return;
        }
        if (result.status() == SocialRelationshipService.Status.REQUEST_SENT
                || result.status() == SocialRelationshipService.Status.AUTO_ACCEPTED) {
            if (result.status() == SocialRelationshipService.Status.REQUEST_SENT && legacyPendingTarget != null) {
                sendRequestSuccessWithLegacyPendingTarget(player, legacyPendingTarget);
            } else {
                sendSuccess(player, SocialV2Protocol.SEND_REQUEST);
            }
            notifySuccess(player, switch (result.status()) {
                case REQUEST_SENT -> "Đã gửi lời mời kết bạn";
                case AUTO_ACCEPTED -> "Đã trở thành bạn bè";
                default -> throw new IllegalStateException("Unexpected successful friend-request status");
            });
            if (result.status() == SocialRelationshipService.Status.REQUEST_SENT) {
                Player target = Client.gI().getPlayer(targetPlayerId);
                if (target != null) {
                    sendPendingCount(target);
                }
            } else if (result.status() == SocialRelationshipService.Status.AUTO_ACCEPTED) {
                // The reverse request committed a friendship; refresh both affected online v2 clients.
                sendFriendList(player);
                pushFriendListForOnline(result.affectedPlayerId());
            }
            return;
        }
        sendRelationshipError(player, SocialV2Protocol.SEND_REQUEST, result, targetPlayerId);
    }

    private void acceptRequest(Player player, long requestId) {
        SocialRelationshipService.Result result;
        synchronized (interactionLock) {
            result = relationships.acceptRequest(player.id, requestId, Instant.now());
            if (result.status() == SocialRelationshipService.Status.REQUEST_ACCEPTED) {
                presence.linkFriendship(player.id, result.affectedPlayerId());
            }
        }
        if (result.status() == SocialRelationshipService.Status.REQUEST_ACCEPTED
                || result.status() == SocialRelationshipService.Status.ALREADY_FRIENDS) {
            sendSuccess(player, SocialV2Protocol.ACCEPT_REQUEST);
            sendPendingCount(player);
            notifySuccess(player, result.status() == SocialRelationshipService.Status.REQUEST_ACCEPTED
                    ? "Đã chấp nhận lời mời kết bạn" : "Đã là bạn bè");
            if (result.status() == SocialRelationshipService.Status.REQUEST_ACCEPTED) {
                sendFriendList(player);
                pushFriendListForOnline(result.affectedPlayerId());
            }
            return;
        }
        sendRelationshipError(player, SocialV2Protocol.ACCEPT_REQUEST, result, -1L);
    }

    private void rejectRequest(Player player, long requestId) {
        SocialRelationshipService.Result result = relationships.rejectRequest(player.id, requestId);
        if (result.status() == SocialRelationshipService.Status.REQUEST_REJECTED) {
            sendSuccess(player, SocialV2Protocol.REJECT_REQUEST);
            sendPendingCount(player);
            notifySuccess(player, "Đã từ chối lời mời kết bạn");
            return;
        }
        sendRelationshipError(player, SocialV2Protocol.REJECT_REQUEST, result, -1L);
    }

    private void sendProfile(Player player, int targetPlayerId) {
        if (targetPlayerId <= 0) {
            sendError(player, SocialV2Protocol.PROFILE, SocialV2Protocol.ErrorCode.MALFORMED);
            return;
        }
        if (targetPlayerId == player.id) {
            sendError(player, SocialV2Protocol.PROFILE, SocialV2Protocol.ErrorCode.SELF_TARGET);
            return;
        }
        try {
            SocialProfileService.Profile profile;
            synchronized (interactionLock) {
                if (!relationships.areFriends(player.id, targetPlayerId)) {
                    sendError(player, SocialV2Protocol.PROFILE, SocialV2Protocol.ErrorCode.NOT_FRIENDS);
                    return;
                }
                Player online = presence.onlinePlayer(targetPlayerId);
                profile = online != null ? profiles.fromOnline(online)
                        : profiles.loadOffline(targetPlayerId, Instant.now());
            }
            if (profile == null) {
                sendError(player, SocialV2Protocol.PROFILE, SocialV2Protocol.ErrorCode.NOT_FOUND);
                return;
            }
            Message response = new Message(SocialV2Protocol.COMMAND_SOCIAL);
            response.writer().writeByte(SocialV2Protocol.PROFILE);
            response.writer().writeByte(SocialV2Protocol.RESULT_OK);
            response.writer().writeInt(asWirePlayerId(profile.playerId()));
            response.writer().writeShort(profile.head());
            response.writer().writeUTF(profile.name());
            response.writer().writeUTF(profile.clanName());
            response.writer().writeUTF(profile.activityLabel());
            response.writer().writeLong(profile.rawPower());
            response.writer().writeUTF(profile.formattedPower());
            response.writer().writeBoolean(profile.online());
            deliver(player, response);
        } catch (SQLException databaseError) {
            Logger.logException(SocialV2ServerFacade.class, databaseError);
            sendError(player, SocialV2Protocol.PROFILE, SocialV2Protocol.ErrorCode.NOT_FOUND);
        } catch (IOException | IllegalArgumentException packetError) {
            sendError(player, SocialV2Protocol.PROFILE, SocialV2Protocol.ErrorCode.PACKET_TOO_LARGE);
        }
    }

    private void shareLocation(Player player, int targetPlayerId) {
        if (targetPlayerId <= 0) {
            sendError(player, SocialV2Protocol.SHARE_LOCATION, SocialV2Protocol.ErrorCode.MALFORMED);
            return;
        }
        if (targetPlayerId == player.id) {
            sendError(player, SocialV2Protocol.SHARE_LOCATION, SocialV2Protocol.ErrorCode.SELF_TARGET);
            return;
        }
        SocialFriendPolicy.Authorization authorization;
        Instant now = Instant.now();
        try {
            synchronized (interactionLock) {
                boolean mutualFriends = relationships.areFriends(player.id, targetPlayerId);
                boolean targetOnline = presence.isOnline(targetPlayerId);
                authorization = policy.authorizeLocation(mutualFriends, targetOnline,
                        locationCooldown.lastSharedAt(player.id, now), now);
                if (authorization == SocialFriendPolicy.Authorization.ALLOWED) {
                    if (!presence.withOnlineTarget(player, targetPlayerId, (sender, target) -> {
                        LocationSnapshot location = LocationSnapshot.from(sender);
                        sendLocationEvent(sender, sender.id, location);
                        sendLocationEvent(target, sender.id, location);
                    })) {
                        authorization = SocialFriendPolicy.Authorization.OFFLINE;
                    } else {
                        locationCooldown.markDelivered(player.id, now);
                    }
                }
            }
        } catch (Exception unavailable) {
            authorization = SocialFriendPolicy.Authorization.OFFLINE;
        }
        if (authorization == SocialFriendPolicy.Authorization.ALLOWED) {
            sendSuccess(player, SocialV2Protocol.SHARE_LOCATION);
        } else {
            sendLocationRejection(player, authorization);
        }
    }

    private static void sendLocationEvent(Player recipient, long senderId, LocationSnapshot location)
            throws IOException {
        Message event = new Message(SocialV2Protocol.COMMAND_SOCIAL);
        event.writer().writeByte(SocialV2Protocol.LOCATION_EVENT);
        event.writer().writeInt(asWirePlayerId(senderId));
        event.writer().writeShort(location.mapId());
        event.writer().writeShort(location.zoneId());
        event.writer().writeShort(location.x());
        event.writer().writeShort(location.y());
        deliver(recipient, event);
    }

    private void sendSearchPage(Player player,
            SocialDirectoryService.Page<SocialDirectoryRepository.SearchEntry> page) throws IOException {
        Message response = new Message(SocialV2Protocol.COMMAND_SOCIAL);
        response.writer().writeByte(SocialV2Protocol.SEARCH);
        response.writer().writeByte(SocialV2Protocol.RESULT_OK);
        response.writer().writeInt(page.requestToken());
        response.writer().writeInt(page.nextCursor());
        response.writer().writeBoolean(page.hasMore());
        response.writer().writeByte(page.entries().size());
        for (SocialDirectoryRepository.SearchEntry entry : page.entries()) {
            response.writer().writeInt(asWirePlayerId(entry.player().playerId()));
            response.writer().writeShort(entry.player().head());
            response.writer().writeUTF(entry.player().name());
            response.writer().writeByte(relationshipWireValue(entry.relationship()));
        }
        try {
            deliver(player, response);
        } catch (IllegalArgumentException tooLarge) {
            sendError(player, SocialV2Protocol.SEARCH, SocialV2Protocol.ErrorCode.PACKET_TOO_LARGE);
        }
    }

    private void sendInboxPage(Player player,
            SocialDirectoryService.Page<SocialDirectoryRepository.InboxEntry> page) throws IOException {
        Message response = new Message(SocialV2Protocol.COMMAND_SOCIAL);
        response.writer().writeByte(SocialV2Protocol.INBOX);
        response.writer().writeByte(SocialV2Protocol.RESULT_OK);
        response.writer().writeInt(page.requestToken());
        response.writer().writeInt(page.nextCursor());
        response.writer().writeBoolean(page.hasMore());
        response.writer().writeByte(page.entries().size());
        for (SocialDirectoryRepository.InboxEntry entry : page.entries()) {
            response.writer().writeLong(entry.requestId());
            response.writer().writeInt(asWirePlayerId(entry.sender().playerId()));
            response.writer().writeShort(entry.sender().head());
            response.writer().writeUTF(entry.sender().name());
            response.writer().writeLong(entry.expiresAt().toEpochMilli());
        }
        try {
            deliver(player, response);
        } catch (IllegalArgumentException tooLarge) {
            sendError(player, SocialV2Protocol.INBOX, SocialV2Protocol.ErrorCode.PACKET_TOO_LARGE);
        }
    }

    private void sendPendingCount(Player player) {
        if (!isEnabledFor(player)) {
            return;
        }
        try {
            int pending = directory.friendList(player.id, Instant.now()).pendingRequestCount();
            Message response = new Message(SocialV2Protocol.COMMAND_SOCIAL);
            response.writer().writeByte(SocialV2Protocol.PENDING_COUNT);
            response.writer().writeShort(Math.min(0xFFFF, pending));
            deliver(player, response);
        } catch (SQLException databaseError) {
            Logger.logException(SocialV2ServerFacade.class, databaseError);
        } catch (IOException | IllegalArgumentException packetError) {
            sendError(player, SocialV2Protocol.PENDING_COUNT, SocialV2Protocol.ErrorCode.PACKET_TOO_LARGE);
        }
    }

    /** Uses the already-defined action-0 snapshot as a compatibility-safe state refresh. */
    private void pushFriendListForOnline(long playerId) {
        if (playerId <= 0L) {
            return;
        }
        Player peer = Client.gI().getPlayer(playerId);
        if (peer != null && isEnabledFor(peer)) {
            sendFriendList(peer);
        }
    }

    private void sendRelationshipError(Player player, int action, SocialRelationshipService.Result result,
            long targetId) {
        SocialV2Protocol.ErrorCode code = switch (result.status()) {
            case INVALID_TARGET -> targetId == player.id ? SocialV2Protocol.ErrorCode.SELF_TARGET
                    : SocialV2Protocol.ErrorCode.MALFORMED;
            case TARGET_NOT_FOUND, REQUEST_NOT_FOUND -> SocialV2Protocol.ErrorCode.NOT_FOUND;
            case NOT_REQUEST_RECIPIENT, NOT_FRIENDS -> SocialV2Protocol.ErrorCode.NOT_FRIENDS;
            case REQUEST_EXPIRED -> SocialV2Protocol.ErrorCode.EXPIRED;
            case FRIEND_LIMIT_REACHED, TARGET_FRIEND_LIMIT_REACHED -> SocialV2Protocol.ErrorCode.FRIEND_LIMIT;
            case DATABASE_FAILURE -> SocialV2Protocol.ErrorCode.NOT_FOUND;
            default -> SocialV2Protocol.ErrorCode.MALFORMED;
        };
        sendError(player, action, code);
        notifySuccess(player, switch (code) {
            case FRIEND_LIMIT -> "Danh sách bạn bè đã đầy";
            case EXPIRED -> "Lời mời kết bạn đã hết hạn";
            case NOT_FOUND -> "Không tìm thấy dữ liệu bạn bè";
            case NOT_FRIENDS -> "Không có quyền thực hiện thao tác này";
            case SELF_TARGET -> "Không thể thực hiện với chính bạn";
            default -> "Yêu cầu bạn bè không hợp lệ";
        });
    }

    private void sendRateLimited(Player player, int action) {
        sendError(player, action, SocialV2Protocol.ErrorCode.RATE_LIMITED);
        notifySuccess(player, "Thao tác quá nhanh, vui lòng thử lại sau");
    }

    private void notifyChatRejection(Player player, int targetPlayerId,
            SocialFriendPolicy.Authorization authorization) {
        switch (authorization) {
            case OFFLINE -> {
                sendPresenceOffline(player, targetPlayerId);
                notifySuccess(player, "Bạn bè hiện đang offline");
            }
            case NOT_FRIENDS -> notifySuccess(player, "Bạn chỉ có thể nhắn tin cho bạn bè");
            case INVALID_TEXT -> notifySuccess(player, "Tin nhắn không hợp lệ");
            case RATE_LIMITED -> notifySuccess(player, "Bạn gửi tin nhắn quá nhanh, vui lòng thử lại sau");
            default -> notifySuccess(player, "Không thể gửi tin nhắn");
        }
    }

    private void sendLocationRejection(Player player, SocialFriendPolicy.Authorization authorization) {
        SocialV2Protocol.ErrorCode errorCode = switch (authorization) {
            case NOT_FRIENDS -> SocialV2Protocol.ErrorCode.NOT_FRIENDS;
            case OFFLINE -> SocialV2Protocol.ErrorCode.OFFLINE;
            case COOLDOWN -> SocialV2Protocol.ErrorCode.LOCATION_COOLDOWN;
            default -> SocialV2Protocol.ErrorCode.MALFORMED;
        };
        sendError(player, SocialV2Protocol.SHARE_LOCATION, errorCode);
        notifySuccess(player, switch (authorization) {
            case NOT_FRIENDS -> "Bạn chỉ có thể chia sẻ vị trí cho bạn bè";
            case OFFLINE -> "Bạn bè hiện đang offline";
            case COOLDOWN -> "Vui lòng chờ trước khi chia sẻ vị trí tiếp theo";
            default -> "Không thể chia sẻ vị trí";
        });
    }

    private void sendPresenceOffline(Player player, int targetPlayerId) {
        if (player == null || targetPlayerId <= 0 || !isEnabledFor(player)) {
            return;
        }
        try {
            Message event = new Message(SocialV2Protocol.COMMAND_SOCIAL);
            event.writer().writeByte(SocialV2Protocol.PRESENCE);
            event.writer().writeInt(targetPlayerId);
            event.writer().writeBoolean(false);
            deliver(player, event);
        } catch (IOException | IllegalArgumentException ignored) {
            // A fixed-size best-effort presence correction must not affect chat handling.
        }
    }

    private void sendSuccess(Player player, int action) {
        try {
            Message response = new Message(SocialV2Protocol.COMMAND_SOCIAL);
            response.writer().writeByte(action);
            response.writer().writeByte(SocialV2Protocol.RESULT_OK);
            deliver(player, response);
        } catch (IOException | IllegalArgumentException packetError) {
            sendError(player, action, SocialV2Protocol.ErrorCode.PACKET_TOO_LARGE);
        }
    }

    /**
     * The legacy player-menu flow has no client-side search row to mark as pending.
     * Its additive target tail lets new clients render that pending row, while older
     * clients continue to consume the standard two-byte success envelope.
     */
    private void sendRequestSuccessWithLegacyPendingTarget(Player player, LegacyPendingTarget target) {
        try {
            Message response = new Message(SocialV2Protocol.COMMAND_SOCIAL);
            response.writer().writeByte(SocialV2Protocol.SEND_REQUEST);
            response.writer().writeByte(SocialV2Protocol.RESULT_OK);
            response.writer().writeInt(target.playerId());
            response.writer().writeShort(target.head());
            response.writer().writeUTF(target.name());
            deliver(player, response);
        } catch (IOException | IllegalArgumentException packetError) {
            sendError(player, SocialV2Protocol.SEND_REQUEST, SocialV2Protocol.ErrorCode.PACKET_TOO_LARGE);
        }
    }

    private void sendError(Player player, int action, SocialV2Protocol.ErrorCode errorCode) {
        if (player == null) {
            return;
        }
        try {
            Message response = new Message(SocialV2Protocol.COMMAND_SOCIAL);
            response.writer().writeByte(action);
            response.writer().writeByte(SocialV2Protocol.RESULT_ERROR);
            response.writer().writeByte(errorCode.wireValue());
            deliver(player, response);
        } catch (IOException | IllegalArgumentException ignored) {
            // The fixed-size error envelope cannot reasonably exceed the packet cap.
        }
    }

    private void writeLegacyFriendPrefixEntry(Message response, SocialDirectoryRepository.PlayerSummary friend)
            throws IOException {
        Player online = Client.gI().getPlayer(friend.playerId());
        response.writer().writeInt(asWirePlayerId(friend.playerId()));
        response.writer().writeShort(online != null ? online.getHead() : friend.head());
        response.writer().writeShort(-1);
        response.writer().writeShort(online != null ? online.getBody() : -1);
        response.writer().writeShort(online != null ? online.getLeg() : -1);
        response.writer().writeByte(online != null ? online.getFlagBag() : 0);
        response.writer().writeUTF(online != null ? online.name : friend.name());
        response.writer().writeBoolean(online != null);
        response.writer().writeUTF(online != null ? Util.numberToMoney(online.nPoint.power) : "0");
    }

    private static void deliver(Player player, Message message) {
        try {
            SocialV2Protocol.requirePacketPayloadLength(message.getData().length);
            player.sendMessage(message);
        } finally {
            message.cleanup();
        }
    }

    private static int relationshipWireValue(SocialDirectoryRepository.Relationship relationship) {
        return switch (relationship) {
            case FRIEND -> 0;
            case PENDING -> 1;
            case CAN_ADD -> 2;
        };
    }

    private static int asWirePlayerId(long playerId) {
        if (playerId <= 0L || playerId > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Player id is outside the social-v2 i32 wire range");
        }
        return (int) playerId;
    }

    private static void notifySuccess(Player player, String message) {
        Service.gI().sendThongBao(player, message);
    }

    private static int readTargetPlayerId(Message request) throws IOException {
        int targetPlayerId = request.reader().readInt();
        requireNoTrailingRequestData(request);
        return targetPlayerId;
    }

    private static long readRequestId(Message request) throws IOException {
        long requestId = request.reader().readLong();
        requireNoTrailingRequestData(request);
        return requestId;
    }

    private static void requireNoTrailingRequestData(Message request) throws IOException {
        if (request.reader().available() != 0) {
            throw new IllegalArgumentException("Social-v2 request has trailing data");
        }
    }

    private record LocationSnapshot(short mapId, short zoneId, short x, short y) {
        private static LocationSnapshot from(Player player) {
            if (player == null || player.zone == null || player.zone.map == null || player.location == null) {
                throw new IllegalArgumentException("Player location is unavailable");
            }
            return new LocationSnapshot(asWireShort(player.zone.map.mapId, "map"),
                    asWireShort(player.zone.zoneId, "zone"), asWireShort(player.location.x, "x"),
                    asWireShort(player.location.y, "y"));
        }

        private static short asWireShort(int value, String field) {
            if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
                throw new IllegalArgumentException("Location " + field + " is outside i16 wire range");
            }
            return (short) value;
        }
    }

    private record LegacyPendingTarget(int playerId, short head, String name) {
        private static LegacyPendingTarget from(Player player) {
            if (player == null || player.id <= 0L || player.id > Integer.MAX_VALUE
                    || player.name == null || player.name.isBlank()
                    || !SocialV2Protocol.fitsModifiedUtf(player.name, SocialV2Protocol.SEARCH_MAX_CODE_POINTS)) {
                return null;
            }
            return new LegacyPendingTarget((int) player.id, player.getHead(), player.name);
        }
    }
}
