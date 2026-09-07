package nro.models.shop_ky_gui;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import nro.models.item.Item;
import nro.models.item.Item.ItemOption;
import nro.models.player.InventoryPersistenceSnapshot;
import nro.models.player.Player;
import nro.models.player.PlayerConfig;
import nro.models.services.CostumeCollectionService;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.utils.Logger;

/**
 * SEC-04: Core concurrency coordinator for consignment shop operations.
 * Enforces linearizable, at-most-once state transitions with strict lock order,
 * bag capacity simulation, conditional CAS updates, and purchase ledger recording.
 */
public class ConsignPurchaseCoordinator {

    private static final int STRIPES = 256;
    private static final int MAX_PLAYER_LISTINGS = 100;
    private final ReentrantLock[] listingStripes = new ReentrantLock[STRIPES];
    private final Map<Integer, ConsignItem> listingCache = new ConcurrentHashMap<>();
    private final AtomicBoolean cacheLoaded = new AtomicBoolean(false);
    private final Object cacheLoadLock = new Object();
    private ConsignListingRepository repository;

    private static ConsignPurchaseCoordinator instance;

    public static synchronized ConsignPurchaseCoordinator gI() {
        if (instance == null) {
            instance = new ConsignPurchaseCoordinator(new JdbcConsignListingRepository());
        }
        return instance;
    }

    public static synchronized void setInstanceForTest(ConsignPurchaseCoordinator testInstance) {
        instance = testInstance;
    }

    public ConsignPurchaseCoordinator(ConsignListingRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
        for (int i = 0; i < STRIPES; i++) {
            listingStripes[i] = new ReentrantLock();
        }
    }

    public synchronized void setRepository(ConsignListingRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
        listingCache.clear();
        cacheLoaded.set(false);
    }

    public ConsignListingRepository getRepository() {
        return repository;
    }

    private ReentrantLock getListingLock(int listingId) {
        int hash = Math.floorMod(listingId, STRIPES);
        return listingStripes[hash];
    }

    public void loadCacheFromRepository() {
        listingCache.clear();
        List<ConsignItem> items = repository.loadAllActiveAndSold();
        for (ConsignItem it : items) {
            if (it != null && (it.isActive() || it.isSold())) {
                listingCache.put(it.id, it);
            }
        }
        cacheLoaded.set(true);
    }

    public void populateCacheForTest(List<ConsignItem> items) {
        listingCache.clear();
        for (ConsignItem it : items) {
            if (it != null) {
                listingCache.put(it.id, it);
            }
        }
        cacheLoaded.set(true);
    }

    public void ensureCacheLoaded() {
        if (cacheLoaded.get()) {
            return;
        }
        synchronized (cacheLoadLock) {
            if (cacheLoaded.get()) {
                return;
            }
            if (ConsignShopManager.gI().listItem != null && !ConsignShopManager.gI().listItem.isEmpty()) {
                for (ConsignItem it : ConsignShopManager.gI().listItem) {
                    if (it != null && (it.isActive() || it.isSold())) {
                        listingCache.put(it.id, it);
                    }
                }
            } else if (repository != null) {
                loadCacheFromRepository();
            }
            cacheLoaded.set(true);
        }
    }

    public ConsignItem getListingSnapshot(int listingId) {
        ensureCacheLoaded();
        ConsignItem it = listingCache.get(listingId);
        return it != null ? it.snapshot() : null;
    }

    public List<ConsignItem> getActiveListingsSnapshot() {
        ensureCacheLoaded();
        List<ConsignItem> list = new ArrayList<>();
        for (ConsignItem it : listingCache.values()) {
            if (it != null && it.isActive()) {
                list.add(it.snapshot());
            }
        }
        list.sort(Comparator.comparingInt((ConsignItem it) -> it.isUpTop).reversed()
                .thenComparingInt(it -> it.id));
        return list;
    }

