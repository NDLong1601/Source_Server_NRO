package nro.models.shop_ky_gui;

import nro.models.item.Item;
import nro.models.map.service.ItemMapService;

/** Authoritative server-side policy for items accepted by the consignment shop. */
public final class ConsignItemPolicy {

    private ConsignItemPolicy() {
    }

    public static boolean canConsign(Item item) {
        if (item == null || !item.isNotNullItem() || item.template == null || item.itemOptions == null) {
            return false;
        }
        int templateId = item.template.id;
        int type = item.template.type;
        if (item.itemOptions.size() > Byte.MAX_VALUE
                || type == 9 || type == 10 || type == 13 || type == 34
                || templateId == 74 || templateId == 453 || templateId == 517 || templateId == 518
                || ItemMapService.gI().isBlackBall(templateId)
                || ItemMapService.gI().isNamecBall(templateId)
                || ItemMapService.gI().isNamecBallStone(templateId)) {
            return false;
        }
        boolean explicitlyAllowed = false;
        for (Item.ItemOption option : item.itemOptions) {
            if (option == null || option.optionTemplate == null) {
                continue;
            }
            int optionId = option.optionTemplate.id;
            if (optionId == 30 || optionId == 154) {
                return false;
            }
            if (optionId == 86 || optionId == 87) {
                explicitlyAllowed = true;
            }
        }
        return explicitlyAllowed
                || type == 14
                || type == 15
                || type == 6
                || (templateId >= 14 && templateId <= 20);
    }
}
