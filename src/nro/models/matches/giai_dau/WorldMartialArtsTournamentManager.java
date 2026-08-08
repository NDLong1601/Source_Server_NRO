package nro.models.matches.giai_dau;

import nro.models.matches.dai_hoi_vo_thuat.WorldMartialArtsTournamentService;
import nro.models.matches.dai_hoi_vo_thuat.WorldMartialArtsTournament;
import nro.models.utils.Functions;
import nro.models.consts.ConstTournament;
import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;
import nro.models.map.Map;
import nro.models.map.Zone;
import nro.models.player.Player;
import nro.models.server.Client;
import nro.models.server.Maintenance;
import nro.models.map.service.MapService;
import nro.models.services.Service;
import nro.models.services.TaskService;
import nro.models.utils.TimeUtil;
import nro.models.utils.Util;

public class WorldMartialArtsTournamentManager implements Runnable {

    public ArrayList<Long> listReg = new ArrayList<>();
    public ArrayList<Long> listWait = new ArrayList<>();
    public ArrayList<String> listChamp = new ArrayList<>();

    public String cupName;
    public String[] time;
    public int gem;
    public int gold;
    public int round;

    public long lastUpdateTime;
    public boolean canReg;
    public int nextTime;

    public int lastMins;

    public long lastWaitTime;
    public int waitTime;
    public long lastTime;

    public List<String> chatText;

    public List<WorldMartialArtsTournament> listTournaments;

    private static WorldMartialArtsTournamentManager instance;

    public static WorldMartialArtsTournamentManager gI() {
        if (instance == null) {
            instance = new WorldMartialArtsTournamentManager();
        }
        return instance;
    }

    public WorldMartialArtsTournamentManager() {
        listTournaments = new ArrayList<>();
        chatText = new ArrayList<>();
        lastTime = System.currentTimeMillis();
    }

