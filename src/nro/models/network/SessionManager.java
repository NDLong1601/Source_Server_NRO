package nro.models.network;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import nro.models.interfaces.ISession;

public class SessionManager {

    private static SessionManager instance;
    private final List<ISession> sessions = new CopyOnWriteArrayList<>();

    public static SessionManager gI() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    public void putSession(ISession session) {
        sessions.add(session);
    }

    public void removeSession(ISession session) {
        sessions.remove(session);
    }

    public List<ISession> getSessions() {
        return sessions;
    }

    public void cleanupSessions() {
        for (ISession session : sessions) {
            if (!session.isConnected()) {
                sessions.remove(session);
                session.dispose();
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

    public ISession findByID(long id) throws Exception {
        for (ISession session : sessions) {
            if (session.getID() == id) {
                return session;
            }
        }
        throw new Exception("Session " + id + " does not exist");
    }

    public int getNumSession() {
        return sessions.size();
    }
}
