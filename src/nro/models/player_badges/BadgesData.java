package nro.models.player_badges;

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

    public BadgesData(int id, int days) {
        this(id, System.currentTimeMillis() + Math.max(1, days) * 24L * 60 * 60 * 1000, true);
    }

    @Override
    public String toString() {
        final String n = "\"";
        return "{" + n + "idBadGes" + n + ":" + n + idBadGes + n + "," + n + "timeofUseBadges" + n + ":" + n + timeofUseBadges + n + "," + n + "isUse" + n + ":" + n + isUse + n + "}";
    }
}
