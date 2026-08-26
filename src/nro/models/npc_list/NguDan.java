package nro.models.npc_list;

import nro.models.consts.ConstNpc;
import nro.models.fishing.FishingProgressService;
import nro.models.fishing.FishingService;
import nro.models.map.service.ChangeMapService;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.services.Service;
import nro.models.shop.ShopService;

/** NPC 113 - entry point and hub controller for the Fishing Event. */
public class NguDan extends Npc {

    private static final int MAP_DAO_KAME = 5;
    private static final int MAP_LANG_CHAI = 186;
    private static final int MAP_KHU_DANH_BAT = 192;

    private static final int MENU_SERVICES = 11301;
    private static final int MENU_CRAFT = 11302;
    private static final int MENU_CLEANING_REWARDS = 11303;
    private static final int MENU_QUESTS = 11304;
    private static final int MENU_GUIDE_FIRST = 11310;
    private static final int MENU_GUIDE_LAST = MENU_GUIDE_FIRST + 6;
    private static final int MENU_ABANDON_QUEST = 11320;

    private static final String[] GUIDE_PAGES = {
        "LỜI HỨA CỦA BIỂN\n\nNgày trước, nước quanh Đảo Kame trong đến mức người trên bờ có thể thấy đàn cá lấp lánh dưới đáy. Sau một trận bão lớn, lưới rách và kim loại vụn trôi dạt khắp nơi. Các Ngư Phủ đầu tiên đã lập nên Làng Chài với lời hứa: đánh bắt vừa đủ và luôn trả lại cho biển sự trong lành.",
        "BƯỚC CHÂN ĐẦU TIÊN\n\nKhi đến Làng Chài, hãy nhận Bộ câu khởi đầu. Món quà này chỉ nhận một lần; sau đó lựa chọn nhận bộ câu sẽ tự biến mất. Hãy chọn mồi trong hành trang rồi dùng cần câu để thả dây xuống nước.",
        "NĂM NGƯ TRƯỜNG\n\nBạn có thể câu tại Làng Chài, Khu vực đánh bắt, Khu Câu cá 1, Khu Câu cá 2 và Khu Câu cá 3. Cả năm nơi đều có thể xuất hiện đủ các loài cá; sức của cần, loại mồi và độ khó của lượt kéo mới quyết định cá quý đến đâu.",
        "KHI PHAO CHUYỂN ĐỘNG\n\nDùng cần một lần để thả câu. Khi phao động, dùng lại đúng chiếc cần đó. Một chuỗi hướng Lên, Xuống, Trái, Phải sẽ hiện ra; nhập đúng theo thứ tự trước khi hết giờ. Sai một hướng hoặc quá chậm đều khiến cá giật hụt. Chuỗi dài hơn mở cơ hội gặp cá cấp cao hơn.",
        "BỘ ĐỒ NGƯ PHỦ\n\nCần tốt mở giới hạn cá quý. Dây chắc giảm nguy cơ đứt, phao tốt kéo dài thời gian phản ứng, máy câu tăng sức kéo và lưỡi chùm có thể mang thêm cá phụ. Cửa hàng thường bán vật dụng cơ bản; Cửa hàng Điểm đổi trang bị cao nhất và cải trang bằng Xu Ngư Phủ.",
        "SỔ TAY VÀ NHIỆM VỤ\n\nSổ sưu tầm chỉ hiển thị các thẻ nhân vật. Gặp Ngư Dân và chọn Sổ tay để mở riêng Sổ Tay Ngư Phủ. Loài chưa câu sẽ bị khóa và mất màu; lần câu đầu tiên sẽ khôi phục hình ảnh cùng số lần bắt được. Mỗi ngày được nhận tối đa mười nhiệm vụ. Có thể xóa nhiệm vụ đang nhận, nhưng lượt đã dùng sẽ không được hoàn lại.",
        "GIỮ BIỂN SẠCH\n\nNếu kéo lên rác, đừng vứt trở lại biển. Mang rác đến mục Dọn rác để nhận Điểm Làm Sạch, rồi đổi điểm lấy xu, mồi, rương và Huy Hiệu. Một số rác còn dùng làm nguyên liệu chế tạo. Đó là cách mỗi Ngư Phủ tiếp tục lời hứa năm xưa."
    };

    public NguDan(int mapId, int status, int cx, int cy, int tempId, int avatar) {
        super(mapId, status, cx, cy, tempId, avatar);
    }

