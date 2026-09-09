package nro.models.player;

import java.time.LocalDateTime;
import nro.models.consts.ConstTaskBadges;
import nro.models.task.BadgesTaskService;

/** Domain facade for event mutations; persisted flags live in PlayerEventState. */
public final class PlayerEvent {

    private int eventPoint;
    private final Player player;
    private PlayerEventState state = new PlayerEventState();

    public PlayerEvent(Player player) {
        this.player = player;
    }
    
    public synchronized int getEventPoint() {
        return eventPoint;
    }

    public synchronized void setEventPoint(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("eventPoint must not be negative");
        }
        eventPoint = value;
        markDirty();
    }

    public synchronized void addEventPoint(int amount) {
        grantEventPoint(amount);
    }

    public synchronized void grantEventPoint(int amount) {
        if (amount <= 0) {
            return;
        }
        eventPoint = Math.addExact(eventPoint, amount);
        markDirty();
        BadgesTaskService.updateCountBagesTask(player, ConstTaskBadges.XSMAX, amount);
    }

    public synchronized void subEventPoint(int amount) {
        if (amount <= 0 || amount > eventPoint) {
            throw new IllegalArgumentException("Invalid event point debit");
        }
        eventPoint -= amount;
        markDirty();
    }

    public synchronized PlayerEventState state() {
        return state;
    }

    public synchronized void restoreState(PlayerEventState restored) {
        state = restored == null ? new PlayerEventState() : restored;
    }

    public boolean isFreeGemClaimAvailable() {
        return state().isFreeGemClaimAvailable();
    }

    public boolean isClanCapsuleClaimAvailable() {
        return state().isClanCapsuleClaimAvailable();
    }

    public void setFreeGemClaimAvailable(boolean value) {
        state().setFreeGemClaimAvailable(value);
        markDirty();
    }

    public void setClanCapsuleClaimAvailable(boolean value) {
        state().setClanCapsuleClaimAvailable(value);
        markDirty();
    }

    public LocalDateTime getLastCheckIn() { return state().lastCheckIn(); }

    public void setLastCheckIn(LocalDateTime value) {
        state().setLastCheckIn(value);
        markDirty();
    }

    public void update() {
    }

    private void markDirty() {
        if (player != null) {
            player.getPersistenceState().markDirty(PlayerPersistenceComponent.EVENT);
        }
    }

}
