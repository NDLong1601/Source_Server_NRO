package nro.models.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import nro.models.map.Zone;
import nro.models.mob.Mob;
import nro.models.network.Message;
import nro.models.player.Player;
import nro.models.skill.Skill;
import nro.models.skill.SuperGhostFormation;
import nro.models.skill.SuperGhostPacket;
import nro.models.utils.Logger;
import nro.models.utils.SkillUtil;
import nro.models.utils.Util;

/**
 * Server-authoritative mechanics for the four techniques added after the
 * original skill table.  The class deliberately does not depend on any new
 * image/effect asset: combat state is valid before client VFX are installed.
 */
public final class CustomSkillService {

    private static final CustomSkillService INSTANCE = new CustomSkillService();
    // Matches the clear interior of the 112px client VFX. A target may move
    // inside the prison, but every movement path is clamped at this radius.
    private static final int BARRIER_RADIUS = 42;
    private static final int BARRIER_PULSE_MS = 1_000;
    private static final int HELLZONE_CAST_DURATION_MS = 1_600;
    private static final int GHOST_TICK_MS = 100;
    private final AtomicInteger ghostCastSequence = new AtomicInteger();

    private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(2, runnable -> {
        Thread thread = new Thread(runnable, "custom-skill-runtime");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<Long, GhostCast> ghostCasts = new ConcurrentHashMap<>();

    private CustomSkillService() {
        scheduler.setRemoveOnCancelPolicy(true);
    }

    public static CustomSkillService gI() {
        return INSTANCE;
    }

    public boolean castBarrierPrison(Player caster, Player playerTarget, Mob mobTarget) {
        if (caster == null || caster.zone == null || caster.playerSkill == null
                || caster.playerSkill.skillSelect == null || caster.isDie()) {
            return false;
        }
        Skill skill = caster.playerSkill.skillSelect;
        if (skill.template == null || skill.template.id != Skill.BARRIER_PRISON) {
            return false;
        }
        int range = Math.max(80, skill.dx);
        if (playerTarget != null) {
            if (playerTarget == caster || playerTarget.zone != caster.zone || playerTarget.isDie()
                    || Util.getDistance(caster, playerTarget) > range
                    || !SkillService.gI().canAttackPlayer(caster, playerTarget)) {
                return false;
            }
            startBarrierPrison(playerTarget, caster, skill);
            if (playerTarget.effectSkill == null || !playerTarget.effectSkill.isBarrierPrison) {
                return false;
            }
            sendBarrierPrison(caster, playerTarget.effectSkill.barrierPrisonCenterX,
                    playerTarget.effectSkill.barrierPrisonCenterY, playerTarget.effectSkill.timeBarrierPrison,
                    2, (int) playerTarget.id);
            SkillService.gI().affterUseSkill(caster, skill.template.id, true);
            return true;
        }
        if (mobTarget != null && !mobTarget.isDie() && mobTarget.zone == caster.zone
                && Util.getDistance(caster, mobTarget) <= range) {
            startBarrierPrison(mobTarget, caster, skill);
            if (mobTarget.effectSkill == null || !mobTarget.effectSkill.isBarrierPrison) {
                return false;
            }
            sendBarrierPrison(caster, mobTarget.effectSkill.barrierPrisonCenterX,
                    mobTarget.effectSkill.barrierPrisonCenterY, mobTarget.effectSkill.timeBarrierPrison,
                    1, mobTarget.id);
            SkillService.gI().affterUseSkill(caster, skill.template.id, true);
            return true;
        }
        return false;
    }

    private void startBarrierPrison(Player target, Player owner, Skill skill) {
        if (target.effectSkill == null) {
            return;
        }
        if (target.effectSkill.isBarrierPrison) {
            removeBarrierPrison(target);
        }
        target.effectSkill.isBarrierPrison = true;
        target.effectSkill.barrierPrisonOwner = owner;
        target.effectSkill.barrierPrisonSkill = new Skill(skill);
        target.effectSkill.barrierPrisonCenterX = target.location.x;
        target.effectSkill.barrierPrisonCenterY = target.location.y;
        target.effectSkill.barrierPrisonRadius = BARRIER_RADIUS;
        target.effectSkill.lastTimeBarrierPrison = System.currentTimeMillis();
        target.effectSkill.lastTimeBarrierPrisonPulse = target.effectSkill.lastTimeBarrierPrison;
        target.effectSkill.timeBarrierPrison = Math.max(1_000, skill.dy);
        target.effectSkill.barrierPrisonDamagePercent = Math.max(1, skill.damage);
    }

    private void startBarrierPrison(Mob target, Player owner, Skill skill) {
        target.effectSkill.removeBarrierPrison();
        target.effectSkill.isBarrierPrison = true;
        target.effectSkill.barrierPrisonOwner = owner;
        target.effectSkill.barrierPrisonSkill = new Skill(skill);
        target.effectSkill.barrierPrisonCenterX = target.location.x;
        target.effectSkill.barrierPrisonCenterY = target.location.y;
        target.effectSkill.barrierPrisonRadius = BARRIER_RADIUS;
        target.effectSkill.lastTimeBarrierPrison = System.currentTimeMillis();
        target.effectSkill.lastTimeBarrierPrisonPulse = target.effectSkill.lastTimeBarrierPrison;
        target.effectSkill.timeBarrierPrison = Math.max(1_000, skill.dy);
    }

    /** Sends the visual-only Barrier Prison timeline after the server has accepted the cast. */
    private void sendBarrierPrison(Player caster, int centerX, int centerY, int duration,
            int targetType, int targetId) {
        Message message = null;
        try {
            message = new Message(-45);
            message.writer().writeByte(24);
            message.writer().writeInt((int) caster.id);
            message.writer().writeShort(Skill.BARRIER_PRISON);
            message.writer().writeShort(centerX);
            message.writer().writeShort(centerY);
            message.writer().writeShort(Math.max(1_000, Math.min(Short.MAX_VALUE, duration)));
            // The target descriptor lets each client snapshot the target's real
            // on-screen position. Normal mobs roam locally, so their visual x/y
            // can differ from the server spawn coordinate.
            message.writer().writeByte(targetType);
            message.writer().writeInt(targetId);
            Service.gI().sendMessAllPlayerInMap(caster, message);
        } catch (IOException e) {
            Logger.logException(CustomSkillService.class, e,
                    "Không gửi được hiệu ứng Barrier Prison");
        } finally {
            if (message != null) {
                message.cleanup();
            }
        }
    }

    public void updateBarrierPrison(Player target) {
        if (target == null || target.effectSkill == null || !target.effectSkill.isBarrierPrison) {
            return;
        }
        long now = System.currentTimeMillis();
        Player owner = target.effectSkill.barrierPrisonOwner;
        if (target.isDie() || target.zone == null || owner == null || owner.isDie()
                || owner.zone != target.zone
                || now - target.effectSkill.lastTimeBarrierPrison >= target.effectSkill.timeBarrierPrison) {
            removeBarrierPrison(target);
            return;
        }
        int[] boundedPosition = clampBarrierPosition(target, target.location.x, target.location.y);
        if (boundedPosition[0] != target.location.x || boundedPosition[1] != target.location.y) {
            Service.gI().setPos(target, boundedPosition[0], boundedPosition[1]);
        }
        if (now - target.effectSkill.lastTimeBarrierPrisonPulse >= BARRIER_PULSE_MS) {
            target.effectSkill.lastTimeBarrierPrisonPulse = now;
            Skill pulseSkill = target.effectSkill.barrierPrisonSkill;
            if (pulseSkill == null) {
                return;
            }
            pulseSkill = new Skill(pulseSkill);
            pulseSkill.damage = target.effectSkill.barrierPrisonDamagePercent;
            pulseSkill.manaUse = 0;
            pulseSkill.coolDown = 0;
            synchronized (owner.playerSkill) {
                Skill previous = owner.playerSkill.skillSelect;
                owner.playerSkill.skillSelect = pulseSkill;
                try {
                    SkillService.gI().playerAttackPlayer(owner, target, false);
                } finally {
                    owner.playerSkill.skillSelect = previous;
                }
            }
        }
    }

    public void updateBarrierPrison(Mob target) {
        if (target == null || target.effectSkill == null || !target.effectSkill.isBarrierPrison) {
            return;
        }
        long now = System.currentTimeMillis();
        Player owner = target.effectSkill.barrierPrisonOwner;
        Skill skill = target.effectSkill.barrierPrisonSkill;
        if (target.isDie() || target.zone == null || owner == null || owner.isDie()
                || owner.zone != target.zone || skill == null
                || now - target.effectSkill.lastTimeBarrierPrison >= target.effectSkill.timeBarrierPrison) {
            target.effectSkill.removeBarrierPrison();
            return;
        }
        int[] boundedPosition = clampBarrierPosition(target, target.location.x, target.location.y);
        target.location.x = boundedPosition[0];
        target.location.y = boundedPosition[1];
        if (now - target.effectSkill.lastTimeBarrierPrisonPulse >= BARRIER_PULSE_MS) {
            target.effectSkill.lastTimeBarrierPrisonPulse = now;
            synchronized (owner.playerSkill) {
                Skill previous = owner.playerSkill.skillSelect;
                owner.playerSkill.skillSelect = skill;
                try {
                    SkillService.gI().playerAttackMob(owner, target, false, false);
                } finally {
                    owner.playerSkill.skillSelect = previous;
                }
            }
        }
    }

    public void removeBarrierPrison(Player target) {
        if (target == null || target.effectSkill == null) {
            return;
        }
        target.effectSkill.isBarrierPrison = false;
        target.effectSkill.barrierPrisonOwner = null;
        target.effectSkill.barrierPrisonSkill = null;
        target.effectSkill.barrierPrisonCenterX = 0;
        target.effectSkill.barrierPrisonCenterY = 0;
        target.effectSkill.barrierPrisonRadius = 0;
        target.effectSkill.lastTimeBarrierPrison = 0L;
        target.effectSkill.lastTimeBarrierPrisonPulse = 0L;
        target.effectSkill.barrierPrisonDamagePercent = 0;
    }

    public void removeBarrierPrison(Mob target) {
        if (target != null && target.effectSkill != null) {
            target.effectSkill.removeBarrierPrison();
        }
    }

    /** Returns a bounded position for a player inside their active prison. */
    public int[] clampBarrierPosition(Player target, int x, int y) {
        if (target == null || target.effectSkill == null || !target.effectSkill.isBarrierPrison) {
            return new int[]{x, y};
        }
        long now = System.currentTimeMillis();
        if (now - target.effectSkill.lastTimeBarrierPrison >= target.effectSkill.timeBarrierPrison) {
            removeBarrierPrison(target);
            return new int[]{x, y};
        }
        int centerX = target.effectSkill.barrierPrisonCenterX;
        int centerY = target.effectSkill.barrierPrisonCenterY;
        int radius = Math.max(24, target.effectSkill.barrierPrisonRadius);
        int offsetX = x - centerX;
        int offsetY = y - centerY;
        long distanceSquared = (long) offsetX * offsetX + (long) offsetY * offsetY;
        if (distanceSquared <= (long) radius * radius) {
            return new int[]{x, y};
        }
        double distance = Math.sqrt(distanceSquared);
        return new int[]{centerX + (int) Math.round(offsetX * radius / distance),
            centerY + (int) Math.round(offsetY * radius / distance)};
    }

    /** Returns a bounded position for a mob inside its active prison. */
    public int[] clampBarrierPosition(Mob target, int x, int y) {
        if (target == null || target.effectSkill == null || !target.effectSkill.isBarrierPrison) {
            return new int[]{x, y};
        }
        int centerX = target.effectSkill.barrierPrisonCenterX;
        int centerY = target.effectSkill.barrierPrisonCenterY;
        int radius = Math.max(24, target.effectSkill.barrierPrisonRadius);
        return clampToCircle(centerX, centerY, radius, x, y);
    }

    private int[] clampToCircle(int centerX, int centerY, int radius, int x, int y) {
        int offsetX = x - centerX;
        int offsetY = y - centerY;
        long distanceSquared = (long) offsetX * offsetX + (long) offsetY * offsetY;
        if (distanceSquared <= (long) radius * radius) {
            return new int[]{x, y};
        }
        double distance = Math.sqrt(distanceSquared);
        return new int[]{centerX + (int) Math.round(offsetX * radius / distance),
            centerY + (int) Math.round(offsetY * radius / distance)};
    }

    public boolean activateEnergyAbsorption(Player player) {
        if (player == null || player.effectSkill == null || player.nPoint == null
                || player.playerSkill == null || player.playerSkill.skillSelect == null
                || player.effectSkill.isShielding || player.effectSkill.isEnergyAbsorption) {
            return false;
        }
        Skill skill = player.playerSkill.skillSelect;
        if (skill.template == null || skill.template.id != Skill.ENERGY_ABSORPTION) {
            return false;
        }
        player.effectSkill.isEnergyAbsorption = true;
        player.effectSkill.lastTimeEnergyAbsorption = System.currentTimeMillis();
        player.effectSkill.timeEnergyAbsorption = Math.max(1_000, skill.dy);
        player.effectSkill.energyAbsorptionPercent = Math.max(1, Math.min(100, skill.damage));
        player.effectSkill.energyAbsorptionCapacity = Math.max(1L,
                (long) player.nPoint.hpMax * Math.max(1, skill.dx) / 100L);
        player.effectSkill.energyAbsorptionUsed = 0L;
        player.effectSkill.energyAbsorptionHits = Math.max(1, skill.maxFight);
        SkillService.gI().affterUseSkill(player, skill.template.id, true);
        return true;
    }

    /**
     * Reduces one incoming energy hit and returns the remaining HP damage.
     * This is called after armor and before the legacy shield/equipment layers.
     */
    public long absorbEnergyDamage(Player target, Player attacker, long damage) {
        if (target == null || attacker == null || damage <= 0 || target.effectSkill == null
                || !target.effectSkill.isEnergyAbsorption || !SkillUtil.isEnergySkill(attacker)) {
            return damage;
        }
        synchronized (target.effectSkill) {
            long now = System.currentTimeMillis();
            if (now - target.effectSkill.lastTimeEnergyAbsorption >= target.effectSkill.timeEnergyAbsorption
                    || target.effectSkill.energyAbsorptionHits <= 0
                    || target.effectSkill.energyAbsorptionUsed >= target.effectSkill.energyAbsorptionCapacity) {
                removeEnergyAbsorption(target);
                return damage;
            }
            long requested = Math.max(1L, damage * target.effectSkill.energyAbsorptionPercent / 100L);
            long remainingCapacity = target.effectSkill.energyAbsorptionCapacity
                    - target.effectSkill.energyAbsorptionUsed;
            long absorbed = Math.min(requested, remainingCapacity);
            target.effectSkill.energyAbsorptionUsed += absorbed;
            target.effectSkill.energyAbsorptionHits--;
            long mpRestore = absorbed / 2L;
            if (mpRestore > 0) {
                PlayerService.gI().hoiPhuc(target, 0, mpRestore);
            }
            if (target.effectSkill.energyAbsorptionHits <= 0
                    || target.effectSkill.energyAbsorptionUsed >= target.effectSkill.energyAbsorptionCapacity) {
                removeEnergyAbsorption(target);
            }
            return Math.max(0L, damage - absorbed);
        }
    }

    public void removeEnergyAbsorption(Player target) {
        if (target == null || target.effectSkill == null) {
            return;
        }
        target.effectSkill.isEnergyAbsorption = false;
        target.effectSkill.lastTimeEnergyAbsorption = 0L;
        target.effectSkill.timeEnergyAbsorption = 0;
        target.effectSkill.energyAbsorptionPercent = 0;
        target.effectSkill.energyAbsorptionCapacity = 0L;
        target.effectSkill.energyAbsorptionUsed = 0L;
        target.effectSkill.energyAbsorptionHits = 0;
    }

    public boolean startHellzoneGrenade(Player caster, Short requestedX, Short requestedY) {
        if (caster == null || caster.zone == null || caster.isDie() || requestedX == null
                || requestedY == null || caster.playerSkill == null
                || caster.playerSkill.skillSelect == null) {
            return false;
        }
        Skill skill = caster.playerSkill.skillSelect;
        if (skill.template == null || skill.template.id != Skill.HELLZONE_GRENADE) {
            return false;
        }
        int range = Math.max(160, skill.dx);
        if (Util.getDistance(caster.location.x, caster.location.y, requestedX, requestedY) > range) {
            return false;
        }
        HellzoneCast cast = new HellzoneCast(caster, new Skill(skill), caster.zone,
                requestedX, requestedY);
        int startX = caster.location.x;
        int startY = caster.location.y - 24;
        int endX = requestedX;
        int endY = requestedY - 24;
        byte direction = (byte) (endX < startX ? -1 : 1);
        sendHellzoneGrenade(caster, startX, startY, endX, endY, direction,
                HELLZONE_CAST_DURATION_MS);
        scheduler.schedule(() -> resolveHellzone(cast), HELLZONE_CAST_DURATION_MS,
                TimeUnit.MILLISECONDS);
        return true;
    }

    /**
     * Sends a visual-only, server-authoritative cast timeline.  Damage is still
     * resolved exclusively by {@link #resolveHellzone(HellzoneCast)} after this
     * timeline finishes, so clients cannot influence targets or timing.
     */
    private void sendHellzoneGrenade(Player caster, int startX, int startY,
            int endX, int endY, byte direction, int duration) {
        Message message = null;
        try {
            message = new Message(-45);
            message.writer().writeByte(23);
            message.writer().writeInt((int) caster.id);
            message.writer().writeShort(Skill.HELLZONE_GRENADE);
            message.writer().writeShort(startX);
            message.writer().writeShort(startY);
            message.writer().writeShort(endX);
            message.writer().writeShort(endY);
            message.writer().writeByte(direction);
            message.writer().writeShort(duration);
            Service.gI().sendMessAllPlayerInMap(caster, message);
        } catch (IOException e) {
            Logger.logException(CustomSkillService.class, e,
                    "Không gửi được hiệu ứng Hellzone Grenade");
        } finally {
            if (message != null) {
                message.cleanup();
            }
        }
    }

    private void resolveHellzone(HellzoneCast cast) {
        Player caster = cast.caster;
        if (caster == null || caster.zone != cast.zone || caster.isDie()) {
            return;
        }
        List<CustomTarget> targets = collectTargets(caster, cast.zone, cast.x, cast.y,
                Math.max(60, cast.skill.dy), true);
        targets.sort(Comparator.comparingInt(target -> target.distance));
        int maxTargets = Math.max(1, cast.skill.maxFight);
        int hits = Math.min(maxTargets, targets.size());
        if (hits == 0) {
            return;
        }
        synchronized (caster.playerSkill) {
            Skill previous = caster.playerSkill.skillSelect;
            caster.playerSkill.skillSelect = cast.skill;
            try {
                for (int i = 0; i < hits; i++) {
                    CustomTarget target = targets.get(i);
                    if (target.player != null) {
                        SkillService.gI().playerAttackPlayer(caster, target.player, false);
                    } else {
                        SkillService.gI().playerAttackMob(caster, target.mob, false, false,
                                100, false);
                    }
                }
            } finally {
                caster.playerSkill.skillSelect = previous;
            }
        }
        SkillMasteryService.gI().recordValidUse(caster, cast.skill);
    }

    /**
     * Begins the summon.  A target is deliberately not required here: ghosts
     * circle their owner until the player explicitly orders the attack.
     */
    public boolean startSuperGhostKamikaze(Player caster) {
        if (caster == null || caster.zone == null || caster.isDie() || caster.playerSkill == null
                || caster.playerSkill.skillSelect == null) return false;
        Skill skill = caster.playerSkill.skillSelect;
        if (skill.template == null || skill.template.id != Skill.SUPER_GHOST_KAMIKAZE) return false;
        GhostCast cast = new GhostCast(caster, new Skill(skill), caster.zone);
        if (ghostCasts.putIfAbsent(caster.id, cast) != null) return false;
        cast.event(SuperGhostFormation.SPAWN, -1, System.currentTimeMillis());
        cast.future = scheduler.scheduleAtFixedRate(cast::tick, 0, GHOST_TICK_MS, TimeUnit.MILLISECONDS);
        return true;
    }

    public boolean hasSuperGhostKamikaze(Player caster) {
        return caster != null && ghostCasts.containsKey(caster.id);
    }

    public boolean launchSuperGhostKamikaze(Player caster, int castId, int targetType, int targetId) {
        GhostCast cast = caster == null ? null : ghostCasts.get(caster.id);
        return cast != null && cast.castId == castId && cast.requestLaunch(targetType, targetId);
    }

    public void cancelSuperGhostKamikaze(Player caster) {
        GhostCast cast = caster == null ? null : ghostCasts.get(caster.id);
        if (cast != null) cast.cancel();
    }

    /** Called after the joining client has received the map's characters/mobs. */
    public void syncSuperGhostKamikaze(Player viewer) {
        if (viewer == null || viewer.zone == null) return;
        for (GhostCast cast : ghostCasts.values()) {
            if (cast.zone == viewer.zone) cast.snapshot(viewer);
        }
    }

    private final class GhostCast implements SuperGhostFormation.Listener {
        private final Player caster;
        private final Skill skill;
        private final Zone zone;
        private final int castId = ghostCastSequence.incrementAndGet();
        private final SuperGhostFormation formation;
        private ScheduledFuture<?> future;
        private int sequence;
        private boolean masteryRecorded;

        private GhostCast(Player caster, Skill skill, Zone zone) {
            this.caster = caster;
            this.skill = skill;
            this.zone = zone;
            formation = new SuperGhostFormation(skill.point, skill.maxFight, System.currentTimeMillis(),
                    caster.location.x, caster.location.y,
                    () -> java.util.concurrent.ThreadLocalRandom.current().nextInt(3), this);
        }

        private boolean validCaster() {
            return caster.zone == zone && caster.nPoint != null && caster.playerSkill != null
                    && !caster.isDie() && (!caster.isPl()
                    || (caster.getSession() != null && caster.getSession().isConnected()));
        }

        private synchronized boolean requestLaunch(int targetType, int targetId) {
            if (!validCaster()) return false;
            CustomTarget selected = null;
            // Resolve the exact entity selected by the player, not the nearest
            // entity to the cursor (which can silently select an overlapping mob).
            if (targetType == 1) {
                for (Mob mob : zone.mobs) {
                    if (mob != null && mob.id == targetId) {
                        selected = new CustomTarget(mob, 0);
                        break;
                    }
                }
            } else if (targetType == 2) {
                for (Player player : new ArrayList<>(zone.getHumanoids())) {
                    if (player != null && player.id == targetId) {
                        selected = new CustomTarget(player, 0);
                        break;
                    }
                }
            }
            if (selected == null || Util.getDistance(caster.location.x, caster.location.y,
                    selected.x(), selected.y()) > Math.max(160, skill.dx)) return false;
            return formation.command(new GhostTarget(selected), System.currentTimeMillis());
        }

        private synchronized void tick() {
            try {
                if (!validCaster()) {
                    formation.end(System.currentTimeMillis());
                } else {
                    formation.tick(System.currentTimeMillis(), caster.location.x, caster.location.y);
                }
            } catch (Exception e) {
                Logger.logException(CustomSkillService.class, e, "Lỗi cập nhật Super Ghost Kamikaze");
                formation.end(System.currentTimeMillis());
            }
            if (formation.ended) {
                ghostCasts.remove(caster.id, this);
                if (future != null) future.cancel(false);
            }
        }

        private synchronized void cancel() {
            formation.end(System.currentTimeMillis());
            ghostCasts.remove(caster.id, this);
            if (future != null) future.cancel(false);
        }

        private synchronized void snapshot(Player viewer) {
            if (validCaster() && !formation.ended) {
                sendState(SuperGhostFormation.SNAPSHOT, -1, System.currentTimeMillis(), viewer);
            }
        }

        @Override
        public void event(int event, int ghostIndex, long now) {
            sendState(event, ghostIndex, now, null);
        }

        private void sendState(int event, int ghostIndex, long now, Player viewer) {
            Message message = null;
            try {
                message = new Message(-45);
                GhostTarget selected = formation.target instanceof GhostTarget
                        ? (GhostTarget) formation.target : null;
                SuperGhostPacket.write(message.writer(), (int) caster.id, castId,
                        ++sequence, formation, event, ghostIndex, now, caster.location.x, caster.location.y,
                        selected == null ? 0 : selected.type(), selected == null ? -1 : selected.id());
                if (viewer != null) viewer.sendMessage(message);
                else if (caster.zone == zone) Service.gI().sendMessAllPlayerInMap(caster, message);
                else Service.gI().sendMessAllPlayerInMap(zone, message);
            } catch (IOException e) {
                Logger.logException(CustomSkillService.class, e, "Không gửi được hiệu ứng Siêu Ma Cảm Tử");
            } finally {
                if (message != null) message.cleanup();
            }
        }

        @Override
        public void hit(SuperGhostFormation.Ghost ghost, SuperGhostFormation.Target target) {
            CustomTarget primary = ((GhostTarget) target).entity;
            // The initial allocation is immutable: five remaining ghosts must
            // not suddenly be scaled from 1/7 to 1/5 of the total skill damage.
            int damageShare = ghost.damageShare;
            int hitX = primary.x(), hitY = primary.y();
            synchronized (caster.playerSkill) {
                Skill previous = caster.playerSkill.skillSelect;
                caster.playerSkill.skillSelect = skill;
                try {
                    if (primary.player != null) {
                        SkillService.gI().playerAttackPlayer(caster, primary.player, false, damageShare);
                    } else {
                        SkillService.gI().playerAttackMob(caster, primary.mob, false, false, damageShare, false);
                    }
                    for (CustomTarget secondary : collectTargets(caster, zone, hitX, hitY,
                            Math.max(40, skill.dy), false)) {
                        if (sameTarget(primary, secondary)) continue;
                        if (secondary.player != null) {
                            SkillService.gI().playerAttackPlayer(caster, secondary.player, false,
                                    Math.max(1, damageShare * 35 / 100));
                        } else {
                            SkillService.gI().playerAttackMob(caster, secondary.mob, false, false,
                                    Math.max(1, damageShare * 35 / 100), false);
                        }
                    }
                } finally {
                    caster.playerSkill.skillSelect = previous;
                }
            }
            if (!masteryRecorded) {
                masteryRecorded = true;
                SkillMasteryService.gI().recordValidUse(caster, skill);
            }
        }

        private boolean sameTarget(CustomTarget first, CustomTarget second) {
            return first.player != null ? first.player == second.player : first.mob == second.mob;
        }

        private final class GhostTarget implements SuperGhostFormation.Target {
            private final CustomTarget entity;
            private final long revivedAt;
            private GhostTarget(CustomTarget entity) {
                this.entity = entity;
                revivedAt = entity.player == null ? 0 : entity.player.lastTimeRevived;
            }
            public int type() { return entity.player == null ? 1 : 2; }
            public int id() { return entity.player == null ? entity.mob.id : (int) entity.player.id; }
            @Override public int x() { return entity.x(); }
            @Override public int y() { return entity.y() - 18; }
            @Override public boolean valid() {
                if (entity.player != null) {
                    Player victim = entity.player;
                    return victim != caster && victim.zone == zone && victim.nPoint != null
                            && !victim.isDie() && victim.lastTimeRevived == revivedAt
                            && Util.canDoWithTime(victim.lastTimeRevived, 1500)
                            && SkillService.gI().canAttackPlayer(caster, victim);
                }
                return entity.mob != null && entity.mob.zone == zone && !entity.mob.isDie();
            }
        }
    }

    private static final class HellzoneCast {

        private final Player caster;
        private final Skill skill;
        private final Zone zone;
        private final int x;
        private final int y;

        private HellzoneCast(Player caster, Skill skill, Zone zone, int x, int y) {
            this.caster = caster;
            this.skill = skill;
            this.zone = zone;
            this.x = x;
            this.y = y;
        }
    }

    private static final class CustomTarget {

        private final Player player;
        private final Mob mob;
        private final int distance;

        private CustomTarget(Player player, int distance) {
            this.player = player;
            this.mob = null;
            this.distance = distance;
        }

        private CustomTarget(Mob mob, int distance) {
            this.player = null;
            this.mob = mob;
            this.distance = distance;
        }

        private int x() {
            return player != null ? player.location.x : mob.location.x;
        }

        private int y() {
            return player != null ? player.location.y : mob.location.y;
        }
    }

    private List<CustomTarget> collectTargets(Player caster, Zone zone, int x, int y,
            int radius, boolean includeCasterExcluded) {
        List<CustomTarget> targets = new ArrayList<>();
        if (zone == null || caster == null) {
            return targets;
        }
        List<Player> players = caster.isBoss ? zone.getNotBosses() : zone.getHumanoids();
        for (Player player : new ArrayList<>(players)) {
            if (player == null || (includeCasterExcluded && player == caster) || player == caster
                    || player.zone != zone || player.isDie()
                    || !SkillService.gI().canAttackPlayer(caster, player)) {
                continue;
            }
            int distance = Util.getDistance(x, y, player.location.x, player.location.y);
            if (distance <= radius) {
                targets.add(new CustomTarget(player, distance));
            }
        }
        if (!caster.isBoss) {
            for (Mob mob : new ArrayList<>(zone.mobs)) {
                if (mob == null || mob.isDie() || mob.zone != zone) {
                    continue;
                }
                int distance = Util.getDistance(x, y, mob.location.x, mob.location.y);
                if (distance <= radius) {
                    targets.add(new CustomTarget(mob, distance));
                }
            }
        }
        return targets;
    }
}
