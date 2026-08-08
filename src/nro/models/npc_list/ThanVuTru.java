package nro.models.npc_list;
import nro.models.boss.BossID;
import nro.models.consts.ConstNpc;
import nro.models.services_dungeon.SnakeWayService;
import nro.models.services_dungeon.TrainingService;
import nro.models.npc.Npc;
import static nro.models.npc.NpcFactory.PLAYERID_OBJECT;
import nro.models.player.Player;
import nro.models.map.service.NpcService;
import nro.models.map.service.ChangeMapService;
import nro.models.services.Service;
import nro.models.services_func.Input;
import nro.models.utils.TimeUtil;

public class ThanVuTru extends Npc {

    public ThanVuTru(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    @Override
    public void openBaseMenu(Player player) {
        if (canOpenNpc(player)) {
            if (this.mapId == 48) {
                switch (player.levelLuyenTap) {
                    case 4 ->
                        this.createOtherMenu(player, ConstNpc.BASE_MENU,
                                "Thượng đế đưa ngươi đến đây, chắc muốn ta dạy võ chứ gì\nBắt được con khỉ Bubbles rồi hãy tính",
                                player.dangKyTapTuDong ? "Hủy đăng\nký tập\ntự động" : "Đăng ký\ntập\ntự động", "Tập luyện\nvới\nBubbles", "Thách đấu\nBubbles", "Di chuyển");
                    case 5 ->
                        this.createOtherMenu(player, ConstNpc.BASE_MENU,
                                "Ta là Thần Vũ Trụ Phương Bắc cai quản khu vực bắc vũ trụ\nnếu thắng được ta, ngươi sẽ được đến\nLãnh Địa Kaio, nơi ở của Thần Linh",
                                player.dangKyTapTuDong ? "Hủy đăng\nký tập\ntự động" : "Đăng ký\ntập\ntự động", "Tập luyện\nvới\nThần Vũ Trụ", "Thách đấu\nThần Vũ Trụ", "Di chuyển");
                    default ->
                        this.createOtherMenu(player, ConstNpc.BASE_MENU,
                                "Con mạnh nhất phía bắc vũ trụ này rồi đấy\nnhưng ngoài vũ trụ bao la kia vẫn có những kẻ mạnh hơn nhìu\ncon cần phải tập luyện để mạnh hơn nữa",
                                player.dangKyTapTuDong ? "Hủy đăng\nký tập\ntự động" : "Đăng ký\ntập\ntự động", "Tập luyện\nvới\nBubbles", "Tập luyện\nvới\nThần Vũ Trụ", "Di chuyển");
                }
            }
        }
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (canOpenNpc(player)) {
            if (this.mapId == 48) {
                if (player.idMark.isBaseMenu()) {
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
                        case 1 -> {
                            switch (player.levelLuyenTap) {
                                case 5 ->
                                    this.createOtherMenu(player, 2002, "Con có chắc muốn tập luyện ?\nTập luyện với ta sẽ tăng 640 sức mạnh mỗi phút",
                                            "Đồng ý\nluyện tập", "Không\nđồng ý");
                                default ->
                                    this.createOtherMenu(player, 2002, "Con có chắc muốn tập luyện ?\nTập luyện với Khỉ Bubbles sẽ tăng 320 sức mạnh mỗi phút",
                                            "Đồng ý\nluyện tập", "Không\nđồng ý");
                            }
                        }
                        case 2 -> {
                            switch (player.levelLuyenTap) {
                                case 4 ->
                                    this.createOtherMenu(player, 2003, "Con có chắc muốn thách đấu ?\nNếu thắng Khỉ Bubbles sẽ được tập với ta, tăng 640 sức mạnh mỗi phút",
                                            "Đồng ý\ngiao đấu", "Không\nđồng ý");
                                case 5 ->
                                    this.createOtherMenu(player, 2003, "Con có chắc muốn thách đấu ?\nNếu thắng được ta, con sẽ được học võ với người mạnh hơn ta để tăng đến 1280 sức mạnh mỗi phút",
                                            "Đồng ý\ngiao đấu", "Không\nđồng ý");
                                default ->
                                    this.createOtherMenu(player, 2003, "Con có chắc muốn tập luyện ?\nTập luyện với ta sẽ tăng 640 sức mạnh mỗi phút",
                                            "Đồng ý\nluyện tập", "Không\nđồng ý");
                            }
                        }
                        case 3 ->
                            this.createOtherMenu(player, ConstNpc.MENU_DI_CHUYEN,
                                    "Ta sẽ đưa con đi",
                                    "Về\nthần điện", "Thánh địa\nKaio", "Con\nđường\nrắn độc", "Từ chối");
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
                        case 5 ->
                            TrainingService.gI().callBoss(player, BossID.THAN_VU_TRU, false);
                        default ->
                            TrainingService.gI().callBoss(player, BossID.KHI_BUBBLES, false);
                    }
                } else if (player.idMark.getIndexMenu() == 2003) {
                    switch (player.levelLuyenTap) {
                        case 4 ->
                            TrainingService.gI().callBoss(player, BossID.KHI_BUBBLES, true);
                        case 5 ->
                            TrainingService.gI().callBoss(player, BossID.THAN_VU_TRU, true);
                        default ->
                            TrainingService.gI().callBoss(player, BossID.THAN_VU_TRU, false);
                    }
                } else if (player.idMark.getIndexMenu() == ConstNpc.MENU_DI_CHUYEN) {
                    switch (select) {
                        case 0 ->
                            ChangeMapService.gI().changeMapBySpaceShip(player, 45, -1, 354);
                        case 1 ->
                            ChangeMapService.gI().changeMap(player, 50, -1, 318, 336);
                        case 2 -> {
                            if (player.clan != null) {
                                if (!player.getSession().actived) {
                                    Service.gI().sendThongBao(player, "Vui lòng mở thành viên trước");
                                    return;
                                }
                                if (player.clan.ConDuongRanDoc != null) {
                                    this.createOtherMenu(player, 2,
                                            "Bang hội con đang ở con đường rắn độc cấp độ "
                                            + player.clan.ConDuongRanDoc.level + "\ncon có muốn đi cùng họ không? ("
                                            + TimeUtil.convertTimeNow(player.clan.ConDuongRanDoc.getLastTimeOpen())
                                            + " trước)",
                                            "Top\nBang hội", "Thành tích\nBang", "Đồng ý", "Từ chối");
                                } else {
                                    this.createOtherMenu(player, 2,
                                            "Hãy mau trở về bằng con đường rắn độc\nbọn Xayda đã đến Trái Đất",
                                            "Top\nBang hội", "Thành tích\nBang", "Chọn\ncấp độ", "Từ chối");
                                }
                            } else {
                                NpcService.gI().createTutorial(player, tempId, this.avartar,
                                        "Hãy vào bang hội trước");
                            }
                        }
                    }
                } else if (player.idMark.getIndexMenu() == 2) {
                    switch (select) {
                        case 0 -> {
                            Service.gI().showTopClanCDRD(player);
                        }
                        case 1 -> {
                            Service.gI().showTopClanCDRD(player);
                        }
                        case 2 -> {
                            if (player.clan == null) {
                                return;
                            }
                            if (player.clanMember.getNumDateFromJoinTimeToToday() < 1) {
                                NpcService.gI().createTutorial(player, tempId, this.avartar,
                                        "Gia nhập bang hội trên 1 ngày mới được tham gia");
                                return;
                            }
                            if (player.clan.ConDuongRanDoc == null) {
                                Input.gI().createFormChooseLevelCDRD(player);
                            } else {
                                SnakeWayService.gI().openConDuongRanDoc(player, (byte) 0);
                            }
                        }
                    }
                } else if (player.idMark.getIndexMenu() == 3) {
                    if (select == 0) {
                        if (player.clan.ConDuongRanDoc != null) {
                            SnakeWayService.gI().openConDuongRanDoc(player, (byte) 0);
                        } else {
                            SnakeWayService.gI().openConDuongRanDoc(player, Byte.parseByte(String.valueOf(PLAYERID_OBJECT.get(player.id))));
                        }
                    }
                }
            }
        }
    }

}
