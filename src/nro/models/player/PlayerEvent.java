package nro.models.player;

import lombok.Getter;
import lombok.Setter;
import nro.models.player.Player;

/**
 * @author By Mr Blue
 */
@Setter
@Getter
public class PlayerEvent {
    private int eventPoint;
    private Player player;
    public int luotNhanNgocMienPhi = 1;
    public int luotNhanCapsuleBang = 1;

    public PlayerEvent(Player player) {
        this.player = player;
    }
    
    public void addEventPoint(int num) {
        eventPoint += num;
    }
    
    public void subEventPoint(int num) {
        eventPoint -= num;
    }

    public void update() {
       
    }

}
