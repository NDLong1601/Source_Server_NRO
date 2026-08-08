package nro.models.combine;

import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import java.util.Objects;
import static nro.models.combine.CombineService.MAX_LEVEL_ITEM;
import nro.models.consts.ConstTaskBadges;
import nro.models.player.Player;
import nro.models.server.ServerNotify;
import nro.models.services.ChatGlobalService;
import nro.models.services.InventoryService;
import nro.models.services.Service;
import nro.models.task.BadgesTaskService;
import nro.models.utils.Util;

/**
 *
 * @author By Mr Blue
 */
public class NangCapVatPham {

    public static void showInfoCombine(Player player) {
        if (player.combineNew.itemsCombine.size() >= 2 && player.combineNew.itemsCombine.size() < 4) {
            if (player.combineNew.itemsCombine.stream().filter(item -> item.isNotNullItem() && item.template.type < 5).count() < 1) {
                CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, "Thiếu đồ nâng cấp", "Đóng");
                return;
            }
            if (player.combineNew.itemsCombine.stream().filter(item -> item.isNotNullItem() && item.template.type == 14).count() < 1) {
                CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, "Thiếu đá nâng cấp", "Đóng");
                return;
            }
            if (player.combineNew.itemsCombine.size() == 3 && player.combineNew.itemsCombine.stream().filter(item -> item.isNotNullItem() && item.template.id == 987).count() < 1) {
                CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, "Thiếu đồ nâng cấp", "Đóng");
                return;
            }
            Item itemDo = null;
            Item itemDNC = null;
            Item itemDBV = null;
            for (int j = 0; j < player.combineNew.itemsCombine.size(); j++) {
                if (player.combineNew.itemsCombine.get(j).isNotNullItem()) {
                    if (player.combineNew.itemsCombine.size() == 3 && player.combineNew.itemsCombine.get(j).template.id == 987) {
                        itemDBV = player.combineNew.itemsCombine.get(j);
                        continue;
                    }
                    if (player.combineNew.itemsCombine.get(j).template.type < 5) {
                        itemDo = player.combineNew.itemsCombine.get(j);
                    } else {
                        itemDNC = player.combineNew.itemsCombine.get(j);
                    }
                }
            }
            if (CombineSystem.isCoupleItemNangCapCheck(itemDo, itemDNC)) {
                int level = 0;
                for (Item.ItemOption io : itemDo.itemOptions) {
                    if (io.optionTemplate.id == 72) {
                        level = io.param;
                        break;
                    }
                }
                if (level < CombineService.MAX_LEVEL_ITEM) {
                    player.combineNew.goldCombine = CombineSystem.getGoldNangCapDo(level);
                    player.combineNew.ratioCombine = (float) CombineSystem.getTileNangCapDo(level);
                    player.combineNew.countDaNangCap = CombineSystem.getCountDaNangCapDo(level);
                    player.combineNew.countDaBaoVe = (short) CombineSystem.getCountDaBaoVe(level);
                    String npcSay = "|2|Hiện tại " + itemDo.template.name + " (+" + level + ")\n|0|";
                    for (Item.ItemOption io : itemDo.itemOptions) {
                        if (io.optionTemplate.id != 72) {
                            npcSay += io.getOptionString() + "\n";
                        }
                    }
                    String option = null;
                    int param = 0;
                    for (Item.ItemOption io : itemDo.itemOptions) {
                        if (io.optionTemplate.id == 47
                                || io.optionTemplate.id == 6
                                || io.optionTemplate.id == 0
                                || io.optionTemplate.id == 7
                                || io.optionTemplate.id == 14
                                || io.optionTemplate.id == 22
                                || io.optionTemplate.id == 23) {
                            option = io.optionTemplate.name;
                            param = io.param + (io.param * 10 / 100);
                            break;
                        }
                    }
                    npcSay += "|2|Sau khi nâng cấp (+" + (level + 1) + ")\n|7|"
                            + option.replaceAll("#", String.valueOf(param))
                            + "\n|7|Tỉ lệ thành công: " + player.combineNew.ratioCombine + "%\n"
                            + (player.combineNew.countDaNangCap > itemDNC.quantity ? "|7|" : "|1|")
                            + "Cần " + player.combineNew.countDaNangCap + " " + itemDNC.template.name
                            + "\n" + (player.combineNew.goldCombine > player.inventory.gold ? "|7|" : "|1|")
                            + "Cần " + Util.numberToMoney(player.combineNew.goldCombine) + " vàng";

                    String daNPC = player.combineNew.itemsCombine.size() == 3 && itemDBV != null ? String.format("\nCần tốn %s đá bảo vệ", player.combineNew.countDaBaoVe) : "";
                    if ((level == 2 || level == 4 || level == 6) && !(player.combineNew.itemsCombine.size() == 3 && itemDBV != null)) {
                        npcSay += "\nNếu thất bại sẽ rớt xuống (+" + (level - 1) + ")";
                    }
                    if (player.combineNew.countDaNangCap > itemDNC.quantity) {
                        CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU,
                                npcSay, "Còn thiếu\n" + (player.combineNew.countDaNangCap - itemDNC.quantity) + " " + itemDNC.template.name);
                    } else if (player.combineNew.goldCombine > player.inventory.gold) {
                        CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU,
                                npcSay, "Còn thiếu\n" + Util.numberToMoney((player.combineNew.goldCombine - player.inventory.gold)) + " vàng");
                    } else if (player.combineNew.itemsCombine.size() == 3 && Objects.nonNull(itemDBV) && itemDBV.quantity < player.combineNew.countDaBaoVe) {
                        CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU,
                                npcSay, "Còn thiếu\n" + (player.combineNew.countDaBaoVe - itemDBV.quantity) + " đá bảo vệ");
                    } else {
                        CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.MENU_START_COMBINE,
                                npcSay, "Nâng cấp\n" + Util.numberToMoney(player.combineNew.goldCombine) + " vàng" + daNPC, "Từ chối");
                    }
                } else {
                    CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, "Trang bị của ngươi đã đạt cấp tối đa", "Đóng");
                }
            } else {
                CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, "Hãy chọn 1 trang bị và 1 loại đá nâng cấp", "Đóng");
            }
        } else {
            if (player.combineNew.itemsCombine.size() > 3) {
                CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, "Cất đi con ta không thèm", "Đóng");
                return;
            }
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, "Hãy chọn 1 trang bị và 1 loại đá nâng cấp", "Đóng");
        }
    }

    public static void nangCapVatPham(Player player) {
        if (player.combineNew.itemsCombine.size() >= 2 && player.combineNew.itemsCombine.size() < 4) {
            if (player.combineNew.itemsCombine.stream().filter(item -> item.isNotNullItem() && item.template.type < 5).count() != 1) {
                return;
            }
            if (player.combineNew.itemsCombine.stream().filter(item -> item.isNotNullItem() && item.template.type == 14).count() != 1) {
                return;
            }
            if (player.combineNew.itemsCombine.size() == 3 && player.combineNew.itemsCombine.stream().filter(item -> item.isNotNullItem() && item.template.id == 987).count() != 1) {
                return;
            }
            Item itemDo = null;
            Item itemDNC = null;
            Item itemDBV = null;
            for (int j = 0; j < player.combineNew.itemsCombine.size(); j++) {
                if (player.combineNew.itemsCombine.get(j).isNotNullItem()) {
                    if (player.combineNew.itemsCombine.size() == 3 && player.combineNew.itemsCombine.get(j).template.id == 987) {
                        itemDBV = player.combineNew.itemsCombine.get(j);
                        continue;
                    }
                    if (player.combineNew.itemsCombine.get(j).template.type < 5) {
                        itemDo = player.combineNew.itemsCombine.get(j);
                    } else {
                        itemDNC = player.combineNew.itemsCombine.get(j);
                    }
                }
            }
            if (CombineSystem.isCoupleItemNangCapCheck(itemDo, itemDNC)) {
                int countDaNangCap = player.combineNew.countDaNangCap;
                int gold = player.combineNew.goldCombine;
                short countDaBaoVe = player.combineNew.countDaBaoVe;
                if (player.inventory.gold < gold) {
                    Service.gI().sendThongBao(player, "Không đủ vàng để thực hiện");
                    return;
                }

                if (itemDNC.quantity < countDaNangCap) {
                    return;
                }
                if (player.combineNew.itemsCombine.size() == 3) {
                    if (Objects.isNull(itemDBV)) {
                        return;
                    }
                    if (itemDBV.quantity < countDaBaoVe) {
                        return;
                    }
                }

                int level = 0;
                Item.ItemOption optionLevel = null;
                for (Item.ItemOption io : itemDo.itemOptions) {
                    if (io.optionTemplate.id == 72) {
                        level = io.param;
                        optionLevel = io;
                        break;
                    }
                }
                if (level < MAX_LEVEL_ITEM) {
                    player.inventory.gold -= gold;
                    Item.ItemOption option = null;
                    Item.ItemOption option2 = null;
                    for (Item.ItemOption io : itemDo.itemOptions) {
                        if (io.optionTemplate.id == 47
                                || io.optionTemplate.id == 6
                                || io.optionTemplate.id == 0
                                || io.optionTemplate.id == 7
                                || io.optionTemplate.id == 14
                                || io.optionTemplate.id == 22
                                || io.optionTemplate.id == 23) {
                            option = io;
                        } else if (io.optionTemplate.id == 27
                                || io.optionTemplate.id == 28) {
                            option2 = io;
                        }
                    }
                    if (Util.isTrue(player.combineNew.ratioCombine, 100)) {
                        option.param += (option.param * 10 / 100) < 1 ? 1 : (option.param * 10 / 100);
                        if (option2 != null) {
                            option2.param += (option2.param * 10 / 100) < 1 ? 1 : (option2.param * 10 / 100);
                        }
                        if (optionLevel == null) {
                            itemDo.itemOptions.add(new Item.ItemOption(72, 1));
                        } else {
                            optionLevel.param++;
                        }
                        if (optionLevel != null && optionLevel.param >= 5) {
                         //   ChatGlobalService.gI().ThongBaoRoiDo(player, "Chúc mừng " + player.name + " vừa nâng cấp " + "thành công " + itemDo.template.name + " lên +" + optionLevel.param);
                        }
                        CombineService.gI().sendEffectSuccessCombine(player);
                        CombineService.gI().baHatMit.npcChat(player, "Chúc mừng con nhé");
                        if (level == 7) {
                            BadgesTaskService.updateCountBagesTask(player, ConstTaskBadges.THANH_DAP_DO_7, 1);
                        }
                    } else {
                        if ((level == 2 || level == 4 || level == 6) && (player.combineNew.itemsCombine.size() != 3)) {
                            option.param -= (option.param * 11 / 100) < 1 ? 1 : (option.param * 11 / 100);
                            if (option2 != null) {
                                option2.param -= (option2.param * 11 / 100) < 1 ? 1 : (option2.param * 11 / 100);
                            }
                            optionLevel.param--;

                            Item.ItemOption downgradeOption = itemDo.itemOptions.stream()
                                    .filter(io -> io.optionTemplate.id == 209)
                                    .findFirst()
                                    .orElse(null);

                            if (downgradeOption == null) {
                                itemDo.itemOptions.add(new Item.ItemOption(209, 1));
                            } else {
                                downgradeOption.param++;
                            }
                        }
                        CombineService.gI().sendEffectFailCombine(player);
                        String[] failMessages = {
                            "Lần này xui thôi, đập tiếp đi!",
                            "Càng cay, càng phải đập!",
                            "Bình tĩnh, chưa lên thì mai lên!",
                            "Thất bại là mẹ thành công!",
                            "Lại xịt, nhân phẩm đang ngủ à?",
                            "Đập thế này thì phá sản sớm thôi!",
                            "Ai đó gọi thầy độ chưa?"
                        };
                        String msg = failMessages[Util.nextInt(failMessages.length)];
                        CombineService.gI().baHatMit.npcChat(player, msg);
                    }
                    if (player.combineNew.itemsCombine.size() == 3) {
                        InventoryService.gI().subQuantityItemsBag(player, itemDBV, countDaBaoVe);
                    }
                    InventoryService.gI().subQuantityItemsBag(player, itemDNC, player.combineNew.countDaNangCap);
                    InventoryService.gI().sendItemBags(player);
                    Service.gI().sendMoney(player);
                    CombineService.gI().reOpenItemCombine(player);
                    player.combineNew.itemsCombine.clear();
                }
            }
        }
    }

}
