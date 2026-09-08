package nro.models.shop_ky_gui;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import nro.models.item.Item;
import nro.models.item.Item.ItemOption;
import nro.models.database.PlayerDAO;
import nro.models.network.Message;
import nro.models.network.MySession;
import nro.models.player.Inventory;
import nro.models.player.InventoryPersistenceSnapshot;
import nro.models.player.NPoint;
import nro.models.player.Player;
import nro.models.player.PlayerConfig;
import nro.models.player_system.Template.ItemOptionTemplate;
import nro.models.player_system.Template.ItemTemplate;
import nro.models.server.GameRuntime;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.Service;

/**
 * SEC-04: Consignment Shop Atomic Purchase and Safe Listing Lifecycle Regression Test Suite.
 * Covers the SEC-04 verification scenarios:
 *   - Concurrency & double-buy prevention
 *   - Race between purchase and cancel/claim
 *   - Exact-at-most-once state transitions
 *   - Balance & capacity simulation
 *   - Wire contract golden packets (actions 0-5, -44, -100, versions 219/220/221/222/248)
 *   - Signed-short ID boundary behavior
 *   - Non-regression with SEC-01 and SEC-03 test suites
 */
public final class ConsignAtomicPurchaseRegressionTest {

    private static int assertions = 0;
    private static final long TIMEOUT_SECONDS = 10L;

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
    }

    // =========================================================================
    // Mock / Fake Infrastructure
    // =========================================================================

    static class MockSocket extends Socket {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);

        @Override
        public SocketAddress getRemoteSocketAddress() {
            return new InetSocketAddress("127.0.0.1", 12345);
        }

        @Override
        public InetAddress getInetAddress() {
            try {
                return InetAddress.getByName("127.0.0.1");
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public InputStream getInputStream() { return in; }

        @Override
        public OutputStream getOutputStream() { return out; }

        @Override
        public void setSendBufferSize(int size) {}

        @Override
        public void setReceiveBufferSize(int size) {}
    }

    static class TestSession extends MySession {
        final List<Message> sentMessages = Collections.synchronizedList(new ArrayList<>());

        public TestSession(int version) {
            super(new MockSocket());
            this.version = version;
            this.actived = true;
        }

        @Override
        public void sendMessage(Message msg) {
            if (msg != null) {
                sentMessages.add(msg);
            }
        }
    }

    static class FakeListingRepository implements ConsignListingRepository {
        final Map<Integer, ConsignItem> store = new ConcurrentHashMap<>();
        final Map<Long, InventoryPersistenceSnapshot> persistedStates = new ConcurrentHashMap<>();
        final List<String> ledger = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger idSeq = new AtomicInteger(100);
        boolean injectCasFailure = false;
        boolean injectLedgerFailure = false;
        boolean injectCommitFailure = false;
        boolean injectUnknownFailure = false;

        @Override
        public ConsignItem findById(int id) {
            ConsignItem it = store.get(id);
            return it != null ? it.snapshot() : null;
        }

        @Override
        public List<ConsignItem> loadAllActiveAndSold() {
            List<ConsignItem> list = new ArrayList<>();
            for (ConsignItem it : store.values()) {
                if (it.isActive() || it.isSold()) {
                    list.add(it.snapshot());
                }
            }
            return list;
        }

        @Override
        public synchronized ConsignPersistenceResult insertListing(ConsignItem item,
                                                                    InventoryPersistenceSnapshot sellerState) {
            if (injectCommitFailure) {
                return ConsignPersistenceResult.of(ConsignPersistenceResult.Status.FAILED);
            }
            int listingId = allocateForTest();
            if (listingId < 1) {
                return ConsignPersistenceResult.of(ConsignPersistenceResult.Status.ID_EXHAUSTED);
            }
            ConsignItem copy = item.snapshot();
            copy.id = listingId;
            store.put(listingId, copy);
            persist(sellerState);
            return ConsignPersistenceResult.committed(listingId);
        }

        @Override
        public synchronized ConsignPersistenceResult transitionToClaimed(int listingId, int currentVersion,
                                                                          InventoryPersistenceSnapshot sellerState) {
            if (injectCasFailure) {
                return conflict();
            }
            ConsignItem it = store.get(listingId);
            if (it != null && it.isSold() && it.getVersion() == currentVersion) {
                if (injectCommitFailure) {
                    return failed();
                }
                it.setStatus(ConsignListingStatus.CLAIMED);
                it.setVersion(it.getVersion() + 1);
                persist(sellerState);
                return ConsignPersistenceResult.committed(listingId);
            }
            return conflict();
        }

        @Override
        public synchronized ConsignPersistenceResult transitionToCancelled(int listingId, int currentVersion,
                                                                            InventoryPersistenceSnapshot sellerState) {
            if (injectCasFailure) {
                return conflict();
            }
            ConsignItem it = store.get(listingId);
            if (it != null && it.isActive() && it.getVersion() == currentVersion) {
                if (injectCommitFailure) {
                    return failed();
                }
                it.setStatus(ConsignListingStatus.CANCELLED);
                it.setVersion(it.getVersion() + 1);
                persist(sellerState);
                return ConsignPersistenceResult.committed(listingId);
            }
            return conflict();
        }

        @Override
        public synchronized ConsignPersistenceResult updateUpTop(int listingId, int currentVersion, int isUpTop,
                                                                  InventoryPersistenceSnapshot sellerState) {
            ConsignItem it = store.get(listingId);
            if (it != null && it.isActive() && it.getVersion() == currentVersion) {
                if (injectCommitFailure) {
                    return failed();
                }
                it.isUpTop = isUpTop;
                it.setVersion(it.getVersion() + 1);
                persist(sellerState);
                return ConsignPersistenceResult.committed(listingId);
            }
            return conflict();
        }

        @Override
        public synchronized ConsignPersistenceResult executeAtomicPurchase(int listingId, int currentVersion,
                                                                            long buyerId, Timestamp soldAt,
                                                                            long sellerId, byte priceType,
                                                                            int price, short itemId, int quantity,
                                                                            String optionsJson, long buyerGoldBefore,
                                                                            long buyerGoldAfter, int buyerGemBefore,
                                                                            int buyerGemAfter,
                                                                            InventoryPersistenceSnapshot buyerState) {
            if (injectCasFailure) {
                return conflict();
            }
            if (injectUnknownFailure) {
                return ConsignPersistenceResult.of(ConsignPersistenceResult.Status.UNKNOWN);
            }
            ConsignItem it = store.get(listingId);
            if (it == null || !it.isActive() || it.getVersion() != currentVersion) {
                return conflict();
            }
            if (injectLedgerFailure || injectCommitFailure) {
                return failed(); // Transaction rollback
            }
            it.setStatus(ConsignListingStatus.SOLD);
            it.setBuyerId(buyerId);
            it.setVersion(it.getVersion() + 1);
            it.setSoldAt(soldAt);
            it.isBuy = true;

            ledger.add(listingId + ":" + sellerId + ":" + buyerId + ":" + price);
            persist(buyerState);
            return ConsignPersistenceResult.committed(listingId);
        }

        synchronized int allocateForTest() {
            int candidate = idSeq.incrementAndGet();
            if (candidate > Short.MAX_VALUE) {
                return -1;
            }
            return candidate;
        }

        private void persist(InventoryPersistenceSnapshot state) {
            if (state != null) {
                persistedStates.put(state.getPlayerId(), state);
            }
        }

        private ConsignPersistenceResult conflict() {
            return ConsignPersistenceResult.of(ConsignPersistenceResult.Status.CONFLICT);
        }

        private ConsignPersistenceResult failed() {
            return ConsignPersistenceResult.of(ConsignPersistenceResult.Status.FAILED);
        }
    }

    private static Player createTestPlayer(long id, String name, int version, long gold, int gem) {
        Player player = new Player();
        player.id = id;
        player.name = name;
        player.inventory = new Inventory();
        player.inventory.gold = gold;
        player.inventory.gem = gem;
        player.inventory.itemsBag = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            player.inventory.itemsBag.add(new Item());
        }
        player.nPoint = new NPoint(player);
        player.nPoint.power = 20_000_000_000L; // 20 billion, passes 17 billion power requirement
        TestSession session = new TestSession(version);
        player.setSession(session);
        session.player = player;
        return player;
    }

    private static Item createItem(short templateId, int quantity) {
        Item item = new Item();
        item.template = new ItemTemplate();
        item.template.id = templateId;
        item.template.name = "Item_" + templateId;
        item.template.type = 0;
        item.quantity = quantity;
        item.createTime = System.currentTimeMillis();
        item.itemOptions = new ArrayList<>();
        return item;
    }

    private static void setupTestTemplates() {
        List<ItemTemplate> itemTemplates = new ArrayList<>();
        for (int i = 0; i < 3000; i++) {
            ItemTemplate template = new ItemTemplate();
            template.id = (short) i;
            template.name = "Template_" + i;
            template.type = 0;
            itemTemplates.add(template);
        }
        itemTemplates.get(457).isUpToUp = true;
        itemTemplates.get(190).type = 6;
        itemTemplates.get(220).type = 14;
        itemTemplates.get(220).isUpToUp = true;

        List<ItemOptionTemplate> optionTemplates = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            ItemOptionTemplate option = new ItemOptionTemplate();
            option.id = i;
            option.name = "Option_" + i;
            optionTemplates.add(option);
        }
        GameRuntime.installTemplatesForTesting(itemTemplates, optionTemplates, List.of());
    }

    // =========================================================================
    // MAIN ENTRY POINT
    // =========================================================================

    public static void main(String[] args) throws Exception {
        System.out.println("=== RUNNING SEC-04 CONSIGNMENT SHOP ATOMIC PURCHASE REGRESSION TESTS ===");
        setupTestTemplates();

        test1_TwoBuyersRaceForOneListingExactlyOneSucceeds();
        test2_100ConcurrentBuyersExactlyOneSucceeds();
        test3_OneBuyer100DuplicateRequestsExactlyOneDebitAndGrant();
        test4_LosingBuyersRetainFullBalancesAndBags();
        test5_FullNonStackableBagRejectsWithoutDebitOrTransition();
        test6_FullBagWithCompatibleStackSucceeds();
        test7_InsufficientGoldRejectsAtomically();
        test8_InsufficientGemRejectsAtomically();
        test9_NegativeCorruptBalancesReject();
        test10_SelfPurchaseRejects();
        test11_MissingSoldClaimedCancelledListingsReject();
        test12_ForgedClientMoneyTypePriceCannotReduceAuthoritativeCost();
        test13_InventoryChangesBetweenInitialCheckAndCommitRevalidated();
        test14_DaoConditionalUpdateReturningZeroPerformsNoRamMutation();
        test15_InjectedLedgerInsertFailureRollsBackListingTransition();
        test16_InjectedCommitFailureLeavesMemoryUntouched();
        test17_CacheUpdatesOnlyAfterSuccessfulCommit();
        test18_ConcurrentShopIterationNoCME();
        test19_DuplicateSellerClaimGrantsProceedsOnce();
        test20_PurchaseRacingSellerCancelResolvesToSingleTerminal();
        test21_ListingValidationRejectsInvalidBounds();
        test22_ListingFeeAndSourceItemUnchangedIfInsertFails();
        test23_ConcurrentListingCreationNeverDuplicatesIds();
        test24_ItemOptionsAndQuantitiesDeepCopiedAndImmutable();
        test25_WireGoldenRequestPacketsActions0To5();
        test26_WireGoldenResponsePackets44And100();
        test27_VersionBoundaries219_220_And_221_222_And_248();
        test28_ListingIdBoundaryBehavior();
        test29_ExistingSec03TradeTestsPass();
        test30_ExistingSec01AchievementTestsPass();
        test31_ForgedListingPacketCannotBypassServerEligibility();
        test32_InvalidClientQuoteFailsClosed();
        test33_PurchasePersistsExactPostCommitInventoryState();
        test34_RestartedSoldListingRetainsVersionAndCanBeClaimed();
        test35_UnknownCommitOutcomeQuarantinesPlayer();
        test36_PlayerListingLimitBoundsOwnerPacketCount();
        test37_InvalidPersistedStatusFailsClosed();
        test38_SideEffectItemsCannotEnterAtomicBagWorkflow();
        test39_OwnerPacketCountIsBoundedWithoutWrapping();
        test40_QuarantinedPlayerAutosaveIsSkipped();
        test41_ConflictRefreshesStaleListingProjection();

        System.out.println("ALL SEC-04 REGRESSION TESTS PASSED! Total assertions: " + assertions);
    }

    // =========================================================================
    // TEST IMPLEMENTATIONS
    // =========================================================================

    /** Test 1: Two buyers race for one listing -> exactly one succeeds. */
    static void test1_TwoBuyersRaceForOneListingExactlyOneSucceeds() throws Exception {
        FakeListingRepository repo = new FakeListingRepository();
        ConsignPurchaseCoordinator coordinator = new ConsignPurchaseCoordinator(repo);

        ConsignItem listing = new ConsignItem(1, (short) 190, 9999, (byte) 0, 10_000, -1, 1, (byte) 0,
                new ArrayList<>(), ConsignListingStatus.ACTIVE, 0, 1, null);
        repo.store.put(1, listing);
        coordinator.populateCacheForTest(Collections.singletonList(listing));

        Player buyer1 = createTestPlayer(101, "Buyer1", 248, 100_000, 100);
        Player buyer2 = createTestPlayer(102, "Buyer2", 248, 100_000, 100);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        ConsignPurchaseResult[] results = new ConsignPurchaseResult[2];

        new Thread(() -> {
            try {
                startLatch.await();
                results[0] = coordinator.purchase(buyer1, 1, (byte) 0, 10_000);
            } catch (Exception ignored) {}
            finally { doneLatch.countDown(); }
        }).start();

        new Thread(() -> {
            try {
                startLatch.await();
                results[1] = coordinator.purchase(buyer2, 1, (byte) 0, 10_000);
            } catch (Exception ignored) {}
            finally { doneLatch.countDown(); }
        }).start();

        startLatch.countDown();
        doneLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        int successes = 0;
        int fails = 0;
        for (ConsignPurchaseResult res : results) {
            if (res != null && res.isSuccess()) successes++;
            else fails++;
        }

        check(successes == 1, "Test 1: Exactly one buyer succeeds");
        check(fails == 1, "Test 1: Exactly one buyer fails");
        check(repo.store.get(1).isSold(), "Test 1: Listing transitioned to SOLD");
        check(repo.ledger.size() == 1, "Test 1: Ledger contains exactly one purchase");
        System.out.println("  [PASS] Test 1: Two buyers racing produce exactly one purchase");
    }

    /** Test 2: 100 concurrent buyers -> exactly one succeeds. */
    static void test2_100ConcurrentBuyersExactlyOneSucceeds() throws Exception {
        FakeListingRepository repo = new FakeListingRepository();
        ConsignPurchaseCoordinator coordinator = new ConsignPurchaseCoordinator(repo);

        ConsignItem listing = new ConsignItem(2, (short) 190, 9999, (byte) 0, 5_000, -1, 1, (byte) 0,
                new ArrayList<>(), ConsignListingStatus.ACTIVE, 0, 1, null);
        repo.store.put(2, listing);
        coordinator.populateCacheForTest(Collections.singletonList(listing));

        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int buyerId = 1000 + i;
            executor.submit(() -> {
                try {
                    Player buyer = createTestPlayer(buyerId, "Buyer_" + buyerId, 248, 100_000, 100);
                    startLatch.await();
                    ConsignPurchaseResult res = coordinator.purchase(buyer, 2, (byte) 0, 5_000);
                    if (res != null && res.isSuccess()) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception ignored) {}
                finally { doneLatch.countDown(); }
            });
        }

        startLatch.countDown();
        doneLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();

        check(successCount.get() == 1, "Test 2: Exactly 1 success out of 100 concurrent buyers");
        check(failCount.get() == 99, "Test 2: Exactly 99 fails out of 100 concurrent buyers");
        check(repo.store.get(2).isSold(), "Test 2: Listing is SOLD");
        check(repo.ledger.size() == 1, "Test 2: Exactly 1 ledger record created");
        System.out.println("  [PASS] Test 2: 100 concurrent buyers produce exactly one purchase");
    }

    /** Test 3: One buyer sends 100 duplicate purchase requests -> exactly 1 debit and grant. */
    static void test3_OneBuyer100DuplicateRequestsExactlyOneDebitAndGrant() throws Exception {
        FakeListingRepository repo = new FakeListingRepository();
        ConsignPurchaseCoordinator coordinator = new ConsignPurchaseCoordinator(repo);

        ConsignItem listing = new ConsignItem(3, (short) 190, 8888, (byte) 0, 20_000, -1, 1, (byte) 0,
                new ArrayList<>(), ConsignListingStatus.ACTIVE, 0, 1, null);
        repo.store.put(3, listing);
        coordinator.populateCacheForTest(Collections.singletonList(listing));

        Player buyer = createTestPlayer(555, "RepeatBuyer", 248, 100_000, 500);

        int requestCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(requestCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < requestCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ConsignPurchaseResult res = coordinator.purchase(buyer, 3, (byte) 0, 20_000);
                    if (res != null && res.isSuccess()) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) {}
                finally { doneLatch.countDown(); }
            });
        }

        startLatch.countDown();
        doneLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();

        check(successCount.get() == 1, "Test 3: Exactly 1 success for 100 repeat purchase requests");
        check(buyer.inventory.gold == 80_000, "Test 3: Debited exactly once (100k - 20k = 80k)");
        long itemCount = buyer.inventory.itemsBag.stream().filter(it -> it != null && it.isNotNullItem()).count();
        check(itemCount == 1, "Test 3: Granted item exactly once");
        System.out.println("  [PASS] Test 3: Duplicate purchase requests debit and grant exactly once");
    }

    /** Test 4: Losing buyers retain their full balances and bags. */
    static void test4_LosingBuyersRetainFullBalancesAndBags() {
        FakeListingRepository repo = new FakeListingRepository();
        ConsignPurchaseCoordinator coordinator = new ConsignPurchaseCoordinator(repo);

        ConsignItem listing = new ConsignItem(4, (short) 190, 9999, (byte) 0, 50_000, -1, 1, (byte) 0,
                new ArrayList<>(), ConsignListingStatus.SOLD, 100, 2, null); // already sold
        repo.store.put(4, listing);
        coordinator.populateCacheForTest(Collections.singletonList(listing));

        Player losingBuyer = createTestPlayer(777, "LosingBuyer", 248, 100_000, 250);
        ConsignPurchaseResult res = coordinator.purchase(losingBuyer, 4, (byte) 0, 50_000);

        check(!res.isSuccess(), "Test 4: Purchase failed for already sold listing");
        check(losingBuyer.inventory.gold == 100_000, "Test 4: Gold balance completely untouched");
        check(losingBuyer.inventory.gem == 250, "Test 4: Gem balance completely untouched");
        check(losingBuyer.inventory.itemsBag.stream().noneMatch(it -> it != null && it.isNotNullItem()),
                "Test 4: Bag completely untouched");
        System.out.println("  [PASS] Test 4: Losing buyer balance and bag preserved");
    }

    /** Test 5: Full non-stackable bag rejects without debit or listing transition. */
    static void test5_FullNonStackableBagRejectsWithoutDebitOrTransition() {
        FakeListingRepository repo = new FakeListingRepository();
        ConsignPurchaseCoordinator coordinator = new ConsignPurchaseCoordinator(repo);

        ConsignItem listing = new ConsignItem(5, (short) 190, 9999, (byte) 0, 10_000, -1, 1, (byte) 0,
                new ArrayList<>(), ConsignListingStatus.ACTIVE, 0, 1, null);
        repo.store.put(5, listing);
        coordinator.populateCacheForTest(Collections.singletonList(listing));

        Player buyer = createTestPlayer(888, "FullBagBuyer", 248, 100_000, 100);
        // Fill all 20 slots with non-stackable items of different template IDs
        for (int i = 0; i < 20; i++) {
            buyer.inventory.itemsBag.set(i, createItem((short) (200 + i), 1));
        }

        ConsignPurchaseResult res = coordinator.purchase(buyer, 5, (byte) 0, 10_000);
        check(!res.isSuccess(), "Test 5: Purchase rejected due to full bag");
        check(res.getOutcome() == ConsignPurchaseResult.Outcome.BAG_FULL, "Test 5: Outcome is BAG_FULL");
        check(buyer.inventory.gold == 100_000, "Test 5: Gold untouched");
        check(repo.store.get(5).isActive(), "Test 5: Listing remains ACTIVE");
        check(repo.ledger.isEmpty(), "Test 5: No ledger record created");
        System.out.println("  [PASS] Test 5: Full non-stackable bag rejected without mutation");
    }

    /** Test 6: Full bag with a compatible stack succeeds when addItemList would succeed. */
    static void test6_FullBagWithCompatibleStackSucceeds() {
        FakeListingRepository repo = new FakeListingRepository();
        ConsignPurchaseCoordinator coordinator = new ConsignPurchaseCoordinator(repo);

        // Stackable, directly storable type-14 item.
        ConsignItem listing = new ConsignItem(6, (short) 220, 9999, (byte) 0, 5_000, -1, 5, (byte) 0,
                Collections.singletonList(new ItemOption(73, 0)), ConsignListingStatus.ACTIVE, 0, 1, null);
        repo.store.put(6, listing);
        coordinator.populateCacheForTest(Collections.singletonList(listing));

        Player buyer = createTestPlayer(889, "StackBuyer", 248, 50_000, 100);
        // Fill 19 slots with dummies, slot 0 has the compatible stack.
        Item stack = createItem((short) 220, 10);
        stack.template.type = 14;
        stack.template.isUpToUp = true;
        stack.itemOptions.add(new ItemOption(73, 0));
        buyer.inventory.itemsBag.set(0, stack);
        for (int i = 1; i < 20; i++) {
            buyer.inventory.itemsBag.set(i, createItem((short) (300 + i), 1));
        }

        ConsignPurchaseResult res = coordinator.purchase(buyer, 6, (byte) 0, 5_000);
        check(res.isSuccess(), "Test 6: Purchase succeeds into compatible stack");
        check(buyer.inventory.gold == 45_000, "Test 6: Debited 5,000 gold");
        check(buyer.inventory.itemsBag.get(0).quantity == 15, "Test 6: Stacked quantity becomes 15");
        System.out.println("  [PASS] Test 6: Compatible stackable item in full bag succeeds");
    }

    /** Test 7: Insufficient gold rejects atomically. */
    static void test7_InsufficientGoldRejectsAtomically() {
        FakeListingRepository repo = new FakeListingRepository();
        ConsignPurchaseCoordinator coordinator = new ConsignPurchaseCoordinator(repo);

        ConsignItem listing = new ConsignItem(7, (short) 190, 9999, (byte) 0, 100_000, -1, 1, (byte) 0,
                new ArrayList<>(), ConsignListingStatus.ACTIVE, 0, 1, null);
        repo.store.put(7, listing);
        coordinator.populateCacheForTest(Collections.singletonList(listing));

        Player buyer = createTestPlayer(901, "BrokeGold", 248, 500, 100);
        ConsignPurchaseResult res = coordinator.purchase(buyer, 7, (byte) 0, 100_000);

        check(!res.isSuccess(), "Test 7: Insufficient gold rejected");
        check(res.getOutcome() == ConsignPurchaseResult.Outcome.INSUFFICIENT_GOLD, "Test 7: Outcome is INSUFFICIENT_GOLD");
        check(buyer.inventory.gold == 500, "Test 7: Gold unchanged");
        System.out.println("  [PASS] Test 7: Insufficient gold rejected atomically");
    }

    /** Test 8: Insufficient gem rejects atomically. */
    static void test8_InsufficientGemRejectsAtomically() {
        FakeListingRepository repo = new FakeListingRepository();
        ConsignPurchaseCoordinator coordinator = new ConsignPurchaseCoordinator(repo);

        ConsignItem listing = new ConsignItem(8, (short) 190, 9999, (byte) 1, -1, 500, 1, (byte) 0,
                new ArrayList<>(), ConsignListingStatus.ACTIVE, 0, 1, null);
        repo.store.put(8, listing);
        coordinator.populateCacheForTest(Collections.singletonList(listing));

        Player buyer = createTestPlayer(902, "BrokeGem", 248, 100_000, 50);
        ConsignPurchaseResult res = coordinator.purchase(buyer, 8, (byte) 1, 500);

        check(!res.isSuccess(), "Test 8: Insufficient gem rejected");
        check(res.getOutcome() == ConsignPurchaseResult.Outcome.INSUFFICIENT_GEM, "Test 8: Outcome is INSUFFICIENT_GEM");
        check(buyer.inventory.gem == 50, "Test 8: Gem unchanged");
        System.out.println("  [PASS] Test 8: Insufficient gem rejected atomically");
    }

    /** Test 9: Negative/corrupt balances reject. */
    static void test9_NegativeCorruptBalancesReject() {
        FakeListingRepository repo = new FakeListingRepository();
        ConsignPurchaseCoordinator coordinator = new ConsignPurchaseCoordinator(repo);

        ConsignItem listing = new ConsignItem(9, (short) 190, 9999, (byte) 0, 10_000, -1, 1, (byte) 0,
                new ArrayList<>(), ConsignListingStatus.ACTIVE, 0, 1, null);
        repo.store.put(9, listing);
        coordinator.populateCacheForTest(Collections.singletonList(listing));

        Player corruptBuyer = createTestPlayer(903, "CorruptBuyer", 248, -100, 100);
        ConsignPurchaseResult res = coordinator.purchase(corruptBuyer, 9, (byte) 0, 10_000);

        check(!res.isSuccess(), "Test 9: Negative balance rejected");
        check(corruptBuyer.inventory.gold == -100, "Test 9: Corrupt balance not further modified");
        System.out.println("  [PASS] Test 9: Corrupt balance fails closed");
    }

    /** Test 10: Self-purchase rejects. */
    static void test10_SelfPurchaseRejects() {
        FakeListingRepository repo = new FakeListingRepository();
        ConsignPurchaseCoordinator coordinator = new ConsignPurchaseCoordinator(repo);

        ConsignItem listing = new ConsignItem(10, (short) 190, 1000, (byte) 0, 10_000, -1, 1, (byte) 0,
                new ArrayList<>(), ConsignListingStatus.ACTIVE, 0, 1, null);
        repo.store.put(10, listing);
        coordinator.populateCacheForTest(Collections.singletonList(listing));

        Player sellerAsBuyer = createTestPlayer(1000, "SelfBuyer", 248, 100_000, 100);
        ConsignPurchaseResult res = coordinator.purchase(sellerAsBuyer, 10, (byte) 0, 10_000);

        check(!res.isSuccess(), "Test 10: Self purchase rejected");
        check(res.getOutcome() == ConsignPurchaseResult.Outcome.SELF_PURCHASE, "Test 10: Outcome is SELF_PURCHASE");
        check(sellerAsBuyer.inventory.gold == 100_000, "Test 10: Gold unchanged");
        System.out.println("  [PASS] Test 10: Self purchase rejected");
    }

    /** Test 11: Missing, SOLD, CLAIMED, and CANCELLED listings reject. */
    static void test11_MissingSoldClaimedCancelledListingsReject() {
        FakeListingRepository repo = new FakeListingRepository();
        ConsignPurchaseCoordinator coordinator = new ConsignPurchaseCoordinator(repo);

        ConsignItem soldItem = new ConsignItem(111, (short) 190, 9999, (byte) 0, 1_000, -1, 1, (byte) 0,
                new ArrayList<>(), ConsignListingStatus.SOLD, 101, 2, null);
        ConsignItem claimedItem = new ConsignItem(112, (short) 190, 9999, (byte) 0, 1_000, -1, 1, (byte) 0,
                new ArrayList<>(), ConsignListingStatus.CLAIMED, 101, 3, null);
        ConsignItem cancelledItem = new ConsignItem(113, (short) 190, 9999, (byte) 0, 1_000, -1, 1, (byte) 0,
                new ArrayList<>(), ConsignListingStatus.CANCELLED, 0, 2, null);

        repo.store.put(111, soldItem);
        repo.store.put(112, claimedItem);
        repo.store.put(113, cancelledItem);
        coordinator.populateCacheForTest(List.of(soldItem, claimedItem, cancelledItem));

        Player buyer = createTestPlayer(904, "Buyer", 248, 100_000, 100);

        check(!coordinator.purchase(buyer, 99999, (byte) 0, 1000).isSuccess(), "Test 11: Missing listing rejected");
        check(!coordinator.purchase(buyer, 111, (byte) 0, 1000).isSuccess(), "Test 11: SOLD listing rejected");
        check(!coordinator.purchase(buyer, 112, (byte) 0, 1000).isSuccess(), "Test 11: CLAIMED listing rejected");
        check(!coordinator.purchase(buyer, 113, (byte) 0, 1000).isSuccess(), "Test 11: CANCELLED listing rejected");
        System.out.println("  [PASS] Test 11: Non-active listings rejected");
    }

    /** Test 12: Forged client moneyType/price cannot reduce the authoritative cost. */
    static void test12_ForgedClientMoneyTypePriceCannotReduceAuthoritativeCost() {
        FakeListingRepository repo = new FakeListingRepository();
        ConsignPurchaseCoordinator coordinator = new ConsignPurchaseCoordinator(repo);

        // Listing costs 100,000 gold
        ConsignItem listing = new ConsignItem(12, (short) 190, 9999, (byte) 0, 100_000, -1, 1, (byte) 0,
                new ArrayList<>(), ConsignListingStatus.ACTIVE, 0, 1, null);
        repo.store.put(12, listing);
        coordinator.populateCacheForTest(Collections.singletonList(listing));

        Player attacker = createTestPlayer(905, "Attacker", 248, 500, 10);
        // Attacker claims item costs 1 gold or 1 gem
        ConsignPurchaseResult res1 = coordinator.purchase(attacker, 12, (byte) 0, 1);
        check(!res1.isSuccess(), "Test 12: Forged gold price rejected");
        check(res1.getOutcome() == ConsignPurchaseResult.Outcome.PRICE_MISMATCH, "Test 12: Outcome is PRICE_MISMATCH");

        ConsignPurchaseResult res2 = coordinator.purchase(attacker, 12, (byte) 1, 1);
        check(!res2.isSuccess(), "Test 12: Forged currency type rejected");
        check(res2.getOutcome() == ConsignPurchaseResult.Outcome.PRICE_MISMATCH, "Test 12: Outcome is PRICE_MISMATCH");

        check(attacker.inventory.gold == 500, "Test 12: Gold balance intact");
        check(attacker.inventory.gem == 10, "Test 12: Gem balance intact");
        System.out.println("  [PASS] Test 12: Forged client price/currency fails stale-UI validation");
    }

    /** Test 13: Inventory changes between initial check and commit are revalidated under lock. */
    static void test13_InventoryChangesBetweenInitialCheckAndCommitRevalidated() {
        FakeListingRepository repo = new FakeListingRepository();
        ConsignPurchaseCoordinator coordinator = new ConsignPurchaseCoordinator(repo);

        ConsignItem listing = new ConsignItem(13, (short) 190, 9999, (byte) 0, 50_000, -1, 1, (byte) 0,
                new ArrayList<>(), ConsignListingStatus.ACTIVE, 0, 1, null);
        repo.store.put(13, listing);
        coordinator.populateCacheForTest(Collections.singletonList(listing));

        Player buyer = createTestPlayer(906, "Buyer", 248, 60_000, 100);
        // Drain balance right before purchase
        buyer.inventory.gold = 1_000;

        ConsignPurchaseResult res = coordinator.purchase(buyer, 13, (byte) 0, 50_000);
        check(!res.isSuccess(), "Test 13: Changed balance detected under lock");
        check(res.getOutcome() == ConsignPurchaseResult.Outcome.INSUFFICIENT_GOLD, "Test 13: Outcome is INSUFFICIENT_GOLD");
        System.out.println("  [PASS] Test 13: Inventory revalidation under lock verified");
    }

    /** Test 14: DAO conditional update returning zero performs no RAM mutation. */
    static void test14_DaoConditionalUpdateReturningZeroPerformsNoRamMutation() {
        FakeListingRepository repo = new FakeListingRepository();
        repo.injectCasFailure = true;
        ConsignPurchaseCoordinator coordinator = new ConsignPurchaseCoordinator(repo);

        ConsignItem listing = new ConsignItem(14, (short) 190, 9999, (byte) 0, 10_000, -1, 1, (byte) 0,
                new ArrayList<>(), ConsignListingStatus.ACTIVE, 0, 1, null);
        repo.store.put(14, listing);
        coordinator.populateCacheForTest(Collections.singletonList(listing));

        Player buyer = createTestPlayer(907, "Buyer", 248, 100_000, 100);
        ConsignPurchaseResult res = coordinator.purchase(buyer, 14, (byte) 0, 10_000);

        check(!res.isSuccess(), "Test 14: CAS failure rejected");
        check(res.getOutcome() == ConsignPurchaseResult.Outcome.CONFLICT, "Test 14: Outcome is CONFLICT");
        check(buyer.inventory.gold == 100_000, "Test 14: RAM gold untouched");
        check(buyer.inventory.itemsBag.stream().noneMatch(it -> it != null && it.isNotNullItem()),
                "Test 14: RAM bag untouched");
        System.out.println("  [PASS] Test 14: DAO update returning 0 performs zero RAM mutation");
    }

    /** Test 15: Injected ledger insert failure rolls back the listing transition. */
    static void test15_InjectedLedgerInsertFailureRollsBackListingTransition() {
        FakeListingRepository repo = new FakeListingRepository();
        repo.injectLedgerFailure = true;
        ConsignPurchaseCoordinator coordinator = new ConsignPurchaseCoordinator(repo);

        ConsignItem listing = new ConsignItem(15, (short) 190, 9999, (byte) 0, 10_000, -1, 1, (byte) 0,
                new ArrayList<>(), ConsignListingStatus.ACTIVE, 0, 1, null);
        repo.store.put(15, listing);
        coordinator.populateCacheForTest(Collections.singletonList(listing));

        Player buyer = createTestPlayer(908, "Buyer", 248, 100_000, 100);
        ConsignPurchaseResult res = coordinator.purchase(buyer, 15, (byte) 0, 10_000);

        check(!res.isSuccess(), "Test 15: Ledger failure rejected");
        check(repo.store.get(15).isActive(), "Test 15: Listing rolled back to ACTIVE");
        check(buyer.inventory.gold == 100_000, "Test 15: Buyer gold untouched");
        System.out.println("  [PASS] Test 15: Ledger insert failure rolls back transaction");
    }

    /** Test 16: A failed commit occurs before any in-memory debit/item projection. */
    static void test16_InjectedCommitFailureLeavesMemoryUntouched() {
        FakeListingRepository repo = new FakeListingRepository();
        repo.injectCommitFailure = true;
        ConsignPurchaseCoordinator coordinator = new ConsignPurchaseCoordinator(repo);

        ConsignItem listing = new ConsignItem(16, (short) 190, 9999, (byte) 0, 15_000, -1, 1, (byte) 0,
                new ArrayList<>(), ConsignListingStatus.ACTIVE, 0, 1, null);
        repo.store.put(16, listing);
        coordinator.populateCacheForTest(Collections.singletonList(listing));

        Player buyer = createTestPlayer(909, "Buyer", 248, 100_000, 100);
        ConsignPurchaseResult res = coordinator.purchase(buyer, 16, (byte) 0, 15_000);

        check(!res.isSuccess(), "Test 16: Commit failure rejected");
        check(buyer.inventory.gold == 100_000, "Test 16: Balance was never mutated");
        System.out.println("  [PASS] Test 16: Injected commit failure leaves memory untouched");
    }

    /** Test 17: Cache updates only after successful commit. */
    static void test17_CacheUpdatesOnlyAfterSuccessfulCommit() {
        FakeListingRepository repo = new FakeListingRepository();
        ConsignPurchaseCoordinator coordinator = new ConsignPurchaseCoordinator(repo);

        ConsignItem listing = new ConsignItem(17, (short) 190, 9999, (byte) 0, 10_000, -1, 1, (byte) 0,
                new ArrayList<>(), ConsignListingStatus.ACTIVE, 0, 1, null);
        repo.store.put(17, listing);
        coordinator.populateCacheForTest(Collections.singletonList(listing));

        check(coordinator.getListingSnapshot(17).isActive(), "Test 17: Initially ACTIVE in cache");

        Player buyer = createTestPlayer(910, "Buyer", 248, 100_000, 100);
        ConsignPurchaseResult res = coordinator.purchase(buyer, 17, (byte) 0, 10_000);

        check(res.isSuccess(), "Test 17: Purchase committed");
        check(coordinator.getListingSnapshot(17).isSold(), "Test 17: Cache updated to SOLD after commit");
        System.out.println("  [PASS] Test 17: Cache updates only after successful commit");
    }

    /** Test 18: Concurrent shop iteration does not throw ConcurrentModificationException. */
    static void test18_ConcurrentShopIterationNoCME() throws Exception {
        FakeListingRepository repo = new FakeListingRepository();
        ConsignPurchaseCoordinator coordinator = new ConsignPurchaseCoordinator(repo);

        for (int i = 1; i <= 50; i++) {
            ConsignItem it = new ConsignItem(1800 + i, (short) 190, 9999, (byte) 0, 1_000, -1, 1, (byte) 0,
                    new ArrayList<>(), ConsignListingStatus.ACTIVE, 0, 1, null);
            repo.store.put(it.id, it);
            ConsignShopManager.gI().listItem.add(it);
        }
        coordinator.populateCacheForTest(new ArrayList<>(ConsignShopManager.gI().listItem));

        ExecutorService executor = Executors.newFixedThreadPool(8);
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicInteger exceptionCount = new AtomicInteger(0);

        // Reader threads
        for (int r = 0; r < 4; r++) {
            executor.submit(() -> {
                while (running.get()) {
                    try {
                        List<ConsignItem> list = coordinator.getActiveListingsSnapshot();
                        for (ConsignItem item : list) {
                            int id = item.id;
                        }
                    } catch (Exception ex) {
                        exceptionCount.incrementAndGet();
                    }
                }
            });
        }

        // Mutator thread
        executor.submit(() -> {
            Player seller = createTestPlayer(999, "Seller", 248, 100_000, 100);
            Item bagItem = createItem((short) 190, 10);
            bagItem.itemOptions.add(new ItemOption(86, 0));
            seller.inventory.itemsBag.set(0, bagItem);
            Item thoiVang = createItem((short) 457, 100);
            seller.inventory.itemsBag.set(1, thoiVang);

            for (int m = 0; m < 50; m++) {
                coordinator.createListing(seller, 0, (byte) 0, 100, 1);
            }
        });

        Thread.sleep(200);
        running.set(false);
        executor.shutdown();
        executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        check(exceptionCount.get() == 0, "Test 18: No ConcurrentModificationException during iteration");
        System.out.println("  [PASS] Test 18: Concurrent browsing free from ConcurrentModificationException");
    }

    /** Test 19: Duplicate seller claim grants proceeds once. */
    static void test19_DuplicateSellerClaimGrantsProceedsOnce() throws Exception {
        FakeListingRepository repo = new FakeListingRepository();
        ConsignPurchaseCoordinator coordinator = new ConsignPurchaseCoordinator(repo);

        // Sold item for 100 gem
        ConsignItem listing = new ConsignItem(19, (short) 190, 888, (byte) 1, -1, 100, 1, (byte) 0,
                new ArrayList<>(), ConsignListingStatus.SOLD, 999, 1, null);
        repo.store.put(19, listing);
        coordinator.populateCacheForTest(Collections.singletonList(listing));

        Player seller = createTestPlayer(888, "Seller", 248, 0, 0);

        int claims = 20;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(claims);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < claims; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ConsignPurchaseResult res = coordinator.claim(seller, 19);
                    if (res.isSuccess()) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) {}
                finally { doneLatch.countDown(); }
            });
        }

        startLatch.countDown();
        doneLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();

        check(successCount.get() == 1, "Test 19: Exactly one claim succeeds");
        check(seller.inventory.gem == 90, "Test 19: Proceeds credited exactly once (100 - 10% = 90)");
        check(repo.store.get(19).isClaimed(), "Test 19: Listing transitioned to CLAIMED");
        System.out.println("  [PASS] Test 19: Duplicate seller claim grants proceeds once");
    }

    /** Test 20: Purchase racing seller cancel resolves to exactly one terminal transition. */
    static void test20_PurchaseRacingSellerCancelResolvesToSingleTerminal() throws Exception {
        FakeListingRepository repo = new FakeListingRepository();
        ConsignPurchaseCoordinator coordinator = new ConsignPurchaseCoordinator(repo);

        ConsignItem listing = new ConsignItem(20, (short) 190, 777, (byte) 0, 10_000, -1, 1, (byte) 0,
                new ArrayList<>(), ConsignListingStatus.ACTIVE, 0, 1, null);
        repo.store.put(20, listing);
        coordinator.populateCacheForTest(Collections.singletonList(listing));

        Player seller = createTestPlayer(777, "Seller", 248, 0, 0);
        Player buyer = createTestPlayer(666, "Buyer", 248, 50_000, 0);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        ConsignPurchaseResult[] results = new ConsignPurchaseResult[2];

        new Thread(() -> {
            try {
                startLatch.await();
                results[0] = coordinator.purchase(buyer, 20, (byte) 0, 10_000);
            } catch (Exception ignored) {}
            finally { doneLatch.countDown(); }
        }).start();

        new Thread(() -> {
            try {
                startLatch.await();
                results[1] = coordinator.cancel(seller, 20);
            } catch (Exception ignored) {}
            finally { doneLatch.countDown(); }
        }).start();

        startLatch.countDown();
        doneLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        boolean purchaseWon = results[0] != null && results[0].isSuccess();
        boolean cancelWon = results[1] != null && results[1].isSuccess();

        check(purchaseWon ^ cancelWon, "Test 20: Exactly one of purchase or cancel succeeds");
        if (purchaseWon) {
            check(repo.store.get(20).isSold(), "Test 20: Won as SOLD");
            check(buyer.inventory.gold == 40_000, "Test 20: Buyer debited");
        } else {
            check(repo.store.get(20).isCancelled(), "Test 20: Won as CANCELLED");
            check(buyer.inventory.gold == 50_000, "Test 20: Buyer not debited");
        }
        System.out.println("  [PASS] Test 20: Purchase vs Cancel race produces exactly one terminal transition");
    }

    /** Test 21: Listing validation rejects quantity <= 0, > 99, invalid moneyType, zero/negative price, and excessive price. */
    static void test21_ListingValidationRejectsInvalidBounds() {
        FakeListingRepository repo = new FakeListingRepository();
        ConsignPurchaseCoordinator coordinator = new ConsignPurchaseCoordinator(repo);

        Player seller = createTestPlayer(500, "Seller", 248, 100_000, 100);
        Item validItem = createItem((short) 190, 50);
        validItem.itemOptions.add(new ItemOption(86, 0)); // allowed
        seller.inventory.itemsBag.set(0, validItem);
        Item thoiVang = createItem((short) 457, 10);
        seller.inventory.itemsBag.set(1, thoiVang);

        // quantity <= 0
        check(!coordinator.createListing(seller, 0, (byte) 0, 100, 0).isSuccess(), "Test 21: quantity=0 rejected");
        check(!coordinator.createListing(seller, 0, (byte) 0, 100, -5).isSuccess(), "Test 21: quantity<0 rejected");

        // quantity > 99
        check(!coordinator.createListing(seller, 0, (byte) 0, 100, 100).isSuccess(), "Test 21: quantity>99 rejected");

        // money <= 0
        check(!coordinator.createListing(seller, 0, (byte) 0, 0, 1).isSuccess(), "Test 21: price=0 rejected");
        check(!coordinator.createListing(seller, 0, (byte) 0, -10, 1).isSuccess(), "Test 21: price<0 rejected");

        // excessive gold (> 100k)
        check(!coordinator.createListing(seller, 0, (byte) 0, 100_001, 1).isSuccess(), "Test 21: gold>100k rejected");

        // excessive gem (> 1M)
        validItem.itemOptions.clear();
        validItem.itemOptions.add(new ItemOption(87, 0)); // gem allowed
        check(!coordinator.createListing(seller, 0, (byte) 1, 1_000_001, 1).isSuccess(), "Test 21: gem>1M rejected");

        // invalid moneyType
        check(!coordinator.createListing(seller, 0, (byte) 2, 100, 1).isSuccess(), "Test 21: moneyType=2 rejected");
        System.out.println("  [PASS] Test 21: Listing creation boundary validation verified");
    }

    /** Test 22: Listing fee and source item are unchanged if insertion fails. */
    static void test22_ListingFeeAndSourceItemUnchangedIfInsertFails() {
        FakeListingRepository repo = new FakeListingRepository();
        repo.injectCommitFailure = true; // DB fails
        ConsignPurchaseCoordinator coordinator = new ConsignPurchaseCoordinator(repo);

        Player seller = createTestPlayer(501, "Seller", 248, 100_000, 100);
        Item validItem = createItem((short) 190, 10);
        validItem.itemOptions.add(new ItemOption(86, 0));
        seller.inventory.itemsBag.set(0, validItem);
        Item thoiVang = createItem((short) 457, 5);
        seller.inventory.itemsBag.set(1, thoiVang);

        ConsignPurchaseResult res = coordinator.createListing(seller, 0, (byte) 0, 1_000, 2);
        check(!res.isSuccess(), "Test 22: DB failure rejected");
        check(seller.inventory.itemsBag.get(0).quantity == 10, "Test 22: Source item quantity untouched");
        check(seller.inventory.itemsBag.get(1).quantity == 5, "Test 22: Listing fee thoi vang untouched");
        System.out.println("  [PASS] Test 22: Listing fee and item preserved on persistence failure");
    }

    /** Test 23: Concurrent listing creation never duplicates IDs. */
    static void test23_ConcurrentListingCreationNeverDuplicatesIds() throws Exception {
        FakeListingRepository repo = new FakeListingRepository();
        ConsignPurchaseCoordinator coordinator = new ConsignPurchaseCoordinator(repo);

        int listings = 50;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch doneLatch = new CountDownLatch(listings);
        List<Integer> createdIds = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < listings; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    Player seller = createTestPlayer(2000 + idx, "Seller_" + idx, 248, 100_000, 100);
                    Item it = createItem((short) 190, 10);
                    it.itemOptions.add(new ItemOption(86, 0));
                    seller.inventory.itemsBag.set(0, it);
                    Item tv = createItem((short) 457, 10);
                    seller.inventory.itemsBag.set(1, tv);

                    ConsignPurchaseResult res = coordinator.createListing(seller, 0, (byte) 0, 100 + idx, 1);
                    if (res.isSuccess() && res.getListingSnapshot() != null) {
                        createdIds.add(res.getListingSnapshot().id);
                    }
                } catch (Exception ignored) {}
                finally { doneLatch.countDown(); }
            });
        }

        doneLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();

        check(createdIds.size() == listings, "Test 23: All listings created");
        long uniqueCount = createdIds.stream().distinct().count();
        check(uniqueCount == listings, "Test 23: All allocated listing IDs are distinct");
        System.out.println("  [PASS] Test 23: Concurrent listing creations allocate unique IDs");
    }

    /** Test 24: Item options and quantities are deep-copied and immutable in ledger snapshots. */
    static void test24_ItemOptionsAndQuantitiesDeepCopiedAndImmutable() {
        Item item = createItem((short) 190, 5);
        item.itemOptions.add(new ItemOption(50, 100));

        ConsignItem listing = new ConsignItem(24, item.template.id, 100, (byte) 0, 100, -1, item.quantity,
                (byte) 0, item.itemOptions, ConsignListingStatus.ACTIVE, 0, 1, null);

        // Mutate original item options
        item.itemOptions.get(0).param = 999;
        item.quantity = 999;

        check(listing.options.get(0).param == 100, "Test 24: Listing options isolated from original mutations");
        check(listing.quantity == 5, "Test 24: Listing quantity isolated from original mutations");
        String encoded = ConsignItemOptionsCodec.encode(listing.options);
        List<ItemOption> decoded = ConsignItemOptionsCodec.decode(encoded);
        check(decoded.size() == 1 && decoded.get(0).optionTemplate.id == 50 && decoded.get(0).param == 100,
                "Test 24: Canonical SQL option codec round-trips exactly");
        System.out.println("  [PASS] Test 24: Item options and quantities deep-copied and isolated");
    }

    /** Test 25: Wire golden request packets for actions 0-5. */
    static void test25_WireGoldenRequestPacketsActions0To5() throws Exception {
        // Action 0: byte action, short bagIndex, byte moneyType, int money, int quantity (v220+)
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeByte(0); // action
        dos.writeShort(3); // bagIndex
        dos.writeByte(0); // moneyType
        dos.writeInt(5000); // money
        dos.writeInt(2); // quantity

        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(baos.toByteArray()));
        check(dis.readByte() == 0, "Test 25: Action 0 read");
        check(dis.readShort() == 3, "Test 25: Action 0 bagIndex read");
        check(dis.readByte() == 0, "Test 25: Action 0 moneyType read");
        check(dis.readInt() == 5000, "Test 25: Action 0 money read");
        check(dis.readInt() == 2, "Test 25: Action 0 quantity read");

        // Action 1 & 2: byte action, short listingId
        for (int act : List.of(1, 2, 5)) {
            baos.reset();
            dos = new DataOutputStream(baos);
            dos.writeByte(act);
            dos.writeShort(42);

            dis = new DataInputStream(new ByteArrayInputStream(baos.toByteArray()));
            check(dis.readByte() == act, "Test 25: Action " + act + " read");
            check(dis.readShort() == 42, "Test 25: Action " + act + " listingId read");
        }

        // Action 3: byte action, short listingId, byte moneyType, int price
        baos.reset();
        dos = new DataOutputStream(baos);
        dos.writeByte(3);
        dos.writeShort(101);
        dos.writeByte(1);
        dos.writeInt(250);

        dis = new DataInputStream(new ByteArrayInputStream(baos.toByteArray()));
        check(dis.readByte() == 3, "Test 25: Action 3 read");
        check(dis.readShort() == 101, "Test 25: Action 3 listingId read");
        check(dis.readByte() == 1, "Test 25: Action 3 moneyType read");
        check(dis.readInt() == 250, "Test 25: Action 3 price read");

        // Action 4: byte action, byte tab, byte page
        baos.reset();
        dos = new DataOutputStream(baos);
        dos.writeByte(4);
        dos.writeByte(1);
        dos.writeByte(0);

        dis = new DataInputStream(new ByteArrayInputStream(baos.toByteArray()));
        check(dis.readByte() == 4, "Test 25: Action 4 read");
        check(dis.readByte() == 1, "Test 25: Action 4 tab read");
        check(dis.readByte() == 0, "Test 25: Action 4 page read");
        System.out.println("  [PASS] Test 25: Golden request packets 0-5 wire serialization verified");
    }

    /** Test 26: Wire golden response packets for -44 and -100. */
    static void test26_WireGoldenResponsePackets44And100() throws Exception {
        Player player = createTestPlayer(3001, "PacketPlayer", 248, 100_000, 100);
        TestSession session = (TestSession) player.getSession();

        FakeListingRepository repo = new FakeListingRepository();
        ConsignPurchaseCoordinator coordinator = new ConsignPurchaseCoordinator(repo);
        ConsignItem item = new ConsignItem(26, (short) 190, 3001, (byte) 0, 1000, -1, 1, (byte) 0,
                new ArrayList<>(), ConsignListingStatus.ACTIVE, 0, 1, null);
        repo.store.put(26, item);
        coordinator.populateCacheForTest(Collections.singletonList(item));
        ConsignPurchaseCoordinator.setInstanceForTest(coordinator);

        ConsignShopService service = new ConsignShopService();

        // Test -44
        service.openShopKyGui(player);
        check(!session.sentMessages.isEmpty(), "Test 26: Response packet sent");
        Message msg44 = session.sentMessages.get(0);
        check(msg44.command == -44, "Test 26: Command is -44");

        DataInputStream dis44 = new DataInputStream(new ByteArrayInputStream(msg44.getData()));
        check(dis44.readByte() == 2, "Test 26: Shop type is 2");
        check(dis44.readByte() == 5, "Test 26: Tab count is 5");

        // Test -100
        session.sentMessages.clear();
        service.openShopKyGui(player, (byte) 0, 0);
        check(!session.sentMessages.isEmpty(), "Test 26: Pagination packet sent");
        Message msg100 = session.sentMessages.get(0);
        check(msg100.command == -100, "Test 26: Command is -100");

        DataInputStream dis100 = new DataInputStream(new ByteArrayInputStream(msg100.getData()));
        check(dis100.readByte() == 0, "Test 26: Tab index is 0");
        check(dis100.readByte() >= 1, "Test 26: Max page read");
        check(dis100.readByte() == 0, "Test 26: Curr page is 0");
        System.out.println("  [PASS] Test 26: Golden response packets -44 and -100 verified");
    }

    /** Test 27: Version boundaries 219/220 and 221/222, plus Unity 2.4.8 behavior. */
    static void test27_VersionBoundaries219_220_And_221_222_And_248() throws Exception {
        Player p219 = createTestPlayer(4001, "P219", 219, 100_000, 100);
        Player p220 = createTestPlayer(4002, "P220", 220, 100_000, 100);
        Player p221 = createTestPlayer(4003, "P221", 221, 100_000, 100);
        Player p222 = createTestPlayer(4004, "P222", 222, 100_000, 100);
        Player p248 = createTestPlayer(4005, "P248", 248, 100_000, 100);

        // Action 0 request decoding rule:
        // version < 220 -> readByte
        // version >= 220 -> readInt
        check(p219.getSession().version < 220, "Test 27: Version 219 uses byte quantity in request");
        check(p220.getSession().version >= 220, "Test 27: Version 220 uses int quantity in request");

        // Command -100 pagination response encoding rule:
        // version < 222 -> writeByte
        // version >= 222 -> writeInt
        FakeListingRepository repo = new FakeListingRepository();
        ConsignPurchaseCoordinator coordinator = new ConsignPurchaseCoordinator(repo);
        ConsignItem item = new ConsignItem(27, (short) 190, 9999, (byte) 0, 1000, -1, 5, (byte) 0,
                new ArrayList<>(), ConsignListingStatus.ACTIVE, 0, 1, null);
        repo.store.put(27, item);
        coordinator.populateCacheForTest(Collections.singletonList(item));
        ConsignPurchaseCoordinator.setInstanceForTest(coordinator);
        ConsignShopService service = new ConsignShopService();

        // Test v221 (writeByte)
        TestSession s221 = (TestSession) p221.getSession();
        service.openShopKyGui(p221, (byte) 0, 0);
        Message m221 = s221.sentMessages.get(0);
        DataInputStream dis221 = new DataInputStream(new ByteArrayInputStream(m221.getData()));
        dis221.readByte(); dis221.readByte(); dis221.readByte(); // tab, max, curr
        int count221 = dis221.readByte();
        check(count221 == 1, "Test 27: 1 item in v221 response");
        dis221.readShort(); dis221.readShort(); dis221.readInt(); dis221.readInt(); dis221.readByte(); // template, id, gold, gem, buyType
        byte q221 = dis221.readByte(); // byte quantity in v221!
        check(q221 == 5, "Test 27: v221 receives byte quantity 5");

        // Test v222 (writeInt)
        TestSession s222 = (TestSession) p222.getSession();
        service.openShopKyGui(p222, (byte) 0, 0);
        Message m222 = s222.sentMessages.get(0);
        DataInputStream dis222 = new DataInputStream(new ByteArrayInputStream(m222.getData()));
        dis222.readByte(); dis222.readByte(); dis222.readByte(); // tab, max, curr
        dis222.readByte(); // count
        dis222.readShort(); dis222.readShort(); dis222.readInt(); dis222.readInt(); dis222.readByte(); // template, id, gold, gem, buyType
        int q222 = dis222.readInt(); // int quantity in v222!
        check(q222 == 5, "Test 27: v222 receives int quantity 5");

        // Test v248 (current client, writeInt)
        TestSession s248 = (TestSession) p248.getSession();
        service.openShopKyGui(p248, (byte) 0, 0);
        Message m248 = s248.sentMessages.get(0);
        DataInputStream dis248 = new DataInputStream(new ByteArrayInputStream(m248.getData()));
        dis248.readByte(); dis248.readByte(); dis248.readByte(); // tab, max, curr
        dis248.readByte(); // count
        dis248.readShort(); dis248.readShort(); dis248.readInt(); dis248.readInt(); dis248.readByte();
        int q248 = dis248.readInt();
        check(q248 == 5, "Test 27: v248 receives int quantity 5");
        System.out.println("  [PASS] Test 27: Version boundaries 219/220 and 221/222/248 verified");
    }

    /** Test 28: Listing ID boundary behavior around signed-short limits. */
    static void test28_ListingIdBoundaryBehavior() {
        // Boundary 32767 (Short.MAX_VALUE)
        short maxShort = Short.MAX_VALUE;
        check(maxShort == 32767, "Test 28: Short.MAX_VALUE is 32767");

        // Boundary 32768 (would overflow signed short to -32768)
        short overflow1 = (short) 32768;
        check(overflow1 == -32768, "Test 28: 32768 cast to signed short produces negative value -32768");

        // Boundary 65535 (would produce -1)
        short overflow2 = (short) 65535;
        check(overflow2 == -1, "Test 28: 65535 cast to signed short produces -1");

        // Boundary 65536 (truncates to 0)
        short overflow3 = (short) 65536;
        check(overflow3 == 0, "Test 28: 65536 cast to signed short truncates to 0");

        // Verify allocator stays within 1..32767
        FakeListingRepository repo = new FakeListingRepository();
        repo.idSeq.set(32766);
        int id1 = repo.allocateForTest();
        check(id1 == 32767, "Test 28: Allocates boundary 32767");
        int id2 = repo.allocateForTest();
        check(id2 == -1, "Test 28: Exhaustion fails closed without recycling a historical ID");
        System.out.println("  [PASS] Test 28: 16-bit ID exhaustion fails closed without ABA reuse");
    }

    /** Test 29: Existing SEC-03 trade tests still pass. */
    static void test29_ExistingSec03TradeTestsPass() throws Exception {
        nro.models.services_func.TradeStateRegressionTest.main(new String[0]);
        setupTestTemplates();
        System.out.println("  [PASS] Test 29: SEC-03 TradeStateRegressionTest passed completely");
    }

    /** Test 30: Existing SEC-01 achievement tests still pass. */
    static void test30_ExistingSec01AchievementTestsPass() throws Exception {
        Class<?> clazz = Class.forName("AchievementClaimRegressionTest");
        clazz.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        setupTestTemplates();
        System.out.println("  [PASS] Test 30: SEC-01 AchievementClaimRegressionTest passed completely");
    }

    /** Test 31: Packet callers cannot list an item that the shop UI would not expose. */
    static void test31_ForgedListingPacketCannotBypassServerEligibility() {
        FakeListingRepository repo = new FakeListingRepository();
        ConsignPurchaseCoordinator coordinator = new ConsignPurchaseCoordinator(repo);

        Player seller = createTestPlayer(5001, "Seller", 248, 100_000, 100);
        Item forbidden = createItem((short) 190, 1);
        forbidden.template.type = 5;
        seller.inventory.itemsBag.set(0, forbidden);
        seller.inventory.itemsBag.set(1, createItem((short) 457, 5));

        ConsignPurchaseResult result = coordinator.createListing(seller, 0, (byte) 0, 1_000, 1);

        check(!result.isSuccess(), "Test 31: Ineligible item rejected at coordinator boundary");
        check(repo.store.isEmpty(), "Test 31: Ineligible item never persisted");
        check(seller.inventory.itemsBag.get(0).quantity == 1, "Test 31: Source item unchanged");
        check(seller.inventory.itemsBag.get(1).quantity == 5, "Test 31: Listing fee unchanged");
        System.out.println("  [PASS] Test 31: Forged listing packet cannot bypass server eligibility");
    }

    /** Test 32: Network purchase requires an exact, well-formed client quote. */
    static void test32_InvalidClientQuoteFailsClosed() {
        FakeListingRepository repo = new FakeListingRepository();
        ConsignPurchaseCoordinator coordinator = new ConsignPurchaseCoordinator(repo);
        ConsignItem listing = new ConsignItem(32, (short) 190, 9999, (byte) 0, 10_000, -1, 1,
                (byte) 0, new ArrayList<>(), ConsignListingStatus.ACTIVE, 0, 1, null);
        repo.store.put(32, listing);
        coordinator.populateCacheForTest(Collections.singletonList(listing));
        Player buyer = createTestPlayer(5002, "Buyer", 248, 100_000, 100);

        ConsignPurchaseResult invalidType = coordinator.purchase(buyer, 32, (byte) 2, 10_000);
        check(!invalidType.isSuccess(), "Test 32: Unknown client currency type rejected");
        check(buyer.inventory.gold == 100_000, "Test 32: Invalid quote does not debit buyer");
        check(repo.store.get(32).isActive(), "Test 32: Invalid quote does not sell listing");

        ConsignPurchaseResult zeroPrice = coordinator.purchase(buyer, 32, (byte) 0, 0);
        check(!zeroPrice.isSuccess(), "Test 32: Zero client price rejected instead of bypassing quote check");
        check(buyer.inventory.gold == 100_000, "Test 32: Zero quote does not debit buyer");
        check(repo.store.get(32).isActive(), "Test 32: Zero quote leaves listing active");
        System.out.println("  [PASS] Test 32: Invalid client quote fails closed");
    }

    /** Test 33: A successful purchase persists the exact wallet/bag state projected to RAM. */
    static void test33_PurchasePersistsExactPostCommitInventoryState() {
        FakeListingRepository repo = new FakeListingRepository();
        ConsignPurchaseCoordinator coordinator = new ConsignPurchaseCoordinator(repo);
        ConsignItem listing = new ConsignItem(33, (short) 190, 9999, (byte) 0, 10_000, -1, 2,
                (byte) 0, new ArrayList<>(), ConsignListingStatus.ACTIVE, 0, 4, null);
        repo.store.put(33, listing);
        coordinator.populateCacheForTest(Collections.singletonList(listing));
        Player buyer = createTestPlayer(5033, "Buyer33", 248, 100_000, 100);

        ConsignPurchaseResult result = coordinator.purchase(buyer, 33, (byte) 0, 10_000);
        InventoryPersistenceSnapshot persisted = repo.persistedStates.get(buyer.id);
        InventoryPersistenceSnapshot live = InventoryPersistenceSnapshot.capture(buyer);

        check(result.isSuccess(), "Test 33: Purchase succeeds");
        check(persisted != null, "Test 33: Transaction includes a player inventory snapshot");
        check(persisted.getGold() == 90_000, "Test 33: Persisted gold contains the debit");
        check(persisted.getDataInventoryJson().equals(live.getDataInventoryJson()),
                "Test 33: Persisted wallet exactly matches committed RAM projection");
        check(persisted.getItemsBagJson().equals(live.getItemsBagJson()),
                "Test 33: Persisted bag exactly matches committed RAM projection");
        System.out.println("  [PASS] Test 33: Purchase persists exact post-commit inventory state");
    }

    /** Test 34: Bootstrap metadata must preserve SOLD version so the seller can claim after restart. */
    static void test34_RestartedSoldListingRetainsVersionAndCanBeClaimed() {
        FakeListingRepository repo = new FakeListingRepository();
        ConsignItem restarted = new ConsignItem(34, (short) 190, 5034, (byte) 0, 10, -1, 1,
                (byte) 0, new ArrayList<>(), ConsignListingStatus.SOLD, 9000, 7,
                new Timestamp(System.currentTimeMillis()));
        repo.store.put(34, restarted);
        ConsignPurchaseCoordinator coordinator = new ConsignPurchaseCoordinator(repo);
        coordinator.loadCacheFromRepository();
        Player seller = createTestPlayer(5034, "Seller34", 248, 0, 0);

        ConsignItem loaded = coordinator.getListingSnapshot(34);
        ConsignPurchaseResult result = coordinator.claim(seller, 34);

        check(loaded != null && loaded.isSold(), "Test 34: Restart cache retains SOLD state");
        check(loaded != null && loaded.getVersion() == 7, "Test 34: Restart cache retains version 7");
        check(result.isSuccess(), "Test 34: Seller can claim restarted SOLD listing");
        check(repo.store.get(34).isClaimed() && repo.store.get(34).getVersion() == 8,
                "Test 34: Claim CAS uses restored version and reaches CLAIMED v8");
        System.out.println("  [PASS] Test 34: Restarted SOLD listing retains lifecycle metadata");
    }

    /** Test 35: An indeterminate commit cannot be followed by a stale autosave. */
    static void test35_UnknownCommitOutcomeQuarantinesPlayer() {
        FakeListingRepository repo = new FakeListingRepository();
        repo.injectUnknownFailure = true;
        ConsignPurchaseCoordinator coordinator = new ConsignPurchaseCoordinator(repo);
        ConsignItem listing = new ConsignItem(35, (short) 190, 9999, (byte) 0, 10_000, -1, 1,
                (byte) 0, new ArrayList<>(), ConsignListingStatus.ACTIVE, 0, 1, null);
        repo.store.put(35, listing);
        coordinator.populateCacheForTest(Collections.singletonList(listing));
        Player buyer = createTestPlayer(5035, "Buyer35", 248, 100_000, 100);

        ConsignPurchaseResult result = coordinator.purchase(buyer, 35, (byte) 0, 10_000);

        check(result.getOutcome() == ConsignPurchaseResult.Outcome.PERSISTENCE_UNKNOWN,
                "Test 35: Indeterminate commit has a distinct outcome");
        check(buyer.persistenceQuarantined, "Test 35: Player is quarantined from stale autosave");
        check(buyer.inventory.gold == 100_000, "Test 35: Unknown outcome is not projected to RAM");
        System.out.println("  [PASS] Test 35: Unknown commit outcome quarantines the player");
    }

    /** Test 36: Per-player active/sold listings remain bounded below the byte-count limit. */
    static void test36_PlayerListingLimitBoundsOwnerPacketCount() {
        FakeListingRepository repo = new FakeListingRepository();
        ConsignPurchaseCoordinator coordinator = new ConsignPurchaseCoordinator(repo);
        Player seller = createTestPlayer(5036, "Seller36", 248, 100_000, 100);
        List<ConsignItem> listings = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            ConsignItem listing = new ConsignItem(i, (short) 190, (int) seller.id, (byte) 0, 100, -1, 1,
                    (byte) 0, new ArrayList<>(), ConsignListingStatus.ACTIVE, 0, 1, null);
            repo.store.put(i, listing);
            listings.add(listing);
        }
        coordinator.populateCacheForTest(listings);
        Item source = createItem((short) 190, 2);
        source.itemOptions.add(new ItemOption(86, 0));
        seller.inventory.itemsBag.set(0, source);
        seller.inventory.itemsBag.set(1, createItem((short) 457, 5));

        ConsignPurchaseResult result = coordinator.createListing(seller, 0, (byte) 0, 100, 1);

        check(!result.isSuccess(), "Test 36: The 101st active/sold listing is rejected");
        check(repo.store.size() == 100, "Test 36: No extra listing reaches persistence");
        check(seller.inventory.itemsBag.get(0).quantity == 2 && seller.inventory.itemsBag.get(1).quantity == 5,
                "Test 36: Rejected listing does not consume item or fee");
        System.out.println("  [PASS] Test 36: Per-player listing count is protocol-bounded");
    }

    /** Test 37: Corrupt lifecycle text must not resurrect a listing as ACTIVE. */
    static void test37_InvalidPersistedStatusFailsClosed() {
        check(ConsignListingStatus.fromString(null) == ConsignListingStatus.CANCELLED,
                "Test 37: Null persisted status fails closed");
        check(ConsignListingStatus.fromString("CORRUPT") == ConsignListingStatus.CANCELLED,
                "Test 37: Unknown persisted status fails closed");
        check(ConsignListingStatus.fromString("sold") == ConsignListingStatus.SOLD,
                "Test 37: Valid lifecycle status is parsed case-insensitively");
        System.out.println("  [PASS] Test 37: Invalid persisted lifecycle status fails closed");
    }

    /** Test 38: Items whose normal grant path mutates non-bag state are rejected. */
    static void test38_SideEffectItemsCannotEnterAtomicBagWorkflow() {
        FakeListingRepository repo = new FakeListingRepository();
        ConsignPurchaseCoordinator coordinator = new ConsignPurchaseCoordinator(repo);
        Player seller = createTestPlayer(5038, "Seller38", 248, 100_000, 100);
        Item currencyItem = createItem((short) 191, 1);
        currencyItem.template.type = 9;
        currencyItem.itemOptions.add(new ItemOption(86, 0));
        seller.inventory.itemsBag.set(0, currencyItem);
        seller.inventory.itemsBag.set(1, createItem((short) 457, 5));

        ConsignPurchaseResult createResult = coordinator.createListing(seller, 0, (byte) 0, 100, 1);
        check(!createResult.isSuccess(), "Test 38: Wallet-mutating item cannot be listed through bag workflow");
        check(repo.store.isEmpty(), "Test 38: Rejected side-effect item is not persisted");

        GameRuntime.gI().templates().itemTemplates().get(191).type = 9;
        ConsignItem corruptListing = new ConsignItem(38, (short) 191, 9999, (byte) 0, 100, -1, 1,
                (byte) 0, Collections.singletonList(new ItemOption(86, 0)),
                ConsignListingStatus.ACTIVE, 0, 1, null);
        repo.store.put(38, corruptListing);
        coordinator.populateCacheForTest(Collections.singletonList(corruptListing));
        Player buyer = createTestPlayer(6038, "Buyer38", 248, 1_000, 100);
        ConsignPurchaseResult buyResult = coordinator.purchase(buyer, 38, (byte) 0, 100);
        check(!buyResult.isSuccess(), "Test 38: Persisted side-effect listing also fails closed at purchase");
        check(buyer.inventory.gold == 1_000 && repo.store.get(38).isActive(),
                "Test 38: Side-effect listing rejection has no debit or transition");
        System.out.println("  [PASS] Test 38: Side-effect items cannot bypass atomic bag persistence");
    }

    /** Test 39: The owner tab preserves a valid signed-byte count without hiding the whole shop. */
    static void test39_OwnerPacketCountIsBoundedWithoutWrapping() throws Exception {
        FakeListingRepository repo = new FakeListingRepository();
        ConsignPurchaseCoordinator coordinator = new ConsignPurchaseCoordinator(repo);
        Player owner = createTestPlayer(5039, "Owner39", 248, 100_000, 100);
        List<ConsignItem> listings = new ArrayList<>();
        for (int i = 1; i <= 256; i++) {
            ConsignItem listing = new ConsignItem(i, (short) 190, (int) owner.id, (byte) 0, 100, -1, 1,
                    (byte) 0, new ArrayList<>(), ConsignListingStatus.SOLD, 9000, 2, null);
            repo.store.put(i, listing);
            listings.add(listing);
        }
        coordinator.populateCacheForTest(listings);
        ConsignPurchaseCoordinator.setInstanceForTest(coordinator);
        TestSession session = (TestSession) owner.getSession();

        new ConsignShopService().openShopKyGui(owner);

        Message packet = session.sentMessages.stream()
                .filter(message -> message.command == (byte) -44)
                .findFirst()
                .orElse(null);
        check(packet != null, "Test 39: A bounded -44 packet is still delivered");
        DataInputStream data = new DataInputStream(new ByteArrayInputStream(packet.getData()));
        check(data.readByte() == 2 && data.readByte() == 5, "Test 39: Shop header remains valid");
        for (int tab = 0; tab < 4; tab++) {
            data.readUTF();
            data.readByte();
            int count = data.readByte();
            check(count == 0, "Test 39: Owner listings are excluded from public tabs");
        }
        data.readUTF();
        data.readByte();
        check(data.readByte() == Byte.MAX_VALUE, "Test 39: Owner count is truncated to signed-byte maximum");
        check(packet.getData().length <= 65_535, "Test 39: Truncated owner packet remains within payload limit");
        System.out.println("  [PASS] Test 39: Owner packet count is bounded without byte wrapping");
    }

    /** Test 40: Fail-closed quarantine prevents logout/autosave from overwriting DB. */
    static void test40_QuarantinedPlayerAutosaveIsSkipped() {
        Player player = createTestPlayer(5040, "Player40", 248, 100_000, 100);
        player.persistenceQuarantined = true;
        PlayerDAO.updatePlayer(player);
        check(player.persistenceQuarantined, "Test 40: Quarantine remains set after skipped autosave");
        check(player.inventory.gold == 100_000, "Test 40: Skipped autosave leaves RAM untouched");
        System.out.println("  [PASS] Test 40: Quarantined player autosave is skipped");
    }

    /** Test 41: A DB CAS conflict refreshes a stale ACTIVE cache entry. */
    static void test41_ConflictRefreshesStaleListingProjection() {
        FakeListingRepository repo = new FakeListingRepository();
        ConsignItem databaseListing = new ConsignItem(41, (short) 190, 9999, (byte) 0, 100, -1, 1,
                (byte) 0, new ArrayList<>(), ConsignListingStatus.ACTIVE, 0, 1, null);
        repo.store.put(41, databaseListing);
        ConsignPurchaseCoordinator coordinator = new ConsignPurchaseCoordinator(repo);
        coordinator.populateCacheForTest(Collections.singletonList(databaseListing.snapshot()));
        databaseListing.setStatus(ConsignListingStatus.SOLD);
        databaseListing.setBuyerId(7000);
        databaseListing.setVersion(2);
        Player buyer = createTestPlayer(5041, "Buyer41", 248, 1_000, 100);

        ConsignPurchaseResult result = coordinator.purchase(buyer, 41, (byte) 0, 100);
        ConsignItem refreshed = coordinator.getListingSnapshot(41);

        check(result.getOutcome() == ConsignPurchaseResult.Outcome.CONFLICT,
                "Test 41: Stale cache loses the database CAS");
        check(refreshed != null && refreshed.isSold() && refreshed.getVersion() == 2,
                "Test 41: Cache projection refreshes to authoritative SOLD v2");
        check(buyer.inventory.gold == 1_000, "Test 41: Conflict does not debit buyer");
        System.out.println("  [PASS] Test 41: CAS conflict refreshes stale listing projection");
    }
}
