package nro.models.npc_list;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.map.service.NpcService;
import nro.models.services.TaskService;
import nro.models.map.service.ChangeMapService;
import nro.models.services.ItemService;
import nro.models.services.PlayerService;
import nro.models.services.Service;
import nro.models.shop.ShopService;

/**
 *
 * @author By Mr Blue
 *
 */
public class Bill extends Npc {

    public Bill(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    @Override
    public void openBaseMenu(Player player) {
        if (canOpenNpc(player)) {
            TaskService.gI().checkDoneTaskTalkNpc(player, this);
            player.idMark.setIndexMenu(ConstNpc.BASE_MENU);

            if (mapId == 154) {
                createOtherMenu(player, ConstNpc.BASE_MENU,
                        "...",
                        "Về\nthánh địa\nKaio", "Từ chối");
            } else {
                createOtherMenu(player, ConstNpc.BASE_MENU,
                        "Chưa tới giờ thi đấu, xem hướng dẫn để biết thêm chi tiết",
                        "Nói\nchuyện", "Hướng\ndẫn\nthêm", "Đổi thức ăn\nlấy phiếu ăn", "Đổi phiếu ăn\nlấy quà", "Từ chối");
            }
        }
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (!canOpenNpc(player)) {
            return;
        }

        switch (mapId) {
            case 48 -> {
                switch (player.idMark.getIndexMenu()) {
                    case ConstNpc.BASE_MENU -> {
                        switch (select) {
                            case 0 -> {
                                if (InventoryService.gI().canOpenBillShop(player)) {
                                    createOtherMenu(player, 2,
                                            "Đói bụng quá... ngươi mang cho ta 99 phần đồ ăn\n"
                                            + "ta sẽ cho một món đồ Hủy Diệt.\n"
                                            + "Nếu tâm trạng ta vui ngươi có thể nhận trang bị tăng đến 15%",
                                            "OK", "Từ chối");
                                    player.idMark.setIndexMenu(100);
                                } else {
                                    createOtherMenu(player, 2,
                                            "Ngươi trang bị đủ bộ 5 món trang bị Thần\n"
                                            + "và mang 99 phần đồ ăn tới đây...\n"
                                            + "rồi ta nói chuyện tiếp.",
                                            "OK");
                                    player.idMark.setIndexMenu(101);
                                }
                            }
                            case 1 ->
                                NpcService.gI().createTutorial(player, tempId, this.avartar, ConstNpc.HUONG_DAN_BILL);
                            case 2 -> {
                                createOtherMenu(player, 10, "Ngươi có muốn đổi thức ăn thành phiếu ăn nhanh không?",
                                        "Đồng ý",
                                        "Từ chối");
                                player.idMark.setIndexMenu(10);
                            }
                            case 3 -> {
                                ShopService.gI().opendShop(player, "SHOP_SU_KIEN_VL", true);
                            }
                        }
                    }
                    case 10 -> {
                        if (select == 0) {
                            doiNhanhItem(player);
                        }
                    }
                }
            }

            case 2 -> {
                if (select == 100 && InventoryService.gI().canOpenBillShop(player)) {
                    ShopService.gI().opendShop(player, "BILL", true);
                }
            }

            case 154 -> {
                if (select == 0) {
                    ChangeMapService.gI().changeMap(player, 50, -1, 318, 336);
                }
            }
        }
    }

    private void doiNhanhItem(Player player) {
        int[] itemIds = {1798, 1799, 1800, 1801, 1802};
        Set<Integer> itemIdSet = Arrays.stream(itemIds).boxed().collect(Collectors.toSet());
        int total1805 = 0;

        for (int id : itemIds) {
            int count = 0;
            List<Item> itemsToSubtract = new ArrayList<>();

            for (Item item : player.inventory.itemsBag) {
                if (item != null && item.template != null && item.template.id == id) {
                    count += item.quantity;
                    itemsToSubtract.add(item);
                }
            }

            int soLanDoi = count / 99;
            int soLuongTru = soLanDoi * 99;

            if (soLanDoi > 0) {
                total1805 += soLanDoi;

                int remaining = soLuongTru;
                for (Item item : itemsToSubtract) {
                    if (remaining <= 0) {
                        break;
                    }

                    int toSub = Math.min(remaining, item.quantity);
                    InventoryService.gI().subQuantityItemsBag(player, item, toSub);
                    remaining -= toSub;
                }
            }
        }

        if (total1805 > 0) {
            Item item1805 = ItemService.gI().createNewItem((short) 1805);
            item1805.quantity = total1805;
            InventoryService.gI().addItemBag(player, item1805);

            PlayerService.gI().sendInfoHpMpMoney(player);
            InventoryService.gI().sendItemBags(player);
            Service.gI().sendThongBao(player, "Bạn đã đổi thành công " + total1805 + " x Phiếu ăn.");
        } else {
            Service.gI().sendThongBao(player, "Bạn không có đủ 99 phần của bất kỳ vật phẩm nào để đổi.");
        }
    }

}
