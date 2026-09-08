package nro.models.player;

import org.json.simple.JSONArray;

/**
 * Immutable snapshot of player wallet balances.
 */
public final class WalletSnapshot {

    private final long gold;
    private final int gem;
    private final int ruby;
    private final int coupon;

    public WalletSnapshot(long gold, int gem, int ruby, int coupon) {
        this.gold = gold;
        this.gem = gem;
        this.ruby = ruby;
        this.coupon = coupon;
    }

    public static WalletSnapshot fromInventory(Inventory inventory) {
        if (inventory == null) {
            return new WalletSnapshot(0L, 0, 0, 0);
        }
        synchronized (inventory) {
            return new WalletSnapshot(inventory.gold, inventory.gem, inventory.ruby, inventory.coupon);
        }
    }

    public static WalletSnapshot fromParsedJson(JSONArray array) {
        if (array == null || array.size() < 4) {
            throw new IllegalArgumentException("Wallet JSON must contain four currency values");
        }
        long g = parseLong(array.get(0));
        int gm = parseInt(array.get(1));
        int r = parseInt(array.get(2));
        int c = parseInt(array.get(3));
        return new WalletSnapshot(g, gm, r, c);
    }

    private static long parseLong(Object obj) {
        if (obj == null) {
            throw new IllegalArgumentException("Persisted long currency value is null");
        }
        try {
            return Long.parseLong(String.valueOf(obj).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid persisted long currency value", e);
        }
    }

    private static int parseInt(Object obj) {
        if (obj == null) {
            throw new IllegalArgumentException("Persisted integer currency value is null");
        }
        try {
            return Integer.parseInt(String.valueOf(obj).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid persisted integer currency value", e);
        }
    }

    @SuppressWarnings("unchecked")
    public JSONArray toDataInventoryJson(int event) {
        if (gold < 0L || gold > PlayerConfig.getMaxGold()
                || gem < 0 || gem > PlayerConfig.getMaxGem()
                || ruby < 0 || ruby > PlayerConfig.getMaxRuby()
                || coupon < 0 || coupon > PlayerConfig.getMaxCoupon()) {
            throw new IllegalStateException("Cannot serialize an invalid wallet snapshot");
        }
        JSONArray array = new JSONArray();
        array.add(gold);
        array.add(gem);
        array.add(ruby);
        array.add(coupon);
        array.add(event);
        return array;
    }

    public String toDataInventoryJsonString(int event) {
        return toDataInventoryJson(event).toJSONString();
    }

    public long getGold() {
        return gold;
    }

    public int getGem() {
        return gem;
    }

    public int getRuby() {
        return ruby;
    }

    public int getCoupon() {
        return coupon;
    }

    public long getBalance(Currency currency) {
        if (currency == null) {
            return 0L;
        }
        return switch (currency) {
            case GOLD -> gold;
            case GEM -> gem;
            case RUBY -> ruby;
            case COUPON -> coupon;
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WalletSnapshot that = (WalletSnapshot) o;
        return gold == that.gold && gem == that.gem && ruby == that.ruby && coupon == that.coupon;
    }

    @Override
    public int hashCode() {
        int result = Long.hashCode(gold);
        result = 31 * result + gem;
        result = 31 * result + ruby;
        result = 31 * result + coupon;
        return result;
    }

    @Override
    public String toString() {
        return "WalletSnapshot{"
                + "gold=" + gold
                + ", gem=" + gem
                + ", ruby=" + ruby
                + ", coupon=" + coupon
                + '}';
    }
}
