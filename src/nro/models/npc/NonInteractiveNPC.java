package nro.models.npc;

import nro.models.boss.BossID;
import nro.models.map.Map;
import nro.models.map.Zone;
import nro.models.matches.giai_dau.WorldMartialArtsTournamentManager;
import nro.models.server.Manager;
import nro.models.map.service.MapService;
import nro.models.services.Service;
import nro.models.utils.Util;
import nro.models.player.Player;
import nro.models.services.PlayerService;

public class NonInteractiveNPC extends Player {

    private long lastTimeChat;
    private long lastTimeChat2;
    private long lastTimeMove;
    private short head = -1;
    private short body = -1;
    private short leg = -1;

    public void initNonInteractiveNPC() {
        init();
    }

    @Override
    public short getHead() {
        return head;
    }

    @Override
    public short getBody() {
        return body;
    }

    @Override
    public short getLeg() {
        return leg;
    }

    public void joinMap(Zone z, Player player) {
        MapService.gI().goToMap(player, z);
        z.load_Me_To_Another(player);
    }

    @Override
    public void update() {
        if (this.isDie()) {
            Service.gI().hsChar(this, nPoint.hpMax, nPoint.mpMax);
        }
        if (this.id == BossID.KHI_BUBBLES) {
            move();
        }
        if (Util.canDoWithTime(lastTimeChat, 5000)) {
            if (this.id == BossID.KHI_BUBBLES) {
                if (Util.isTrue(2, 3)) {
                    String[] text = {"ù ù khẹc khẹc", "khẹc khẹc", "éc éc"};
                    Service.gI().chat(this, text[Util.nextInt(text.length)]);
                }
            }
            lastTimeChat = System.currentTimeMillis();
        }
        if (Util.canDoWithTime(lastTimeChat2, 10000)) {
            if (this.zone.map.mapId == 52) {
                if (this.id == -114) {
                    for (int i = 0; i < WorldMartialArtsTournamentManager.gI().chatText.size(); i++) {
                        if (WorldMartialArtsTournamentManager.gI().chatText != null
                                && !WorldMartialArtsTournamentManager.gI().chatText.isEmpty()) {
                            try {
                                Service.gI().chat(this, WorldMartialArtsTournamentManager.gI().chatText.get(i));
                            } catch (Exception e) {
                            }
                        }
                    }
                }
            }
            lastTimeChat2 = System.currentTimeMillis();
        }
    }

    private void move() {
        if (Util.canDoWithTime(lastTimeMove, 1000)) {
            if (Util.isTrue(2, 3)) {
                int x = this.location.x;
                x += Util.nextInt(-50, 50);
                if (x > 470 || x < 250) {
                    x = Util.nextInt(250, 470);
                }
                int y = 240;
                PlayerService.gI().playerMove(this, x, y);
            }
            lastTimeMove = System.currentTimeMillis();
        }

    }

