package nro.models.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import nro.models.clan.Clan;
import nro.models.item.Item.ItemOption;
import nro.models.player_system.Template.ItemOptionTemplate;
import nro.models.player_system.Template.ItemTemplate;
import nro.models.shop_ky_gui.ConsignItem;

public final class Gate4RuntimeOwnershipTest {

    private Gate4RuntimeOwnershipTest() {
    }

    public static void main(String[] args) throws Exception {
        immutableConfigIsValidated();
        templateSnapshotsPublishAtomically();
        invalidTemplateReloadKeepsPreviousSnapshot();
        stagedConsignOptionsDoNotReadLiveRegistry();
        dynamicRegistriesExposeSnapshotsOnly();
        worldRegistrySupportsSparseMapIds();
        clanRegistrySupportsConcurrentAdds();
        leaderboardDirtyStateHasOneOwner();
        tickEngineReportsHealthAndShutsDown();
        System.out.println("GATE4_RUNTIME_OWNERSHIP_OK");
    }

    private static void immutableConfigIsValidated() {
        Properties properties = new Properties();
        properties.setProperty("server.sv", "2");
        properties.setProperty("server.name", "Gate 4");
        properties.setProperty("server.ip", "127.0.0.1");
        properties.setProperty("server.port", "14445");
        properties.setProperty("server.waitlogin", "4");
        properties.setProperty("server.maxperip", "8");
        properties.setProperty("server.maxplayer", "100");
        properties.setProperty("server.expserver", "3");
        properties.setProperty("server.sender.max_messages", "256");
        properties.setProperty("server.sender.max_bytes", "1048576");

        ServerConfig config = ServerConfig.from(properties);
        check(config.serverId() == 2, "server id was not parsed");
        check(config.port() == 14445, "port was not parsed");
        check(config.maxConnectionsPerIp() == 8, "max-per-ip was not parsed");

        properties.setProperty("server.port", "70000");
        expectThrows(IllegalArgumentException.class, () -> ServerConfig.from(properties));
    }

    private static void templateSnapshotsPublishAtomically() throws Exception {
        TemplateRegistry registry = new TemplateRegistry();
        DataBundle first = bundle(1, "first", 1);
        DataBundle second = bundle(2, "second", 2);
        registry.publish(first);

        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger mixedSnapshots = new AtomicInteger();
        Thread reader = new Thread(() -> {
            await(start);
            for (int i = 0; i < 20_000; i++) {
                TemplateRegistry.Snapshot snapshot = registry.snapshot();
                int size = snapshot.itemTemplates().size();
                if (!((snapshot.revision() == 1 && size == 1)
                        || (snapshot.revision() == 2 && size == 2))) {
                    mixedSnapshots.incrementAndGet();
                }
            }
        }, "Gate4RegistryReader");
        reader.start();
        start.countDown();
        registry.publish(second);
        reader.join(2_000);

        check(!reader.isAlive(), "registry reader did not finish");
        check(mixedSnapshots.get() == 0, "reader observed a partial template snapshot");
        expectThrows(UnsupportedOperationException.class,
                () -> registry.snapshot().itemTemplates().add(new ItemTemplate()));
    }

    private static void invalidTemplateReloadKeepsPreviousSnapshot() {
        TemplateRegistry registry = new TemplateRegistry();
        registry.publish(bundle(7, "stable", 1));

        ItemTemplate invalid = new ItemTemplate();
        invalid.id = 4;
        DataBundle broken = DataBundle.forTesting(8, List.of(invalid), List.of(), List.of("broken"));
        expectThrows(IllegalArgumentException.class, () -> registry.publish(broken));

        check(registry.snapshot().revision() == 7, "failed reload replaced the live snapshot");
        check("stable".equals(registry.snapshot().notifications().get(0)),
                "failed reload leaked partial data");
    }

    private static void stagedConsignOptionsDoNotReadLiveRegistry() {
        ItemOptionTemplate stagedTemplate = new ItemOptionTemplate(73, "staged", 0);
        ItemOption stagedOption = new ItemOption(stagedTemplate, 11);

        ConsignItem listing = new ConsignItem(1, (short) 1, 2, (byte) 0,
                3, 0, 1, (byte) 0, List.of(stagedOption), false);

        check(listing.options.size() == 1, "staged option was dropped while copying");
        check(listing.options.get(0) != stagedOption, "staged option was not defensively copied");
        check(listing.options.get(0).optionTemplate == stagedTemplate,
                "staged option copy consulted the unpublished live registry");
    }

