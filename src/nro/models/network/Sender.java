package nro.models.network;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.NonNull;
import nro.models.interfaces.IMessageSendCollect;
import nro.models.interfaces.ISession;
import nro.models.server.ServerRuntimeMetrics;
import nro.models.utils.Logger;

public final class Sender implements Runnable {

    private static volatile int defaultMaxQueueMessages = 1000;
    private static volatile long defaultMaxQueueBytes = 2 * 1024 * 1024; // 2MB

    private volatile int maxQueueMessages;
    private volatile long maxQueueBytes;

    @NonNull
    private ISession session;
    @NonNull
    private final BlockingQueue<QueuedFrame> messages;
    private final Object queueLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicInteger queuedMessages = new AtomicInteger(0);
    private final AtomicLong queuedBytes = new AtomicLong(0);
    private final AtomicLong highWaterMarkBytes = new AtomicLong(0);

    private DataOutputStream dos;
    private IMessageSendCollect sendCollect;

    public Sender(@NonNull ISession session) {
        if (session == null) {
            throw new NullPointerException("session is marked non-null but is null");
        }
        this.session = session;
        this.maxQueueMessages = requirePositive(defaultMaxQueueMessages, "defaultMaxQueueMessages");
        this.maxQueueBytes = requirePositive(defaultMaxQueueBytes, "defaultMaxQueueBytes");
        this.messages = new ArrayBlockingQueue<>(this.maxQueueMessages);
    }

    public Sender(@NonNull ISession session, @NonNull Socket socket) {
        this(session);
        if (socket == null) {
            throw new NullPointerException("socket is marked non-null but is null");
        }
        this.setSocket(socket);
    }

    public Sender setSocket(@NonNull Socket socket) {
        if (socket == null) {
            throw new NullPointerException("socket is marked non-null but is null");
        }
        try {
            this.dos = new DataOutputStream(socket.getOutputStream());
        } catch (IOException ignored) {
        }
        return this;
    }

    public static synchronized void configureDefaultLimits(int maxMessages, long maxBytes) {
        defaultMaxQueueMessages = requirePositive(maxMessages, "defaultMaxQueueMessages");
        defaultMaxQueueBytes = requirePositive(maxBytes, "defaultMaxQueueBytes");
    }

    public static int getDefaultMaxQueueMessages() {
        return defaultMaxQueueMessages;
    }

    public static long getDefaultMaxQueueBytes() {
        return defaultMaxQueueBytes;
    }

