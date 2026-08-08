package nro.models.minigame;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import nro.models.player.Player;
import nro.models.server.Client;
import nro.models.services.Service;
import nro.models.utils.Util;

/**
 *
 * @author By Mr Blue
 */
public class ConSoMayManGem implements Runnable {

    public long second = 50;
    public long currlast = System.currentTimeMillis();
    public long rewardAmount = 450;
    public long cost = 5;
    public long min = 0;
    public long max = 100;
    public long result = 0;
    public long result_next = Util.nextInt((int) min, (int) max);
    public String result_name;

    public List<ConSoMayManData> players = new ArrayList<>();
    public List<Long> dataKQ_CSMM = new ArrayList<>();

    private static ConSoMayManGem instance;
    private boolean inBettingPhase = true;

    private ConSoMayManGem() {
    }

    public static ConSoMayManGem gI() {
        if (instance == null) {
            instance = new ConSoMayManGem();
        }
        return instance;
    }

    @Override
    public void run() {
        while (true) {
            try {
                if (inBettingPhase) {
                    if (second > 0) {
                        second--;
                    } else {
                        inBettingPhase = false;
                        currlast = System.currentTimeMillis();
                    }
                } else {
                    if ((System.currentTimeMillis() - currlast) >= 10000) {
                        ResetGame((int) result_next);
                        result_next = Util.nextInt((int) min, (int) max);
                        second = 50;
                        currlast = System.currentTimeMillis();
                        inBettingPhase = true;
                    }
                }
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void newData(Player player, int point) {
        if (!inBettingPhase) {
            Service.gI().sendThongBao(player, "Đã hết thời gian đặt cược cho vòng này. Vui lòng chờ vòng mới.");
            return;
        }

        if (player.inventory.gem < this.cost) {
            Service.gI().sendThongBao(player, "Bạn không đủ " + this.cost + " ngọc để thực hiện");
            return;
        }

        if (players.stream().filter(d -> d.id == player.id).count() >= 10) {
            Service.gI().sendThongBao(player, "Bạn đã chọn 10 số rồi không thể chọn thêm");
            return;
        }

        if (players.stream().anyMatch(d -> d.id == player.id && d.point == point)) {
            Service.gI().sendThongBao(player, "Số này bạn đã chọn rồi vui lòng chọn số khác.");
            return;
        }

        ConSoMayManData data = new ConSoMayManData();
        data.id = (int) player.id;
        data.point = point;
        data.conSoMayManNgoc = 1;
        data.conSoMayManVang = 0;
        players.add(data);
        player.inventory.gem -= (this.cost);
        Service.gI().sendMoney(player);
        Service.gI().sendThongBao(player, "Bạn đã chọn số " + point + " bằng " + this.cost + " ngọc.");
        Service.gI().showYourNumber(player, strNumber((int) player.id), null, null, 0);
    }

    public void ramdom1SoLe(Player player) {
        if (!inBettingPhase) {
            Service.gI().sendThongBao(player, "Đã hết thời gian đặt cược cho vòng này. Vui lòng chờ vòng mới.");
            return;
        }

        if (player.inventory.gem < this.cost) {
            Service.gI().sendThongBao(player, "Bạn không đủ " + this.cost + " ngọc để thực hiện");
            return;
        }

        if (players.stream().filter(d -> d.id == player.id).count() >= 10) {
            Service.gI().sendThongBao(player, "Bạn đã chọn 10 số rồi không thể chọn thêm");
            return;
        }

        Random random = new Random();
        int generatedPoint;
        do {
            generatedPoint = random.nextInt(50) * 2 + 1;
            final int currentPoint = generatedPoint;
            if (players.stream().filter(d -> d.id == player.id && d.point == currentPoint).findAny().isEmpty()) {
                break;
            }
            if (players.stream().filter(d -> d.id == player.id).count() >= 50) {
                Service.gI().sendThongBao(player, "Bạn đã chọn tất cả các số lẻ khả dụng.");
                return;
            }
        } while (true);

        ConSoMayManData data = new ConSoMayManData();
        data.id = (int) player.id;
        data.point = generatedPoint;
        data.conSoMayManNgoc = 1;
        data.conSoMayManVang = 0;
        players.add(data);
        player.inventory.gem -= this.cost;
        Service.gI().sendMoney(player);
        Service.gI().sendThongBao(player, "Bạn đã chọn ngẫu nhiên số lẻ " + generatedPoint + " bằng " + this.cost + " ngọc.");
        Service.gI().showYourNumber(player, strNumber((int) player.id), null, null, 0);
    }

    public void ramdom1SoChan(Player player) {
        if (!inBettingPhase) {
            Service.gI().sendThongBao(player, "Đã hết thời gian đặt cược cho vòng này. Vui lòng chờ vòng mới.");
            return;
        }

        if (player.inventory.gem < this.cost) {
            Service.gI().sendThongBao(player, "Bạn không đủ " + this.cost + " ngọc để thực hiện");
            return;
        }

        if (players.stream().filter(d -> d.id == player.id).count() >= 10) {
            Service.gI().sendThongBao(player, "Bạn đã chọn 10 số rồi không thể chọn thêm");
            return;
        }

        Random random = new Random();
        int generatedPoint;
        do {
            generatedPoint = random.nextInt(51) * 2;
            final int currentPoint = generatedPoint;
            if (players.stream().filter(d -> d.id == player.id && d.point == currentPoint).findAny().isEmpty()) {
                break;
            }
            if (players.stream().filter(d -> d.id == player.id).count() >= 51) {
                Service.gI().sendThongBao(player, "Bạn đã chọn tất cả các số chẵn khả dụng.");
                return;
            }
        } while (true);

        ConSoMayManData data = new ConSoMayManData();
        data.id = (int) player.id;
        data.point = generatedPoint;
        data.conSoMayManNgoc = 1;
        data.conSoMayManVang = 0;
        players.add(data);
        player.inventory.gem -= this.cost;
        Service.gI().sendMoney(player);
        Service.gI().sendThongBao(player, "Bạn đã chọn ngẫu nhiên số chẵn " + generatedPoint + " bằng " + this.cost + " ngọc.");
        Service.gI().showYourNumber(player, strNumber((int) player.id), null, null, 0);
    }

    public String strNumber(int id) {
        String number = "";
        List<ConSoMayManData> pl = players.stream().filter(d -> d.id == id).collect(Collectors.toList());
        for (int i = 0; i < pl.size(); i++) {
            ConSoMayManData d = pl.get(i);
            number += d.point + (i >= pl.size() - 1 ? "" : ",");
        }
        return number;
    }

    public String strFinish(int id) {
        String finish = "Con số trúng thưởng là " + result + " chúc bạn may mắn lần sau";
        Player currentPlayer = Client.gI().getPlayer(id);

        for (ConSoMayManData g : players) {
            if (id == g.id && g.point == result) {
                if (currentPlayer != null) {
                    if (g.conSoMayManNgoc == 1) {
                        finish = "Chúc mừng " + currentPlayer.name + " đã thắng " + this.rewardAmount + " ngọc với con số may mắn " + result;
                        currentPlayer.inventory.gem += this.rewardAmount;
                        Service.gI().sendMoney(currentPlayer);
                    }
                } else {
                    if (g.conSoMayManNgoc == 1) {
                        finish = "Chúc mừng người chơi ID: " + g.id + " đã thắng " + this.rewardAmount + " ngọc với con số may mắn " + result;
                    }
                }
                break;
            }
        }
        return finish;
    }

    public void ResetGame(int result) {
        this.result = result;
        dataKQ_CSMM.add(this.result);
        this.result_name = "";

        for (ConSoMayManData g : players) {
            Player player = Client.gI().getPlayer(g.id);
            if (player != null) {
                Service.gI().showYourNumber(player, "", result + "", strFinish(g.id), 1);
            }

            if (g.point == result) {
                if (player != null) {
                    result_name += player.name + ",";
                } else {
                    result_name += "ID:" + g.id + ",";
                }
            }
        }

        if (result_name.length() > 0) {
            result_name = result_name.substring(0, result_name.length() - 1);
        }

        for (ConSoMayManData g : players) {
            Player player = Client.gI().getPlayer(g.id);
            if (player != null) {
                String msg = "Con số may mắn Ngọc vòng này là: " + result;
                if (!result_name.isEmpty()) {
                    msg += ". Người thắng cuộc: " + result_name;
                } else {
                    msg += ". Không có người thắng cuộc.";
                }
                Service.gI().sendThongBao(player, msg);
            }
        }

        players.clear();
    }

}