    @Override
    public void run() {
        while (!Maintenance.isRunning) {
            try {
                long start = System.currentTimeMillis();
                if (Util.isAfterMidnight(lastTime)) {
                    listChamp.clear();
                    lastTime = System.currentTimeMillis();
                }
                if (Util.canDoWithTime(lastUpdateTime, 1000)) {
                    lastUpdateTime = System.currentTimeMillis();
                    int tour = WorldMartialArtsTournamentService.getTournament();
                    canReg = TimeUtil.getCurrMin() < ConstTournament.MINS_MAX_CAN_REG && tour != -1;
                    if (tour != -1) {
                        gem = ConstTournament.tournamentGems[tour];
                        gold = ConstTournament.tournamentGolds[tour];
                        cupName = ConstTournament.tournamentNames[tour];
                        update();
                    }
                    nextTime = WorldMartialArtsTournamentService.getNextTournamentTime();
                }
                updateChatText();
                Functions.sleep(Math.max(1000 - (System.currentTimeMillis() - start), 10));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void updateChatText() {
        chatText.clear();
        chatText.add("Đại hội võ thuật thế giới là một giải đấu uy tín bậc nhất");

        if (listChamp.isEmpty()) {
            chatText.add("Với những đấu thủ huyền thoại như Sôn Gô Ku, Thiên Xin Hăng ... đã từng đạt chức vô địch");
        } else {
            chatText.add("Với những nhà vô địch giải trước là " + String.join(",", listChamp) + " ...");
        }

        if (canReg) {
            chatText.add("Bạn hãy nhanh chân đăng ký ngay bây giờ, giải đấu sẽ bắt đầu vào lúc " + TimeUtil.getCurrHour() + "h30");
        } else if (TimeUtil.getCurrMin() < ConstTournament.MINS_START) {
            chatText.add("Giải đấu tiếp theo sẽ diễn ra vào lúc " + WorldMartialArtsTournamentService.getNextTournamentTime() + "h");
        } else {
            chatText.add("Đang trong thời gian thi đấu, xin chờ đến " + WorldMartialArtsTournamentService.getNextTournamentTime() + "h để đăng ký");
        }
    }

    public void update() {
        if (TimeUtil.getCurrMin() > ConstTournament.MINS_END) {
            round = 0;
            for (int i = listTournaments.size() - 1; i >= 0; i--) {
                listTournaments.get(i).finish();
            }
        } else if (TimeUtil.getCurrMin() >= ConstTournament.MINS_START) {
            if (listWait.size() == 1 && listReg.isEmpty() && listTournaments.isEmpty()) {
                Player plRw = getPlayerById(listWait.get(0));
                if (plRw != null) {
                    reward(plRw);
                }
                return;
            }
            if (round > 0) {
                if (listWait.size() > 1 && listTournaments.isEmpty() && waitTime - (System.currentTimeMillis() - lastWaitTime) > 30000) {
                    lastWaitTime = System.currentTimeMillis();
                    waitTime = 30000;
                    sendWaitNotify();
                }
                if (listWait.size() > 1 && listReg.isEmpty() && listTournaments.isEmpty() && Util.canDoWithTime(lastWaitTime, waitTime)) {
                    listReg.addAll(listWait);
                    listWait.clear();
                }
                if (lastMins != TimeUtil.getCurrMin()) {
                    lastMins = TimeUtil.getCurrMin();
                    sendWaitNotify();
                }
            }
            if (!listReg.isEmpty() && listTournaments.isEmpty()) {
                round++;
                for (int i = listReg.size() - 1; i >= 0; i--) {
                    Player pl = getPlayerById(listReg.get(i));
                    if (pl != null && pl.zone != null) {
                        if (pl.zone.map.mapId != 52) {
                            Service.gI().sendThongBao(pl, ConstTournament.TEXT_TRUAT_QUYEN);
                            listReg.remove(i);
                        }
                    } else {
                        listReg.remove(i);
                    }
                }

                if (listReg.size() % 2 != 0) {
                    Player plHup = getPlayerById(listReg.remove(listReg.size() - 1));
                    if (plHup != null) {
                        listWait.add(plHup.id);
                        Service.gI().sendThongBao(plHup, ConstTournament.TEXT_DOI_THU_BO_CUOC);                        
                    
                    }
                }

                for (int i = 0; i < listReg.size() - 1; i += 2) {
                    Player p1 = getPlayerById(listReg.get(i));
                    Player p2 = getPlayerById(listReg.get(i + 1));
                    Zone z = getZoneTournament();
                    WorldMartialArtsTournament wmat = new WorldMartialArtsTournament(p1, p2, z);
                    addWMAT(wmat);
                }
                lastWaitTime = System.currentTimeMillis();
                waitTime = 240000;
                listReg.clear();
            }
        } else {
            round = 0;
            if (lastMins != TimeUtil.getCurrMin()) {
                lastMins = TimeUtil.getCurrMin();
                for (int i = listReg.size() - 1; i >= 0; i--) {
                    try {
                        Player pl = getPlayerById(listReg.get(i));
                        if (pl != null && pl.zone != null) {
                            Service.gI().sendThongBao(pl, "Trận đấu của bạn sẽ diễn ra trong vòng " + (ConstTournament.MINS_START - TimeUtil.getCurrMin()) + " phút nữa");
                        }
                    } catch (Exception e) {
                    }
                }
            }
        }
    }

    public void sendWaitNotify() {
        if (!listWait.isEmpty()) {
            for (int i = listWait.size() - 1; i >= 0; i--) {
                try {
                    Player pl = getPlayerById(listWait.get(i));
                    if (pl != null && pl.zone != null) {
                        Service.gI().sendThongBao(pl, "Trận đấu của bạn sẽ diễn ra trong vòng " + TimeUtil.getTimeLeft(lastWaitTime, waitTime / 1000) + " nữa");
                    }
                } catch (Exception e) {
                }
            }
        }
    }

    public void reward(Player pl) {
        listChamp.add(pl.name);
        listReg.clear();
        listWait.clear();
        Service.gI().dropAndPickItem(pl, 77, 50);
        Service.gI().sendThongBao(pl, ConstTournament.TEXT_VO_DICH);
        for (int i = 220; i < 225; i++) {
            Service.gI().dropAndPickItemDNC(pl, i);
        }
        String msg = ConstTournament.TEXT_KHOE_VO_DICH.replaceAll("%1", pl.name).replaceAll("%2", ConstTournament.tournamentNames[WorldMartialArtsTournamentService.getTournament()]);
        Service.gI().sendThongBaoToAnotherNotMe(pl, msg);
    }

    public void addWMAT(WorldMartialArtsTournament wmat) {
        listTournaments.add(wmat);
    }

    public void removeWMAT(WorldMartialArtsTournament wmat) {
        listTournaments.remove(wmat);
    }

    public boolean checkPlayer(long id) {
        return listWait.contains(id);
    }

    public Zone getZoneTournament() {
        Map map = MapService.gI().getMapById(51);
        Zone zone = null;
        try {
            if (map != null) {
                int zoneId = 0;
                while (zoneId < map.zones.size()) {
                    Zone zonez = map.zones.get(zoneId);
                    if (getWMAT(zonez) == null) {
                        zone = zonez;
                        break;
                    }
                    zoneId++;
                }
            }
        } catch (Exception e) {
        }
        return zone;
    }

    public WorldMartialArtsTournament getWMAT(@NonNull Zone zone) {
        for (WorldMartialArtsTournament wmat : listTournaments) {
            if (wmat.zone.equals(zone)) {
                return wmat;
            }
        }
        return null;
    }

    public Player getPlayerById(long id) {
        try {
            return Client.gI().getPlayer(id);
        } catch (Exception e) {
        }
        return null;
    }
}
