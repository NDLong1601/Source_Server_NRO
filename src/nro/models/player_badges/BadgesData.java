package nro.models.player_badges;

import nro.models.player.Player;

/**
 *
 * @author By Mr Blue
 * 
 */

public class BadgesData {

    public int idBadGes; // id danh hiệu
    public long timeofUseBadges; // hạn sử dụng danh hiệu
    public boolean isUse;

    public BadgesData() {
        idBadGes = -1;
        timeofUseBadges = -1;
        isUse = false;
    }

    public BadgesData(int id, long time, boolean isuse) {
        idBadGes = id;
        timeofUseBadges = time;
        isUse = isuse;
    }

    public BadgesData(Player player, int id, int days) {
        idBadGes = id;
        timeofUseBadges = System.currentTimeMillis() + days * 24 * 60 * 60 * 1000L;
        if (player.dataBadges != null) {
            for (BadgesData data2 : player.dataBadges) {
                data2.isUse = false;
            }
        }
        isUse = true;
        player.dataBadges.add(this);
    }

    @Override
    public String toString() {
        final String n = "\"";
        return "{" + n + "idBadGes" + n + ":" + n + idBadGes + n + "," + n + "timeofUseBadges" + n + ":" + n + timeofUseBadges + n + "," + n + "isUse" + n + ":" + n + isUse + n + "}";
    }
}
