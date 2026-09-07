package nro.models.shop_ky_gui;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SEC-04: Concurrency-safe listing manager replacing legacy whole-table TRUNCATE operations
 * with row-level transaction persistence managed by ConsignPurchaseCoordinator.
 */
public class ConsignShopManager {

    private static ConsignShopManager instance;

    public static synchronized ConsignShopManager gI() {
        if (instance == null) {
            instance = new ConsignShopManager();
        }
        return instance;
    }

    public static synchronized void setInstanceForTest(ConsignShopManager testInstance) {
        instance = testInstance;
    }

    public long lastTimeUpdate;

    public String[] tabName = { "Áo Quần", "Găng Tay", "Phụ Kiện", "Linh tinh", "" };

    /**
     * Concurrent collection preventing ConcurrentModificationException during active browsing.
     */
    public List<ConsignItem> listItem = new CopyOnWriteArrayList<>();

    /**
     * SEC-04: Legacy save() no longer performs TRUNCATE shop_ky_gui.
     * All listing transitions are written row-level in real-time transactions by ConsignPurchaseCoordinator.
     */
    public void save() {
        // Row-level persistence is guaranteed in real-time by ConsignListingRepository.
        // TRUNCATE shop_ky_gui is explicitly removed to prevent table wipeouts.
    }
}
