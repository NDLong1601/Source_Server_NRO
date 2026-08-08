package nro.models.npc_list;

import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import nro.models.matches.dai_hoi_vo_thuat.The23rdMartialArtCongressService;
import nro.models.matches.dai_hoi_vo_thuat.WorldMartialArtsTournamentService;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.map.service.NpcService;
import nro.models.services.PlayerService;
import nro.models.services.Service;
import nro.models.map.service.ChangeMapService;
import nro.models.server.Manager;
import nro.models.utils.Util;

/**
 *
 * @author By Mr Blue
 * 
 */

public class GhiDanh extends Npc {

    String[] menuselect = new String[]{};

    public GhiDanh(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    @Override
    public void openBaseMenu(Player pl) {
        if (canOpenNpc(pl)) {
            switch (this.mapId) {
                case 42, 43, 44 ->
                    this.createOtherMenu(pl, ConstNpc.MAY_DAM, "Tính điểm máy đấm nào các thí sinh\nMáy đấm đằng kia không phải tôi.",
                            "Top 100\n Trái đất",
                            "Top 100\nNamek",
                            "Top 100\nXayda",
                            "Xem điểm",
                            "Đóng");
                case 52 ->
                    WorldMartialArtsTournamentService.menu(this, pl);
                case 129 -> {
                    if (Util.isAfterMidnight(pl.lastTimePKDHVT23)) {
                        pl.goldChallenge = 50_000;
                        pl.rubyChallenge = 2;
                        pl.levelWoodChest = 0;
                    }
                    long goldchallenge = pl.goldChallenge;
                    long rubychallenge = pl.rubyChallenge;
                    // Hướng dẫn thêm - Hủy\nđăng kí - về dhvt
                    if (pl.levelWoodChest == 0) {
                        menuselect = new String[]{"Hướng\ndẫn\nthêm", "Thi đấu\n" + Util.numberToMoney(rubychallenge) + " ngọc", "Thi đấu\n" + Util.numberToMoney(goldchallenge) + " vàng", "Về\nĐại Hội\nVõ Thuật"};
                    } else {
                        menuselect = new String[]{"Hướng\ndẫn\nthêm", "Thi đấu\n" + Util.numberToMoney(rubychallenge) + " ngọc", "Thi đấu\n" + Util.numberToMoney(goldchallenge) + " vàng", "Nhận\nthưởng\nRương Cấp\n" + pl.levelWoodChest, "Về\nĐại Hội\nVõ Thuật"};
                    }
                    this.createOtherMenu(pl, ConstNpc.BASE_MENU, "Đại hội võ thuật lần thứ 23\nDiễn ra bất kể ngày đêm, ngày nghỉ, ngày lễ\nPhần thưởng vô cùng quý giá\nNhanh chóng tham gia nào", menuselect, "Từ chối");
                }
                default ->
                    super.openBaseMenu(pl);
            }
        }
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (canOpenNpc(player)) {
            if (this.mapId == 52) {
                WorldMartialArtsTournamentService.confirm(this, player, select);
            } else if (this.mapId == 129) {
                switch (player.idMark.getIndexMenu()) {
                    case ConstNpc.BASE_MENU -> {
                        long goldchallenge = player.goldChallenge;
                        long rubychallenge = player.rubyChallenge;
                        if (player.levelWoodChest == 0) {
                            switch (select) {
                                case 0 ->
                                    NpcService.gI().createTutorial(player, tempId, this.avartar, ConstNpc.NPC_DHVT23);
                                case 1, 2 -> {
                                    if (player.levelWoodChest != 12) {
                                        if (InventoryService.gI().finditemWoodChest(player)) {
                                            if (select == 1) {
                                                if (player.inventory.gem >= rubychallenge) {
                                                    The23rdMartialArtCongressService.gI().startChallenge(player);
                                                    player.inventory.gem -= (rubychallenge);
                                                    PlayerService.gI().sendInfoHpMpMoney(player);
                                                    player.goldChallenge *= 2;
                                                    player.rubyChallenge += 2;
                                                } else {
                                                    Service.gI().sendThongBao(player, "Bạn không đủ ngọc, còn thiếu " + Util.numberToMoney(rubychallenge - player.inventory.ruby) + " ngọc nữa");
                                                }
                                            } else {
                                                if (player.inventory.gold >= goldchallenge) {
                                                    The23rdMartialArtCongressService.gI().startChallenge(player);
                                                    player.inventory.gold -= (goldchallenge);
                                                    PlayerService.gI().sendInfoHpMpMoney(player);
                                                    player.goldChallenge *= 2;
                                                    player.rubyChallenge += 2;
                                                } else {
                                                    Service.gI().sendThongBao(player, "Bạn không đủ vàng, còn thiếu " + Util.numberToMoney(goldchallenge - player.inventory.gold) + " vàng nữa");
                                                }
                                            }
                                        } else {
                                            Service.gI().sendThongBao(player, "Hãy mở rương báu vật trước");
                                        }
                                    } else {
                                        Service.gI().sendThongBao(player, "Bạn đã vô địch giải. Vui lòng chờ đến ngày mai");
                                    }
                                }
                                case 3 ->
                                    ChangeMapService.gI().changeMapNonSpaceship(player, 52, player.location.x, 336);
                            }
                        } else {
                            switch (select) {
                                case 0 ->
                                    NpcService.gI().createTutorial(player, tempId, this.avartar, ConstNpc.NPC_DHVT23);
                                case 1, 2 -> {
                                    if (player.levelWoodChest != 12) {
                                        if (InventoryService.gI().finditemWoodChest(player)) {
                                            if (select == 1) {
                                                if (player.inventory.gem >= rubychallenge) {
                                                    The23rdMartialArtCongressService.gI().startChallenge(player);
                                                    player.inventory.gem -= (rubychallenge);
                                                    PlayerService.gI().sendInfoHpMpMoney(player);
                                                    player.goldChallenge *= 2;
                                                    player.rubyChallenge += 2;
                                                } else {
                                                    Service.gI().sendThongBao(player, "Bạn không đủ ngọc, còn thiếu " + Util.numberToMoney(rubychallenge - player.inventory.gem) + " ngọc nữa");
                                                }
                                            } else {
                                                if (player.inventory.gold >= goldchallenge) {
                                                    The23rdMartialArtCongressService.gI().startChallenge(player);
                                                    player.inventory.gold -= (goldchallenge);
                                                    PlayerService.gI().sendInfoHpMpMoney(player);
                                                    player.goldChallenge *= 2;
                                                    player.rubyChallenge += 2;
                                                } else {
                                                    Service.gI().sendThongBao(player, "Bạn không đủ vàng, còn thiếu " + Util.numberToMoney(goldchallenge - player.inventory.gold) + " vàng nữa");
                                                }
                                            }
                                        } else {
                                            Service.gI().sendThongBao(player, "Hãy mở rương báu vật trước");
                                        }
                                    } else {
                                        Service.gI().sendThongBao(player, "Bạn đã vô địch giải. Vui lòng chờ đến ngày mai");
                                    }
                                }
                                case 3 ->
                                    this.createOtherMenu(player, 1, "Phần thưởng của bạn đang ở cấp " + player.levelWoodChest + " / 12\n"
                                            + "Mỗi ngày chỉ được nhận được nhận thưởng 1 lần\n"
                                            + "bạn có chắc sẽ nhận phần thưởng ngay bây giờ?", "OK", "Từ chối");
                                case 4 ->
                                    ChangeMapService.gI().changeMapNonSpaceship(player, 52, player.location.x, 336);
                            }
                        }
                    }
                    case 1 -> {
                        if (select == 0) {
                            if (InventoryService.gI().finditemWoodChest(player)) {
                                if (InventoryService.gI().getCountEmptyBag(player) > 0) {
                                    Item it = ItemService.gI().createNewItem((short) 570);
                                    it.itemOptions.add(new Item.ItemOption(72, player.levelWoodChest));
                                    it.itemOptions.add(new Item.ItemOption(30, 0));
                                    it.createTime = System.currentTimeMillis();
                                    InventoryService.gI().addItemBag(player, it);
                                    InventoryService.gI().sendItemBags(player);
                                    player.levelWoodChest = 0;
                                    player.lastTimeRewardWoodChest = System.currentTimeMillis();
                                    NpcService.gI().createMenuConMeo(player, -1, -1, "Bạn nhận được\n|1|Rương Gỗ\n|2|Giấu bên trong nhiều vật phẩm quý giá", "OK");
                                } else {
                                    this.npcChat(player, "Hành trang đã đầy, cần một ô trống trong hành trang để nhận vật phẩm");
                                }
                            } else {
                                Service.gI().sendThongBao(player, "Hãy mở rương báu vật trước");
                            }
                        }
                    }
                }
            } else if (this.mapId == 44 || this.mapId == 43 || this.mapId == 42) {
                switch (player.idMark.getIndexMenu()) {
                    case ConstNpc.MAY_DAM -> {
                        switch (select) {
                            case 0 ->
                                Service.gI().showListTopTraiDat(player, Manager.Topmaydam);
                            case 1 ->
                                Service.gI().showListTopNamek(player, Manager.Topmaydam);
                            case 2 ->
                                Service.gI().showListTopXayda(player, Manager.Topmaydam);
                            case 3 ->
                                Service.gI().sendThongBao(player, "Điểm hiện tại của bạn là: " + player.point_maydam);
                            }
                        }
                    }
                }
            }
        }
    }

