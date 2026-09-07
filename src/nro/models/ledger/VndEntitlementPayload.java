package nro.models.ledger;

import java.util.ArrayList;
import java.util.List;
import nro.models.item.Item;
import nro.models.item.Item.ItemOption;
import nro.models.services.ItemService;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

/**
 * SEC-05: Frozen, complete entitlement specification.
 * Persisted in the durable outbox to allow exact recovery independent of future config changes.
 */
@SuppressWarnings("unchecked")
public final class VndEntitlementPayload {

    public static final class ItemSpec {
        public final short templateId;
        public final int quantity;
        public final List<OptionSpec> options;

        public ItemSpec(short templateId, int quantity, List<OptionSpec> options) {
            this.templateId = templateId;
            this.quantity = quantity;
            this.options = options != null ? options : new ArrayList<>();
        }

        public Item toItem() {
            Item item = ItemService.gI().createNewItem(templateId, quantity);
            if (options != null && !options.isEmpty()) {
                for (OptionSpec opt : options) {
                    item.itemOptions.add(new ItemOption(opt.id, opt.param));
                }
            }
            return item;
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("templateId", (int) templateId);
            json.put("quantity", quantity);
            JSONArray opts = new JSONArray();
            for (OptionSpec o : options) {
                JSONObject oj = new JSONObject();
                oj.put("id", o.id);
                oj.put("param", o.param);
                opts.add(oj);
            }
            json.put("options", opts);
            return json;
        }

        public static ItemSpec fromJson(JSONObject json) {
            short tId = ((Number) json.get("templateId")).shortValue();
            int qty = ((Number) json.get("quantity")).intValue();
            List<OptionSpec> opts = new ArrayList<>();
            JSONArray optArr = (JSONArray) json.get("options");
            if (optArr != null) {
                for (Object o : optArr) {
                    JSONObject oj = (JSONObject) o;
                    opts.add(new OptionSpec(((Number) oj.get("id")).intValue(), ((Number) oj.get("param")).intValue()));
                }
            }
            return new ItemSpec(tId, qty, opts);
        }
    }

    public static final class OptionSpec {
        public final int id;
        public final int param;

        public OptionSpec(int id, int param) {
            this.id = id;
            this.param = param;
        }
    }

    public static final class BadgeProgressSpec {
        public final int badgeTaskId;
        public final int amount;

        public BadgeProgressSpec(int badgeTaskId, int amount) {
            this.badgeTaskId = badgeTaskId;
            this.amount = amount;
        }
    }

    private final List<ItemSpec> items = new ArrayList<>();
    private final List<BadgeProgressSpec> badgeUpdates = new ArrayList<>();
    private long gold = 0;
    private int gem = 0;
    private int eventPoints = 0;

    // VIP attributes
    private boolean vipPurchase = false;
    private int vipLevel = 0;
    private long vipDurationMs = 0;
    private boolean createNormalPetIfMissing = false;

    public VndEntitlementPayload() {}

    public void addItem(short templateId, int quantity) {
        items.add(new ItemSpec(templateId, quantity, new ArrayList<>()));
    }

    public void addItem(short templateId, int quantity, List<OptionSpec> options) {
        items.add(new ItemSpec(templateId, quantity, options));
    }

    public void addBadgeProgress(int badgeTaskId, int amount) {
        badgeUpdates.add(new BadgeProgressSpec(badgeTaskId, amount));
    }

    public List<ItemSpec> getItems() {
        return items;
    }

    public List<BadgeProgressSpec> getBadgeUpdates() {
        return badgeUpdates;
    }

    public long getGold() {
        return gold;
    }

    public void setGold(long gold) {
        this.gold = gold;
    }

    public int getGem() {
        return gem;
    }

    public void setGem(int gem) {
        this.gem = gem;
    }

    public int getEventPoints() {
        return eventPoints;
    }

    public void setEventPoints(int eventPoints) {
        this.eventPoints = eventPoints;
    }

    public boolean isVipPurchase() {
        return vipPurchase;
    }

