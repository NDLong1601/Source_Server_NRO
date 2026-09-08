package nro.models.server.dispatch;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class CommandDispatcher {

    private final Map<CommandKey, CommandRegistration> registrations = new LinkedHashMap<>();
    private final CommandHandler legacyFallback;

    public CommandDispatcher(CommandHandler legacyFallback) {
        this.legacyFallback = Objects.requireNonNull(legacyFallback, "legacyFallback");
    }

    public void register(CommandDefinition definition, CommandHandler handler) {
        CommandRegistration registration = new CommandRegistration(definition, handler);
        CommandRegistration previous = registrations.putIfAbsent(definition.key(), registration);
        if (previous != null) {
            throw new IllegalStateException("Duplicate command owner for " + definition.key().wireValue()
                    + ": " + previous.domain() + " and " + definition.domain());
        }
    }

    public DispatchResult dispatch(CommandContext context) throws Exception {
        CommandRegistration registration = registrations.get(CommandKey.of(context.command()));
        if (registration == null) {
            legacyFallback.handle(context);
            return DispatchResult.LEGACY_FALLBACK;
        }
        if (!registration.policy().allows(context)) {
            return DispatchResult.POLICY_REJECTED;
        }
        registration.handler().handle(context);
        return DispatchResult.HANDLED;
    }

    public CommandRegistration registration(int command) {
        return registrations.get(CommandKey.of(command));
    }

    public int registrationCount() {
        return registrations.size();
    }

    public Map<CommandKey, CommandRegistration> registrations() {
        return Collections.unmodifiableMap(registrations);
    }
}
