package nro.models.interfaces;

import nro.models.matches.TYPE_LOSE_PVP;
import nro.models.player.Player;


public interface IPVP {

    void start();

    void finish();

    void dispose();

    void update();

    void reward(Player plWin);

    void sendResult(Player plLose, TYPE_LOSE_PVP typeLose);

    void lose(Player plLose, TYPE_LOSE_PVP typeLose);

    boolean isInPVP(Player pl);
}
