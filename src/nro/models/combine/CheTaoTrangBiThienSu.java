
package nro.models.combine;

import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import nro.models.item.Item.ItemOption;
import java.util.ArrayList;
import java.util.Arrays;
import nro.models.player.Player;
import nro.models.services.ItemService;
import nro.models.services.Service;
import nro.models.services.InventoryService;
import nro.models.utils.Util;

/**
 *
 * @author By Mr Blue
 */

public class CheTaoTrangBiThienSu {
      public static void showInfoCombine(Player player) {
     if (player.combineNew.itemsCombine.size() < 4 || player.combineNew.itemsCombine.size() > 4) {
                    Service.gI().sendThongBao(player, "Thiếu vật phẩm, vui lòng thêm vào");
                    return;
                } else if (player.combineNew.itemsCombine.stream().filter(item -> item.isNotNullItem() && item.isCongThucVip()).count() != 1) {
                     Service.gI().sendThongBao(player, "Thiếu Công Thức Vip");
                    return;
                } else if (player.combineNew.itemsCombine.stream().filter(item -> item.isNotNullItem() && item.isManhTS() && item.quantity >= 999).count() != 1) {
                     Service.gI().sendThongBao(player, "Thiếu Mảnh Thiên Sứ");
                    return;
                } else if (player.combineNew.itemsCombine.stream().filter(item -> item.isNotNullItem() && item.isDaNangCap1()).count() != 1) {
                     Service.gI().sendThongBao(player, "Thiếu Đá Nâng Cấp");
                    return;
                } else if (player.combineNew.itemsCombine.stream().filter(item -> item.isNotNullItem() && item.isDaMayMan()).count() != 1) {
                     Service.gI().sendThongBao(player, "Thiếu Đá May Mắn");
                    return;
                }
                Item mTS = null,
                        daNC = null,
                        daMM = null,
                        CtVip = null;
                for (Item item : player.combineNew.itemsCombine) {
                    if (item.isNotNullItem()) {
                        if (item.isManhTS()) {
                            mTS = item;
                        } else if (item.isDaNangCap1()) {
                            daNC = item;
                        } else if (item.isDaMayMan()) {
                            daMM = item;
                        } else if (item.isCongThucVip()) {
                            CtVip = item;
                        }
                    }
                }
                if (InventoryService.gI().getCountEmptyBag(player) > 0) {//check chỗ trống hành trang
                    if (player.inventory.gold < 10000000) {
                        Service.gI().sendThongBao(player, "Không đủ vàng để thực hiện");
                        return;
                    }
                    player.inventory.gold -= 10000000;

                    int tilemacdinh = 90;
                    int tileLucky = 5;
                    if (daNC != null) {
                        tilemacdinh += (daNC.template.id - 1073);
                    }
                    if (daMM != null) {
                        tileLucky += tileLucky * (daMM.template.id - 1078);
                    }

                    if (Util.nextInt(0, 100) < tilemacdinh) {
                        Item itemCtVip = player.combineNew.itemsCombine.stream().filter(item -> item.isNotNullItem() && item.isCongThucVip()).findFirst().get();

                        Item itemManh = player.combineNew.itemsCombine.stream().filter(item -> item.isNotNullItem() && item.isManhTS() && item.quantity >= 999).findFirst().get();

                        tilemacdinh = 100;
                        short[][] itemIds = {{1048, 1051, 1054, 1057, 1060}, {1049, 1052, 1055, 1058, 1061}, {1050, 1053, 1056, 1059, 1062}}; // thứ tự td - 0,nm - 1, xd - 2

                        Item itemTS = ItemService.gI().DoThienSu(itemIds[itemCtVip.template.gender > 2 ? player.gender : itemCtVip.template.gender][itemManh.typeIdManh()], itemCtVip.template.gender);

                        if (tilemacdinh > 0) {
                            for (byte w = 0; w < itemTS.itemOptions.size(); w++) {
                                if (itemTS.itemOptions.get(w).optionTemplate.id != 0 && itemTS.itemOptions.get(w).optionTemplate.id != 20) {
                                    itemTS.itemOptions.get(w).param += (itemTS.itemOptions.get(w).param * tilemacdinh / 100);
                                }
                            }
                        }
                        tilemacdinh = Util.nextInt(0, 50);

                        if (tilemacdinh <= tileLucky) {
                            if (tilemacdinh >= (tileLucky - 3)) {
                                tileLucky = 3;
                            } else if (tilemacdinh <= (tileLucky - 4) && tilemacdinh >= (tileLucky - 10)) {
                                tileLucky = 2;
                            } else {
                                tileLucky = 1;
                            }
                            itemTS.itemOptions.add(new ItemOption(15, tileLucky));
                            ArrayList<Integer> listOptionBonus = new ArrayList<>(Arrays.asList(50, 77, 103, 94, 5));
                            for (int j = 0; j < tileLucky; j++) {
                                tilemacdinh = Util.nextInt(0, listOptionBonus.size() - 1);
                                itemTS.itemOptions.add(new ItemOption(listOptionBonus.get(tilemacdinh), Util.nextInt(1, 3)));
                                listOptionBonus.remove(tilemacdinh);
                            }
                        }

                        InventoryService.gI().addItemBag(player, itemTS);
                        CombineService.gI().sendEffectSuccessCombine(player);
                    } else {
                        CombineService.gI().sendEffectFailCombine(player);
                    }
                    if (mTS != null && daMM != null && daNC != null && CtVip != null) {
                        InventoryService.gI().subQuantityItemsBag(player, CtVip, 1);
                        InventoryService.gI().subQuantityItemsBag(player, daNC, 1);
                        InventoryService.gI().subQuantityItemsBag(player, mTS, 999);
                        InventoryService.gI().subQuantityItemsBag(player, daMM, 1);
                    } else if (CtVip != null && mTS != null) {
                        InventoryService.gI().subQuantityItemsBag(player, CtVip, 1);
                        InventoryService.gI().subQuantityItemsBag(player, mTS, 999);
                    } else if (CtVip != null && mTS != null && daNC != null) {
                        InventoryService.gI().subQuantityItemsBag(player, CtVip, 1);
                        InventoryService.gI().subQuantityItemsBag(player, mTS, 999);
                        InventoryService.gI().subQuantityItemsBag(player, daNC, 1);
                    } else if (CtVip != null && mTS != null && daMM != null) {
                        InventoryService.gI().subQuantityItemsBag(player, CtVip, 1);
                        InventoryService.gI().subQuantityItemsBag(player, mTS, 999);
                        InventoryService.gI().subQuantityItemsBag(player, daMM, 1);
                    }

                    InventoryService.gI().sendItemBags(player);
                     Service.gI().sendMoney(player);
                    CombineService.gI().reOpenItemCombine(player);
                } else {
                     Service.gI().sendThongBao(player, "Bạn phải có ít nhất 1 ô trống hành trang");
                }
      }
            
    

