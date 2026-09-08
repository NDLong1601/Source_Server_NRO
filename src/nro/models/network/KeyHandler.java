package nro.models.network;

import nro.models.interfaces.IKeySessionHandler;
import nro.models.interfaces.ISession;
import nro.models.utils.Logger;

public class KeyHandler implements IKeySessionHandler {

    @Override
    public void sendKey(ISession session) {
        Message msg = new Message(-27);
        try {
            byte[] KEYS = session.getKey();
            msg.writer().writeByte(KEYS.length);
            msg.writer().writeByte(KEYS[0]);
            for (int i = 1; i < KEYS.length; ++i) {
                msg.writer().writeByte(KEYS[i] ^ KEYS[i - 1]);
            }
            session.doSendMessage(msg);
            session.setSentKey(true);
        } catch (Exception exception) {
            Logger.error("[SESSION] event=session_key_send_failed sessionId=" + session.getID()
                    + " errorType=" + exception.getClass().getSimpleName() + "\n");
            session.close(SessionCloseCause.SEND_FAILURE);
        } finally {
            msg.cleanup();
        }
    }
}
