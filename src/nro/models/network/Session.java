package nro.models.network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import nro.models.interfaces.IKeySessionHandler;
import nro.models.interfaces.IMessageHandler;
import nro.models.interfaces.IMessageSendCollect;
import nro.models.interfaces.ISession;
import nro.models.server.ServerRuntimeMetrics;
import nro.models.utils.Logger;
import nro.models.utils.StringUtil;

public class Session implements ISession {

    private static ISession instance;
    private static final AtomicLong ID_GENERATOR = new AtomicLong();

    private final long id = ID_GENERATOR.incrementAndGet();
    private final Socket socket;
    private final String ip;
    private byte[] KEYS = "NRO".getBytes();
    private volatile boolean sentKey;

    private final AtomicReference<SessionState> state = new AtomicReference<>(SessionState.NEW);
    private final AtomicBoolean senderStarted = new AtomicBoolean(false);
    private final AtomicBoolean collectorStarted = new AtomicBoolean(false);
    private volatile SessionCloseCause closeCause;
    private volatile IpLease ipLease;
    private final Object resourceLock = new Object();
    private final AtomicLong duplicateCloseAttempts = new AtomicLong();

    private Sender sender;
    private Collector collector;
    private final Thread tSender;
    private final Thread tCollector;

    private IKeySessionHandler keyHandler;

    public static ISession gI() throws Exception {
        if (instance == null) {
            throw new Exception("Instance has not been initialized!");
        }
        return instance;
    }

    public Session() {
        this.socket = null;
        this.state.set(SessionState.ACTIVE);
        this.ip = "127.0.0.1";
        this.sender = new Sender(this);
        this.collector = null;
        this.tSender = null;
        this.tCollector = null;
    }

    public Session(Socket socket) {
        this.socket = socket;
        this.state.set(SessionState.NEW);

        try {
            this.socket.setSendBufferSize(0x100000);
            this.socket.setReceiveBufferSize(0x100000);
        } catch (SocketException ignored) {
        }

        this.ip = socket.getRemoteSocketAddress() != null && socket.getRemoteSocketAddress() instanceof InetSocketAddress
                ? ((InetSocketAddress) socket.getRemoteSocketAddress()).getAddress().getHostAddress()
                : "127.0.0.1";

        this.sender = new Sender(this, socket);
        this.collector = new Collector(this, socket);

        this.tSender = new Thread(this.sender, "Sender - IP : " + IpRedactor.redact(ip));
        this.tCollector = new Thread(this.collector, "Collector - IP : " + IpRedactor.redact(ip));
    }

    @Override
    public ISession start() {
        this.startSend();
        this.startCollect();
        return this;
    }

    @Override
    public ISession startSend() {
        this.activate();
        if (this.tSender != null && this.isConnected() && this.senderStarted.compareAndSet(false, true)) {
            this.tSender.start();
        }
        return this;
    }

    @Override
    public ISession startCollect() {
        this.activate();
        if (this.tCollector != null && this.isConnected() && this.collectorStarted.compareAndSet(false, true)) {
            this.tCollector.start();
        }
        return this;
    }

    @Override
    public void activate() {
        this.state.compareAndSet(SessionState.NEW, SessionState.ACTIVE);
    }

    @Override
    public SessionState getSessionState() {
        return this.state.get();
    }

    public SessionState getState() {
        return this.state.get();
    }

    @Override
    public SessionCloseCause getCloseCause() {
        return this.closeCause;
    }

    @Override
    public boolean isClosed() {
        SessionState s = this.state.get();
        return s == SessionState.CLOSING || s == SessionState.CLOSED;
    }

    @Override
    public void setIpLease(IpLease lease) {
        IpLease releaseNow = null;
        synchronized (this.resourceLock) {
            if (this.isClosed()) {
                releaseNow = lease;
            } else {
                releaseNow = this.ipLease;
                this.ipLease = lease;
            }
        }
        if (releaseNow != null && releaseNow != lease) {
            releaseNow.release();
        } else if (this.isClosed() && releaseNow != null) {
            releaseNow.release();
        }
    }

    @Override
    public IpLease getIpLease() {
        return this.ipLease;
    }

    public long getDuplicateCloseAttempts() {
        return duplicateCloseAttempts.get();
    }

