package nro.models.npc_list;

import nro.models.consts.ConstNpc;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.services.TaskService;

/**
 *
 * @author By Mr Blue
 */
public class DrMyuu extends Npc {

    public DrMyuu(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    @Override
    public void openBaseMenu(Player player) {
        if (canOpenNpc(player)) {
            if (!TaskService.gI().checkDoneTaskTalkNpc(player, this)) {
                this.createOtherMenu(player, ConstNpc.BASE_MENU,
                        "Năm 740, ta tìm thấy các kí sinh trùng của King Tuffle,\nsau đó ta đã nghiên cứu và chế tạo kí sinh trùng Baby.\nBaby có khả năng bám vào cơ thể người khác,\nkiểm soát sức mạnh của họ và làm việc theo ý của ta.\nTuy nhiên ta đã mất kiểm soát nó hoàn toàn...\n Người có thể giúp ta chế ngự nó không ?",
                        "Đồng ý",
                        "Từ chối");
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
