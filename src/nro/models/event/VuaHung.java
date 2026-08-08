package nro.models.event;

import java.util.ArrayList;
import java.util.List;
import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import java.util.Random;
import nro.models.consts.ConstFont;
import nro.models.npc.DuaHauEgg;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.Service;

/**
 *
 * @author By Mr Blue
 */
public class VuaHung extends Npc {

    private static final int NGA_VOI = 1220;
    private static final int CUA_GA = 1221;
    private static final int HONG_MAO = 1222;
    private static final int HOP_QUA_THUONG = 1776;
    private static final int HOP_QUA_VIP = 1777;
    private static final int BANH_DAY = 1542;
    private static final int BANH_CHUNG = 1556;
    private static final int DUA_HAU = 569;
    private static final int TEM = 1558;
    private static final int THOI_VANG = 457;
    private static final List<Integer> nguoiDaTrong = new ArrayList<>();
    private static final long START_TIME = System.currentTimeMillis();
    private static final long THOI_GIAN_TRONG_CAY_MS = 6 * 60 * 60 * 1000;
    private static final int SO_LUONG_TOI_DA = 10;

    private final Random random = new Random();

    public VuaHung(int mapId, int status, int cx, int cy, int tempId, int avatar) {
        super(mapId, status, cx, cy, tempId, avatar);
    }

    @Override
    public void openBaseMenu(Player player) {
        long now = System.currentTimeMillis();
        long elapsed = now - START_TIME;
        long phaseStartTime = START_TIME + (elapsed / (60 * 60 * 1000)) * (60 * 60 * 1000);
        long timeInCurrentPhase = now - phaseStartTime;

        boolean isPhaseActive = timeInCurrentPhase < 15 * 60 * 1000;
        boolean isSlotAvailable = nguoiDaTrong.size() < SO_LUONG_TOI_DA;

        boolean isDangTrong = player.DuaHauEgg != null && (now - player.DuaHauEgg.timeStart < THOI_GIAN_TRONG_CAY_MS);
        boolean daTrongTrongPhase = nguoiDaTrong.contains(player.id);
        if (player.DuaHauEgg != null) {
            boolean hetThoiGian = (now - player.DuaHauEgg.timeStart >= THOI_GIAN_TRONG_CAY_MS);
            player.idMark.setIndexMenu(0);
            List<String> options = new ArrayList<>(List.of(
                    "Đổi\nDưa hấu",
                    "Dâng\nsính lễ",
                    "Dâng\nsính lễ\nxịn",
                    "Dâng\nbánh dầy",
                    "Dâng\nbánh chưng"
            ));
            String info = hetThoiGian
                    ? "Cây dưa đã sẵn sàng, ngươi muốn đổi quà chăng?"
                    : "Cây dưa đang lớn, quay lại sau để đổi quà!";
            createOtherMenu(player, 0, info, options.toArray(new String[0]));
            return;
        }

        if (isPhaseActive && isSlotAvailable && !daTrongTrongPhase) {
            player.idMark.setIndexMenu(100);
            createOtherMenu(player, 100, "Hãy trồng dưa hấu và mang quả đến gặp ta đổi quà?", "Trồng\nDưa Hấu");
            return;
        }

        if (isPhaseActive && daTrongTrongPhase) {
            player.idMark.setIndexMenu(101);
            createOtherMenu(player, 101, "Bạn đã trồng cây trong phiên này rồi!", "OK");
            return;
        }

        player.idMark.setIndexMenu(0);
        List<String> options = new ArrayList<>(List.of(
                "Đổi\nDưa hấu",
                "Dâng\nsính lễ",
                "Dâng\nsính lễ\nxịn",
                "Dâng\nbánh dầy",
                "Dâng\nbánh chưng"
        ));
        createOtherMenu(player, 0, "Ngươi muốn dâng sính lễ?", options.toArray(new String[0]));
    }

