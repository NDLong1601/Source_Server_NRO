package nro.models.npc_list;

import nro.models.consts.ConstNpc;
import java.util.ArrayList;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.services.ItemTimeService;
import nro.models.map.service.NpcService;
import nro.models.services.Service;
import nro.models.map.service.ChangeMapService;

/**
 *
 * @author By Mr Blue
 *
 */
public class Babiday extends Npc {

    public Babiday(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    @Override
    public void openBaseMenu(Player player) {
        if (canOpenNpc(player)) {
            if (player.cFlag != 10) {
                NpcService.gI().createTutorial(player, tempId, avartar, "Ngươi hãy về phe của mình mà thể hiện");
                return;
            }
            String npcSay = "Bọn Kaiô do con nhóc Ôsin cầm đầu đã có mặt tại đây...Hãy chuẩn bị 'Tiếp khách' nhé!";
            ArrayList<String> menuAL = new ArrayList<>();
            menuAL.add("Hướng\ndẫn\nthêm");
            switch (this.mapId) {
                case 114, 115, 117, 118, 119, 120 -> {
                    if (!player.itemTime.isUseGTPT) {
                        menuAL.add("Giải trừ\nphép thuật\n1 ngọc");
                    }
                    if (player.fightMabu.pointMabu >= player.fightMabu.POINT_MAX && this.mapId != 120) {
                        menuAL.add("Xuống\nTầng dưới");
                    }
                    menuAL.add("Về nhà");
                }

            }

            String[] menus = menuAL.toArray(String[]::new);

            this.createOtherMenu(player, ConstNpc.GO_UPSTAIRS_MENU, npcSay, menus);
        }
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (canOpenNpc(player)) {
            if (player.idMark.getIndexMenu() == ConstNpc.GO_UPSTAIRS_MENU) {
                if (player.cFlag != 10) {
                    return;
                }
                switch (select) {
                    case 0 ->
                        NpcService.gI().createTutorial(player, tempId, 4388, ConstNpc.HUONG_DAN_MAP_MA_BU);
                    case 1 -> {
                        if (!player.itemTime.isUseGTPT) {
                            player.itemTime.lastTimeUseGTPT = System.currentTimeMillis();
                            player.itemTime.isUseGTPT = true;
                            ItemTimeService.gI().sendAllItemTime(player);
                            Service.gI().sendThongBao(player, "Phép thuật đã được giải trừ, sức đánh của bạn đã tăng theo điểm tích lũy");
                        } else if (player.fightMabu.pointMabu >= player.fightMabu.POINT_MAX && this.mapId != 120) {
                            ChangeMapService.gI().changeMap(player, this.map.mapIdNextMabu((short) this.mapId), -1, this.cx, this.cy);
                        } else {
                            ChangeMapService.gI().changeMapBySpaceShip(player, player.gender + 21, 0, -1);
                        }
                    }
                    case 2 -> {
                        if (!player.itemTime.isUseGTPT && player.fightMabu.pointMabu >= player.fightMabu.POINT_MAX && this.mapId != 120) {
                            ChangeMapService.gI().changeMap(player, this.map.mapIdNextMabu((short) this.mapId), -1, this.cx, this.cy);
                        } else {
                            ChangeMapService.gI().changeMapBySpaceShip(player, player.gender + 21, 0, -1);
                        }
                    }
                    case 3 ->
                        ChangeMapService.gI().changeMapBySpaceShip(player, player.gender + 21, 0, -1);
                }
            }
        }
    }
}
