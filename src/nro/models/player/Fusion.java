package nro.models.player;

import nro.models.consts.ConstPlayer;
import lombok.Setter;
import nro.models.utils.Util;

public class Fusion {

    @Setter
    private Player player;
    public byte typeFusion;
    public long lastTimeFusion;

    public Fusion(Player player) {
        this.player = player;
    }

    public void update() {
        if (typeFusion == ConstPlayer.LUONG_LONG_NHAT_THE
                && Util.canDoWithTime(lastTimeFusion, PetConfig.getFusionDurationMs())) {
            this.player.pet.unFusion();
        }
    }

    public void dispose() {
        this.player = null;
    }

}
