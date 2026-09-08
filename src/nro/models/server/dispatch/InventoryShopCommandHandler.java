package nro.models.server.dispatch;

import java.io.IOException;
import nro.models.combine.CombineService;
import nro.models.server.Maintenance;
import nro.models.server.MenuController;
import nro.models.services.IntrinsicService;
import nro.models.services.Service;
import nro.models.shop.ShopService;
import nro.models.services_func.Input;
import nro.models.services_func.TransactionService;
import nro.models.services_func.UseItem;

public final class InventoryShopCommandHandler implements CommandHandler {

    @Override
    public void handle(CommandContext context) throws Exception {
        switch (context.command()) {
            case -125 -> Input.gI().doInput(context.player(), context.message());
            case 112 -> IntrinsicService.gI().showMenu(context.player());
            case -34 -> handleMagicTree(context);
            case -107 -> {
                context.player().lastTimeOpenPetInfo = System.currentTimeMillis();
                Service.gI().showInfoPet(context.player());
            }
            case -108 -> {
                if (context.player().pet != null) {
                    context.player().pet.changeStatus(context.message().reader().readByte());
                }
            }
            case 6 -> handleBuy(context);
            case 7 -> handleSell(context);
            case -79 -> Service.gI().getPlayerMenu(
                    context.player(), context.message().reader().readInt());
            case -113 -> handleSkillShortcut(context);
            case -103 -> handleFlagSelection(context);
            case -81 -> handleCombine(context);
            case -40 -> {
                if (transactionAllowed(context)) {
                    UseItem.gI().getItem(context.session(), context.message());
                }
            }
            case -43 -> handleUseItem(context);
            case 32 -> MenuController.gI().doSelectMenu(context.player(),
                    context.message().reader().readShort(), context.message().reader().readByte());
            case 33 -> MenuController.gI().openMenuNPC(context.session(),
                    context.message().reader().readShort(), context.player());
            default -> throw new IllegalArgumentException("Inventory/shop handler does not own command "
                    + context.command());
        }
    }

    private void handleMagicTree(CommandContext context) throws IOException {
        switch (context.message().reader().readByte()) {
            case 1 -> context.player().magicTree.openMenuTree();
            case 2 -> context.player().magicTree.loadMagicTree();
            default -> {
            }
        }
    }

    private void handleBuy(CommandContext context) throws IOException {
        if (Maintenance.isRunning || !transactionAllowed(context) || accountProtected(context)) {
            return;
        }
        byte typeBuy = context.message().reader().readByte();
        int templateId = context.message().reader().readShort();
        ShopService.gI().takeItem(context.player(), typeBuy, templateId);
    }

    private void handleSell(CommandContext context) throws IOException {
        if (Maintenance.isRunning || !transactionAllowed(context) || accountProtected(context)) {
            return;
        }
        byte action = context.message().reader().readByte();
        if (action == 0) {
            ShopService.gI().showConfirmSellItem(context.player(),
                    context.message().reader().readByte(), context.message().reader().readShort());
        } else {
            ShopService.gI().sellItem(context.player(),
                    context.message().reader().readByte(), context.message().reader().readShort());
        }
    }

    private void handleSkillShortcut(CommandContext context) {
        for (int i = 0; i < 10; i++) {
            try {
                context.player().playerSkill.skillShortCut[i] = context.message().reader().readByte();
            } catch (IOException e) {
                context.player().playerSkill.skillShortCut[i] = -1;
            }
        }
        context.player().playerSkill.sendSkillShortCut();
    }

    private void handleFlagSelection(CommandContext context) throws IOException {
        byte action = context.message().reader().readByte();
        switch (action) {
            case 0 -> Service.gI().openFlagUI(context.player());
            case 1 -> Service.gI().chooseFlag(
                    context.player(), context.message().reader().readByte());
            default -> {
            }
        }
    }

    private void handleCombine(CommandContext context) {
        try {
            context.message().reader().readByte();
            int[] itemIndexes = new int[context.message().reader().readByte()];
            for (int i = 0; i < itemIndexes.length; i++) {
                itemIndexes[i] = context.message().reader().readByte();
            }
            CombineService.gI().showInfoCombine(context.player(), itemIndexes);
        } catch (IOException ignored) {
        }
    }

    private void handleUseItem(CommandContext context) throws Exception {
        if (!transactionAllowed(context) || accountProtected(context)) {
            return;
        }
        UseItem.gI().doItem(context.player(), context.message());
    }

    private boolean transactionAllowed(CommandContext context) {
        if (!TransactionService.gI().check(context.player())) {
            return true;
        }
        Service.gI().sendThongBao(context.player(), "Không thể thực hiện");
        return false;
    }

    private boolean accountProtected(CommandContext context) {
        if (!context.player().baovetaikhoan) {
            return false;
        }
        Service.gI().sendThongBao(context.player(),
                "Chức năng bảo vệ đã được bật. Bạn vui lòng kiểm tra lại");
        return true;
    }
}
