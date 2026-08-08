package nro.models.combine;

import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import nro.models.item.Item.ItemOption;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.Service;
import nro.models.utils.Util;

/**
 *
 * @author By Mr Blue
 */

public class PhanRaTrangBiKichHoat {

    public class PhanRaTrangBi {

        private static final int GOLD_PHAN_RA = 2_000_000_000;  // Lượng vàng cần thiết
        private static final int RATIO_PHAN_RA = 100;  // Tỉ lệ thành công
        private static final int[][] optionIds = {
            {128, 129, 127, 233, 245, 130, 131, 132, 233, 237, 133, 135, 134, 233, 241}};

        public static void showInfoCombine(Player player) {
            if (player.combineNew.itemsCombine.size() != 1) {
                CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, "Cần đặt đúng vật phẩm!", "Đóng");
                return;
            }

            Item item1 = player.combineNew.itemsCombine.get(0);

            if (!isValidItem(item1)) {
                CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, "Vật phẩm không đủ điều kiện để phân rã!", "Đóng");
                return;
            }

            player.combineNew.goldCombine = GOLD_PHAN_RA;
            player.combineNew.ratioCombine = RATIO_PHAN_RA;

            String npcSay = "|2|Tỉ lệ thành công: " + RATIO_PHAN_RA + "%\n"
                    + "|2|Cần: " + Util.numberToMoney(GOLD_PHAN_RA) + " vàng\n";

            if (player.inventory.gold < GOLD_PHAN_RA) {
                npcSay += "|7|Còn thiếu " + Util.powerToString(GOLD_PHAN_RA - player.inventory.gold) + " vàng\n";
                CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, npcSay, "Đóng");
            } else {
                CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.MENU_START_COMBINE, npcSay,
                        "Phân rã\n" + Util.numberToMoney(GOLD_PHAN_RA) + " vàng", "Từ chối");
            }
        }

        public static void ThucHienPhanRa(Player player) {
            if (player.combineNew.itemsCombine.size() != 1) {
                Service.gI().sendThongBao(player, "Cần đặt đúng 1 vật phẩm!");
                return;
            }

            Item item1 = player.combineNew.itemsCombine.get(0);

            if (!isValidItem(item1)) {
                Service.gI().sendThongBao(player, "Vật phẩm không đủ điều kiện để phân rã!");
                return;
            }

            if (player.inventory.gold < GOLD_PHAN_RA) {
                Service.gI().sendThongBao(player, "Không đủ vàng để thực hiện!");
                return;
            }

            if (item1.quantity < 1) {
                Service.gI().sendThongBao(player, "Không đủ vật phẩm để thực hiện!");
                return;
            }
            player.inventory.gold -= GOLD_PHAN_RA;
            InventoryService.gI().subQuantityItemsBag(player, item1, 1);
            if (Util.isTrue(RATIO_PHAN_RA, 100)) {
                int itemId = 1656;
                Item existingItem = InventoryService.gI().findItemBag(player, itemId);
                if (existingItem != null) {
                    existingItem.quantity += 1;
                } else {
                    Item newItem = new Item();
                    newItem.template = ItemService.gI().getTemplate(itemId);
                    newItem.quantity = 1;
                    InventoryService.gI().addItemBag(player, newItem);
                }
                CombineService.gI().sendEffectSuccessCombine(player);
                Service.gI().sendThongBao(player, "Phân rã thành công!");
            } else {
                CombineService.gI().sendEffectFailCombine(player);
                Service.gI().sendThongBao(player, "Phân rã thất bại!");
            }

            InventoryService.gI().sendItemBags(player);
            Service.gI().sendMoney(player);
            CombineService.gI().reOpenItemCombine(player);
        }

        private static boolean isValidItem(Item item) {
            if (item == null || item.itemOptions == null) {
                return false;
            }
            for (ItemOption option : item.itemOptions) {
                for (int[] optionsForRace : optionIds) {
                    for (int validOptionId : optionsForRace) {
                        if (option.optionTemplate.id == validOptionId) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

    }
}
