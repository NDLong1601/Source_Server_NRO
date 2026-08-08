package nro.models.combine;

import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import nro.models.item.Item.ItemOption;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.Service;
import nro.models.utils.Util;

/**
 *
 * @author By Mr Blue
 *
 */
public class CuongHoaLoSaoPhaLe {

    private static final int COST = 500_000_000;

    public static void showInfoCombine(Player player) {
        if (InventoryService.gI().getCountEmptyBag(player) > 0) {
            if (player.combineNew.itemsCombine.size() == 3) {
                Item item = null, Hematite = null, DuiDuc = null;

                for (Item i : player.combineNew.itemsCombine) {
                    if (CombineSystem.isTrangBiPhaLeHoa(i)) {
                        item = i;
                    } else if (i.template.id == 1423) { // Hematite
                        Hematite = i;
                    } else if (i.template.id == 1438) { // Dùi Đục
                        DuiDuc = i;
                    }
                }

                if (item != null && Hematite != null && DuiDuc != null && Hematite.quantity >= 1 && DuiDuc.quantity >= 1) {
                    int star = 0;
                    for (ItemOption io : item.itemOptions) {
                        if (io.optionTemplate.id == 107) {
                            star = io.param;
                            break;
                        }
                    }

                    if (star < 7) {
                        CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU,
                                "Trang bị phải có ít nhất 7 sao pha lê mới có thể cường hóa!", "Đóng");
                        return;
                    }

                    String npcSay = item.template.name + "\n|2|";
                    for (ItemOption io : Hematite.itemOptions) {
                        npcSay += io.getOptionString() + "\n";
                    }
                    npcSay += "Cường hóa\n" + " Ô sao pha lê thứ 8 hoặc 9\n" + item.template.name
                            + "\nTỉ lệ thành công: 50%\n"
                            + "|7| Cần 1 " + Hematite.template.name
                            + "\n|7| Cần 1 " + DuiDuc.template.name
                            + "\nCần " + Util.numberToMoney(COST) + " vàng";

                    CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.MENU_START_COMBINE, npcSay, "Cường Hóa", "Từ chối");
                } else {
                    CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU,
                            "Bạn chưa bỏ đủ vật phẩm !!!", "Đóng");
                }
            } else {
                CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU,
                        "Cần bỏ đủ vật phẩm yêu cầu", "Đóng");
            }
        } else {
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU,
                    "Hành trang cần ít nhất 1 chỗ trống", "Đóng");
        }
    }

    public static void cuongHoaLoSaoPhaLe(Player player) {
        if (InventoryService.gI().getCountEmptyBag(player) == 0) {
            Service.gI().sendThongBao(player, "Cần ít nhất 1 ô trống trong hành trang.");
            return;
        }

        if (player.inventory.gold < COST) {
            Service.gI().sendThongBao(player, "Con cần thêm vàng để cường hóa...");
            return;
        }

        if (player.combineNew.itemsCombine.isEmpty()) {
            Service.gI().sendThongBao(player, "Không có vật phẩm để cường hóa.");
            return;
        }

        Item item = null;
        Item hematite = null;
        Item duiDuc = null;

        for (Item i : player.combineNew.itemsCombine) {
            if (CombineSystem.isTrangBiPhaLeHoa(i)) {
                item = i;
            } else if (i.template.id == 1423) {
                hematite = i;
            } else if (i.template.id == 1438) {
                duiDuc = i;
            }
        }

        if (item == null || hematite == null || duiDuc == null || hematite.quantity < 1 || duiDuc.quantity < 1) {
            Service.gI().sendThongBao(player, "Thiếu vật phẩm hoặc số lượng không đủ.");
            return;
        }

        int star = 0;
        ItemOption opt228 = null;
        boolean hasOption218 = false;
        boolean hasOption102With7 = false;

        for (ItemOption io : item.itemOptions) {
            if (io.optionTemplate.id == 107) {
                star = io.param;
            } else if (io.optionTemplate.id == 228) {
                opt228 = io;
            } else if (io.optionTemplate.id == 218) {
                hasOption218 = true;
            } else if (io.optionTemplate.id == 102 && io.param == 7) {
                hasOption102With7 = true;
            }
        }

        if ((opt228 == null || opt228.param < 8) && !hasOption102With7) {
            Service.gI().sendThongBao(player, "Trang bị cần có đủ 7 lỗ để cường hóa.");
            return;
        }

        if (star < 8) {
            Service.gI().sendThongBao(player, "Vui lòng nâng cấp trang bị lên 8 hoặc 9 sao trước khi cường hóa.");
            return;
        }

        boolean success = Util.isTrue(50, 200);
        player.inventory.gold -= COST;

        if (star == 8) {
            if (opt228 == null) {
                if (!hasOption218) {
                    item.itemOptions.add(new ItemOption(218, 0));
                }
                item.itemOptions.add(new ItemOption(228, 8));
                CombineService.gI().sendEffectSuccessCombine(player);
                CombineService.gI().baHatMit.npcChat(player, "Chúc mừng con nhé");
            } else if (opt228.param >= 8) {
                Service.gI().sendThongBao(player, "Trang bị đã có lỗ thứ 8.");
                return;
            } else {
                opt228.param = 8;
                if (!hasOption218) {
                    item.itemOptions.add(new ItemOption(218, 0));
                }
                CombineService.gI().sendEffectSuccessCombine(player);
                CombineService.gI().baHatMit.npcChat(player, "Chúc mừng con nhé");
            }

        } else if (star == 9) {
            if (opt228 == null) {
                if (hasOption102With7) {
                    if (!hasOption218) {
                        item.itemOptions.add(new ItemOption(218, 0));
                    }
                    item.itemOptions.add(new ItemOption(228, 8));
                    CombineService.gI().sendEffectSuccessCombine(player);
                    CombineService.gI().baHatMit.npcChat(player, "Chúc mừng con nhé");
                } else {
                 //   Service.gI().sendThongBao(player, "Trang bị cần có lỗ thứ 8 trước khi nâng lên lỗ thứ 9.");
                }
            } else if (opt228.param == 8) {
                if (success) {
                    opt228.param = 9;
                    CombineService.gI().sendEffectSuccessCombine(player);
                    CombineService.gI().baHatMit.npcChat(player, "Chúc mừng con nhé");
                } else {
                    CombineService.gI().sendEffectFailCombine(player);
                }
            } else if (opt228.param == 9) {
                Service.gI().sendThongBao(player, "Không thể cường hóa thêm.");
                return;
            } else {
                Service.gI().sendThongBao(player, "Trang bị không đủ điều kiện để cường hóa lên lỗ tiếp theo.");
                return;
            }

        } else {
            Service.gI().sendThongBao(player, "Chỉ có thể cường hóa khi trang bị đạt 8 hoặc 9 sao.");
            return;
        }

        InventoryService.gI().subQuantityItemsBag(player, hematite, 1);
        InventoryService.gI().subQuantityItemsBag(player, duiDuc, 1);
        Service.gI().sendMoney(player);
        InventoryService.gI().sendItemBags(player);
        CombineService.gI().reOpenItemCombine(player);
        CombineService.gI().sendEffectCombineDB(player, item.template.iconID);
    }

}
