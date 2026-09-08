package nro.models.server.dispatch;

@FunctionalInterface
public interface CommandHandler {

    void handle(CommandContext context) throws Exception;
}
