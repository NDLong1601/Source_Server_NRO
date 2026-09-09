package nro.models.clan;

import nro.models.player.Player;
import nro.models.services.Service;

/** In-game admin command used to select a clan-tree level for visual testing. */
public final class ClanTreeAdminCommand {

    public static final String NAME = "setcay";

    private ClanTreeAdminCommand() {
    }

    public static void execute(Player player, String rawLevel) {
        if (player == null) {
            return;
        }
        if (!player.isAdmin()) {
            Service.gI().sendThongBao(player, "Bạn không có quyền dùng lệnh này.");
            return;
        }

        ClanTreeService treeService = ClanTreeService.gI();
        LevelInput input = parseLevel(rawLevel, treeService.maxLevel());
        if (!input.accepted()) {
            Service.gI().sendThongBao(player, input.message());
            return;
        }

        ClanTreeService.AdminLevelChange change = treeService.setLevelForAdminTesting(player, input.level());
        if (!change.success()) {
            Service.gI().sendThongBao(player, change.message());
            return;
        }

        ClanAppearancePolicy.Appearance appearance = ClanAppearanceService.gI()
                .snapshot(player.clan).appearance();
        String levelText = change.changed()
                ? "Đã đặt Cây bang từ cấp " + change.previousLevel() + " thành cấp " + change.currentLevel() + "."
                : "Cây bang đã ở cấp " + change.currentLevel() + ".";
        Service.gI().sendThongBao(player, levelText
                + "\nNgoại hình 5D: " + appearance.tier().name() + " - " + appearance.tier().title()
                + "\nTiến độ và sản lượng được giữ nguyên; lượt nâng cấp đang chờ đã được hủy khi đổi cấp.");
    }

    /** Pure boundary validation so malformed chat text never reaches persistence code. */
    public static LevelInput parseLevel(String rawLevel, int maxLevel) {
        if (maxLevel < 1) {
            return LevelInput.rejected("Cấu hình cấp tối đa của Cây bang không hợp lệ.");
        }
        String usage = "Cú pháp: " + NAME + " <cấp 1-" + maxLevel + ">";
        if (rawLevel == null) {
            return LevelInput.rejected(usage);
        }
        String value = rawLevel.trim();
        if (value.isEmpty() || value.length() > 3) {
            return LevelInput.rejected(usage);
        }
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return LevelInput.rejected(usage);
            }
        }
        int level;
        try {
            level = Integer.parseInt(value);
        } catch (NumberFormatException error) {
            return LevelInput.rejected(usage);
        }
        if (level < 1 || level > maxLevel) {
            return LevelInput.rejected("Cấp cây phải nằm trong khoảng 1-" + maxLevel + ".");
        }
        return LevelInput.accepted(level);
    }

    public record LevelInput(boolean accepted, int level, String message) {

        private static LevelInput accepted(int level) {
            return new LevelInput(true, level, "");
        }

        private static LevelInput rejected(String message) {
            return new LevelInput(false, 0, message);
        }
    }
}
