package nro.models.minigame;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import nro.models.player.Player;
import nro.models.services.ChatGlobalService;
import nro.models.services.Service;
import nro.models.utils.Util;

/**
 *
 * @author Mr Blue
 */
public class ChonAiDay_Gem implements Runnable {

    public int gemNormar;
    public int gemVip;
    public long lastTimeEnd;
    public static final int TIME_CHONAIDAY = 300000;
    public List<Player> PlayersNormar = new ArrayList<>();
    public List<Player> PlayersVIP = new ArrayList<>();
    private static ChonAiDay_Gem instance;

    public static ChonAiDay_Gem gI() {
        if (instance == null) {
            instance = new ChonAiDay_Gem();
        }
        return instance;
    }

    public void addPlayerVIP(Player pl) {
        if (!PlayersVIP.contains(pl)) {
            PlayersVIP.add(pl);
        }
    }

    public void addPlayerNormar(Player pl) {
        if (!PlayersNormar.contains(pl)) {
            PlayersNormar.add(pl);
        }
    }

    public void removePlayerVIP(Player pl) {
        PlayersVIP.removeIf(player -> player != null && player.equals(pl));
    }

    public void removePlayerNormar(Player pl) {
        PlayersNormar.removeIf(player -> player == pl);
    }

    @Override
    public void run() {
        while (true) {
            try {
                if ((lastTimeEnd - System.currentTimeMillis()) / 1000 <= 0) {
                    List<Player> listN = new ArrayList<>();

                    PlayersNormar.stream().filter(p -> p != null && p.gemNormar > 0)
                            .sorted(Comparator.comparing(p -> Math.ceil(((double) p.gemNormar / gemNormar) * 100), Comparator.reverseOrder()))
                            .forEach(listN::add);

                    if (!listN.isEmpty()) {
                        if (listN.size() == 1) {
                            Player pl = listN.get(0);
                            if (pl != null && pl.inventory != null) {
                                int refund = pl.gemNormar * 90 / 100;
                                pl.inventory.gem += refund;
                                Service.gI().sendThongBao(pl, "Bạn là người duy nhất tham gia và bị hoàn lại 90% gem đã đặt (" + Util.mumberToBlue(refund) + ")");
                                Service.gI().sendMoney(pl);
                            }
                        } else {
                            int numWinners = Math.min(listN.size(), 5);
                            Player pl = listN.get(Util.nextInt(0, numWinners - 1));
                            if (pl != null && pl.inventory != null) {
                                String chatMessage = pl.name + " đã chiến thắng Chọn ai đây giải thưởng";
                                int goldC = gemNormar * 80 / 100;
                                pl.inventory.gem += goldC;
                                Service.gI().sendThongBao(pl, "Chúc mừng bạn đã giành chiến thắng và nhận được " + Util.mumberToBlue(goldC) + " hồng ngọc");
                                Service.gI().sendMoney(pl);
                                ChatGlobalService.gI().chat(pl, chatMessage);
                            }
                        }
                    }

                    listN.clear();

                    PlayersVIP.stream().filter(p -> p != null && p.gemVIP > 0)
                            .sorted(Comparator.comparing(p -> Math.ceil(((double) p.gemVIP / gemVip) * 100), Comparator.reverseOrder()))
                            .forEach(listN::add);

                    if (!listN.isEmpty()) {
                        if (listN.size() == 1) {
                            Player pl = listN.get(0);
                            if (pl != null && pl.inventory != null) {
                                int refund = pl.gemVIP * 90 / 100;
                                pl.inventory.gem += refund;
                                Service.gI().sendThongBao(pl, "Bạn là người duy nhất tham gia và bị hoàn lại 90% gem đã đặt (" + Util.mumberToBlue(refund) + ")");
                                Service.gI().sendMoney(pl);
                            }
                        } else {
                            int numWinners = Math.min(listN.size(), 5);
                            Player pl = listN.get(Util.nextInt(0, numWinners - 1));
                            if (pl != null && pl.inventory != null) {
                                String chatMessage = pl.name + " đã chiến thắng Chọn Ai Đây ngọc xanh giải VIP";
                                int goldC = gemVip * 90 / 100;
                                pl.inventory.gem += goldC;
                                Service.gI().sendThongBao(pl, "Chúc mừng bạn đã giành chiến thắng và nhận được " + Util.mumberToBlue(goldC) + " hồng ngọc");
                                Service.gI().sendMoney(pl);
                                ChatGlobalService.gI().chat(pl, chatMessage);
                            }
                        }
                    }

                    resetPlayers(PlayersNormar);
                    resetPlayers(PlayersVIP);
                    resetChonAiDay();
                }

                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void resetPlayers(List<Player> players) {
        players.forEach(player -> {
            if (player != null) {
                player.gemNormar = 0;
                player.gemVIP = 0;
            }
        });
        players.clear();
    }

    private void resetChonAiDay() {
        gemNormar = 0;
        gemVip = 0;
        lastTimeEnd = System.currentTimeMillis() + TIME_CHONAIDAY;
    }
}
