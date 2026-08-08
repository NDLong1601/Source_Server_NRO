package nro.models.npc_list;
import nro.models.consts.ConstNpc;
import nro.models.map.phoban.BlackBallWar;
import nro.models.services_dungeon.BlackBallWarService;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.services.Service;
import nro.models.map.service.ChangeMapService;
import nro.models.utils.Util;

public class Rong1Sao extends Npc {

    public Rong1Sao(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    @Override
    public void openBaseMenu(Player player) {
        if (canOpenNpc(player)) {
            if (player.idMark.isHoldBlackBall()) {
                this.createOtherMenu(player, ConstNpc.MENU_PHU_HP, "Ta có thể giúp gì cho ngươi?", "Phù hộ", "Từ chối");
            } else {
                this.createOtherMenu(player, ConstNpc.MENU_OPTION_GO_HOME, "Ta có thể giúp gì cho ngươi?", "Về nhà", "Từ chối");
            }
        }
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (canOpenNpc(player)) {
            switch (player.idMark.getIndexMenu()) {
                case ConstNpc.MENU_PHU_HP -> {
                    if (select == 0) {
                        this.createOtherMenu(player, ConstNpc.MENU_OPTION_PHU_HP,
                                "Ta sẽ giúp ngươi tăng HP lên mức kinh hoàng, ngươi chọn đi",
                                "x3 HP\n" + Util.numberToMoney(BlackBallWar.COST_X3) + " vàng",
                                "x5 HP\n" + Util.numberToMoney(BlackBallWar.COST_X5) + " vàng",
                                "x7 HP\n" + Util.numberToMoney(BlackBallWar.COST_X7) + " vàng",
                                "x3 SD\n" + Util.numberToMoney(BlackBallWar.COST_X3) + " vàng",
                                "x5 SD\n" + Util.numberToMoney(BlackBallWar.COST_X5) + " vàng",
                                "Từ chối"
                        );
                    }
                }
                case ConstNpc.MENU_OPTION_GO_HOME -> {
                    switch (select) {
                        case 0 ->
                            ChangeMapService.gI().changeMapBySpaceShip(player, player.gender + 21, -1, 250);
                        case 1 ->
                            this.npcChat(player, "Để ta xem ngươi trụ được bao lâu");
                        default -> {
                        }
                    }
                }

                case ConstNpc.MENU_OPTION_PHU_HP -> {
                    if (player.effectSkin.xHPKI > 1 || player.effectSkin.xDame > 1) {
                        Service.gI().sendThongBao(player, "Bạn đã được phù hộ rồi!");
                        return;
                    }
                    switch (select) {
                        case 0 ->
                            BlackBallWarService.gI().xHPKI(player, BlackBallWar.X3);
                        case 1 ->
                            BlackBallWarService.gI().xHPKI(player, BlackBallWar.X5);
                        case 2 ->
                            BlackBallWarService.gI().xHPKI(player, BlackBallWar.X7);
                        case 3 ->
                            BlackBallWarService.gI().xDame(player, BlackBallWar.X3);
                        case 4 ->
                            BlackBallWarService.gI().xDame(player, BlackBallWar.X5);
                        case 5 ->
                            this.npcChat(player, "Để ta xem ngươi trụ được bao lâu");
                    }
                }
                default -> {
                }
            }
        }
    }
}
