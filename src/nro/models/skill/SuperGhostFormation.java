package nro.models.skill;

import java.util.function.IntSupplier;

/** Pure, server-authoritative lifecycle. No networking or client clock dependency. */
public final class SuperGhostFormation {
    public static final int SUMMONING = 0, ORBITING = 1, FLYING = 2, RETURNING = 3, SPENT = 4;
    // V2 events deliberately do not reuse the legacy 0..3 payloads.
    public static final int SPAWN = 10, LAUNCH = 11, IMPACT = 12, RECALL = 13,
            END = 14, SNAPSHOT = 15, MOVE = 16, READY = 17;
    public static final int SUMMON_FRAME_MS = 180, SUMMON_STAGGER_MS = 55;
    public static final int REGROUP_MS = 250, HIT_DISTANCE = 12, SPEED = 420;
    public static final int NEXT_LAUNCH_MS = 180, MAX_FLIGHT_MS = 4_000;

    public interface Target {
        boolean valid();
        int x();
        int y();
    }

    public interface Listener {
        void event(int event, int ghostIndex, long now);
        void hit(Ghost ghost, Target target);
    }

    public static final class Ghost {
        public final int index;
        public final int damageShare;
        public int state = SUMMONING;
        public int variant;
        public double x, y;
        public long stateAt;
        private double layoutX, layoutY;
        private long layoutAt;

        private Ghost(int index, int count, long now) {
            this.index = index;
            damageShare = 100 / count + (index < 100 % count ? 1 : 0);
            stateAt = now;
        }
    }

    public final Ghost[] ghosts;
    public final int summonMs, lifetimeMs;
    public final long startedAt, expiresAt;
    public Target target;
    public boolean ended;
    private final Listener listener;
    private final IntSupplier variantSource;
    private Ghost active;
    private long lastTick, nextLaunchAt;
    private int ownerX, ownerY, layoutMask = -1;

    public SuperGhostFormation(int level, int count, long now, int x, int y,
            IntSupplier variantSource, Listener listener) {
        count = Math.max(1, Math.min(7, count));
        ghosts = new Ghost[count];
        startedAt = lastTick = now;
        summonMs = 3 * SUMMON_FRAME_MS + (count - 1) * SUMMON_STAGGER_MS;
        lifetimeMs = lifetimeForLevel(level);
        expiresAt = now + summonMs + lifetimeMs;
        this.listener = listener;
        this.variantSource = variantSource;
        ownerX = x;
        ownerY = y;
        for (int i = 0; i < count; i++) {
            ghosts[i] = new Ghost(i, count, now);
            ghosts[i].x = x + spawnX(i, count);
            ghosts[i].y = y + spawnY(i, count);
        }
    }

    public static int lifetimeForLevel(int level) {
        return 20_000 + (Math.max(1, Math.min(7, level)) - 1) * 5_000;
    }

    public static double spawnX(int index, int count) {
        return (index - (count - 1) / 2.0) * (18 + Math.max(0, count - 3) * 2);
    }

    public static double spawnY(int index, int count) {
        return -54 - (count - 1) * 3 + Math.abs(index - (count - 1) / 2.0) * 10;
    }

    public static double orbitX(int rank, int count, long elapsed) {
        return Math.cos(elapsed * 0.006 + Math.PI * 2 * rank / count) * (42 + (count - 1) * 8);
    }

    public static double orbitY(int rank, int count, long elapsed) {
        return -38 - (count - 1) * 4
                + Math.sin(elapsed * 0.006 + Math.PI * 2 * rank / count) * (18 + (count - 1) * 4);
    }

    public int remaining() {
        int count = 0;
        for (Ghost ghost : ghosts) if (ghost.state != SPENT) count++;
        return count;
    }

    public boolean command(Target selected, long now) {
        if (ended || now >= expiresAt || active != null || target != null
                || remaining() == 0 || selected == null || !selected.valid()) return false;
        target = selected;
        nextLaunchAt = Math.max(now, startedAt + summonMs);
        return true;
    }