    private void init() {
        for (Map m : Manager.MAPS) {
            switch (m.mapId) {
                case 45 -> {
                    for (Zone z : m.zones) {
                        NonInteractiveNPC pl = new NonInteractiveNPC();
                        pl.name = "Mr.PôPô";
                        pl.gender = 0;
                        pl.id = BossID.MRPOPO;
                        pl.head = 83;
                        pl.body = 84;
                        pl.leg = 85;
                        pl.nPoint.hpMax = 5100;
                        pl.nPoint.hpg = 5100;
                        pl.nPoint.hp = 5100;
                        pl.nPoint.setFullHpMp();
                        pl.location.x = 295;
                        pl.location.y = 408;
                        joinMap(z, pl);
                        z.setNpc(pl);
                    }
                }
                case 46 -> {
                    for (Zone z : m.zones) {
                        NonInteractiveNPC pl = new NonInteractiveNPC();
                        pl.name = "Yajirô";
                        pl.gender = 0;
                        pl.id = BossID.YAJIRO;
                        pl.head = 77;
                        pl.body = 78;
                        pl.leg = 79;
                        pl.nPoint.hpMax = 1100;
                        pl.nPoint.hpg = 1100;
                        pl.nPoint.hp = 1100;
                        pl.nPoint.setFullHpMp();
                        pl.location.x = 320;
                        pl.location.y = 408;
                        joinMap(z, pl);
                        z.setNpc(pl);
                    }
                }
                case 48 -> {
                    for (Zone z : m.zones) {
                        NonInteractiveNPC pl = new NonInteractiveNPC();
                        pl.name = "Khỉ Bubbles";
                        pl.gender = 0;
                        pl.id = BossID.KHI_BUBBLES;
                        pl.head = 95;
                        pl.body = 96;
                        pl.leg = 97;
                        pl.nPoint.hpMax = 30000;
                        pl.nPoint.hpg = 30000;
                        pl.nPoint.hp = 30000;
                        pl.nPoint.setFullHpMp();
                        pl.location.x = 360;
                        pl.location.y = 240;
                        joinMap(z, pl);
                        z.setNpc(pl);
                    }
                }
                case 51 -> {
                    for (Zone z : m.zones) {
                        NonInteractiveNPC pl = new NonInteractiveNPC();
                        pl.name = "Trọng Tài";
                        pl.gender = 0;
                        pl.id = -114;
                        pl.head = 114;
                        pl.body = 115;
                        pl.leg = 116;
                        pl.nPoint.hpMax = 500;
                        pl.nPoint.hpg = 500;
                        pl.nPoint.hp = 500;
                        pl.nPoint.setFullHpMp();
                        pl.location.x = 383;
                        pl.location.y = 112;
                        joinMap(z, pl);
                        z.setNpc(pl);
                    }
                }
                case 52 -> {
                    for (Zone z : m.zones) {
                        NonInteractiveNPC pl = new NonInteractiveNPC();
                        pl.name = "Trọng Tài";
                        pl.gender = 0;
                        pl.id = -114;
                        pl.head = 114;
                        pl.body = 115;
                        pl.leg = 116;
                        pl.nPoint.hpMax = 500;
                        pl.nPoint.hpg = 500;
                        pl.nPoint.hp = 500;
                        pl.nPoint.setFullHpMp();
                        pl.location.x = z.zoneId > 0 ? 301 : 373;
                        pl.location.y = 336;
                        joinMap(z, pl);
                        z.setNpc(pl);
                    }
                }
                case 129 -> {
                    for (Zone z : m.zones) {
                        NonInteractiveNPC pl = new NonInteractiveNPC();
                        pl.name = "Trọng Tài";
                        pl.gender = 0;
                        pl.id = -114;
                        pl.head = 114;
                        pl.body = 115;
                        pl.leg = 116;
                        pl.nPoint.hpMax = 500;
                        pl.nPoint.hpg = 500;
                        pl.nPoint.hp = 500;
                        pl.nPoint.setFullHpMp();
                        pl.location.x = 385;
                        pl.location.y = 264;
                        joinMap(z, pl);
                        z.setNpc(pl);
                    }
                }
                case 103 -> {
                    for (Zone z : m.zones) {
                        NonInteractiveNPC pl = new NonInteractiveNPC();
                        pl.name = "Trọng Tài";
                        pl.gender = 0;
                        pl.id = -114;
                        pl.head = 114;
                        pl.body = 115;
                        pl.leg = 116;
                        pl.nPoint.hpMax = 500;
                        pl.nPoint.hpg = 500;
                        pl.nPoint.hp = 500;
                        pl.nPoint.setFullHpMp();
                        pl.location.x = 401;
                        pl.location.y = 288;
                        joinMap(z, pl);
                        z.setNpc(pl);
                    }
                }
                case 146 -> {
                    for (Zone z : m.zones) {
                        NonInteractiveNPC pl = new NonInteractiveNPC();
                        pl.name = "Yajirô";
                        pl.gender = 0;
                        pl.id = -77;
                        pl.head = 77;
                        pl.body = 78;
                        pl.leg = 79;
                        pl.nPoint.hpMax = 1100;
                        pl.nPoint.hpg = 1100;
                        pl.nPoint.hp = 1100;
                        pl.nPoint.setFullHpMp();
                        pl.location.x = 100;
                        pl.location.y = 336;
                        joinMap(z, pl);
                        z.setNpc(pl);
                    }
                }
            }
        }
    }
}
