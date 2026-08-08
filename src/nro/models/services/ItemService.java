package nro.models.services;

import nro.models.combine.CombineService;
import nro.models.player_system.Template;
import nro.models.player_system.Template.ItemOptionTemplate;
import nro.models.item.Item;
import nro.models.map.ItemMap;
import nro.models.player.Player;
import nro.models.shop.ItemShop;
import nro.models.server.Manager;
import nro.models.utils.TimeUtil;
import nro.models.utils.Util;
import nro.models.item.Item.ItemOption;
import java.util.*;
import java.util.stream.Collectors;
import nro.models.map.Zone;

/**
 *
 * @author By Mr Blue
 *
 */
public class ItemService {

    private static ItemService i;

    public static ItemService gI() {
        if (i == null) {
            i = new ItemService();
        }
        return i;
    }

    public short getItemIdByIcon(short IconID) {
        for (int i = 0; i < Manager.ITEM_TEMPLATES.size(); i++) {
            if (Manager.ITEM_TEMPLATES.get(i).iconID == IconID) {
                return Manager.ITEM_TEMPLATES.get(i).id;
            }
        }
        return -1;
    }

    public Item createItemNull() {
        Item item = new Item();
        return item;
    }

    public Item createItemFromItemShop(ItemShop itemShop) {
        Item item = new Item();
        item.template = itemShop.temp;
        item.quantity = 1;
        item.content = item.getContent();
        item.info = item.getInfo();
        for (Item.ItemOption io : itemShop.options) {
            item.itemOptions.add(new Item.ItemOption(io));
        }
        return item;
    }

    public Item copyItem(Item item) {
        Item it = new Item();
        it.itemOptions = new ArrayList<>();
        it.template = item.template;
        it.info = item.info;
        it.content = item.content;
        it.quantity = item.quantity;
        it.createTime = item.createTime;
        for (Item.ItemOption io : item.itemOptions) {
            it.itemOptions.add(new Item.ItemOption(io));
        }
        return it;
    }

    public Item createNewItem(short tempId) {
        return createNewItem(tempId, 1);
    }

    public Item createNewItem(short tempId, int quantity) {
        Item item = new Item();
        item.template = getTemplate(tempId);
        item.quantity = quantity;
        item.createTime = System.currentTimeMillis();

        item.content = item.getContent();
        item.info = item.getInfo();
        return item;
    }

    public Item otpts(short tempId, int quantity) {
        Item item = new Item();
        item.template = getTemplate(tempId);
        item.quantity = quantity;
        item.createTime = System.currentTimeMillis();
        if (item.template.type == 0) {
            item.itemOptions.add(new ItemOption(21, 80));
            item.itemOptions.add(new ItemOption(47, Util.nextInt(2000, 2500)));
        }
        if (item.template.type == 1) {
            item.itemOptions.add(new ItemOption(21, 80));
            item.itemOptions.add(new ItemOption(22, Util.nextInt(150, 200)));
        }
        if (item.template.type == 2) {
            item.itemOptions.add(new ItemOption(21, 80));
            item.itemOptions.add(new ItemOption(0, Util.nextInt(18000, 20000)));
        }
        if (item.template.type == 3) {
            item.itemOptions.add(new ItemOption(21, 80));
            item.itemOptions.add(new ItemOption(23, Util.nextInt(150, 200)));
        }
        if (item.template.type == 4) {
            item.itemOptions.add(new ItemOption(21, 80));
            item.itemOptions.add(new ItemOption(14, Util.nextInt(20, 25)));
        }
        item.content = item.getContent();
        item.info = item.getInfo();
        return item;
    }

    public Item createItemSetKichHoat(int tempId, int quantity) {
        Item item = new Item();
        item.template = getTemplate(tempId);
        item.quantity = quantity;
        item.itemOptions = createItemNull().itemOptions;
        item.createTime = System.currentTimeMillis();
        item.content = item.getContent();
        item.info = item.getInfo();
        return item;
    }

    public void OpenSKH(Player player, int itemUseId, int select) throws Exception {
        if (select < 0 || select > 4) {
            return;
        }
        if (InventoryService.gI().getCountEmptyBag(player) <= 0) {
            Service.gI().sendThongBao(player, "Bạn phải có ít nhất 1 ô trống trong hành trang!");
            return;
        }

        Item itemUse = InventoryService.gI().findItem(player.inventory.itemsBag, itemUseId);
        if (itemUse == null) {
            return;
        }

        int gender = player.gender;
        if (gender > 2) {
            gender = 2;
        }
        int[][][] itemIds = {
            { // Trái đất
                {0, 3, 33, 34, 136, 137, 138, 139, 230, 231, 232, 233}, // Áo
                {6, 9, 35, 36, 140, 141, 142, 143, 242, 243, 244, 245}, // Quần
                {21, 24, 37, 38, 144, 145, 146, 147, 254, 256, 257}, // Găng
                {27, 30, 39, 40, 148, 149, 150, 151, 266, 267, 268, 269}, // Giày
                {12, 57, 58, 59, 184, 185, 186, 187, 278, 279, 280, 281} // Rada
            },
            { // Namek
                {1, 4, 41, 42, 152, 153, 154, 155, 235, 236, 237}, // Áo
                {7, 10, 43, 44, 156, 157, 158, 159, 246, 247, 248, 249}, // Quần
                {22, 25, 45, 46, 160, 161, 162, 163, 259, 260, 261}, // Găng
                {28, 31, 47, 48, 164, 165, 166, 167, 270, 271, 272, 273}, // Giày
                {12, 57, 58, 59, 184, 185, 186, 187, 278, 279, 280, 281} // Rada
            },
            { // Xayda
                {2, 5, 49, 50, 168, 169, 170, 171, 238, 239, 240, 241}, // Áo
                {8, 11, 51, 52, 172, 173, 174, 174, 250, 251, 252, 253}, // Quần
                {23, 26, 53, 54, 176, 177, 178, 179, 262, 263, 264, 265}, // Găng
                {29, 32, 55, 56, 180, 181, 182, 183, 274, 275, 276, 277}, // Giày
                {12, 57, 58, 59, 184, 185, 186, 187, 278, 279, 280, 281} // Rada
            }
        };

        int[][] optionIds = {
            {128, 129, 127, 233, 245}, // Trái đất
            {130, 131, 132, 233, 237}, // Namek
            {133, 135, 134, 233, 241} // Xayda
        };

        int[] selectedItemIds = itemIds[gender][select];
        int itemId = selectedItemIds[Util.nextInt(selectedItemIds.length)];
        int[] possibleOptions = optionIds[gender];
        int optionRandom = possibleOptions[Util.nextInt(possibleOptions.length)];

        Item item = createItemSKH(itemId, optionRandom);
        if (item != null) {
            InventoryService.gI().addItemBag(player, item);
            InventoryService.gI().sendItemBags(player);
            Service.gI().sendThongBao(player, "Bạn đã nhận được " + item.template.name);
            InventoryService.gI().subQuantityItemsBag(player, itemUse, 1);
            InventoryService.gI().sendItemBags(player);
        }
    }

