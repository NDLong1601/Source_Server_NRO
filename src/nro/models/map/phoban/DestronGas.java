package nro.models.map.phoban;

import nro.models.utils.Functions;
import nro.models.boss.Boss;
import nro.models.boss.khi_gas.DrLychee;
import nro.models.clan.Clan;
import nro.models.map.Zone;
import nro.models.mob.Mob;
import nro.models.player.Player;
import nro.models.services.ItemTimeService;
import nro.models.map.service.MapService;
import nro.models.services.Service;
import nro.models.map.service.ChangeMapService;
import nro.models.utils.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import lombok.Data;
import nro.models.server.Maintenance;
import nro.models.map.service.ItemMapService;
import nro.models.utils.TimeUtil;

@Data
public class DestronGas implements Runnable {

    public static final long POWER_CAN_GO_TO_KHI_GAS_HUY_DIET = 2000000000;
    public static final int AVAILABLE = 50;
    public static final int TIME_KHI_GAS_HUY_DIET = 1800000;
    public static final int N_PLAYER_CLAN = 0;

    public int id;
    public byte level;
    public final List<Zone> zones;

    public Clan clan;
    public boolean isOpened;
    private long lastTimeOpen;
    private long lastTimeUpdateMessage;
    private boolean kickoutkghd;
    private long timeKickOutKGHD;
    public List<Boss> bosses = new ArrayList<>();
    private boolean callBoss;
    public boolean hatchiyatchDead;

    public DestronGas(int id) {
        this.id = id;
        this.zones = new ArrayList<>();
    }

