
package nro.models.interfaces;

import nro.models.network.Message;

public interface IMessageHandler {
    public void onMessage(ISession var1, Message var2) throws Exception;
}

