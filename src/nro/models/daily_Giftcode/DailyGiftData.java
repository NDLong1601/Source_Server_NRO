package nro.models.daily_Giftcode;

public class DailyGiftData {

    public byte id;
    public boolean daNhan;

    public DailyGiftData() {
        id = -1;
        daNhan = false;
    }

    @Override
    public String toString() {
        final String n = "\"";
        return "{" + n + "id" + n + ":" + n + id + n + "," + n + "daNhan" + n + ":" + n + daNhan + n + "}";
    }
}
