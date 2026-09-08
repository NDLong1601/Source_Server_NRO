package nro.models.server.dispatch;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import nro.models.network.Message;
import nro.models.network.MySession;
import nro.models.server.Controller;
import nro.models.server.ServerRuntimeMetrics;

public final class Gate3CommandDispatcherTest {

    private static final int[] LEGACY_COMMANDS = {
        -100, 127, -105, 42, -127, -125, 112, -34, -99, 18, -72, -80, -59, -58,
        -86, -107, -108, 6, 7, 29, 21, -71, -79, -113, -101, -103, -7, -74,
        -81, -87, -67, 66, -66, -62, -63, -32, 22, -33, -23, -45, -46, -51,
        -54, -49, -50, -56, -47, -55, -57, -40, -41, -43, -91, -39, 11, 44,
        32, 33, 34, 54, -60, -27, -111, -20, -28, -29, -30, -15, -16, -104,
        -118, -38, 126, -78, -114, 27, -76, -48
    };

    private Gate3CommandDispatcherTest() {
    }

    public static void main(String[] args) throws Exception {
        testCommandKeyUsesSignedWireByte();
        testDuplicateRegistrationFailsClosed();
        testUnknownCommandUsesLegacyFallback();
        testPlayerPolicyRejectsBeforeHandler();
        testProductionRegistryOwnsEveryLegacyCommandExactlyOnce();
        testProtocolErrorLimiterIsPerSessionAndWindowed();
        testAssetNameBoundaryRejectsPathTraversal();
        testControllerClassifiesUnknownAndMalformedPackets();
        System.out.println("GATE3_COMMAND_DISPATCHER_OK");
    }

    private static void testCommandKeyUsesSignedWireByte() {
        check(CommandKey.of(-127).wireValue() == (byte) -127,
                "CommandKey must preserve the signed protocol byte");
        check(CommandKey.of(129).equals(CommandKey.of(-127)),
                "CommandKey must normalize integer inputs to one wire byte");
    }

    private static void testDuplicateRegistrationFailsClosed() {
        CommandDispatcher dispatcher = new CommandDispatcher(context -> { });
        dispatcher.register(CommandDefinition.player(-76, CommandDomain.ECONOMY), context -> { });
        boolean rejected = false;
        try {
            dispatcher.register(CommandDefinition.player(-76, CommandDomain.ECONOMY), context -> { });
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        check(rejected, "Duplicate command ownership must be rejected at startup");
    }

    private static void testUnknownCommandUsesLegacyFallback() throws Exception {
        AtomicInteger fallbacks = new AtomicInteger();
        CommandDispatcher dispatcher = new CommandDispatcher(context -> fallbacks.incrementAndGet());
        DispatchResult result = dispatcher.dispatch(context(99, null));
        check(result == DispatchResult.LEGACY_FALLBACK, "Unknown command must use the legacy fallback");
        check(fallbacks.get() == 1, "Legacy fallback must execute exactly once");
    }

    private static void testPlayerPolicyRejectsBeforeHandler() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        CommandDispatcher dispatcher = new CommandDispatcher(context -> { });
        dispatcher.register(CommandDefinition.player(6, CommandDomain.INVENTORY_SHOP),
                context -> executions.incrementAndGet());
        DispatchResult result = dispatcher.dispatch(context(6, null));
        check(result == DispatchResult.POLICY_REJECTED, "Player-required command must be rejected pre-login");
        check(executions.get() == 0, "Rejected command must not reach its handler");
    }

    private static void testProductionRegistryOwnsEveryLegacyCommandExactlyOnce() {
        CommandDispatcher dispatcher = ControllerCommandRegistry.create();
        Set<CommandKey> expected = new HashSet<>();
        for (int command : LEGACY_COMMANDS) {
            check(expected.add(CommandKey.of(command)), "Legacy command inventory contains a duplicate: " + command);
            CommandRegistration registration = dispatcher.registration(command);
            check(registration != null, "Missing Gate 3 owner for command " + command);
            check(registration.domain() != CommandDomain.LEGACY,
                    "Known command must not remain on legacy fallback: " + command);
        }
        check(dispatcher.registrationCount() == expected.size(),
                "Registry must contain exactly the frozen 78-command inventory");
        check(dispatcher.registration(-27).domain() == CommandDomain.AUTH_ASSET,
                "Handshake command must be owned by auth/asset");
        check(dispatcher.registration(-100).domain() == CommandDomain.ECONOMY,
                "Consignment command must be owned by economy");
        check(dispatcher.registration(6).domain() == CommandDomain.INVENTORY_SHOP,
                "Shop buy command must be owned by inventory/shop");
        check(dispatcher.registration(54).domain() == CommandDomain.WORLD_COMBAT,
                "Mob attack command must be owned by world/combat");
        check(dispatcher.registration(-46).domain() == CommandDomain.SOCIAL_CLAN_ACTIVITY,
                "Clan command must be owned by social/clan/activity");
    }

    private static void testProtocolErrorLimiterIsPerSessionAndWindowed() {
        ProtocolErrorRateLimiter limiter = new ProtocolErrorRateLimiter(2, 1_000L);
        Object sessionA = new Object();
        Object sessionB = new Object();
        check(limiter.tryAcquire(sessionA, 100L), "First session error must be reportable");
        check(limiter.tryAcquire(sessionA, 200L), "Second session error must be reportable");
        check(!limiter.tryAcquire(sessionA, 300L), "Session must be rate limited after its allowance");
        check(limiter.tryAcquire(sessionB, 300L), "One noisy session must not suppress another session");
        check(limiter.tryAcquire(sessionA, 1_101L), "Allowance must reset after the window");
    }

    private static void testAssetNameBoundaryRejectsPathTraversal() {
        check(AssetRequestPolicy.isSafeImageName("aura_77_1"),
                "Existing alphanumeric/underscore asset names must remain valid");
        check(!AssetRequestPolicy.isSafeImageName("../config"), "Parent traversal must be rejected");
        check(!AssetRequestPolicy.isSafeImageName("..\\config"), "Windows traversal must be rejected");
        check(!AssetRequestPolicy.isSafeImageName("C:\\secret"), "Absolute paths must be rejected");
        check(!AssetRequestPolicy.isSafeImageName(""), "Empty asset names must be rejected");
        check(!AssetRequestPolicy.isSafeImageName("a".repeat(65)), "Asset names must be length bounded");
    }

    private static void testControllerClassifiesUnknownAndMalformedPackets() {
        ServerRuntimeMetrics metrics = ServerRuntimeMetrics.gI();
        metrics.reset();
        Controller controller = new Controller();
        MySession session = new MySession();

        controller.onMessage(session, new Message((byte) 100, new byte[0]));
        check(metrics.getProtocolErrorCount(ProtocolErrorType.UNKNOWN_COMMAND) == 1,
                "Unknown command must be measured without escaping the session boundary");

        controller.onMessage(session, new Message((byte) -67, new byte[0]));
        check(metrics.getProtocolErrorCount(ProtocolErrorType.MALFORMED_PACKET) == 1,
                "Truncated payload must be classified as malformed without escaping dispatch");
    }

    private static CommandContext context(int command, nro.models.player.Player player) {
        MySession session = new MySession();
        session.player = player;
        return new CommandContext(session, new Message((byte) command, new byte[0]));
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
