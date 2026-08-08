package nro.models.services_dungeon;

import nro.models.consts.ConstNpc;
import nro.models.consts.ConstPlayer;
import nro.models.item.Item;
import nro.models.map.ItemMap;
import nro.models.map.Zone;
import nro.models.player.Player;
import nro.models.server.Client;
import nro.models.server.Manager;
import nro.models.server.ServerManager;
import nro.models.map.service.ItemMapService;
import nro.models.map.service.NpcService;
import nro.models.services.PlayerService;
import nro.models.services.Service;
import nro.models.map.service.ChangeMapService;
import nro.models.utils.Util;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author By Mr Blue
 * 
 */

public class NgocRongNamecService implements Runnable {

    private static NgocRongNamecService instance;

    public static NgocRongNamecService gI() {
        if (instance == null) {
            instance = new NgocRongNamecService();
            instance.initNgocRongNamec((byte) 0);
        }
        return instance;
    }

    public int mapNrNamec[] = {-1, -1, -1, -1, -1, -1, -1};
    public String nameNrNamec[] = {"", "", "", "", "", "", ""};
    public byte zoneNrNamec[] = {-1, -1, -1, -1, -1, -1, -1};
    public String pNrNamec[] = {"", "", "", "", "", "", ""};
    public int idpNrNamec[] = {-1, -1, -1, -1, -1, -1, -1};
    public long timeNrNamec = 0;
    public boolean firstNrNamec = true;
    public long tOpenNrNamec = 0;
    public long lastTimeReinit;
    public boolean isReinit;
    private long lastTimeReInitNRNM = System.currentTimeMillis();

    // 7 8 9 10 11 12 13 25 31 32 33 34 43
    public boolean isMapNRNM(int mapId) {
        return (mapId >= 7 && mapId <= 13) || mapId == 25
                || (mapId >= 31 && mapId <= 34) || mapId == 43;
    }

    public void initNgocRongNamec(byte type) { //type 0: INIT NGOC RONG, type 1: INIT HOA THACH NGOC RONG
        ArrayList<Integer> listMap = new ArrayList<>();
        listMap.add(7);
        listMap.add(8);
        listMap.add(9);
        listMap.add(10);
        listMap.add(11);
        listMap.add(12);
        listMap.add(13);
        listMap.add(25);
        listMap.add(31);
        listMap.add(32);
        listMap.add(33);
        listMap.add(34);
        listMap.add(43);
        for (byte i = 0; i < (byte) 7; i++) {
            int index = Util.nextInt(0, listMap.size() - 1);
            int idZone = Util.nextInt(0, Manager.MAPS.get(listMap.get(index)).zones.size() - 1);
            mapNrNamec[i] = listMap.get(index);
            nameNrNamec[i] = Manager.MAPS.get(listMap.get(index)).mapName;
            zoneNrNamec[i] = (byte) idZone;
            Zone zone = Manager.MAPS.get(listMap.get(index)).zones.get(idZone);
            int x = Util.nextInt(100, zone.map.mapWidth - 100);
            int y = zone.map.yPhysicInTop(x, 100);
            if (type == (byte) 0) {
                ItemMap itemMap = new ItemMap(zone, i + 353, 1, x, y, -1);
                Service.gI().dropItemMap(zone, itemMap);
            } else {
                ItemMap itemMap = new ItemMap(zone, 362, 1, x, y, -1);
                Service.gI().dropItemMap(zone, itemMap);
            }
            listMap.remove(index);
        }
    }

