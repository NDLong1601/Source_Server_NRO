package nro.models.services;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import nro.models.data.LocalManager;
import nro.models.mob.Mob;
import nro.models.player.Player;
import nro.models.player.PlayerConfig;
import nro.models.services_func.EffectMapService;
import nro.models.utils.Logger;
import nro.models.utils.Util;

/** Runtime behavior shared by equipment options 153 and 161-169. */
public final class EquipmentOptionService {

    private static final int BURN_DURATION_MS = 30_000;
    private static final int BURN_TICK_MS = 5_000;
    private static final int FIRE_COOLDOWN_MS = 60_000;
    private static final int POISON_COOLDOWN_MS = 60_000;
    private static final int ABSORB_COOLDOWN_MS = 120_000;
    private static final int ABSORB_REQUIRED_STACKS = 5;
    private static final int ABSORB_RANGE = 500;
    private static final ZoneId SERVER_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static EquipmentOptionService instance;
    private volatile boolean dailyClaimTableReady;
    private final Map<Long, LocalDate> dailyClaimCache = new ConcurrentHashMap<>();

    public static EquipmentOptionService gI() {
        if (instance == null) {
            instance = new EquipmentOptionService();
        }
        return instance;
    }

    private EquipmentOptionService() {
    }

    /** Processes options 165 and 169 after an attack has really dealt damage. */
    public void onSuccessfulAttack(Player attacker, Player playerTarget, Mob mobTarget, boolean melee) {
        if (attacker == null || attacker.nPoint == null || attacker.effectSkin == null
                || (playerTarget == null && mobTarget == null)) {
            return;
        }
        if ((playerTarget != null && playerTarget.isDie()) || (mobTarget != null && mobTarget.isDie())) {
            return;
        }
        long now = System.currentTimeMillis();
        if (melee && attacker.nPoint.hasFireStrike
                && Util.canDoWithTime(attacker.effectSkin.lastTimeFireStrike, FIRE_COOLDOWN_MS)
                && Util.isTrue(50, 100)) {
            attacker.effectSkin.lastTimeFireStrike = now;
            if (playerTarget != null) {
                startPlayerBurn(attacker, playerTarget);
            } else {
                startMobBurn(attacker, mobTarget);
            }
        }
        if (attacker.nPoint.hasPoisonStrike
                && Util.canDoWithTime(attacker.effectSkin.lastTimePoisonStrike, POISON_COOLDOWN_MS)
                && Util.isTrue(5, 100)) {
            attacker.effectSkin.lastTimePoisonStrike = now;
            applyPoison(attacker, playerTarget, mobTarget);
        }
    }

    private void startPlayerBurn(Player source, Player target) {
        if (target == null || target.effectSkill == null || target.isDie()) {
            return;
        }
        target.effectSkill.isBurning = true;
        target.effectSkill.burnSource = source;
        target.effectSkill.lastTimeBurning = System.currentTimeMillis();
        target.effectSkill.lastTimeBurnTick = target.effectSkill.lastTimeBurning;
        target.effectSkill.timeBurning = BURN_DURATION_MS;
        Service.gI().chat(target, "Bạn đang bị thiêu đốt trong 30 giây");
    }

    private void startMobBurn(Player source, Mob target) {
        if (target == null || target.effectSkill == null || target.isDie()) {
            return;
        }
        target.effectSkill.isBurning = true;
        target.effectSkill.burnSource = source;
        target.effectSkill.lastTimeBurning = System.currentTimeMillis();
        target.effectSkill.lastTimeBurnTick = target.effectSkill.lastTimeBurning;
        target.effectSkill.timeBurning = BURN_DURATION_MS;
        target.sendFire(true);
    }

    public void updatePlayerBurn(Player target) {
        if (target == null || target.effectSkill == null || !target.effectSkill.isBurning) {
            return;
        }
        if (target.isDie() || target.effectSkill.burnSource == null
                || target.effectSkill.burnSource.beforeDispose) {
            clearPlayerBurn(target);
            return;
        }
        if (Util.canDoWithTime(target.effectSkill.lastTimeBurnTick, BURN_TICK_MS)) {
            target.effectSkill.lastTimeBurnTick = System.currentTimeMillis();
            long damage = Math.max(1L, (long) target.nPoint.hpMax * 2L / 100L);
            Player source = target.effectSkill.burnSource;
            applyOptionDamage(source, () -> target.injured(source, damage, true, false));
            Service.gI().reload_HP_NV(target);
        }
        if (target.isDie() || Util.canDoWithTime(target.effectSkill.lastTimeBurning,
                target.effectSkill.timeBurning)) {
            clearPlayerBurn(target);
        }
    }