    @Override
    public void openBaseMenu(Player player) {
        if (!canOpenNpc(player)) {
            return;
        }
        if (mapId == MAP_DAO_KAME) {
            createOtherMenu(player, ConstNpc.BASE_MENU,
                    "Ngoài khơi đang có một mùa cá lớn. Ta có thể đưa ngươi đến Làng Chài ngay bây giờ.",
                    "Đến\nLàng Chài", "Hướng dẫn", "Đóng");
            return;
        }
        if (mapId != MAP_LANG_CHAI) {
            createOtherMenu(player, ConstNpc.BASE_MENU, "Ta đang trông nom bến cảng này.", "Đóng");
            return;
        }
        if (!player.itemEvent.fishingStarterClaimed) {
            createOtherMenu(player, ConstNpc.BASE_MENU,
                    "Chào mừng đến Làng Chài. Hãy nhận bộ câu, khám phá ngư trường và ghi đầy Sổ Tay Ngư Phủ.",
                    "Nhận\nbộ câu", "Ra\nngư trường", "Cửa hàng", "Cửa hàng\nĐiểm",
                    "Sổ tay", "Nhiệm vụ", "Dịch vụ", "Hướng dẫn");
        } else {
            createOtherMenu(player, ConstNpc.BASE_MENU,
                    "Biển hôm nay rất đẹp. Ngươi muốn chuẩn bị gì trước khi thả câu?",
                    "Ra\nngư trường", "Cửa hàng", "Cửa hàng\nĐiểm", "Sổ tay",
                    "Nhiệm vụ", "Dịch vụ", "Hướng dẫn");
        }
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (!canOpenNpc(player)) {
            return;
        }
        if (player.idMark.isBaseMenu()) {
            if (mapId == MAP_DAO_KAME) {
                handleKameMenu(player, select);
            } else if (mapId == MAP_LANG_CHAI) {
                handleVillageMenu(player, select);
            }
            return;
        }
        int menuId = player.idMark.getIndexMenu();
        if (menuId >= MENU_GUIDE_FIRST && menuId <= MENU_GUIDE_LAST) {
            handleGuidePage(player, menuId - MENU_GUIDE_FIRST, select);
            return;
        }
        if (mapId != MAP_LANG_CHAI) {
            return;
        }
        switch (menuId) {
            case MENU_SERVICES -> handleServices(player, select);
            case MENU_CRAFT -> handleCraft(player, select);
            case MENU_CLEANING_REWARDS -> handleCleaningRewards(player, select);
            case MENU_QUESTS -> handleQuests(player, select);
            case MENU_ABANDON_QUEST -> handleAbandonQuest(player, select);
            default -> {
            }
        }
    }

    private void handleKameMenu(Player player, int select) {
        switch (select) {
            case 0 -> ChangeMapService.gI().changeMapNonSpaceship(player, MAP_LANG_CHAI, 120, 648);
            case 1 -> showGuide(player);
            default -> {
            }
        }
    }

    private void handleVillageMenu(Player player, int select) {
        int option = select;
        if (!player.itemEvent.fishingStarterClaimed) {
            if (option == 0) {
                if (FishingService.gI().claimStarterKit(player)) {
                    openBaseMenu(player);
                }
                return;
            }
            option--;
        }
        switch (option) {
            case 0 -> ChangeMapService.gI().changeMapNonSpaceship(player, MAP_KHU_DANH_BAT, 72, 792);
            case 1 -> ShopService.gI().opendShop(player, "FISHING_SHOP", true);
            case 2 -> ShopService.gI().opendShop(player, "FISHING_POINT_SHOP", true);
            case 3 -> FishingProgressService.gI().openFishBook(player);
            case 4 -> openQuests(player);
            case 5 -> openServices(player);
            case 6 -> showGuide(player);
            default -> {
            }
        }
    }

    private void openServices(Player player) {
        createOtherMenu(player, MENU_SERVICES,
                "Dịch vụ Làng Chài: đổi cá, thu gom rác biển, đổi Điểm Làm Sạch hoặc chế tạo vật phẩm.",
                "Đổi toàn bộ\ncá", "Dọn rác", "Đổi Điểm\nLàm Sạch", "Chế tạo", "Quay lại");
    }

    private void handleServices(Player player, int select) {
        switch (select) {
            case 0 -> FishingService.gI().exchangeAllFish(player);
            case 1 -> FishingService.gI().cleanPollution(player);
            case 2 -> openCleaningRewards(player);
            case 3 -> openCraft(player);
            case 4 -> openBaseMenu(player);
            default -> {
            }
        }
    }

    private void openCraft(Player player) {
        createOtherMenu(player, MENU_CRAFT,
                "Tận dụng rác biển, Điểm Làm Sạch và Xu Ngư Phủ để chế tạo đồ hỗ trợ.",
                "Bộ Sửa\n20 Xu", "Hộp Mồi\n10 Xu + 5 Điểm", "3 Mồi Pha Lê\n20 Xu",
                "Rương Ngư Phủ\n50 Xu + 20 Điểm", "Quay lại");
    }

    private void handleCraft(Player player, int select) {
        switch (select) {
            case 0 -> FishingService.gI().craft(player, FishingService.CRAFT_REPAIR_KIT);
            case 1 -> FishingService.gI().craft(player, FishingService.CRAFT_BAIT_BOX);
            case 2 -> FishingService.gI().craft(player, FishingService.CRAFT_CRYSTAL_BAIT);
            case 3 -> FishingService.gI().craft(player, FishingService.CRAFT_FISHER_CHEST);
            case 4 -> openServices(player);
            default -> {
            }
        }
    }

