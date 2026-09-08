package nro.models.server.dispatch;

import nro.models.services.AchievementService;
import nro.models.services.Service;
import nro.models.services_func.LuckyRound;
import nro.models.services_func.TransactionService;
import nro.models.shop_ky_gui.ConsignShopService;

public final class EconomyCommandHandler implements CommandHandler {

    @Override
    public void handle(CommandContext context) throws Exception {
        switch (context.command()) {
            case -100 -> handleConsignment(context);
            case -127 -> LuckyRound.gI().readOpenBall(context.player(), context.message());
            case -86 -> TransactionService.gI().controller(context.player(), context.message());
            case -76 -> {
                if (context.message().reader() != null && context.message().reader().available() >= 1) {
                    AchievementService.gI().confirmAchievement(
                            context.player(), context.message().reader().readByte());
                }
            }
            default -> throw new IllegalArgumentException("Economy handler does not own command "
                    + context.command());
        }
    }

    private void handleConsignment(CommandContext context) throws Exception {
        if (TransactionService.gI().check(context.player())) {
            Service.gI().sendThongBao(context.player(), "Không thể thực hiện");
            return;
        }
        if (context.player().baovetaikhoan) {
            Service.gI().sendThongBao(context.player(),
                    "Chức năng bảo vệ đã được bật. Bạn vui lòng kiểm tra lại");
            return;
        }
        byte action = context.message().reader().readByte();
        switch (action) {
            case 0 -> {
                short itemId = context.message().reader().readShort();
                byte moneyType = context.message().reader().readByte();
                int money = context.message().reader().readInt();
                int quantity = context.player().getSession().version >= 220
                        ? context.message().reader().readInt()
                        : context.message().reader().readByte();
                if (quantity > 0) {
                    ConsignShopService.gI().KiGui(context.player(), itemId, money, moneyType, quantity);
                }
            }
            case 1, 2 -> ConsignShopService.gI().claimOrDel(
                    context.player(), action, context.message().reader().readShort());
            case 3 -> {
                short itemId = context.message().reader().readShort();
                byte clientMoneyType = context.message().reader().readByte();
                int clientPrice = context.message().reader().readInt();
                ConsignShopService.gI().buyItem(context.player(), itemId, clientMoneyType, clientPrice);
            }
            case 4 -> ConsignShopService.gI().openShopKyGui(context.player(),
                    context.message().reader().readByte(), context.message().reader().readByte());
            case 5 -> ConsignShopService.gI().upItemToTop(
                    context.player(), context.message().reader().readShort());
            default -> Service.gI().sendThongBao(context.player(), "Không thể thực hiện");
        }
    }
}
