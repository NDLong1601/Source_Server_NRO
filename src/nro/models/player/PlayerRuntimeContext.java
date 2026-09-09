package nro.models.player;

import java.util.concurrent.atomic.AtomicBoolean;
import nro.models.network.MySession;

/** Owns transient session, lifecycle, mailbox, and persistence coordination. */
public final class PlayerRuntimeContext {

    private final PlayerLifecycleState lifecycle = new PlayerLifecycleState();
    private final PlayerPersistenceState persistence = new PlayerPersistenceState();
    private final PlayerMailbox mailbox;
    private final AtomicBoolean persistenceQuarantined = new AtomicBoolean();
    private volatile MySession session;

    public PlayerRuntimeContext(Player owner, int mailboxCapacity) {
        this.mailbox = new PlayerMailbox(owner, mailboxCapacity);
    }

    public PlayerLifecycleState lifecycle() {
        return lifecycle;
    }

    public PlayerPersistenceState persistence() {
        return persistence;
    }

    public PlayerMailbox mailbox() {
        return mailbox;
    }

    public MySession session() {
        return session;
    }

    public void attachSession(MySession value) {
        if (lifecycle.isDisposed() && value != null) {
            throw new IllegalStateException("Cannot attach a session to a disposed player");
        }
        session = value;
    }

    public boolean isPersistenceQuarantined() {
        return persistenceQuarantined.get();
    }

    public void quarantinePersistence() {
        persistenceQuarantined.set(true);
    }
}
