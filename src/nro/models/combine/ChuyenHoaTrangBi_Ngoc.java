package nro.models.combine;

import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import nro.models.item.Item.ItemOption;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.Service;

/**
 *
 * @author By Mr Blue
 */
public class ChuyenHoaTrangBi_Ngoc {

    public static void chuyenHoaTrangBiNgoc(Player player) {
        if (player.combineNew.itemsCombine.size() != 2) {
            Service.gI().sendThongBaoOK(player, "Cần 1 trang bị có cấp từ [+4] và 1 trang bị không có cấp nhưng cao hơn 1 bậc");
            return;
        }

        Item trangBiGoc = player.combineNew.itemsCombine.get(0);
        Item trangBiCanChuyenHoa = player.combineNew.itemsCombine.get(1);
        int goldChuyenHoa = 2_000_000_000;

        int levelTrangBi = 0;
        int soLanRotCap = 0;

        for (ItemOption io : trangBiGoc.itemOptions) {
            if (io.optionTemplate.id == 72) {
                levelTrangBi = io.param;
            } else if (io.optionTemplate.id == 209) {
                soLanRotCap += io.param;
            }
        }

        if (!isTrangBiGoc(trangBiGoc)) {
            Service.gI().sendThongBaoOK(player, "Trang bị phải từ bậc lưỡng long, Jean hoặc Zelot trở lên");
            return;
        }

        if (levelTrangBi < 4) {
            Service.gI().sendThongBaoOK(player, "Trang bị gốc có cấp từ [+4]");
            return;
        }

        if (!isTrangBiChuyenHoa(trangBiCanChuyenHoa)) {
            Service.gI().sendThongBaoOK(player, "Trang bị cần chuyển hóa phải là Thần Xayda, Thần Trái Đất hoặc Thần Namek");
            return;
        }

        boolean daNangCapHoacPhaLe = trangBiCanChuyenHoa.itemOptions.stream()
                .anyMatch(io -> io.optionTemplate.id == 72 || io.optionTemplate.id == 102);
        if (daNangCapHoacPhaLe) {
            Service.gI().sendThongBaoOK(player, "Trang bị cần chuyển hóa phải chưa nâng cấp và chưa pha lê hóa");
            return;
        }

        if (!isCheckTrungTypevsGender(trangBiGoc, trangBiCanChuyenHoa)) {
            Service.gI().sendThongBaoOK(player, "Trang bị gốc và Trang bị nhập thể phải cùng loại và hành tinh");
            return;
        }

        int chisogoc = trangBiCanChuyenHoa.itemOptions.get(0).param;
        chisogoc = (int) (chisogoc * Math.pow(1.1, levelTrangBi) * Math.pow(0.9, soLanRotCap));

        StringBuilder npcSay = new StringBuilder("|2|Hiện tại " + trangBiCanChuyenHoa.getName() + "\n");
        for (ItemOption io : trangBiCanChuyenHoa.itemOptions) {
            if (io.optionTemplate.id != 72) {
                npcSay.append("|0|").append(io.getOptionString()).append("\n");
            }
        }

        npcSay.append("|2|Sau khi nâng cấp (+" + levelTrangBi + ")\n");
        for (ItemOption io : trangBiCanChuyenHoa.itemOptions) {
            if (isBaseStatOption(io.optionTemplate.id)) {
                npcSay.append("|1|").append(io.getOptionString(chisogoc)).append("\n");
            } else if (io.optionTemplate.id != 72) {
                npcSay.append("|1|").append(io.getOptionString()).append("\n");
            }
        }

        for (ItemOption io : trangBiGoc.itemOptions) {
            int id = io.optionTemplate.id;
            if (!isIgnoredOption(id) && !isBaseStatOption(id)) {
                npcSay.append(io.getOptionString()).append("\n");
            }
        }

        npcSay.append("Chuyển qua tất cả sao pha lê\n");
        npcSay.append("|2|Cần 5000 ngọc");

        CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.MENU_START_COMBINE,
                npcSay.toString(), "Chuyển hóa", "Từ chối");
    }

    public static void thucHienChuyenHoa(Player player) {
        Item trangBiGoc = player.combineNew.itemsCombine.get(0);
        Item trangBiCanChuyenHoa = player.combineNew.itemsCombine.get(1);
        int goldChuyenHoa = 2_000_000_000;

        int levelTrangBi = 0;
        int soLanRotCap = 0;

        for (ItemOption io : trangBiGoc.itemOptions) {
            if (io.optionTemplate.id == 72) {
                levelTrangBi = io.param;
            } else if (io.optionTemplate.id == 209) {
                soLanRotCap += io.param;
            }
        }

        int chisogoc = trangBiCanChuyenHoa.itemOptions.get(0).param;
        chisogoc = (int) (chisogoc * Math.pow(1.1, levelTrangBi) * Math.pow(0.9, soLanRotCap));
        Item newItem = ItemService.gI().createNewItem(trangBiCanChuyenHoa.template.id);
        for (ItemOption io : trangBiCanChuyenHoa.itemOptions) {
            int id = io.optionTemplate.id;
            if (!isIgnoredOption(id)) {
                int value = isBaseStatOption(id) ? chisogoc : io.param;
                newItem.itemOptions.add(new ItemOption(io.optionTemplate, value));
            }
        }

        for (ItemOption io : trangBiGoc.itemOptions) {
            int id = io.optionTemplate.id;
            if (!isIgnoredOption(id) && !isBaseStatOption(id)) {
                if (id == 72) {
                    newItem.itemOptions.add(new ItemOption(io.optionTemplate, io.param));
                } else {
                    newItem.itemOptions.add(new ItemOption(io.optionTemplate, io.param));
                }
            }
        }

        player.inventory.gold -= goldChuyenHoa;
        Service.gI().sendMoney(player);
        InventoryService.gI().addItemBag(player, newItem);
        InventoryService.gI().subQuantityItemsBag(player, trangBiGoc, 1);
        InventoryService.gI().subQuantityItemsBag(player, trangBiCanChuyenHoa, 1);
        InventoryService.gI().sendItemBags(player);
        CombineService.gI().reOpenItemCombine(player);
        CombineService.gI().sendEffectSuccessCombine(player);
    }

    private boolean shouldSkipOption(ItemOption io) {
        return io.optionTemplate.id >= 136 && io.optionTemplate.id <= 144
                || io.optionTemplate.id >= 224 && io.optionTemplate.id <= 227
                || io.optionTemplate.id >= 127 && io.optionTemplate.id <= 132
                || io.optionTemplate.id == 6 || io.optionTemplate.id == 7 || io.optionTemplate.id == 9;
    }

    private static boolean isCheckTrungTypevsGender(Item item, Item item2) {
        return item.template.type == item2.template.type && item.template.gender == item2.template.gender;
    }

    private static boolean isTrangBiGoc(Item item) {
        return isDoLuongLong(item) || isDoJean(item) || isDoZelot(item);
    }

    private static boolean isTrangBiChuyenHoa(Item item) {
        return isDoThanXD(item) || isDoThanTD(item) || isDoThanNM(item);
    }

    private static boolean isDoLuongLong(Item item) {
        return item != null && item.isNotNullItem()
                && (item.template.id == 241 || item.template.id == 253 || item.template.id == 265
                || item.template.id == 277 || item.template.id == 281);
    }

    private static boolean isDoJean(Item item) {
        return item != null && item.isNotNullItem()
                && (item.template.id == 237 || item.template.id == 249 || item.template.id == 261 || item.template.id == 273);
    }

    private static boolean isDoZelot(Item item) {
        return item != null && item.isNotNullItem()
                && (item.template.id == 233 || item.template.id == 245 || item.template.id == 257 || item.template.id == 269);
    }

    private static boolean isDoThanXD(Item item) {
        return item.template.id >= 555 && item.template.id <= 557;
    }

    private static boolean isDoThanTD(Item item) {
        return item.template.id >= 558 && item.template.id <= 560;
    }

    private static boolean isDoThanNM(Item item) {
        return item.template.id >= 561 && item.template.id <= 563;
    }

    private static boolean isBaseStatOption(int id) {
        return id == 0 || id == 6 || id == 7 || id == 14 || id == 22 || id == 23 || id == 47;
    }

    private static boolean isIgnoredOption(int id) {
        return (id == 236 || id == 228
                || (id >= 127 && id <= 135) || (id >= 136 && id <= 144)
                || (id >= 233 && id <= 248) || (id >= 136 && id <= 144)
                || (id >= 210 && id <= 218) || (id >= 224 && id <= 227));
    }
}