    @Override
    public void confirmMenu(Player pl, int select) {
        if (canOpenNpc(pl)) {
            switch (pl.idMark.getIndexMenu()) {
                case 0 -> {
                    switch (select) {
                        case 0 ->
                            createOtherMenu(pl, 10, "Muốn đổi ngọc thì mang Dưa Hấu và Tem đến đây?",
                                    "5 thỏi\n1 Quả\n1 tem",
                                    "40 thỏi\n10 Quả\n2 tem",
                                    "120 thỏi\n20 Quả\n3 tem",
                                    "185 thỏi\n25 Quả\n4 tem",
                                    "250 thỏi\n30 Quả\n5 tem");
                        case 1 -> {
                            int q1 = getItemQuantity(pl, NGA_VOI);
                            int q2 = getItemQuantity(pl, CUA_GA);
                            int q3 = getItemQuantity(pl, HONG_MAO);
                            boolean enoughItem = q1 >= 9 && q2 >= 9 && q3 >= 9;
                            boolean enoughGold = pl.inventory.gold >= 1_000_000;

                            StringBuilder sb = new StringBuilder()
                                    .append(ConstFont.BOLD_GREEN).append("Ngươi muốn dâng sính lễ?\n")
                                    .append(formatRequirement("Ngà Voi", q1, 9))
                                    .append(formatRequirement("Của Gà", q2, 9))
                                    .append(formatRequirement("Hồng Mao", q3, 9))
                                    .append(ConstFont.BOLD_RED).append("Giá vàng: 1.000.000\n");

                            if (enoughItem && enoughGold) {
                                createOtherMenu(pl, 1, sb.toString(), "Đồng ý", "Từ chối");
                            } else {
                                createOtherMenu(pl, 123, sb.toString(), "Từ chối");
                            }
                        }
                        case 2 -> {
                            int q1 = getItemQuantity(pl, NGA_VOI);
                            int q2 = getItemQuantity(pl, CUA_GA);
                            int q3 = getItemQuantity(pl, HONG_MAO);
                            boolean enoughItem = q1 >= 9 && q2 >= 9 && q3 >= 9;
                            boolean enoughGem = pl.inventory.gem >= 10;

                            StringBuilder sb = new StringBuilder()
                                    .append(ConstFont.BOLD_GREEN).append("Ngươi muốn dâng sính lễ xịn?\n")
                                    .append(formatRequirement("Ngà Voi", q1, 9))
                                    .append(formatRequirement("Của Gà", q2, 9))
                                    .append(formatRequirement("Hồng Mao", q3, 9))
                                    .append(ConstFont.BOLD_RED).append("Giá ngọc: 10\n");

                            if (enoughItem && enoughGem) {
                                createOtherMenu(pl, 2, sb.toString(), "Đồng ý", "Từ chối");
                            } else {
                                createOtherMenu(pl, 123, sb.toString(), "Từ chối");
                            }
                        }
                        case 3 -> {
                            int quantity = getItemQuantity(pl, BANH_DAY);
                            StringBuilder sb = new StringBuilder()
                                    .append(ConstFont.BOLD_GREEN).append("Ngươi muốn dâng Bánh Dầy?\n")
                                    .append(formatRequirement("Bánh Dầy", quantity, 1));
                            if (quantity >= 1) {
                                createOtherMenu(pl, 3, sb.toString(), "Đồng ý", "Từ chối");
                            } else {
                                createOtherMenu(pl, 123, sb.toString(), "Từ chối");
                            }
                        }
                        case 4 -> {
                            int quantity = getItemQuantity(pl, BANH_CHUNG);
                            StringBuilder sb = new StringBuilder()
                                    .append(ConstFont.BOLD_GREEN).append("Ngươi muốn dâng Bánh chưng?\n")
                                    .append(formatRequirement("Bánh chưng lang liêu", quantity, 1));
                            if (quantity >= 1) {
                                createOtherMenu(pl, 4, sb.toString(), "Đồng ý", "Từ chối");
                            } else {
                                createOtherMenu(pl, 123, sb.toString(), "Từ chối");
                            }
                        }
                    }
                }
                case 100 -> {
                    if (select == 0) {
                        if (pl.DuaHauEgg == null) {
                            DuaHauEgg.createDuaHauEgg(pl);
                            if (pl.DuaHauEgg != null) {
                                pl.DuaHauEgg.sendDuaHauEgg();
                                nguoiDaTrong.add((int) (pl.id));
                                openBaseMenu(pl);
                                Service.gI().sendThongBao(pl, "Bạn đã trồng 1 cây Dưa Hấu!");
                            }
                        } else {
                            Service.gI().sendThongBao(pl, "Bạn đã có cây dưa hấu nên không thể trồng thêm.");
                        }
                    }
                }
                case 10 -> {
                    if (select >= 0 && select <= 4) {
                        DoiDuaHau(pl, select);
                    }
                }
                case 1 -> {
                    if (select == 0) {
                        NhanQuaThuong(pl);
                    }
                }
                case 2 -> {
                    if (select == 0) {
                        NhanQuaVip(pl);
                    }
                }
                case 3 -> {
                    if (select == 0) {
                        BanhDay(pl);
                    }
                }
                case 4 -> {
                    if (select == 0) {
                        BanhChung(pl);
                    }
                }
            }
        }
    }

    private void NhanQuaThuong(Player player) {
        Item ngaVoi = InventoryService.gI().findItemBag(player, NGA_VOI);
        Item cuaGa = InventoryService.gI().findItemBag(player, CUA_GA);
        Item hongMao = InventoryService.gI().findItemBag(player, HONG_MAO);

        if (ngaVoi != null && cuaGa != null && hongMao != null
                && ngaVoi.quantity >= 9 && cuaGa.quantity >= 9 && hongMao.quantity >= 9
                && player.inventory.gold >= 1_000_000) {

            InventoryService.gI().subQuantityItemsBag(player, ngaVoi, 9);
            InventoryService.gI().subQuantityItemsBag(player, cuaGa, 9);
            InventoryService.gI().subQuantityItemsBag(player, hongMao, 9);
            player.inventory.gold -= 1_000_000;
            InventoryService.gI().sendItemBags(player);
            Service.gI().sendMoney(player);

            Item hopQua = ItemService.gI().createNewItem((short) HOP_QUA_THUONG);
            InventoryService.gI().addItemBag(player, hopQua);
            InventoryService.gI().sendItemBags(player);

            Service.gI().sendThongBao(player, "Bạn đã nhận được 1 Hộp Quà Thường!");
        } else {
            Service.gI().sendThongBao(player, "Bạn không đủ nguyên liệu hoặc vàng!");
        }
    }