    public void OpenVeTangNgoc(Player player, int itemUseId, int select) throws Exception {
        if (select < 0 || select > 4) {
            return;
        }
        if (InventoryService.gI().getCountEmptyBag(player) <= 0) {
            Service.gI().sendThongBao(player, "Bạn phải có ít nhất 1 ô trống trong hành trang!");
            return;
        }

        Item itemUse = InventoryService.gI().findItem(player.inventory.itemsBag, itemUseId);
        if (itemUse == null) {
            return;
        }

        int gender = player.gender;
        if (gender > 2) {
            gender = 2;
        }
        int[][][] itemIds = {
            { // Trái đất
                {0, 3, 33, 34, 136, 137, 138, 139, 230, 231, 232, 233}, // Áo
                {6, 9, 35, 36, 140, 141, 142, 143, 242, 243, 244, 245}, // Quần
                {21, 24, 37, 38, 144, 145, 146, 147, 254, 256, 257}, // Găng
                {27, 30, 39, 40, 148, 149, 150, 151, 266, 267, 268, 269}, // Giày
                {12, 57, 58, 59, 184, 185, 186, 187, 278, 279, 280, 281} // Rada
            },
            { // Namek
                {1, 4, 41, 42, 152, 153, 154, 155, 235, 236, 237}, // Áo
                {7, 10, 43, 44, 156, 157, 158, 159, 246, 247, 248, 249}, // Quần
                {22, 25, 45, 46, 160, 161, 162, 163, 259, 260, 261}, // Găng
                {28, 31, 47, 48, 164, 165, 166, 167, 270, 271, 272, 273}, // Giày
                {12, 57, 58, 59, 184, 185, 186, 187, 278, 279, 280, 281} // Rada
            },
            { // Xayda
                {2, 5, 49, 50, 168, 169, 170, 171, 238, 239, 240, 241}, // Áo
                {8, 11, 51, 52, 172, 173, 174, 174, 250, 251, 252, 253}, // Quần
                {23, 26, 53, 54, 176, 177, 178, 179, 262, 263, 264, 265}, // Găng
                {29, 32, 55, 56, 180, 181, 182, 183, 274, 275, 276, 277}, // Giày
                {12, 57, 58, 59, 184, 185, 186, 187, 278, 279, 280, 281} // Rada
            }
        };

        int[][] optionIds = {
            {128, 129, 127, 233, 245}, // Trái đất
            {130, 131, 132, 233, 237}, // Namek
            {133, 135, 134, 233, 241} // Xayda
        };

        int[] selectedItemIds = itemIds[gender][select];
        int itemId = selectedItemIds[Util.nextInt(selectedItemIds.length)];
        int[] possibleOptions = optionIds[gender];
        int optionRandom = possibleOptions[Util.nextInt(possibleOptions.length)];

        Item item = createItemSKH(itemId, optionRandom);
        if (item != null) {
            InventoryService.gI().addItemBag(player, item);
            InventoryService.gI().sendItemBags(player);
            Service.gI().sendThongBao(player, "Bạn đã nhận được " + item.template.name);
            InventoryService.gI().subQuantityItemsBag(player, itemUse, 1);
            InventoryService.gI().sendItemBags(player);
        }
    }

    public Item createItemSKH(int itemId, int skhId) {
        Item item = createItemSetKichHoat(itemId, 1);
        if (item != null) {
            item.itemOptions.addAll(ItemService.gI().getListOptionItemShop((short) itemId));
            item.itemOptions.add(new Item.ItemOption(skhId, 1));
            for (int subId : getOptionIdsBySKH(skhId)) {
                item.itemOptions.add(new Item.ItemOption(subId, 1));
            }
            item.itemOptions.add(new Item.ItemOption(30, 1));
        }
        return item;
    }

    public int optionItemSKH(int typeItem) {
        switch (typeItem) {
            case 0:
                return 47;
            case 1:
                return 6;
            case 2:
                return 0;
            case 3:
                return 7;
            default:
                return 14;
        }
    }

    public int pagramItemSKH(int typeItem) {
        switch (typeItem) {
            case 0:
            case 2:
                return Util.nextInt(5);
            case 1:
            case 3:
                return Util.nextInt(20, 30);
            default:
                return Util.nextInt(3);
        }
    }

