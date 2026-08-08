package nro.models.npc_list;

import nro.models.consts.ConstNpc;
import java.util.ArrayList;
import nro.models.services_dungeon.MajinBuu14HService;
import nro.models.services_dungeon.MajinBuuService;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.services.ItemTimeService;
import nro.models.map.service.NpcService;
import nro.models.services.Service;
import nro.models.services.TaskService;
import nro.models.map.service.ChangeMapService;
import nro.models.map.service.MapService;
import nro.models.services.InventoryService;
import nro.models.shop.ShopService;
import nro.models.utils.TimeUtil;
import nro.models.utils.Util;

public class Osin extends Npc {

    public Osin(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    @Override
    public void openBaseMenu(Player player) {
        if (canOpenNpc(player)) {
            TaskService.gI().checkDoneTaskTalkNpc(player, this);
            switch (this.mapId) {
                case 50 ->
                    this.createOtherMenu(player, ConstNpc.BASE_MENU, "Ta có thể giúp gì cho ngươi ?",
                            "Đến\nKaio", "Đến\nhành tinh\nBill", "Từ chối");
                case 154 ->
                    this.createOtherMenu(player, ConstNpc.BASE_MENU, "Ta có thể giúp gì cho ngươi ?",
                            "Đến\nhành tinh\nngục tù", "Từ chối");
                case 155 ->
                    this.createOtherMenu(player, ConstNpc.BASE_MENU, "Ta có thể giúp gì cho ngươi ?",
                            "Quay về", "Từ chối");
                case 165 ->
                    this.createOtherMenu(player, ConstNpc.BASE_MENU, "Mau hút năng lượng tà ác trong người cậu ấy (Dùng tự động luyện tập)",
                            "Về nhà", "Bùa hỗ\ntrợ", "Từ chối");
                case 52 -> {
                    player.fightMabu.clear();
                    boolean hasEnergyJar = InventoryService.gI().findItemBag(player, 1795) != null;
                    if (TimeUtil.isMabu14HOpen()) {
                        if (hasEnergyJar) {
                            this.createOtherMenu(player, ConstNpc.MENU_OPEN_MMB_WITH_JAR,
                                    "Mabư đã thoát khỏi vỏ bọc\nmau đi cùng ta ngăn chặn hắn lại\ntrước khi hắn tàn phá trái đất này",
                                    "OK", "Bình hút năng lượng", "Từ chối");
                        } else {
                            this.createOtherMenu(player, ConstNpc.MENU_OPEN_MMB_NO_JAR,
                                    "Mabư đã thoát khỏi vỏ bọc\nmau đi cùng ta ngăn chặn hắn lại\ntrước khi hắn tàn phá trái đất này",
                                    "OK", "Từ chối");
                        }
                    } else if (TimeUtil.isMabuOpen()) {
                        if (hasEnergyJar) {
                            this.createOtherMenu(player, ConstNpc.MENU_OPEN_MMB_WITH_JAR,
                                    "Bây giờ tôi sẽ bí mật...\nđuổi theo 2 tên đồ tể...\nQuý vị nào muốn đi theo thì xin mời !",
                                    "OK", "Bình hút năng lượng", "Từ chối");
                        } else {
                            this.createOtherMenu(player, ConstNpc.MENU_OPEN_MMB_NO_JAR,
                                    "Bây giờ tôi sẽ bí mật...\nđuổi theo 2 tên đồ tể...\nQuý vị nào muốn đi theo thì xin mời !",
                                    "OK", "Từ chối");
                        }
                    } else {
                        if (hasEnergyJar) {
                            this.createOtherMenu(player, ConstNpc.MENU_NOT_OPEN_MMB_WITH_JAR,
                                    "Vào lúc " + MajinBuuService.HOUR_OPEN_MAP_MABU + "h tôi sẽ bí mật...\nđuổi theo 2 tên đồ tể...\nQuý vị nào muốn đi theo thì xin mời !",
                                    "Ok", "Bình hút năng lượng");
                        } else {
                            this.createOtherMenu(player, ConstNpc.MENU_NOT_OPEN_MMB_NO_JAR,
                                    "Vào lúc " + MajinBuuService.HOUR_OPEN_MAP_MABU + "h tôi sẽ bí mật...\nđuổi theo 2 tên đồ tể...\nQuý vị nào muốn đi theo thì xin mời !",
                                    "Ok");
                        }
                    }
                }
                case 114, 115, 117, 118, 119, 120 -> {
                    if (player.cFlag != 9) {
                        NpcService.gI().createTutorial(player, tempId, avartar, "Ngươi hãy về phe của mình mà thể hiện");
                        return;
                    }
                    String npcSay = "Đừng vội xem thường Babiđây, ngay đến cha hắn là thần ma đạo sĩ Bibiđây khi còn sống cũng phải sợ hắn đấy!";
                    ArrayList<String> menuAL = new ArrayList<>();
                    menuAL.add("Hướng\ndẫn\nthêm");
                    if (!player.itemTime.isUseGTPT) {
                        menuAL.add("Giải trừ\nphép thuật\n1 ngọc");
                    }
                    if (player.fightMabu.pointMabu >= player.fightMabu.POINT_MAX && this.mapId != 120) {
                        menuAL.add("Xuống\nTầng dưới");
                    }
                    String[] menus = menuAL.toArray(new String[0]);
                    this.createOtherMenu(player, ConstNpc.GO_UPSTAIRS_MENU, npcSay, menus);
                }
                case 127 -> {
                    String npcSay = player.isPhuHoMapMabu ? "Ta có thể giúp gì cho ngươi ?" : "Ta sẽ phù hộ ngươi bằng\nnguồn sức mạnh của Thần Kaiô\n+1 triệu HP, +1 triệu KI, +10k Sức đánh\nLưu ý: sức mạnh này sẽ biến mất khi ngươi rời khỏi đây";
                    ArrayList<String> menuAL = new ArrayList<>();
                    if (!player.isPhuHoMapMabu) {
                        menuAL.add("Phù hộ\n10 ngọc");
                    }
                    menuAL.add("Từ chối");
                    menuAL.add("Về\nĐại Hội\nVõ Thuật");
                    String[] menus = menuAL.toArray(new String[0]);
                    this.createOtherMenu(player, ConstNpc.BASE_MENU, npcSay, menus);
                }
                default ->
                    super.openBaseMenu(player);
            }
        }
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (!canOpenNpc(player)) {
            return;
        }

        int mapId = this.mapId;
        int indexMenu = player.idMark.getIndexMenu();

        switch (mapId) {
            case 50 -> {
                if (player.idMark.isBaseMenu()) {
                    switch (select) {
                        case 0 ->
                            ChangeMapService.gI().changeMap(player, 48, -1, 354, 240);
                        case 1 ->
                            ChangeMapService.gI().changeMap(player, 154, -1, 200, 312);
                    }
                }
            }

            case 154 -> {
                if (player.idMark.isBaseMenu()) {
                    if (select == 0) {
                        ChangeMapService.gI().changeMap(player, 155, -1, 111, 792);
                    }
                }
            }

            case 155 -> {
                if (player.idMark.isBaseMenu()) {
                    if (select == 0) {
                        ChangeMapService.gI().changeMap(player, 154, -1, 200, 312);
                    }
                }
            }

            case 52 -> {
                switch (indexMenu) {
                    case ConstNpc.MENU_OPEN_MMB_WITH_JAR -> {
                        if (select == 0) {
                            if (TimeUtil.isMabu14HOpen()) {
                                MajinBuu14HService.gI().joinMaBu2H(player);
                            } else if (TimeUtil.isMabuOpen()) {
                                ChangeMapService.gI().changeMap(player, 114, -1, Util.nextInt(100, 500), 312);
                            }
                        } else if (select == 1) {
                            createOtherMenu(player, ConstNpc.BINH_HUT_NANG_LUONG,
                                    "Cadic đã bị phù thủy Babidi thôi miên\nhãy mang bình hút năng lượng đến đây\nhút cạn năng lượng tà ác trong cậu ấy",
                                    "Ok", "Từ chối");
                        }
                    }

                    case ConstNpc.MENU_OPEN_MMB_NO_JAR -> {
                        if (select == 0) {
                            if (TimeUtil.isMabu14HOpen()) {
                                MajinBuu14HService.gI().joinMaBu2H(player);
                            } else if (TimeUtil.isMabuOpen()) {
                                ChangeMapService.gI().changeMap(player, 114, -1, Util.nextInt(100, 500), 336);
                            }
                        }
                    }

                    case ConstNpc.MENU_NOT_OPEN_MMB_WITH_JAR -> {
                        if (select == 1) {
                            createOtherMenu(player, ConstNpc.BINH_HUT_NANG_LUONG,
                                    "Cadic đã bị phù thủy Babidi thôi miên\nhãy mang bình hút năng lượng đến đây\nhút cạn năng lượng tà ác trong cậu ấy",
                                    "Ok", "Từ chối");
                        }
                    }

                    case ConstNpc.BINH_HUT_NANG_LUONG -> {
                        if (select == 0) {
                            ChangeMapService.gI().changeMap(player, 165, -1, Util.nextInt(100, 500), 312);
                        }
                    }

                    case 114, 115, 117, 118, 119, 120 -> {
                        if (player.cFlag != 9) {
                            return;
                        }

                        switch (select) {
                            case 0 ->
                                NpcService.gI().createTutorial(player, tempId, 4388, ConstNpc.HUONG_DAN_MAP_MA_BU);
                            case 1 -> {
                                if (!player.itemTime.isUseGTPT) {
                                    player.itemTime.lastTimeUseGTPT = System.currentTimeMillis();
                                    player.itemTime.isUseGTPT = true;
                                    ItemTimeService.gI().sendAllItemTime(player);
                                    Service.gI().sendThongBao(player, "Phép thuật đã được giải trừ, sức đánh của bạn đã tăng theo điểm tích lũy");
                                } else if (player.fightMabu.pointMabu >= player.fightMabu.POINT_MAX && mapId != 120) {
                                    ChangeMapService.gI().changeMap(player, map.mapIdNextMabu((short) mapId), -1, cx, cy);
                                }
                            }
                            case 2 -> {
                                if (!player.itemTime.isUseGTPT && player.fightMabu.pointMabu >= player.fightMabu.POINT_MAX && mapId != 120) {
                                    ChangeMapService.gI().changeMap(player, map.mapIdNextMabu((short) mapId), -1, cx, cy);
                                }
                            }
                        }
                    }

                    case 127 -> {
                        switch (select) {
                            case 0 -> {
                                if (!player.isPhuHoMapMabu) {
                                    if (player.inventory.getGem() < 10) {
                                        Service.gI().sendThongBao(player, "Bạn không có đủ ngọc");
                                    } else {
                                        player.inventory.subGem(10);
                                        player.isPhuHoMapMabu = true;
                                        player.nPoint.calPoint();
                                        player.nPoint.setHp((int) player.nPoint.hpMax);
                                        player.nPoint.setMp((int) player.nPoint.mpMax);
                                        Service.gI().point(player);
                                        Service.gI().Send_Info_NV(player);
                                        Service.gI().Send_Caitrang(player);
                                    }
                                }
                            }
                            case 1 -> {
                                if (player.isPhuHoMapMabu) {
                                    ChangeMapService.gI().changeMap(player, 52, -1, Util.nextInt(100, 300), 336);
                                }
                            }
                            case 2 -> {
                                if (!player.isPhuHoMapMabu) {
                                    ChangeMapService.gI().changeMap(player, 52, -1, Util.nextInt(100, 300), 336);
                                }
                            }
                        }
                    }
                }
            }

            case 165 -> {
                switch (indexMenu) {
                    case ConstNpc.BASE_MENU -> {
                        switch (select) {
                            case 0 ->
                                ChangeMapService.gI().changeMap(player, 52, -1, Util.nextInt(100, 300), 336);
                            case 1 ->
                                createOtherMenu(player, ConstNpc.BUA_HO_TRO,
                                        "Ta có thể làm phép giúp ngươi\nhút nhanh hơn gấp đôi năng lượng tà ác\ntrong 10 phút, tối đa 60 phút",
                                        "Đồng ý\n5 ngọc", "Từ chối");
                        }
                    }

                    case ConstNpc.BUA_HO_TRO -> {
                        if (select == 0) {
                            if (player.inventory.getGem() >= 5) {
                                player.inventory.subGem(5);
                                Service.gI().sendMoney(player);
                                long currentTime = System.currentTimeMillis();
                                long addedTime = 10 * 60 * 1000;
                                long maxTime = 60 * 60 * 1000; 

                                if (!player.itemTime.isUseKilis || currentTime > (player.itemTime.lastTimeUseKilis + player.itemTime.timeLengthKilis)) {
                                    player.itemTime.lastTimeUseKilis = currentTime;
                                    player.itemTime.timeLengthKilis = addedTime;
                                    player.itemTime.isUseKilis = true;
                                } else {
                                    long remainingTime = (player.itemTime.lastTimeUseKilis + player.itemTime.timeLengthKilis) - currentTime;
                                    long newDuration = Math.min(remainingTime + addedTime, maxTime);
                                    player.itemTime.lastTimeUseKilis = currentTime;
                                    player.itemTime.timeLengthKilis = newDuration;
                                }

                                ItemTimeService.gI().sendAllItemTime(player);
                            } else {
                                Service.gI().sendThongBao(player, "Bạn không có đủ ngọc!");
                            }
                        }
                    }
                }
            }
        }
    }

}
