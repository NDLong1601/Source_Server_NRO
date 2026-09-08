package nro.models.server.dispatch;

import java.util.Objects;

public record CommandDefinition(CommandKey key, CommandDomain domain, CommandPolicy policy) {

    public CommandDefinition {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(policy, "policy");
    }

    public static CommandDefinition session(int command, CommandDomain domain) {
        return new CommandDefinition(CommandKey.of(command), domain, CommandPolicy.SESSION);
    }

    public static CommandDefinition player(int command, CommandDomain domain) {
        return new CommandDefinition(CommandKey.of(command), domain, CommandPolicy.PLAYER_REQUIRED);
    }
}