    public int[] getOptionIdsBySKH(int skhId) {
        switch (skhId) {
            case 127:
                return new int[]{140};
            case 128:
                return new int[]{139};
            case 129:
                return new int[]{141};
            case 130:
                return new int[]{142};
            case 131:
                return new int[]{143};
            case 132:
                return new int[]{144};
            case 133:
                return new int[]{136};
            case 134:
                return new int[]{137};
            case 135:
                return new int[]{138};
            case 233:
                return new int[]{234};
            case 237:
                return new int[]{238, 239, 240};
            case 241:
                return new int[]{242, 243, 244};
            case 245:
                return new int[]{246, 247, 248};
            default:
                return new int[]{};
        }
    }

    public int randomSKHId(byte gender) {
        if (gender == 3) {
            gender = 2;
        }
        int[][] options = {{128, 129, 127, 233, 245}, {130, 131, 132, 233, 237}, {133, 135, 134, 233, 241}};
        int skhv1 = 25;
        int skhv2 = 35;
        int skhc = 40;
        int skhId = -1;
        int rd = Util.nextInt(1, 100);
        if (rd <= skhv1) {
            skhId = 0;
        } else if (rd <= skhv1 + skhv2) {
            skhId = 1;
        } else if (rd <= skhv1 + skhv2 + skhc) {
            skhId = 2;
        }
        return options[gender][skhId];
    }

    public Item createItemFromItemMap(ItemMap itemMap) {
        Item item = createNewItem(itemMap.itemTemplate.id, itemMap.quantity);
        item.itemOptions = itemMap.options;
        return item;
    }

    public ItemOptionTemplate getItemOptionTemplate(int id) {
        return Manager.ITEM_OPTION_TEMPLATES.get(id);
    }

    public Template.ItemTemplate getTemplate(int id) {
        return Manager.ITEM_TEMPLATES.get(id);
    }

    public int getPercentTrainArmor(Item item) {
        if (item != null) {
            switch (item.template.id) {
                case 529:
                case 534:
                    return 10;
                case 530:
                case 535:
                    return 20;
                case 531:
                case 536:
                    return 30;
                case 1716:
                    return 40;
                default:
                    return 0;
            }
        } else {
            return 0;
        }
    }

    public boolean isTrainArmor(Item item) {
        if (item != null && item.template != null) {
            switch (item.template.id) {
                case 529:
                case 534:
                case 530:
                case 535:
                case 531:
                case 536:
                case 1716:
                    return true;
                default:
                    return false;
            }
        } else {
            return false;
        }
    }

    public boolean isOutOfDateTime(Item item) {
        if (item != null) {
            for (Item.ItemOption io : item.itemOptions) {
                if (io.optionTemplate.id == 93) {
                    int dayPass = (int) TimeUtil.diffDate(new Date(), new Date(item.createTime), TimeUtil.DAY);
                    if (dayPass != 0) {
                        io.param -= dayPass;
                        if (io.param <= 0) {
                            return true;
                        } else {
                            item.createTime = System.currentTimeMillis();
                        }
                    }
                }
            }
        }
        return false;
    }

