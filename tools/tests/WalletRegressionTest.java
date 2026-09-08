package tools.tests;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import nro.models.player.Currency;
import nro.models.player.Inventory;
import nro.models.player.PlayerConfig;
import nro.models.player.PlayerWallet;
import nro.models.player.PreparedWalletMutation;
import nro.models.player.WalletLeg;
import nro.models.player.WalletMutationContext;
import nro.models.player.WalletReason;
import nro.models.player.WalletResult;
import nro.models.player.WalletSnapshot;
import nro.models.player.WalletStatus;
import org.json.simple.JSONArray;
import org.json.simple.JSONValue;

/**
 * SEC-06 / WALLET-01 Comprehensive Regression Test Suite.
 * Covers all mandatory core and hardening scenarios for PlayerWallet.
 */
public final class WalletRegressionTest {

    private static int assertions = 0;

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
    }

    private static void assertEquals(long expected, long actual, String message) {
        assertions++;
        if (expected != actual) {
            throw new AssertionError("FAILED: " + message + " - expected: " + expected + ", actual: " + actual);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        assertions++;
        if (expected == null && actual == null) return;
        if (expected == null || !expected.equals(actual)) {
            throw new AssertionError("FAILED: " + message + " - expected: " + expected + ", actual: " + actual);
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== RUNNING WALLET-01 REGRESSION TESTS ===");

        testCase1_DebitSuccess();
        testCase2_InsufficientFundsLeavesStateUnchanged();
        testCase3_ZeroAndNegativeAmountsRejected();
        testCase4_ExactCreditReachingCapSucceeds();
        testCase5_ExactCreditBeyondCapFailsWithoutPartialCredit();
        testCase6_ExplicitCappedCreditReportsExactAppliedAmount();
        testCase7_ArithmeticOverflowRejected();
        testCase8_CorruptCurrentBalancesReturnInvalidBalance();
        testCase9_InactiveDisposedInventoryRejectsMutation();
        testCase10_MultiCurrencyBatchSuccess();
        testCase11_InvalidBatchLegLeavesAllCurrenciesUnchanged();
        testCase12_PreparedMutationRejectsStaleBeforeState();
        testCase13_ConcurrentDebitsCannotProduceNegativeBalance();
        testCase14_HundredsOfConcurrentRequestsProduceCorrectSuccesses();
        testCase15_OppositeDirectionTransfersDoNotDeadlock();
        testCase16_SaveLoadSnapshotRoundTripPreservesAllCurrencies();
        testCase17_SelfTransferIsRejectedWithoutMinting();
        testCase18_ForgedOrForeignPreparedMutationIsRejected();
        testCase19_ExactRestoreRejectsCorruptPersistenceData();
        testCase20_DiscardedFailureCanBeMadeFailClosed();
        testCase21_PairedBatchRejectsWithoutPartialDebit();

        System.out.println("ALL WALLET-01 REGRESSION TESTS PASSED! Total assertions: " + assertions);
    }

    // 1. Debit success
    private static void testCase1_DebitSuccess() {
        Inventory inv = new Inventory();
        inv.gold = 1000L;
        inv.gem = 50;
        PlayerWallet wallet = inv.getWallet();

        WalletResult r1 = wallet.tryDebit(Currency.GOLD, 300L, WalletMutationContext.of(WalletReason.SHOP_PURCHASE));
        check(r1.isSuccess(), "Debit gold should succeed");
        assertEquals(1000L, r1.getBalanceBefore(), "Gold before");
        assertEquals(700L, r1.getBalanceAfter(), "Gold after");
        assertEquals(700L, inv.gold, "Live inventory gold");

        WalletResult r2 = wallet.tryDebit(Currency.GEM, 20L, WalletMutationContext.of(WalletReason.SHOP_PURCHASE));
        check(r2.isSuccess(), "Debit gem should succeed");
        assertEquals(30L, inv.gem, "Live inventory gem");

        System.out.println("  [PASS] Case 1: Debit success");
    }

    // 2. Insufficient funds leaves state unchanged
    private static void testCase2_InsufficientFundsLeavesStateUnchanged() {
        Inventory inv = new Inventory();
        inv.gold = 500L;
        PlayerWallet wallet = inv.getWallet();

        WalletResult res = wallet.tryDebit(Currency.GOLD, 501L, WalletMutationContext.of(WalletReason.SHOP_PURCHASE));
        check(!res.isSuccess(), "Debit should fail");
        assertEquals(WalletStatus.INSUFFICIENT_FUNDS, res.getStatus(), "Status INSUFFICIENT_FUNDS");
        assertEquals(500L, inv.gold, "Gold unchanged");

        System.out.println("  [PASS] Case 2: Insufficient funds leaves state unchanged");
    }

    // 3. Zero and negative amounts rejected
    private static void testCase3_ZeroAndNegativeAmountsRejected() {
        Inventory inv = new Inventory();
        inv.gold = 500L;
        PlayerWallet wallet = inv.getWallet();

        WalletResult r1 = wallet.tryDebit(Currency.GOLD, 0L, WalletMutationContext.of(WalletReason.OTHER));
        assertEquals(WalletStatus.INVALID_AMOUNT, r1.getStatus(), "Debit 0 rejected");
        assertEquals(500L, inv.gold, "Gold unchanged");

        WalletResult r2 = wallet.tryDebit(Currency.GOLD, -100L, WalletMutationContext.of(WalletReason.OTHER));
        assertEquals(WalletStatus.INVALID_AMOUNT, r2.getStatus(), "Debit negative rejected");
        assertEquals(500L, inv.gold, "Gold unchanged");

        WalletResult r3 = wallet.tryCreditExact(Currency.GOLD, 0L, WalletMutationContext.of(WalletReason.OTHER));
        assertEquals(WalletStatus.INVALID_AMOUNT, r3.getStatus(), "Credit exact 0 rejected");

        WalletResult r4 = wallet.tryCreditExact(Currency.GOLD, -50L, WalletMutationContext.of(WalletReason.OTHER));
        assertEquals(WalletStatus.INVALID_AMOUNT, r4.getStatus(), "Credit exact negative rejected");

        WalletResult r5 = wallet.creditUpToCap(Currency.GOLD, 0L, WalletMutationContext.of(WalletReason.OTHER));
        assertEquals(WalletStatus.INVALID_AMOUNT, r5.getStatus(), "Credit capped 0 rejected");

        WalletResult r6 = wallet.creditUpToCap(Currency.GOLD, -10L, WalletMutationContext.of(WalletReason.OTHER));
        assertEquals(WalletStatus.INVALID_AMOUNT, r6.getStatus(), "Credit capped negative rejected");

        System.out.println("  [PASS] Case 3: Zero and negative amounts rejected");
    }

    // 4. Exact credit reaching cap succeeds
    private static void testCase4_ExactCreditReachingCapSucceeds() {
        Inventory inv = new Inventory();
        long maxGold = PlayerConfig.getMaxGold();
        inv.gold = maxGold - 1000L;
        PlayerWallet wallet = inv.getWallet();

        WalletResult res = wallet.tryCreditExact(Currency.GOLD, 1000L, WalletMutationContext.of(WalletReason.TASK_REWARD));
        check(res.isSuccess(), "Credit exactly to cap should succeed");
        assertEquals(maxGold, inv.gold, "Gold reached cap exactly");

        System.out.println("  [PASS] Case 4: Exact credit reaching cap succeeds");
    }

    // 5. Exact credit beyond cap fails without partial credit
    private static void testCase5_ExactCreditBeyondCapFailsWithoutPartialCredit() {
        Inventory inv = new Inventory();
        long maxGold = PlayerConfig.getMaxGold();
        inv.gold = maxGold - 500L;
        PlayerWallet wallet = inv.getWallet();

        WalletResult res = wallet.tryCreditExact(Currency.GOLD, 501L, WalletMutationContext.of(WalletReason.ACHIEVEMENT_REWARD));
        check(!res.isSuccess(), "Credit exceeding cap should fail");
        assertEquals(WalletStatus.LIMIT_EXCEEDED, res.getStatus(), "Status LIMIT_EXCEEDED");
        assertEquals(maxGold - 500L, inv.gold, "Gold remains strictly unchanged");

        System.out.println("  [PASS] Case 5: Exact credit beyond cap fails without partial credit");
    }

    // 6. Explicit capped credit reports exact applied amount
    private static void testCase6_ExplicitCappedCreditReportsExactAppliedAmount() {
        Inventory inv = new Inventory();
        long maxGold = PlayerConfig.getMaxGold();
        inv.gold = maxGold - 300L;
        PlayerWallet wallet = inv.getWallet();

        WalletResult res = wallet.creditUpToCap(Currency.GOLD, 1000L, WalletMutationContext.of(WalletReason.ITEM_PICKUP));
        check(res.isSuccess(), "creditUpToCap should succeed");
        assertEquals(1000L, res.getAmountRequested(), "Requested amount");
        assertEquals(300L, res.getAmountApplied(), "Applied amount");
        assertEquals(maxGold, res.getBalanceAfter(), "Balance after");
        assertEquals(maxGold, inv.gold, "Live balance reached cap");

        System.out.println("  [PASS] Case 6: Explicit capped credit reports exact applied amount");
    }

    // 7. Arithmetic overflow rejected
    private static void testCase7_ArithmeticOverflowRejected() {
        Inventory inv = new Inventory();
        inv.gold = 1000L;
        PlayerWallet wallet = inv.getWallet();

        WalletResult res = wallet.tryCreditExact(Currency.GOLD, Long.MAX_VALUE, WalletMutationContext.of(WalletReason.OTHER));
        check(!res.isSuccess(), "Overflow credit should fail");
        assertEquals(WalletStatus.LIMIT_EXCEEDED, res.getStatus(), "Overflow treated as LIMIT_EXCEEDED");
        assertEquals(1000L, inv.gold, "Gold unchanged");

        System.out.println("  [PASS] Case 7: Arithmetic overflow rejected");
    }

    // 8. Corrupt current balances return INVALID_BALANCE
    private static void testCase8_CorruptCurrentBalancesReturnInvalidBalance() {
        Inventory inv = new Inventory();
        inv.gem = -50;
        PlayerWallet wallet = inv.getWallet();

        WalletResult r1 = wallet.tryDebit(Currency.GEM, 10L, WalletMutationContext.of(WalletReason.OTHER));
        assertEquals(WalletStatus.INVALID_BALANCE, r1.getStatus(), "Negative balance rejects debit");
        assertEquals(-50, inv.gem, "Gem unchanged");

        WalletResult r2 = wallet.tryCreditExact(Currency.GEM, 10L, WalletMutationContext.of(WalletReason.OTHER));
        assertEquals(WalletStatus.INVALID_BALANCE, r2.getStatus(), "Negative balance rejects credit");

        inv.gem = PlayerConfig.getMaxGem() + 10;
        WalletResult r3 = wallet.tryDebit(Currency.GEM, 10L, WalletMutationContext.of(WalletReason.OTHER));
        assertEquals(WalletStatus.INVALID_BALANCE, r3.getStatus(), "Above-cap balance rejects debit");

        System.out.println("  [PASS] Case 8: Corrupt current balances return INVALID_BALANCE");
    }

    // 9. Inactive disposed inventory rejects mutation
    private static void testCase9_InactiveDisposedInventoryRejectsMutation() {
        Inventory inv = new Inventory();
        inv.gold = 1000L;
        PlayerWallet wallet = inv.getWallet();
        inv.dispose();

        WalletResult res = wallet.tryDebit(Currency.GOLD, 100L, WalletMutationContext.of(WalletReason.OTHER));
        assertEquals(WalletStatus.INACTIVE, res.getStatus(), "Disposed inventory rejects debit");
        assertEquals(1000L, inv.gold, "Gold unchanged");

        WalletResult r2 = wallet.tryCreditExact(Currency.GOLD, 100L, WalletMutationContext.of(WalletReason.OTHER));
        assertEquals(WalletStatus.INACTIVE, r2.getStatus(), "Disposed inventory rejects credit");

        System.out.println("  [PASS] Case 9: Inactive disposed inventory rejects mutation");
    }

    // 10. Multi-currency batch success
    private static void testCase10_MultiCurrencyBatchSuccess() {
        Inventory inv = new Inventory();
        inv.gold = 1000L;
        inv.gem = 50;
        inv.ruby = 20;
        inv.coupon = 10;
        PlayerWallet wallet = inv.getWallet();

        List<WalletLeg> legs = List.of(
                WalletLeg.debit(Currency.GOLD, 400L),
                WalletLeg.debit(Currency.GEM, 10L),
                WalletLeg.credit(Currency.RUBY, 5L),
                WalletLeg.debit(Currency.COUPON, 2L)
        );

        WalletResult res = wallet.executeBatch(legs, WalletMutationContext.of(WalletReason.SHOP_PURCHASE));
        check(res.isSuccess(), "Batch should succeed");
        assertEquals(600L, inv.gold, "Gold after batch");
        assertEquals(40, inv.gem, "Gem after batch");
        assertEquals(25, inv.ruby, "Ruby after batch");
        assertEquals(8, inv.coupon, "Coupon after batch");

        System.out.println("  [PASS] Case 10: Multi-currency batch success");
    }

    // 11. One invalid batch leg leaves every currency unchanged
    private static void testCase11_InvalidBatchLegLeavesAllCurrenciesUnchanged() {
        Inventory inv = new Inventory();
        inv.gold = 1000L;
        inv.gem = 50;
        inv.ruby = 20;
        inv.coupon = 10;
        PlayerWallet wallet = inv.getWallet();

        List<WalletLeg> legs = List.of(
                WalletLeg.debit(Currency.GOLD, 400L),
                WalletLeg.debit(Currency.GEM, 60L), // Insufficient!
                WalletLeg.credit(Currency.RUBY, 5L)
        );

        WalletResult res = wallet.executeBatch(legs, WalletMutationContext.of(WalletReason.SHOP_PURCHASE));
        check(!res.isSuccess(), "Batch should fail");
        assertEquals(WalletStatus.INSUFFICIENT_FUNDS, res.getStatus(), "Status INSUFFICIENT_FUNDS");
        assertEquals(1000L, inv.gold, "Gold unchanged");
        assertEquals(50, inv.gem, "Gem unchanged");
        assertEquals(20, inv.ruby, "Ruby unchanged");
        assertEquals(10, inv.coupon, "Coupon unchanged");

        System.out.println("  [PASS] Case 11: One invalid batch leg leaves every currency unchanged");
    }

    // 12. Prepared mutation rejects stale before-state
    private static void testCase12_PreparedMutationRejectsStaleBeforeState() {
        Inventory inv = new Inventory();
        inv.gold = 1000L;
        PlayerWallet wallet = inv.getWallet();

        PreparedWalletMutation prepared = wallet.prepareDebit(Currency.GOLD, 300L,
                WalletMutationContext.of(WalletReason.CONSIGN_PURCHASE));
        check(prepared != null, "Preparation should succeed");
        assertEquals(1000L, prepared.getBeforeSnapshot().getGold(), "Before gold in plan");
        assertEquals(700L, prepared.getAfterSnapshot().getGold(), "After gold in plan");

        // Concurrent mutation alters state
        inv.gold = 900L;

        // Applying prepared mutation must fail with STATE_CHANGED
        WalletResult res = wallet.applyCommitted(prepared);
        check(!res.isSuccess(), "Applying stale prepared mutation must fail");
        assertEquals(WalletStatus.STATE_CHANGED, res.getStatus(), "Status STATE_CHANGED");
        assertEquals(900L, inv.gold, "Gold remains at modified balance");

        System.out.println("  [PASS] Case 12: Prepared mutation rejects stale before-state");
    }

    // 13. Concurrent debits cannot produce a negative balance
    private static void testCase13_ConcurrentDebitsCannotProduceNegativeBalance() throws Exception {
        Inventory inv = new Inventory();
        inv.gold = 100L;
        PlayerWallet wallet = inv.getWallet();

        int threads = 10;
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            exec.submit(() -> {
                try {
                    latch.await();
                    wallet.tryDebit(Currency.GOLD, 20L, WalletMutationContext.of(WalletReason.OTHER));
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        latch.countDown();
        check(done.await(5, TimeUnit.SECONDS), "Concurrent debits timeout");
        exec.shutdown();

        check(inv.gold >= 0L, "Balance must never be negative: " + inv.gold);
        assertEquals(0L, inv.gold, "100 gold with 5 debits of 20 = exactly 0 remaining");

        System.out.println("  [PASS] Case 13: Concurrent debits cannot produce a negative balance");
    }

    // 14. 100–1,000 concurrent requests produce the mathematically correct number of successes
    private static void testCase14_HundredsOfConcurrentRequestsProduceCorrectSuccesses() throws Exception {
        Inventory inv = new Inventory();
        inv.gold = 10_000L;
        PlayerWallet wallet = inv.getWallet();

        int totalThreads = 500;
        long debitAmount = 50L;
        // Total gold 10,000 / 50 = exactly 200 successes possible
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        ExecutorService exec = Executors.newFixedThreadPool(32);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalThreads);

        for (int i = 0; i < totalThreads; i++) {
            exec.submit(() -> {
                try {
                    startLatch.await();
                    WalletResult res = wallet.tryDebit(Currency.GOLD, debitAmount, WalletMutationContext.of(WalletReason.OTHER));
                    if (res.isSuccess()) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        check(doneLatch.await(10, TimeUnit.SECONDS), "Stress test timeout");
        exec.shutdown();

        assertEquals(200, successCount.get(), "Exact success count");
        assertEquals(300, failCount.get(), "Exact fail count");
        assertEquals(0L, inv.gold, "Final balance exact 0");

        System.out.println("  [PASS] Case 14: 500 concurrent requests produce mathematically correct outcomes");
    }

    // 15. Opposite-direction two-player transfers do not deadlock
    private static void testCase15_OppositeDirectionTransfersDoNotDeadlock() throws Exception {
        Inventory inv1 = new Inventory();
        inv1.gold = 100_000L;
        Inventory inv2 = new Inventory();
        inv2.gold = 100_000L;

        PlayerWallet w1 = inv1.getWallet();
        PlayerWallet w2 = inv2.getWallet();

        long p1Id = 1001L;
        long p2Id = 1002L;

        int iterations = 500;
        ExecutorService exec = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        // Thread 1: p1 -> p2
        exec.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < iterations; i++) {
                    PlayerWallet.transfer(w1, w2, Currency.GOLD, 10L, WalletMutationContext.of(WalletReason.GIFT_TRANSFER), p1Id, p2Id);
                }
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: p2 -> p1
        exec.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < iterations; i++) {
                    PlayerWallet.transfer(w2, w1, Currency.GOLD, 10L, WalletMutationContext.of(WalletReason.GIFT_TRANSFER), p2Id, p1Id);
                }
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean finished = doneLatch.await(5, TimeUnit.SECONDS);
        exec.shutdown();

        check(finished, "Transfers must not deadlock");
        assertEquals(200_000L, inv1.gold + inv2.gold, "Conservation of total currency");

        System.out.println("  [PASS] Case 15: Opposite-direction two-player transfers do not deadlock");
    }

    // 16. Save/load snapshot round-trip preserves gold, gem, ruby, and coupon
    private static void testCase16_SaveLoadSnapshotRoundTripPreservesAllCurrencies() {
        Inventory inv = new Inventory();
        inv.gold = 123456789L;
        inv.gem = 4321;
        inv.ruby = 987;
        inv.coupon = 65;
        inv.event = 42;

        WalletSnapshot snapshot = inv.getWallet().getSnapshot();
        String json = snapshot.toDataInventoryJsonString(inv.event);

        JSONArray parsed = (JSONArray) JSONValue.parse(json);
        WalletSnapshot loaded = WalletSnapshot.fromParsedJson(parsed);

        assertEquals(123456789L, loaded.getGold(), "Round-trip gold");
        assertEquals(4321, loaded.getGem(), "Round-trip gem");
        assertEquals(987, loaded.getRuby(), "Round-trip ruby");
        assertEquals(65, loaded.getCoupon(), "Round-trip coupon");

        Inventory restored = new Inventory();
        WalletResult restoreResult = restored.getWallet().restoreExact(loaded,
                WalletMutationContext.of(WalletReason.LOAD, "wallet-round-trip", "Regression restore"));
        check(restoreResult.isSuccess(), "Valid round-trip snapshot must restore exactly");

        assertEquals(123456789L, restored.gold, "Restored inventory gold");
        assertEquals(4321, restored.gem, "Restored inventory gem");
        assertEquals(987, restored.ruby, "Restored inventory ruby");
        assertEquals(65, restored.coupon, "Restored inventory coupon");

        System.out.println("  [PASS] Case 16: Save/load snapshot round-trip preserves all currencies");
    }

    // 17. A transfer to the same wallet must be a no-op, never a mint operation.
    private static void testCase17_SelfTransferIsRejectedWithoutMinting() {
        Inventory inv = new Inventory();
        inv.gold = 100L;
        PlayerWallet wallet = inv.getWallet();

        WalletResult result = PlayerWallet.transfer(wallet, wallet, Currency.GOLD, 10L,
                WalletMutationContext.of(WalletReason.GIFT_TRANSFER), 1001L, 1001L);

        check(!result.isSuccess(), "Self-transfer must be rejected");
        assertEquals(100L, inv.gold, "Self-transfer must not mint or debit currency");
        System.out.println("  [PASS] Case 17: Self-transfer is rejected without minting");
    }

    // 18. A prepared mutation is valid only for the wallet that created it.
    private static void testCase18_ForgedOrForeignPreparedMutationIsRejected() throws Exception {
        Inventory targetInventory = new Inventory();
        targetInventory.gold = 100L;
        PlayerWallet target = targetInventory.getWallet();

        Inventory foreignInventory = new Inventory();
        foreignInventory.gold = 100L;
        PlayerWallet foreign = foreignInventory.getWallet();

        WalletSnapshot before = target.getSnapshot();
        WalletSnapshot maliciousAfter = new WalletSnapshot(-1L, -1, -1, -1);
        PreparedWalletMutation forged = constructPreparedMutation(
                foreign, before, maliciousAfter, List.of(), WalletMutationContext.of(WalletReason.OTHER));

        WalletResult result = target.applyCommitted(forged);
        check(!result.isSuccess(), "Forged or foreign prepared mutation must be rejected");
        assertEquals(100L, targetInventory.gold, "Forged plan must leave gold unchanged");
        assertEquals(0L, targetInventory.gem, "Forged plan must leave gem unchanged");
        assertEquals(0L, targetInventory.ruby, "Forged plan must leave ruby unchanged");
        assertEquals(0L, targetInventory.coupon, "Forged plan must leave coupon unchanged");
        System.out.println("  [PASS] Case 18: Forged or foreign prepared mutation is rejected");
    }

    private static PreparedWalletMutation constructPreparedMutation(PlayerWallet owner,
            WalletSnapshot before, WalletSnapshot after, List<WalletLeg> legs,
            WalletMutationContext context) throws Exception {
        for (Constructor<?> constructor : PreparedWalletMutation.class.getDeclaredConstructors()) {
            constructor.setAccessible(true);
            if (constructor.getParameterCount() == 5) {
                return (PreparedWalletMutation) constructor.newInstance(owner, before, after, legs, context);
            }
            if (constructor.getParameterCount() == 4) {
                return (PreparedWalletMutation) constructor.newInstance(before, after, legs, context);
            }
        }
        throw new AssertionError("PreparedWalletMutation constructor not found");
    }

    // 19. Persistence recovery must fail closed instead of silently clamping corrupt balances.
    private static void testCase19_ExactRestoreRejectsCorruptPersistenceData() throws Exception {
        Inventory inv = new Inventory();
        inv.gold = 50L;
        inv.gem = 25;
        PlayerWallet wallet = inv.getWallet();

        Method restoreExact;
        try {
            restoreExact = PlayerWallet.class.getMethod("restoreExact",
                    WalletSnapshot.class, WalletMutationContext.class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("PlayerWallet.restoreExact must exist for persistence hydration", e);
        }
        WalletResult result = (WalletResult) restoreExact.invoke(wallet,
                new WalletSnapshot(-1L, 10, 10, 10),
                WalletMutationContext.of(WalletReason.LOAD, "player-load:regression", "Regression load"));

        check(!result.isSuccess(), "Corrupt persistence snapshot must be rejected");
        assertEquals(50L, inv.gold, "Rejected restore must leave gold unchanged");
        assertEquals(25L, inv.gem, "Rejected restore must leave gem unchanged");

        JSONArray malformed = new JSONArray();
        malformed.add("not-a-number");
        malformed.add(10);
        malformed.add(10);
        malformed.add(10);
        boolean malformedRejected = false;
        try {
            WalletSnapshot.fromParsedJson(malformed);
        } catch (IllegalArgumentException expected) {
            malformedRejected = true;
        }
        check(malformedRejected, "Malformed persisted currency must not be normalized to zero");
        System.out.println("  [PASS] Case 19: Exact restore rejects corrupt persistence data");
    }

    // 20. Legacy call sites can explicitly fail closed until they expose a typed result path.
    private static void testCase20_DiscardedFailureCanBeMadeFailClosed() {
        Inventory inv = new Inventory();
        inv.gold = 5L;
        boolean rejected = false;
        try {
            inv.getWallet().tryDebit(Currency.GOLD, 10L,
                    WalletMutationContext.of(WalletReason.OTHER, "Fail-closed regression"))
                    .requireSuccess();
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        check(rejected, "requireSuccess must abort a rejected legacy mutation path");
        assertEquals(5L, inv.gold, "Rejected fail-closed debit must leave balance unchanged");
        System.out.println("  [PASS] Case 20: Discarded failure can be made fail-closed");
    }

    // 21. Two-wallet composite debits are atomic across both wallet monitors.
    private static void testCase21_PairedBatchRejectsWithoutPartialDebit() {
        Inventory firstInventory = new Inventory();
        firstInventory.gold = 100L;
        Inventory secondInventory = new Inventory();
        secondInventory.gold = 5L;

        WalletResult result = PlayerWallet.executePair(
                firstInventory.getWallet(), List.of(WalletLeg.debit(Currency.GOLD, 10L)),
                secondInventory.getWallet(), List.of(WalletLeg.debit(Currency.GOLD, 10L)),
                WalletMutationContext.of(WalletReason.PVP_WAGER, "Paired wager regression"));

        check(!result.isSuccess(), "Paired debit must reject when either wallet cannot pay");
        assertEquals(100L, firstInventory.gold, "Rejected paired debit must preserve first wallet");
        assertEquals(5L, secondInventory.gold, "Rejected paired debit must preserve second wallet");
        System.out.println("  [PASS] Case 21: Paired batch rejects without partial debit");
    }
}
