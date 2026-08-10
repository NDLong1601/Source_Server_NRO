package nro.models.event;

import nro.models.consts.ConstFont;
import nro.models.item.Item;
import nro.models.database.PlayerDAO;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.server.Manager;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.Service;

public class XeNuocMia extends Npc {

    private static final int KHUC_MIA = 1612;
    private static final int NUOC_DA = 1613;

    public XeNuocMia(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    @Override
    public void openBaseMenu(Player player) {
        if (!EventManager.gI().isActive("summer")) {
            createOtherMenu(player, 0, "Sự kiện mùa hè hiện chưa mở.", "Đóng");
            return;
        }
        createOtherMenu(player, 0, "Cậu muốn uống gì nào",
                "Mua 1 ly\nnước mía",
                "Mua 10 ly\nnước mía");
    }

    @Override
    public void confirmMenu(Player pl, int select) {
        if (!EventManager.gI().isActive("summer")) {
            return;
        }
        if (canOpenNpc(pl)) {
            switch (pl.idMark.getIndexMenu()) {
                case 0 -> {
                    switch (select) {
                        case 0 -> {
                            int q1 = getItemQuantity(pl, KHUC_MIA);
                            int q2 = getItemQuantity(pl, NUOC_DA);
                            boolean enoughItem = q1 >= 5 && q2 >= 2;
                            boolean enoughGold = pl.inventory.gold >= 5_000_000;

                            StringBuilder sb = new StringBuilder()
                                    .append(ConstFont.BOLD_GREEN).append("Bạn muốn mua nước mía?\n")
                                    .append(formatRequirement("Khúc mía", q1, 5))
                                    .append(formatRequirement("Nước đá", q2, 2))
                                    .append(ConstFont.BOLD_RED).append("Giá vàng: 5.000.000\n");

                            if (enoughItem && enoughGold) {
                                createOtherMenu(pl, 1, sb.toString(), "Đồng ý", "Từ chối");
                            } else {
                                createOtherMenu(pl, 123, sb.toString(), "Từ chối");
                            }
                        }
                        case 1 -> {
                            int q1 = getItemQuantity(pl, KHUC_MIA);
                            int q2 = getItemQuantity(pl, NUOC_DA);
                            boolean enoughItem = q1 >= 50 && q2 >= 20;
                            boolean enoughGold = pl.inventory.gold >= 50_000_000;

                            StringBuilder sb = new StringBuilder()
                                    .append(ConstFont.BOLD_GREEN).append("Bạn muốn mua nước mía?\n")
                                    .append(formatRequirement("Khúc mía", q1, 50))
                                    .append(formatRequirement("Nước đá", q2, 20))
                                    .append(ConstFont.BOLD_RED).append("Giá vàng: 50.000.000\n");

                            if (enoughItem && enoughGold) {
                                createOtherMenu(pl, 2, sb.toString(), "Đồng ý", "Từ chối");
                            } else {
                                createOtherMenu(pl, 123, sb.toString(), "Từ chối");
                            }
                        }
                    }
                }
                case 1 -> {
                    if (select == 0) {
                        MuaNuocMia(pl);
                    }
                }
                case 2 -> {
                    if (select == 0) {
                        MuaNuocMiaX10(pl);
                    }
                }
            }
        }
    }

    private void MuaNuocMia(Player player) {
        Item khucMia = InventoryService.gI().findItemBag(player, KHUC_MIA);
        Item nuocDa = InventoryService.gI().findItemBag(player, NUOC_DA);

        if (khucMia != null && nuocDa != null
                && khucMia.quantity >= 5 && nuocDa.quantity >= 2
                && player.inventory.gold >= 5_000_000) {

            InventoryService.gI().subQuantityItemsBag(player, khucMia, 5);
            InventoryService.gI().subQuantityItemsBag(player, nuocDa, 2);
            player.inventory.gold -= 5_000_000;

            InventoryService.gI().sendItemBags(player);
            Service.gI().sendMoney(player);

            short[] waterItems = {1614, 1615, 1616};
            int randIndex = (int) (Math.random() * waterItems.length);
            Item itemRandom = ItemService.gI().createNewItem(waterItems[randIndex]);

            InventoryService.gI().addItemBag(player, itemRandom);
            InventoryService.gI().sendItemBags(player);
            player.point_sukien1 += 1;
            PlayerDAO.updateEventRankingPoints(player);
            if (!Manager.isTopSukien1Changed) {
                Manager.isTopSukien1Changed = true;
            }

            Service.gI().sendThongBao(player, "Bạn đã nhận được " + itemRandom.template.name + "!");
        } else {
            Service.gI().sendThongBao(player, "Bạn không đủ nguyên liệu hoặc vàng để mua nước mía!");
        }
    }

    private void MuaNuocMiaX10(Player player) {
        Item khucMia = InventoryService.gI().findItemBag(player, KHUC_MIA);
        Item nuocDa = InventoryService.gI().findItemBag(player, NUOC_DA);

        if (khucMia != null && nuocDa != null
                && khucMia.quantity >= 50 && nuocDa.quantity >= 20
                && player.inventory.gold >= 50_000_000) {

            InventoryService.gI().subQuantityItemsBag(player, khucMia, 50);
            InventoryService.gI().subQuantityItemsBag(player, nuocDa, 20);
            player.inventory.gold -= 50_000_000;

            short[] waterItems = {1614, 1615, 1616};
            StringBuilder rewardMsg = new StringBuilder("Bạn đã nhận được:\n");

            int[] counts = new int[waterItems.length];

            for (int i = 0; i < 10; i++) {
                int randIndex = (int) (Math.random() * waterItems.length);
                counts[randIndex]++;
            }

            for (int i = 0; i < waterItems.length; i++) {
                if (counts[i] > 0) {
                    Item item = ItemService.gI().createNewItem(waterItems[i]);
                    item.quantity = counts[i];
                    InventoryService.gI().addItemBag(player, item);
                    rewardMsg.append("- ").append(item.template.name).append(" x").append(counts[i]).append("\n");
                }
            }

            InventoryService.gI().sendItemBags(player);
            Service.gI().sendMoney(player);
            player.point_sukien1 += 10;
            PlayerDAO.updateEventRankingPoints(player);
            if (!Manager.isTopSukien1Changed) {
                Manager.isTopSukien1Changed = true;
            }

            Service.gI().sendThongBao(player, rewardMsg.toString());
        } else {
            Service.gI().sendThongBao(player, "Bạn không đủ nguyên liệu hoặc vàng để mua nước mía!");
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
}