    private static void dynamicRegistriesExposeSnapshotsOnly() {
        ClanRegistry clans = new ClanRegistry();
        Clan clan = new Clan();
        clan.id = 42;
        clans.add(clan);
        check(clans.find(42).orElseThrow() == clan, "clan lookup failed");
        expectThrows(UnsupportedOperationException.class, () -> clans.snapshot().clear());
        check(clans.remove(clan), "clan removal failed");

        WorldRegistry worlds = new WorldRegistry();
        worlds.publish(List.of());
        expectThrows(UnsupportedOperationException.class, () -> worlds.snapshot().clear());
    }

    private static void worldRegistrySupportsSparseMapIds() {
        WorldRegistry worlds = new WorldRegistry();
        nro.models.map.Map first = testMap(0);
        nro.models.map.Map sparse = testMap(151);

        worlds.publish(List.of(first, sparse));

        check(worlds.snapshot().equals(List.of(first, sparse)),
                "world iteration snapshot did not preserve the published worlds");
        check(worlds.get(0) == first, "world ID 0 lookup failed");
        check(worlds.get(151) == sparse, "sparse world ID lookup failed");
        check(worlds.get(1) == null, "missing world ID did not return null");
    }

    private static nro.models.map.Map testMap(int id) {
        return new nro.models.map.Map(id, "map-" + id, (byte) 0, (byte) 1,
                (byte) 0, (byte) 0, (byte) 0, new int[][]{{0}}, new int[0],
                0, 0, List.of());
    }

    private static void clanRegistrySupportsConcurrentAdds() throws Exception {
        ClanRegistry clans = new ClanRegistry();
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> writers = new ArrayList<>();
        for (int id = 0; id < 64; id++) {
            int clanId = id;
            Thread writer = new Thread(() -> {
                await(start);
                Clan clan = new Clan();
                clan.id = clanId;
                check(clans.add(clan), "concurrent clan add was rejected");
            }, "Gate4ClanWriter-" + id);
            writers.add(writer);
            writer.start();
        }
        start.countDown();
        for (Thread writer : writers) writer.join(2_000);
        check(clans.size() == 64, "concurrent clan writes were lost");
        check(clans.snapshot().size() == 64, "clan snapshot is incomplete");
    }

    private static void leaderboardDirtyStateHasOneOwner() {
        LeaderboardService service = new LeaderboardService();
        service.markDirty(LeaderboardService.Board.EVENT_ONE);
        check(service.hasDirtyBoards(), "leaderboard dirty flag was lost");
        check(service.dirtyBoards().equals(List.of(LeaderboardService.Board.EVENT_ONE)),
                "leaderboard dirty snapshot was incorrect");
        service.clearDirty(LeaderboardService.Board.EVENT_ONE);
        check(!service.hasDirtyBoards(), "leaderboard dirty flag was not cleared");
    }

    private static void tickEngineReportsHealthAndShutsDown() throws Exception {
        AtomicInteger ticks = new AtomicInteger();
        CountDownLatch ticked = new CountDownLatch(1);
        WorldTickEngine engine = WorldTickEngine.forTest(() -> {
            ticks.incrementAndGet();
            ticked.countDown();
        }, 10);

        engine.start();
        check(ticked.await(2, TimeUnit.SECONDS), "tick engine did not execute");
        check(engine.health().running(), "tick engine health did not report running");
        engine.shutdown();
        int countAtShutdown = ticks.get();
        Thread.sleep(50);
        check(!engine.health().running(), "tick engine health did not report shutdown");
        check(ticks.get() == countAtShutdown, "tick engine continued after shutdown");
    }

    private static DataBundle bundle(long revision, String notification, int itemCount) {
        List<ItemTemplate> items = new ArrayList<>();
        for (short id = 0; id < itemCount; id++) {
            ItemTemplate item = new ItemTemplate();
            item.id = id;
            items.add(item);
        }
        return DataBundle.forTesting(revision, items, List.<ItemOptionTemplate>of(), List.of(notification));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void expectThrows(Class<? extends Throwable> expected, Runnable action) {
        try {
            action.run();
        } catch (Throwable error) {
            if (expected.isInstance(error)) {
                return;
            }
            throw new AssertionError("Expected " + expected.getSimpleName() + " but got " + error, error);
        }
        throw new AssertionError("Expected " + expected.getSimpleName());
    }
}
