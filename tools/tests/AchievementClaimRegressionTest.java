import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import nro.models.item.Item;
import nro.models.network.Message;
import nro.models.network.MySession;
import nro.models.player.Achievement;
import nro.models.player.Achievement.ClaimResult;
import nro.models.player.Achievement.ClaimStatus;
import nro.models.player.Inventory;
import nro.models.player.Player;
import nro.models.player.PlayerConfig;
import nro.models.player_system.Template.AchievementQuest;
import nro.models.player_system.Template.AchievementTemplate;
import nro.models.server.Controller;
import nro.models.server.Manager;
import nro.models.services.AchievementService;

/**
 * SEC-01 Hardening Regression Test Suite
 * Verifies at-most-once claims on one live Achievement instance under
 * sequential, concurrent, boundary, lifecycle, and wire/controller conditions.
 */
public final class AchievementClaimRegressionTest {

    private static int assertions = 0;
    private static final long DEFAULT_TIMEOUT_SECONDS = 5L;

    /**
     * Mock Socket to safely instantiate MySession in headless test environment
     * without binding real network interfaces.
     */
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
        public InputStream getInputStream() {
            return in;
        }

        @Override
        public OutputStream getOutputStream() {
            return out;
        }

        @Override
        public void setSendBufferSize(int size) {}

