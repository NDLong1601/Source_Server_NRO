package nro.models.player;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import nro.models.consts.ConstPlayer;
import nro.models.database.PlayerDAO;
import nro.models.database.PlayerRepository;
import nro.models.server.GameRuntime;

public final class Gate5PlayerArchitectureTest {

    private Gate5PlayerArchitectureTest() {
    }

    public static void main(String[] args) throws Exception {
        GameRuntime.installTemplatesForTesting(java.util.List.of(), java.util.List.of(), java.util.List.of());
        lifecycleTransitionsAreIdempotent();
        tickPipelineRejectsOverlapAndRemoval();
        dirtyAcknowledgementDoesNotLoseConcurrentMutation();
        inventoryTransactionRollsBackOnFailure();
        eventStateReadsLegacyAndWritesVersionedJson();
        autosavePolicyBoundsRecoveryPoint();
        appearanceIsSafeForIncompleteFusionState();
        snapshotIsImmutableAndVersioned();
        mismatchedSnapshotFailsClosed();
        System.out.println("GATE5_PLAYER_ARCHITECTURE_OK");
    }

    private static void lifecycleTransitionsAreIdempotent() {
        PlayerLifecycleState lifecycle = new PlayerLifecycleState();
        check(lifecycle.phase() == PlayerLifecycleState.Phase.ACTIVE, "new lifecycle is not active");
        check(lifecycle.beginRemoval(), "first removal was rejected");
        check(!lifecycle.beginRemoval(), "duplicate removal was accepted");
        check(!lifecycle.permitsTick(), "removing lifecycle still permits ticks");
        check(lifecycle.beginDisposal(), "disposal after removal was rejected");
        lifecycle.markDisposed();
        check(lifecycle.phase() == PlayerLifecycleState.Phase.DISPOSED, "lifecycle did not finish");
        check(!lifecycle.beginDisposal(), "duplicate disposal was accepted");
    }

    private static void tickPipelineRejectsOverlapAndRemoval() throws Exception {
        PlayerLifecycleState lifecycle = new PlayerLifecycleState();
        AtomicInteger executions = new AtomicInteger();
        AtomicInteger overlaps = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        PlayerTickPipeline pipeline = new PlayerTickPipeline(lifecycle, overlaps::incrementAndGet);

        Thread owner = new Thread(() -> pipeline.tick(() -> {
            executions.incrementAndGet();
            entered.countDown();
            await(release);
        }), "gate5-owner");
        owner.start();
        check(entered.await(2, TimeUnit.SECONDS), "owner tick did not start");
        pipeline.tick(executions::incrementAndGet);
        release.countDown();
        owner.join(2_000);

        check(executions.get() == 1, "overlapping tick executed");
        check(overlaps.get() == 1, "overlap metric was not recorded");
        lifecycle.beginRemoval();
        pipeline.tick(executions::incrementAndGet);
        check(executions.get() == 1, "tick executed during removal");
    }

    private static void dirtyAcknowledgementDoesNotLoseConcurrentMutation() {
        PlayerPersistenceState persistence = new PlayerPersistenceState();
        persistence.initializeLoaded(7L);
        persistence.markDirty(PlayerPersistenceComponent.INVENTORY);
        PlayerPersistenceToken captured = persistence.captureToken();
        persistence.markDirty(PlayerPersistenceComponent.INVENTORY);
        persistence.acknowledge(captured, 8L);

        check(persistence.saveVersion() == 8L, "save version was not advanced");
        check(persistence.dirtyComponents().equals(EnumSet.of(PlayerPersistenceComponent.INVENTORY)),
                "concurrent inventory mutation was incorrectly acknowledged");
        PlayerPersistenceToken latest = persistence.captureToken();
        persistence.acknowledge(latest, 9L);
        check(!persistence.isDirty(), "stable dirty component was not acknowledged");
    }

    private static void eventStateReadsLegacyAndWritesVersionedJson() {
        PlayerEventState legacy = PlayerEventState.fromJson("[3,4,5,6,7,8,true,false,true,false]");
        check(legacy.eventPoint(0) == 3 && legacy.eventPoint(5) == 8, "legacy event points changed");
        check(legacy.dailyRewardClaimed() && legacy.topRewardClaimed(1), "legacy reward flags changed");

        legacy.setFreeGemClaimAvailable(false);
        legacy.setClanCapsuleClaimAvailable(false);
        legacy.setLastCheckIn(LocalDateTime.of(2026, 9, 9, 12, 30));
        String versioned = legacy.toJson();
        check(versioned.contains("\"schemaVersion\":1"), "event JSON is not versioned");
        PlayerEventState roundTrip = PlayerEventState.fromJson(versioned);
        check(!roundTrip.isFreeGemClaimAvailable() && !roundTrip.isClanCapsuleClaimAvailable(),
                "event quota changed during round trip");
        check(legacy.equals(roundTrip), "event state changed during JSON round trip");
    }

