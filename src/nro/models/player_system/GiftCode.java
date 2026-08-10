package nro.models.player_system;

import nro.models.item.Item.ItemOption;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import nro.models.player.Player;

/**
 *
 * @author By Mr Blue
 * 
 */

public class GiftCode {

    public String code;
    public int countLeft;
    public int id;
    public HashMap<Integer, Integer> detail = new HashMap<>();
    public HashMap<Integer, ArrayList<ItemOption>> option = new HashMap<>();
    public Timestamp datecreate;
    public Timestamp dateexpired;

    public boolean isUsedGiftCode(Player player) {
        return player.giftCode.isUsedGiftCode(code);
    }

    public boolean timeCode() {
        long now = System.currentTimeMillis();
        return now < this.datecreate.getTime() || now > this.dateexpired.getTime();
    }

    public boolean hasStarted() {
        return System.currentTimeMillis() >= this.datecreate.getTime();
    }

    public boolean hasExpired() {
        return System.currentTimeMillis() > this.dateexpired.getTime();
    }
}
