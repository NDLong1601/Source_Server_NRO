package nro.models.services_func;

import nro.models.minigame.ConSoMayManGem;
import nro.models.minigame.ConSoMayManGold;

/**
 *
 * @author By Mr Blue
 */
public class MiniGame {

    private static MiniGame instance;
    public final ConSoMayManGold MiniGame_S1_Gold;
    public final ConSoMayManGem MiniGame_S1_Gem;

    private MiniGame() {
        MiniGame_S1_Gold = ConSoMayManGold.gI();
        MiniGame_S1_Gem = ConSoMayManGem.gI();
    }

    public static MiniGame gI() {
        if (instance == null) {
            synchronized (MiniGame.class) {
                if (instance == null) {
                    instance = new MiniGame();
                }
            }
        }
        return instance;
    }
}
