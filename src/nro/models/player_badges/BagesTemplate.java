package nro.models.player_badges;

import nro.models.item.Item;
import nro.models.player.Player;
import nro.models.server.Manager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    public static BagesTemplate findBadgesByIdEffect(int idEffect) {
        for (BagesTemplate data : Manager.BAGES_TEMPLATES) {
            if (data.idEffect == idEffect) {
                return data;
            }
        }
        return null;
    }

    /**
     * Older saves stored an item-template ID in {@code dataBadges} for a few
     * titles. Convert it to the effect ID used by the runtime when possible.
     */
    public static int normalizeEffectId(int idEffectOrItemId) {
        if (findBadgesByIdEffect(idEffectOrItemId) != null) {
            return idEffectOrItemId;
        }
        int idEffect = fineIdEffectbyIdItem(idEffectOrItemId);
        return idEffect >= 0 ? idEffect : idEffectOrItemId;
    }

    public static List<Integer> listEffect(Player player) {
        Set<Integer> setIdItem = new HashSet<>();
        BadgesService.normalize(player);
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
        long now = System.currentTimeMillis();
        for (BadgesData data : player.dataBadges) {
            for (BagesTemplate temp : Manager.BAGES_TEMPLATES) {
                if (data.idBadGes == temp.idEffect && data.isUse && data.timeofUseBadges > now) {
                    listOptions.addAll(temp.options);
                }
            }
        }
        return listOptions;
    }

}
