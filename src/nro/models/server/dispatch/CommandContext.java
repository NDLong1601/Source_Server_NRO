package nro.models.server.dispatch;

import java.util.Objects;
import nro.models.network.Message;
import nro.models.network.MySession;
import nro.models.player.Player;

public record CommandContext(MySession session, Message message, Player player) {

    public CommandContext(MySession session, Message message) {
        this(session, message, session == null ? null : session.player);
    }

    public CommandContext {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(message, "message");
    }

    public byte command() {
        return message.command;
    }
}
