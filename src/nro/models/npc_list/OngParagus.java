package nro.models.npc_list;

import nro.models.map.service.NpcService;
import nro.models.player.Player;
import nro.models.services.Service;
import nro.models.services.TaskService;

public class OngParagus extends OngGohan {

    public OngParagus(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    @Override
    public void openBaseMenu(Player player) {
        if (canOpenNpc(player)) {
            if (!TaskService.gI().checkDoneTaskTalkNpc(player, this)) {
                NpcService.gI().createTutorial(player, tempId, this.avartar,
                        "Con cố gắng theo Vua Vegeta học thành tài, đừng lo lắng cho ta.");
            }
        }
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (canOpenNpc(player)) {
            if (player.idMark.isBaseMenu()) {
                switch (select) {
                    case 0 -> {

                    }
                }
            }
        }
    }
}
