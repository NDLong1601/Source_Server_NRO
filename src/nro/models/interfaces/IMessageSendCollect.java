
package nro.models.interfaces;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import nro.models.network.Message;

public interface IMessageSendCollect {
    public Message readMessage(ISession var1, DataInputStream var2) throws Exception;

    public byte readKey(ISession var1, byte var2);

    public void doSendMessage(ISession var1, DataOutputStream var2, Message var3) throws Exception;

    public byte writeKey(ISession var1, byte var2);
}

