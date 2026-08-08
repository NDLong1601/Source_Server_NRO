package nro.models.player;

import lombok.Getter;
import lombok.Setter;

/**
 *
 * @author By Mr Blue
 * 
 */

public class Traning {

    @Setter
    @Getter
    private int top;

    @Setter
    @Getter
    private int topWhis;

    @Setter
    @Getter
    private int time;

    @Setter
    @Getter
    private long lastTime;

    @Setter
    @Getter
    private int lastTop;

    @Setter
    @Getter
    private long lastRewardTime;

}