    public List<ConsignItem> getPlayerListingsSnapshot(long playerId) {
        ensureCacheLoaded();
        List<ConsignItem> list = new ArrayList<>();
        for (ConsignItem it : listingCache.values()) {
            if (it != null && it.player_sell == playerId && (it.isActive() || it.isSold())) {
                list.add(it.snapshot());
            }
        }
        list.sort(Comparator.comparingInt((ConsignItem it) -> it.isUpTop).reversed()
                .thenComparingInt(it -> it.id));
        return list;
    }

    // =========================================================================
    // 1. PURCHASE WORKFLOW
    // =========================================================================

    public ConsignPurchaseResult purchase(Player buyer, int listingId, byte clientMoneyType, int clientPrice) {
        if (buyer == null || buyer.inventory == null || !buyer.inventory.isActive()) {
            return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.PLAYER_UNAVAILABLE,
                    "Người chơi không hợp lệ hoặc đang khóa giao dịch");
        }
        if (buyer.nPoint != null && buyer.nPoint.power < 17_000_000_000L) {
            return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.POWER_TOO_LOW,
                    "Yêu cầu sức mạnh lớn hơn 17 tỷ");
        }

        ensureCacheLoaded();
        ReentrantLock listingLock = getListingLock(listingId);
        listingLock.lock();
        try {
            Object buyerLock = buyer.inventory != null ? buyer.inventory : buyer;
            synchronized (buyerLock) {
                ConsignItem liveListing = listingCache.get(listingId);
                if (liveListing == null) {
                    liveListing = repository.findById(listingId);
                    if (liveListing != null && (liveListing.isActive() || liveListing.isSold())) {
                        ConsignItem existing = listingCache.putIfAbsent(listingId, liveListing);
                        if (existing != null) {
                            liveListing = existing;
                        } else {
                            boolean alreadyProjected = ConsignShopManager.gI().listItem.stream()
                                    .anyMatch(item -> item != null && item.id == listingId);
                            if (!alreadyProjected) {
                                ConsignShopManager.gI().listItem.add(liveListing);
                            }
                        }
                    }
                }
                if (liveListing == null) {
                    return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.LISTING_NOT_FOUND,
                            "Vật phẩm không tồn tại");
                }
                if (!liveListing.isActive()) {
                    return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.ALREADY_SOLD,
                            "Vật phẩm không tồn tại hoặc đã được bán");
                }
                if (liveListing.player_sell == buyer.id) {
                    return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.SELF_PURCHASE,
                            "Không thể mua vật phẩm bản thân đăng bán");
                }

                // Authoritative price validation: exactly one currency valid
                boolean isGoldPrice = liveListing.goldSell > 0 && liveListing.gemSell <= 0;
                boolean isGemPrice = liveListing.gemSell > 0 && liveListing.goldSell <= 0;
                if (!isGoldPrice && !isGemPrice) {
                    return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.INVALID_LISTING,
                            "Cấu hình giá niêm yết không hợp lệ");
                }
                if (liveListing.quantity <= 0 || liveListing.quantity > 99) {
                    return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.INVALID_LISTING,
                            "Số lượng niêm yết không hợp lệ");
                }

                // The network quote is untrusted and must match exactly.
                int authoritativePrice = isGoldPrice ? liveListing.goldSell : liveListing.gemSell;
                byte authoritativeType = (byte) (isGoldPrice ? 0 : 1);
                if (clientMoneyType != authoritativeType || clientPrice != authoritativePrice) {
                    return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.PRICE_MISMATCH,
                            "Thông tin giá vật phẩm đã thay đổi, vui lòng làm mới");
                }

                // Balance validation
                long buyerGold = buyer.inventory.gold;
                int buyerGem = buyer.inventory.gem;
                if (buyerGold < 0 || buyerGem < 0) {
                    return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.INVENTORY_CHANGED,
                            "Số dư tài khoản không hợp lệ");
                }

                if (isGoldPrice) {
                    if (buyerGold < liveListing.goldSell) {
                        return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.INSUFFICIENT_GOLD,
                                "Bạn không đủ vàng để mua vật phẩm");
                    }
                } else {
                    if (buyerGem < liveListing.gemSell) {
                        return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.INSUFFICIENT_GEM,
                                "Bạn không đủ Ngọc Xanh để mua vật phẩm này!");
                    }
                }

                // Create deep-copied item to be granted
                Item itemToGrant;
                try {
                    itemToGrant = materializeListingItem(liveListing, true);
                    ItemService.gI().resolveOption231(itemToGrant);
                } catch (RuntimeException invalidItem) {
                    Logger.logException(ConsignPurchaseCoordinator.class, invalidItem);
                    return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.INVALID_LISTING,
                            "Dữ liệu vật phẩm ký gửi không hợp lệ");
                }
                if (!ConsignItemPolicy.canConsign(itemToGrant)) {
                    return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.INVALID_LISTING,
                            "Vật phẩm ký gửi không được phép giao dịch");
                }

                // Build the exact post-commit bag without mutating the live player.
                List<Item> afterBag = InventoryService.gI().copyList(buyer.inventory.itemsBag);
                if (!InventoryService.gI().addItemList(afterBag, ItemService.gI().copyItem(itemToGrant))) {
                    return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.BAG_FULL,
                            "Hành trang không đủ chỗ chứa");
                }

                // Database atomic purchase execution
                byte priceType = authoritativeType;
                long buyerGoldBefore = buyerGold;
                int buyerGemBefore = buyerGem;
                long buyerGoldAfter = isGoldPrice ? buyerGold - liveListing.goldSell : buyerGold;
                int buyerGemAfter = isGemPrice ? buyerGem - liveListing.gemSell : buyerGem;
                InventoryPersistenceSnapshot buyerState = InventoryPersistenceSnapshot.capture(
                        buyer, buyerGoldAfter, buyerGemAfter, afterBag);

                Timestamp soldAt = new Timestamp(System.currentTimeMillis());
                String optionsJson = ConsignItemOptionsCodec.encode(itemToGrant.itemOptions);

                ConsignPersistenceResult persistence = repository.executeAtomicPurchase(
                        liveListing.id, liveListing.getVersion(), buyer.id, soldAt,
                        liveListing.player_sell, priceType, authoritativePrice, liveListing.itemId,
                        liveListing.quantity, optionsJson, buyerGoldBefore, buyerGoldAfter,
                        buyerGemBefore, buyerGemAfter, buyerState);

                if (!persistence.isCommitted()) {
                    return persistenceFailure(buyer, persistence,
                            "Vật phẩm không tồn tại hoặc đã được bán bởi người khác", liveListing.id);
                }

                // Database is authoritative and already contains this exact state.
                ConsignPurchaseResult applyFailure = applyCommittedState(buyer, buyerState);
                if (applyFailure != null) {
                    return applyFailure;
                }
                try {
                    CostumeCollectionService.gI().recordOwnership(buyer, itemToGrant);
                    if (itemToGrant.template != null && itemToGrant.template.id >= 1765
                            && itemToGrant.template.id <= 1771 && itemToGrant.getOptionById(93) == null) {
                        buyer.inventory.checkAndUpdateMeRongBadges(buyer);
                    }
                } catch (RuntimeException sideEffectError) {
                    Logger.logException(ConsignPurchaseCoordinator.class, sideEffectError);
                }

                // Update in-memory projection only after commit
                liveListing.setStatus(ConsignListingStatus.SOLD);
                liveListing.setBuyerId(buyer.id);
                liveListing.setVersion(liveListing.getVersion() + 1);
                liveListing.setSoldAt(soldAt);
                liveListing.isBuy = true;

                return ConsignPurchaseResult.success("Bạn đã nhận được " + itemToGrant.template.name, liveListing.snapshot());
            }
        } finally {
            listingLock.unlock();
        }
    }

    // =========================================================================
    // 2. SELLER CANCEL WORKFLOW
    // =========================================================================

    public ConsignPurchaseResult cancel(Player seller, int listingId) {
        if (seller == null || seller.inventory == null || !seller.inventory.isActive()) {
            return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.PLAYER_UNAVAILABLE,
                    "Người chơi không hợp lệ");
        }

        ReentrantLock listingLock = getListingLock(listingId);
        listingLock.lock();
        try {
            Object sellerLock = seller.inventory != null ? seller.inventory : seller;
            synchronized (sellerLock) {
                ConsignItem liveListing = listingCache.get(listingId);
                if (liveListing == null) {
                    liveListing = repository.findById(listingId);
                }
                if (liveListing == null) {
                    return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.LISTING_NOT_FOUND,
                            "Vật phẩm không tồn tại");
                }
                if (liveListing.isCancelled()) {
                    return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.ALREADY_CANCELLED,
                            "Vật phẩm đã được hủy bán trước đó");
                }
                if (liveListing.isSold() || liveListing.isClaimed()) {
                    return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.ALREADY_SOLD,
                            "Vật phẩm không tồn tại hoặc đã được bán");
                }
                if (!liveListing.isActive()) {
                    return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.CONFLICT,
                            "Trạng thái vật phẩm không thể hủy");
                }
                if (liveListing.player_sell != seller.id) {
                    return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.NOT_OWNER,
                            "Vật phẩm không thuộc quyền sở hữu");
                }

                Item returnItem;
                try {
                    returnItem = materializeListingItem(liveListing, false);
                } catch (RuntimeException invalidItem) {
                    Logger.logException(ConsignPurchaseCoordinator.class, invalidItem);
                    return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.INVALID_LISTING,
                            "Dữ liệu vật phẩm ký gửi không hợp lệ");
                }

                List<Item> afterBag = InventoryService.gI().copyList(seller.inventory.itemsBag);
                if (!InventoryService.gI().addItemList(afterBag, ItemService.gI().copyItem(returnItem))) {
                    return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.BAG_FULL,
                            "Hành trang không đủ chỗ chứa để nhận lại vật phẩm");
                }
                InventoryPersistenceSnapshot sellerState = InventoryPersistenceSnapshot.capture(
                        seller, seller.inventory.gold, seller.inventory.gem, afterBag);

                ConsignPersistenceResult persistence = repository.transitionToCancelled(
                        liveListing.id, liveListing.getVersion(), sellerState);
                if (!persistence.isCommitted()) {
                    return persistenceFailure(seller, persistence,
                            "Hủy bán thất bại do vật phẩm đã thay đổi trạng thái", liveListing.id);
                }

                ConsignPurchaseResult applyFailure = applyCommittedState(seller, sellerState);
                if (applyFailure != null) {
                    return applyFailure;
                }

                liveListing.setStatus(ConsignListingStatus.CANCELLED);
                liveListing.setVersion(liveListing.getVersion() + 1);
                listingCache.remove(liveListing.id);
                ConsignShopManager.gI().listItem.remove(liveListing);

                return ConsignPurchaseResult.success("Hủy bán vật phẩm thành công", liveListing.snapshot());
            }
        } finally {
            listingLock.unlock();
        }
    }

    // =========================================================================
    // 3. SELLER CLAIM WORKFLOW
    // =========================================================================

    public ConsignPurchaseResult claim(Player seller, int listingId) {
        if (seller == null || seller.inventory == null || !seller.inventory.isActive()) {
            return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.PLAYER_UNAVAILABLE,
                    "Người chơi không hợp lệ");
        }

        ReentrantLock listingLock = getListingLock(listingId);
        listingLock.lock();
        try {
            Object sellerLock = seller.inventory != null ? seller.inventory : seller;
            synchronized (sellerLock) {
                ConsignItem liveListing = listingCache.get(listingId);
                if (liveListing == null) {
                    liveListing = repository.findById(listingId);
                }
                if (liveListing == null) {
                    return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.LISTING_NOT_FOUND,
                            "Vật phẩm không tồn tại");
                }
                if (liveListing.isClaimed()) {
                    return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.ALREADY_CLAIMED,
                            "Tiền bán vật phẩm này đã được nhận trước đó");
                }
                if (!liveListing.isSold()) {
                    return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.INVALID_LISTING,
                            "Vật phẩm không tồn tại hoặc chưa được bán");
                }
                if (liveListing.player_sell != seller.id) {
                    return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.NOT_OWNER,
                            "Vật phẩm không thuộc quyền sở hữu");
                }

                boolean isGold = liveListing.goldSell > 0;
                boolean isGem = liveListing.gemSell > 0;
                if (!isGold && !isGem) {
                    return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.INVALID_LISTING,
                            "Dữ liệu giá bán không hợp lệ");
                }

                Item thoiVang = null;
                int gemProceeds = 0;
                List<Item> afterBag = InventoryService.gI().copyList(seller.inventory.itemsBag);
                int gemAfter = seller.inventory.gem;
                if (isGold) {
                    thoiVang = ItemService.gI().createNewItem((short) 457);
                    long netGold = (long) liveListing.goldSell - ((long) liveListing.goldSell * 10L / 100L);
                    if (netGold <= 0 || netGold > Integer.MAX_VALUE) {
                        return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.INVALID_LISTING,
                                "Số lượng thỏi vàng nhận được không hợp lệ");
                    }
                    thoiVang.quantity = (int) netGold;

                    if (!InventoryService.gI().addItemList(afterBag, ItemService.gI().copyItem(thoiVang))) {
                        return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.BAG_FULL,
                                "Hành trang không đủ chỗ chứa để nhận thỏi vàng");
                    }
                } else {
                    long netGem = (long) liveListing.gemSell - ((long) liveListing.gemSell * 10L / 100L);
                    long futureGem = (long) seller.inventory.gem + netGem;
                    if (futureGem > PlayerConfig.getMaxGem()) {
                        return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.INVALID_LISTING,
                                "Số ngọc sau khi nhận vượt quá giới hạn tối đa");
                    }
                    gemProceeds = (int) netGem;
                    gemAfter = seller.inventory.gem + gemProceeds;
                }
                InventoryPersistenceSnapshot sellerState = InventoryPersistenceSnapshot.capture(
                        seller, seller.inventory.gold, gemAfter, afterBag);

                ConsignPersistenceResult persistence = repository.transitionToClaimed(
                        liveListing.id, liveListing.getVersion(), sellerState);
                if (!persistence.isCommitted()) {
                    return persistenceFailure(seller, persistence,
                            "Nhận tiền thất bại do trạng thái đã được xử lý", liveListing.id);
                }

                ConsignPurchaseResult applyFailure = applyCommittedState(seller, sellerState);
                if (applyFailure != null) {
                    return applyFailure;
                }

                liveListing.setStatus(ConsignListingStatus.CLAIMED);
                liveListing.setVersion(liveListing.getVersion() + 1);
                listingCache.remove(liveListing.id);
                ConsignShopManager.gI().listItem.remove(liveListing);

                return ConsignPurchaseResult.success("Bạn đã bán vật phẩm thành công", liveListing.snapshot());
            }
        } finally {
            listingLock.unlock();
        }
    }

    // =========================================================================
    // 4. LISTING CREATION (KiGui) WORKFLOW
    // =========================================================================

    public ConsignPurchaseResult createListing(Player seller, int bagIndex, byte moneyType, int money, int quantity) {
        if (seller == null || seller.inventory == null || !seller.inventory.isActive()) {
            return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.PLAYER_UNAVAILABLE,
                    "Người chơi không hợp lệ");
        }
        if (seller.id <= 0 || seller.id > Integer.MAX_VALUE) {
            return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.PLAYER_UNAVAILABLE,
                    "Mã người chơi không tương thích với kho ký gửi");
        }

        Object sellerLock = seller.inventory != null ? seller.inventory : seller;
        synchronized (sellerLock) {
            if (bagIndex < 0 || bagIndex >= seller.inventory.itemsBag.size()) {
                return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.INVALID_LISTING,
                        "Vị trí hành trang không hợp lệ");
            }

            Item liveItem = seller.inventory.itemsBag.get(bagIndex);
            if (liveItem == null || !liveItem.isNotNullItem() || liveItem.template == null) {
                return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.INVALID_LISTING,
                        "Vật phẩm không hợp lệ");
            }

            if (!ConsignItemPolicy.canConsign(liveItem)) {
                return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.INVALID_LISTING,
                        "Vật phẩm không thể kí gửi");
            }

            // Options 30/154 untradeable restrictions
            for (ItemOption opt : liveItem.itemOptions) {
                if (opt != null && opt.optionTemplate != null) {
                    if (opt.optionTemplate.id == 30 || opt.optionTemplate.id == 154) {
                        return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.INVALID_LISTING,
                                "Vật phẩm không thể kí gửi");
                    }
                }
            }

            // Quantity validation: 1 <= quantity <= 99 and quantity <= liveItem.quantity
            if (quantity <= 0 || quantity > 99 || quantity > liveItem.quantity) {
                return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.INVALID_LISTING,
                        "Ký gửi tối đa x99 và không vượt quá số lượng sở hữu");
            }

            // Money & moneyType validation
            if (money <= 0) {
                return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.INVALID_LISTING,
                        "Giá đăng bán phải lớn hơn 0");
            }

            if (moneyType == 0) {
                if (money > 100_000) {
                    return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.INVALID_LISTING,
                            "Không thể ký gửi quá 100.000 thỏi vàng");
                }
            } else if (moneyType == 1) {
                if (money > 1_000_000) {
                    return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.INVALID_LISTING,
                            "Không thể ký gửi quá 1.000.000 ngọc");
                }
            } else {
                return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.INVALID_LISTING,
                        "Loại tiền tệ không hợp lệ");
            }

            // Option 235: remaining consignment allowance
            if (!hasConsignmentRemaining(liveItem)) {
                return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.INVALID_LISTING,
                        "Vật phẩm đã hết số lần ký gửi");
            }

            if (getPlayerListingsSnapshot(seller.id).size() >= MAX_PLAYER_LISTINGS) {
                return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.INVALID_LISTING,
                        "Bạn đã đạt giới hạn " + MAX_PLAYER_LISTINGS + " vật phẩm đang ký gửi");
            }

            // Currency restriction options 86/87
            boolean consignForGold = liveItem.itemOptions.stream()
                    .anyMatch(option -> option != null && option.optionTemplate != null && option.optionTemplate.id == 86);
            boolean consignForGem = liveItem.itemOptions.stream()
                    .anyMatch(option -> option != null && option.optionTemplate != null && option.optionTemplate.id == 87);
            if (moneyType == 0 && consignForGem && !consignForGold) {
                return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.INVALID_LISTING,
                        "Vật phẩm này chỉ có thể ký gửi bằng ngọc");
            }
            if (moneyType == 1 && consignForGold && !consignForGem) {
                return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.INVALID_LISTING,
                        "Vật phẩm này chỉ có thể ký gửi bằng vàng");
            }

            List<Item> afterBag = InventoryService.gI().copyList(seller.inventory.itemsBag);
            Item stagedSource = afterBag.get(bagIndex);
            InventoryService.gI().subQuantityItem(afterBag, stagedSource, quantity);
            Item stagedFee = findThoiVang(afterBag, 1);
            if (stagedFee == null) {
                return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.INSUFFICIENT_GOLD,
                        "Bạn cần có ít nhất 1 thỏi vàng để làm phí đăng bán");
            }
            InventoryService.gI().subQuantityItem(afterBag, stagedFee, 1);

            // Prepare deep-copied listing item
            Item cloned = ItemService.gI().copyItem(liveItem);
            cloned.quantity = quantity;
            syncQuantityOption(cloned);
            increaseConsignCount(cloned);
            consumeConsignmentRemaining(cloned);

            byte tab = getTabKiGui(cloned);
            int goldSell = (moneyType == 0) ? money : -1;
            int gemSell = (moneyType == 1) ? money : -1;

            ConsignItem newListing = new ConsignItem(0, cloned.template.id, (int) seller.id,
                    tab, goldSell, gemSell, quantity, (byte) 0, cloned.itemOptions, ConsignListingStatus.ACTIVE,
                    0, 1, null);
            InventoryPersistenceSnapshot sellerState = InventoryPersistenceSnapshot.capture(
                    seller, seller.inventory.gold, seller.inventory.gem, afterBag);

            ConsignPersistenceResult persistence = repository.insertListing(newListing, sellerState);
            if (!persistence.isCommitted()) {
                if (persistence.getStatus() == ConsignPersistenceResult.Status.ID_EXHAUSTED) {
                    return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.INVALID_LISTING,
                            "Kho ký gửi đã hết mã giao dịch an toàn; cần nâng cấp giao thức client");
                }
                return persistenceFailure(seller, persistence,
                        "Đăng bán thất bại do lỗi hệ thống lưu trữ", -1);
            }

            newListing.id = persistence.getListingId();
            ConsignPurchaseResult applyFailure = applyCommittedState(seller, sellerState);
            if (applyFailure != null) {
                return applyFailure;
            }

            // Add to cache projection
            listingCache.put(newListing.id, newListing);
            ConsignShopManager.gI().listItem.add(newListing);

            return ConsignPurchaseResult.success("Đăng bán thành công", newListing.snapshot());
        }
    }

    // =========================================================================
    // 5. UP-TOP WORKFLOW
    // =========================================================================

    public ConsignPurchaseResult upTop(Player seller, int listingId) {
        if (seller == null || seller.inventory == null || !seller.inventory.isActive()) {
            return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.PLAYER_UNAVAILABLE,
                    "Người chơi không hợp lệ");
        }

        ReentrantLock listingLock = getListingLock(listingId);
        listingLock.lock();
        try {
            Object sellerLock = seller.inventory != null ? seller.inventory : seller;
            synchronized (sellerLock) {
                ConsignItem liveListing = listingCache.get(listingId);
                if (liveListing == null) {
                    liveListing = repository.findById(listingId);
                }
                if (liveListing == null || !liveListing.isActive()) {
                    return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.LISTING_NOT_FOUND,
                            "Vật phẩm không tồn tại hoặc đã được bán");
                }
                if (liveListing.player_sell != seller.id) {
                    return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.NOT_OWNER,
                            "Vật phẩm không thuộc quyền sở hữu");
                }

                List<Item> afterBag = InventoryService.gI().copyList(seller.inventory.itemsBag);
                Item feeItem = findThoiVang(afterBag, 2);
                if (feeItem == null) {
                    return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.INSUFFICIENT_GOLD,
                            "Bạn cần có ít nhất 2 thỏi vàng đưa vật phẩm lên trang đầu");
                }
                InventoryService.gI().subQuantityItem(afterBag, feeItem, 2);
                InventoryPersistenceSnapshot sellerState = InventoryPersistenceSnapshot.capture(
                        seller, seller.inventory.gold, seller.inventory.gem, afterBag);

                ConsignPersistenceResult persistence = repository.updateUpTop(
                        liveListing.id, liveListing.getVersion(), 1, sellerState);
                if (!persistence.isCommitted()) {
                    return persistenceFailure(seller, persistence,
                            "Cập nhật thất bại, vui lòng thử lại", liveListing.id);
                }

                ConsignPurchaseResult applyFailure = applyCommittedState(seller, sellerState);
                if (applyFailure != null) {
                    return applyFailure;
                }

                liveListing.isUpTop = 1;
                liveListing.setVersion(liveListing.getVersion() + 1);
                ConsignShopManager.gI().listItem.sort(Comparator.comparingInt((ConsignItem it) -> it.isUpTop).reversed());

                return ConsignPurchaseResult.success("Đưa vật phẩm lên trang đầu thành công", liveListing.snapshot());
            }
        } finally {
            listingLock.unlock();
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private Item findThoiVang(List<Item> items, int quantity) {
        if (items == null) {
            return null;
        }
        for (Item item : items) {
            if (item != null && item.isNotNullItem() && item.template != null && item.template.id == 457 && item.quantity >= quantity) {
                return item;
            }
        }
        return null;
    }

    private Item materializeListingItem(ConsignItem listing, boolean addDefaultOption) {
        Item item = ItemService.gI().createNewItem(listing.itemId);
        if (item == null || item.template == null) {
            throw new IllegalArgumentException("Unknown consignment item template " + listing.itemId);
        }
        item.quantity = listing.quantity;
        item.itemOptions.clear();
        if (addDefaultOption && listing.options.isEmpty()) {
            item.itemOptions.add(new ItemOption(73, 0));
        } else {
            item.itemOptions.addAll(ConsignItem.deepCopyOptions(listing.options));
        }
        return item;
    }

    private ConsignPurchaseResult persistenceFailure(Player player, ConsignPersistenceResult persistence,
                                                     String conflictMessage, int listingId) {
        if (persistence.getStatus() == ConsignPersistenceResult.Status.UNKNOWN) {
            return quarantinePlayer(player, "Trạng thái giao dịch chưa xác định; vui lòng đăng nhập lại");
        }
        if (persistence.getStatus() == ConsignPersistenceResult.Status.CONFLICT) {
            if (listingId > 0) {
                reconcileListingProjection(listingId);
            }
            return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.CONFLICT, conflictMessage);
        }
        return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.DATABASE_FAILURE,
                "Giao dịch không thể lưu an toàn, vui lòng thử lại");
    }

    private void reconcileListingProjection(int listingId) {
        ConsignItem fresh = repository.findById(listingId);
        ConsignShopManager.gI().listItem.removeIf(item -> item != null && item.id == listingId);
        if (fresh != null && (fresh.isActive() || fresh.isSold())) {
            listingCache.put(listingId, fresh);
            ConsignShopManager.gI().listItem.add(fresh);
        } else {
            listingCache.remove(listingId);
        }
    }

    private ConsignPurchaseResult applyCommittedState(Player player, InventoryPersistenceSnapshot state) {
        try {
            state.applyTo(player);
            return null;
        } catch (RuntimeException ex) {
            Logger.logException(ConsignPurchaseCoordinator.class, ex);
            return quarantinePlayer(player, "Không thể đồng bộ giao dịch; vui lòng đăng nhập lại");
        }
    }

    private ConsignPurchaseResult quarantinePlayer(Player player, String message) {
        if (player != null) {
            player.persistenceQuarantined = true;
            Logger.error("[SEC-04] Quarantined player after indeterminate persistence, playerId=" + player.id);
        }
        return ConsignPurchaseResult.fail(ConsignPurchaseResult.Outcome.PERSISTENCE_UNKNOWN, message);
    }

    private boolean hasConsignmentRemaining(Item item) {
        ItemOption limit = item.getOptionById(235);
        return limit == null || limit.param > 0;
    }

    private boolean consumeConsignmentRemaining(Item item) {
        ItemOption limit = item.getOptionById(235);
        if (limit == null) {
            return true;
        }
        if (limit.param <= 0) {
            return false;
        }
        limit.param--;
        return true;
    }

    private void increaseConsignCount(Item item) {
        for (ItemOption option : item.itemOptions) {
            if (option != null && option.optionTemplate != null && option.optionTemplate.id == 37) {
                option.param = Math.min(Short.MAX_VALUE, Math.max(0, option.param) + 1);
                return;
            }
        }
        item.itemOptions.add(new ItemOption(37, 1));
    }

    private void syncQuantityOption(Item item) {
        for (ItemOption option : item.itemOptions) {
            if (option != null && option.optionTemplate != null && option.optionTemplate.id == 31) {
                option.param = Math.max(0, item.quantity);
                return;
            }
        }
    }

    public byte getTabKiGui(Item it) {
        if (it == null || it.template == null) {
            return 3;
        }
        if (it.template.type >= 0 && it.template.type <= 2) {
            return 0;
        } else if ((it.template.type >= 3 && it.template.type <= 4)) {
            return 1;
        } else if (it.template.type == 29) {
            return 2;
        } else {
            return 3;
        }
    }
}
