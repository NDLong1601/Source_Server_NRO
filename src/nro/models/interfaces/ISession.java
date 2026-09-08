/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 */
package nro.models.interfaces;

import nro.models.network.IpLease;
import nro.models.network.Message;
import nro.models.network.SessionCloseCause;
import nro.models.network.SessionState;

public interface ISession {
    public ISession setSendCollect(IMessageSendCollect var1);

    public ISession setMessageHandler(IMessageHandler var1);

    public ISession setKeyHandler(IKeySessionHandler var1);

    public ISession startSend();

    public ISession startCollect();

    public ISession start();

    public String getIP();

    public boolean isConnected();

    public long getID();

    public void sendMessage(Message var1);

    public void doSendMessage(Message var1) throws Exception;

    public void disconnect();

    public void dispose();

    public int getNumMessages();

    public void sendKey() throws Exception;

    public byte[] getKey();

    public boolean sentKey();

    public void setSentKey(boolean var1);

    public void close(SessionCloseCause cause);

    public SessionState getSessionState();

    public SessionCloseCause getCloseCause();

    public boolean isClosed();

    public void activate();

    public void setIpLease(IpLease lease);

    public IpLease getIpLease();
}

