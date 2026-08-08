package nro.models.services;

import nro.models.consts.ConstNpc;
import nro.models.player.Player;
import nro.models.server.Client;
import nro.models.network.Message;
import java.util.ArrayList;
import nro.models.services_func.BuyBackService;
import nro.models.map.service.NpcService;
import nro.models.shop.ItemShop;
import nro.models.utils.Util;

/**
 *
 * @author By Mr Blue
 * 
 */

public class SubMenuService {

    public static final int BAN = 500;
    public static final int BUFF_PET = 501;
    public static final int OTT = 502;
    public static final int CUU_SAT = 503;
    public static final int MENU = 504;
    public static final int BUY_BACK = 505;

    private static SubMenuService i;

    private SubMenuService() {
    }

    public static SubMenuService gI() {
        if (i == null) {
            i = new SubMenuService();
        }
        return i;
    }

    public void controller(Player player, int playerTarget, int menuId) {
        Player plTarget = Client.gI().getPlayer(playerTarget);
        switch (menuId) {
//            case MENU:
//                if (plTarget != null) {
//                    String[] selects = new String[]{"Oẳn tù tì", "Cừu sát", "Bắn Bluetooth", "Hủy"};
//                    NpcService.gI().createMenuConMeo(player, ConstNpc.SUB_MENU, -1,
//                            "|0|Ngọc Rồng Lính Thủy Đánh Bạc\n" + plTarget.name + " (sức mạnh " + Util.numberToMoney(plTarget.nPoint.power) + ")", selects, plTarget);
//                }
//                break;
            case BAN:
                if (plTarget != null) {
                    String[] selects = new String[]{"Đồng ý", "Hủy"};
                    NpcService.gI().createMenuConMeo(player, ConstNpc.BAN_PLAYER, -1,
                            "Bạn có chắc chắn muốn ban " + plTarget.name, selects, plTarget);
                }
                break;
            case BUFF_PET:
                if (plTarget != null) {
                    String[] selects = new String[]{"Đồng ý", "Hủy"};
                    NpcService.gI().createMenuConMeo(player, ConstNpc.BUFF_PET, -1,
                            "Bạn có chắc chắn muốn phát đệ tử cho " + plTarget.name, selects, plTarget);
                }
                break;
            case OTT:
                if (plTarget != null) {
                    if (plTarget.isBoss) {
                        String[] selects = new String[]{"Kéo", "Búa", "Bao", "Hủy"};
                        NpcService.gI().createMenuConMeo(player, ConstNpc.IGNORE_MENU, -1,
                                "Chơi oẳn tù tì với " + plTarget.name + " mức cược 5tr.", selects);
                        return;
                    }
                    if (!plTarget.getSession().actived) {
                        Service.gI().sendThongBao(player, plTarget.name + " chưa kích hoạt tài khoản!");
                        return;
                    }
                    if (!player.getSession().actived) {
                        Service.gI().sendThongBao(player, "Bạn chưa kích hoạt tài khoản!");
                        return;
                    }
                    if (plTarget.inventory.gold < 5000000) {
                        Service.gI().sendThongBao(player, plTarget.name + " không có đủ 5tr vàng.");
                    } else if (player.inventory.gold < 5000000) {
                        Service.gI().sendThongBao(player, "Bạn không có đủ 5tr vàng.");
                    } else {
                        String[] selects = new String[]{"Kéo", "Búa", "Bao", "Hủy"};
                        NpcService.gI().createMenuConMeo(player, ConstNpc.OTT, -1,
                                "Chơi oẳn tù tì với " + plTarget.name + " mức cược 5tr.", selects, plTarget);
                    }
                }
                break;
            case CUU_SAT:
                if (Util.isAfterMidnight(player.lastTimeCuuSat)) {
                    switch (player.getSession().vip) {
                        case 1:
                            player.timesPerDayCuuSat = 15;
                            break;
                        case 2:
                            player.timesPerDayCuuSat = 30;
                            break;
                    }
                    player.lastTimeCuuSat = System.currentTimeMillis();
                }
                if (!player.isAdmin() && !(player.getSession().vip > 0 && player.timesPerDayCuuSat > 0)) {
                    Service.gI().hideWaitDialog(player);
                    Service.gI().sendThongBao(player, "Không thể thực hiện");
                    return;
                }
                if (plTarget != null) {
                    if (player.pvp != null || plTarget.pvp != null) {
                        Service.gI().hideWaitDialog(player);
                        Service.gI().sendThongBao(player, "Không thể thực hiện");
                        return;
                    }
                    player.timesPerDayCuuSat--;
//                    new CuuSat(player, plTarget);
                }
                break;
            case BUY_BACK:
//                ItemShop item;
//                if ((item = BuyBackService.gI().getItemBuyBack(plTarget)) != null) {
//                NpcService.gI().createMenuConMeo(player, ConstNpc.BUY_BACK, -1, "Bạn có muốn mua " + item.temp.name + " từ " + plTarget.name + " không?\n"
//                        + "Giá cửa hàng " + item.cost + " ĐSK\n"
//                        + "Giá tại đây " + (int) (item.cost * 0.95) + " ĐSK\n"
//                        + "Bạn cũng có thể nhận được " + (int) (item.cost * 0.2) + " ĐSK\n"
//                        + "khi người khác mua từ bạn (Cải trang của bạn sẽ không mất)", "Đồng ý", "Từ chối");
//                }
                break;
        }
        Service.gI().hideWaitDialog(player);
    }

//    public void showMenu(Player player) {
//        ArrayList<SubMenu> subMenusList = new ArrayList<>();
//        subMenusList.add(new SubMenu(MENU, "Chức năng khác", "Oẳn tù tì, cừu sát, mua skin,..."));
//
//        showSubMenu(player, subMenusList.toArray(SubMenu[]::new));
//    }

    public void showSubMenu(Player player, SubMenu... subMenus) {
        Message msg;
        try {
            msg = Service.gI().messageSubCommand((byte) 63);
            msg.writer().writeByte(subMenus.length);
            for (SubMenu subMenu : subMenus) {
                msg.writer().writeUTF(subMenu.caption1);
                msg.writer().writeUTF(subMenu.caption2);
                msg.writer().writeShort((short) subMenu.id);
            }
            player.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            nro.models.utils.Logger.logException(SubMenuService.class, e);
        }
    }

    public static class SubMenu {

        private int id;
        private String caption1;
        private String caption2;

        public SubMenu(int id, String caption1, String caption2) {
            this.id = id;
            this.caption1 = caption1;
            this.caption2 = caption2;
        }
    }
}
