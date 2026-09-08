package nro.models.shop_ky_gui;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import nro.models.item.Item.ItemOption;

/**
 * SEC-04: Enriched consignment listing model with lifecycle status, optimistic locking
 * version, buyer reference, and defensive option copying.
 */
public class ConsignItem {

    public int id;
    public short itemId;
    public int player_sell;
    public byte tab;
    public int goldSell;
    public int gemSell;
    public int quantity;
    public int isUpTop;
    public List<ItemOption> options = new ArrayList<>();
    public boolean isBuy;

    // SEC-04 additive fields
    private ConsignListingStatus status = ConsignListingStatus.ACTIVE;
    private long buyerId;
    private int version = 1;
    private Timestamp soldAt;

    public ConsignItem() {
    }

    public ConsignItem(int i, short id, int plId, byte t, int gold, int gem, int q, byte isUp, List<ItemOption> op,
            boolean b) {
        this.id = i;
        this.itemId = id;
        this.player_sell = plId;
        this.tab = t;
        this.goldSell = gold;
        this.gemSell = gem;
        this.quantity = q;
        this.isUpTop = isUp;
        this.options = deepCopyOptions(op);
        this.isBuy = b;
        this.status = b ? ConsignListingStatus.SOLD : ConsignListingStatus.ACTIVE;
        this.version = 1;
    }

    public ConsignItem(int i, short id, int plId, byte t, int gold, int gem, int q, byte isUp, List<ItemOption> op,
            ConsignListingStatus status, long buyerId, int version, Timestamp soldAt) {
        this.id = i;
        this.itemId = id;
        this.player_sell = plId;
        this.tab = t;
        this.goldSell = gold;
        this.gemSell = gem;
        this.quantity = q;
        this.isUpTop = isUp;
        this.options = deepCopyOptions(op);
        this.status = status != null ? status : ConsignListingStatus.CANCELLED;
        this.isBuy = (this.status == ConsignListingStatus.SOLD || this.status == ConsignListingStatus.CLAIMED);
        this.buyerId = buyerId;
        this.version = Math.max(1, version);
        this.soldAt = copyTimestamp(soldAt);
    }

    public static List<ItemOption> deepCopyOptions(List<ItemOption> source) {
        List<ItemOption> copy = new ArrayList<>();
        if (source != null) {
            for (ItemOption opt : source) {
                if (opt != null && opt.optionTemplate != null) {
                    copy.add(new ItemOption(opt));
                }
            }
        }
        return copy;
    }

    public ConsignItem snapshot() {
        ConsignItem snap = new ConsignItem(this.id, this.itemId, this.player_sell, this.tab, this.goldSell,
                this.gemSell, this.quantity, (byte) this.isUpTop, this.options, this.status, this.buyerId,
                this.version, this.soldAt);
        return snap;
    }

    public ConsignListingStatus getStatus() {
        return status;
    }

    public void setStatus(ConsignListingStatus status) {
        this.status = status != null ? status : ConsignListingStatus.CANCELLED;
        this.isBuy = (this.status == ConsignListingStatus.SOLD || this.status == ConsignListingStatus.CLAIMED);
    }

    public long getBuyerId() {
        return buyerId;
    }

    public void setBuyerId(long buyerId) {
        this.buyerId = buyerId;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = Math.max(1, version);
    }

    public Timestamp getSoldAt() {
        return copyTimestamp(soldAt);
    }

    public void setSoldAt(Timestamp soldAt) {
        this.soldAt = copyTimestamp(soldAt);
    }

    public boolean isActive() {
        return status == ConsignListingStatus.ACTIVE && !isBuy;
    }

    public boolean isSold() {
        return status == ConsignListingStatus.SOLD;
    }

    public boolean isClaimed() {
        return status == ConsignListingStatus.CLAIMED;
    }

    public boolean isCancelled() {
        return status == ConsignListingStatus.CANCELLED;
    }

    private static Timestamp copyTimestamp(Timestamp value) {
        return value == null ? null : new Timestamp(value.getTime());
    }
}
