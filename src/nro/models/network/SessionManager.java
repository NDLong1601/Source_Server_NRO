package nro.models.network;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import nro.models.interfaces.ISession;

public class SessionManager {

    private static final SessionManager INSTANCE = new SessionManager();
    private final ConcurrentHashMap<Long, ISession> sessions = new ConcurrentHashMap<>();
    private final AtomicBoolean acceptingSessions = new AtomicBoolean(true);
    private volatile SessionCloseCause shutdownCause = SessionCloseCause.SERVER_SHUTDOWN;

    public static SessionManager gI() {
        return INSTANCE;
    }

    public void putSession(ISession session) {
        if (session == null) {
            return;
        }
        if (!acceptingSessions.get()) {
            session.close(shutdownCause);
            return;
        }
        if (session.isClosed()) {
            return;
        }

        sessions.put(session.getID(), session);

        // Close may have removed the session just before the insertion above.
        // Recheck after publication so that race cannot resurrect a terminal session.
        if (session.isClosed()) {
            sessions.remove(session.getID(), session);
        } else if (!acceptingSessions.get()) {
            sessions.remove(session.getID(), session);
            session.close(shutdownCause);
        }
    }

    public void removeSession(ISession session) {
        if (session != null) {
            sessions.remove(session.getID(), session);
        }
    }

    public void closeAll(SessionCloseCause cause) {
        shutdownCause = cause != null ? cause : SessionCloseCause.SERVER_SHUTDOWN;
        acceptingSessions.set(false);
        for (ISession session : List.copyOf(sessions.values())) {
            if (session != null) {
                session.close(shutdownCause);
            }
        }
        sessions.clear();
    }

    public List<ISession> getSessions() {
        return List.copyOf(sessions.values());
    }

    public void cleanupSessions() {
        for (ISession session : List.copyOf(sessions.values())) {
            if (session != null && (!session.isConnected() || session.isClosed())) {
                sessions.remove(session.getID());
                session.close(SessionCloseCause.INTERNAL_ERROR);
            }
        }
    }

    public void startCleanupThread() {
        Thread cleanupThread = new Thread(() -> {
            while (true) {
                cleanupSessions();
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "SessionCleanup");
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    public ISession find(long id) {
        return sessions.get(id);
    }

    public ISession findByID(long id) throws Exception {
        ISession session = sessions.get(id);
        if (session != null) {
            return session;
        }
        throw new Exception("Session " + id + " does not exist");
    }

    public int getNumSession() {
        return sessions.size();
    }
}
