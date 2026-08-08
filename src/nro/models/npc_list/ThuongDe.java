package nro.models.npc_list;

import nro.models.boss.BossID;
import nro.models.consts.ConstNpc;
import nro.models.services_dungeon.TrainingService;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.map.service.NpcService;
import nro.models.services.Service;
import nro.models.map.service.ChangeMapService;
import nro.models.services_func.LuckyRound;
import nro.models.shop.ShopService;

public class ThuongDe extends Npc {

    public ThuongDe(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    @Override
    public void openBaseMenu(Player player) {    
         if (canOpenNpc(player)) {
            switch (mapId) {
                case 45 -> {
                    if (player.clan != null && player.clan.ConDuongRanDoc != null && player.joinCDRD && player.clan.ConDuongRanDoc.allMobsDead && !player.talkToThuongDe) {
                        Service.gI().sendThongBao(player, "Hãy xuống gặp thần mèo Karin");
                        this.createOtherMenu(player, ConstNpc.BASE_MENU, "Hãy xuống gặp thần mèo Karin", "OK");
                        return;
                    }
                    switch (player.levelLuyenTap) {
                        case 2 ->
                            this.createOtherMenu(player, ConstNpc.BASE_MENU,
                                    "Pôpô là đệ tử của ta, luyện tập với Pôpô con sẽ có thêm nhiều kinh nghiệm\nđánh bại được Pôpô ta sẽ dạy võ công cho con",
                                    player.dangKyTapTuDong ? "Hủy đăng\nký tập\ntự động" : "Đăng ký\ntập\ntự động", "Tập luyện\nvới\nMr.PôPô", "Thách đấu\nMr.PôPô", "Đến\nKaio", "Quay ngọc\nMay mắn");
                        case 3 ->
                            this.createOtherMenu(player, ConstNpc.BASE_MENU,
                                    "Từ nay con sẽ là đệ tử của ta. Ta sẽ truyền cho con tất cả tuyệt kĩ",
                                    player.dangKyTapTuDong ? "Hủy đăng\nký tập\ntự động" : "Đăng ký\ntập\ntự động", "Tập luyện\nvới\nThượng Đế", "Thách đấu\nThượng Đế", "Đến\nKaio", "Quay ngọc\nMay mắn");
                        default ->
                            this.createOtherMenu(player, ConstNpc.BASE_MENU,
                                    "Con đã mạnh hơn ta, ta sẽ chỉ đường cho con đến Kaio\nđể gặp thần Vũ Trụ Phương Bắc\nNgài là thần cai quản vũ trụ này, hãy theo ngài ấy học võ công.",
                                    player.dangKyTapTuDong ? "Hủy đăng\nký tập\ntự động" : "Đăng ký\ntập\ntự động", "Tập luyện\nvới\nMr.PôPô", "Tập luyện\nvới\nThượng Đế", "Đến\nKaio", "Quay ngọc\nMay mắn");
                    }
                }
                case 141 ->
                    this.createOtherMenu(player, 0,
                            "Hãy nắm lấy tay ta mau!", "về\nthần điện");
            }
        }
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (canOpenNpc(player)) {
            switch (mapId) {
                case 45 -> {
                    if (player.idMark.isBaseMenu()) {
                        switch (select) {
                            case 0 -> {
                                if (player.clan != null && player.clan.ConDuongRanDoc != null && player.joinCDRD && player.clan.ConDuongRanDoc.allMobsDead && !player.talkToThuongDe) {
                                    player.talkToThuongDe = true;
                                    return;
                                }
                                if (player.dangKyTapTuDong) {
                                    player.dangKyTapTuDong = false;
                                    NpcService.gI().createTutorial(player, tempId, avartar, "Con đã hủy thành công đăng ký tập tự động\ntừ giờ con muốn tập Offline hãy tự đến đây trước");
                                    return;
                                }
                                this.createOtherMenu(player, 2001, "Đăng ký để mỗi khi Offline quá 30 phút, con sẽ được tự động luyện tập với tốc độ 1280 sức mạnh mỗi phút",
                                        "Hướng\ndẫn\nthêm", "Đồng ý\n1 ngọc\nmỗi lần", "Không\nđồng ý");
                            }
                            case 1 -> {
                                switch (player.levelLuyenTap) {
                                    case 3 ->
                                        this.createOtherMenu(player, 2002, "Con có chắc muốn tập luyện ?\nTập luyện với ta sẽ tăng 160 sức mạnh mỗi phút",
                                                "Đồng ý\nluyện tập", "Không\nđồng ý");
                                    default ->
                                        this.createOtherMenu(player, 2002, "Con có chắc muốn tập luyện ?\nTập luyện với Mr.PôPô sẽ tăng 80 sức mạnh mỗi phút",
                                                "Đồng ý\nluyện tập", "Không\nđồng ý");
                                }
                            }
                            case 2 -> {
                                switch (player.levelLuyenTap) {
                                    case 2 ->
                                        this.createOtherMenu(player, 2003, "Con có chắc muốn thách đấu ?\nNếu thắng Mr.PôPô sẽ được tập với ta, tăng 160 sức mạnh mỗi phút",
                                                "Đồng ý\ngiao đấu", "Không\nđồng ý");
                                    case 3 ->
                                        this.createOtherMenu(player, 2003, "Con có chắc muốn thách đấu ?\nNếu thắng được ta, con sẽ được học võ với người mạnh hơn ta để tăng đến 320 sức mạnh mỗi phút",
                                                "Đồng ý\ngiao đấu", "Không\nđồng ý");
                                    default ->
                                        this.createOtherMenu(player, 2003, "Con có chắc muốn tập luyện ?\nTập luyện với ta sẽ tăng 160 sức mạnh mỗi phút",
                                                "Đồng ý\nluyện tập", "Không\nđồng ý");
                                }
                            }
                            case 3 ->
                                ChangeMapService.gI().changeMapBySpaceShip(player, 48, -1, 354);
                            case 4 ->
                                this.createOtherMenu(player, ConstNpc.MENU_CHOOSE_LUCKY_ROUND,
                                        "Con muốn làm gì nào?", "Quay bằng\nvàng",
                                        "Vòng quay\nđặc biệt",
                                        "Rương phụ\n("
                                        + (player.inventory.itemsBoxCrackBall.size()
                                        - InventoryService.gI().getCountEmptyListItem(player.inventory.itemsBoxCrackBall))
                                        + " món)",
                                        "Xóa hết\ntrong rương", "Đóng");
                        }
                    } else if (player.idMark.getIndexMenu() == 2001) {
                        switch (select) {
                            case 0 ->
                                NpcService.gI().createTutorial(player, tempId, avartar, ConstNpc.TAP_TU_DONG);
                            case 1 -> {
                                player.mapIdDangTapTuDong = mapId;
                                player.dangKyTapTuDong = true;
                                NpcService.gI().createTutorial(player, tempId, avartar, "Từ giờ, quá 30 phút Offline con sẽ được tự động luyện tập");
                            }
                        }

                    } else if (player.idMark.getIndexMenu() == 2002) {
                        switch (player.levelLuyenTap) {
                            case 3 ->
                                TrainingService.gI().callBoss(player, BossID.THUONG_DE, false);
                            default ->
                                TrainingService.gI().callBoss(player, BossID.MRPOPO, false);
                        }
                    } else if (player.idMark.getIndexMenu() == 2003) {
                        switch (player.levelLuyenTap) {
                            case 2 ->
                                TrainingService.gI().callBoss(player, BossID.MRPOPO, true);
                            case 3 ->
                                TrainingService.gI().callBoss(player, BossID.THUONG_DE, true);
                            default ->
                                TrainingService.gI().callBoss(player, BossID.THUONG_DE, false);
                        }
                    } else if (player.idMark.getIndexMenu() == ConstNpc.MENU_CHOOSE_LUCKY_ROUND) {
                        switch (select) {
                            case 0 ->
                                LuckyRound.gI().openCrackBallUI(player, LuckyRound.USING_GOLD);
                            case 1 ->
                                LuckyRound.gI().openCrackBallVipUI(player, LuckyRound.USING_GOLD);
                            case 2 ->
                                ShopService.gI().opendShop(player, "ITEMS_LUCKY_ROUND", true);
                            case 3 ->
                                NpcService.gI().createMenuConMeo(player,
                                        ConstNpc.CONFIRM_REMOVE_ALL_ITEM_LUCKY_ROUND, this.avartar,
                                        "Con có chắc muốn xóa hết vật phẩm trong rương phụ? Sau khi xóa "
                                        + "sẽ không thể khôi phục!",
                                        "Đồng ý", "Hủy bỏ");
                        }
                    }
                }
                case 141 -> {
                    switch (select) {
                        case 0 -> {
                            if (player.clan == null || player.clan.ConDuongRanDoc == null || !player.clan.ConDuongRanDoc.allMobsDead) {
                                Service.gI().sendThongBao(player, "Chưa hạ hết đối thủ");
                                return;
                            }
                            ChangeMapService.gI().changeMapYardrat(player, ChangeMapService.gI().getMapCanJoin(player, 45), 295, 408);
                            Service.gI().sendThongBao(player, "Hãy xuống gặp thần mèo Karin");
                        }
                    }
                }
            }
        }
    }
}
