package nro.models.network;

import java.net.Socket;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import nro.models.interfaces.INetwork;
import nro.models.interfaces.IServerClose;
import nro.models.interfaces.ISession;
import nro.models.interfaces.ISessionAcceptHandler;
import nro.models.utils.Logger;

public class Network implements INetwork, Runnable {

    private static Network instance;
    private int port = -1;
    private ServerSocketChannel serverSocketChannel;
    private Class sessionClone = Session.class;
    private final AtomicBoolean start = new AtomicBoolean(false);
    private boolean randomKey;
    private IServerClose serverClose;
    private ISessionAcceptHandler acceptHandler;
    private Thread loopServer;
    private Selector selector;

    public static Network gI() {
        if (instance == null) {
            instance = new Network();
        }
        return instance;
    }

    private Network() {
    }

    @Override
    public INetwork init() {
        try {
            this.selector = Selector.open();
        } catch (IOException ex) {
            Logger.error(ex.toString());
        }
        this.loopServer = new Thread((Runnable) this, "Network");
        return this;
    }

    @Override
    public INetwork start(int port) throws Exception {
        if (port < 0) {
            throw new Exception("Please initialize the server port!");
        }
        if (this.acceptHandler == null) {
            throw new Exception("AcceptHandler has not been initialized!");
        }
        if (!ISession.class.isAssignableFrom(this.sessionClone)) {
            throw new Exception("The session clone type is invalid!");
        }
        try {
            this.port = port;
            this.serverSocketChannel = ServerSocketChannel.open();
            this.serverSocketChannel.configureBlocking(false);
            this.serverSocketChannel.socket().bind(new InetSocketAddress(port));
            this.serverSocketChannel.register(this.selector, 16);
        } catch (IOException ex) {
            Logger.error("Error initializing server at port " + port + "\n");
            System.exit(0);
        }
        this.start.set(true);
        this.loopServer.start();
        Logger.success("Server initialized and listening on port " + this.port + "\n");
        return this;
    }

    @Override
    public INetwork close() {
        this.start.set(false);
        if (this.serverSocketChannel != null) {
            try {
                this.serverSocketChannel.close();
            } catch (IOException iOException) {
            }
        }
        if (this.serverClose != null) {
            this.serverClose.serverClose();
        }
        return this;
    }

    @Override
    public INetwork dispose() {
        this.acceptHandler = null;
        this.loopServer = null;
        this.serverSocketChannel = null;
        return this;
    }

    @Override
    public INetwork setAcceptHandler(ISessionAcceptHandler handler) {
        this.acceptHandler = handler;
        return this;
    }

    @Override
    public void run() {
        while (this.start.get()) {
            try {
                int numKeys = this.selector.select(500);
                if (numKeys == 0) {
                    continue;
                }

                Set<SelectionKey> selectedKeys = this.selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectedKeys.iterator();

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();

                    if (!key.isValid() || !key.isAcceptable()) {
                        continue;
                    }

                    ServerSocketChannel server = (ServerSocketChannel) key.channel();
                    Socket socket = null;
                    ISession session = null;
                    try {
                        java.nio.channels.SocketChannel accepted = server.accept();
                        if (accepted == null) {
                            continue;
                        }
                        socket = accepted.socket();
                        socket.setTcpNoDelay(true);

                        session = SessionFactory.gI().cloneSession(this.sessionClone, socket);
                        this.acceptHandler.sessionInit(session);
                        if (session.getSessionState() == SessionState.ACTIVE && !session.isClosed()) {
                            SessionManager.gI().putSession(session);
                        }
                    } catch (Exception acceptFailure) {
                        if (session != null) {
                            session.close(SessionCloseCause.INTERNAL_ERROR);
                        } else if (socket != null) {
                            try {
                                socket.close();
                            } catch (IOException ignored) {
                            }
                        }
                        Logger.error("[SESSION] event=session_accept_failed errorType="
                                + acceptFailure.getClass().getSimpleName() + "\n");
                    }
                }
            } catch (IOException ex) {
                Logger.error("[NETWORK] event=loop_failure errorType="
                        + ex.getClass().getSimpleName() + "\n");
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } catch (Exception ex2) {
                Logger.error("[NETWORK] event=loop_failure errorType="
                        + ex2.getClass().getSimpleName() + "\n");
            }
        }
    }

    @Override
    public INetwork setDoSomeThingWhenClose(IServerClose serverClose) {
        this.serverClose = serverClose;
        return this;
    }

    @Override
    public INetwork randomKey(boolean isRandom) {
        this.randomKey = isRandom;
        return this;
    }

    @Override
    public boolean isRandomKey() {
        return this.randomKey;
    }

    @Override
    public INetwork setTypeSessioClone(Class clazz) throws Exception {
        this.sessionClone = clazz;
        return this;
    }

    @Override
    public ISessionAcceptHandler getAcceptHandler() throws Exception {
        if (this.acceptHandler == null) {
            throw new Exception("AcceptHandler has not been initialized!");
        }
        return this.acceptHandler;
    }

    @Override
    public void stopConnect() {
        this.start.set(false);
        if (this.selector != null) {
            this.selector.wakeup();
        }
    }
}