        @Override
        public void setReceiveBufferSize(int size) {}
    }

    /**
     * TestSession extending MySession to capture outbound messages
     * and optionally simulate network write failures.
     */
    static class TestSession extends MySession {
        final List<Message> sentMessages = new ArrayList<>();
        boolean throwOnSend = false;

        public TestSession() {
            super(new MockSocket());
        }

        @Override
        public void sendMessage(Message msg) {
            if (throwOnSend && msg != null && msg.command == -76) {
                throw new RuntimeException("Simulated network write error for packet -76");
            }
            if (msg != null) {
                sentMessages.add(msg);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== RUNNING SEC-01 ACHIEVEMENT CLAIM REGRESSION TESTS ===");

        List<AchievementTemplate> backupTemplates = new ArrayList<>(Manager.ACHIEVEMENT_TEMPLATE);
        try {
            setupTemplates();

            testCase1_UncompletedAchievement();
            testCase2_CompletedAchievement();
            testCase3_SequentialRepeatClaims();
            testCase4_ConcurrentClaims();
            testCase5_InvalidIndices();
            testCase6_GemLimitPolicy();
            testCase7_NullSafety();
            testCase8_BagCapacityRequirement();
            testCase9_InventoryInactive();
            testCase10_ClaimClosed();
            testCase11_ConcurrentClaimVsClose();
            testCase12_InvalidRewardMoney();
            testCase13_WireControllerSuccessAndFailure();
            testCase14_ResponseSendFailureLogged();

            // Round 3 additions
            testCaseR3_1_doneAndDoneNotAddAfterClosed();
            testCaseR3_2_rewardIsNoOp();
            testCaseR3_3_negativeGemBalance();
            testCaseR3_4_snapshotPreservesNullIndex();
            testCaseR3_5_getAchievementListDefensive();
            testCaseR3_6_raceCloseVsDone();
            testCaseR3_7_getCompletedUnderLock();
            testCaseR3_8_inventoryDisposeRace();
            testCaseR3_9_playerDisposeOrder();

            System.out.println("ALL SEC-01 REGRESSION TESTS PASSED! Total assertions: " + assertions);

        } finally {
            Manager.ACHIEVEMENT_TEMPLATE.clear();
            Manager.ACHIEVEMENT_TEMPLATE.addAll(backupTemplates);
            System.out.println("Restored Manager.ACHIEVEMENT_TEMPLATE successfully.");
        }
    }

    private static void setupTemplates() {
        Manager.ACHIEVEMENT_TEMPLATE.clear();
        // Index 0: Power achievement (100 gem, maxCount = 1000)
        Manager.ACHIEVEMENT_TEMPLATE.add(new AchievementTemplate("Gia nhập vệ binh", "1000 SM", 100, 1000));
        // Index 1: Power achievement (200 gem, maxCount = 5000)
        Manager.ACHIEVEMENT_TEMPLATE.add(new AchievementTemplate("Sức mạnh siêu cấp", "5000 SM", 200, 5000));
        // Index 2: Magic tree achievement (150 gem, maxCount = 5)
        Manager.ACHIEVEMENT_TEMPLATE.add(new AchievementTemplate("Nông dân chăm chỉ", "Cây đậu cấp 5", 150, 5));
        // Index 3: Generic count achievement (300 gem, maxCount = 100)
        Manager.ACHIEVEMENT_TEMPLATE.add(new AchievementTemplate("Trăm trận trăm thắng", "Thắng 100 trận", 300, 100));
    }

    private static Player createPlayerWithBag() {
        Player player = new Player();
        player.id = 100001;
        player.name = "HeroTest";
        player.inventory.gem = 0;
        player.inventory.itemsBag.add(new Item()); // 1 empty slot (template == null)
        TestSession session = new TestSession();
        player.setSession(session);
        session.player = player;

        // Add quests matching templates
        player.achievement.add(new AchievementQuest(0, false)); // 0
        player.achievement.add(new AchievementQuest(0, false)); // 1
        player.achievement.add(new AchievementQuest(0, false)); // 2
        player.achievement.add(new AchievementQuest(0, false)); // 3
        return player;
    }

    /** Case 1: Uncompleted achievement cannot be claimed, gem unchanged, isRecieve remains false. */
    private static void testCase1_UncompletedAchievement() {
        Player player = createPlayerWithBag();
        player.achievement.doneNotAdd(3, 50); // 50 / 100 (not completed)

        ClaimResult result = player.achievement.claimReward(3);
        check(result.getStatus() == ClaimStatus.NOT_COMPLETED, "Case 1: status must be NOT_COMPLETED");
        check(!result.isSuccess(), "Case 1: isSuccess must be false");
        check(player.inventory.gem == 0, "Case 1: gem balance must remain 0");
        check(!player.achievement.isRecieve(3), "Case 1: isRecieve must remain false");
        check(!player.achievement.canReward(3), "Case 1: canReward must return false");

        player.nPoint.power = 500; // Power index 0: 500 < 1000
        ClaimResult powerResult = player.achievement.claimReward(0);
        check(powerResult.getStatus() == ClaimStatus.NOT_COMPLETED, "Case 1: power achievement not completed");
        check(player.inventory.gem == 0, "Case 1: gem balance remains 0");

        System.out.println("  [PASS] Case 1: Uncompleted achievement rejected safely");
    }

    /** Case 2: Completed achievement claimed once successfully, gem credited, isRecieve is true. */
    private static void testCase2_CompletedAchievement() {
        Player player = createPlayerWithBag();
        player.achievement.doneNotAdd(3, 100);

        check(player.achievement.canReward(3), "Case 2: canReward must be true before claim");
        ClaimResult result = player.achievement.claimReward(3);

        check(result.getStatus() == ClaimStatus.SUCCESS, "Case 2: status must be SUCCESS");
        check(result.isSuccess(), "Case 2: isSuccess must be true");
        check(result.getRewardGem() == 300, "Case 2: rewardGem must match template (300)");
        check(player.inventory.gem == 300, "Case 2: player gem balance must be 300");
        check(player.achievement.isRecieve(3), "Case 2: isRecieve must be true after claim");
        check(!player.achievement.canReward(3), "Case 2: canReward must be false after claim");

        player.nPoint.power = 1000;
        check(player.achievement.canReward(0), "Case 2: canReward power achievement must be true");
        ClaimResult powerResult = player.achievement.claimReward(0);
        check(powerResult.isSuccess(), "Case 2: power achievement claim succeeds");
        check(player.inventory.gem == 400, "Case 2: gem balance updated to 300 + 100 = 400");
        check(player.achievement.isRecieve(0), "Case 2: power achievement isRecieve is true");

        System.out.println("  [PASS] Case 2: Completed achievement claimed successfully once");
    }

    /** Case 3: Calling claim 100 times sequentially yields exactly 1 SUCCESS and 99 ALREADY_CLAIMED. */
    private static void testCase3_SequentialRepeatClaims() {
        Player player = createPlayerWithBag();
        player.achievement.doneNotAdd(3, 100);

        int successCount = 0;
        int alreadyClaimedCount = 0;

        for (int i = 0; i < 100; i++) {
            ClaimResult result = player.achievement.claimReward(3);
            if (result.getStatus() == ClaimStatus.SUCCESS) {
                successCount++;
            } else if (result.getStatus() == ClaimStatus.ALREADY_CLAIMED) {
                alreadyClaimedCount++;
            }
        }

        check(successCount == 1, "Case 3: exactly 1 claim must succeed, got " + successCount);
        check(alreadyClaimedCount == 99, "Case 3: exactly 99 claims must be ALREADY_CLAIMED, got " + alreadyClaimedCount);
        check(player.inventory.gem == 300, "Case 3: total gem must increase only once (expected 300, got " + player.inventory.gem + ")");

        System.out.println("  [PASS] Case 3: Sequential 100x repeat claims resulted in exactly 1 SUCCESS");
    }

    /** Case 4: 100 concurrent threads attempting to claim the same achievement via CountDownLatch. */
    private static void testCase4_ConcurrentClaims() throws Exception {
        int threadCount = 100;
        Player player = createPlayerWithBag();
        player.achievement.doneNotAdd(3, 100);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCounter = new AtomicInteger(0);
        AtomicInteger alreadyClaimedCounter = new AtomicInteger(0);
        AtomicInteger otherCounter = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    boolean started = startLatch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    if (!started) {
                        otherCounter.incrementAndGet();
                        return;
                    }
                    ClaimResult result = player.achievement.claimReward(3);
                    if (result.getStatus() == ClaimStatus.SUCCESS) {
                        successCounter.incrementAndGet();
                    } else if (result.getStatus() == ClaimStatus.ALREADY_CLAIMED) {
                        alreadyClaimedCounter.incrementAndGet();
                    } else {
                        otherCounter.incrementAndGet();
                    }
                } catch (Exception e) {
                    otherCounter.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        check(readyLatch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS), "Case 4: readyLatch timed out");
        startLatch.countDown(); // Release all threads concurrently
        check(doneLatch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS), "Case 4: doneLatch timed out");
        executor.shutdown();

        check(successCounter.get() == 1, "Case 4: exactly 1 concurrent thread must succeed, got " + successCounter.get());
        check(alreadyClaimedCounter.get() == threadCount - 1,
                "Case 4: all other threads must receive ALREADY_CLAIMED, got " + alreadyClaimedCounter.get());
        check(otherCounter.get() == 0, "Case 4: no other status or exceptions allowed, got " + otherCounter.get());
        check(player.inventory.gem == 300, "Case 4: total gem must be exactly 300, got " + player.inventory.gem);

        System.out.println("  [PASS] Case 4: 100 concurrent threads resulted in exactly 1 SUCCESS and no double-reward");
    }

    /** Case 5: Invalid indices (-1, size, size + 10) must return INVALID_INDEX without exceptions. */
    private static void testCase5_InvalidIndices() {
        Player player = createPlayerWithBag();
        player.achievement.doneNotAdd(3, 100);

        int[] invalidIndices = new int[] { -1, 4, 5, 999 };
        for (int idx : invalidIndices) {
            ClaimResult result = player.achievement.claimReward(idx);
            check(result.getStatus() == ClaimStatus.INVALID_INDEX,
                    "Case 5: index " + idx + " must return INVALID_INDEX, got " + result.getStatus());
            check(!player.achievement.canReward(idx),
                    "Case 5: canReward for invalid index " + idx + " must return false");
        }

        AchievementService.gI().confirmAchievement(player, (byte) -1);
        AchievementService.gI().confirmAchievement(player, 100);
        check(player.inventory.gem == 0, "Case 5: invalid indices must not modify gem balance");

        System.out.println("  [PASS] Case 5: Invalid indices handled safely without throwing exceptions");
    }

    /** Case 6: Gem limit policy: rejection without partial credit or claim commit when overflowing maxGem. */
    private static void testCase6_GemLimitPolicy() {
        Player player = createPlayerWithBag();
        int maxGem = PlayerConfig.getMaxGem();
        int rewardAmount = 300; // Template index 3 has money = 300
        player.inventory.gem = maxGem - 100; // balance + reward = maxGem + 200 > maxGem
        player.achievement.doneNotAdd(3, 100);

        ClaimResult result = player.achievement.claimReward(3);
        check(result.getStatus() == ClaimStatus.GEM_LIMIT,
                "Case 6: claim must return GEM_LIMIT when exceeding maxGem, got: " + result.getStatus());
        check(!result.isSuccess(), "Case 6: isSuccess must be false on GEM_LIMIT");
        check(player.inventory.gem == maxGem - 100, "Case 6: gem balance must NOT change (no partial credit)");
        check(!player.achievement.isRecieve(3), "Case 6: isRecieve must remain false when rejected due to limit");
        check(player.achievement.canReward(3), "Case 6: canReward must remain true so player can claim later");

        // Direct test on tryCreditGemExact
        Inventory.GemCreditResult creditResult = player.inventory.tryCreditGemExact(200);
        check(creditResult.status == Inventory.GemCreditStatus.GEM_LIMIT, "Case 6: tryCreditGemExact must return GEM_LIMIT");
        check(player.inventory.gem == maxGem - 100, "Case 6: tryCreditGemExact must leave gem unchanged");

        // Player spends gems down to safe level
        player.inventory.gem = maxGem - 500;
        ClaimResult retryResult = player.achievement.claimReward(3);
        check(retryResult.getStatus() == ClaimStatus.SUCCESS, "Case 6: claim succeeds once player has space for gems");
        check(player.inventory.gem == (maxGem - 500) + rewardAmount, "Case 6: full reward must be credited");
        check(player.achievement.isRecieve(3), "Case 6: isRecieve must be true after successful claim");

        System.out.println("  [PASS] Case 6: Gem limit policy rejects safely without losing player gems");
    }

    /** Case 7: Null player or null achievement handled safely with no NPE. */
    private static void testCase7_NullSafety() {
        AchievementService.gI().confirmAchievement(null, (byte) 0);
        AchievementService.gI().confirmAchievement(null, 0);

        Player nullAchPlayer = new Player();
        nullAchPlayer.achievement = null;
        AchievementService.gI().confirmAchievement(nullAchPlayer, (byte) 0);

        Achievement orphan = new Achievement(null);
        orphan.add(new AchievementQuest(100, false));
        ClaimResult result = orphan.claimReward(0);
        check(result.getStatus() == ClaimStatus.PLAYER_NULL, "Case 7: orphan achievement must return PLAYER_NULL");
        check(orphan.getCompleted(0) == 100, "Case 7: orphan getCompleted returns quest value without NPE");

        System.out.println("  [PASS] Case 7: Null player and null achievement handled safely");
    }

    /** Case 8: Bag capacity requirement: BAG_FULL when no space, SUCCESS when space available. */
    private static void testCase8_BagCapacityRequirement() {
        Player player = new Player();
        player.inventory.gem = 0;
        player.inventory.itemsBag.clear(); // 0 bag slots available
        player.achievement.add(new AchievementQuest(0, false));
        player.achievement.add(new AchievementQuest(0, false));
        player.achievement.add(new AchievementQuest(0, false));
        player.achievement.add(new AchievementQuest(100, false)); // index 3 completed

        ClaimResult fullResult = player.achievement.claimReward(3, true);
        check(fullResult.getStatus() == ClaimStatus.BAG_FULL, "Case 8: must return BAG_FULL when bag has no slots");
        check(!player.achievement.isRecieve(3), "Case 8: isRecieve must remain false when bag is full");
        check(player.inventory.gem == 0, "Case 8: gem must not be credited when bag is full");

        player.inventory.itemsBag.add(new Item());
        ClaimResult successResult = player.achievement.claimReward(3, true);
        check(successResult.getStatus() == ClaimStatus.SUCCESS, "Case 8: must succeed when bag slot is available");
        check(player.achievement.isRecieve(3), "Case 8: isRecieve must be true after success");
        check(player.inventory.gem == 300, "Case 8: gem must be credited (300)");

        System.out.println("  [PASS] Case 8: Bag capacity condition checked and respected before commit");
    }

    /** Case 9: Inactive inventory rejects credit and claim cleanly. */
    private static void testCase9_InventoryInactive() {
        Player player = createPlayerWithBag();
        player.achievement.doneNotAdd(3, 100);

        player.inventory.dispose();
        check(!player.inventory.isActive(), "Case 9: inventory must be inactive after dispose");

        ClaimResult result = player.achievement.claimReward(3);
        check(result.getStatus() == ClaimStatus.PLAYER_UNAVAILABLE,
                "Case 9: claimReward on inactive inventory must return PLAYER_UNAVAILABLE, got " + result.getStatus());
        check(!player.achievement.isRecieve(3), "Case 9: isRecieve must remain false");
        check(player.inventory.gem == 0, "Case 9: gem balance must remain 0");

        System.out.println("  [PASS] Case 9: Inactive inventory rejects claims safely");
    }

    /** Case 10: Closed achievement lifecycle rejects subsequent claims. */
    private static void testCase10_ClaimClosed() {
        Player player = createPlayerWithBag();
        player.achievement.doneNotAdd(3, 100);

        check(player.achievement.isAcceptingClaims(), "Case 10: achievement must accept claims initially");
        player.achievement.closeClaims();
        check(!player.achievement.isAcceptingClaims(), "Case 10: achievement must NOT accept claims after closeClaims");

        ClaimResult result = player.achievement.claimReward(3);
        check(result.getStatus() == ClaimStatus.PLAYER_UNAVAILABLE,
                "Case 10: closed achievement must return PLAYER_UNAVAILABLE, got " + result.getStatus());
        check(!player.achievement.isRecieve(3), "Case 10: isRecieve must remain false");
        check(player.inventory.gem == 0, "Case 10: gem must not be credited");

        // Idempotency: calling closeClaims again does not throw
        player.achievement.closeClaims();
        check(!player.achievement.isAcceptingClaims(), "Case 10: closeClaims is idempotent");

        // Snapshotting quests returns a clean copy
        List<AchievementQuest> snapshot = player.achievement.snapshotQuests();
        check(snapshot.size() == 4, "Case 10: snapshotQuests must return all quests");
        check(!snapshot.get(3).isRecieve, "Case 10: snapshot shows quest 3 is not claimed");

        System.out.println("  [PASS] Case 10: Achievement closeClaims enforces closed lifecycle and safe snapshots");
    }

    /** Case 11: Concurrent claims racing against closeClaims/logout cannot cause inconsistent state. */
    private static void testCase11_ConcurrentClaimVsClose() throws Exception {
        int threadCount = 50;
        Player player = createPlayerWithBag();
        player.achievement.doneNotAdd(3, 100);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount + 1);
        CountDownLatch readyLatch = new CountDownLatch(threadCount + 1);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount + 1);

        AtomicInteger successCounter = new AtomicInteger(0);
        AtomicInteger rejectedCounter = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    boolean started = startLatch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    if (!started) {
                        rejectedCounter.incrementAndGet();
                        return;
                    }
                    ClaimResult result = player.achievement.claimReward(3);
                    if (result.getStatus() == ClaimStatus.SUCCESS) {
                        successCounter.incrementAndGet();
                    } else {
                        rejectedCounter.incrementAndGet();
                    }
                } catch (Exception e) {
                    rejectedCounter.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Close thread racing to shut down claims
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                boolean started = startLatch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (started) {
                    player.achievement.closeClaims();
                }
            } catch (Exception e) {
            } finally {
                doneLatch.countDown();
            }
        });

        check(readyLatch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS), "Case 11: readyLatch timed out");
        startLatch.countDown();
        check(doneLatch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS), "Case 11: doneLatch timed out");
        executor.shutdown();

        int successes = successCounter.get();
        check(successes <= 1, "Case 11: successes must be at most 1, got " + successes);
        if (successes == 1) {
            check(player.inventory.gem == 300, "Case 11: if claimed, gem must be 300");
            check(player.achievement.isRecieve(3), "Case 11: if claimed, isRecieve must be true");
        } else {
            check(player.inventory.gem == 0, "Case 11: if close won, gem must be 0");
            check(!player.achievement.isRecieve(3), "Case 11: if close won, isRecieve must be false");
        }

        System.out.println("  [PASS] Case 11: Concurrent claims vs close race preserves strict at most 1 claim");
    }

    /** Case 12: Invalid reward money (money <= 0 in template) rejected without side effects. */
    private static void testCase12_InvalidRewardMoney() {
        Player player = createPlayerWithBag();
        AchievementTemplate original = Manager.ACHIEVEMENT_TEMPLATE.get(3);
        try {
            // Tamper template with non-positive reward
            Manager.ACHIEVEMENT_TEMPLATE.set(3, new AchievementTemplate(original.info1, original.info2, 0, original.maxCount));
            player.achievement.doneNotAdd(3, 100);

            ClaimResult result = player.achievement.claimReward(3);
            check(result.getStatus() == ClaimStatus.PLAYER_UNAVAILABLE,
                    "Case 12: non-positive reward money must return PLAYER_UNAVAILABLE, got " + result.getStatus());
            check(!player.achievement.isRecieve(3), "Case 12: quest must not be marked claimed");
            check(player.inventory.gem == 0, "Case 12: gem balance must remain 0");
        } finally {
            Manager.ACHIEVEMENT_TEMPLATE.set(3, original);
        }

        System.out.println("  [PASS] Case 12: Invalid reward money rejected safely");
    }

    /** Case 13: Wire / Controller level verification of command -76. */
    private static void testCase13_WireControllerSuccessAndFailure() throws Exception {
        Player player = createPlayerWithBag();
        TestSession session = (TestSession) player.getSession();
        player.achievement.doneNotAdd(3, 100);

        // 13.1 Success wire packet: command -76, select = 3
        Message msg = new Message((byte) -76, new byte[] { (byte) 3 });
        Controller.gI().onMessage(session, msg);

        check(player.inventory.gem == 300, "Case 13.1: Controller wire claim credited 300 gem");
        check(player.achievement.isRecieve(3), "Case 13.1: Controller wire claim marked isRecieve = true");

        // Verify response message sent back to client
        check(!session.sentMessages.isEmpty(), "Case 13.1: outbound message list should not be empty");
        Message responseMsg = null;
        for (Message m : session.sentMessages) {
            if (m.command == -76) {
                responseMsg = m;
                break;
            }
        }
        check(responseMsg != null, "Case 13.1: response command -76 packet must be sent to client");
        byte[] respData = responseMsg.getData();
        check(respData != null && respData.length >= 2, "Case 13.1: response payload length >= 2");
        byte action = respData[0];
        byte select = respData[1];
        check(action == 1, "Case 13.1: response action must be 1 (success), got " + action);
        check(select == 3, "Case 13.1: response select must be 3, got " + select);

        // 13.2 Repeat wire packet: duplicate claim attempt via Controller
        session.sentMessages.clear();
        Message repeatMsg = new Message((byte) -76, new byte[] { (byte) 3 });
        Controller.gI().onMessage(session, repeatMsg);

        check(player.inventory.gem == 300, "Case 13.2: duplicate wire packet must not add more gems");
        boolean hasDuplicateSuccess = session.sentMessages.stream().anyMatch(m -> m.command == -76);
        check(!hasDuplicateSuccess, "Case 13.2: no duplicate success packet should be sent for repeated claim");

        // 13.3 Empty payload wire packet: command -76 with 0 bytes
        Message emptyMsg = new Message((byte) -76, new byte[0]);
        Controller.gI().onMessage(session, emptyMsg);
        check(player.inventory.gem == 300, "Case 13.3: empty payload must not crash or modify state");

        // 13.4 Out of bounds wire packet: select = 99
        Message outOfBoundsMsg = new Message((byte) -76, new byte[] { (byte) 99 });
        Controller.gI().onMessage(session, outOfBoundsMsg);
        check(player.inventory.gem == 300, "Case 13.4: out of bounds select must not crash or modify state");

        System.out.println("  [PASS] Case 13: Wire / Controller layer correctly handles success, duplicate, empty, and out-of-bounds");
    }

    /** Case 14: Response send failure logs error without rolling back already committed gem. */
    private static void testCase14_ResponseSendFailureLogged() {
        Player player = createPlayerWithBag();
        TestSession session = (TestSession) player.getSession();
        session.throwOnSend = true; // Force exception on player.sendMessage()
        player.achievement.doneNotAdd(3, 100);

        // confirmAchievement attempts to send -76 packet which fails and gets logged
        AchievementService.gI().confirmAchievement(player, 3);

        check(player.inventory.gem == 300, "Case 14: gem remains credited despite packet delivery failure");
        check(player.achievement.isRecieve(3), "Case 14: quest remains marked received (exactly-once preserved)");

        System.out.println("  [PASS] Case 14: Response send failure handled and logged gracefully without state corruption");
    }


    // =========================================================================
    // ROUND 3 ADDITIONAL TESTS
    // =========================================================================

    /** R3-1: closeClaims() blocks done()/doneNotAdd() from updating progress. */
    private static void testCaseR3_1_doneAndDoneNotAddAfterClosed() {
        Player player = createPlayerWithBag();
        player.achievement.doneNotAdd(3, 50);
        player.achievement.doneNotAdd(0, 7);
        int sizeBeforeClose = player.achievement.snapshotQuests().size();
        // Close lifecycle
        player.achievement.closeClaims();

        long completedBefore;
        // done() must not update after CLOSED
        player.achievement.done(3, 100);
        AchievementQuest aq3 = player.achievement.snapshotQuests().get(3);
        check(aq3.completed == 50, "R3-1: done() must not update progress after CLOSED (expected 50, got " + aq3.completed + ")");

        // doneNotAdd() must not update after CLOSED
        player.achievement.doneNotAdd(3, 999);
        AchievementQuest aq3b = player.achievement.snapshotQuests().get(3);
        check(aq3b.completed == 50, "R3-1: doneNotAdd() must not update progress after CLOSED (expected 50, got " + aq3b.completed + ")");

        // add() must not mutate the positional list after CLOSED.
        player.achievement.add(new AchievementQuest(999, true));
        check(player.achievement.snapshotQuests().size() == sizeBeforeClose,
                "R3-1: add() must not append quests after CLOSED");

        // Dynamic achievements must not update their stored progress after CLOSED.
        player.nPoint.power = 999_999;
        check(player.achievement.getCompleted(0) == 7,
                "R3-1: getCompleted() must not mutate dynamic progress after CLOSED");

        System.out.println("  [PASS] R3-1: all achievement mutations blocked after CLOSED");
    }

    /** R3-2: reward(int) is a no-op – neither sets isRecieve nor credits gem. */
    private static void testCaseR3_2_rewardIsNoOp() {
        Player player = createPlayerWithBag();
        player.achievement.doneNotAdd(3, 100);
        int gemBefore = player.inventory.gem;

        // Call the deprecated no-op
        @SuppressWarnings("deprecation")
        Runnable callReward = () -> player.achievement.reward(3);
        callReward.run();

        check(!player.achievement.isRecieve(3), "R3-2: reward() must not set isRecieve");
        check(player.inventory.gem == gemBefore, "R3-2: reward() must not change gem balance");
        // claimReward should still succeed (not already-claimed)
        ClaimResult r = player.achievement.claimReward(3);
        check(r.getStatus() == ClaimStatus.SUCCESS, "R3-2: claimReward must still succeed after no-op reward()");
        System.out.println("  [PASS] R3-2: reward(int) is a harmless no-op");
    }

    /** R3-3: Negative gem balance returns INVALID_BALANCE, gem unchanged, isRecieve stays false. */
    private static void testCaseR3_3_negativeGemBalance() {
        Player player = createPlayerWithBag();
        player.achievement.doneNotAdd(3, 100);
        player.inventory.gem = -50; // corrupt state

        ClaimResult result = player.achievement.claimReward(3);
        check(result.getStatus() == ClaimStatus.INVALID_BALANCE,
                "R3-3: negative gem balance must map to INVALID_BALANCE");
        check(player.inventory.gem == -50, "R3-3: gem must remain -50 (unchanged)");
        check(!player.achievement.isRecieve(3), "R3-3: isRecieve must remain false");

        Player overLimit = createPlayerWithBag();
        overLimit.achievement.doneNotAdd(3, 100);
        overLimit.inventory.gem = PlayerConfig.getMaxGem() + 1;
        ClaimResult overLimitResult = overLimit.achievement.claimReward(3);
        check(overLimitResult.getStatus() == ClaimStatus.INVALID_BALANCE,
                "R3-3: balance above maxGem must map to INVALID_BALANCE");
        check(overLimit.inventory.gem == PlayerConfig.getMaxGem() + 1,
                "R3-3: corrupt high balance must remain unchanged");
        check(!overLimit.achievement.isRecieve(3),
                "R3-3: corrupt high balance must not mark the quest received");

        System.out.println("  [PASS] R3-3: Corrupt gem balances rejected with INVALID_BALANCE");
    }

    /** R3-4: snapshotQuests() with a null element preserves index (no shrinkage). */
    private static void testCaseR3_4_snapshotPreservesNullIndex() throws Exception {
        // We need to inject a null into achievementList via reflection
        Player player = createPlayerWithBag();
        java.lang.reflect.Field f = Achievement.class.getDeclaredField("achievementList");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.List<AchievementQuest> list = (java.util.List<AchievementQuest>) f.get(player.achievement);
        list.set(2, null); // inject null at index 2

        java.util.List<AchievementQuest> snap = player.achievement.snapshotQuests();
        check(snap.size() == 4, "R3-4: snapshot size must equal original (4), got " + snap.size());
        check(snap.get(2) != null, "R3-4: null element must become a placeholder, not absent");
        check(snap.get(2).completed == 0, "R3-4: placeholder completed must be 0");
        check(!snap.get(2).isRecieve, "R3-4: placeholder isRecieve must be false");
        // other indices must be intact
        check(snap.get(0) != null, "R3-4: index 0 must not be shifted");
        check(snap.get(3) != null, "R3-4: index 3 must not be shifted");
        System.out.println("  [PASS] R3-4: snapshotQuests() preserves null element index with placeholder");
    }

    /** R3-5: getAchievementList() returns a defensive copy – mutations do not affect internal state. */
    private static void testCaseR3_5_getAchievementListDefensive() {
        Player player = createPlayerWithBag();
        player.achievement.doneNotAdd(3, 100);

        java.util.List<AchievementQuest> external = player.achievement.getAchievementList();
        check(external != null, "R3-5: getAchievementList must not return null");
        check(external.size() == 4, "R3-5: snapshot must have 4 elements");

        // Mutate the returned list
        external.get(3).isRecieve = true;
        external.get(3).completed = 9999;

        // Internal state must be unchanged
        check(!player.achievement.isRecieve(3), "R3-5: external mutation must not affect internal isRecieve");
        java.util.List<AchievementQuest> snap2 = player.achievement.snapshotQuests();
        check(snap2.get(3).completed == 100, "R3-5: external mutation must not affect internal completed (expected 100)");

        // get(index) must also return a defensive copy.
        AchievementQuest oneQuest = player.achievement.get(3);
        oneQuest.completed = 42_000;
        oneQuest.isRecieve = true;
        check(player.achievement.getCompleted(3) == 100,
                "R3-5: get(index) must not expose mutable internal progress");
        check(!player.achievement.isRecieve(3),
                "R3-5: get(index) must not expose mutable receive state");

        boolean listMutationRejected = false;
        try {
            external.add(new AchievementQuest(0, false));
        } catch (UnsupportedOperationException expected) {
            listMutationRejected = true;
        }
        check(listMutationRejected, "R3-5: getAchievementList() must return an immutable list");
        System.out.println("  [PASS] R3-5: getAchievementList() returns immutable defensive snapshot");
    }

    /** R3-6: Race between closeClaims and done()/doneNotAdd() – state always consistent after CLOSED. */
    private static void testCaseR3_6_raceCloseVsDone() throws Exception {
        final int ROUNDS = 200;
        for (int round = 0; round < ROUNDS; round++) {
            Player player = createPlayerWithBag();
            player.achievement.doneNotAdd(3, 50);

            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(2);

            Thread closeThread = new Thread(() -> {
                try { startGate.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                player.achievement.closeClaims();
                doneLatch.countDown();
            });
            Thread doneThread = new Thread(() -> {
                try { startGate.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                // Call both done variants rapidly
                for (int i = 0; i < 10; i++) {
                    player.achievement.done(3, 10);
                    player.achievement.doneNotAdd(3, 50);
                }
                doneLatch.countDown();
            });

            closeThread.start();
            doneThread.start();
            startGate.countDown();
            boolean finished = doneLatch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            check(finished, "R3-6 round " + round + ": threads did not finish within timeout");

            // After CLOSED, claimReward must fail
            ClaimResult r = player.achievement.claimReward(3);
            check(!r.isSuccess(), "R3-6 round " + round + ": claimReward must fail after CLOSED");
            // gem must be 0
            check(player.inventory.gem == 0, "R3-6 round " + round + ": gem must stay 0 after CLOSED");
        }
        System.out.println("  [PASS] R3-6: Race closeClaims vs done/doneNotAdd (" + ROUNDS + " rounds) – always consistent");
    }

    /** R3-7: concurrent progress writers/readers preserve every committed increment. */
    private static void testCaseR3_7_getCompletedUnderLock() throws Exception {
        Player player = createPlayerWithBag();
        player.achievement.doneNotAdd(3, 0);
        final int writerCount = 100;
        final int readerCount = 100;
        AtomicInteger errors = new AtomicInteger(0);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch latch = new CountDownLatch(writerCount + readerCount);
        for (int i = 0; i < writerCount; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    player.achievement.done(3, 1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        for (int i = 0; i < readerCount; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    long v = player.achievement.getCompleted(3);
                    if (v < 0 || v > writerCount) {
                        errors.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        start.countDown();
        boolean done = latch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        pool.shutdown();
        check(done, "R3-7: concurrent getCompleted threads timed out");
        check(errors.get() == 0, "R3-7: readers must only observe valid committed values");
        check(player.achievement.getCompleted(3) == writerCount,
                "R3-7: no concurrent progress increment may be lost");
        System.out.println("  [PASS] R3-7: concurrent progress reads/writes preserve committed state");
    }

    /** R3-8: dispose state transition uses the same monitor as gem credit. */
    private static void testCaseR3_8_inventoryDisposeRace() throws Exception {
        Player player = createPlayerWithBag();
        Inventory inventory = player.inventory;
        CountDownLatch disposeStarted = new CountDownLatch(1);
        CountDownLatch disposeFinished = new CountDownLatch(1);
        Thread disposeThread = new Thread(() -> {
            disposeStarted.countDown();
            inventory.dispose();
            disposeFinished.countDown();
        });

        synchronized (inventory) {
            disposeThread.start();
            check(disposeStarted.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "R3-8: dispose thread did not start");
            check(!disposeFinished.await(100, TimeUnit.MILLISECONDS),
                    "R3-8: dispose must wait for the inventory monitor");
        }
        check(disposeFinished.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "R3-8: dispose did not finish after monitor release");
        check(!inventory.isActive(), "R3-8: inventory must be inactive after dispose");
        Inventory.GemCreditResult afterDispose = inventory.tryCreditGemExact(100);
        check(afterDispose.status == Inventory.GemCreditStatus.INACTIVE,
                "R3-8: credit after dispose must return INACTIVE");
        check(inventory.gem == 0, "R3-8: credit after dispose must not change gem");
        System.out.println("  [PASS] R3-8: dispose and credit share one lifecycle monitor");
    }

    /** R3-9: Player.dispose() closes claims before any cleanup step can throw. */
    private static void testCaseR3_9_playerDisposeOrder() {
        Player player = createPlayerWithBag();
        player.achievement.doneNotAdd(3, 100);
        Achievement retainedAchievement = player.achievement;
        player.itemsTradeWVP = new java.util.AbstractList<Item>() {
            @Override
            public Item get(int index) {
                throw new IndexOutOfBoundsException(index);
            }

            @Override
            public int size() {
                throw new RuntimeException("Simulated early Player.dispose cleanup failure");
            }
        };

        boolean cleanupFailed = false;
        try {
            player.dispose();
        } catch (RuntimeException expected) {
            cleanupFailed = true;
        }
        check(cleanupFailed, "R3-9: test precondition must trigger an early cleanup failure");
        check(!retainedAchievement.isAcceptingClaims(),
                "R3-9: Player.dispose() must close claims before early cleanup failure");
        check(retainedAchievement.claimReward(3).getStatus() == ClaimStatus.PLAYER_UNAVAILABLE,
                "R3-9: retained achievement must reject claims after failed dispose");

        Player normalPlayer = createPlayerWithBag();
        normalPlayer.dispose();
        check(normalPlayer.achievement == null, "R3-9: achievement must be null after successful dispose");
        check(normalPlayer.inventory == null, "R3-9: inventory must be null after successful dispose");
        System.out.println("  [PASS] R3-9: Player.dispose() closes lifecycle before fallible cleanup");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError("ASSERTION FAILED: " + message);
        }
    }
}
