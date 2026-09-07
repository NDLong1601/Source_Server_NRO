package nro.models.services_func;

import nro.models.player.Player;

/**
 * SEC-03: Actor-specific Trade State Machine.
 * Enforces explicit transition matrix, actor authorization, idempotency,
 * and single-commit execution guarantees.
 */
public class TradeStateMachine {

    public enum TradeState {
        INVITED,
        OPEN,
        P1_LOCKED,
        P2_LOCKED,
        BOTH_LOCKED,
        P1_ACCEPTED,
        P2_ACCEPTED,
        COMMITTING,
        COMMITTED,
        CANCELLED,
        EXPIRED
    }

    public enum ParticipantState {
        OFFERING,
        LOCKED,
        ACCEPTED
    }

    public enum LockStatus {
        SUCCESS,
        ALREADY_LOCKED,
        UNAUTHORIZED,
        TERMINAL
    }

    public enum AcceptStatus {
        WAITING_OPPONENT,
        READY_TO_COMMIT,
        ALREADY_ACCEPTED,
        NOT_LOCKED,
        UNAUTHORIZED,
        TERMINAL
    }

    private final long player1Id;
    private final long player2Id;

    private TradeState aggregateState = TradeState.INVITED;
    private ParticipantState p1State = ParticipantState.OFFERING;
    private ParticipantState p2State = ParticipantState.OFFERING;

    private final Object lock = new Object();

    public TradeStateMachine(long player1Id, long player2Id) {
        if (player1Id == player2Id) {
            throw new IllegalArgumentException("Self-trade is not permitted");
        }
        this.player1Id = player1Id;
        this.player2Id = player2Id;
    }

    /** Opens a newly registered trade exactly once. */
    public boolean open() {
        synchronized (lock) {
            if (aggregateState != TradeState.INVITED) {
                return false;
            }
            aggregateState = TradeState.OPEN;
            return true;
        }
    }

    public boolean isParticipant(Player player) {
        if (player == null) {
            return false;
        }
        long id = player.id;
        return id == player1Id || id == player2Id;
    }

    public boolean isP1(Player player) {
        return player != null && player.id == player1Id;
    }

    public boolean isP2(Player player) {
        return player != null && player.id == player2Id;
    }

    public TradeState getAggregateState() {
        synchronized (lock) {
            return aggregateState;
        }
    }

    public ParticipantState getParticipantState(Player player) {
        synchronized (lock) {
            if (isP1(player)) {
                return p1State;
            } else if (isP2(player)) {
                return p2State;
            }
            return null;
        }
    }

    public boolean isTerminal() {
        synchronized (lock) {
            return aggregateState == TradeState.COMMITTED
                    || aggregateState == TradeState.CANCELLED
                    || aggregateState == TradeState.EXPIRED;
        }
    }

    /**
     * Offers can only be added or modified while the trade is non-terminal
     * and the specific actor has not yet locked.
     */
    public boolean canModifyOffer(Player player) {
        synchronized (lock) {
            if (aggregateState == TradeState.INVITED || isTerminal() || aggregateState == TradeState.COMMITTING) {
                return false;
            }
            if (isP1(player)) {
                return p1State == ParticipantState.OFFERING;
            } else if (isP2(player)) {
                return p2State == ParticipantState.OFFERING;
            }
            return false;
        }
    }

    public boolean isP1Locked() {
        synchronized (lock) {
            return p1State == ParticipantState.LOCKED || p1State == ParticipantState.ACCEPTED;
        }
    }

    public boolean isP2Locked() {
        synchronized (lock) {
            return p2State == ParticipantState.LOCKED || p2State == ParticipantState.ACCEPTED;
        }
    }

    public boolean isActorLocked(Player player) {
        synchronized (lock) {
            ParticipantState s = getParticipantState(player);
            return s == ParticipantState.LOCKED || s == ParticipantState.ACCEPTED;
        }
    }

