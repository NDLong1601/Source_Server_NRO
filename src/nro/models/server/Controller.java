package nro.models.server;

import java.io.IOException;
import nro.models.interfaces.IMessageHandler;
import nro.models.interfaces.ISession;
import nro.models.network.Message;
import nro.models.network.MySession;
import nro.models.player.Player;
import nro.models.server.dispatch.AuthAssetCommandHandler;
import nro.models.server.dispatch.CommandContext;
import nro.models.server.dispatch.CommandDispatcher;
import nro.models.server.dispatch.ControllerCommandRegistry;
import nro.models.server.dispatch.DispatchResult;
import nro.models.server.dispatch.EconomyCommandHandler;
import nro.models.server.dispatch.InventoryShopCommandHandler;
import nro.models.server.dispatch.ProtocolErrorReporter;
import nro.models.server.dispatch.ProtocolErrorType;
import nro.models.server.dispatch.SocialClanActivityCommandHandler;
import nro.models.server.dispatch.WorldCombatCommandHandler;

/**
 * Compatibility entry point for the session reader. Business command ownership
 * lives in the Gate 3 domain handlers under {@code server.dispatch}.
 */
public class Controller implements IMessageHandler {

    private static final Controller INSTANCE = new Controller();

    private final AuthAssetCommandHandler authAssetHandler;
    private final CommandDispatcher dispatcher;
    private final ProtocolErrorReporter errorReporter;

    public Controller() {
        this.authAssetHandler = new AuthAssetCommandHandler();
        this.dispatcher = ControllerCommandRegistry.create(
                authAssetHandler,
                new EconomyCommandHandler(),
                new InventoryShopCommandHandler(),
                new WorldCombatCommandHandler(),
                new SocialClanActivityCommandHandler());
        this.errorReporter = new ProtocolErrorReporter();
    }

    public static Controller gI() {
        return INSTANCE;
    }

    @Override
    public void onMessage(ISession session, Message message) {
        if (!(session instanceof MySession mySession) || message == null) {
            dispose(message);
            return;
        }

        CommandContext context = new CommandContext(mySession, message);
        try {
            DispatchResult result = dispatcher.dispatch(context);
            if (result == DispatchResult.LEGACY_FALLBACK) {
                errorReporter.recordUnknown(context);
            }
        } catch (IOException malformedPacket) {
            errorReporter.report(ProtocolErrorType.MALFORMED_PACKET, context, malformedPacket);
        } catch (Exception handlerFailure) {
            errorReporter.report(ProtocolErrorType.HANDLER_FAILURE, context, handlerFailure);
        } finally {
            dispose(message);
        }
    }

    private static void dispose(Message message) {
        if (message != null) {
            message.cleanup();
            message.dispose();
        }
    }

    public void messageNotLogin(MySession session, Message message) {
        authAssetHandler.messageNotLogin(session, message);
    }

    public void messageNotMap(MySession session, Message message) {
        authAssetHandler.messageNotMap(session, message);
    }

    public void messageSubCommand(MySession session, Message message) {
        authAssetHandler.messageSubCommand(session, message);
    }

    public void createChar(MySession session, Message message) {
        authAssetHandler.createChar(session, message);
    }

    public void login2(MySession session, Message message) {
        authAssetHandler.login2(session, message);
    }

    public void sendInfo(MySession session) {
        authAssetHandler.sendInfo(session);
    }

    public void finishUpdate(Player player) {
        authAssetHandler.finishUpdate(player);
    }
}
