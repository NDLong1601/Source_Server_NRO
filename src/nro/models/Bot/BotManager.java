package nro.models.Bot;

import java.util.ArrayList;
import java.util.List;
import nro.models.server.ServerManager;

public class BotManager implements Runnable {

    public static BotManager i;

    public List<Bot> bot = new ArrayList<>();

    public static BotManager gI() {
        if (i == null) {
            i = new BotManager();
        }
        return i;
    }

    @Override
    public void run() {
        while (ServerManager.isRunning) {
            try {
                long st = System.currentTimeMillis();

                for (Bot bot : new ArrayList<>(this.bot)) {
                    if (bot != null) {
                        bot.update();
                    }
                }

                long timeLeft = 150 - (System.currentTimeMillis() - st);
                if (timeLeft > 0) {
                    Thread.sleep(timeLeft);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
