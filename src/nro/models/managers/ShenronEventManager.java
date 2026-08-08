package nro.models.managers;

import models.ShenronEvent;
import nro.models.utils.Functions;
import nro.models.utils.Util;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import nro.models.server.Maintenance;

public class ShenronEventManager implements Runnable {

    private static ShenronEventManager instance;
    private long lastUpdate;
    private static final List<ShenronEvent> list = new ArrayList<>();
    private boolean isRunning;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static ShenronEventManager gI() {
        if (instance == null) {
            instance = new ShenronEventManager();
        }
        return instance;
    }

    private ShenronEventManager() {
        isRunning = true;
        executor.submit(this);
    }

    @Override
    public void run() {
        while (isRunning && !Maintenance.isRunning) {
            try {
                long start = System.currentTimeMillis();
                update();
                long timeUpdate = System.currentTimeMillis() - start;
                Functions.sleep(Math.max(1000 - timeUpdate, 10));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public void update() {
        if (Util.canDoWithTime(lastUpdate, 1000)) {
            lastUpdate = System.currentTimeMillis();
            List<ShenronEvent> listCopy = new ArrayList<>(list);
            for (ShenronEvent se : listCopy) {
                try {
                    se.update();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            listCopy.clear();
        }
    }

    public void add(ShenronEvent se) {
        list.add(se);
    }

    public void remove(ShenronEvent se) {
        list.remove(se);
    }

    public void stop() {
        isRunning = false;
        executor.shutdownNow();
    }
}
