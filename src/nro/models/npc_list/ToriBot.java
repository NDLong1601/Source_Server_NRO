package nro.models.npc_list;

import nro.models.item.Item;
import nro.models.item.Item.ItemOption;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.PetService;
import nro.models.services.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.Duration;

public class ToriBot extends Npc {

    private static final LocalDateTime VIP_SEASON_START_DATE = LocalDateTime.of(LocalDate.now().getYear(), Month.JUNE, 5, 0, 0, 0);
    private static final LocalDateTime VIP_SEASON_END_DATE = LocalDateTime.of(LocalDate.now().getYear(), Month.DECEMBER, 5, 23, 59, 59);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    public ToriBot(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    @Override
    public void openBaseMenu(Player player) {
        LocalDateTime currentDateTime = LocalDateTime.now();
        if (currentDateTime.isAfter(VIP_SEASON_END_DATE)) {
            this.createOtherMenu(player, 0, "Ngươi tìm ta có việc gì?", "Đóng");
        } else {
            int currentPurchases = player.vipPurchaseCount;
            String vipInfoMessage = "Lưu ý: nâng cấp VIP chỉ được tối đa 4 lần mỗi mùa (Bạn đã mua: " + currentPurchases + "/4 lần)";
            String startDateStr = VIP_SEASON_START_DATE.format(DATE_TIME_FORMATTER);
            String endDateStr = VIP_SEASON_END_DATE.format(DATE_TIME_FORMATTER);
            Duration remainingDuration = Duration.between(currentDateTime, VIP_SEASON_END_DATE);
            long days = remainingDuration.toDays();
            long hours = remainingDuration.toHours() % 24;
            long minutes = remainingDuration.toMinutes() % 60;
            long seconds = remainingDuration.getSeconds() % 60;

            String remainingTimeString;
            if (remainingDuration.isNegative() || remainingDuration.isZero()) {
                remainingTimeString = "Mùa VIP sắp kết thúc!";
            } else if (days == 0 && hours == 0 && minutes == 0) {
                remainingTimeString = "Còn " + seconds + " giây!";
            } else if (days == 0 && hours == 0) {
                remainingTimeString = "Còn " + minutes + " phút " + seconds + " giây!";
            } else if (days == 0) {
                remainingTimeString = "Còn " + hours + " giờ " + minutes + " phút " + seconds + " giây!";
            } else {
                remainingTimeString = "Còn " + days + " ngày " + hours + " giờ " + minutes + " phút " + seconds + " giây!";
            }

            this.createOtherMenu(player, 0,
                    "Trong thời gian mùa VIP diễn ra\n(Từ " + startDateStr + " đến hết " + endDateStr + ")\n"
                    + "Thời gian còn lại: " + remainingTimeString + "\n"
                    + // Thêm dòng thời gian còn lại
                    "Tạo nhân vật mới sẽ được X2 Kinh nghiệm toàn mùa\n Nếu nâng cấp VIP sẽ được nhận\n nhiều ưu đãi hơn nữa.\n" + vipInfoMessage,
                    "Vip 1", "Vip 2", "Vip 3", "Vip 4");
        }
    }

    @Override
    public void confirmMenu(Player pl, int select) {
        if (!canOpenNpc(pl)) {
            return;
        }
        LocalDateTime currentDateTime = LocalDateTime.now();
        if (currentDateTime.isAfter(VIP_SEASON_END_DATE)) {
            Service.gI().sendThongBao(pl, "Mùa VIP đã kết thúc. Bạn không thể mua VIP vào lúc này.");
           // openBaseMenu(pl);
            return;
        }
        switch (pl.idMark.getIndexMenu()) {
            case 0 -> {
                switch (select) {
                    case 0 ->
                        createOtherMenu(pl, 1, "Nâng cấp VIP 1 bạn sẽ nhận được\n200 thỏi vàng, 10 phiếu giảm giá 80%\nX3 Kinh nghiệm toàn mùa\nThú cưỡi ve sầu xên hsd 30 ngày\nTặng 1 đệ tử, 5 viên đá bảo vệ\nPet bọ cánh cứng hsd 30 ngày\nBúa hắc hường hsd 30 ngày",
                                "50.000\nđiểm mùa [" + pl.getSession().vnd + "]",
                                "Đóng");
                    case 1 ->
                        createOtherMenu(pl, 2, "Nâng cấp VIP 2 bạn sẽ nhận được\n500 thỏi vàng, 10 phiếu giảm giá 80%\nX3 Kinh nghiệm toàn mùa\nThú cưỡi ve sầu xên hsd 30 ngày\nTặng 1 đệ tử, 10 viên đá bảo vệ\nPet bọ cánh cứng hsd 30 ngày\nBúa hắc hường hsd 30 ngày\nCải trang thỏ buma hsd 30 ngày",
                                "100.000\nđiềm mùa [" + pl.getSession().vnd + "]",
                                "Đóng");
                    case 2 ->
                        createOtherMenu(pl, 3, "Nâng cấp VIP 3 bạn sẽ nhận được\n700 thỏi vàng, 10 phiếu giảm giá 80%\nX3 Kinh nghiệm toàn mùa\nThú cưỡi ve sầu xên vĩnh viễn\nTặng 1 đệ tử, 30 viên đá bảo vệ\nPet bọ cánh cứng vĩnh viễn\nBúa hắc hường vĩnh viễn\n2 viên capsule kích hoạt\n10 thẻ đội trưởng vàng\nCải trang thỏ buma vĩnh viễn",
                                "150.000\nđiềm mùa [" + pl.getSession().vnd + "]",
                                "Đóng");
                    case 3 ->
                        createOtherMenu(pl, 4, "Nâng cấp VIP 4 bạn sẽ nhận được\n1000 thỏi vàng, 10 phiếu giảm giá 80%\nX3 Kinh nghiệm toàn mùa\nCải trang hắc mị vĩnh viễn\nTặng 1 đệ tử mabu, 50 viên đá bảo vệ\nTàu ngầm cam 19 vĩnh viễn\nPet rồng nhí vĩnh viễn\n5 viên capsule kích hoạt\n20 thẻ rồng thần",
                                "200.000\nđiềm mùa [" + pl.getSession().vnd + "]",
                                "Đóng");
                }
            }

            case 1 ->
                BuyVip(pl, 1, 50000);
            case 2 ->
                BuyVip(pl, 2, 100000);
            case 3 ->
                BuyVip(pl, 3, 150000);
            case 4 ->
                BuyVip(pl, 4, 200000);
        }
    }

    private void BuyVip(Player pl, int vipLevel, int cost) {
        LocalDateTime currentDateTime = LocalDateTime.now();
        if (currentDateTime.isAfter(VIP_SEASON_END_DATE)) {
            Service.gI().sendThongBao(pl, "Mùa VIP đã kết thúc. Bạn không thể mua VIP vào lúc này.");
            openBaseMenu(pl); // Mở lại menu mặc định
            return;
        }

        if (pl.vipPurchaseCount >= 4) {
            Service.gI().sendThongBao(pl, "Bạn đã mua tối đa 4 lượt VIP mùa này rồi!");
            return;
        }
        if (pl.vip >= vipLevel) {
            Service.gI().sendThongBao(pl, "Bạn đã có VIP cấp " + pl.vip + ". Bạn vẫn có thể mua gói này để nhận lại phần thưởng.");
        }

        if (pl.getSession().vnd < cost) {
            Service.gI().sendThongBao(pl, "Không đủ tiền (" + (cost / 1000) + "k VND) để mua VIP!");
            return;
        }

        pl.getSession().vnd -= cost;
        if (vipLevel > pl.vip) {
            pl.vip = (byte) vipLevel;
        }
        pl.vipPurchaseCount++;
        switch (vipLevel) {
            case 1 ->
                VIP1(pl);
            case 2 ->
                VIP2(pl);
            case 3 ->
                VIP3(pl);
            case 4 ->
                VIP4(pl);
        }

        Service.gI().sendThongBao(pl, "Mua VIP " + vipLevel + " thành công! Bạn đã mua " + pl.vipPurchaseCount + "/4 lượt VIP mùa này.");
        openBaseMenu(pl);
    }

    private void VIP1(Player pl) {
        long currentTime = System.currentTimeMillis();
        long time = 30L * 24 * 60 * 60 * 1000;

        if (pl.timevip < currentTime) {
            pl.timevip = currentTime + time;
        } else {
            pl.timevip += time;
        }

        Item gold = ItemService.gI().createNewItem((short) 457, 200);

        Item coupon = ItemService.gI().createNewItem((short) 459, 10);

        Item mount = ItemService.gI().createNewItem((short) 1252);
        mount.itemOptions.add(new ItemOption(50, 10));
        mount.itemOptions.add(new ItemOption(77, 10));
        mount.itemOptions.add(new ItemOption(103, 10));
        mount.itemOptions.add(new ItemOption(93, 30)); // HSD 30 ngày

        Item pet = ItemService.gI().createNewItem((short) 1248);
        pet.itemOptions.add(new ItemOption(50, 10));
        pet.itemOptions.add(new ItemOption(77, 10));
        pet.itemOptions.add(new ItemOption(103, 10));
        pet.itemOptions.add(new ItemOption(93, 30)); // HSD 30 ngày

        Item hammer = ItemService.gI().createNewItem((short) 1256);
        hammer.itemOptions.add(new ItemOption(50, 10));
        hammer.itemOptions.add(new ItemOption(77, 10));
        hammer.itemOptions.add(new ItemOption(103, 10));
        hammer.itemOptions.add(new ItemOption(93, 30)); // HSD 30 ngày

        Item protectStone = ItemService.gI().createNewItem((short) 987, 5);

        if (pl.pet == null) {
            PetService.gI().createNormalPet(pl);
        }

        // Thêm toàn bộ vật phẩm vào túi đồ của người chơi
        InventoryService.gI().addItemBag(pl, gold);
        InventoryService.gI().addItemBag(pl, coupon);
        InventoryService.gI().addItemBag(pl, mount);
        InventoryService.gI().addItemBag(pl, pet);
        InventoryService.gI().addItemBag(pl, hammer);
        InventoryService.gI().addItemBag(pl, protectStone);

        InventoryService.gI().sendItemBags(pl);
    }

    private void VIP2(Player pl) {
        long currentTime = System.currentTimeMillis();
        long time = 30L * 24 * 60 * 60 * 1000;

        if (pl.timevip < currentTime) {
            pl.timevip = currentTime + time;
        } else {
            pl.timevip += time;
        }

        Item gold = ItemService.gI().createNewItem((short) 457, 500);

        Item coupon = ItemService.gI().createNewItem((short) 459, 10);

        Item mount = ItemService.gI().createNewItem((short) 1252);
        mount.itemOptions.add(new ItemOption(50, 12));
        mount.itemOptions.add(new ItemOption(77, 12));
        mount.itemOptions.add(new ItemOption(103, 12));
        mount.itemOptions.add(new ItemOption(93, 30)); // HSD 30 ngày

        Item pet = ItemService.gI().createNewItem((short) 1248);
        pet.itemOptions.add(new ItemOption(50, 12));
        pet.itemOptions.add(new ItemOption(77, 12));
        pet.itemOptions.add(new ItemOption(103, 12));
        pet.itemOptions.add(new ItemOption(93, 30)); // HSD 30 ngày

        Item hammer = ItemService.gI().createNewItem((short) 1254);
        hammer.itemOptions.add(new ItemOption(50, 12));
        hammer.itemOptions.add(new ItemOption(77, 12));
        hammer.itemOptions.add(new ItemOption(103, 12));
        hammer.itemOptions.add(new ItemOption(93, 30)); // HSD 30 ngày

        Item protectStone = ItemService.gI().createNewItem((short) 987, 10);

        if (pl.pet == null) {
            PetService.gI().createNormalPet(pl);
        }
        Item CaiTrang = ItemService.gI().createNewItem((short) 584);
        CaiTrang.itemOptions.add(new ItemOption(50, 24));
        CaiTrang.itemOptions.add(new ItemOption(77, 24));
        CaiTrang.itemOptions.add(new ItemOption(117, 15));
        CaiTrang.itemOptions.add(new ItemOption(93, 30)); // HSD 30 ngày

        InventoryService.gI().addItemBag(pl, gold);
        InventoryService.gI().addItemBag(pl, coupon);
        InventoryService.gI().addItemBag(pl, mount);
        InventoryService.gI().addItemBag(pl, pet);
        InventoryService.gI().addItemBag(pl, hammer);
        InventoryService.gI().addItemBag(pl, protectStone);
        InventoryService.gI().addItemBag(pl, CaiTrang);

        InventoryService.gI().sendItemBags(pl);
    }

    private void VIP3(Player pl) {
        long currentTime = System.currentTimeMillis();
        long time = 30L * 24 * 60 * 60 * 1000;

        if (pl.timevip < currentTime) {
            pl.timevip = currentTime + time;
        } else {
            pl.timevip += time;
        }

        Item gold = ItemService.gI().createNewItem((short) 457, 700);

        Item coupon = ItemService.gI().createNewItem((short) 459, 10);

        Item mount = ItemService.gI().createNewItem((short) 1252);
        mount.itemOptions.add(new ItemOption(50, 12));
        mount.itemOptions.add(new ItemOption(77, 12));
        mount.itemOptions.add(new ItemOption(103, 12));

        Item pet = ItemService.gI().createNewItem((short) 1248);
        pet.itemOptions.add(new ItemOption(50, 12));
        pet.itemOptions.add(new ItemOption(77, 12));
        pet.itemOptions.add(new ItemOption(103, 12));

        Item hammer = ItemService.gI().createNewItem((short) 1254);
        hammer.itemOptions.add(new ItemOption(50, 12));
        hammer.itemOptions.add(new ItemOption(77, 12));
        hammer.itemOptions.add(new ItemOption(103, 12));

        Item protectStone = ItemService.gI().createNewItem((short) 987, 30);

        if (pl.pet == null) {
            PetService.gI().createNormalPet(pl);
        }
        Item CaiTrang = ItemService.gI().createNewItem((short) 584);
        CaiTrang.itemOptions.add(new ItemOption(50, 24));
        CaiTrang.itemOptions.add(new ItemOption(77, 24));
        CaiTrang.itemOptions.add(new ItemOption(117, 15));

        Item capsule = ItemService.gI().createNewItem((short) 1655, 2);
        capsule.itemOptions.add(new ItemOption(30, 0));
        
        Item captainCard = ItemService.gI().createNewItem((short) 956, 10);

        InventoryService.gI().addItemBag(pl, gold);
        InventoryService.gI().addItemBag(pl, coupon);
        InventoryService.gI().addItemBag(pl, mount);
        InventoryService.gI().addItemBag(pl, pet);
        InventoryService.gI().addItemBag(pl, hammer);
        InventoryService.gI().addItemBag(pl, protectStone);
        InventoryService.gI().addItemBag(pl, CaiTrang);
        InventoryService.gI().addItemBag(pl, capsule);
        InventoryService.gI().addItemBag(pl, captainCard);

        InventoryService.gI().sendItemBags(pl);
    }

    private void VIP4(Player pl) {
        long currentTime = System.currentTimeMillis();
        long time = 30L * 24 * 60 * 60 * 1000;

        if (pl.timevip < currentTime) {
            pl.timevip = currentTime + time;
        } else {
            pl.timevip += time;
        }

        Item gold = ItemService.gI().createNewItem((short) 457, 1000);

        Item coupon = ItemService.gI().createNewItem((short) 459, 10);

        Item mabuSidekick = ItemService.gI().createNewItem((short) 568, 1); // Tặng 1 đệ tử mabu

        Item protectStone = ItemService.gI().createNewItem((short) 987, 50);

        Item submarine = ItemService.gI().createNewItem((short) 1554); // tàu ngầm cam 19 vĩnh viễn
        submarine.itemOptions.add(new ItemOption(50, 15));
        submarine.itemOptions.add(new ItemOption(77, 15));
        submarine.itemOptions.add(new ItemOption(103, 15));
        submarine.itemOptions.add(new ItemOption(14, 10));

        Item petDragon = ItemService.gI().createNewItem((short) 1771); // pet rồng nhí vĩnh viễn
        petDragon.itemOptions.add(new ItemOption(50, 18));
        petDragon.itemOptions.add(new ItemOption(77, 18));
        petDragon.itemOptions.add(new ItemOption(5, 18)); // Chí mạng
        petDragon.itemOptions.add(new ItemOption(14, 10)); // Sức mạnh
        petDragon.itemOptions.add(new ItemOption(236, 15)); // Kháng tất cả

        Item hammer = ItemService.gI().createNewItem((short) 1772); // búa vĩnh viễn
        hammer.itemOptions.add(new ItemOption(50, 15));
        hammer.itemOptions.add(new ItemOption(77, 15));
        hammer.itemOptions.add(new ItemOption(103, 15));
        hammer.itemOptions.add(new ItemOption(236, 15)); // Kháng tất cả

        Item CaiTrang = ItemService.gI().createNewItem((short) 1557);
        CaiTrang.itemOptions.add(new ItemOption(50, 25));
        CaiTrang.itemOptions.add(new ItemOption(77, 25));
        CaiTrang.itemOptions.add(new ItemOption(117, 25)); // HP
        CaiTrang.itemOptions.add(new ItemOption(236, 25)); // Kháng tất cả

        Item capsule = ItemService.gI().createNewItem((short) 1655, 5); // 5 viên capsule kích hoạt
        capsule.itemOptions.add(new ItemOption(30, 0));

        Item dragonCard = ItemService.gI().createNewItem((short) 1204, 20); // 20 thẻ rồng thần

        InventoryService.gI().addItemBag(pl, gold);
        InventoryService.gI().addItemBag(pl, coupon);
        InventoryService.gI().addItemBag(pl, mabuSidekick);
        InventoryService.gI().addItemBag(pl, protectStone);
        InventoryService.gI().addItemBag(pl, submarine);
        InventoryService.gI().addItemBag(pl, petDragon);
        InventoryService.gI().addItemBag(pl, hammer);
        InventoryService.gI().addItemBag(pl, CaiTrang);
        InventoryService.gI().addItemBag(pl, capsule);
        InventoryService.gI().addItemBag(pl, dragonCard);

        InventoryService.gI().sendItemBags(pl);
    }
}