    public void reInitNgocRongNamec() {
        ArrayList<Integer> listMap = new ArrayList<>();
        listMap.add(7);
        listMap.add(8);
        listMap.add(9);
        listMap.add(10);
        listMap.add(11);
        listMap.add(12);
        listMap.add(13);
        listMap.add(25);
        listMap.add(31);
        listMap.add(32);
        listMap.add(33);
        listMap.add(34);
        listMap.add(43);
        for (byte i = 0; i < (byte) 7; i++) {
            if (NgocRongNamecService.gI().pNrNamec[i].equals("")) {
                int index = Util.nextInt(0, listMap.size() - 1);
                int idZone = Util.nextInt(0, Manager.MAPS.get(listMap.get(index)).zones.size() - 1);
                mapNrNamec[i] = listMap.get(index);
                nameNrNamec[i] = Manager.MAPS.get(listMap.get(index)).mapName;
                zoneNrNamec[i] = (byte) idZone;
                Zone zone = Manager.MAPS.get(listMap.get(index)).zones.get(idZone);
                int x = Util.nextInt(100, zone.map.mapWidth - 100);
                int y = zone.map.yPhysicInTop(x, 100);
                if (this.isReinit && this.lastTimeReinit - System.currentTimeMillis() > 0) {
                    ItemMap itemMap = new ItemMap(zone, 362, 1, x, y, -1);
                    Service.gI().dropItemMap(zone, itemMap);
                } else {
                    ItemMap itemMap = new ItemMap(zone, i + 353, 1, x, y, -1);
                    Service.gI().dropItemMap(zone, itemMap);
                }
                listMap.remove(index);
            }
        }
    }

    public void removeStoneNrNamec() {
        for (byte i = 0; i < (byte) 7; i++) {
            Zone zone = Manager.MAPS.get(mapNrNamec[i]).zones.get(zoneNrNamec[i]);
            int idItem = (int) (i + 353);
            int idItem2 = 362;
            for (byte j = 0; j < zone.items.size(); j++) {
                if (zone.items.get(j).itemTemplate.id == idItem
                        || zone.items.get(j).itemTemplate.id == idItem2) {
                    ItemMapService.gI().removeItemMapAndSendClient(zone.items.remove(j));
                }
            }
        }
    }

    public void doneDragonNamec() {
        for (int i = 0; i < 7; i++) {
            Player p = Client.gI().getPlayer(idpNrNamec[i]);
            if (p != null) {
                p.idNRNM = -1;
                pNrNamec[i] = "";
                idpNrNamec[i] = -1;
                Service.gI().sendFlagBag(p);
                PlayerService.gI().changeAndSendTypePK(p, ConstPlayer.NON_PK);
            }
        }
    }

    public void reInitNrNamec(long time) {
        lastTimeReinit = System.currentTimeMillis() + time;
        isReinit = true;
    }

    public boolean isSameMapNrNamec() {
        return (mapNrNamec[0] == 7) && (mapNrNamec[1] == 7) && (mapNrNamec[2] == 7) && (mapNrNamec[3] == 7) && (mapNrNamec[4] == 7) && (mapNrNamec[5] == 7) && (mapNrNamec[6] == 7);
    }

    public boolean isSameZoneNrNamec() {
        return (zoneNrNamec[0] == zoneNrNamec[1]) && (zoneNrNamec[2] == zoneNrNamec[0]) && (zoneNrNamec[3] == zoneNrNamec[0]) && (zoneNrNamec[4] == zoneNrNamec[0]) && (zoneNrNamec[5] == zoneNrNamec[0]) && (zoneNrNamec[6] == zoneNrNamec[0]);
    }

    public boolean canCallDragonNamec(Player p) {
        byte count = (byte) 0;
        if (isSameMapNrNamec() && isSameZoneNrNamec()) {
            if (p.clan != null) {
                for (int i = 0; i < idpNrNamec.length; i++) {
                    for (int j = 0; j < p.clan.members.size(); j++) {
                        if (idpNrNamec[i] == p.clan.members.get(j).id) {
                            count++;
                        }
                    }
                }
                if (count == (byte) 7) {
                    return true;
                }
            }
        }
        return false;
    }

