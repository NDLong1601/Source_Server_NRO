package nro.models.npc_list;

import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import nro.models.map.service.NpcService;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.Service;
import nro.models.services_func.Input;
import nro.models.utils.Util;
import nro.models.map.*;

/**
 *
 * @author By Mr Blue
 */
public class DuaHau extends Npc {

    public DuaHau(int mapId, int status, int cx, int cy, int tempId, int avatar) {
        super(mapId, status, cx, cy, tempId, avatar);
    }

    @Override
    public void openBaseMenu(Player player) {
        if (player.DuaHauEgg.getSecondDone() <= 0) {
            this.createOtherMenu(player, ConstNpc.CO_THE_THU_HOACH, "Dưa hấu đã chín, bạn có thể thu hoạch!", "Thu hoạch", "Từ chối");
        } else {
            int timeLeft = player.DuaHauEgg.getSecondDone();
            int hours = timeLeft / 3600;
            int minutes = (timeLeft % 3600) / 60;
            int seconds = timeLeft % 60;
            String timeFormatted = String.format("%02d:%02d:%02d", hours, minutes, seconds);
            this.createOtherMenu(player, ConstNpc.CHUA_THE_THU_HOACH,
                    "Dưa hấu đang lớn.\nCòn khoảng " + timeFormatted + " nữa sẽ chín.", "Đóng");
        }
    }

    @Override
    public void confirmMenu(Player player, int select) {
        switch (player.idMark.getIndexMenu()) {
            case ConstNpc.CO_THE_THU:
                if (select == 0) {
                    player.DuaHauEgg.openEgg();
                }
                break;
        }
    }
}
