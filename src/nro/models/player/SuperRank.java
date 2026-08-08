package nro.models.player;

import java.util.ArrayList;
import java.util.List;
import nro.models.consts.ConstTaskBadges;
import nro.models.matches.dai_hoi_vo_thuat.SuperRankService;
import nro.models.services.Service;
import nro.models.map.service.NpcService;
import nro.models.task.BadgesTaskService;

/**
 *
 * @author By Mr Blue
 * 
 */

public class SuperRank {

    private Player player;
    public int rank;
    public int win;
    public int lose;
    public List<String> history;
    public List<Long> lastTime;
    public long lastPKTime;
    public long lastRewardTime;
    public int ticket = 3;

    public SuperRank(Player player) {
        this.player = player;
        this.history = new ArrayList<>();
        this.lastTime = new ArrayList<>();
    }

    public void history(String text, long lastTime) {
        if (this.history.size() > 4) {
            // this.history.removeFirst();
            // this.lastTime.removeFirst();
        }
        this.history.add(text);
        this.lastTime.add(lastTime);
    }

    public void reward() {
        if (rank == 1) {
            BadgesTaskService.updateCountBagesTask(player, ConstTaskBadges.CAO_THU_SIEU_HANG, 1);
        }

        int rw = SuperRankService.gI().reward(rank);
        if (rw != -1) {
            Service.gI().sendThongBao(player, "Bạn đang ở TOP " + rank + " Siêu Hạng, nhận được " + rw + " ngọc");
            player.inventory.gem += rw;
        }

        lastRewardTime = System.currentTimeMillis();
    }

    public void dispose() {
        history.clear();
        lastTime.clear();
        win = -1;
        lose = -1;
        lastPKTime = -1;
        player = null;
    }
}
