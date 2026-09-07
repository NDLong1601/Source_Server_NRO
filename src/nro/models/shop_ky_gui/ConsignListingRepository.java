package nro.models.shop_ky_gui;

import java.sql.Timestamp;
import java.util.List;
import nro.models.player.InventoryPersistenceSnapshot;

/**
 * SEC-04: Repository abstraction for persistent consignment listings and purchase ledger records.
 * Provides a clean dependency seam so unit and concurrency tests do not require a live MySQL database.
 */
public interface ConsignListingRepository {

    /**
     * Finds a listing by its primary/wire identifier.
     */
    ConsignItem findById(int id);

    /**
     * Loads all listings currently in ACTIVE or SOLD status (not CLAIMED or CANCELLED).
     */
    List<ConsignItem> loadAllActiveAndSold();

    /**
     * Inserts a new listing into persistent storage.
     */
    ConsignPersistenceResult insertListing(ConsignItem item, InventoryPersistenceSnapshot sellerState);

    /**
     * Atomic CAS transition: SOLD -> CLAIMED.
     * Must enforce `status = 'SOLD' AND version = currentVersion`.
     * Commits only if exactly one listing row and the supplied player snapshot
     * are persisted in the same transaction.
     */
    ConsignPersistenceResult transitionToClaimed(int listingId, int currentVersion,
                                                  InventoryPersistenceSnapshot sellerState);

    /**
     * Atomic CAS transition: ACTIVE -> CANCELLED.
     * Must enforce `status = 'ACTIVE' AND version = currentVersion`.
     * Commits only if exactly one listing row and the supplied player snapshot
     * are persisted in the same transaction.
     */
    ConsignPersistenceResult transitionToCancelled(int listingId, int currentVersion,
                                                    InventoryPersistenceSnapshot sellerState);

    /**
     * Updates upTop status for an active listing.
     */
    ConsignPersistenceResult updateUpTop(int listingId, int currentVersion, int isUpTop,
                                         InventoryPersistenceSnapshot sellerState);

    /**
     * Executes the purchase atomically in a single transaction:
     * 1) CAS update: ACTIVE -> SOLD
     * 2) persist the buyer wallet/bag snapshot
     * 3) insert into consign_purchase_ledger
     * Returns committed only when all three operations succeeded and committed.
     */
    ConsignPersistenceResult executeAtomicPurchase(int listingId, int currentVersion, long buyerId,
                                                    Timestamp soldAt, long sellerId, byte priceType,
                                                    int price, short itemId, int quantity,
                                                    String optionsJson, long buyerGoldBefore,
                                                    long buyerGoldAfter, int buyerGemBefore,
                                                    int buyerGemAfter,
                                                    InventoryPersistenceSnapshot buyerState);
}