    @Override
    public void run() {
        try {
            while (!this.closed.get() && this.session != null
                    && this.session.isConnected() && !this.session.isClosed()) {
                QueuedFrame frame = this.messages.poll(500, TimeUnit.MILLISECONDS);
                if (frame == null) {
                    continue;
                }
                try {
                    this.doSendFrame(frame);
                } catch (IOException e) {
                    ServerRuntimeMetrics.gI().recordSendFailure();
                    if (this.session != null) {
                        this.session.close(SessionCloseCause.SEND_FAILURE);
                    }
                    break;
                } catch (Exception e) {
                    ServerRuntimeMetrics.gI().recordSendFailure();
                    if (this.session != null) {
                        this.session.close(SessionCloseCause.SEND_FAILURE);
                    }
                    break;
                } finally {
                    releaseFrameAccounting(frame);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            ServerRuntimeMetrics.gI().recordSendFailure();
            ISession currentSession = this.session;
            if (currentSession != null && !currentSession.isClosed()) {
                currentSession.close(SessionCloseCause.SEND_FAILURE);
            }
        }
    }

    public synchronized void doSendMessage(Message message) throws Exception {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        ensureWritable();
        if (rejectOversizedPayload(message, message.getData(), this.session)) {
            throw new IOException("outbound payload exceeds the command wire-format limit");
        }
        this.sendCollect.doSendMessage(this.session, this.dos, message);
    }

    public synchronized void doSendFrame(QueuedFrame frame) throws Exception {
        if (frame == null) {
            throw new IllegalArgumentException("frame must not be null");
        }
        ensureWritable();
        Message msg = frame.toMessage();
        try {
            this.sendCollect.doSendMessage(this.session, this.dos, msg);
        } finally {
            msg.cleanup();
        }
    }

    public void sendMessage(Message msg) {
        ISession currentSession = this.session;
        if (this.closed.get() || currentSession == null
                || !currentSession.isConnected() || currentSession.isClosed() || msg == null) {
            return;
        }
        byte[] data = msg.getData();
        if (rejectOversizedPayload(msg, data, currentSession)) {
            return;
        }

        QueuedFrame frame = new QueuedFrame(msg.command, data);
        boolean overflow = false;
        synchronized (this.queueLock) {
            if (this.closed.get() || this.session == null
                    || !this.session.isConnected() || this.session.isClosed()) {
                return;
            }
            int nextCount = this.queuedMessages.get() + 1;
            long nextBytes = this.queuedBytes.get() + frame.getWireSize();
            if (nextCount > this.maxQueueMessages || nextBytes > this.maxQueueBytes
                    || !this.messages.offer(frame)) {
                overflow = true;
            } else {
                this.queuedMessages.set(nextCount);
                this.queuedBytes.set(nextBytes);
                this.highWaterMarkBytes.accumulateAndGet(nextBytes, Math::max);
                ServerRuntimeMetrics.gI().adjustSenderQueueMetrics(1, frame.getWireSize());
            }
        }

        if (overflow) {
            ServerRuntimeMetrics.gI().recordSenderOverflow();
            ServerRuntimeMetrics.gI().recordSlowConsumer();
            this.close();
            currentSession.close(SessionCloseCause.SLOW_CONSUMER);
        }
    }

    public void setSend(IMessageSendCollect sendCollect) {
        this.sendCollect = sendCollect;
    }

    public int getNumMessages() {
        return this.queuedMessages.get();
    }

    public long getQueuedBytes() {
        return this.queuedBytes.get();
    }

    public long getHighWaterMarkBytes() {
        return this.highWaterMarkBytes.get();
    }

    public int getMaxQueueMessages() {
        return maxQueueMessages;
    }

    public void setMaxQueueMessages(int maxQueueMessages) {
        int valid = requirePositive(maxQueueMessages, "maxQueueMessages");
        if (valid > this.messages.size() + this.messages.remainingCapacity()) {
            throw new IllegalArgumentException("maxQueueMessages exceeds physical queue capacity");
        }
        this.maxQueueMessages = valid;
    }

    public long getMaxQueueBytes() {
        return maxQueueBytes;
    }

    public void setMaxQueueBytes(long maxQueueBytes) {
        this.maxQueueBytes = requirePositive(maxQueueBytes, "maxQueueBytes");
    }

    public void send(Message msg) {
        sendMessage(msg);
    }

    public int getQueuedMessages() {
        return getNumMessages();
    }

    public void stop() {
        close();
    }

    public void close() {
        if (!this.closed.compareAndSet(false, true)) {
            return;
        }
        synchronized (this.queueLock) {
            int releasedMessages = this.queuedMessages.getAndSet(0);
            long releasedBytes = this.queuedBytes.getAndSet(0);
            this.messages.clear();
            ServerRuntimeMetrics.gI().adjustSenderQueueMetrics(-releasedMessages, -releasedBytes);
        }

        if (this.dos != null) {
            try {
                this.dos.close();
            } catch (IOException ignored) {
            }
        }
    }

    public void dispose() {
        this.close();
        this.session = null;
        this.sendCollect = null;
        this.dos = null;
    }

    private void ensureWritable() throws IOException {
        if (this.closed.get() || this.sendCollect == null || this.dos == null || this.session == null) {
            throw new IOException("sender is not writable");
        }
    }

    private void releaseFrameAccounting(QueuedFrame frame) {
        synchronized (this.queueLock) {
            if (this.queuedMessages.get() <= 0) {
                return;
            }
            this.queuedMessages.decrementAndGet();
            long releasedBytes = Math.min(this.queuedBytes.get(), frame.getWireSize());
            this.queuedBytes.addAndGet(-releasedBytes);
            ServerRuntimeMetrics.gI().adjustSenderQueueMetrics(-1, -releasedBytes);
        }
    }

    private boolean rejectOversizedPayload(Message message, byte[] data, ISession currentSession) {
        int payloadLimit = QueuedFrame.getMaxPayloadLength(message.command);
        if (data == null || data.length <= payloadLimit) {
            return false;
        }
        Logger.error("[SENDER] event=outbound_payload_rejected sessionId=" + currentSession.getID()
                + " command=" + message.command + " bytes=" + data.length + " limit=" + payloadLimit + "\n");
        this.close();
        currentSession.close(SessionCloseCause.INTERNAL_ERROR);
        return true;
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
        return value;
    }

    private static long requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
        return value;
    }
}
