package nro.models.player;

import java.util.ArrayList;
import java.util.List;
import nro.models.item.Item;
import nro.models.services.ItemService;
import org.json.simple.JSONArray;

/**
 * Immutable snapshot of the player wallet and bag fields persisted in the
 * {@code player} table.  The JSON format intentionally matches PlayerDAO.
 */
@SuppressWarnings("unchecked")
public final class InventoryPersistenceSnapshot {

    private final long playerId;
    private final long gold;
    private final int gem;
    private final List<Item> itemsBag;
    private final String dataInventoryJson;
    private final String itemsBagJson;

    private InventoryPersistenceSnapshot(Player player, long gold, int gem, List<Item> itemsBag) {
        if (player == null || player.inventory == null || itemsBag == null) {
            throw new IllegalArgumentException("Player inventory snapshot requires a live player and bag");
        }
        this.playerId = player.id;
        this.gold = gold;
        this.gem = gem;
        this.itemsBag = deepCopyItems(itemsBag);
        this.dataInventoryJson = serializeInventory(player, gold, gem);
        this.itemsBagJson = serializeItemsBag(this.itemsBag);
    }

    public static InventoryPersistenceSnapshot capture(Player player) {
        return capture(player, player.getWallet().getBalance(Currency.GOLD),
                (int) player.getWallet().getBalance(Currency.GEM), player.inventory.itemsBag);
    }

    public static InventoryPersistenceSnapshot capture(Player player, long gold, int gem, List<Item> itemsBag) {
        return new InventoryPersistenceSnapshot(player, gold, gem, itemsBag);
    }

    public long getPlayerId() {
        return playerId;
    }

    public long getGold() {
        return gold;
    }

    public int getGem() {
        return gem;
    }

    public String getDataInventoryJson() {
        return dataInventoryJson;
    }

    public String getItemsBagJson() {
        return itemsBagJson;
    }

    /** Apply only after the database transaction has committed. */
    public void applyTo(Player player) {
        if (player == null || player.inventory == null || player.id != playerId) {
            throw new IllegalArgumentException("Snapshot belongs to a different player");
        }
        List<Item> committedBag = deepCopyItems(itemsBag);
        WalletResult walletRestore = player.getWallet().restoreExact(
                new WalletSnapshot(gold, gem, player.inventory.ruby, player.inventory.coupon),
                WalletMutationContext.of(WalletReason.RECOVERY,
                        "inventory-snapshot:" + playerId + ":" + System.identityHashCode(this),
                        "Áp dụng inventory snapshot đã commit"));
        if (!walletRestore.isSuccess()) {
            throw new IllegalStateException("Committed inventory snapshot contains an invalid wallet");
        }
        player.inventory.itemsBag.clear();
        player.inventory.itemsBag.addAll(committedBag);
    }

    private static String serializeInventory(Player player, long gold, int gem) {
        JSONArray values = new JSONArray();
        values.add(Math.min(gold, PlayerConfig.getMaxGold()));
        values.add(gem);
        values.add(player.inventory.ruby);
        values.add(player.inventory.coupon);
        values.add(player.inventory.event);
        return values.toJSONString();
    }

    private static String serializeItemsBag(List<Item> items) {
        JSONArray bag = new JSONArray();
        for (Item item : items) {
            JSONArray row = new JSONArray();
            JSONArray options = new JSONArray();
            if (item != null && item.isNotNullItem() && item.template != null) {
                row.add(item.template.id);
                row.add(item.quantity);
                if (item.itemOptions != null) {
                    for (Item.ItemOption option : item.itemOptions) {
                        if (option == null || option.optionTemplate == null) {
                            continue;
                        }
                        JSONArray encodedOption = new JSONArray();
                        encodedOption.add(option.optionTemplate.id);
                        encodedOption.add(option.param);
                        options.add(encodedOption.toJSONString());
                    }
                }
            } else {
                row.add(-1);
                row.add(0);
            }
            row.add(options.toJSONString());
            row.add(item != null ? item.createTime : 0L);
            bag.add(row.toJSONString());
        }
        return bag.toJSONString();
    }

    private static List<Item> deepCopyItems(List<Item> source) {
        List<Item> copy = new ArrayList<>(source.size());
        for (Item item : source) {
            copy.add(item == null ? null : ItemService.gI().copyItem(item));
        }
        return copy;
    }
}
