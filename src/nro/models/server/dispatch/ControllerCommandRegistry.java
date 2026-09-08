package nro.models.server.dispatch;

public final class ControllerCommandRegistry {

    private ControllerCommandRegistry() {
    }

    public static CommandDispatcher create() {
        return create(new AuthAssetCommandHandler(), new EconomyCommandHandler(),
                new InventoryShopCommandHandler(), new WorldCombatCommandHandler(),
                new SocialClanActivityCommandHandler());
    }

    public static CommandDispatcher create(
            AuthAssetCommandHandler authAsset,
            EconomyCommandHandler economy,
            InventoryShopCommandHandler inventoryShop,
            WorldCombatCommandHandler worldCombat,
            SocialClanActivityCommandHandler socialClanActivity) {
        CommandDispatcher dispatcher = new CommandDispatcher(new LegacyFallbackCommandHandler());

        registerSession(dispatcher, authAsset, CommandDomain.AUTH_ASSET,
                42, -74, -87, -67, 66, -32, -41, 11, -27, -111, -28, -29, -30, -101);
        registerPlayer(dispatcher, authAsset, CommandDomain.AUTH_ASSET,
                -66, -62, -63, -38);

        registerPlayer(dispatcher, economy, CommandDomain.ECONOMY,
                -100, -127, -86, -76);

        registerPlayer(dispatcher, inventoryShop, CommandDomain.INVENTORY_SHOP,
                -125, 112, -34, -107, -108, 6, 7, -79, -113, -103, -81, -40, -43, 32, 33);

        registerPlayer(dispatcher, worldCombat, CommandDomain.WORLD_COMBAT,
                -105, 29, 21, -7, 22, -33, -23, -45, -91, -39, 34, 54, -60,
                -20, -15, -16, -104, -118, 126);
        registerSession(dispatcher, worldCombat, CommandDomain.WORLD_COMBAT,
                -78, -114, 27);

        registerPlayer(dispatcher, socialClanActivity, CommandDomain.SOCIAL_CLAN_ACTIVITY,
                127, -99, 18, -72, -80, -59, -58, -71, -46, -51, -54, -49, -50,
                -56, -47, -55, -57, 44, -48);

        return dispatcher;
    }

    private static void registerSession(CommandDispatcher dispatcher, CommandHandler handler,
            CommandDomain domain, int... commands) {
        for (int command : commands) {
            dispatcher.register(CommandDefinition.session(command, domain), handler);
        }
    }

    private static void registerPlayer(CommandDispatcher dispatcher, CommandHandler handler,
            CommandDomain domain, int... commands) {
        for (int command : commands) {
            dispatcher.register(CommandDefinition.player(command, domain), handler);
        }
    }
}