    public void tick(long now, int x, int y) {
        if (ended) return;
        if (now >= expiresAt) {
            end(now);
            return;
        }
        double step = SPEED * Math.max(0, Math.min(100, now - lastTick)) / 1000.0;
        lastTick = now;
        ownerX = x;
        ownerY = y;
        if (now < startedAt + summonMs) {
            for (Ghost ghost : ghosts) {
                ghost.x = x + spawnX(ghost.index, ghosts.length);
                ghost.y = y + spawnY(ghost.index, ghosts.length);
            }
            return;
        }
        boolean ready = ghosts[0].state == SUMMONING;
        if (ready) {
            for (Ghost ghost : ghosts) setState(ghost, ORBITING, now);
        }
        updateOrbit(now);
        if (ready) listener.event(READY, -1, now);
        if (active != null && active.state == RETURNING) {
            returnToOrbit(now, step);
            return;
        }
        if (target != null && !target.valid()) {
            recall(now);
            return;
        }
        if (active == null) {
            if (target == null || now < nextLaunchAt) return;
            for (Ghost ghost : ghosts) {
                if (ghost.state == ORBITING) {
                    active = ghost;
                    ghost.variant = Math.floorMod(variantSource.getAsInt(), 3);
                    setState(ghost, FLYING, now);
                    updateOrbit(now);
                    listener.event(LAUNCH, ghost.index, now);
                    break;
                }
            }
            // Never move/detonate in the launch tick: every shot has a visible departure.
            return;
        }
        if (now - active.stateAt >= MAX_FLIGHT_MS) {
            recall(now);
            return;
        }
        double dx = target.x() - active.x, dy = target.y() - active.y;
        double distance = Math.hypot(dx, dy);
        if (distance <= HIT_DISTANCE + step) {
            Ghost hit = active;
            hit.x = target.x();
            hit.y = target.y();
            setState(hit, SPENT, now); // mark before invoking combat; never hit twice
            active = null;
            Target victim = target;
            listener.hit(hit, victim);
            if (!victim.valid()) target = null;
            nextLaunchAt = now + NEXT_LAUNCH_MS;
            listener.event(IMPACT, hit.index, now);
            if (remaining() == 0) end(now);
        } else {
            active.x += dx * step / distance;
            active.y += dy * step / distance;
            listener.event(MOVE, active.index, now);
        }
    }

    private void recall(long now) {
        target = null;
        if (active != null) setState(active, RETURNING, now);
        updateOrbit(now);
        listener.event(RECALL, active == null ? -1 : active.index, now);
    }

    private void returnToOrbit(long now, double step) {
        int count = orbitCount(), rank = 0;
        for (Ghost ghost : ghosts) {
            if (ghost == active) break;
            if (inOrbit(ghost)) rank++;
        }
        double destX = ownerX + orbitX(rank, count, now - startedAt);
        double destY = ownerY + orbitY(rank, count, now - startedAt);
        double dx = destX - active.x, dy = destY - active.y;
        double distance = Math.hypot(dx, dy);
        // A fast-moving owner must not leave a returning ghost stuck forever.
        if (distance <= step * 1.6 + 12 || now - active.stateAt >= 2_000) {
            Ghost returned = active;
            returned.x = destX;
            returned.y = destY;
            setState(returned, ORBITING, now);
            active = null;
            listener.event(READY, returned.index, now);
        } else {
            active.x += dx * step * 1.6 / distance;
            active.y += dy * step * 1.6 / distance;
            listener.event(MOVE, active.index, now);
        }
    }

    private static boolean inOrbit(Ghost ghost) {
        return ghost.state == ORBITING || ghost.state == RETURNING;
    }

    private int orbitCount() {
        int count = 0;
        for (Ghost ghost : ghosts) if (inOrbit(ghost)) count++;
        return Math.max(1, count);
    }

    private void updateOrbit(long now) {
        int mask = 0;
        for (Ghost ghost : ghosts) if (inOrbit(ghost)) mask |= 1 << ghost.index;
        if (layoutMask != mask) {
            layoutMask = mask;
            for (Ghost ghost : ghosts) {
                ghost.layoutX = ghost.x - ownerX;
                ghost.layoutY = ghost.y - ownerY;
                ghost.layoutAt = now;
            }
        }
        int count = orbitCount(), rank = 0;
        for (Ghost ghost : ghosts) {
            if (!inOrbit(ghost)) continue;
            if (ghost.state == ORBITING) {
                double t = Math.min(1, (now - ghost.layoutAt) / (double) REGROUP_MS);
                t = t * t * (3 - 2 * t);
                ghost.x = ownerX + ghost.layoutX * (1 - t) + orbitX(rank, count, now - startedAt) * t;
                ghost.y = ownerY + ghost.layoutY * (1 - t) + orbitY(rank, count, now - startedAt) * t;
            }
            rank++;
        }
    }

    private static void setState(Ghost ghost, int state, long now) {
        ghost.state = state;
        ghost.stateAt = now;
    }

    public void end(long now) {
        if (ended) return;
        ended = true;
        target = null;
        listener.event(END, -1, now);
    }
}