    public static void CheTaoTS(Player player) {
         if (player.combineNew.itemsCombine.size() != 4) {
            Service.gI().sendThongBao(player, "Thiếu đồ");
            return;
        }
        if (player.inventory.gold < 500_000_000) {
            Service.gI().sendThongBao(player, "Ảo ít thôi con...");
            return;
        }
        if (InventoryService.gI().getCountEmptyBag(player) < 1) {
            Service.gI().sendThongBao(player, "Bạn phải có ít nhất 1 ô trống hành trang");
            return;
        }
        Item itemTL = player.combineNew.itemsCombine.stream().filter(item -> item.isNotNullItem() && item.isDHD()).findFirst().get();
        Item itemManh = player.combineNew.itemsCombine.stream().filter(item -> item.isNotNullItem() && item.isManhTS() && item.quantity >= 5).findFirst().get();

        player.inventory.gold -= 500_000_000;
          CombineService.gI().sendEffectSuccessCombine(player);
        short[][] itemIds = {{1048, 1051, 1054, 1057, 1060}, {1049, 1052, 1055, 1058, 1061}, {1050, 1053, 1056, 1059, 1062}}; // thứ tự td - 0,nm - 1, xd - 2

        Item itemTS = ItemService.gI().DoThienSu(itemIds[itemTL.template.gender > 2 ? player.gender : itemTL.template.gender][itemManh.typeIdManh()], itemTL.template.gender);
        InventoryService.gI().addItemBag(player, itemTS);

        InventoryService.gI().subQuantityItemsBag(player, itemTL, 1);
        InventoryService.gI().subQuantityItemsBag(player, itemManh, 99);
        InventoryService.gI().sendItemBags(player);
        Service.gI().sendMoney(player);
        Service.gI().sendThongBao(player, "Bạn đã nhận được " + itemTS.template.name);
        player.combineNew.itemsCombine.clear();
          CombineService.gI().reOpenItemCombine(player);
    }

    
}
