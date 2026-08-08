package nro.models.radar;

/**
 *
 * @author By Mr Blue
 * 
 */

public class OptionCard {

    public int id;
    public int param;
    public byte active;

    public OptionCard(int i, int p, byte a) {
        id = i;
        param = p;
        active = a;
    }

    @Override
    public String toString() {
        final String n = "\"";
        return "{"
                + n + "id" + n + ":" + n + id + n + ","
                + n + "active" + n + ":" + n + active + n + ","
                + n + "param" + n + ":" + n + param + n
                + "}";
    }
}
