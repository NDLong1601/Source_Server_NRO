package nro.models.server.dispatch;

/** Preserves the legacy behavior for unsupported commands: ignore the packet. */
public final class LegacyFallbackCommandHandler implements CommandHandler {

    @Override
    public void handle(CommandContext context) {
        // Intentionally ignored for wire compatibility.
    }
}
