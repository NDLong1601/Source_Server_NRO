package nro.models.server.dispatch;

public enum CommandPolicy {
    SESSION {
        @Override
        boolean allows(CommandContext context) {
            return true;
        }
    },
    PLAYER_REQUIRED {
        @Override
        boolean allows(CommandContext context) {
            return context.player() != null;
        }
    };

    abstract boolean allows(CommandContext context);
}
