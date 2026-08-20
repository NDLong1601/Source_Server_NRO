package nro.models.npc_list;

import nro.models.consts.ConstNpc;
import nro.models.npc.Npc;
import nro.models.player.Player;

public class Kanao extends Npc {

    public Kanao(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    @Override
    public void openBaseMenu(Player player) {
        if (canOpenNpc(player)) {
            this.createOtherMenu(player, ConstNpc.BASE_MENU,
                    "Xin chào chiến binh! Tôi là Kanao Tsuyuri.",
                    "Đóng");
        }
    }

    @Override
    public void confirmMenu(Player player, int select) {
    }
}
