package nro.models.player_badges;

import nro.models.item.Item;
import nro.models.player.Player;
import nro.models.server.Manager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 * @author By Mr Blue
 *
 */
public class BagesTemplate {

    public int id;
    public int idEffect;
    public int idItem;
    public String NAME;
    public List<Item.ItemOption> options = new ArrayList<>();

    public static int findIdItemByIdIdEffect(int idEffect) {
        for (BagesTemplate data : Manager.BAGES_TEMPLATES) {
            if (data.idEffect == idEffect) {
                return data.idItem;
            }
        }
        return -1;
    }

    public static int fineIdEffectbyIdItem(int idItem) {
        for (BagesTemplate data : Manager.BAGES_TEMPLATES) {
            if (data.idItem == idItem) {
                return data.idEffect;
            }
        }
        return -1;
    }

    public static BagesTemplate fineBadgesbyIdItem(int idItem) {
        for (BagesTemplate data : Manager.BAGES_TEMPLATES) {
            if (data.idItem == idItem) {
                return data;
            }
        }
        return null;
    }

    public static List<Integer> listEffect(Player player) {
        Set<Integer> setIdItem = new HashSet<>();
        for (BadgesData data : player.dataBadges) {
            for (BagesTemplate temp : Manager.BAGES_TEMPLATES) {
                if (temp.idEffect == data.idBadGes) {
                    setIdItem.add(temp.idItem);
                }
            }
        }
        return new ArrayList<>(setIdItem);
    }

    public static List<Item.ItemOption> sendListItemOption(Player player) {
        List<Item.ItemOption> listOptions = new ArrayList<>();
        for (BadgesData data : player.dataBadges) {
            for (BagesTemplate temp : Manager.BAGES_TEMPLATES) {
                if (data.idBadGes == temp.idEffect && data.isUse) {
                    listOptions.addAll(temp.options);
                }
            }
        }
        return listOptions;
    }

}
