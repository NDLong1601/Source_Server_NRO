package nro.models.clan;

import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import nro.models.network.Message;
import nro.models.player.Player;
import nro.models.server.GameRuntime;
import nro.models.services.Service;
import nro.models.utils.Logger;
import nro.models.utils.Util;

/** Read-only phase-5C leaderboard backed by live, materialized Clan Value. */
public final class ClanRankingService {

    public static final byte REQUEST_PAGE = 127;
    public static final byte RESPONSE_PAGE = 127;

    private static final ClanRankingService INSTANCE = new ClanRankingService();

    private final ClanRankingConfig config = ClanRankingConfig.load();
    private final AtomicLong boardVersions = new AtomicLong();
    private volatile BoardSnapshot board = BoardSnapshot.empty();

    private ClanRankingService() {
    }

    public static ClanRankingService gI() {
        return INSTANCE;
    }

    public void handleRequest(Player player, byte action, Message request) {
        if (action != REQUEST_PAGE || player == null) {
            return;
        }
        if (!ClanFeatureFlags.gI().isEnabled(ClanFeatureFlags.Feature.RANKING)) {
            notify(player, "Bảng xếp hạng bang đang tạm khóa.");
            return;
        }
        int pageNumber = 0;
        int pageSize = 0;
        try {
            DataInputStream reader = request == null ? null : request.reader();
            if (reader != null && reader.available() >= Short.BYTES) {
                pageNumber = reader.readUnsignedShort();
            }
            if (reader != null && reader.available() >= Byte.BYTES) {
                pageSize = reader.readUnsignedByte();
            }
        } catch (Exception error) {
            Logger.logException(ClanRankingService.class, error, "Yêu cầu trang xếp hạng bang không hợp lệ");
            notify(player, "Dữ liệu xếp hạng không hợp lệ.");
            return;
        }
        sendPage(player, pageNumber, pageSize);
    }

    public void sendPage(Player player, int rawPage, int rawPageSize) {
        if (player == null || !ClanFeatureFlags.gI().isEnabled(ClanFeatureFlags.Feature.RANKING)) {
            return;
        }
        BoardSnapshot snapshot = currentBoard();
        int requesterClanId = player.clan == null ? -1 : player.clan.id;
        ClanRankingPolicy.Page page = ClanRankingPolicy.page(snapshot.entries,
                requesterClanId, rawPage, rawPageSize, config);
        Message response = null;
        try {
            response = new Message(127);
            ClanRankingProtocol.write(response.writer(), page, snapshot.version, snapshot.generatedAt);
            if (response.getData().length > 65_535) {
                throw new IllegalStateException("Clan ranking payload exceeds 65535 bytes");
            }
            player.sendMessage(response);
        } catch (Exception error) {
            Logger.logException(ClanRankingService.class, error, "Không gửi được trang xếp hạng bang");
            notify(player, "Không tải được bảng xếp hạng bang.");
        } finally {
            if (response != null) {
                response.cleanup();
            }
        }
    }

    /** Compatibility view for existing clients through the generic leaderboard UI. */
    public void sendLegacyBoard(Player player) {
        if (player == null) {
            return;
        }
        if (!ClanFeatureFlags.gI().isEnabled(ClanFeatureFlags.Feature.RANKING)) {
            notify(player, "Bảng xếp hạng bang đang tạm khóa.");
            return;
        }
        BoardSnapshot snapshot = currentBoard();
        int requesterClanId = player.clan == null ? -1 : player.clan.id;
        ClanRankingPolicy.Page page = ClanRankingPolicy.page(snapshot.entries,
                requesterClanId, 0, config.legacyTopSize(), config);
        Message response = null;
        try {
            response = new Message(-96);
            response.writer().writeByte(0);
            response.writer().writeUTF("Xếp hạng Clan Value");
            response.writer().writeByte(page.entries().size());
            for (int index = 0; index < page.entries().size(); index++) {
                ClanRankingPolicy.Candidate clan = page.entries().get(index);
                response.writer().writeInt(index + 1);
                response.writer().writeInt(clan.leaderId());
                response.writer().writeShort(clan.leaderHead());
                if (player.getSession() != null && player.getSession().version > 214) {
                    response.writer().writeShort(-1);
                }
                response.writer().writeShort(clan.leaderBody());
                response.writer().writeShort(clan.leaderLeg());
                response.writer().writeUTF(clan.name());
                response.writer().writeUTF("[" + clan.shortName() + "] Cấp bang "
                        + clan.clanLevel() + " - Cây " + clan.treeLevel());
                response.writer().writeUTF("Clan Value: " + Util.formatNumber(clan.clanValue())
                        + "\nThành viên: " + clan.currentMembers() + "/" + clan.maxMembers());
            }
            player.sendMessage(response);
        } catch (Exception error) {
            Logger.logException(ClanRankingService.class, error, "Không gửi được bảng xếp hạng bang tương thích");
            notify(player, "Không tải được bảng xếp hạng bang.");
        } finally {
            if (response != null) {
                response.cleanup();
            }
        }
    }

    private BoardSnapshot currentBoard() {
        long now = System.currentTimeMillis();
        long valueRevision = ClanValueService.gI().revision();
        BoardSnapshot current = board;
        if (current.generatedAt > 0 && current.valueRevision == valueRevision
                && now - current.generatedAt < config.refreshMillis()) {
            return current;
        }
        synchronized (this) {
            current = board;
            now = System.currentTimeMillis();
            valueRevision = ClanValueService.gI().revision();
            if (current.generatedAt > 0 && current.valueRevision == valueRevision
                    && now - current.generatedAt < config.refreshMillis()) {
                return current;
            }
            ArrayList<ClanRankingPolicy.Candidate> candidates = new ArrayList<>();
            for (Clan clan : GameRuntime.gI().clans().snapshot()) {
                ClanRankingPolicy.Candidate candidate = capture(clan);
                if (candidate != null) {
                    candidates.add(candidate);
                }
            }
            List<ClanRankingPolicy.Candidate> entries = ClanRankingPolicy.sorted(candidates);
            board = new BoardSnapshot(boardVersions.incrementAndGet(), now,
                    ClanValueService.gI().revision(), entries);
            return board;
        }
    }

    private ClanRankingPolicy.Candidate capture(Clan clan) {
        if (clan == null || clan.id < 0) {
            return null;
        }
        ClanValueService.Snapshot value = ClanValueService.gI().snapshot(clan);
        ClanTreeService.ValueState tree = ClanTreeService.gI().valueState(clan.id);
        synchronized (clan) {
            ClanMember leader = clan.getLeader();
            return new ClanRankingPolicy.Candidate(clan.id, clan.name, clan.name2, clan.imgId,
                    clan.level, tree.level(), clan.members.size(), clan.maxMember,
                    value.score().totalValue(), value.version(), Integer.toUnsignedLong(clan.createTime),
                    leader.id, leader.head, leader.body, leader.leg);
        }
    }

    private static void notify(Player player, String text) {
        if (player != null) {
            Service.gI().sendThongBao(player, text);
        }
    }

    private record BoardSnapshot(long version, long generatedAt, long valueRevision,
            List<ClanRankingPolicy.Candidate> entries) {

        static BoardSnapshot empty() {
            return new BoardSnapshot(0L, 0L, -1L, List.of());
        }

        BoardSnapshot {
            entries = entries == null ? List.of() : List.copyOf(entries);
        }
    }
}
