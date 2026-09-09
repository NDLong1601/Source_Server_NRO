package nro.models.clan;

import nro.models.network.Message;
import nro.models.player.Player;
import nro.models.services.Service;
import nro.models.utils.Logger;
import nro.models.utils.Util;

/** Server-authoritative, read-only phase-5D cosmetic snapshot service. */
public final class ClanAppearanceService {

    public static final byte REQUEST_VIEW = ClanAppearanceProtocol.ACTION;
    public static final byte RESPONSE_SNAPSHOT = ClanAppearanceProtocol.ACTION;

    private static final ClanAppearanceService INSTANCE = new ClanAppearanceService();

    private final ClanAppearanceConfig config = ClanAppearanceConfig.load();

    private ClanAppearanceService() {
    }

    public static ClanAppearanceService gI() {
        return INSTANCE;
    }

    public String resourceName(int treeLevel) {
        return config.resourceName(treeLevel);
    }

    public AppearanceView snapshot(Clan clan) {
        if (clan == null || clan.id < 0) {
            return disabledView(null, 1);
        }
        ClanTreeService.ValueState tree = ClanTreeService.gI().valueState(clan.id);
        ClanValueService.Snapshot value = ClanValueService.gI().snapshot(clan);
        return snapshot(clan, tree.level(), value);
    }

    public AppearanceView snapshot(Clan clan, int treeLevel, ClanValueService.Snapshot value) {
        if (clan == null || clan.id < 0) {
            return disabledView(clan, treeLevel);
        }
        boolean enabled = ClanFeatureFlags.gI().isEnabled(ClanFeatureFlags.Feature.APPEARANCE);
        long clanValue = value == null ? 0L : value.score().totalValue();
        long clanValueVersion = value == null ? 0L : value.version();
        ClanAppearancePolicy.Appearance appearance = enabled
                ? ClanAppearancePolicy.resolve(config, clan.level, treeLevel,
                        clanValue, clanValueVersion)
                : ClanAppearancePolicy.resolve(config, 0, treeLevel, 0L, 0L);
        return new AppearanceView(enabled, appearance);
    }

    public void handleRequest(Player player, byte action) {
        if (action != REQUEST_VIEW || player == null) {
            return;
        }
        if (!ClanFeatureFlags.gI().isEnabled(ClanFeatureFlags.Feature.APPEARANCE)) {
            notify(player, "Ngoại hình Cây bang đang tạm khóa.");
            return;
        }
        if (player.clan == null) {
            notify(player, "Bạn chưa có bang hội.");
            return;
        }
        sendSnapshot(player);
    }

    public void sendSnapshot(Player player) {
        if (player == null || player.clan == null) {
            return;
        }
        AppearanceView view = snapshot(player.clan);
        Message response = null;
        try {
            response = new Message(127);
            ClanAppearanceProtocol.write(response.writer(), player.clan.id,
                    view.enabled(), view.appearance());
            if (response.getData().length > 65_535) {
                throw new IllegalStateException("Clan appearance payload exceeds 65535 bytes");
            }
            player.sendMessage(response);
        } catch (Exception error) {
            Logger.logException(ClanAppearanceService.class, error,
                    "Không gửi được snapshot ngoại hình Cây bang");
            notify(player, "Không tải được ngoại hình Cây bang.");
        } finally {
            if (response != null) {
                response.cleanup();
            }
        }
    }

    /** Text fallback shown when an existing client directly inspects the clan tree. */
    public void sendLegacyStatus(Player player) {
        if (player == null || player.clan == null
                || !ClanFeatureFlags.gI().isEnabled(ClanFeatureFlags.Feature.APPEARANCE)) {
            return;
        }
        ClanAppearancePolicy.Appearance appearance = snapshot(player.clan).appearance();
        StringBuilder text = new StringBuilder("Ngoại hình Cây bang: ")
                .append(appearance.tier().name()).append(" - ")
                .append(appearance.tier().title());
        if (appearance.nextTier() != null) {
            text.append("\nMốc tiếp: ").append(appearance.nextTier().name());
            if (appearance.remainingClanLevels() > 0) {
                text.append("\n- Còn ").append(appearance.remainingClanLevels()).append(" cấp bang");
            }
            if (appearance.remainingTreeLevels() > 0) {
                text.append("\n- Còn ").append(appearance.remainingTreeLevels()).append(" cấp cây");
            }
            if (appearance.remainingClanValue() > 0L) {
                text.append("\n- Còn ").append(Util.formatNumber(appearance.remainingClanValue()))
                        .append(" Clan Value");
            }
        } else {
            text.append("\nĐã đạt bậc ngoại hình cao nhất.");
        }
        notify(player, text.toString());
    }

    private AppearanceView disabledView(Clan clan, int treeLevel) {
        int clanLevel = clan == null ? 0 : clan.level;
        return new AppearanceView(false, ClanAppearancePolicy.resolve(config,
                clanLevel, treeLevel, 0L, 0L));
    }

    private static void notify(Player player, String text) {
        if (player != null) {
            Service.gI().sendThongBao(player, text);
        }
    }

    public record AppearanceView(boolean enabled, ClanAppearancePolicy.Appearance appearance) {
    }
}