    private static void inventoryTransactionRollsBackOnFailure() {
        Player player = new Player();
        player.id = 77L;
        player.inventory.gold = 100L;
        player.inventory.gem = 7;
        try {
            PlayerInventoryTransaction.execute(player, () -> {
                player.inventory.gold = 1L;
                player.inventory.gem = 0;
                throw new IllegalStateException("rollback");
            });
            throw new AssertionError("failed inventory transaction was accepted");
        } catch (IllegalStateException expected) {
            check("rollback".equals(expected.getMessage()), "unexpected inventory failure");
        }
        check(player.inventory.gold == 100L && player.inventory.gem == 7,
                "inventory transaction did not restore wallet state");
    }

    private static void autosavePolicyBoundsRecoveryPoint() {
        PlayerAutosavePolicy policy = new PlayerAutosavePolicy(60_000L, 5_000L, 5);
        long playerId = 42L;
        long due = policy.firstDueAt(playerId, 1_000_000L);
        check(due >= 1_000_000L && due <= 1_060_000L, "stagger exceeded configured RPO");
        check(policy.isDue(due, due), "autosave was not due at its deadline");
        check(policy.retryDelayMillis(1) == 5_000L, "first retry delay changed");
        check(policy.retryDelayMillis(20) <= 60_000L, "retry backoff exceeded RPO");
    }

    private static void appearanceIsSafeForIncompleteFusionState() {
        Player player = new Player();
        player.isPlayer = true;
        player.fusion.typeFusion = ConstPlayer.LUONG_LONG_NHAT_THE;
        short head = player.getHead();
        short body = player.getBody();
        short leg = player.getLeg();
        check(head >= -1 && body >= -1 && leg >= -1, "appearance returned invalid sentinel");

        player.pet = new Pet(player);
        for (byte fusionType : new byte[]{ConstPlayer.LUONG_LONG_NHAT_THE, ConstPlayer.HOP_THE_PORATA,
                ConstPlayer.HOP_THE_PORATA2, ConstPlayer.HOP_THE_PORATA3, ConstPlayer.HOP_THE_GOGETA}) {
            player.fusion.typeFusion = fusionType;
            player.getHead();
            player.getBody();
            player.getLeg();
        }
        player.fusion = null;
        player.getHead();
        player.getBody();
        player.getLeg();
        check(player.getAura() == -1 && player.getEffFront() == -1
                && player.getFlagBag() == -1 && player.getMount() == -1,
                "incomplete appearance state did not use safe sentinels");
    }

    private static void snapshotIsImmutableAndVersioned() {
        PlayerPersistenceToken token = new PlayerPersistenceToken(11L,
                Map.of(PlayerPersistenceComponent.CORE, 3L));
        Object[] values = new Object[]{"name", 9L, 11L};
        PlayerSnapshot snapshot = new PlayerSnapshot(9L, "snapshot", false, 11L,
                1, "UPDATE player SET name=?,save_version=save_version+1 WHERE id=? AND save_version=?",
                values, token,
                new PlayerWalletSnapshot(10L, 20, 30, 40), null);
        values[0] = "mutated";
        Object[] copy = snapshot.parameters();
        check("name".equals(copy[0]), "snapshot retained caller-owned parameter array");
        copy[0] = "again";
        check("name".equals(snapshot.parameters()[0]), "snapshot exposed mutable parameter array");
        check(snapshot.expectedSaveVersion() == 11L && snapshot.schemaVersion() == 1,
                "snapshot version metadata changed");
    }

    private static void mismatchedSnapshotFailsClosed() {
        Player source = new Player();
        source.id = 10L;
        source.name = "source";
        source.getPersistenceState().initializeLoaded(11L);
        PlayerPersistenceToken token = new PlayerPersistenceToken(11L,
                Map.of(PlayerPersistenceComponent.CORE, 3L));
        PlayerSnapshot mismatched = new PlayerSnapshot(9L, "other", false, 11L,
                1, "UPDATE player SET name=?,save_version=save_version+1 WHERE id=? AND save_version=?",
                new Object[]{"other", 9L, 11L}, token,
                new PlayerWalletSnapshot(10L, 20, 30, 40), null);

        PlayerDAO.SaveResult result = PlayerDAO.persistCaptured(source, mismatched,
                new PlayerRepository());

        check(result == PlayerDAO.SaveResult.OPTIMISTIC_CONFLICT,
                "mismatched snapshot was not rejected");
        check(source.isPersistenceQuarantined(),
                "mismatched snapshot did not quarantine persistence");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
