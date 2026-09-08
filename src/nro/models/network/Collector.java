package nro.models.network;

import java.net.Socket;
import java.net.SocketException;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import nro.models.interfaces.IMessageHandler;
import nro.models.interfaces.IMessageSendCollect;
import nro.models.interfaces.ISession;
import nro.models.utils.Logger;

public final class Collector
        implements Runnable {

    private ISession session;
    private DataInputStream dis;
    private IMessageSendCollect collect;
    private IMessageHandler messageHandler;

    public Collector(ISession session, Socket socket) {
        this.session = session;
        this.setSocket(socket);
    }

    public Collector setSocket(Socket socket) {
        try {
            this.dis = new DataInputStream(socket.getInputStream());
        } catch (IOException iOException) {
        }
        return this;
    }

    @Override
    public void run() {
        SessionCloseCause closeCause = SessionCloseCause.REMOTE_EOF;
        try {
            while (this.session != null && this.session.isConnected()) {
                Message msg = null;
                try {
                    msg = this.collect.readMessage(this.session, this.dis);
                    if (msg == null) {
                        closeCause = SessionCloseCause.PROTOCOL_ERROR;
                        break;
                    }
                    if (msg.command == -27) {
                        this.session.sendKey();
                    } else {
                        this.messageHandler.onMessage(this.session, msg);
                    }
                } finally {
                    if (msg != null) {
                        msg.cleanup();
                    }
                }
            }
        } catch (EOFException | SocketException e) {
            closeCause = SessionCloseCause.REMOTE_EOF;
        } catch (IOException e) {
            closeCause = SessionCloseCause.REMOTE_EOF;
            logCollectorFailure(closeCause, e);
        } catch (Exception e) {
            closeCause = SessionCloseCause.PROTOCOL_ERROR;
            logCollectorFailure(closeCause, e);
        } catch (Throwable t) {
            closeCause = SessionCloseCause.INTERNAL_ERROR;
            logCollectorFailure(closeCause, t);
        } finally {
            if (this.session != null) {
                this.session.close(closeCause);
            }
        }
    }

    private void logCollectorFailure(SessionCloseCause cause, Throwable error) {
        long sessionId = this.session != null ? this.session.getID() : -1;
        Logger.error("[SESSION] event=collector_failure sessionId=" + sessionId
                + " cause=" + cause + " errorType=" + error.getClass().getSimpleName() + "\n");
    }

    public void setCollect(IMessageSendCollect collect) {
        this.collect = collect;
    }

    public void setMessageHandler(IMessageHandler handler) {
        this.messageHandler = handler;
    }

    public void close() {
        if (this.dis != null) {
            try {
                this.dis.close();
            } catch (IOException iOException) {
            }
        }
    }

    public void dispose() {
        this.session = null;
        this.dis = null;
        this.collect = null;
    }
}