    public void updateMobBurn(Mob target) {
        if (target == null || target.effectSkill == null || !target.effectSkill.isBurning) {
            return;
        }
        if (target.isDie() || target.effectSkill.burnSource == null
                || target.effectSkill.burnSource.beforeDispose) {
            clearMobBurn(target);
            return;
        }
        if (Util.canDoWithTime(target.effectSkill.lastTimeBurnTick, BURN_TICK_MS)) {
            target.effectSkill.lastTimeBurnTick = System.currentTimeMillis();
            long damage = Math.max(1L, (long) target.point.getHpFull() * 2L / 100L);
            Player source = target.effectSkill.burnSource;
            applyOptionDamage(source, () -> target.injured(source, damage, true));
        }
        if (target.isDie() || Util.canDoWithTime(target.effectSkill.lastTimeBurning,
                target.effectSkill.timeBurning)) {
            clearMobBurn(target);
        }
    }

    private void clearPlayerBurn(Player target) {
        target.effectSkill.isBurning = false;
        target.effectSkill.burnSource = null;
    }

    private void clearMobBurn(Mob target) {
        target.effectSkill.isBurning = false;
        target.effectSkill.burnSource = null;
        target.sendFire(false);
    }

    private void applyPoison(Player source, Player playerTarget, Mob mobTarget) {
        int percent = Util.nextInt(15, 20);
        if (playerTarget != null && playerTarget.nPoint != null && !playerTarget.isDie()) {
            long damage = Math.max(1L, (long) playerTarget.nPoint.hpMax * percent / 100L);
            Service.gI().chat(playerTarget, "Bạn đã trúng độc và mất " + percent + "% HP");
            applyOptionDamage(source, () -> playerTarget.injured(source, damage, true, false));
            Service.gI().reload_HP_NV(playerTarget);
        } else if (mobTarget != null && !mobTarget.isDie()) {
            long damage = Math.max(1L, (long) mobTarget.point.getHpFull() * percent / 100L);
            applyOptionDamage(source, () -> mobTarget.injured(source, damage, true));
        }
    }

    /** Option 168 absorbs one hit and releases the stored total on the fifth stack. */
    public boolean tryAbsorbDamage(Player owner, long damage) {
        if (owner == null || owner.nPoint == null || owner.effectSkin == null
                || !owner.nPoint.hasAbsorbBurst || damage <= 0
                || !Util.canDoWithTime(owner.effectSkin.lastTimeAbsorbBurst, ABSORB_COOLDOWN_MS)
                || !Util.isTrue(50, 100)) {
            return false;
        }
        owner.effectSkin.absorbedDamage = saturatingAdd(owner.effectSkin.absorbedDamage, damage);
        owner.effectSkin.absorbStackCount++;
        Service.gI().chat(owner, "Đã hấp thụ đòn đánh ("
                + Math.min(ABSORB_REQUIRED_STACKS, owner.effectSkin.absorbStackCount) + "/5)");
        if (owner.effectSkin.absorbStackCount >= ABSORB_REQUIRED_STACKS) {
            releaseAbsorbedDamage(owner);
        }
        return true;
    }

    private void releaseAbsorbedDamage(Player owner) {
        if (owner.zone == null) {
            return;
        }
        List<Mob> mobs = new ArrayList<>();
        for (Mob mob : owner.zone.mobs) {
            if (mob != null && !mob.isDie() && Util.getDistance(owner, mob) <= ABSORB_RANGE) {
                mobs.add(mob);
            }
        }
        List<Player> players = new ArrayList<>();
        for (Player target : owner.zone.getHumanoids()) {
            if (target != null && !target.equals(owner) && !target.isDie()
                    && Util.getDistance(owner, target) <= ABSORB_RANGE
                    && SkillService.gI().canAttackPlayer2(owner, target)) {
                players.add(target);
            }
        }
        int targetCount = mobs.size() + players.size();
        if (targetCount == 0) {
            return;
        }
        long share = Math.max(1L, owner.effectSkin.absorbedDamage / targetCount);
        owner.effectSkin.absorbStackCount = 0;
        owner.effectSkin.absorbedDamage = 0L;
        owner.effectSkin.lastTimeAbsorbBurst = System.currentTimeMillis();
        EffectMapService.gI().sendEffectMapToAllInMap(owner, 37, 3, 1,
                owner.location.x, owner.location.y, -1);
        for (Mob mob : mobs) {
            applyOptionDamage(owner, () -> mob.injured(owner, share, true));
        }
        for (Player target : players) {
            applyOptionDamage(owner, () -> target.injured(owner, share, true, false));
            Service.gI().reload_HP_NV(target);
        }
    }

