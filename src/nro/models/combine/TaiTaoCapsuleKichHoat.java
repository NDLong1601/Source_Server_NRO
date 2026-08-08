package nro.models.combine;

import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import java.util.ArrayList;
import java.util.List;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.Service;
import nro.models.utils.Util;

/**
 *
 * @author MrBlue
 */
public class TaiTaoCapsuleKichHoat {

    private static final int GOLD_TAI_TAO = 2_000_000_000;
    private static final int RATIO_TAI_TAO = 100;

    private static final int KHOANG_TAI_CHE_ID = 1656;
    private static final int CAPSULE_ID = 1634;

    private static final int REQUIRED_KHOANG = 3;
    private static final int REQUIRED_CAPSULE = 1;

    public static void showInfoCombine(Player player) {
        if (player.combineNew.itemsCombine.isEmpty()) {
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, "Cần đặt đủ vật phẩm!", "Đóng");
            return;
        }

        int countKhoang = 0;
        int countVatLieu2 = 0;

        for (Item item : player.combineNew.itemsCombine) {
            if (item.template.id == KHOANG_TAI_CHE_ID) {
                countKhoang += item.quantity;
            } else if (item.template.id == CAPSULE_ID) {
                countVatLieu2 += item.quantity;
            }
        }

        if (countKhoang < REQUIRED_KHOANG || countVatLieu2 < REQUIRED_CAPSULE) {
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU,
                    "Thiếu vật phẩm cần thiết!\n"
                    + "- Cần: " + REQUIRED_KHOANG + " Khoáng tái chế\n"
                    + "- Cần: " + REQUIRED_CAPSULE + " Capsule Vỡ", "Đóng");
            return;
        }

        player.combineNew.goldCombine = GOLD_TAI_TAO;
        player.combineNew.ratioCombine = RATIO_TAI_TAO;

        String npcSay = "|2|Tỉ lệ thành công: " + RATIO_TAI_TAO + "%\n"
                + "|2|Cần: " + REQUIRED_KHOANG + " Khoáng tái chế\n"
                + "|2|Cần: " + REQUIRED_CAPSULE + " Capsule Vỡ\n"
                + "|2|Cần: " + Util.numberToMoney(GOLD_TAI_TAO) + " vàng\n";

        if (player.inventory.gold < GOLD_TAI_TAO) {
            npcSay += "|7|Còn thiếu " + Util.powerToString(GOLD_TAI_TAO - player.inventory.gold) + " vàng\n";
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, npcSay, "Đóng");
        } else {
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.MENU_START_COMBINE, npcSay,
                    "Tái tạo\n" + Util.numberToMoney(GOLD_TAI_TAO) + " vàng", "Từ chối");
        }
    }

    public static void thucHienTaiTao(Player player) {
        if (player.combineNew.itemsCombine.isEmpty()) {
            Service.gI().sendThongBao(player, "Cần đặt đủ vật phẩm!");
            return;
        }

        int countKhoang = 0;
        int countCapsule = 0;

        for (Item item : player.combineNew.itemsCombine) {
            if (item.template.id == KHOANG_TAI_CHE_ID) {
                countKhoang += item.quantity;
            } else if (item.template.id == CAPSULE_ID) {
                countCapsule += item.quantity;
            }
        }

        if (countKhoang < REQUIRED_KHOANG || countCapsule < REQUIRED_CAPSULE) {
            Service.gI().sendThongBao(player, "Không đủ vật phẩm cần thiết để tái tạo!");
            return;
        }

        if (player.inventory.gold < GOLD_TAI_TAO) {
            Service.gI().sendThongBao(player, "Không đủ vàng để thực hiện!");
            return;
        }

        player.inventory.gold -= GOLD_TAI_TAO;
        removeItem(player, KHOANG_TAI_CHE_ID, REQUIRED_KHOANG);
        removeItem(player, CAPSULE_ID, REQUIRED_CAPSULE);

        if (Util.isTrue(RATIO_TAI_TAO, 100)) {
            int itemId = 1655;
            Item newItem = new Item();
            newItem.template = ItemService.gI().getTemplate(itemId);
            newItem.quantity = 1;
            InventoryService.gI().addItemBag(player, newItem);

            CombineService.gI().sendEffectSuccessCombine(player);
            Service.gI().sendThongBao(player, "Tái tạo thành công!");
        } else {
            CombineService.gI().sendEffectFailCombine(player);
            Service.gI().sendThongBao(player, "Tái tạo thất bại!");
        }

        InventoryService.gI().sendItemBags(player);
        Service.gI().sendMoney(player);
        CombineService.gI().reOpenItemCombine(player);
    }

    private static void removeItem(Player player, int itemId, int quantityToRemove) {
        List<Item> items = new ArrayList<>(player.combineNew.itemsCombine);
        for (Item item : items) {
            if (item != null && item.template != null && quantityToRemove > 0) {
                if (item.template.id == itemId) {
                    int remove = Math.min(item.quantity, quantityToRemove);
                    InventoryService.gI().subQuantityItemsBag(player, item, remove);
                    quantityToRemove -= remove;
                }
            }
        }
    }

}
