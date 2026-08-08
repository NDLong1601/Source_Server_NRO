package nro.models.network;

import nro.models.interfaces.IKeySessionHandler;
import nro.models.interfaces.ISession;

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
            msg.cleanup();
            session.setSentKey(true);
        } catch (Exception exception) {
        }
    }
}
