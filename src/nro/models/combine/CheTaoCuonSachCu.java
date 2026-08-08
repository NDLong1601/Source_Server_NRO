package nro.models.combine;

import nro.models.consts.ConstFont;
import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import nro.models.player.Player;
import nro.models.services.ItemService;
import nro.models.services.Service;
import nro.models.services.InventoryService;
import nro.models.utils.Util;
/**
 *
 * @author By Mr Blue
 */

public class CheTaoCuonSachCu {

    private static final int TRANG_SACH_CU_ID = 1281;
    private static final int BIA_SACH_ID = 1282;
    private static final int CUON_SACH_CU_ID = 1283;
    private static final int REQUIRED_TRANG_SACH_CU = 9999;
    private static final int REQUIRED_BIA_SACH = 1;
    private static final int SUCCESS_RATE_PERCENT = 20;
    private static final int TRANG_SACH_CU_LOSS_ON_FAIL = 99;

    public static void showCombine(Player player) {
        int quantityTrangSachCu = getItemQuantity(player, TRANG_SACH_CU_ID);
        int quantityBiaSach = getItemQuantity(player, BIA_SACH_ID);

        StringBuilder text = new StringBuilder()
            .append(ConstFont.BOLD_GREEN).append("Chế tạo Cuốn sách cũ\n")
            .append(formatRequirement("Trang sách cũ", quantityTrangSachCu, REQUIRED_TRANG_SACH_CU))
            .append(formatRequirement("Bìa sách", quantityBiaSach, REQUIRED_BIA_SACH))
            .append(formatSuccessRate(quantityTrangSachCu, quantityBiaSach))
            .append(ConstFont.BOLD_RED).append("Thất bại mất 99 trang sách và 1 bìa sách");

        int menuType = (quantityTrangSachCu >= REQUIRED_TRANG_SACH_CU && quantityBiaSach >= REQUIRED_BIA_SACH)
            ? ConstNpc.DONG_THANH_SACH_CU
            : ConstNpc.IGNORE_MENU;

        CombineService.gI().baHatMit.createOtherMenu(player, menuType, text.toString(), "Đồng ý", "Từ chối");
    }

    public static void cheTaoCuonSachCu(Player player) {
        if (!hasSufficientSpace(player)) {
            Service.gI().sendThongBao(player, "Cần 1 ô trống trong hành trang.");
            return;
        }

        Item trangSachCu = InventoryService.gI().findItemBag(player, TRANG_SACH_CU_ID);
        Item biaSach = InventoryService.gI().findItemBag(player, BIA_SACH_ID);

        if (trangSachCu == null || biaSach == null || trangSachCu.quantity < REQUIRED_TRANG_SACH_CU || biaSach.quantity < REQUIRED_BIA_SACH) {
            return;
        }

        CombineService.gI().sendAddItemCombine(player, ConstNpc.BA_HAT_MIT, trangSachCu, biaSach);

        if (Util.isTrue(SUCCESS_RATE_PERCENT, 100)) {
            processSuccess(player, trangSachCu, biaSach);
        } else {
            processFailure(player, trangSachCu, biaSach);
        }
    }

    private static int getItemQuantity(Player player, int itemId) {
        Item item = InventoryService.gI().findItemBag(player, itemId);
        return item != null ? item.quantity : 0;
    }

    private static String formatRequirement(String itemName, int currentQuantity, int requiredQuantity) {
        String color = currentQuantity >= requiredQuantity ? ConstFont.BOLD_BLUE : ConstFont.BOLD_RED;
        return String.format("%s%s %d/%d\n", color, itemName, currentQuantity, requiredQuantity);
    }

    private static String formatSuccessRate(int quantityTrangSachCu, int quantityBiaSach) {
        boolean canCombine = quantityTrangSachCu >= REQUIRED_TRANG_SACH_CU && quantityBiaSach >= REQUIRED_BIA_SACH;
        String color = canCombine ? ConstFont.BOLD_BLUE : ConstFont.BOLD_RED;
        return String.format("%sTỉ lệ thành công: %d%%\n", color, SUCCESS_RATE_PERCENT);
    }

    private static boolean hasSufficientSpace(Player player) {
        return InventoryService.gI().getCountEmptyBag(player) > 0 || InventoryService.gI().findItemBag(player, CUON_SACH_CU_ID) != null;
    }

    private static void processSuccess(Player player, Item trangSachCu, Item biaSach) {
        InventoryService.gI().subQuantityItemsBag(player, trangSachCu, REQUIRED_TRANG_SACH_CU);
        InventoryService.gI().subQuantityItemsBag(player, biaSach, REQUIRED_BIA_SACH);

        Item cuonSachCu = ItemService.gI().createNewItem((short) CUON_SACH_CU_ID);
        cuonSachCu.itemOptions.add(new Item.ItemOption(30, 0));
        InventoryService.gI().addItemBag(player, cuonSachCu);

        CombineService.gI().sendEffSuccessVip(player, cuonSachCu.template.iconID);
        Util.setTimeout(() -> {
            Service.gI().sendServerMessage(player, "Bạn nhận được " + cuonSachCu.template.name);
            CombineService.gI().baHatMit.npcChat(player, "Chúc mừng con nhé");
        }, 2000);
    }

    private static void processFailure(Player player, Item trangSachCu, Item biaSach) {
        InventoryService.gI().subQuantityItemsBag(player, trangSachCu, TRANG_SACH_CU_LOSS_ON_FAIL);
        InventoryService.gI().subQuantityItemsBag(player, biaSach, REQUIRED_BIA_SACH);

        CombineService.gI().sendEffFailVip(player);
        Util.setTimeout(() -> {
            CombineService.gI().baHatMit.npcChat(player, "Chúc con may mắn lần sau, đừng buồn con nhé");
        }, 2000);
    }
}