    /**
     * Locks the offer of the specified actor. Idempotent.
     */
    public LockStatus lock(Player actor) {
        synchronized (lock) {
            if (aggregateState == TradeState.INVITED || isTerminal() || aggregateState == TradeState.COMMITTING) {
                return LockStatus.TERMINAL;
            }
            if (!isParticipant(actor)) {
                return LockStatus.UNAUTHORIZED;
            }

            if (isP1(actor)) {
                if (p1State != ParticipantState.OFFERING) {
                    return LockStatus.ALREADY_LOCKED;
                }
                p1State = ParticipantState.LOCKED;
                if (p2State == ParticipantState.LOCKED) {
                    aggregateState = TradeState.BOTH_LOCKED;
                } else {
                    aggregateState = TradeState.P1_LOCKED;
                }
                return LockStatus.SUCCESS;
            } else {
                if (p2State != ParticipantState.OFFERING) {
                    return LockStatus.ALREADY_LOCKED;
                }
                p2State = ParticipantState.LOCKED;
                if (p1State == ParticipantState.LOCKED) {
                    aggregateState = TradeState.BOTH_LOCKED;
                } else {
                    aggregateState = TradeState.P2_LOCKED;
                }
                return LockStatus.SUCCESS;
            }
        }
    }

    /**
     * Accepts the trade. Valid only after both participants have locked.
     * Returns READY_TO_COMMIT to exactly one caller whose accept completes the agreement.
     */
    public AcceptStatus accept(Player actor) {
        synchronized (lock) {
            if (isTerminal()) {
                return AcceptStatus.TERMINAL;
            }
            if (aggregateState == TradeState.COMMITTING) {
                return AcceptStatus.ALREADY_ACCEPTED;
            }
            if (!isParticipant(actor)) {
                return AcceptStatus.UNAUTHORIZED;
            }

            // Both must be locked before any accept is valid
            if (p1State == ParticipantState.OFFERING || p2State == ParticipantState.OFFERING) {
                return AcceptStatus.NOT_LOCKED;
            }

            if (isP1(actor)) {
                if (p1State == ParticipantState.ACCEPTED) {
                    return AcceptStatus.ALREADY_ACCEPTED;
                }
                p1State = ParticipantState.ACCEPTED;
                if (p2State == ParticipantState.ACCEPTED) {
                    aggregateState = TradeState.COMMITTING;
                    return AcceptStatus.READY_TO_COMMIT;
                } else {
                    aggregateState = TradeState.P1_ACCEPTED;
                    return AcceptStatus.WAITING_OPPONENT;
                }
            } else {
                if (p2State == ParticipantState.ACCEPTED) {
                    return AcceptStatus.ALREADY_ACCEPTED;
                }
                p2State = ParticipantState.ACCEPTED;
                if (p1State == ParticipantState.ACCEPTED) {
                    aggregateState = TradeState.COMMITTING;
                    return AcceptStatus.READY_TO_COMMIT;
                } else {
                    aggregateState = TradeState.P2_ACCEPTED;
                    return AcceptStatus.WAITING_OPPONENT;
                }
            }
        }
    }

    /**
     * Marks commit success. Exactly once transition from COMMITTING to COMMITTED.
     */
    public boolean markCommitted() {
        synchronized (lock) {
            if (aggregateState == TradeState.COMMITTING) {
                aggregateState = TradeState.COMMITTED;
                return true;
            }
            return false;
        }
    }

    /**
     * Idempotent cancel from any non-terminal state.
     * Returns true only for the first caller that successfully cancels the trade.
     */
    public boolean cancel() {
        synchronized (lock) {
            if (isTerminal() || aggregateState == TradeState.COMMITTING) {
                return false;
            }
            aggregateState = TradeState.CANCELLED;
            return true;
        }
    }

    /**
     * Idempotent timeout expiration from any non-terminal state.
     * Returns true only for the first caller that transitions to EXPIRED.
     */
    public boolean expire() {
        synchronized (lock) {
            if (isTerminal() || aggregateState == TradeState.COMMITTING) {
                return false;
            }
            aggregateState = TradeState.EXPIRED;
            return true;
        }
    }

    /** Commit owner only: converts a failed COMMITTING phase to a terminal state. */
    public boolean failCommit() {
        synchronized (lock) {
            if (aggregateState != TradeState.COMMITTING) {
                return false;
            }
            aggregateState = TradeState.CANCELLED;
            return true;
        }
    }
}