    @Override
    public void run() {
        while (!Maintenance.isRunning && isOpened) {
            try {
                long startTime = System.currentTimeMillis();
                update();
                Functions.sleep(Math.max(150 - (System.currentTimeMillis() - startTime), 10));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void update() {
        if (isOpened) {
            if (Util.canDoWithTime(lastTimeOpen, TIME_KHI_GAS_HUY_DIET) || (kickoutkghd && Util.canDoWithTime(timeKickOutKGHD, 60000))) {
                finish();
                dispose();
            }

            boolean allCharactersDead = true;
            for (Zone zone : zones) {
                for (Mob mob : zone.mobs) {
                    if (!mob.isDie()) {
                        allCharactersDead = false;
                        break;
                    }
                }
            }

            if (allCharactersDead && !callBoss) {
                try {
                    long bossDamage = (1000 * level);
                    long bossMaxHealth = (15000000 * level);
                    bossDamage = Math.min(bossDamage, 200000000L);
                    bossMaxHealth = Math.min(bossMaxHealth, 2000000000L);
                    bosses.add(new DrLychee(
                            getMapById(148),
                            clan,
                            level,
                            (int) bossDamage,
                            (int) bossMaxHealth
                    ));
                    callBoss = true;
                } catch (Exception exception) {
                }
            }

            if (!kickoutkghd && (hatchiyatchDead || Util.canDoWithTime(lastTimeOpen, TIME_KHI_GAS_HUY_DIET - 60000))) {
                kickoutkghd = true;
                timeKickOutKGHD = System.currentTimeMillis();
                for (Zone zone : zones) {
                    List<Player> players = zone.getPlayers();
                    for (Player pl : players) {
                        Service.gI().sendThongBao(pl, "Nơi này sắp nổ tung mau chạy đi");
                    }

                }
            }
            if (kickoutkghd && Util.canDoWithTime(lastTimeUpdateMessage, 10000)) {
                lastTimeUpdateMessage = System.currentTimeMillis();
                for (Zone zone : zones) {
                    List<Player> players = zone.getPlayers();
                    for (Player pl : players) {
                        Service.gI().sendThongBao(pl, "Về làng Aru sau " + TimeUtil.getTimeLeft(timeKickOutKGHD, 60) + " nữa");
                    }
                }
            }

        }
    }

    public void openKhiGasHuyDiet(Player plOpen, Clan clan, byte level) {
        try {
            this.level = level;
            this.lastTimeOpen = System.currentTimeMillis();
            this.clan = clan;
            this.clan.lastTimeOpenKhiGasHuyDiet = this.lastTimeOpen;
            this.clan.playerOpenKhiGasHuyDiet = plOpen;
            this.clan.KhiGasHuyDiet = this;
            this.callBoss = false;
            this.isOpened = true;
            this.init();
            sendTextKhiGasHuyDiet();
        } catch (Exception e) {
            plOpen.clan.lastTimeOpenKhiGasHuyDiet = 0;
            this.dispose();
        }
    }

    public void sendThanhTichKhiGas(Player pl) {
        if (pl == null || pl.clan == null || pl.clan.KhiGasHuyDiet != this) {
            return;
        }
        long timeDoneKhiGas = System.currentTimeMillis() - pl.clan.lastTimeOpenKhiGasHuyDiet;
        int levelDoneKhiGas = pl.clan.KhiGasHuyDiet.level;

        if (levelDoneKhiGas > pl.clan.levelDoneKhiGas) {
            pl.clan.levelDoneKhiGas = levelDoneKhiGas;
            pl.clan.thoiGianHoanThanhKhiGas = timeDoneKhiGas;
        } else if (levelDoneKhiGas == pl.clan.levelDoneKhiGas) {
            if (timeDoneKhiGas < pl.clan.thoiGianHoanThanhKhiGas) {
                pl.clan.thoiGianHoanThanhKhiGas = timeDoneKhiGas;
            }
        }

        if (levelDoneKhiGas >= 70) {
            pl.destronGas70CompletionCount++;
        }

        pl.clan.updatethanhTichKhiGasForLeader();
    }

    private void init() {
        //Hồi sinh quái
        for (Zone zone : this.zones) {
            List<Mob> mobs = zone.mobs;
            for (int i = 0; i < mobs.size(); i++) {
                Mob mob = mobs.get(i);
                if (((i == 0) && zone.map.mapId == 147)
                        || // Quái 76 xuất hiện thứ 1 ở Map 147 (Sa mạc)
                        ((i == 7) && zone.map.mapId == 149)
                        || // Quái 76 xuất hiện thứ 8 ở Map 149 (Thành phố Santa)
                        ((i == 0) && zone.map.mapId == 151)
                        || // Quái 76 xuất hiện thứ 1 ở Map 151 (Hành tinh bóng tối)
                        ((i == 0) && zone.map.mapId == 152)
                        || // Quái 76 xuất hiện thứ 1 ở Map 152 (Vùng đất băng giá)
                        ((i == 33) && zone.map.mapId == 152)) { // Quái 76 xuất hiện thứ 34 ở Map 152 (Vùng đất băng giá)

                    mob.lvMob = 1;
                    mob.point.dame = (int) Math.min((long) level * 31 * 5 * mob.tempId * 10, 2_147_483_647);
                    mob.point.maxHp = (int) Math.min((long) level * 3 * 6700 * mob.tempId * 10, 2_147_483_647);
                    mob.hoiSinh();
                    mob.hoiSinhMobPhoBan();
                } else {
                    mob.lvMob = mob.tempId == 76 ? 1 : 0;
                    mob.point.dame = (int) Math.min((long) level * 31 * 5 * mob.tempId, 2_147_483_647);
                    mob.point.maxHp = (int) Math.min((long) level * 5 * 45 * mob.tempId, 2_147_483_647);
                    mob.hoiSinh();
                    mob.hoiSinhMobPhoBan();
                }
            }
        }
        Executors.newSingleThreadExecutor().submit(this, "Khí Gas Hủy Diệt: " + this.clan.name);
    }

    //kết thúc khí gas hủy diệt
    public void finish() {
        for (Zone zone : zones) {
            for (int i = zone.getPlayers().size() - 1; i >= 0; i--) {
                if (i < zone.getPlayers().size()) {
                    Player pl = zone.getPlayers().get(i);
                    sendThanhTichKhiGas(pl);
                    kickOutOfKGHD(pl);
                }
            }

        }
    }

    private void kickOutOfKGHD(Player player) {
        if (MapService.gI().isMapKhiGasHuyDiet(player.zone.map.mapId)) {
            ChangeMapService.gI().changeMapBySpaceShip(player, 0, -1, -1);
        }
    }

    public Zone getMapById(int mapId) {
        for (Zone zone : this.zones) {
            if (zone.map.mapId == mapId) {
                return zone;
            }
        }
        return null;
    }

    private void sendTextKhiGasHuyDiet() {
        for (Player pl : this.clan.membersInGame) {
            ItemTimeService.gI().sendTextKhiGasHuyDiet(pl);
        }
    }

    private void removeTextKhiGasHuyDiet() {
        for (Player pl : this.clan.membersInGame) {
            ItemTimeService.gI().removeTextKhiGasHuyDiet(pl);
        }
    }

    public void dispose() {
        for (Zone zone : zones) {
            for (int i = zone.items.size() - 1; i >= 0; i--) {
                if (i < zone.items.size()) {
                    ItemMapService.gI().removeItemMap(zone.items.get(i));
                }
            }
        }
        for (Boss boss : bosses) {
            if (!boss.isDie()) {
                boss.leaveMap();
            }
        }
        this.removeTextKhiGasHuyDiet();
        this.bosses.clear();
        this.isOpened = false;
        this.clan.KhiGasHuyDiet = null;
        this.clan = null;
        this.kickoutkghd = false;
        this.hatchiyatchDead = false;
    }
}
