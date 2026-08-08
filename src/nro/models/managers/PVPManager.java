package nro.models.managers;

import nro.models.matches.PVP;
import nro.models.player.Player;
import nro.models.utils.Functions;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PVPManager implements Runnable {

    private static PVPManager i;
    private ArrayList<PVP> pvps;
    private boolean isRunning;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    public static PVPManager gI() {
        if (i == null) {
            i = new PVPManager();
        }
        return i;
    }

    public PVPManager() {
        this.pvps = new ArrayList<>();
        this.isRunning = true;
        executor.submit(this); 
    }

    public void removePVP(PVP pvp) {
        this.pvps.remove(pvp);
    }

    public void addPVP(PVP pvp) {
        this.pvps.add(pvp);
    }

    public PVP getPVP(Player player) {
        for (PVP pvp : this.pvps) {
            if (pvp.p1.equals(player) || pvp.p2.equals(player)) {
                return pvp;
            }
        }
        return null;
    }

    @Override
    public void run() {
        this.update();
    }

    private void update() {
        while (isRunning) {
            try {
                long st = System.currentTimeMillis();
                for (PVP pvp : pvps) {
                    pvp.update();
                }
                Functions.sleep(Math.max(1000 - (System.currentTimeMillis() - st), 10));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void stop() {
        this.isRunning = false;
        executor.shutdownNow();
    }

}
