package nro.models.services_dungeon;

import nro.models.consts.ConstNpc;
import nro.models.player.Player;
import nro.models.map.service.MapService;
import nro.models.services.Service;
import nro.models.map.service.ChangeMapService;
import nro.models.utils.Util;
import java.util.List;
import nro.models.map.Zone;
import nro.models.npc.Npc;
import nro.models.map.service.NpcService;
import nro.models.utils.TimeUtil;

/**
 *
 * @author By Mr Blue
 * 
 */

public class MajinBuuService {

    public static byte HOUR_OPEN_MAP_MABU = 12;
    public static final int AVAILABLE = 13;

    private static MajinBuuService instance;

    public static MajinBuuService gI() {
        if (instance == null) {
            instance = new MajinBuuService();
        }
        return instance;
    }

    public Npc getNpcOsin(Player player) {
        for (Npc npc : player.zone.map.npcs) {
            if (npc.tempId == 44) {
                return npc;
            }
        }
        return null;
    }

    public Npc getNpcBabiday(Player player) {
        for (Npc npc : player.zone.map.npcs) {
            if (npc.tempId == 46) {
                return npc;
            }
        }
        return null;
    }

    public Npc getNpcBabiday(Zone zone) {
        for (Npc npc : zone.map.npcs) {
            if (npc.tempId == 46) {
                return npc;
            }
        }
        return null;
    }

    public void joinMapMabu(Player player) {
        boolean changed = false;
        if (player.clan != null) {
            List<Player> players = player.zone.getPlayers();
            for (Player pl : players) {
                if (pl.clan != null && !player.equals(pl) && player.clan.equals(pl.clan) && !player.isBoss) {
                    Service.gI().changeFlag(player, Util.nextInt(9, 10));
                    changed = true;
                    break;
                }
            }
        }
        if (!changed && !player.isBoss) {
            Service.gI().changeFlag(player, Util.nextInt(9, 10));
        }
    }

    public void xuongTangDuoi(Player player) {
        NpcService.gI().createMenuConMeo(player, ConstNpc.MENU_XUONG_TANG_DUOI, player.cFlag == 9 ? 4390 : 4388, "Mau đi với ta xuống tầng tiếp theo", "OK");
    }

    public void goHome(Player player) {
        if (player.goHome && Util.canDoWithTime(player.lastUpdateGohomeTime, 3000)) {
            if (player.timeGohome == 30) {
                NpcService.gI().createMenuConMeo(player, -1, 4390, "Trận chiến đã kết thúc, chúng ta phải rời khỏi đây ngay", "OK");
            }
            if (player.timeGohome > 0) {
                Service.gI().sendThongBao(player, "Về nhà sau " + player.timeGohome + " giây nữa");
            }
            player.timeGohome -= 3;
            if (player.timeGohome <= 0) {
                ChangeMapService.gI().changeMapBySpaceShip(player, player.gender + 21, -1, 250);
                player.goHome = false;
            }
            player.lastUpdateGohomeTime = System.currentTimeMillis();
        }
    }

    public void update(Player player) {
        if (player.zone != null && player.isPl() && MapService.gI().isMapMaBu(player.zone.map.mapId)) {
            try {
                goHome(player);
                if (!TimeUtil.isMabuOpen()) {
                    if (!player.goHome && !player.isAdmin()) {
                        player.goHome = true;
                        player.timeGohome = 30;
                    }
                    return;
                }
                if (Util.isTrue(1, 100) && player.cFlag == 9) {
                    getNpcBabiday(player).npcChat(player.zone, "Úm bala xì bùa " + player.name);
                    Service.gI().sendThongBao(player, "Bạn đã bị Babiđây thôi miên");
                    Service.gI().changeFlag(player, 10);
                }
                if (Util.isTrue(1, 50) && player.cFlag == 10) {
                    getNpcOsin(player).npcChat(player.zone, "Úm bala xì bùa hóa giải cho " + player.name);
                    Service.gI().sendThongBao(player, "Bạn đã được Ôsin giải bùa mê");
                    Service.gI().changeFlag(player, 9);
                }
            } catch (Exception ignored) {
            }
        }

    }
}