    public void setVipPurchase(boolean vipPurchase) {
        this.vipPurchase = vipPurchase;
    }

    public int getVipLevel() {
        return vipLevel;
    }

    public void setVipLevel(int vipLevel) {
        this.vipLevel = vipLevel;
    }

    public long getVipDurationMs() {
        return vipDurationMs;
    }

    public void setVipDurationMs(long vipDurationMs) {
        this.vipDurationMs = vipDurationMs;
    }

    public boolean isCreateNormalPetIfMissing() {
        return createNormalPetIfMissing;
    }

    public void setCreateNormalPetIfMissing(boolean createNormalPetIfMissing) {
        this.createNormalPetIfMissing = createNormalPetIfMissing;
    }

    public String toJsonString() {
        JSONObject obj = new JSONObject();
        obj.put("gold", gold);
        obj.put("gem", gem);
        obj.put("eventPoints", eventPoints);
        obj.put("vipPurchase", vipPurchase);
        obj.put("vipLevel", vipLevel);
        obj.put("vipDurationMs", vipDurationMs);
        obj.put("createNormalPetIfMissing", createNormalPetIfMissing);

        JSONArray itemArr = new JSONArray();
        for (ItemSpec is : items) {
            itemArr.add(is.toJson());
        }
        obj.put("items", itemArr);

        JSONArray badgeArr = new JSONArray();
        for (BadgeProgressSpec bs : badgeUpdates) {
            JSONObject bObj = new JSONObject();
            bObj.put("id", bs.badgeTaskId);
            bObj.put("amount", bs.amount);
            badgeArr.add(bObj);
        }
        obj.put("badges", badgeArr);

        return obj.toJSONString();
    }

    public static VndEntitlementPayload fromJsonString(String jsonStr) {
        VndEntitlementPayload payload = new VndEntitlementPayload();
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty VND entitlement");
        }
        JSONObject obj = (JSONObject) JSONValue.parse(jsonStr);
        if (obj == null) {
            throw new IllegalArgumentException("Invalid VND entitlement JSON");
        }

        if (obj.containsKey("gold")) payload.setGold(((Number) obj.get("gold")).longValue());
        if (obj.containsKey("gem")) payload.setGem(((Number) obj.get("gem")).intValue());
        if (obj.containsKey("eventPoints")) payload.setEventPoints(((Number) obj.get("eventPoints")).intValue());
        if (obj.containsKey("vipPurchase")) payload.setVipPurchase((Boolean) obj.get("vipPurchase"));
        if (obj.containsKey("vipLevel")) payload.setVipLevel(((Number) obj.get("vipLevel")).intValue());
        if (obj.containsKey("vipDurationMs")) payload.setVipDurationMs(((Number) obj.get("vipDurationMs")).longValue());
        if (obj.containsKey("createNormalPetIfMissing")) payload.setCreateNormalPetIfMissing((Boolean) obj.get("createNormalPetIfMissing"));

        JSONArray itemArr = (JSONArray) obj.get("items");
        if (itemArr != null) {
            for (Object o : itemArr) {
                payload.items.add(ItemSpec.fromJson((JSONObject) o));
            }
        }

        JSONArray badgeArr = (JSONArray) obj.get("badges");
        if (badgeArr != null) {
            for (Object o : badgeArr) {
                JSONObject bObj = (JSONObject) o;
                payload.addBadgeProgress(((Number) bObj.get("id")).intValue(), ((Number) bObj.get("amount")).intValue());
            }
        }

        if (payload.gold < 0 || payload.gem < 0 || payload.eventPoints < 0
            || itemArr == null || badgeArr == null || payload.vipDurationMs < 0
            || (payload.vipPurchase && (payload.vipLevel < 1 || payload.vipLevel > 4))) {
            throw new IllegalArgumentException("Invalid VND entitlement values");
        }
        for (ItemSpec item : payload.items) {
            if (item.templateId < 0 || item.quantity <= 0) throw new IllegalArgumentException("Invalid entitlement item");
        }
        return payload;
    }
}
