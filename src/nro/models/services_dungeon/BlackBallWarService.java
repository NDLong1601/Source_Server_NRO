package nro.models.services_dungeon;

import nro.models.map.phoban.BlackBallWar;
import nro.models.item.Item;
import java.util.ArrayList;
import java.util.List;
import static nro.models.map.phoban.BlackBallWar.COST_X3;
import static nro.models.map.phoban.BlackBallWar.COST_X5;
import static nro.models.map.phoban.BlackBallWar.COST_X7;
import static nro.models.map.phoban.BlackBallWar.X3;
import static nro.models.map.phoban.BlackBallWar.X5;
import static nro.models.map.phoban.BlackBallWar.X7;
import nro.models.map.ItemMap;
import nro.models.map.Zone;
import nro.models.player.Player;
import nro.models.services.PlayerService;
import nro.models.services.Service;
import nro.models.map.service.ChangeMapService;
import nro.models.utils.TimeUtil;
import nro.models.utils.Util;

/**
 *
 * @author By Mr Blue
 * 
 */

public class BlackBallWarService {

    private static BlackBallWarService instance;

    public List<BlackBallWar> blackBallWars;

    public static BlackBallWarService gI() {
        if (instance == null) {
            instance = new BlackBallWarService();
        }
        return instance;
    }

    private BlackBallWarService() {
        this.blackBallWars = new ArrayList<>();
    }

    public void addMapBlackBallWar(int id, Zone zone) {
        this.blackBallWars.add(new BlackBallWar(zone));
    }

    public void dropBlackBall(Player player) {
        if (player.idMark.isHoldBlackBall()) {
            player.idMark.setHoldBlackBall(false);
            ItemMap itemMap = new ItemMap(player.zone,
                    player.idMark.getTempIdBlackBallHold(), 1, player.location.x,
                    player.zone.map.yPhysicInTop(player.location.x, player.location.y - 24),
                    -1);
            Service.gI().dropItemMap(itemMap.zone, itemMap);
            player.idMark.setTempIdBlackBallHold(-1);
            player.zone.lastTimeDropBlackBall = System.currentTimeMillis();
            Service.gI().sendFlagBag(player);

            if (player.clan != null) {
                List<Player> players = player.zone.getPlayers();
                for (Player pl : players) {
                    if (pl.clan != null && player.clan.equals(pl.clan)) {
                        Service.gI().changeFlag(pl, Util.nextInt(1, 7));
                    }
                }
            } else {
                Service.gI().changeFlag(player, Util.nextInt(1, 7));
            }
        }
    }

    public void joinMapBlackBallWar(Player player) {
        boolean changed = false;
        if (player.clan != null) {
            List<Player> players = player.zone.getPlayers();
            for (Player pl : players) {
                if (pl.clan != null && !player.equals(pl) && player.clan.equals(pl.clan) && !player.isBoss) {
                    Service.gI().changeFlag(player, pl.cFlag);
                    changed = true;
                    break;
                }
            }
        }
        if (!changed && !player.isBoss) {
            Service.gI().changeFlag(player, Util.nextInt(1, 7));
        }
    }

    public boolean pickBlackBall(Player player, Item item) {
        try {
            if (!TimeUtil.isBlackBallWarCanPick()) {
                Service.gI().sendThongBao(player, "Chưa thể nhặt lúc này, hãy đợi "
                        + TimeUtil.getSecondsUntilCanPick() + " giây nữa");
                return false;
            } else if (player.zone.finishBlackBallWar) {
                Service.gI().sendThongBao(player, "Trò chơi tìm ngọc hôm nay đã kết thúc, hẹn gặp lại vào 20h ngày mai");
                return false;
            } else {
                if (Util.canDoWithTime(player.zone.lastTimeDropBlackBall, BlackBallWar.TIME_CAN_PICK_BLACK_BALL_AFTER_DROP)) {
                    player.idMark.setHoldBlackBall(true);
                    player.idMark.setTempIdBlackBallHold(item.template.id);
                    player.idMark.setLastTimeHoldBlackBall(System.currentTimeMillis());
                    Service.gI().sendFlagBag(player);
                    if (player.clan != null) {
                        List<Player> players = player.zone.getPlayers();
                        for (Player pl : players) {
                            if (pl.clan != null && player.clan.equals(pl.clan)) {
                                Service.gI().changeFlag(pl, 8);
                            }
                        }
                    } else {
                        Service.gI().changeFlag(player, 8);
                    }
                    return true;
                } else {
                    Service.gI().sendThongBao(player, "Chưa thể nhặt lúc này, hãy đợi "
                            + TimeUtil.getTimeLeft(player.zone.lastTimeDropBlackBall, BlackBallWar.TIME_CAN_PICK_BLACK_BALL_AFTER_DROP / 1000) + " nữa");
                    return false;
                }
            }
        } catch (Exception ex) {
            return false;
        }
    }

    public void xHPKI(Player player, byte x) {
        int cost = 0;
        switch (x) {
            case X3:
                cost = COST_X3;
                break;
            case X5:
                cost = COST_X5;
                break;
            case X7:
                cost = COST_X7;
                break;
        }
        if (player.inventory.gold >= cost) {
            player.inventory.gold -= cost;
            Service.gI().sendMoney(player);
            player.effectSkin.lastTimeXHPKI = System.currentTimeMillis();
            player.effectSkin.xHPKI = x;
            player.nPoint.calPoint();
            player.nPoint.setHp((int) player.nPoint.hp * x);
            player.nPoint.setMp((int) player.nPoint.mp * x);
            PlayerService.gI().sendInfoHpMp(player);
            Service.gI().point(player);
        } else {
            Service.gI().sendThongBao(player, "Không đủ vàng để thực hiện, còn thiếu "
                    + Util.numberToMoney(cost - player.inventory.gold) + " vàng");
        }
    }

    public void xDame(Player player, byte x) {
        int cost = 0;
        switch (x) {
            case X3:
                cost = COST_X3;
                break;
            case X5:
                cost = COST_X5;
                break;
            case X7:
                cost = COST_X7;
                break;
        }
        if (player.inventory.gold >= cost) {
            player.inventory.gold -= cost;
            Service.gI().sendMoney(player);
            player.effectSkin.lastTimeXDame = System.currentTimeMillis();
            player.effectSkin.xDame = x;
            player.nPoint.calPoint();
            player.nPoint.setHp((int) player.nPoint.hp * x);
            player.nPoint.setMp((int) player.nPoint.mp * x);
            PlayerService.gI().sendInfoHpMp(player);
            Service.gI().point(player);
        } else {
            Service.gI().sendThongBao(player, "Không đủ vàng để thực hiện, còn thiếu "
                    + Util.numberToMoney(cost - player.inventory.gold) + " vàng");
        }
    }

    public void changeMap(Player player, byte index) {
        try {
            if (TimeUtil.isBlackBallWarOpen()) {
                ChangeMapService.gI().changeMap(player,
                        player.mapBlackBall.get(index).map.mapId, -1, 50, 50);
            } else {
                Service.gI().sendThongBao(player, "Trò chơi tìm ngọc hôm nay đã kết thúc, hẹn gặp lại vào 20h ngày mai");
                Service.gI().hideWaitDialog(player);
            }
        } catch (Exception ex) {
        }
    }
}
