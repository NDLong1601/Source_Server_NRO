package nro.models.map;
import nro.models.player.Player;

public class MaBuHold {

    public int slot;
    public Player player;
    public int x;
    public int y;

    public MaBuHold(int slot, Player player) {
        this.slot = slot;
        this.player = player;
        this.x = slot == 0 ? 196 : slot == 1 ? 340 : slot == 2 ? 412 : 532;
        this.y = slot == 0 ? 257 : slot == 1 ? 256 : slot == 2 ? 232 : 257;
    }

}