    /** Option 153 uses the self-destruct context: max HP as piercing area damage. */
    public void explodeOnDeath(Player owner) {
        if (owner == null || owner.zone == null || owner.nPoint == null) {
            return;
        }
        long damage = Math.max(1L, owner.nPoint.hpMax);
        EffectMapService.gI().sendEffectMapToAllInMap(owner, 37, 3, 1,
                owner.location.x, owner.location.y, -1);
        for (Mob mob : new ArrayList<>(owner.zone.mobs)) {
            if (mob != null && !mob.isDie() && Util.getDistance(owner, mob) <= ABSORB_RANGE) {
                applyOptionDamage(owner, () -> mob.injured(owner, damage, true));
            }
        }
        for (Player target : new ArrayList<>(owner.zone.getHumanoids())) {
            if (target != null && !target.equals(owner) && !target.isDie()
                    && Util.getDistance(owner, target) <= ABSORB_RANGE
                    && SkillService.gI().canAttackPlayer2(owner, target)) {
                applyOptionDamage(owner, () -> target.injured(owner, damage, true, false));
                Service.gI().reload_HP_NV(target);
            }
        }
    }

    /** Option 161 grants gems only on the first eligible mob kill of a server day. */
    public synchronized void grantDailyMobGem(Player player) {
        if (player == null || !player.isPl() || player.isBot || player.isPet || player.isBoss
                || player.nPoint == null || player.nPoint.dailyGemFromMob <= 0
                || player.inventory.gem >= PlayerConfig.getMaxGem()) {
            return;
        }
        try {
            ensureDailyClaimTable();
            LocalDate today = LocalDate.now(SERVER_ZONE);
            if (today.equals(dailyClaimCache.get(player.id))) {
                return;
            }
            int changed;
            try (Connection connection = LocalManager.getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO equipment_option_daily_claim (player_id, claim_date) VALUES (?, ?) "
                            + "ON DUPLICATE KEY UPDATE claim_date = IF(claim_date < VALUES(claim_date), "
                            + "VALUES(claim_date), claim_date)")) {
                statement.setLong(1, player.id);
                statement.setDate(2, Date.valueOf(today));
                changed = statement.executeUpdate();
            }
            dailyClaimCache.put(player.id, today);
            if (changed <= 0) {
                return;
            }
            int amount = Math.min(player.nPoint.dailyGemFromMob,
                    PlayerConfig.getMaxGem() - player.inventory.gem);
            if (amount <= 0) {
                return;
            }
            player.inventory.gem += amount;
            Service.gI().sendMoney(player);
            Service.gI().sendThongBao(player, "Option 161: nhận " + amount
                    + " ngọc từ lần hạ quái đầu tiên hôm nay.");
        } catch (Exception e) {
            Logger.logException(EquipmentOptionService.class, e);
        }
    }

    private void ensureDailyClaimTable() throws Exception {
        if (dailyClaimTableReady) {
            return;
        }
        try (Connection connection = LocalManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "CREATE TABLE IF NOT EXISTS equipment_option_daily_claim ("
                        + "player_id BIGINT NOT NULL, claim_date DATE NOT NULL, "
                        + "PRIMARY KEY (player_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4")) {
            statement.executeUpdate();
            dailyClaimTableReady = true;
        }
    }

    private void applyOptionDamage(Player source, Runnable damageAction) {
        if (damageAction == null) {
            return;
        }
        boolean marked = source != null && source.effectSkin != null;
        if (marked) {
            source.effectSkin.applyingEquipmentOptionDamage = true;
        }
        try {
            damageAction.run();
        } finally {
            if (marked) {
                source.effectSkin.applyingEquipmentOptionDamage = false;
            }
        }
    }

    private long saturatingAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }
}
