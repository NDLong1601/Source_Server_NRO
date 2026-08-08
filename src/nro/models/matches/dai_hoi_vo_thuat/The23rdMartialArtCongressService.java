package nro.models.matches.dai_hoi_vo_thuat;
import nro.models.matches.giai_dau.The23rdMartialArtCongressManager;
import nro.models.map.Zone;
import nro.models.player.Player;
import nro.models.matches.dai_hoi_vo_thuat.The23rdMartialArtCongress;
import nro.models.network.Message;
import nro.models.map.service.MapService;
import nro.models.services.Service;
import nro.models.map.service.ChangeMapService;

public class The23rdMartialArtCongressService {

    private static The23rdMartialArtCongressService i;

    public static The23rdMartialArtCongressService gI() {
        if (i == null) {
            i = new The23rdMartialArtCongressService();
        }
        return i;
    }

    public void startChallenge(Player player) {
        if (The23rdMartialArtCongressManager.gI().plCheck(player)) {
            return;
        }
        Zone zone = getMapChallenge(129);
        if (zone != null) {
            ChangeMapService.gI().changeMap(player, zone, player.location.x, 360);
            setTimeout(() -> {
                The23rdMartialArtCongress mc = new The23rdMartialArtCongress();
                mc.setZone(zone);
                mc.setPlayer(player);
                mc.setNpc(zone.getNpc());
                mc.setRound(player.levelWoodChest);
                mc.toTheNextRound();
                The23rdMartialArtCongressManager.gI().add(mc);
                Service.gI().sendThongBao(player, "Số thứ tự của ngươi là 1 chuẩn bị thi đấu nhé.");
                Service.gI().releaseCooldownSkill(player);
                player.isPKDHVT = true;
                player.lastTimePKDHVT23 = System.currentTimeMillis();
                mc.endChallenge = false;
            }, 500);
        } else {
        }
    }

    public static void setTimeout(Runnable runnable, int delay) {
        new Thread(() -> {
            try {
                Thread.sleep(delay);
                runnable.run();
            } catch (Exception e) {
            }
        }).start();
    }

    public void sendTypePK(Player player, Player boss) {
        Message msg;
        try {
            msg = Service.gI().messageSubCommand((byte) 35);
            msg.writer().writeInt((int) boss.id);
            msg.writer().writeByte(3);
            player.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
        }
    }

    public Zone getMapChallenge(int mapId) {
        Zone map = MapService.gI().getMapWithRandZone(mapId);
        if (map.getNumOfBosses() < 1) {
            return map;
        }
        return null;
    }
}
