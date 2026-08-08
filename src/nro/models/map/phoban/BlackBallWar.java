package nro.models.map.phoban;



import nro.models.utils.Functions;
import nro.models.database.MrBlue;
import nro.models.database.PlayerDAO;
import java.util.List;
import java.util.concurrent.Executors;
import nro.models.player.Player;
import nro.models.map.service.MapService;
import nro.models.services.Service;
import nro.models.map.service.ChangeMapService;
import nro.models.utils.TimeUtil;
import nro.models.utils.Util;

import lombok.Data;
import nro.models.map.Zone;
import nro.models.server.Client;
import nro.models.server.Maintenance;

@Data
public class BlackBallWar implements Runnable {

    public static final int TIME_CAN_PICK_BLACK_BALL_AFTER_DROP = 5000;
    public static final byte X3 = 3;
    public static final byte X5 = 5;
    public static final byte X7 = 7;
    public static final int COST_X3 = 10000000;
    public static final int COST_X5 = 30000000;
    public static final int COST_X7 = 50000000;
    public static final byte HOUR_OPEN = 20;
    public static final byte MIN_OPEN = 0;
    public static final byte SECOND_OPEN = 0;
    public static final byte HOUR_CAN_PICK_DB = 20;
    public static final byte MIN_CAN_PICK_DB = 30;
    public static final byte SECOND_CAN_PICK_DB = 0;
    public static final byte HOUR_CLOSE = 21;
    public static final byte MIN_CLOSE = 0;
    public static final byte SECOND_CLOSE = 0;
    public static final int AVAILABLE = 5;
    private static final int TIME_WIN = 300000;

    private final Zone zone;

    public BlackBallWar(Zone zone) {
        this.zone = zone;
        start();
    }

    private void start() {
    Executors.newSingleThreadExecutor().submit(this, "Zone " + zone.zoneId);
    }

    @Override
    public void run() {
        while (!Maintenance.isRunning) {
            try {
                long startTime = System.currentTimeMillis();
                update();
                Functions.sleep(Math.max(150 - (System.currentTimeMillis() - startTime), 10));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void update() {
        if (!TimeUtil.isBlackBallWarOpen()) {
            zone.finishBlackBallWar = false;
        }

        List<Player> players = zone.getPlayers();
        for (int i = players.size() - 1; i >= 0; i--) {
            try {
                updatePlayer(players.get(i));
            } catch (Exception e) {
                System.err.println("Error updating player: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public void updatePlayer(Player player) {
        if (player.zone == null || !MapService.gI().isMapBlackBallWar(player.zone.map.mapId)) {
            return;
        }

        if (!TimeUtil.isBlackBallWarOpen()) {
            kickOutOfMap(player);
            return;
        }

        if (player.idMark.isHoldBlackBall()) {
            if (Util.canDoWithTime(player.idMark.getLastTimeHoldBlackBall(), TIME_WIN)) {
                win(player);
            } else if (Util.canDoWithTime(player.idMark.getLastTimeNotifyTimeHoldBlackBall(), 10000)) {
                Service.gI().sendThongBao(player, "Cố giữ ngọc thêm "
                        + TimeUtil.getSecondLeft(player.idMark.getLastTimeHoldBlackBall(), TIME_WIN / 1000)
                        + " giây nữa sẽ thắng");
                player.idMark.setLastTimeNotifyTimeHoldBlackBall(System.currentTimeMillis());
            }
        }
    }

    private synchronized void win(Player player) {
        player.zone.finishBlackBallWar = true;
        int star = player.idMark.getTempIdBlackBallHold() - 371;
        player.rewardBlackBall.reward((byte) star);
        Service.gI().sendThongBao(player, "Chúc mừng bạn đã "
                + "dành được Ngọc rồng " + star + " sao đen cho bang");

        if (player.clan != null) {
            player.clan.members.forEach(m -> {
                Player p = Client.gI().getPlayer(m.id);
                if (p != null) {
                    p.rewardBlackBall.reward((byte) star);
                } else {
                    Player pFromDb = MrBlue.loadById(m.id);
                    if (pFromDb != null) {
                        pFromDb.rewardBlackBall.reward((byte) star);
                        PlayerDAO.updatePlayer(pFromDb);
                    }
                }
            });
        }

        kickAllPlayersOutOfMap(player.zone);
    }

    private void kickOutOfMap(Player player) {
        if (player.cFlag == 8) {
            Service.gI().changeFlag(player, Util.nextInt(1, 7));
        }

        Service.gI().sendThongBao(player, "Trò chơi tìm ngọc hôm nay đã kết thúc, hẹn gặp lại vào 20h ngày mai");

        ChangeMapService.gI().changeMapBySpaceShip(player, player.gender + 24, -1, 250);
    }

    private void kickAllPlayersOutOfMap(Zone zone) {
        List<Player> players = zone.getPlayers();
        for (int i = players.size() - 1; i >= 0; i--) {
            Player pl = players.get(i);
            kickOutOfMap(pl);
        }
    }
}