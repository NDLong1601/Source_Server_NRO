package nro.models.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import nro.models.item.Item;
import nro.models.player.Player;
import nro.models.player.PlayerConfig;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.Service;
import nro.models.utils.Util;

/** Applies the optional per-template rewards configured in task.properties. */
public final class TaskRewardService {

    private TaskRewardService() {
    }

    public static boolean grant(Player player, String type, int taskId) {
        int[] itemIds = TaskConfig.getTaskRewardItems(type, taskId);
        Map<Integer, TaskConfig.TaskItemOptions> itemOptionConfigs = TaskConfig.getTaskRewardItemOptions(type, taskId);
        if (itemIds.length > 0 && InventoryService.gI().getCountEmptyBag(player) < itemIds.length) {
            Service.gI().sendThongBao(player, "Hành trang không đủ chỗ trống để nhận phần thưởng nhiệm vụ.");
            return false;
        }

        long potential = TaskConfig.getTaskRewardPotential(type, taskId);
        long gold = TaskConfig.getTaskRewardGold(type, taskId);
        int gem = TaskConfig.getTaskRewardGem(type, taskId);
        List<String> received = new ArrayList<>();
        if (potential > 0) {
            Service.gI().addSMTN(player, (byte) 1, potential, false);
            received.add(Util.numberToMoney(potential) + " tiềm năng");
        }
        if (gold > 0) {
            player.inventory.gold = Math.min(player.inventory.gold + gold, PlayerConfig.getMaxGold());
            received.add(Util.numberToMoney(gold) + " vàng");
        }
        if (gem > 0) {
            player.inventory.gem = Math.min(player.inventory.gem + gem, PlayerConfig.getMaxGem());
            received.add(Util.numberToMoney(gem) + " ngọc");
        }
        for (int itemId : itemIds) {
            TaskConfig.TaskItemOptions config = itemOptionConfigs.get(itemId);
            boolean useDefaults = config == null || config.useDefaultOptions;
            List<Item.ItemOption> extras = config == null ? null : config.options;
            Item item = ItemService.gI().createNewItemWithOptions((short) itemId, 1, useDefaults, extras);
            if (item != null) {
                InventoryService.gI().addItemBag(player, item);
                received.add(item.template.name);
            }
        }
        if (itemIds.length > 0) {
            InventoryService.gI().sendItemBags(player);
        }
        if (gold > 0 || gem > 0) {
            Service.gI().sendMoney(player);
        }
        if (!received.isEmpty()) {
            Service.gI().sendThongBao(player, "Bạn nhận được: " + String.join(", ", received));
        }
        return true;
    }
}
