package nro.models.combine;

import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.Service;
import nro.models.player_system.Template;
import nro.models.utils.Util;

/**
 *
 * @author By Mr Blue
 */

public class LamPhepNhapDa {

    private static final int GOLD_TAO_DA = 10_000_000;  // Lượng vàng cần thiết
    private static final int RATIO_TAO_DA = 80;  // Tỉ lệ thành công

    public static void showInfoCombine(Player player) {
        if (player.combineNew.itemsCombine.size() != 2) {
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, "Cần đặt đúng 2 vật phẩm!", "Đóng");
            return;
        }

        Item item1 = player.combineNew.itemsCombine.get(0);
        Item item2 = player.combineNew.itemsCombine.get(1);

        if (!isValidCombination(item1, item2)) {
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, "Nguyên liệu không hợp lệ!", "Đóng");
            return;
        }

        player.combineNew.goldCombine = GOLD_TAO_DA;
        player.combineNew.ratioCombine = RATIO_TAO_DA;

        String npcSay = "|2|Tỉ lệ thành công: " + RATIO_TAO_DA + "%\n"
                      + "|2|Cần: " + Util.numberToMoney(GOLD_TAO_DA) + " vàng\n";

        if (player.inventory.gold < GOLD_TAO_DA) {
            npcSay += "|7|Còn thiếu " + Util.powerToString(GOLD_TAO_DA - player.inventory.gold) + " vàng\n";
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, npcSay, "Đóng");
        } else {
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.MENU_START_COMBINE, npcSay,
                    "Nâng cấp\n" + Util.numberToMoney(GOLD_TAO_DA) + " vàng\n", "Từ chối");
        }
    }

    public static void lamphepnhapda(Player player) {
        if (player.combineNew.itemsCombine.size() != 2) {
            Service.gI().sendThongBao(player, "Cần đặt đúng 2 vật phẩm!");
            return;
        }

        Item item1 = player.combineNew.itemsCombine.get(0);
        Item item2 = player.combineNew.itemsCombine.get(1);

        if (!isValidCombination(item1, item2)) {
            Service.gI().sendThongBao(player, "Nguyên liệu không hợp lệ!");
            return;
        }

        if (player.inventory.gold < GOLD_TAO_DA) {
            Service.gI().sendThongBao(player, "Không đủ vàng để thực hiện!");
            return;
        }

        if (item1.quantity < 10 || item2.quantity < 1) {
            Service.gI().sendThongBao(player, "Không đủ nguyên liệu để thực hiện!");
            return;
        }

        // Trừ nguyên liệu và vàng
        player.inventory.gold -= GOLD_TAO_DA;
        InventoryService.gI().subQuantityItemsBag(player, item1, 10);
        InventoryService.gI().subQuantityItemsBag(player, item2, 1);

        // Xác suất tạo đá mới
        if (Util.isTrue(RATIO_TAO_DA, 100)) {
            int randomId = Util.nextInt(220, 224);
            Item newItem = new Item();
            newItem.template = ItemService.gI().getTemplate(randomId);
            newItem.quantity = 1;
            InventoryService.gI().addItemBag(player, newItem);
            CombineService.gI().sendEffectSuccessCombine(player);
            Service.gI().sendThongBao(player, "Chúc mừng! Bạn đã chế tạo thành công.");
        } else {
            CombineService.gI().sendEffectFailCombine(player);
            Service.gI().sendThongBao(player, "Thất bại! Nguyên liệu đã bị mất.");
        }

        InventoryService.gI().sendItemBags(player);
        Service.gI().sendMoney(player);
        CombineService.gI().reOpenItemCombine(player);
    }

    private static boolean isValidCombination(Item item1, Item item2) {
        return (item1.template.id == 225 && item2.template.id == 226) ||
               (item1.template.id == 226 && item2.template.id == 225);
    }
}