package nro.models.server;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import nro.models.network.SessionCloseCause;
import nro.models.server.dispatch.ProtocolErrorType;

public final class ServerRuntimeMetrics {

    private static final ServerRuntimeMetrics INSTANCE = new ServerRuntimeMetrics();

    private final AtomicLong lastTickDurationMs = new AtomicLong(0);
    private final AtomicLong maxTickDurationMs = new AtomicLong(0);
    private final LongAdder tickExceptionCount = new LongAdder();
    private final LongAdder deadlineOverrunCount = new LongAdder();
    private final LongAdder rejectedOverlapCount = new LongAdder();

    private final AtomicInteger senderQueueDepth = new AtomicInteger(0);
    private final AtomicLong senderQueuedBytes = new AtomicLong(0);
    private final AtomicLong senderHighWaterMarkBytes = new AtomicLong(0);
    private final LongAdder senderOverflowCount = new LongAdder();
    private final LongAdder senderSlowConsumerCount = new LongAdder();
    private final LongAdder senderSendFailureCount = new LongAdder();

    private final LongAdder duplicateCloseAttempts = new LongAdder();
    private final ConcurrentHashMap<SessionCloseCause, LongAdder> closeCauseCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ProtocolErrorType, LongAdder> protocolErrorCounts = new ConcurrentHashMap<>();

    private ServerRuntimeMetrics() {
    }

    public static ServerRuntimeMetrics gI() {
        return INSTANCE;
    }

    public void recordTickDuration(long durationMs) {
        lastTickDurationMs.set(durationMs);
        maxTickDurationMs.accumulateAndGet(durationMs, Math::max);
    }

    public void recordTickException() {
        tickExceptionCount.increment();
    }

    public void recordDeadlineOverrun() {
        deadlineOverrunCount.increment();
    }

    public void recordRejectedOverlap() {
        rejectedOverlapCount.increment();
    }

    public void adjustSenderQueueMetrics(int messageDelta, long byteDelta) {
        int currentDepth = senderQueueDepth.updateAndGet(current -> Math.max(0, current + messageDelta));
        long currentBytes = senderQueuedBytes.updateAndGet(current -> Math.max(0, current + byteDelta));
        senderHighWaterMarkBytes.accumulateAndGet(currentBytes, Math::max);
    }

    public void recordSenderOverflow() {
        senderOverflowCount.increment();
    }

    public void recordSlowConsumer() {
        senderSlowConsumerCount.increment();
    }

    public void recordSendFailure() {
        senderSendFailureCount.increment();
    }

    public void recordDuplicateClose() {
        duplicateCloseAttempts.increment();
    }

    public long recordCloseCause(SessionCloseCause cause) {
        if (cause == null) {
            return 0;
        }
        LongAdder counter = closeCauseCounts.computeIfAbsent(cause, key -> new LongAdder());
        counter.increment();
        return counter.sum();
    }

    public long recordProtocolError(ProtocolErrorType type) {
        if (type == null) {
            return 0;
        }
        LongAdder counter = protocolErrorCounts.computeIfAbsent(type, key -> new LongAdder());
        counter.increment();
        return counter.sum();
    }

    public long getLastTickDurationMs() {
        return lastTickDurationMs.get();
    }

    public long getMaxTickDurationMs() {
        return maxTickDurationMs.get();
    }

    public long getTickExceptionCount() {
        return tickExceptionCount.sum();
    }

    public long getDeadlineOverrunCount() {
        return deadlineOverrunCount.sum();
    }

    public long getRejectedOverlapCount() {
        return rejectedOverlapCount.sum();
    }

    public int getSenderQueueDepth() {
        return senderQueueDepth.get();
    }

    public long getSenderQueuedBytes() {
        return senderQueuedBytes.get();
    }

    public long getSenderHighWaterMarkBytes() {
        return senderHighWaterMarkBytes.get();
    }

    public long getSenderOverflowCount() {
        return senderOverflowCount.sum();
    }

    public long getSenderSlowConsumerCount() {
        return senderSlowConsumerCount.sum();
    }

    public long getSenderSendFailureCount() {
        return senderSendFailureCount.sum();
    }

    public long getDuplicateCloseAttempts() {
        return duplicateCloseAttempts.sum();
    }

    public long getCloseCauseCount(SessionCloseCause cause) {
        LongAdder adder = closeCauseCounts.get(cause);
        return adder == null ? 0 : adder.sum();
    }

    public Map<SessionCloseCause, Long> getCloseCauseSnapshot() {
        Map<SessionCloseCause, Long> snapshot = new EnumMap<>(SessionCloseCause.class);
        for (Map.Entry<SessionCloseCause, LongAdder> entry : closeCauseCounts.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().sum());
        }
        return Collections.unmodifiableMap(snapshot);
    }

    public long getProtocolErrorCount(ProtocolErrorType type) {
        LongAdder adder = protocolErrorCounts.get(type);
        return adder == null ? 0 : adder.sum();
    }

    public Map<ProtocolErrorType, Long> getProtocolErrorSnapshot() {
        Map<ProtocolErrorType, Long> snapshot = new EnumMap<>(ProtocolErrorType.class);
        for (Map.Entry<ProtocolErrorType, LongAdder> entry : protocolErrorCounts.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().sum());
        }
        return Collections.unmodifiableMap(snapshot);
    }

    public String formatRuntimeSnapshot(int activeSessions, long activeIpLeases) {
        return "[RUNTIME] event=runtime_snapshot activeSessions=" + activeSessions
                + " activeIpLeases=" + activeIpLeases
                + " senderQueueDepth=" + getSenderQueueDepth()
                + " senderQueuedBytes=" + getSenderQueuedBytes()
                + " senderHighWaterBytes=" + getSenderHighWaterMarkBytes()
                + " senderOverflows=" + getSenderOverflowCount()
                + " sendFailures=" + getSenderSendFailureCount()
                + " lastTickMs=" + getLastTickDurationMs()
                + " maxTickMs=" + getMaxTickDurationMs()
                + " tickExceptions=" + getTickExceptionCount()
                + " tickOverruns=" + getDeadlineOverrunCount()
                + " rejectedPlayerOverlaps=" + getRejectedOverlapCount()
                + " duplicateCloses=" + getDuplicateCloseAttempts()
                + " closeCauses=" + getCloseCauseSnapshot()
                + " protocolErrors=" + getProtocolErrorSnapshot();
    }

    public void reset() {
        lastTickDurationMs.set(0);
        maxTickDurationMs.set(0);
        tickExceptionCount.reset();
        deadlineOverrunCount.reset();
        rejectedOverlapCount.reset();
        senderQueueDepth.set(0);
        senderQueuedBytes.set(0);
        senderHighWaterMarkBytes.set(0);
        senderOverflowCount.reset();
        senderSlowConsumerCount.reset();
        senderSendFailureCount.reset();
        duplicateCloseAttempts.reset();
        closeCauseCounts.clear();
        protocolErrorCounts.clear();
    }
}