    public void OpenItem736(Player player, Item itemUse) {
        try {
            if (InventoryService.gI().getCountEmptyBag(player) <= 1) {
                Service.gI().sendThongBao(player, "Bạn phải có ít nhất 2 ô trống hành trang");
                return;
            }
            short[] icon = new short[2];
            int rd = Util.nextInt(1, 100);
            int rac = 50;
            int ruby = 20;
            int dbv = 10;
            int vb = 10;
            int bh = 5;
            int ct = 5;
            Item item = randomRac();
            if (rd <= rac) {
                item = randomRac();
            } else if (rd <= rac + ruby) {
                item = createItemSetKichHoat(77, 1);
            } else if (rd <= rac + ruby + dbv) {
                item = daBaoVe();
            } else if (rd <= rac + ruby + dbv + vb) {
                item = vanBay2011(true);
            } else if (rd <= rac + ruby + dbv + vb + bh) {
                item = phuKien2011(true);
            } else if (rd <= rac + ruby + dbv + vb + bh + ct) {
                item = caitrang2011(true);
            }
            if (item.template.id == 77) {
                item.quantity = Util.nextInt(1, 2);
            }
            icon[0] = itemUse.template.iconID;
            icon[1] = item.template.iconID;
            InventoryService.gI().subQuantityItemsBag(player, itemUse, 1);
            InventoryService.gI().addItemBag(player, item);
            InventoryService.gI().sendItemBags(player);
            player.inventory.event++;
            Service.gI().sendThongBao(player, "Bạn đã nhận được " + item.template.name);
            CombineService.gI().sendEffectOpenItem(player, icon[0], icon[1]);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void OpenItem648(Player player, Item itemUse) {
        try {
            if (InventoryService.gI().getCountEmptyBag(player) <= 1) {
                Service.gI().sendThongBao(player, "Bạn phải có ít nhất 2 ô trống hành trang");
                return;
            }
            short[] icon = new short[2];
            int rd = Util.nextInt(1, 100);
            int rac = 50;
            int ruby = 20;
            int dbv = 10;
            int vb = 10;
            int bh = 5;
            int ct = 5;
            Item item = randomRac();
            if (rd <= rac) {
                item = randomRac2();
            } else if (rd <= rac + ruby) {
                item = createItemSetKichHoat(77, 1);
            } else if (rd <= rac + ruby + dbv) {
                item = vatphamsk(true);
            } else if (rd <= rac + ruby + dbv + vb) {
                item = vanBayChrimas(true);
            } else if (rd <= rac + ruby + dbv + vb + bh) {
                item = phuKienChristmas(true);
            } else if (rd <= rac + ruby + dbv + vb + bh + ct) {
                item = caitrangChristmas(true);
            }
            if (item.template.id == 77) {
                item.quantity = Util.nextInt(1, 2);
            }
            icon[0] = itemUse.template.iconID;
            icon[1] = item.template.iconID;
            InventoryService.gI().subQuantityItemsBag(player, itemUse, 1);
            InventoryService.gI().addItemBag(player, item);
            InventoryService.gI().sendItemBags(player);
            player.inventory.event++;
            Service.gI().sendThongBao(player, "Bạn đã nhận được " + item.template.name);
            CombineService.gI().sendEffectOpenItem(player, icon[0], icon[1]);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //Cải trang sự kiện 20/11
    public Item caitrang2011(boolean rating) {
        Item item = createItemSetKichHoat(680, 1);
        item.itemOptions.add(new Item.ItemOption(76, 1));//VIP
        item.itemOptions.add(new Item.ItemOption(77, 24));//hp 28%
        item.itemOptions.add(new Item.ItemOption(103, 25));//ki 25%
        item.itemOptions.add(new Item.ItemOption(147, 24));//sd 26%
        if (Util.isTrue(995, 1000) && rating) {// tỉ lệ ra hsd
            item.itemOptions.add(new Item.ItemOption(93, new Random().nextInt(3) + 1));//hsd
        }
        return item;
    }

    //Cải trang sự kiện giáng sinh
    public Item caitrangChristmas(boolean rating) {
        Item item = createItemSetKichHoat(Util.nextInt(386, 394), 1);
        item.itemOptions.add(new Item.ItemOption(77, Util.nextInt(15, 20)));
        item.itemOptions.add(new Item.ItemOption(103, Util.nextInt(15, 20)));
        item.itemOptions.add(new Item.ItemOption(147, Util.nextInt(15, 20)));
        item.itemOptions.add(new Item.ItemOption(95, Util.nextInt(1, 10)));
        item.itemOptions.add(new Item.ItemOption(5, Util.nextInt(1, 30)));
        item.itemOptions.add(new Item.ItemOption(106, 0));//sd 26%
        if (Util.isTrue(995, 1000) && rating) {// tỉ lệ ra hsd
            item.itemOptions.add(new Item.ItemOption(93, new Random().nextInt(3) + 1));//hsd
        }
        return item;
    }

    //610 - bong hoa
    //Phụ kiện bó hoa 20/11
    public Item phuKien2011(boolean rating) {
        Item item = createItemSetKichHoat(954, 1);
        item.itemOptions.add(new Item.ItemOption(77, new Random().nextInt(5) + 5));
        item.itemOptions.add(new Item.ItemOption(103, new Random().nextInt(5) + 5));
        item.itemOptions.add(new Item.ItemOption(147, new Random().nextInt(5) + 5));
        if (Util.isTrue(1, 100)) {
            item.itemOptions.get(Util.nextInt(item.itemOptions.size() - 1)).param = 10;
        }
        item.itemOptions.add(new Item.ItemOption(30, 1));//ko the gd
        if (Util.isTrue(995, 1000) && rating) {// tỉ lệ ra hsd
            item.itemOptions.add(new Item.ItemOption(93, new Random().nextInt(3) + 1));//hsd
        }
        return item;
    }

    public Item phuKienChristmas(boolean rating) {
        Item item = createItemSetKichHoat(745, 1);
        item.itemOptions.add(new Item.ItemOption(77, new Random().nextInt(5) + 5));
        item.itemOptions.add(new Item.ItemOption(103, new Random().nextInt(5) + 5));
        item.itemOptions.add(new Item.ItemOption(147, new Random().nextInt(5) + 5));
        if (Util.isTrue(1, 100)) {
            item.itemOptions.get(Util.nextInt(item.itemOptions.size() - 1)).param = 10;
        }
        item.itemOptions.add(new Item.ItemOption(30, 1));//ko the gd
        if (Util.isTrue(995, 1000) && rating) {// tỉ lệ ra hsd
            item.itemOptions.add(new Item.ItemOption(93, new Random().nextInt(3) + 1));//hsd
        }
        return item;
    }

    public Item vanBay2011(boolean rating) {
        Item item = createItemSetKichHoat(795, 1);
        item.itemOptions.add(new Item.ItemOption(89, 1));
        item.itemOptions.add(new Item.ItemOption(30, 1));//ko the gd
        if (Util.isTrue(950, 1000) && rating) {// tỉ lệ ra hsd
            item.itemOptions.add(new Item.ItemOption(93, new Random().nextInt(3) + 1));//hsd
        }
        return item;
    }

    public Item daBaoVe() {
        Item item = createItemSetKichHoat(987, 1);
        item.itemOptions.add(new Item.ItemOption(30, 1));//ko the gd
        return item;
    }

    public Item randomRac() {
        short[] racs = {20, 19, 18, 17};
        Item item = createItemSetKichHoat(racs[Util.nextInt(racs.length - 1)], 1);
        if (optionRac(item.template.id) != 0) {
            item.itemOptions.add(new Item.ItemOption(optionRac(item.template.id), 1));
        }
        return item;
    }

    public Item randomRac2() {
        short[] racs = {585, 704, 2048, 379, 384, 385, 381, 828, 829, 830, 831, 832, 833, 834, 835, 836, 837, 838, 839, 840, 841, 842, 934, 935};
        int idItem = racs[Util.nextInt(racs.length - 1)];
        if (Util.isTrue(1, 100)) {
            idItem = 956;
        }
        Item item = createItemSetKichHoat(idItem, 1);
        if (optionRac(item.template.id) != 0) {
            item.itemOptions.add(new Item.ItemOption(optionRac(item.template.id), 1));
        }
        return item;
    }

    public Item vanBayChrimas(boolean rating) {
        Item item = createItemSetKichHoat(746, 1);
        item.itemOptions.add(new Item.ItemOption(89, 1));
        item.itemOptions.add(new Item.ItemOption(30, 1));//ko the gd
        if (Util.isTrue(950, 1000) && rating) {// tỉ lệ ra hsd
            item.itemOptions.add(new Item.ItemOption(93, new Random().nextInt(3) + 1));//hsd
        }
        return item;
    }

    public byte optionRac(short itemId) {
        switch (itemId) {
            case 220:
                return 71;
            case 221:
                return 70;
            case 222:
                return 69;
            case 224:
                return 67;
            case 223:
                return 68;
            default:
                return 0;
        }
    }

    public Item vatphamsk(boolean hsd) {
        int[] itemId = {2025, 2026, 2036, 2037, 2038, 2039, 2040, 2019, 2020, 2021, 2022, 2023, 2024, 954, 955, 952, 953, 924, 860, 742};
        byte[] option = {77, 80, 81, 103, 50, 94, 5};
        byte[] option_v2 = {14, 16, 17, 19, 27, 28, 47, 87}; //77 %hp // 80 //81 //103 //50 //94 //5 % sdcm
        byte optionid = 0;
        byte optionid_v2 = 0;
        byte param = 0;
        Item lt = ItemService.gI().createNewItem((short) itemId[Util.nextInt(itemId.length)]);
        lt.itemOptions.clear();
        optionid = option[Util.nextInt(0, 6)];
        param = (byte) Util.nextInt(5, 15);
        lt.itemOptions.add(new Item.ItemOption(optionid, param));
        if (Util.isTrue(1, 100)) {
            optionid_v2 = option_v2[Util.nextInt(option_v2.length)];
            lt.itemOptions.add(new Item.ItemOption(optionid_v2, param));
        }
        if (Util.isTrue(999, 1000) && hsd) {
            lt.itemOptions.add(new Item.ItemOption(93, Util.nextInt(1, 7)));
        }
        lt.itemOptions.add(new Item.ItemOption(30, 0));
        return lt;
    }

    public List<Item.ItemOption> getListOptionItemShop(short id) {
        List<Item.ItemOption> list = new ArrayList<>();
        Manager.SHOPS.forEach(shop -> shop.tabShops.forEach(tabShop -> tabShop.itemShops.forEach(itemShop -> {
            if (itemShop.temp.id == id && list.isEmpty()) {
                list.addAll(itemShop.options);
            }
        })));
        return list;
    }

    public int randTempItemDoSao(int gender) {
        // Mảng chứa các item theo từng loại (type)
        int[][] ao = {{3, 34, 136, 137, 138, 139}, {4, 42, 152, 153, 154, 155}, {5, 50, 168, 169, 170, 171}};
        int[][] quan = {{9, 36, 140, 141, 142, 143}, {10, 44, 156, 157, 158, 159}, {11, 52, 172, 173, 174, 175}};
        int[][] gang = {{37, 38, 144, 145, 146, 147}, {25, 45, 160, 161, 162, 163}, {26, 54, 176, 177, 178, 179}};
        int[][] giay = {{39, 40, 148, 149, 150, 151}, {31, 48, 164, 165, 166, 167}, {32, 56, 180, 181, 182, 183}};
        int[][] rada = {{58, 59, 184, 185, 186, 187}, {58, 59, 184, 185, 186, 187}, {58, 59, 184, 185, 186, 187}};
        int[][][] item = {ao, gang, quan, giay, rada};

        // Khởi tạo đối tượng Random
        Random random = new Random();

        // Xác định type
        int type;
        if (Util.isTrue(10, 100)) {
            type = 4; // rada
        } else if (Util.isTrue(23, 100)) {
            type = 3; // giay
        } else if (Util.isTrue(23, 100)) {
            type = 1; // ao
        } else if (Util.isTrue(23, 100)) {
            type = 0; // gang
        } else {
            type = 2; // quan
        }

        // Lấy chỉ số ngẫu nhiên từ 0 đến 5 bằng Random
        int index = random.nextInt(6); // Lấy giá trị ngẫu nhiên từ 0 đến 5

        // Trả về phần tử tương ứng
        return item[type][gender][index];
    }

    public int randTempItemKichHoat(int gender) {
        int[][][] items = {{{0, 33}, {1, 41}, {2, 49}}, {{6, 35}, {7, 43}, {8, 51}}, {{27, 30}, {28, 47}, {29, 55}}, {{21, 24}, {22, 46}, {23, 53}}, {{12, 57}, {12, 57}, {12, 57}}};

        int type;
        if (Util.isTrue(10, 100)) {
            type = 4; // rada
        } else if (Util.isTrue(23, 100)) {
            type = 3; // gang
        } else if (Util.isTrue(23, 100)) {
            type = 1; // quan
        } else if (Util.isTrue(23, 100)) {
            type = 0; // ao
        } else {
            type = 2; // giay
        }

        return items[type][gender][Util.nextInt(2)];
    }

    public int randDoSao(int gender) {
        int[][][] items = {
            {{0, 33}, {1, 41}, {2, 49}},
            {{6, 35}, {7, 43}, {8, 51}},
            {{27, 30}, {28, 47}, {29, 55}},
            {{21, 24}, {22, 46}, {23, 53}},
            {{12, 57}, {12, 57}, {12, 57}}
        };

        int rand = Util.nextInt(100);

        int type;
        if (rand < 10) {
            type = 4; // rada (10%)
        } else if (rand < 32) {
            type = 0; // ao (22.5%)
        } else if (rand < 55) {
            type = 1; // quan (22.5%)
        } else if (rand < 77) {
            type = 2; // giày (22.5%)
        } else {
            type = 3; // găng (22.5%)
        }

        return items[type][gender][Util.nextInt(2)];
    }

    public int[] randOptionItemKichHoat(int gender) {
        int op1 = -1;
        int op2 = -1;
        int op3 = -1;
        int op4 = -1;
        if (Util.isTrue(30, 100)) {
            switch (gender) {
                case 0 -> { // td
                    op1 = 245;
                    op2 = 246;
                    op3 = 247;
                    op4 = 248;
                }
                case 1 -> { // nm
                    op1 = 237;
                    op2 = 238;
                    op3 = 239;
                    op4 = 240;
                }
                default -> { // xd
                    op1 = 241;
                    op2 = 242;
                    op3 = 243;
                    op4 = 244;
                }
            }
        } else {
            switch (gender) {
                case 0 -> { // td
                    if (Util.isTrue(50, 100)) {
                        op1 = 128;
                        op2 = 140;
                    } else if (Util.isTrue(50, 100)) {
                        op1 = 127;
                        op2 = 139;
                    } else if (Util.isTrue(50, 100)) {
                        op1 = 233;
                        op2 = 234;
                    } else {
                        op1 = 129;
                        op2 = 141;
                    }
                }
                case 1 -> { // nm
                    if (Util.isTrue(50, 100)) {
                        op1 = 130;
                        op2 = 142;
                    } else if (Util.isTrue(50, 100)) {
                        op1 = 131;
                        op2 = 143;
                    } else if (Util.isTrue(50, 100)) {
                        op1 = 233;
                        op2 = 234;
                    } else {
                        op1 = 132;
                        op2 = 144;
                    }
                }
                default -> { // xd
                    if (Util.isTrue(50, 100)) {
                        op1 = 134;
                        op2 = 137;
                    } else if (Util.isTrue(50, 100)) {
                        op1 = 135;
                        op2 = 138;
                    } else if (Util.isTrue(50, 100)) {
                        op1 = 233;
                        op2 = 234;
                    } else {
                        op1 = 133;
                        op2 = 136;
                    }
                }
            }
        }
        return new int[]{op1, op2, op3, op4};
    }

    public ItemMap randDoTL(Zone zone, int quantity, int x, int y, long id) {
        short idTempTL;
        short[] ao = {555, 557, 559};
        short[] quan = {556, 558, 560};
        short[] gang = {562, 564, 566};
        short[] giay = {563, 565, 567};
        short[] nhan = {561};
        short[] options = {86, 87};
        /// Lựa chọn ngẫu nhiên trang bị
        if (Util.isTrue(10, 100)) {  // Nhẫn (10%)
            idTempTL = nhan[0];
        } else if (Util.isTrue(25, 100)) {  // Găng tay (15%)
            idTempTL = gang[Util.nextInt(3)];
        } else if (Util.isTrue(45, 100)) {  // Quần (20%)
            idTempTL = quan[Util.nextInt(3)];
        } else if (Util.isTrue(75, 100)) {  // Áo (30%)
            idTempTL = ao[Util.nextInt(3)];
        } else {  // Giày (25%)
            idTempTL = giay[Util.nextInt(3)];
        }

        int tiLe = Util.nextInt(100, 115);
        List<ItemOption> itemoptions = new ArrayList<>();

        switch (idTempTL) {
            case 555:
                itemoptions.add(new ItemOption(47, 800 * tiLe / 100));
                if (tiLe > 100) {
                    itemoptions.add(new ItemOption(206, tiLe - 100)); // Vật phẩm hiếm rơi từ quái
                }
                break;
            case 557: // Áo Thần Linh NM
                itemoptions.add(new ItemOption(47, 850 * tiLe / 100));
                if (tiLe > 100) {
                    itemoptions.add(new ItemOption(206, tiLe - 100));
                }
                break;
            case 559: // Áo Thần Linh XD
                itemoptions.add(new ItemOption(47, 900 * tiLe / 100));
                if (tiLe > 100) {
                    itemoptions.add(new ItemOption(206, tiLe - 100));
                }
                break;
            case 556: // Quần Thần Linh TD
                int chiso = 52000 * tiLe / 100;
                itemoptions.add(new ItemOption(22, chiso / 1000));
                itemoptions.add(new ItemOption(27, chiso / 20));
                if (tiLe > 100) {
                    itemoptions.add(new ItemOption(206, tiLe - 100));
                }
                break;
            case 558: // Quần Thần Linh NM
                chiso = 50000 * tiLe / 100;
                itemoptions.add(new ItemOption(22, chiso / 1000));
                itemoptions.add(new ItemOption(27, chiso / 20));
                if (tiLe > 100) {
                    itemoptions.add(new ItemOption(206, tiLe - 100));
                }
                break;
            case 560: // Quần Thần Linh XD
                chiso = 48000 * tiLe / 100;
                itemoptions.add(new ItemOption(22, chiso / 1000));
                itemoptions.add(new ItemOption(27, chiso / 20));
                if (tiLe > 100) {
                    itemoptions.add(new ItemOption(206, tiLe - 100));
                }
                break;
            case 562: // Găng tay Thần Linh TD
                itemoptions.add(new ItemOption(0, 4400 * tiLe / 100));
                if (tiLe > 100) {
                    itemoptions.add(new ItemOption(206, tiLe - 100));
                }
                break;
            case 564: // Găng tay Thần Linh NM
                itemoptions.add(new ItemOption(0, 4300 * tiLe / 100));
                if (tiLe > 100) {
                    itemoptions.add(new ItemOption(206, tiLe - 100));
                }
                break;
            case 566: // Găng tay Thần Linh
                itemoptions.add(new ItemOption(0, 4500 * tiLe / 100));
                if (tiLe > 100) {
                    itemoptions.add(new ItemOption(206, tiLe - 100));
                }
                break;
            case 563: // Giày Thần Linh TD
                chiso = 48000 * tiLe / 100;
                itemoptions.add(new ItemOption(23, chiso / 1000));
                itemoptions.add(new ItemOption(28, chiso / 20));
                if (tiLe > 100) {
                    itemoptions.add(new ItemOption(206, tiLe - 100));
                }
                break;
            case 565: // Giày Thần Linh NM
                chiso = 50000 * tiLe / 100;
                itemoptions.add(new ItemOption(23, chiso / 1000));
                itemoptions.add(new ItemOption(28, chiso / 20));
                if (tiLe > 100) {
                    itemoptions.add(new ItemOption(206, tiLe - 100));
                }
                break;
            case 567: // Giày Thần Linh XD
                chiso = 46000 * tiLe / 100;
                itemoptions.add(new ItemOption(23, chiso / 1000));
                itemoptions.add(new ItemOption(28, chiso * 150 / 1000));
                if (tiLe > 100) {
                    itemoptions.add(new ItemOption(206, tiLe - 100));
                }
                break;
            case 561: // Nhẫn Thần Linh
                itemoptions.add(new ItemOption(14, 14 * tiLe / 100));
                break;
            default:
                break;
        }

        if (Util.isTrue(30, 100)) {
            if (Util.isTrue(70, 100)) {
                itemoptions.add(new ItemOption(options[Util.nextInt(options.length)], 0));
            }
        }

        itemoptions.add(new ItemOption(21, Util.nextInt(15, 17)));
        ItemMap it = new ItemMap(zone, idTempTL, quantity, x, y, id);
        it.options.clear();
        it.options.addAll(itemoptions);
        return it;
    }

    public ItemMap randDoTLBoss(Zone zone, int quantity, int x, int y, long id) {
        short idTempTL;
        short[] ao = {555, 557, 559};
        short[] quan = {556, 558, 560};
        short[] gang = {562, 564, 566};
        short[] giay = {563, 565, 567};
        short[] nhan = {561};
        short[] options = {86, 87};
        /// Lựa chọn ngẫu nhiên trang bị
        if (Util.isTrue(10, 100)) {  // Nhẫn (10%)
            idTempTL = nhan[0];
        } else if (Util.isTrue(25, 100)) {  // Găng tay (15%)
            idTempTL = gang[Util.nextInt(3)];
        } else if (Util.isTrue(45, 100)) {  // Quần (20%)
            idTempTL = quan[Util.nextInt(3)];
        } else if (Util.isTrue(75, 100)) {  // Áo (30%)
            idTempTL = ao[Util.nextInt(3)];
        } else {  // Giày (25%)
            idTempTL = giay[Util.nextInt(3)];
        }

        int tiLe = Util.nextInt(100, 115);
        List<ItemOption> itemoptions = new ArrayList<>();

        // Tùy chỉnh chỉ số cho từng ID trang bị cụ thể
        switch (idTempTL) {
            case 555: // Áo Thần Linh TD
                itemoptions.add(new ItemOption(47, 800 * tiLe / 100));
                if (tiLe > 100) {
                    itemoptions.add(new ItemOption(207, tiLe - 100)); // Vật phẩm hiếm rơi từ quái
                }
                break;
            case 557: // Áo Thần Linh NM
                itemoptions.add(new ItemOption(47, 850 * tiLe / 100));
                if (tiLe > 100) {
                    itemoptions.add(new ItemOption(207, tiLe - 100));
                }
                break;
            case 559: // Áo Thần Linh XD
                itemoptions.add(new ItemOption(47, 900 * tiLe / 100));
                if (tiLe > 100) {
                    itemoptions.add(new ItemOption(207, tiLe - 100));
                }
                break;
            case 556: // Quần Thần Linh TD
                int chiso = 52000 * tiLe / 100;
                itemoptions.add(new ItemOption(22, chiso / 1000));
                itemoptions.add(new ItemOption(27, chiso / 20));
                if (tiLe > 100) {
                    itemoptions.add(new ItemOption(207, tiLe - 100));
                }
                break;
            case 558: // Quần Thần Linh NM
                chiso = 50000 * tiLe / 100;
                itemoptions.add(new ItemOption(22, chiso / 1000));
                itemoptions.add(new ItemOption(27, chiso / 20));
                if (tiLe > 100) {
                    itemoptions.add(new ItemOption(207, tiLe - 100));
                }
                break;
            case 560: // Quần Thần Linh XD
                chiso = 48000 * tiLe / 100;
                itemoptions.add(new ItemOption(22, chiso / 1000));
                itemoptions.add(new ItemOption(27, chiso / 20));
                if (tiLe > 100) {
                    itemoptions.add(new ItemOption(207, tiLe - 100));
                }
                break;
            case 562: // Găng tay Thần Linh TD
                itemoptions.add(new ItemOption(0, 4400 * tiLe / 100));
                if (tiLe > 100) {
                    itemoptions.add(new ItemOption(207, tiLe - 100));
                }
                break;
            case 564: // Găng tay Thần Linh NM
                itemoptions.add(new ItemOption(0, 4300 * tiLe / 100));
                if (tiLe > 100) {
                    itemoptions.add(new ItemOption(207, tiLe - 100));
                }
                break;
            case 566: // Găng tay Thần Linh
                itemoptions.add(new ItemOption(0, 4500 * tiLe / 100));
                if (tiLe > 100) {
                    itemoptions.add(new ItemOption(207, tiLe - 100));
                }
                break;
            case 563: // Giày Thần Linh TD
                chiso = 48000 * tiLe / 100;
                itemoptions.add(new ItemOption(23, chiso / 1000));
                itemoptions.add(new ItemOption(28, chiso / 20));
                if (tiLe > 100) {
                    itemoptions.add(new ItemOption(207, tiLe - 100));
                }
                break;
            case 565: // Giày Thần Linh NM
                chiso = 50000 * tiLe / 100;
                itemoptions.add(new ItemOption(23, chiso / 1000));
                itemoptions.add(new ItemOption(28, chiso / 20));
                if (tiLe > 100) {
                    itemoptions.add(new ItemOption(207, tiLe - 100));
                }
                break;
            case 567: // Giày Thần Linh XD
                chiso = 46000 * tiLe / 100;
                itemoptions.add(new ItemOption(23, chiso / 1000));
                itemoptions.add(new ItemOption(28, chiso * 150 / 1000));
                if (tiLe > 100) {
                    itemoptions.add(new ItemOption(207, tiLe - 100));
                }
                break;
            case 561: // Nhẫn Thần Linh
                itemoptions.add(new ItemOption(14, 14 * tiLe / 100));
                break;
            default:
                break;
        }

        // OPtion thêm
        if (Util.isTrue(30, 100)) {
            itemoptions.add(new ItemOption(options[Util.nextInt(options.length)], 0));
        }

        // Thêm chỉ số mặc định
        itemoptions.add(new ItemOption(21, Util.nextInt(15, 17)));

        // Tạo ItemMap và trả về
        ItemMap it = new ItemMap(zone, idTempTL, quantity, x, y, id);
        it.options.clear();
        it.options.addAll(itemoptions);
        return it;
    }

    public Item DoThienSu(int itemId, int gender) {
        Item dots = createItemSetKichHoat(itemId, 1);
        List<Integer> ao = Arrays.asList(1048, 1049, 1050);
        List<Integer> quan = Arrays.asList(1051, 1052, 1053);
        List<Integer> gang = Arrays.asList(1054, 1055, 1056);
        List<Integer> giay = Arrays.asList(1057, 1058, 1059);
        List<Integer> nhan = Arrays.asList(1060, 1061, 1062);
        //áo
        if (ao.contains(itemId)) {
            dots.itemOptions.add(new ItemOption(47, Util.highlightsItem(gender == 2, new Random().nextInt(1201) + 2800))); // áo từ 2800-4000 giáp
        }
        //quần
        if (Util.isTrue(80, 100)) {
            if (quan.contains(itemId)) {
                dots.itemOptions.add(new ItemOption(22, Util.highlightsItem(gender == 0, new Random().nextInt(11) + 120))); // hp 120k-130k
            }
        } else {
            if (quan.contains(itemId)) {
                dots.itemOptions.add(new ItemOption(22, Util.highlightsItem(gender == 0, new Random().nextInt(21) + 130))); // hp 130-150k 15%
            }
        }
        //găng
        if (Util.isTrue(80, 100)) {
            if (gang.contains(itemId)) {
                dots.itemOptions.add(new ItemOption(0, Util.highlightsItem(gender == 2, new Random().nextInt(651) + 10350))); // 9350-10000
            }
        } else {
            if (gang.contains(itemId)) {
                dots.itemOptions.add(new ItemOption(0, Util.highlightsItem(gender == 2, new Random().nextInt(1001) + 10500))); // gang 15% 10-11k -xayda 12k1
            }
        }
        //giày
        if (Util.isTrue(80, 100)) {
            if (giay.contains(itemId)) {
                dots.itemOptions.add(new ItemOption(23, Util.highlightsItem(gender == 1, new Random().nextInt(21) + 90))); // ki 90-110k
            }
        } else {
            if (giay.contains(itemId)) {
                dots.itemOptions.add(new ItemOption(23, Util.highlightsItem(gender == 1, new Random().nextInt(21) + 110))); // ki 110-130k
            }
        }
        if (nhan.contains(itemId)) {
            dots.itemOptions.add(new ItemOption(14, Util.highlightsItem(gender == 1, new Random().nextInt(3) + 18))); // nhẫn 18-20%
        }
        dots.itemOptions.add(new ItemOption(21, 30));
        dots.itemOptions.add(new ItemOption(30, 1));
        return dots;
    }

    public void AddOption(ItemMap item, int skhId) {
        Option_All(item.options, skhId);
    }

    public void Option(Item item, int skhId) {
        Option_All(item.itemOptions, skhId);
    }

    private void Option_All(List<ItemOption> item, int skhId) {
        item.add(new ItemOption(skhId, 1));
        item.add(new ItemOption(ID(skhId), 1));
        item.add(new ItemOption(30, 1));
    }

    public int ID(int skhId) {
        switch (skhId) {
            case 127: // 1
                return 139;
            case 128: // 2
                return 140;
            case 129: // 3
                return 141;
            case 130: // 1
                return 142;
            case 131: // 2
                return 143;
            case 132: // 3
                return 144;
            case 133: // 1
                return 136;
            case 134: // 2
                return 137;
            case 135: // 3
                return 138;
        }
        return 0;
    }

}
