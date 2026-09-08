package nro.models.network;

import java.util.concurrent.atomic.AtomicBoolean;

public final class IpLease implements AutoCloseable {

    private final IpConnectionRegistry registry;
    private final String ip;
    private final AtomicBoolean released = new AtomicBoolean(false);

    public IpLease(IpConnectionRegistry registry, String ip) {
        this.registry = registry;
        this.ip = ip;
    }

    public boolean release() {
        if (released.compareAndSet(false, true)) {
            if (registry != null && ip != null) {
                registry.release(ip);
            }
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        release();
    }

    public boolean isReleased() {
        return released.get();
    }

    public String getRawIp() {
        return ip;
    }

    public String getRedactedIp() {
        return IpRedactor.redact(ip);
    }
}
