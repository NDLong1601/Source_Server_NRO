package nro.models.combine;

import nro.models.consts.ConstFont;
import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.Service;
import nro.models.utils.Util;

/**
 *
 * @author By Mr Blue
 */

public class PhanRaSach {

    public static void showInfoCombine(Player player) {
        if (player.combineNew.itemsCombine.size() != 1) {
            Service.gI().sendDialogMessage(player, "Không tìm thấy vật phẩm");
            return;
        }
        Item sachTuyetKy = null;
        for (Item item : player.combineNew.itemsCombine) {
            if (item.isSachTuyetKy() || item.isSachTuyetKy2()) {
                sachTuyetKy = item;
            }
        }
        if (sachTuyetKy == null) {
            Service.gI().sendDialogMessage(player, "Không tìm thấy vật phẩm");
            return;
        }
        StringBuilder text = new StringBuilder();
        text.append(ConstFont.BOLD_BLUE).append("Phân rã sách\n");
        text.append(ConstFont.BOLD_BLUE).append("Nhận lại 5 cuốn sách cũ\n");
        text.append(player.inventory.gold >= 10_000_000 ? ConstFont.BOLD_BLUE : ConstFont.BOLD_RED).append("Phí rã 10 triệu vàng");
        if (player.inventory.gold < 10_000_000) {
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, text.toString(),
                    "Còn thiếu\n" + Util.numberToMoney(10_000_000 - player.inventory.gold) + " vàng");
            return;
        }
        CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.MENU_START_COMBINE, text.toString(),
                "Đồng ý", "Từ chối");
    }

    public static void phanRaSach(Player player) {
        if (player.combineNew.itemsCombine.size() != 1) {
            return;
        }
        Item sachTuyetKy = null;
        for (Item item : player.combineNew.itemsCombine) {
            if (item.isSachTuyetKy() || item.isSachTuyetKy2()) {
                sachTuyetKy = item;
            }
        }
        if (sachTuyetKy == null || player.inventory.gold < 10_000_000) {
            return;
        }
        InventoryService.gI().subQuantityItemsBag(player, sachTuyetKy, 1);
        Item cuonSachCu = ItemService.gI().createNewItem((short) 1283, 5);
        cuonSachCu.itemOptions.add(new Item.ItemOption(30, 0));
        InventoryService.gI().addItemBag(player, cuonSachCu);
        CombineService.gI().sendEffectSuccessCombine(player);
        Service.gI().sendMoney(player);
        InventoryService.gI().sendItemBags(player);
        CombineService.gI().reOpenItemCombine(player);
    }
}