    public void teleportToNrNamec(Player p) {
        if (p.idNRNM != -1) {
            return;
        }
        int idMAP = mapNrNamec[p.idGo];
        int idZone = zoneNrNamec[p.idGo];
        Zone z = Manager.MAPS.get(idMAP).zones.get(idZone);
        if (z != null && !z.items.isEmpty()) {
            for (int i = 0; i < z.items.size(); i++) {
                ItemMap it = z.items.get(i);
                if (it != null && (it.isNamecBall || it.itemTemplate.id == 362)) {
                    ChangeMapService.gI().changeMap(p, z, Util.nextInt(100, z.map.mapWidth), 5);
                    return;
                }
            }
        }
        Player player = Client.gI().getPlayer(idpNrNamec[p.idGo]);
        if (player != null && player.isPl()) {
            ChangeMapService.gI().changeMap(p, player.zone, Util.nextInt(100, player.zone.map.mapWidth), 5);
        }
    }

    public String getDis(Player pl, int id, short temp) {
        try {
            int idMAP = mapNrNamec[id];
            int idZone = zoneNrNamec[id];
            Integer[] sttMap = {7, 8, 9, 10, 11, 12, 13, 25, 31, 32, 33, 34, 43};
            Zone z = Manager.MAPS.get(idMAP).zones.get(idZone);
            if (z != null && !z.items.isEmpty()) {
                ItemMap it = z.getItemMapByTempId(temp);
                if (it != null) {
                    if (pl.zone.map.mapId == it.zone.map.mapId) {
                        if (pl.zone.zoneId == it.zone.zoneId) {
                            if ((pl.location.x - it.x) / 10 > 0) {
                                return (id + 1) + " Sao:" + Math.abs((pl.location.x - it.x) / 10) + " m(<-)";
                            } else if ((pl.location.x - it.x) / 10 < 0) {
                                return (id + 1) + " Sao:" + Math.abs((pl.location.x - it.x) / 10) + " m(->)";
                            } else {
                                return (id + 1) + " Sao";
                            }
                        }
                        return (id + 1) + " Sao:đây kv " + it.zone.zoneId;
                    } else {
                        List<Integer> check = Arrays.asList(sttMap);
                        if (check.contains(pl.zone.map.mapId)) {
                            int index = findIndex(pl.zone.map.mapId);
                            int indexMap = findIndex(idMAP);
                            int w = 0;
                            for (int i = 0; i < findIndex(index, indexMap).size(); i++) {
                                int map = findIndex(index, indexMap).get(i);
                                w += Manager.MAPS.get(map).mapWidth;
                            }
                            return (id + 1) + " Sao:" + z.map.mapName + " (" + Math.abs((pl.location.x - it.x - w) / 10) + " m)";
                        } else {
                            return (id + 1) + " Sao:Namếc ( ? m)";
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return getDisPlayer(pl, id, temp);
    }

    public String getDisPlayer(Player pl, int id, short temp) {
        try {
            String nPlayer = pNrNamec[id];
            Integer[] sttMap = {7, 8, 9, 10, 11, 12, 13, 25, 31, 32, 33, 34, 43};
            Player player = Client.gI().getPlayer(idpNrNamec[id]);
            if (player != null && player.isPl()) {
                int idMAP = player.zone.map.mapId;
                if (pl.zone.map.mapId == player.zone.map.mapId) {
                    if (pl.zone.zoneId == player.zone.zoneId) {
                        if ((pl.location.x - player.location.x) / 10 > 0) {
                            return (id + 1) + " Sao:" + Math.abs((pl.location.x - player.location.x) / 10) + " m(<-)(" + nPlayer + ")";
                        } else if ((pl.location.x - player.location.x) / 10 < 0) {
                            return (id + 1) + " Sao:" + Math.abs((pl.location.x - player.location.x) / 10) + " m(->)(" + nPlayer + ")";
                        } else {
                            return (id + 1) + " Sao(" + nPlayer + ")";
                        }
                    }
                    return (id + 1) + " Sao:đây kv " + player.zone.zoneId + " (" + nPlayer + ")";
                } else {
                    List<Integer> check = Arrays.asList(sttMap);
                    if (check.contains(pl.zone.map.mapId)) {
                        int index = findIndex(pl.zone.map.mapId);
                        int indexMap = findIndex(idMAP);
                        int w = 0;
                        for (int i = 0; i < findIndex(index, indexMap).size(); i++) {
                            int map = findIndex(index, indexMap).get(i);
                            w += Manager.MAPS.get(map).mapWidth;
                        }
                        return (id + 1) + " Sao:" + player.zone.map.mapName + " (" + Math.abs((pl.location.x - player.location.x - w) / 10) + " m)(" + nPlayer + ")";
                    } else {
                        return (id + 1) + " Sao:Namếc ( ? m)";
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (isReinit) {
            return getDisStone(pl, id, (short) 362);
        }
        return (id + 1) + " Sao:Namếc ( ? m)";
    }

    public String getDisStone(Player pl, int id, short temp) {
        try {
            int idMAP = mapNrNamec[id];
            int idZone = zoneNrNamec[id];
            Integer[] sttMap = {7, 8, 9, 10, 11, 12, 13, 25, 31, 32, 33, 34, 43};
            Zone z = Manager.MAPS.get(idMAP).zones.get(idZone);
            if (z != null && !z.items.isEmpty()) {
                ItemMap it = z.getItemMapByTempId(temp);
                if (it != null) {
                    if (pl.zone.map.mapId == it.zone.map.mapId) {
                        if (pl.zone.zoneId == it.zone.zoneId) {
                            if ((pl.location.x - it.x) / 10 > 0) {
                                return "Hóa thạch Ngọc Rồng:" + Math.abs((pl.location.x - it.x) / 10) + " m(<-)";
                            } else if ((pl.location.x - it.x) / 10 < 0) {
                                return "Hóa thạch Ngọc Rồng:" + Math.abs((pl.location.x - it.x) / 10) + " m(->)";
                            } else {
                                return "Hóa thạch Ngọc Rồng";
                            }
                        }
                        return "Hóa thạch Ngọc Rồng:đây kv " + it.zone.zoneId;
                    } else {
                        List<Integer> check = Arrays.asList(sttMap);
                        if (check.contains(pl.zone.map.mapId)) {
                            int index = findIndex(pl.zone.map.mapId);
                            int indexMap = findIndex(idMAP);
                            int w = 0;
                            for (int i = 0; i < findIndex(index, indexMap).size(); i++) {
                                int map = findIndex(index, indexMap).get(i);
                                w += Manager.MAPS.get(map).mapWidth;
                            }
                            return "Hóa thạch Ngọc Rồng:" + z.map.mapName + " (" + Math.abs((pl.location.x - it.x - w) / 10) + " m)";
                        } else {
                            return "Hóa thạch Ngọc Rồng:Namếc ( ? m)";
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Hóa thạch Ngọc Rồng:Namếc ( ? m)";
    }

    public byte findIndex(int id) {
        Integer[] sttMap = {7, 8, 9, 10, 11, 12, 13, 25, 31, 32, 33, 34, 43};
        for (byte i = 0; i < sttMap.length; i++) {
            if (sttMap[i] == id) {
                return i;
            }
        }
        return -1;
    }

    public List<Integer> findIndex(int start, int stop) {
        List<Integer> a = new ArrayList<>();
        Integer[] sttMap = {7, 8, 9, 10, 11, 12, 13, 25, 31, 32, 33, 34, 43};
        if (start < stop) {
            for (int i = start; i < stop; i++) {
                a.add(sttMap[i]);
            }
        } else {
            for (int i = stop; i < start; i++) {
                a.add(sttMap[i]);
            }
        }
        return a;
    }

    public boolean pickNamekBall(Player player, Item item) {
        try {
            switch (item.template.id) {
                case 362:
                    Service.gI().sendThongBao(player, "Chỉ là cục đá, vác chi cho nặng");
                    return false;
                case 353:
                case 354:
                case 355:
                case 356:
                case 357:
                case 358:
                case 359:
                    if (System.currentTimeMillis() >= NgocRongNamecService.gI().tOpenNrNamec) {
                        if (player.idNRNM == -1) {
                            PlayerService.gI().changeAndSendTypePK(player, ConstPlayer.PK_ALL);
                            if (player.pet != null) {
                                PlayerService.gI().changeAndSendTypePK(player.pet, ConstPlayer.PK_ALL);
                            }
                            player.idNRNM = item.template.id;
                            NgocRongNamecService.gI().mapNrNamec[item.template.id - 353] = player.zone.map.mapId;
                            NgocRongNamecService.gI().nameNrNamec[item.template.id - 353] = player.zone.map.mapName;
                            NgocRongNamecService.gI().zoneNrNamec[item.template.id - 353] = (byte) player.zone.zoneId;
                            NgocRongNamecService.gI().pNrNamec[item.template.id - 353] = player.name;
                            NgocRongNamecService.gI().idpNrNamec[item.template.id - 353] = (int) player.id;
                            player.lastTimePickNRNM = System.currentTimeMillis();
                            Service.gI().sendFlagBag(player);
                            return true;
                        } else {
                            Service.gI().sendThongBao(player, "Ngọc quá bự, bạn chỉ có thể mang theo 1 viên");
                            return false;
                        }
                    } else {
                        Service.gI().sendThongBao(player, "Chỉ là cục đá, vác chi cho nặng");
                        return false;
                    }
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void run() {
        while (ServerManager.isRunning) {
            try {
                if (this.isReinit && this.lastTimeReinit - System.currentTimeMillis() <= 0) {
                    removeStoneNrNamec();
                    initNgocRongNamec((byte) 0);
                    this.isReinit = false;
                }
                if (Util.canDoWithTime(this.lastTimeReInitNRNM, 600000)) {
                    removeStoneNrNamec();
                    reInitNgocRongNamec();
                    this.lastTimeReInitNRNM = System.currentTimeMillis();
                }

                Thread.sleep(1000);
            } catch (Exception e) {
            e.printStackTrace();
            }
        }
    }

    public void dropNamekBall(Player pl) {
        if (!pl.isPet && !pl.isBoss && pl.idNRNM != -1) {
            int x = pl.location.x;
            int y = pl.zone.map.yPhysicInTop(x, 100);
            ItemMap itemMap = new ItemMap(pl.zone, pl.idNRNM, 1, x, y, -1);
            Service.gI().dropItemMap(pl.zone, itemMap);
            NgocRongNamecService.gI().pNrNamec[pl.idNRNM - 353] = "";
            NgocRongNamecService.gI().idpNrNamec[pl.idNRNM - 353] = -1;
            pl.idNRNM = -1;
            PlayerService.gI().changeAndSendTypePK(pl, ConstPlayer.NON_PK);
            if (pl.pet != null) {
                PlayerService.gI().changeAndSendTypePK(pl.pet, ConstPlayer.NON_PK);
            }
            Service.gI().sendFlagBag(pl);
        }
    }

    //Rồng Thần đã xuất hiện, không thể nhặt ngọc được nữa
    public void menuCheckTeleNamekBall(Player pl) {
        String nrnm = "";
        for (int i = 0; i < 7; i++) {
            nrnm += NgocRongNamecService.gI().getDis(pl, i, (short) (353 + i)) + "\n";
        }
        if (pl.idNRNM != -1) {
            NpcService.gI().createMenuConMeo(pl, ConstNpc.CONFIRM_TELE_NAMEC, 2294, nrnm.trim(), "Kết thúc");
            return;
        }
        NpcService.gI().createMenuConMeo(pl, ConstNpc.CONFIRM_TELE_NAMEC, 2294, nrnm.trim(), "Đến ngay\nViên " + (pl.idGo + 1) + " Sao\n50 ngọc", "Kết thúc");
    }
}
