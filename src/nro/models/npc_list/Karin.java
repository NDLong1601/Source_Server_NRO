package nro.models.npc_list;

import nro.models.boss.BossID;
import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import nro.models.services_dungeon.TrainingService;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.map.service.NpcService;
import nro.models.services.Service;
import nro.models.services.TaskService;
import nro.models.utils.Util;

/**
 *
 * @author By Mr Blue
 * 
 */

public class Karin extends Npc {

    public Karin(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    @Override
    public void openBaseMenu(Player player) {
        if (canOpenNpc(player)) {
            if (TaskService.gI().checkDoneTaskTalkNpc(player, this)) {
                return;
            }
            if (this.mapId == 46) {
                if (player.winSTT && !Util.isAfterMidnight(player.lastTimeWinSTT)) {
                    this.createOtherMenu(player, ConstNpc.BASE_MENU, "Hãy bình tĩnh..nghe ta nói đã\nMi chưa đủ sức hạ hắn đâu!\nThôi được rồi...chờ tí\nTa sẽ cho mi uống thuốc.\nThuốc 'Tăng lực siêu thần thủy'", "Đồng ý");
                    return;
                }
                if (player.clan != null && player.clan.ConDuongRanDoc != null && player.joinCDRD && player.clan.ConDuongRanDoc.allMobsDead && player.talkToThanMeo) {
                    Service.gI().sendThongBao(player, "Hãy mau bay xuống chân tháp Karin");
                    this.createOtherMenu(player, ConstNpc.BASE_MENU, "Hãy mau bay xuống chân tháp Karin", "OK");
                    return;
                }
                if (player.clan != null && player.clan.ConDuongRanDoc != null && player.joinCDRD && player.clan.ConDuongRanDoc.allMobsDead) {
                    player.talkToThuongDe = true;
                    this.createOtherMenu(player, ConstNpc.BASE_MENU, "Cầm lấy hai hạt đậu cuối cùng của ta đây\nCố giữ mình nhé " + player.name + "!", "Cám ơn\nsư phụ");
                    return;
                }
                switch (player.levelLuyenTap) {
                    case 0 ->
                        this.createOtherMenu(player, ConstNpc.BASE_MENU, "Muốn chiến thắng Tàu Pảy Pảy phải đánh bại được ta đã", player.dangKyTapTuDong ? "Hủy đăng\nký tập\ntự động" : "Đăng ký\ntập\ntự động", "Nhiệm vụ", "Tập luyện\nvới\nThần Mèo", "Thách đấu\nThần Mèo");
                    case 1 ->
                        this.createOtherMenu(player, ConstNpc.BASE_MENU, "Từ giờ Yajirô sẽ luyện tập cùng ngươi. Yajirô đã từng lên đây tập luyện và bây giờ hắn mạnh hơn ta đấy", player.dangKyTapTuDong ? "Hủy đăng\nký tập\ntự động" : "Đăng ký\ntập\ntự động", "Tập luyện\nvới\nYajirô", "Thách đấu\nYajirô");
                    default ->
                        this.createOtherMenu(player, ConstNpc.BASE_MENU, "Con hãy bay theo cây Gậy Như Ý trên đỉnh tháp để đến Thần Điện gặp Thượng đế\nCon rất xứng đáng để làm đệ tử ông ấy.", player.dangKyTapTuDong ? "Hủy đăng\nký tập\ntự động" : "Đăng ký\ntập\ntự động", "Tập luyện\nvới\nThần Mèo", "Tập luyện\nvới\nYajirô");
                }
            }
        }
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (canOpenNpc(player)) {
            if (this.mapId == 46) {
                if (player.winSTT && !Util.isAfterMidnight(player.lastTimeWinSTT)) {
                    int itemId = player.nPoint.power < 1_000_000_000 ? 727 : 728;
                    Item item = ItemService.gI().createNewItem(((short) itemId));
                    item.itemOptions.add(new Item.ItemOption(30, 0));
                    item.itemOptions.add(new Item.ItemOption(93, 1));
                    if (InventoryService.gI().getCountEmptyBag(player) > 0) {
                        player.callBossPocolo = false;
                        player.winSTT = false;
                        player.zoneSieuThanhThuy = null;
                        InventoryService.gI().addItemBag(player, item);
                        InventoryService.gI().sendItemBags(player);
                        Service.gI().sendThongBao(player, "Bạn nhận được " + item.template.name);
                    } else {
                        Service.gI().sendThongBao(player, "Hành trang đã đầy, cần một ô trống trong hành trang");
                    }
                    return;
                }
                if (player.clan != null && player.clan.ConDuongRanDoc != null && player.joinCDRD && player.clan.ConDuongRanDoc.allMobsDead) {
                    Service.gI().sendThongBao(player, "Hãy mau bay xuống chân tháp Karin");
                    if (!player.talkToThanMeo) {
                        player.nPoint.hp = player.nPoint.hpMax;
                        player.nPoint.mp = player.nPoint.mpMax;
                        Service.gI().sendInfoPlayerEatPea(player);
                    }
                    player.talkToThanMeo = true;
                    return;
                }

                //========================LUYEN TAP========================
                if (player.idMark.isBaseMenu()) {
                    switch (player.levelLuyenTap) {
                        case 0 -> {
                            switch (select) {
                                case 0 -> {
                                    if (player.dangKyTapTuDong) {
                                        player.dangKyTapTuDong = false;
                                        NpcService.gI().createTutorial(player, tempId, avartar, "Con đã hủy thành công đăng ký tập tự động\ntừ giờ con muốn tập Offline hãy tự đến đây trước");
                                        return;
                                    }
                                    this.createOtherMenu(player, 2001, "Đăng ký để mỗi khi Offline quá 30 phút, con sẽ được tự động luyện tập với tốc độ 1280 sức mạnh mỗi phút",
                                            "Hướng\ndẫn\nthêm", "Đồng ý\n1 ngọc\nmỗi lần", "Không\nđồng ý");
                                }
                                case 1 ->
                                    this.npcChat(player, "...");
                                case 2 ->
                                    this.createOtherMenu(player, 2002, "Con có chắc muốn tập luyện ?\nTập luyện với ta sẽ tăng 20 sức mỗi phút",
                                            "Đồng ý\nluyện tập", "Không\nđồng ý");
                                case 3 ->
                                    this.createOtherMenu(player, 2003, "Con có chắc muốn thách đấu ?\nNếu thắng ta sẽ được tập luyện với Yajirô, tăng 40 sức mạnh mỗi phút",
                                            "Đồng ý\ngiao đấu", "Không\nđồng ý");
                            }
                        }

                        case 1 -> {
                            switch (select) {
                                case 0 -> {
                                    if (player.dangKyTapTuDong) {
                                        player.dangKyTapTuDong = false;
                                        NpcService.gI().createTutorial(player, tempId, avartar, "Con đã hủy thành công đăng ký tập tự động\ntừ giờ con muốn tập Offline hãy tự đến đây trước");
                                        return;
                                    }
                                    this.createOtherMenu(player, 2001, "Đăng ký để mỗi khi Offline quá 30 phút, con sẽ được tự động luyện tập với tốc độ 1280 sức mạnh mỗi phút",
                                            "Hướng\ndẫn\nthêm", "Đồng ý\n1 ngọc\nmỗi lần", "Không\nđồng ý");
                                }
                                case 1 ->
                                    this.createOtherMenu(player, 2002, "Con có chắc muốn tập luyện ?\nTập luyện với Yajirô sẽ tăng 40 sức mỗi phút",
                                            "Đồng ý\nluyện tập", "Không\nđồng ý");
                                case 2 ->
                                    this.createOtherMenu(player, 2003, "Con có chắc muốn thách đấu ?\nNếu thắng được Yajirô, con sẽ được học võ với người mạnh hơn để tăng đến 80 sức mạnh mỗi phút",
                                            "Đồng ý\ngiao đấu", "Không\nđồng ý");
                            }
                        }

                        default -> {
                            switch (select) {
                                case 0 -> {
                                    if (player.dangKyTapTuDong) {
                                        player.dangKyTapTuDong = false;
                                        NpcService.gI().createTutorial(player, tempId, avartar, "Con đã hủy thành công đăng ký tập tự động\ntừ giờ con muốn tập Offline hãy tự đến đây trước");
                                        return;
                                    }
                                    this.createOtherMenu(player, 2001, "Đăng ký để mỗi khi Offline quá 30 phút, con sẽ được tự động luyện tập với tốc độ 1280 sức mạnh mỗi phút",
                                            "Hướng\ndẫn\nthêm", "Đồng ý\n1 ngọc\nmỗi lần", "Không\nđồng ý");
                                }
                                case 1 ->
                                    this.createOtherMenu(player, 2002, "Con có chắc muốn tập luyện ?\nTập luyện với ta sẽ tăng 20 sức mỗi phút",
                                            "Đồng ý\nluyện tập", "Không\nđồng ý");
                                case 2 ->
                                    this.createOtherMenu(player, 2003, "Con có chắc muốn tập luyện ?\nTập luyện với Yajirô sẽ tăng 40 sức mỗi phút",
                                            "Đồng ý\nluyện tập", "Không\nđồng ý");
                            }
                        }

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
                    if (select == 0) {
                        switch (player.levelLuyenTap) {
                            case 0 ->
                                TrainingService.gI().callBoss(player, BossID.KARIN, false);
                            case 1 ->
                                TrainingService.gI().callBoss(player, BossID.YAJIRO, false);
                            default ->
                                TrainingService.gI().callBoss(player, BossID.KARIN, false);
                        }
                    }
                } else if (player.idMark.getIndexMenu() == 2003) {
                    if (select == 0) {
                        switch (player.levelLuyenTap) {
                            case 0 ->
                                TrainingService.gI().callBoss(player, BossID.KARIN, true);
                            case 1 ->
                                TrainingService.gI().callBoss(player, BossID.YAJIRO, true);
                            default ->
                                TrainingService.gI().callBoss(player, BossID.YAJIRO, false);
                        }
                    }
                }
            }
        }
    }
}
