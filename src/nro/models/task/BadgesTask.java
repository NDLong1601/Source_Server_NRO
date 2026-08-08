package nro.models.task;

import nro.models.player.Player;
import nro.models.server.Manager;
import nro.models.task.ClanTaskTemplate;

/**
 *
 * @author By Mr Blue
 * 
 */

public class BadgesTask {

    public ClanTaskTemplate template;

    public int id;
    public int count;
    public int countMax;
    public int idBadgesReward;

    public BadgesTask() {
        id = -1;
        count = -1;
        countMax = -1;
        idBadgesReward = -1;
    }

    public boolean isDone() {
        return this.count >= this.countMax;
    }

public int getPercentProcess() {
   
    if (this.count >= this.countMax) {
        return 100;
    }
    int percent = (int) ((long) count * 100 / countMax);
    return percent;
}



    @Override
    public String toString() {
        final String n = "\"";
        return "{" + n + "id" + n + ":" + n + id + n + ","
                + n + "count" + n + ":" + n + count + n + ","
                + n + "countMax" + n + ":" + n + countMax + n + ","
                + n + "idBadgesReward" + n + ":" + n + idBadgesReward + n + "}";
    }
}
