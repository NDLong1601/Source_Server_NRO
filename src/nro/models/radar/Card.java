package nro.models.radar;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author By Mr Blue
 * 
 */

public class Card {

    public short Id;
    public byte Amount;
    public byte MaxAmount;
    public byte Level;
    public byte Used;
    public List<OptionCard> Options;

    public Card() {
        Id = -1;
        Amount = 0;
        MaxAmount = 0;
        Level = 0;
        Used = 0;
        Options = new ArrayList<>();
    }

    public Card(byte m, List<OptionCard> o) {
        MaxAmount = m;
        Options = o;
    }

    public Card(short i, byte a, byte ma, byte le, List<OptionCard> o) {
        Id = i;
        Amount = a;
        MaxAmount = ma;
        Level = le;
        Options = o;
    }

    public Card(short i, byte a, byte ma, byte le, List<OptionCard> o, byte u) {
        Id = i;
        Amount = a;
        MaxAmount = ma;
        Level = le < 0 ? 1 : le;
        Options = o;
        Used = u;
    }

    @Override
    public String toString() {
        final String n = "\"";
        return "{"
                + n + "id" + n + ":" + n + Id + n + ","
                + n + "amount" + n + ":" + n + Amount + n + ","
                + n + "max" + n + ":" + n + MaxAmount + n + ","
                + n + "option" + n + ":" + Options + ","
                + n + "level" + n + ":" + n + Level + n + ","
                + n + "used" + n + ":" + n + Used + n
                + "}";
    }
}