    /**
     * Waits for the socket sender and collector owned by this session to stop.
     * This is primarily used by shutdown verification and does not initiate a
     * close by itself.
     */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout must not be negative");
        }
        long timeoutNanos = unit.toNanos(timeout);
        long start = System.nanoTime();
        long deadline = timeoutNanos >= Long.MAX_VALUE - start ? Long.MAX_VALUE : start + timeoutNanos;
        return joinUntil(this.tSender, deadline) && joinUntil(this.tCollector, deadline);
    }

    private boolean joinUntil(Thread thread, long deadlineNanos) throws InterruptedException {
        if (thread == null || thread == Thread.currentThread()) {
            return true;
        }
        while (thread.isAlive()) {
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                return false;
            }
            long waitMillis = Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remainingNanos));
            thread.join(waitMillis);
        }
        return true;
    }

    @Override
    public ISession setSendCollect(IMessageSendCollect collect) {
        this.sender.setSend(collect);
        this.collector.setCollect(collect);
        return this;
    }

    @Override
    public ISession setMessageHandler(IMessageHandler handler) {
        this.collector.setMessageHandler(handler);
        return this;
    }

    @Override
    public ISession setKeyHandler(IKeySessionHandler handler) {
        this.keyHandler = handler;
        return this;
    }

    @Override
    public void sendMessage(Message msg) {
        if (this.isConnected() && msg != null && !this.isClosed()) {
            this.sender.sendMessage(msg);
        }
    }

    @Override
    public void doSendMessage(Message msg) throws Exception {
        try {
            this.sender.doSendMessage(msg);
        } catch (Exception error) {
            ServerRuntimeMetrics.gI().recordSendFailure();
            this.close(SessionCloseCause.SEND_FAILURE);
            throw error;
        }
    }

    @Override
    public void sendKey() throws Exception {
        if (this.keyHandler == null) {
            throw new Exception("Key handler has not been initialized!");
        }
        if (Network.gI().isRandomKey()) {
            this.KEYS = StringUtil.randomText(7).getBytes();
        }
        this.keyHandler.sendKey(this);
    }

    @Override
    public void setSentKey(boolean sent) {
        this.sentKey = sent;
    }

    @Override
    public boolean sentKey() {
        return this.sentKey;
    }

    @Override
    public boolean isConnected() {
        return this.state.get() == SessionState.ACTIVE;
    }

    @Override
    public long getID() {
        return this.id;
    }

    @Override
    public String getIP() {
        return this.ip;
    }

    @Override
    public byte[] getKey() {
        return this.KEYS;
    }

    @Override
    public int getNumMessages() {
        return this.isConnected() && this.sender != null ? this.sender.getNumMessages() : -1;
    }

    @Override
    public void close(SessionCloseCause cause) {
        if (cause == null) {
            cause = SessionCloseCause.INTERNAL_ERROR;
        }
        while (true) {
            SessionState current = this.state.get();
            if (current == SessionState.CLOSING || current == SessionState.CLOSED) {
                this.duplicateCloseAttempts.incrementAndGet();
                ServerRuntimeMetrics.gI().recordDuplicateClose();
                return;
            }
            if (this.state.compareAndSet(current, SessionState.CLOSING)) {
                break;
            }
        }

        this.closeCause = cause;
        this.sentKey = false;
        long closeCauseCount = ServerRuntimeMetrics.gI().recordCloseCause(cause);
        boolean abnormalClose = cause != SessionCloseCause.CLIENT_DISCONNECT && cause != SessionCloseCause.REMOTE_EOF;
        boolean logarithmicSample = closeCauseCount > 0
                && (closeCauseCount & (closeCauseCount - 1)) == 0;
        if (abnormalClose && (closeCauseCount <= 10 || logarithmicSample)) {
            Logger.warning("[SESSION] event=session_close sessionId=" + this.id
                    + " cause=" + cause + " causeCount=" + closeCauseCount
                    + " ip=" + IpRedactor.redact(this.ip) + "\n");
        }

        try {
            runCloseStep("session_registry", () -> SessionManager.gI().removeSession(this));
            runCloseStep("ip_lease", () -> {
                IpLease lease;
                synchronized (this.resourceLock) {
                    lease = this.ipLease;
                    this.ipLease = null;
                }
                if (lease != null) {
                    lease.release();
                }
            });
            runCloseStep("sender_close", () -> {
                if (this.sender != null) {
                    this.sender.close();
                }
            });
            runCloseStep("collector_close", () -> {
                if (this.collector != null) {
                    this.collector.close();
                }
            });
            runCloseStep("sender_interrupt", () -> {
                if (this.tSender != null && this.tSender != Thread.currentThread() && this.tSender.isAlive()) {
                    this.tSender.interrupt();
                }
            });
            runCloseStep("collector_interrupt", () -> {
                if (this.tCollector != null && this.tCollector != Thread.currentThread() && this.tCollector.isAlive()) {
                    this.tCollector.interrupt();
                }
            });
            runCloseStep("socket_close", () -> {
                if (this.socket != null && !this.socket.isClosed()) {
                    try {
                        this.socket.close();
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                }
            });

            // Network resources are released before player persistence so a slow
            // database cannot retain a socket or IP admission slot.
            runCloseStep("session_hook", () -> onSessionClosing(this.closeCause));
            runCloseStep("sender_dispose", () -> {
                if (this.sender != null) {
                    this.sender.dispose();
                }
            });
            runCloseStep("collector_dispose", () -> {
                if (this.collector != null) {
                    this.collector.dispose();
                }
            });
        } finally {
            this.state.set(SessionState.CLOSED);
        }
    }

    private void runCloseStep(String step, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            Logger.error("[SESSION] event=session_close_step_failed sessionId=" + this.id
                    + " cause=" + this.closeCause + " step=" + step
                    + " errorType=" + t.getClass().getSimpleName() + "\n");
        }
    }

    protected void onSessionClosing(SessionCloseCause cause) {
        // Subclasses may override for player detachment
    }

    @Override
    public void disconnect() {
        this.close(SessionCloseCause.CLIENT_DISCONNECT);
    }

    @Override
    public void dispose() {
        this.close(SessionCloseCause.CLIENT_DISCONNECT);
    }
}
