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
 * Phase-3 protocol facade. It is the only social-v2 class with Player/Message
 * dependencies; persistence and policy stay independently testable.
 */
public final class SocialV2ServerFacade {

    private static final SocialV2ServerFacade INSTANCE = new SocialV2ServerFacade(
            new SocialRelationshipService(new JdbcSocialRelationshipRepository(), new SocialFriendPolicy()),
            new SocialDirectoryService(new JdbcSocialDirectoryRepository(),
                    playerId -> Client.gI().getPlayer(playerId) != null),
            SocialV2FeatureFlags.gI(), new SocialActionRateLimiter());

    private final SocialRelationshipService relationships;
    private final SocialDirectoryService directory;
    private final SocialV2FeatureFlags flags;
    private final SocialActionRateLimiter rateLimiter;

    public static SocialV2ServerFacade gI() {
        return INSTANCE;
    }

    SocialV2ServerFacade(SocialRelationshipService relationships, SocialDirectoryService directory,
            SocialV2FeatureFlags flags) {
        this(relationships, directory, flags, new SocialActionRateLimiter());
    }

    SocialV2ServerFacade(SocialRelationshipService relationships, SocialDirectoryService directory,
            SocialV2FeatureFlags flags, SocialActionRateLimiter rateLimiter) {
        if (relationships == null || directory == null || flags == null || rateLimiter == null) {
            throw new IllegalArgumentException("Social v2 dependencies are required");
        }
        this.relationships = relationships;
        this.directory = directory;
        this.flags = flags;
        this.rateLimiter = rateLimiter;
    }

    public boolean isEnabledFor(Player player) {
        return player != null && player.getSession() != null
                && flags.allowsClientVersion(player.getSession().version);
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
                    sendRequest(player, targetPlayerId);
                }
                case SocialV2Protocol.INBOX -> {
                    int requestToken = request.reader().readInt();
                    int cursor = request.reader().readInt();
                    requireNoTrailingRequestData(request);
                    sendInboxPage(player, directory.inbox(player.id, requestToken, cursor, Instant.now()));
                }
                case SocialV2Protocol.ACCEPT_REQUEST -> acceptRequest(player, readRequestId(request));
                case SocialV2Protocol.REJECT_REQUEST -> rejectRequest(player, readRequestId(request));
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
        SocialRelationshipService.Result result = relationships.removeFriendship(player.id, friendId);
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

    private void sendRequest(Player player, int targetPlayerId) {
        SocialRelationshipService.Result result = relationships.sendRequest(player.id, targetPlayerId, Instant.now());
        if (result.status() == SocialRelationshipService.Status.ALREADY_PENDING
                || result.status() == SocialRelationshipService.Status.ALREADY_FRIENDS) {
            sendError(player, SocialV2Protocol.SEND_REQUEST, SocialV2Protocol.ErrorCode.DUPLICATE);
            notifySuccess(player, result.status() == SocialRelationshipService.Status.ALREADY_PENDING
                    ? "Lời mời kết bạn đang chờ phản hồi" : "Đã là bạn bè");
            return;
        }
        if (result.status() == SocialRelationshipService.Status.REQUEST_SENT
                || result.status() == SocialRelationshipService.Status.AUTO_ACCEPTED) {
            sendSuccess(player, SocialV2Protocol.SEND_REQUEST);
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
        SocialRelationshipService.Result result = relationships.acceptRequest(player.id, requestId, Instant.now());
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
}