    private void NhanQuaVip(Player player) {
        Item ngaVoi = InventoryService.gI().findItemBag(player, NGA_VOI);
        Item cuaGa = InventoryService.gI().findItemBag(player, CUA_GA);
        Item hongMao = InventoryService.gI().findItemBag(player, HONG_MAO);

        if (ngaVoi != null && cuaGa != null && hongMao != null
                && ngaVoi.quantity >= 9 && cuaGa.quantity >= 9 && hongMao.quantity >= 9
                && player.inventory.gem >= 10) {

            InventoryService.gI().subQuantityItemsBag(player, ngaVoi, 9);
            InventoryService.gI().subQuantityItemsBag(player, cuaGa, 9);
            InventoryService.gI().subQuantityItemsBag(player, hongMao, 9);
            player.inventory.gem -= 10;
            InventoryService.gI().sendItemBags(player);
            Service.gI().sendMoney(player);

            Item hopQua = ItemService.gI().createNewItem((short) HOP_QUA_VIP);
            InventoryService.gI().addItemBag(player, hopQua);
            InventoryService.gI().sendItemBags(player);

            Service.gI().sendThongBao(player, "Bạn đã nhận được 1 Hộp Quà VIP!");
        } else {
            Service.gI().sendThongBao(player, "Bạn không đủ nguyên liệu hoặc ngọc!");
        }
    }

    private void BanhDay(Player player) {
        Item banhDay = InventoryService.gI().findItemBag(player, BANH_DAY);
        if (banhDay != null && banhDay.quantity >= 1) {
            InventoryService.gI().subQuantityItemsBag(player, banhDay, 1);
            InventoryService.gI().sendItemBags(player);

            int randomItemId = randomBanhDay();
            Item randomItem = ItemService.gI().createNewItem((short) randomItemId);

            InventoryService.gI().addItemBag(player, randomItem);
            InventoryService.gI().sendItemBags(player);

            Service.gI().sendThongBao(player, "Bạn đã nhận được một món quà ngẫu nhiên từ Bánh Dầy!");
        } else {
            Service.gI().sendThongBao(player, "Bạn không đủ Bánh Dầy!");
        }
    }

    private void BanhChung(Player player) {
        Item banhChung = InventoryService.gI().findItemBag(player, BANH_CHUNG);
        if (banhChung != null && banhChung.quantity >= 1) {
            InventoryService.gI().subQuantityItemsBag(player, banhChung, 1);
            InventoryService.gI().sendItemBags(player);

            int randomItemId = randomBanhChung();
            Item randomItem = ItemService.gI().createNewItem((short) randomItemId);

            InventoryService.gI().addItemBag(player, randomItem);
            InventoryService.gI().sendItemBags(player);

            Service.gI().sendThongBao(player, "Bạn đã nhận được một món quà ngẫu nhiên từ Bánh Chưng!");
        } else {
            Service.gI().sendThongBao(player, "Bạn không đủ Bánh Chưng!");
        }
    }

    private int randomBanhDay() {
        int[] possibleItems = {381, 382, 383, 384, 385, 1635};
        return possibleItems[random.nextInt(possibleItems.length)];
    }

    private int randomBanhChung() {
        int[] possibleItems = {1150, 1151, 1152, 1153, 1635, 1154, 1423, 1438, 1634};
        return possibleItems[random.nextInt(possibleItems.length)];
    }

    private void DoiDuaHau(Player player, int select) {
        Item DuaHau = InventoryService.gI().findItemBag(player, DUA_HAU);
        Item tem = InventoryService.gI().findItemBag(player, TEM);

        int[] gems = {5, 40, 120, 185, 250};
        int[] duahau = {1, 10, 20, 25, 30};
        int[] temNeeded = {1, 2, 3, 4, 5};

        if (DuaHau != null && tem != null
                && DuaHau.quantity >= duahau[select]
                && tem.quantity >= temNeeded[select]) {

            InventoryService.gI().subQuantityItemsBag(player, DuaHau, duahau[select]);
            InventoryService.gI().subQuantityItemsBag(player, tem, temNeeded[select]);
            InventoryService.gI().sendItemBags(player);

            player.inventory.gem += gems[select];
            Service.gI().sendMoney(player);

            Service.gI().sendThongBao(player, "Bạn đã nhận được " + gems[select] + " Ngọc!");
        } else {
            Service.gI().sendThongBao(player, "Bạn không đủ nguyên liệu để đổi Ngọc!");
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