    private void openCleaningRewards(Player player) {
        createOtherMenu(player, MENU_CLEANING_REWARDS,
                "Điểm hiện có: " + player.itemEvent.fishingCleaningPoints
                + ". Rương đổi bằng điểm được giới hạn hai lần mỗi ngày.",
                "10 Điểm\n10 Xu", "25 Điểm\nHộp Mồi", "50 Điểm\nRương Ngư Phủ",
                "100 Điểm\nHuy Hiệu + Rương", "Quay lại");
    }

    private void handleCleaningRewards(Player player, int select) {
        switch (select) {
            case 0, 1, 2, 3 -> {
                FishingService.gI().redeemCleaningReward(player, select);
                openCleaningRewards(player);
            }
            case 4 -> openServices(player);
            default -> {
            }
        }
    }

    private void openQuests(Player player) {
        FishingProgressService progress = FishingProgressService.gI();
        if (!progress.hasActiveQuest(player)) {
            if (progress.hasReachedDailyQuestLimit(player)) {
                createOtherMenu(player, MENU_QUESTS, progress.questMenuText(player), "Quay lại");
                return;
            }
            createOtherMenu(player, MENU_QUESTS, progress.questMenuText(player),
                    "Dễ\nCá phổ thông", "Vừa\nCá khá hiếm", "Khó\nCá hiếm",
                    "Cực khó\nCá quý", "Quay lại");
        } else if (progress.isActiveQuestComplete(player)) {
            createOtherMenu(player, MENU_QUESTS, progress.questMenuText(player),
                    "Nhận thưởng", "Xóa\nnhiệm vụ", "Quay lại");
        } else {
            createOtherMenu(player, MENU_QUESTS, progress.questMenuText(player),
                    "Cập nhật\ntiến độ", "Xóa\nnhiệm vụ", "Quay lại");
        }
    }

    private void handleQuests(Player player, int select) {
        FishingProgressService progress = FishingProgressService.gI();
        if (!progress.hasActiveQuest(player)) {
            if (progress.hasReachedDailyQuestLimit(player)) {
                if (select == 0) {
                    openBaseMenu(player);
                }
                return;
            }
            if (select >= 0 && select <= FishingProgressService.QUEST_EXTREME) {
                progress.acceptQuest(player, (byte) select);
                openQuests(player);
            } else if (select == 4) {
                openBaseMenu(player);
            }
        } else if (progress.isActiveQuestComplete(player)) {
            if (select == 0) {
                progress.claimQuestReward(player);
                openQuests(player);
            } else if (select == 1) {
                confirmAbandonQuest(player);
            } else if (select == 2) {
                openBaseMenu(player);
            }
        } else if (select == 0) {
            openQuests(player);
        } else if (select == 1) {
            confirmAbandonQuest(player);
        } else if (select == 2) {
            openBaseMenu(player);
        }
    }

    private void confirmAbandonQuest(Player player) {
        createOtherMenu(player, MENU_ABANDON_QUEST,
                "Bạn chắc chắn muốn xóa nhiệm vụ đang nhận? Tiến độ sẽ mất và lượt nhận hôm nay không được hoàn lại.",
                "Đồng ý\nxóa", "Giữ lại");
    }

    private void handleAbandonQuest(Player player, int select) {
        if (select == 0) {
            FishingProgressService.gI().abandonQuest(player);
        }
        openQuests(player);
    }

    private void showGuide(Player player) {
        showGuidePage(player, 0);
    }

    private void showGuidePage(Player player, int page) {
        int safePage = Math.max(0, Math.min(GUIDE_PAGES.length - 1, page));
        if (safePage == 0) {
            createOtherMenu(player, MENU_GUIDE_FIRST, GUIDE_PAGES[safePage], "Tiếp theo", "Đóng");
        } else if (safePage == GUIDE_PAGES.length - 1) {
            String startLabel = mapId == MAP_DAO_KAME ? "Đến\nLàng Chài" : "Ra\nngư trường";
            createOtherMenu(player, MENU_GUIDE_FIRST + safePage, GUIDE_PAGES[safePage],
                    startLabel, "Quay lại", "Đóng");
        } else {
            createOtherMenu(player, MENU_GUIDE_FIRST + safePage, GUIDE_PAGES[safePage],
                    "Tiếp theo", "Quay lại", "Đóng");
        }
    }

    private void handleGuidePage(Player player, int page, int select) {
        if (page == 0) {
            if (select == 0) {
                showGuidePage(player, 1);
            }
            return;
        }
        if (page == GUIDE_PAGES.length - 1) {
            if (select == 0) {
                if (mapId == MAP_DAO_KAME) {
                    ChangeMapService.gI().changeMapNonSpaceship(player, MAP_LANG_CHAI, 120, 648);
                } else {
                    ChangeMapService.gI().changeMapNonSpaceship(player, MAP_KHU_DANH_BAT, 72, 792);
                }
            } else if (select == 1) {
                showGuidePage(player, page - 1);
            }
            return;
        }
        if (select == 0) {
            showGuidePage(player, page + 1);
        } else if (select == 1) {
            showGuidePage(player, page - 1);
        }
    }
}
