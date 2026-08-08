package nro.models.npc;

import nro.models.item.Item;
import nro.models.network.Message;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.Service;
import nro.models.utils.Logger;

public class DuaHauEgg {

    private static final long TIME_DONE = 86400000L;
    private Player player;
    public long lastTimeCreate;
    public long timeDone;
    private final short id = 51;
    boolean isPlanted = false;
    public long timeStart;

    public DuaHauEgg(Player player, long lastTimeCreate, long timeDone) {
        this.player = player;
        this.lastTimeCreate = lastTimeCreate;
        this.timeDone = timeDone;
    }

    public static void createDuaHauEgg(Player player) {
        player.DuaHauEgg = new DuaHauEgg(player, System.currentTimeMillis(), TIME_DONE);
    }

    public void sendDuaHauEgg() {
        Message msg;
        try {
            msg = new Message(-122);
            msg.writer().writeShort(this.id);
            msg.writer().writeByte(1);

            short iconId;
            int secondsLeft = getSecondDone();

            if (secondsLeft <= 6 * 60 * 60) {
                iconId = 4672;
            } else if (secondsLeft <= 12 * 60 * 60) {
                iconId = 4671;
            } else if (secondsLeft <= 18 * 60 * 60) {
                iconId = 4670;
            } else {
                iconId = 4669;
            }

            msg.writer().writeShort(iconId);
            msg.writer().writeByte(0);
            msg.writer().writeInt(secondsLeft);
            Service.gI().sendMessAllPlayerInMap(player, msg);
            msg.cleanup();
        } catch (Exception e) {
            Logger.logException(DuaHauEgg.class, e);
        }
    }

    public int getSecondDone() {
        int seconds = (int) ((lastTimeCreate + timeDone - System.currentTimeMillis()) / 1000);
        return seconds > 0 ? seconds : 0;
    }

    public void openEgg() {
        try {
            if (getSecondDone() == 0) {
                Item dua = ItemService.gI().createNewItem((short) 569);
                InventoryService.gI().addItemBag(player, dua);
                InventoryService.gI().sendItemBags(player);
                Service.gI().sendThongBao(player, "Bạn đã thu hoạch dưa hấu thành công!");
                this.lastTimeCreate = System.currentTimeMillis();
                this.timeDone = TIME_DONE;
                this.sendDuaHauEgg();
            } else {
                Service.gI().sendThongBao(player, "Dưa hấu chưa chín, vui lòng đợi thêm.");
            }
        } catch (Exception e) {
            Logger.logException(DuaHauEgg.class, e);
        }
    }

    public void subTimeDone(int d, int h, int m, int s) {
        this.timeDone -= ((d * 24 * 60 * 60 * 1000) + (h * 60 * 60 * 1000) + (m * 60 * 1000) + (s * 1000));
        this.sendDuaHauEgg();
    }

    public void dispose() {
        this.player = null;
    }

    public void plant() {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }
}
