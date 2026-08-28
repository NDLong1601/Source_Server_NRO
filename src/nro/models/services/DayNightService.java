package nro.models.services;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import nro.models.network.Message;
import nro.models.player.Player;
import nro.models.server.Maintenance;
import nro.models.server.ServerManager;
import nro.models.services.shenron.Shenron_Manager;
import nro.models.services.shenron.SummonDragon;
import nro.models.services.shenron.SummonDragonNamek;
import nro.models.utils.Logger;

/**
 * Synchronizes the client-side dark-sky effect with real time in Viet Nam.
 *
 * The stock client already knows how to darken the scene when a Shenron packet
 * targets another map.  Reusing that visual-only branch keeps the HUD and the
 * real Shenron animation untouched, and costs only a tiny packet at a phase
 * change (or when a player finishes loading the map).
 */
public final class DayNightService implements Runnable {

    public static final ZoneId GAME_TIME_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalTime NIGHT_START = LocalTime.of(18, 0);
    private static final LocalTime DAY_START = LocalTime.of(6, 0);

    // This map id cannot be a live NRO map. It deliberately takes the client
    // through its existing remote-Shenron sky branch without spawning a dragon.
    private static final short SKY_OVERLAY_MAP_ID = Short.MIN_VALUE;

    private static DayNightService instance;
    private volatile boolean night;

    private DayNightService() {
        this.night = isNightNow();
    }

    public static synchronized DayNightService gI() {
        if (instance == null) {
            instance = new DayNightService();
        }
        return instance;
    }

    @Override
    public void run() {
        while (ServerManager.isRunning && !Maintenance.isRunning) {
            try {
                boolean nextNight = isNightNow();
                if (nextNight != this.night) {
                    this.night = nextNight;
                    applyPhaseChange();
                }
                Thread.sleep(1_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                Logger.logException(DayNightService.class, e, "Day/night update failed");
            }
        }
    }

    /** Sends the current visual phase to a player once their map is ready. */
    public void sendCurrentState(Player player) {
        if (player == null || !player.isPl()) {
            return;
        }
        if (isNightNow()) {
            sendNightOverlay(player);
        }
    }

    /**
     * Called directly after a real dragon leaves. At night it restores the
     * natural sky overlay; in daytime the normal Shenron hide packet already
     * returns the scene to daylight.
     */
    public void restoreAfterShenron() {
        this.night = isNightNow();
        if (this.night) {
            broadcastNightOverlay();
        }
    }

    public boolean isNight() {
        return this.night;
    }

    private void applyPhaseChange() {
        // A real Shenron has the higher visual priority. Its own leave packet
        // restores the current natural phase through restoreAfterShenron().
        if (isRealShenronActive()) {
            return;
        }
        if (this.night) {
            broadcastNightOverlay();
        } else {
            broadcastDaylight();
        }
    }

    private static boolean isNightNow() {
        LocalTime time = ZonedDateTime.now(GAME_TIME_ZONE).toLocalTime();
        return !time.isBefore(NIGHT_START) || time.isBefore(DAY_START);
    }

    private static boolean isRealShenronActive() {
        return SummonDragon.isShenronActive()
                || SummonDragonNamek.isShenronActive()
                || Shenron_Manager.hasActiveShenronEvent();
    }

    private static void broadcastNightOverlay() {
        try {
            Service.gI().sendMessAllPlayer(createNightOverlayMessage());
        } catch (Exception e) {
            Logger.logException(DayNightService.class, e, "Cannot broadcast night overlay");
        }
    }

    private static void sendNightOverlay(Player player) {
        Message message = null;
        try {
            message = createNightOverlayMessage();
            player.sendMessage(message);
        } catch (Exception e) {
            Logger.logException(DayNightService.class, e, "Cannot send night overlay");
        } finally {
            if (message != null) {
                message.cleanup();
            }
        }
    }

    private static void broadcastDaylight() {
        try {
            Message message = new Message(-83);
            // Matches the normal Shenron leave packet. Because the previous
            // natural packet used SKY_OVERLAY_MAP_ID, it only clears the sky
            // state and never asks the client to hide a real dragon effect.
            message.writer().writeByte(1);
            Service.gI().sendMessAllPlayer(message);
        } catch (Exception e) {
            Logger.logException(DayNightService.class, e, "Cannot broadcast daylight");
        }
    }

    private static Message createNightOverlayMessage() throws Exception {
        Message message = new Message(-83);
        message.writer().writeByte(0);
        message.writer().writeShort(SKY_OVERLAY_MAP_ID);
        message.writer().writeShort(0);
        message.writer().writeByte(0);
        message.writer().writeInt(0);
        message.writer().writeUTF("");
        message.writer().writeShort(0);
        message.writer().writeShort(0);
        message.writer().writeByte(0);
        return message;
    }
}
