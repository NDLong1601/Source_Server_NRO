package nro.models.task;

import nro.models.consts.ConstTask;
import nro.models.utils.Util;

/**
 *
 * @author By Mr Blue
 * 
 */

public class ClanTask {

    public ClanTaskTemplate template;

    public int count;

    public int maxCount;

    public int level;

    public int leftTask;

    public long receivedTime;

    public boolean notify0;
    public boolean notify10;
    public boolean notify20;
    public boolean notify30;
    public boolean notify40;
    public boolean notify50;
    public boolean notify60;
    public boolean notify70;
    public boolean notify80;
    public boolean notify90;

    public void reset() {
        this.template = null;
        this.count = 0;
        this.level = 0;
        this.notify0 = false;
        this.notify10 = false;
        this.notify20 = false;
        this.notify30 = false;
        this.notify40 = false;
        this.notify50 = false;
        this.notify60 = false;
        this.notify70 = false;
        this.notify80 = false;
        this.notify90 = false;
    }

    public ClanTask() {
        this.leftTask = ConstTask.MAX_CLAN_TASK;
    }

    public void renew() {
        if (Util.isAfterMidnight(receivedTime)) {
            this.leftTask = ConstTask.MAX_CLAN_TASK;
            this.receivedTime = System.currentTimeMillis();
        }
    }

    public boolean isDone() {
        return this.count >= this.maxCount;
    }

    public String getName() {
        if (this.template != null) {
            return this.template.name.replaceAll("%1", String.valueOf(maxCount));
        }
        return "Hiện tại không có nhiệm vụ nào";
    }

    public String getLevel() {
        switch (this.level) {
            case ConstTask.EASY:
                return "dễ";
            case ConstTask.NORMAL:
                return "bình thường";
            case ConstTask.HARD:
                return "khó";
            case ConstTask.VERY_HARD:
                return "rất khó";
            case ConstTask.HELL:
                return "địa ngục";
            default:
                return "null";
        }
    }

    public int getPercentProcess() {
        if (this.count >= this.maxCount) {
            return 100;
        }
        return (int) ((long) count * 100 / maxCount);
    }
}
