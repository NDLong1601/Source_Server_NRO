package nro.models.npc_list;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import nro.models.ledger.MoneyLedgerService;
import nro.models.ledger.VndProductType;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.services.Service;

/** VIP menu. Reward policy and delivery belong to MoneyLedgerService. */
public class ToriBot extends Npc {

    private static final LocalDateTime VIP_SEASON_START_DATE =
        LocalDateTime.of(LocalDate.now().getYear(), Month.JUNE, 5, 0, 0, 0);
    private static final LocalDateTime VIP_SEASON_END_DATE =
        LocalDateTime.of(LocalDate.now().getYear(), Month.DECEMBER, 5, 23, 59, 59);
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    public ToriBot(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    @Override
    public void openBaseMenu(Player player) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(VIP_SEASON_END_DATE)) {
            createOtherMenu(player, 0, "Ngươi tìm ta có việc gì?", "Đóng");
            return;
        }

        String message = "Trong thời gian mùa VIP diễn ra\n(Từ "
            + VIP_SEASON_START_DATE.format(DATE_TIME_FORMATTER) + " đến hết "
            + VIP_SEASON_END_DATE.format(DATE_TIME_FORMATTER) + ")\n"
            + "Thời gian còn lại: " + formatRemainingTime(Duration.between(now, VIP_SEASON_END_DATE)) + "\n"
            + "Tạo nhân vật mới sẽ được X2 Kinh nghiệm toàn mùa\n"
            + "Nếu nâng cấp VIP sẽ được nhận nhiều ưu đãi hơn nữa.\n"
            + "Lưu ý: nâng cấp VIP chỉ được tối đa 4 lần mỗi mùa (Bạn đã mua: "
            + player.vipPurchaseCount + "/4 lần)";
        createOtherMenu(player, 0, message, "Vip 1", "Vip 2", "Vip 3", "Vip 4");
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (!canOpenNpc(player)) return;
        if (LocalDateTime.now().isAfter(VIP_SEASON_END_DATE)) {
            Service.gI().sendThongBao(player, "Mùa VIP đã kết thúc. Bạn không thể mua VIP vào lúc này.");
            return;
        }
        switch (player.idMark.getIndexMenu()) {
            case 0 -> openVipOffer(player, select);
            case 1 -> confirmVipPurchase(player, select, VndProductType.VIP_1);
            case 2 -> confirmVipPurchase(player, select, VndProductType.VIP_2);
            case 3 -> confirmVipPurchase(player, select, VndProductType.VIP_3);
            case 4 -> confirmVipPurchase(player, select, VndProductType.VIP_4);
            default -> { }
        }
    }

    private void openVipOffer(Player player, int select) {
        VndProductType product = switch (select) {
            case 0 -> VndProductType.VIP_1;
            case 1 -> VndProductType.VIP_2;
            case 2 -> VndProductType.VIP_3;
            case 3 -> VndProductType.VIP_4;
            default -> null;
        };
        if (product == null) return;
        MoneyLedgerService.gI().createIntent(player, product, product.getFixedCost());
        createOtherMenu(player, product.getVipLevel(), vipDescription(product),
            (product.getFixedCost() / 1_000) + ".000\nđiểm mùa [" + player.getSession().vnd + "]", "Đóng");
    }

    private void confirmVipPurchase(Player player, int select, VndProductType product) {
        if (select != 0) return;
        MoneyLedgerService.PurchaseResult result =
            MoneyLedgerService.gI().executePurchase(player, product, product.getFixedCost());
        if (result.isSuccess()) openBaseMenu(player);
        else if (!result.isPending()) Service.gI().sendThongBao(player, result.message());
    }

    private static String vipDescription(VndProductType product) {
        return switch (product) {
            case VIP_1 -> "Nâng cấp VIP 1 bạn sẽ nhận được\n200 thỏi vàng, 10 phiếu giảm giá 80%\nX3 Kinh nghiệm toàn mùa\nThú cưỡi ve sầu xên hsd 30 ngày\nTặng 1 đệ tử, 5 viên đá bảo vệ\nPet bọ cánh cứng hsd 30 ngày\nBúa hắc hường hsd 30 ngày";
            case VIP_2 -> "Nâng cấp VIP 2 bạn sẽ nhận được\n500 thỏi vàng, 10 phiếu giảm giá 80%\nX3 Kinh nghiệm toàn mùa\nThú cưỡi ve sầu xên hsd 30 ngày\nTặng 1 đệ tử, 10 viên đá bảo vệ\nPet bọ cánh cứng hsd 30 ngày\nBúa hắc hường hsd 30 ngày\nCải trang thỏ buma hsd 30 ngày";
            case VIP_3 -> "Nâng cấp VIP 3 bạn sẽ nhận được\n700 thỏi vàng, 10 phiếu giảm giá 80%\nX3 Kinh nghiệm toàn mùa\nThú cưỡi ve sầu xên vĩnh viễn\nTặng 1 đệ tử, 30 viên đá bảo vệ\nPet bọ cánh cứng vĩnh viễn\nBúa hắc hường vĩnh viễn\n2 viên capsule kích hoạt\n10 thẻ đội trưởng vàng\nCải trang thỏ buma vĩnh viễn";
            case VIP_4 -> "Nâng cấp VIP 4 bạn sẽ nhận được\n1000 thỏi vàng, 10 phiếu giảm giá 80%\nX3 Kinh nghiệm toàn mùa\nCải trang hắc mị vĩnh viễn\nTặng 1 đệ tử mabu, 50 viên đá bảo vệ\nTàu ngầm cam 19 vĩnh viễn\nPet rồng nhí vĩnh viễn\n5 viên capsule kích hoạt\n20 thẻ rồng thần";
            default -> throw new IllegalArgumentException("Unsupported VIP product");
        };
    }

    private static String formatRemainingTime(Duration duration) {
        if (duration.isNegative() || duration.isZero()) return "Mùa VIP sắp kết thúc!";
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;
        if (days == 0 && hours == 0 && minutes == 0) return "Còn " + seconds + " giây!";
        if (days == 0 && hours == 0) return "Còn " + minutes + " phút " + seconds + " giây!";
        if (days == 0) return "Còn " + hours + " giờ " + minutes + " phút " + seconds + " giây!";
        return "Còn " + days + " ngày " + hours + " giờ " + minutes + " phút " + seconds + " giây!";
    }
}
