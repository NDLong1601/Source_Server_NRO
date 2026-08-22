package nro.models.npc_list;

import nro.models.consts.ConstNpc;
import nro.models.map.service.ChangeMapService;
import nro.models.npc.Npc;
import nro.models.player.Player;

public class Kanao extends Npc {

    public Kanao(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    @Override
    public void openBaseMenu(Player player) {
        if (!canOpenNpc(player)) {
            return;
        }
        if (this.mapId == 0) {
            this.createOtherMenu(player, ConstNpc.BASE_MENU,
                    "Tôi có thể đưa bạn đến Vô Hạn Thành.",
                    "Vào\nVô Hạn Thành", "Đóng");
        } else if (this.mapId == 187) {
            this.createOtherMenu(player, ConstNpc.BASE_MENU,
                    "Bạn muốn rời Vô Hạn Thành và trở về Làng Aru?",
                    "Về\nLàng Aru", "Đóng");
        } else {
            this.createOtherMenu(player, ConstNpc.BASE_MENU,
                    "Xin chào chiến binh! Tôi là Kanao Tsuyuri.", "Đóng");
        }
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (!canOpenNpc(player) || !player.idMark.isBaseMenu() || select != 0) {
            return;
        }
        if (this.mapId == 0) {
            ChangeMapService.gI().changeMapNonSpaceship(player, 187, 112, 360);
        } else if (this.mapId == 187) {
            ChangeMapService.gI().changeMapNonSpaceship(player, 0, 560, 432);
        }
    }
}
