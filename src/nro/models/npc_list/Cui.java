package nro.models.npc_list;

import nro.models.boss.Boss;
import nro.models.boss.BossID;
import nro.models.boss.Boss_Manager.BossManager;
import nro.models.consts.ConstNpc;
import nro.models.consts.ConstTask;
import nro.models.map.Zone;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.map.service.MapService;
import nro.models.map.service.NpcService;
import nro.models.services.Service;
import nro.models.services.TaskService;
import nro.models.map.service.ChangeMapService;
import nro.models.utils.Util;

/**
 *
 * @author By Mr Blue
 * 
 */

public class Cui extends Npc {

    private final int COST_FIND_BOSS = 50000000;

    public Cui(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    @Override
    public void openBaseMenu(Player pl) {
        if (canOpenNpc(pl)) {
            if (!TaskService.gI().checkDoneTaskTalkNpc(pl, this)) {
                if (pl.playerTask.taskMain.id == 7) {
                    NpcService.gI().createTutorial(pl, this.avartar, "Hãy lên đường cứu đứa bé nhà tôi\nChắc bây giờ nó đang sợ hãi lắm rồi");
                } else {
                    switch (this.mapId) {
                        case 19 -> {
                            int taskId = TaskService.gI().getIdTask(pl);
                            switch (taskId) {
                                case ConstTask.TASK_19_0 ->
                                    this.createOtherMenu(pl, ConstNpc.MENU_FIND_KUKU,
                                            "Đội quân của Fide đang ở Thung lũng Nappa, ta sẽ đưa ngươi đến đó",
                                            "Đến chỗ\nKuku\n(" + Util.numberToMoney(COST_FIND_BOSS) + " vàng)", "Đến Cold", "Đến\nNappa", "Từ chối");
                                case ConstTask.TASK_19_1 ->
                                    this.createOtherMenu(pl, ConstNpc.MENU_FIND_MAP_DAU_DINH,
                                            "Đội quân của Fide đang ở Thung lũng Nappa, ta sẽ đưa ngươi đến đó",
                                            "Đến chỗ\nMập đầu đinh\n(" + Util.numberToMoney(COST_FIND_BOSS) + " vàng)", "Đến Cold", "Đến\nNappa", "Từ chối");
                                case ConstTask.TASK_19_2 ->
                                    this.createOtherMenu(pl, ConstNpc.MENU_FIND_RAMBO,
                                            "Đội quân của Fide đang ở Thung lũng Nappa, ta sẽ đưa ngươi đến đó",
                                            "Đến chỗ\nRambo\n(" + Util.numberToMoney(COST_FIND_BOSS) + " vàng)", "Đến Cold", "Đến\nNappa", "Từ chối");
                                default ->
                                    this.createOtherMenu(pl, ConstNpc.BASE_MENU,
                                            "Đội quân của Fide đang ở Thung lũng Nappa, ta sẽ đưa ngươi đến đó",
                                            "Đến Cold", "Đến\nNappa", "Từ chối");
                            }
                        }
                        case 68 ->
                            this.createOtherMenu(pl, ConstNpc.BASE_MENU,
                                    "Ngươi muốn về Thành Phố Vegeta", "Đồng ý", "Từ chối");
                        default ->
                            this.createOtherMenu(pl, ConstNpc.BASE_MENU,
                                    "Tàu vũ trụ Xayda sử dụng công nghệ mới nhất, "
                                    + "có thể đưa ngươi đi bất kỳ đâu, chỉ cần trả tiền là được.",
                                    "Đến\nTrái Đất", "Đến\nNamếc", "Siêu thị");
                    }
                }
            }
        }
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (canOpenNpc(player)) {
            if (this.mapId == 26) {
                if (player.idMark.isBaseMenu()) {
                    switch (select) {
                        case 0 ->
                            ChangeMapService.gI().changeMapBySpaceShip(player, 24, -1, -1);
                        case 1 ->
                            ChangeMapService.gI().changeMapBySpaceShip(player, 25, -1, -1);
                        case 2 ->
                            ChangeMapService.gI().changeMapBySpaceShip(player, 84, -1, -1);
                    }
                }
            }
            if (this.mapId == 19) {
                if (player.idMark.isBaseMenu()) {
                    switch (select) {
                        case 0 ->
                            ChangeMapService.gI().changeMapBySpaceShip(player, 109, -1, 295);
                        case 1 ->
                            ChangeMapService.gI().changeMapBySpaceShip(player, 68, -1, 90);
                    }
                } else if (player.idMark.getIndexMenu() == ConstNpc.MENU_FIND_KUKU) {
                    switch (select) {
                        case 0 -> {
                            Boss boss = BossManager.gI().getBossById(BossID.KUKU);
                            if (boss != null && !boss.isDie() && boss.zone != null && !MapService.gI().isMapPhoBan(boss.zone.map.mapId)) {
                                if (player.inventory.gold >= COST_FIND_BOSS) {
                                    Zone z = MapService.gI().getMapCanJoin(player, boss.zone.map.mapId, boss.zone.zoneId);
                                    if (z.getNumOfPlayers() < z.maxPlayer) {
                                        player.inventory.gold -= COST_FIND_BOSS;
                                        ChangeMapService.gI().changeMap(player, boss.zone, boss.location.x, boss.location.y);
                                        Service.gI().sendMoney(player);
                                    } else {
                                        Service.gI().sendThongBao(player, "Khu vực đang full.");
                                    }
                                } else {
                                    Service.gI().sendThongBao(player, "Không đủ vàng, còn thiếu "
                                            + Util.numberToMoney(COST_FIND_BOSS - player.inventory.gold) + " vàng");
                                }
                                break;
                            }
                            Service.gI().sendThongBao(player, "Chết rồi ba...");
                        }
                        case 1 ->
                            ChangeMapService.gI().changeMapBySpaceShip(player, 109, -1, 295);
                        case 2 ->
                            ChangeMapService.gI().changeMapBySpaceShip(player, 68, -1, 90);
                    }
                } else if (player.idMark.getIndexMenu() == ConstNpc.MENU_FIND_MAP_DAU_DINH) {
                    switch (select) {
                        case 0 -> {
                            Boss boss = BossManager.gI().getBossById(BossID.MAP_DAU_DINH);
                            if (boss != null && !boss.isDie() && boss.zone != null && !MapService.gI().isMapPhoBan(boss.zone.map.mapId)) {
                                if (player.inventory.gold >= COST_FIND_BOSS) {
                                    Zone z = MapService.gI().getMapCanJoin(player, boss.zone.map.mapId, boss.zone.zoneId);
                                    if (z.getNumOfPlayers() < z.maxPlayer) {
                                        player.inventory.gold -= COST_FIND_BOSS;
                                        ChangeMapService.gI().changeMap(player, boss.zone, boss.location.x, boss.location.y);
                                        Service.gI().sendMoney(player);
                                    } else {
                                        Service.gI().sendThongBao(player, "Khu vực đang full.");
                                    }
                                } else {
                                    Service.gI().sendThongBao(player, "Không đủ vàng, còn thiếu "
                                            + Util.numberToMoney(COST_FIND_BOSS - player.inventory.gold) + " vàng");
                                }
                                break;
                            }
                            Service.gI().sendThongBao(player, "Chết rồi ba...");
                        }
                        case 1 ->
                            ChangeMapService.gI().changeMapBySpaceShip(player, 109, -1, 295);
                        case 2 ->
                            ChangeMapService.gI().changeMapBySpaceShip(player, 68, -1, 90);
                    }
                } else if (player.idMark.getIndexMenu() == ConstNpc.MENU_FIND_RAMBO) {
                    switch (select) {
                        case 0 -> {
                            Boss boss = BossManager.gI().getBossById(BossID.RAMBO);
                            if (boss != null && !boss.isDie() && boss.zone != null && !MapService.gI().isMapPhoBan(boss.zone.map.mapId)) {
                                if (player.inventory.gold >= COST_FIND_BOSS) {
                                    Zone z = MapService.gI().getMapCanJoin(player, boss.zone.map.mapId, boss.zone.zoneId);
                                    if (z.getNumOfPlayers() < z.maxPlayer) {
                                        player.inventory.gold -= COST_FIND_BOSS;
                                        ChangeMapService.gI().changeMap(player, boss.zone, boss.location.x, boss.location.y);
                                        Service.gI().sendMoney(player);
                                    } else {
                                        Service.gI().sendThongBao(player, "Khu vực đang full.");
                                    }
                                } else {
                                    Service.gI().sendThongBao(player, "Không đủ vàng, còn thiếu "
                                            + Util.numberToMoney(COST_FIND_BOSS - player.inventory.gold) + " vàng");
                                }
                                break;
                            }
                            Service.gI().sendThongBao(player, "Chết rồi ba...");
                        }
                        case 1 ->
                            ChangeMapService.gI().changeMapBySpaceShip(player, 109, -1, 295);
                        case 2 ->
                            ChangeMapService.gI().changeMapBySpaceShip(player, 68, -1, 90);
                    }
                }
            }
            if (this.mapId == 68) {
                if (player.idMark.isBaseMenu()) {
                    switch (select) {
                        case 0 ->
                            ChangeMapService.gI().changeMapBySpaceShip(player, 19, -1, 1100);
                    }
                }
            }
        }
    }
}
