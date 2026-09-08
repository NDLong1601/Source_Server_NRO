package nro.models.server.dispatch;

import java.util.Objects;

public record CommandRegistration(CommandDefinition definition, CommandHandler handler) {

    public CommandRegistration {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(handler, "handler");
    }

    public CommandKey key() {
        return definition.key();
    }

    public CommandDomain domain() {
        return definition.domain();
    }

    public CommandPolicy policy() {
        return definition.policy();
    }
}
