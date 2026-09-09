package nro.models.server;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import nro.models.player_system.Template.AchievementTemplate;
import nro.models.player_system.Template.ItemOptionTemplate;
import nro.models.player_system.Template.ItemTemplate;
import nro.models.data.DataGame;
import nro.models.network.Sender;
import nro.models.npc.NonInteractiveNPC;
import nro.models.npc.NpcFactory;
import nro.models.utils.Logger;

/** Composition root for configuration, registries, and owned runtime services. */
public final class GameRuntime {

    private static volatile GameRuntime instance;

    private final TemplateRegistry templates = new TemplateRegistry();
    private final WorldRegistry worlds = new WorldRegistry();
    private final ClanRegistry clans = new ClanRegistry();
    private final NpcRegistry npcs = new NpcRegistry();
    private final LeaderboardService leaderboards = new LeaderboardService();
    private final WorldTickEngine tickEngine = new WorldTickEngine(worlds);
    private volatile PlayerAutosaveService playerAutosave;
    private volatile ServerConfig config;
    private volatile boolean initializing;
    private volatile Thread initializingThread;
    private volatile boolean initialized;

    private GameRuntime() {
    }

    public static GameRuntime gI() {
        GameRuntime current = instance;
        if (current != null && current.initialized) return current;
        if (current != null && current.initializingThread == Thread.currentThread()) return current;
        synchronized (GameRuntime.class) {
            if (instance == null) instance = new GameRuntime();
            if (!instance.initialized && !instance.initializing) {
                try {
                    instance.initialize();
                } catch (RuntimeException error) {
                    instance = null;
                    throw error;
                }
            }
            return instance;
        }
    }

    private void initialize() {
        initializing = true;
        initializingThread = Thread.currentThread();
        try {
        try {
            config = ServerConfig.load(Path.of("Config.properties"));
        } catch (IOException | IllegalArgumentException error) {
            throw new IllegalStateException("Cannot load validated server configuration", error);
        }
        applyCompatibilityConfig(config);

        DataBundle bundle = new TemplateDataLoader(leaderboards).load();
        templates.publish(bundle);
        clans.publish(bundle.clans());

        npcs.beginStaging();
        try {
            worlds.publish(new WorldFactory().create(bundle.mapTemplates()));
            npcs.publishStaged();
        } catch (RuntimeException error) {
            npcs.abortStaging();
            throw error;
        }
        NpcFactory.createNpcConMeo();
        NpcFactory.createNpcRongThieng();
        new NonInteractiveNPC().initNonInteractiveNPC();
        tickEngine.start();
        playerAutosave = new PlayerAutosaveService(
                () -> Client.gI().getPlayers(), config.playerAutosavePolicy());
        playerAutosave.start();
        initialized = true;
        Logger.success(Logger.RED + "Gate 4 runtime registries published at revision "
                + bundle.revision() + "\n");
        } finally {
            initializingThread = null;
            initializing = false;
        }
    }

    private static void applyCompatibilityConfig(ServerConfig value) {
        ServerManager.NAME = value.name();
        ServerManager.IP = value.ip();
        ServerManager.PORT = value.port();
        DataGame.LINK_IP_PORT = value.serverLinks();
        Sender.configureDefaultLimits(value.senderMaxMessages(), value.senderMaxBytes());
    }

    public ServerConfig config() { return config; }
    public TemplateRegistry templates() { return templates; }
    public WorldRegistry worlds() { return worlds; }
    public ClanRegistry clans() { return clans; }
    public NpcRegistry npcs() { return npcs; }
    public LeaderboardService leaderboards() { return leaderboards; }
    public WorldTickEngine tickEngine() { return tickEngine; }
    public PlayerAutosaveService playerAutosave() { return playerAutosave; }

    public void shutdown() {
        if (playerAutosave != null) playerAutosave.shutdown();
        tickEngine.shutdown();
    }

    /** Isolated test seam used by repository regression programs. */
    public static synchronized void installTemplatesForTesting(List<ItemTemplate> items,
            List<ItemOptionTemplate> options, List<AchievementTemplate> achievements) {
        if (ServerManager.isRunning) {
            throw new IllegalStateException("Cannot replace templates while the server is running");
        }
        GameRuntime testRuntime = new GameRuntime();
        testRuntime.config = ServerConfig.from(new Properties());
        testRuntime.templates.publish(DataBundle.forTesting(1, items, options, achievements, List.of()));
        testRuntime.initialized = true;
        instance = testRuntime;
    }

    public static synchronized void installAchievementsForTesting(List<AchievementTemplate> achievements) {
        List<ItemTemplate> items = instance == null
                ? List.of() : instance.templates.itemTemplates();
        List<ItemOptionTemplate> options = instance == null
                ? List.of() : instance.templates.itemOptionTemplates();
        installTemplatesForTesting(items, options, achievements);
    }
}
