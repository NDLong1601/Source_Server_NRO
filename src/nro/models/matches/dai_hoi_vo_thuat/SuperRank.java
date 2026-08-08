package nro.models.matches.dai_hoi_vo_thuat;

import nro.models.database.SuperRankDAO;
import nro.models.managers.SuperRankManager;
import nro.models.utils.Functions;
import nro.models.boss.Boss;
import nro.models.consts.BossStatus;
import nro.models.boss.sieu_hang.Rival;
import nro.models.consts.ConstPlayer;
import nro.models.consts.ConstSuperRank;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.Data;
import nro.models.map.Zone;
import nro.models.matches.DHVT;
import nro.models.player.Player;
import nro.models.server.Maintenance;
import nro.models.server.ServerNotify;
import nro.models.services.PlayerService;
import nro.models.services.Service;
import nro.models.map.service.ChangeMapService;
import nro.models.utils.Util;

@Data
public final class SuperRank implements Runnable {

    private Zone zone;
    private Player player;
    private Boss rival;
    private long playerId, rivalId;
    private boolean isCompeting, win;
    private int timeUp = 0, timeDown = 180, error = 0;
    private int rankWin, rankLose;
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(1);

    public SuperRank(Player player, long rivalId, Zone zone) {
        try {
            this.player = player;
            this.zone = zone;
            this.playerId = player.id;
            this.rivalId = rivalId;
            this.player.isPKDHVT = true;

            Player rivalPlayer = SuperRankService.gI().loadPlayer(rivalId);
            rivalPlayer.nPoint.calPoint();
            this.rival = new Rival(player, rivalPlayer);

            this.rankLose = player.superRank.rank;
            this.rankWin = rivalPlayer.superRank.rank;

            updateZoneInfo(player, rivalPlayer);
            start();
        } catch (Exception e) {
            dispose();
        }
    }

    private void updateZoneInfo(Player p1, Player p2) {
        this.zone.isCompeting = true;
        this.zone.rank1 = p1.superRank.rank;
        this.zone.rank2 = p2.superRank.rank;
        this.zone.rankName1 = p1.name;
        this.zone.rankName2 = p2.name;
    }

    private void start() {
        isCompeting = true;
        if (player.zone.zoneId != zone.zoneId) {
            ChangeMapService.gI().changeZone(player, zone.zoneId);
        }
        threadPool.submit(this);
    }

    @Override
    public void run() {
        while (!Maintenance.isRunning && isCompeting) {
            long start = System.currentTimeMillis();
            try {
                update();
            } catch (Exception e) {
                if (error++ < 5) {
                    e.printStackTrace();
                }
            }
            Functions.sleep(Math.max(1000 - (System.currentTimeMillis() - start), 10));
        }
    }

    private void update() {
        if (win) {
            return;
        }

        if (timeUp < 5) {
            handleMatchStart();
            return;
        }

        if (timeDown-- > 0) {
            if (isPlayerAliveInZone()) {
                if (rival == null || rival.zone == null || rival.isDie()) {
                    handleWin();
                }
            } else {
                handleLose();
            }
        } else {
            handleLose();
        }
    }

    private void handleMatchStart() {
        switch (timeUp++) {
            case 0 -> {
                Service.gI().sendThongBao(player, "Trận đấu bắt đầu");
                Service.gI().setPos0(player, 334, 264);
                Service.gI().setPos0(rival, 434, 264);
            }
            case 2 ->
                Service.gI().chat(rival, ConstSuperRank.TEXT_SAN_SANG_CHUA);
            case 3 -> {
                Service.gI().chat(player, ConstSuperRank.TEXT_SAN_SANG);
                PlayerService.gI().changeAndSendTypePK(player, ConstPlayer.PK_PVP);
                PlayerService.gI().changeAndSendTypePK(rival, ConstPlayer.PK_PVP);
                Service.gI().sendTypePK(player, rival);
                new DHVT(player, rival);
            }
            case 4 ->
                rival.changeStatus(BossStatus.ACTIVE);
        }
    }

    private boolean isPlayerAliveInZone() {
        return player != null && player.isPKDHVT && !player.lostByDeath && player.location != null
                && !player.isDie() && player.zone != null && player.zone.equals(zone);
    }

    private void handleWin() {
        win = true;
        try {
            finishCombat();
            updateResults(true);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            dispose();
        }
    }

    private void handleLose() {
        try {
            finishCombat();
            updateResults(false);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            dispose();
        }
    }

    private void finishCombat() {
        if (rival != null) {
            rival.leaveMap();
        }
        if (player != null && player.zone != null && player.zone.equals(zone)) {
            if (player.isDie()) {
                Service.gI().hsChar(player, player.nPoint.hpMax, player.nPoint.mpMax);
            }
            PlayerService.gI().changeAndSendTypePK(player, ConstPlayer.NON_PK);
            Service.gI().sendPlayerVS(player, null, (byte) 0);
        }
    }

    private void updateResults(boolean playerWon) {
        Player winner = SuperRankService.gI().loadPlayer(playerWon ? playerId : rivalId);
        Player loser = SuperRankService.gI().loadPlayer(playerWon ? rivalId : playerId);

        winner.superRank.win++;
        loser.superRank.lose++;

        if (!playerWon && loser.superRank.ticket > 0) {
            loser.superRank.ticket--;
        } else if (!playerWon && loser.inventory.getGem() > 0) {
            loser.inventory.subGem(3);
            Service.gI().sendMoney(loser);
        } else if (playerWon && winner.superRank.ticket == 0 && winner.inventory.getGem() > 0) {
            winner.inventory.subGem(2);
        }

        winner.superRank.rank = rankWin;
        loser.superRank.rank = rankLose;

        winner.superRank.history("Hạ " + loser.name + "[" + rankLose + "]", System.currentTimeMillis());
        loser.superRank.history("Thua " + winner.name + "[" + rankWin + "]", System.currentTimeMillis());

        SuperRankDAO.updatePlayer(winner);
        SuperRankDAO.updatePlayer(loser);

        if (playerWon) {
            if (rankWin <= 10) {
                ServerNotify.gI().notify(ConstSuperRank.TEXT_TOP_10.replaceAll("%1", winner.name).replaceAll("%2", rankWin + ""));
            }
            Service.gI().chat(player, ConstSuperRank.TEXT_THANG.replaceAll("%1", rankWin + ""));
        } else {
            Service.gI().chat(player, ConstSuperRank.TEXT_THUA);
        }
    }

    private void resetZoneInfo() {
        if (zone != null) {
            zone.isCompeting = false;
            zone.rank1 = -1;
            zone.rank2 = -1;
            zone.rankName1 = null;
            zone.rankName2 = null;
        }
    }

    public void dispose() {
        if (player != null && player.location != null) {
            Service.gI().setPos(player, Util.nextInt(250, 450), 360);
        }
        resetZoneInfo();
        isCompeting = false;

        if (player != null) {
            player.isPKDHVT = false;
        }

        if (rival != null) {
            rival.dispose();
        }

        player = null;
        rival = null;
        zone = null;
        playerId = rivalId = -1;
        rankWin = rankLose = -1;
        SuperRankManager.gI().removeSPR(this);
    }

    public void shutdown() {
        threadPool.shutdown();
    }
}
