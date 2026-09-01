package nro.models.activity;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashSet;
import java.util.Set;
import nro.models.player.Player;
import nro.models.services.Service;

/** Core reset, cap and dedupe engine. Gameplay hooks are deliberately Phase 2. */
public final class ActivityService {

    private static final ActivityService INSTANCE = new ActivityService();
    private static final long PERIOD_CHECK_INTERVAL_MS = 30_000L;

    private ActivityService() {
    }

    public static ActivityService gI() {
        return INSTANCE;
    }

    public void loadState(Player player, String raw, int legacyDailyPoints) {
        if (player == null) {
            return;
        }
        player.activityState = ActivityRepository.read(raw, legacyDailyPoints);
        ensureCurrentPeriod(player, false, true);
    }

    public void prepareForSave(Player player) {
        ensureCurrentPeriod(player, false, true);
    }

    public int getDailyPoints(Player player) {
        ensureCurrentPeriod(player, false, false);
        if (player == null || player.activityState == null) {
            return 0;
        }
        synchronized (player.activityState) {
            return Math.max(0, Math.min(Integer.MAX_VALUE, player.activityState.dailyPoints));
        }
    }

    public void ensureCurrentPeriod(Player player) {
        ensureCurrentPeriod(player, true, false);
    }

    public void ensureCurrentPeriod(Player player, boolean notifyClient, boolean force) {
        if (player == null) {
            return;
        }
        ActivityState state = player.activityState;
        if (state == null) {
            state = new ActivityState();
            player.activityState = state;
        }
        long now = System.currentTimeMillis();
        if (!force && now - state.lastPeriodCheckAt < PERIOD_CHECK_INTERVAL_MS) {
            return;
        }
        ActivityConfig config = ActivityConfigService.gI().getCurrent();
        boolean changed;
        synchronized (state) {
            if (!force && now - state.lastPeriodCheckAt < PERIOD_CHECK_INTERVAL_MS) {
                return;
            }
            state.lastPeriodCheckAt = now;
            changed = ensureCurrentPeriod(state, config, Instant.ofEpochMilli(now));
        }
        if (changed && notifyClient && player.getSession() != null) {
            Service.gI().sendNangDong(player);
        }
    }

    public boolean ensureCurrentPeriod(ActivityState state, ActivityConfig config, Instant now) {
        state.normalize();
        ActivityPeriod currentPeriod = currentPeriod(config, now);
        boolean changed = false;
        boolean weekChanged = !state.weekKey.isBlank() && !state.weekKey.equals(currentPeriod.weekKey);

        if (state.weekKey.isBlank() || weekChanged) {
            state.resetWeekly(currentPeriod.weekKey);
            changed = true;
        }
        if (state.dayKey.isBlank()) {
            state.dayKey = currentPeriod.dayKey;
            changed = true;
        } else if (!state.dayKey.equals(currentPeriod.dayKey)) {
            if (!weekChanged && state.dailyPoints >= config.getQualifiedDailyPoints()
                    && state.qualifiedDayKeys.add(state.dayKey)) {
                state.qualifiedDays = state.qualifiedDayKeys.size();
            }
            state.resetDaily(currentPeriod.dayKey);
            changed = true;
        }
        if (state.dailyPoints > config.getDailyMax()) {
            state.dailyPoints = config.getDailyMax();
            changed = true;
        }
        state.qualifiedDays = Math.max(state.qualifiedDays, state.qualifiedDayKeys.size());
        return changed;
    }

    public ActivityResult award(Player player, ActivityType type) {
        return award(player, type, type == null ? 0 : type.getDefaultPoints(), null);
    }

    public ActivityResult awardUnique(Player player, ActivityType type, String dedupeKey) {
        return award(player, type, type == null ? 0 : type.getDefaultPoints(), dedupeKey);
    }

    /**
     * PvP has two independent idempotency contracts: one outcome per match
     * and at most one counted win against the same opponent per activity day.
     */
    public ActivityResult awardPvpWin(Player player, long opponentId, long matchId) {
        if (player == null || opponentId < 0 || matchId <= 0) {
            return ActivityResult.of(ActivityResult.Reason.INVALID_REQUEST, 0, 0);
        }
        if (!player.isPl() || player.isBot) {
            return ActivityResult.of(ActivityResult.Reason.NOT_ELIGIBLE, 0, 0);
        }
        ensureCurrentPeriod(player, false, true);
        ActivityConfig config = ActivityConfigService.gI().getCurrent();
        ActivityState state = player.activityState;
        ActivityResult result;
        synchronized (state) {
            String opponentKey = "PVP_OPPONENT";
            Set<String> opponents = state.dailyUnique.computeIfAbsent(opponentKey, ignored -> new LinkedHashSet<>());
            String opponentDedupe = "opponent:" + opponentId;
            if (opponents.contains(opponentDedupe)) {
                result = ActivityResult.of(ActivityResult.Reason.DUPLICATE_EVENT, 0, state.dailyPoints);
            } else if (opponents.size() >= ActivityState.MAX_UNIQUE_KEYS_PER_SOURCE) {
                result = ActivityResult.of(ActivityResult.Reason.SOURCE_CAP_REACHED, 0, state.dailyPoints);
            } else {
                result = awardToState(state, config, ActivityType.PVP_WIN,
                        ActivityType.PVP_WIN.getDefaultPoints(), "pvp-match:" + matchId);
                if (result.isAwarded()) {
                    opponents.add(opponentDedupe);
                }
            }
        }
        ActivityMetricsService.gI().record(config, state, ActivityType.PVP_WIN,
                ActivityType.PVP_WIN.getDefaultPoints(), result);
        if (result.isAwarded()) {
            Service.gI().sendNangDong(player);
        }
        return result;
    }

    public ActivityResult award(Player player, ActivityType type, int requestedPoints, String dedupeKey) {
        if (player == null || type == null || requestedPoints <= 0) {
            return ActivityResult.of(ActivityResult.Reason.INVALID_REQUEST, 0, 0);
        }
        if (!player.isPl() || player.isBot) {
            return ActivityResult.of(ActivityResult.Reason.NOT_ELIGIBLE, 0, 0);
        }
        ensureCurrentPeriod(player, false, true);
        ActivityConfig config = ActivityConfigService.gI().getCurrent();
        ActivityState state = player.activityState;
        ActivityResult result;
        synchronized (state) {
            result = awardToState(state, config, type, requestedPoints, dedupeKey);
        }
        ActivityMetricsService.gI().record(config, state, type, requestedPoints, result);
        if (result.isAwarded()) {
            Service.gI().sendNangDong(player);
        }
        return result;
    }

    /**
     * Pure state transition used by the player entry point and the regression
     * test. The caller must have synchronized the state and applied resets.
     */
    ActivityResult awardToState(ActivityState state, ActivityConfig config, ActivityType type,
            int requestedPoints, String dedupeKey) {
        if (state == null || config == null || type == null || requestedPoints <= 0) {
            return ActivityResult.of(ActivityResult.Reason.INVALID_REQUEST, 0, 0);
        }
        state.normalize();
        if (!config.isEnabled()) {
            return ActivityResult.of(ActivityResult.Reason.FEATURE_DISABLED, 0, state.dailyPoints);
        }
        ActivitySourceRule rule = config.getSourceRule(type);
        if (!rule.isEnabled()) {
            return ActivityResult.of(ActivityResult.Reason.SOURCE_DISABLED, 0, state.dailyPoints);
        }
        if (type.requiresDedupeKey() && (dedupeKey == null || dedupeKey.isBlank())) {
            return ActivityResult.of(ActivityResult.Reason.INVALID_DEDUPE_KEY, 0, state.dailyPoints);
        }
        if (dedupeKey != null && !dedupeKey.isBlank()) {
            if (dedupeKey.length() > 128) {
                return ActivityResult.of(ActivityResult.Reason.INVALID_DEDUPE_KEY, 0, state.dailyPoints);
            }
            Set<String> keys = state.dailyUnique.computeIfAbsent(type.name(), ignored -> new LinkedHashSet<>());
            if (keys.contains(dedupeKey)) {
                return ActivityResult.of(ActivityResult.Reason.DUPLICATE_EVENT, 0, state.dailyPoints);
            }
        }

        int sourceRemaining = Math.max(0, rule.getDailyCap() - state.counter(type.name()));
        int groupRemaining = Integer.MAX_VALUE;
        String groupCounter = null;
        if (rule.getGroupKey() != null && !rule.getGroupKey().isBlank()) {
            groupCounter = "GROUP:" + rule.getGroupKey();
            groupRemaining = Math.max(0, rule.getGroupDailyCap() - state.counter(groupCounter));
        }
        int dailyRemaining = Math.max(0, config.getDailyMax() - state.dailyPoints);
        int awarded = Math.min(rule.getPoints(), Math.min(sourceRemaining, Math.min(groupRemaining, dailyRemaining)));
        if (awarded <= 0) {
            ActivityResult.Reason reason = dailyRemaining <= 0
                    ? ActivityResult.Reason.DAILY_CAP_REACHED : ActivityResult.Reason.SOURCE_CAP_REACHED;
            return ActivityResult.of(reason, 0, state.dailyPoints);
        }

        if (dedupeKey != null && !dedupeKey.isBlank()) {
            Set<String> keys = state.dailyUnique.computeIfAbsent(type.name(), ignored -> new LinkedHashSet<>());
            if (keys.size() >= ActivityState.MAX_UNIQUE_KEYS_PER_SOURCE) {
                return ActivityResult.of(ActivityResult.Reason.SOURCE_CAP_REACHED, 0, state.dailyPoints);
            }
            keys.add(dedupeKey);
        }
        state.increaseCounter(type.name(), awarded);
        if (groupCounter != null) {
            state.increaseCounter(groupCounter, awarded);
        }
        state.dailyPoints += awarded;
        state.weeklyPoints += awarded;
        state.lastAwardAt = System.currentTimeMillis();
        int diversityBonus = 0;
        if (rule.getCategory() != ActivityCategory.BONUS) {
            state.dailyCategories.add(rule.getCategory().name());
            diversityBonus = applyDiversityBonus(state, config);
            awarded += diversityBonus;
        }
        return ActivityResult.of(config.isShadowMode()
                ? ActivityResult.Reason.SHADOW_AWARDED : ActivityResult.Reason.AWARDED,
                awarded, state.dailyPoints, diversityBonus);
    }

    private int applyDiversityBonus(ActivityState state, ActivityConfig config) {
        ActivitySourceRule rule = config.getSourceRule(ActivityType.DIVERSITY_BONUS);
        if (state.dailyCategories.size() < config.getDiversityCategoryCount()
                || state.counter(ActivityType.DIVERSITY_BONUS.name()) > 0
                || config.getDiversityBonus() <= 0 || !rule.isEnabled()) {
            return 0;
        }
        int bonus = Math.min(config.getDiversityBonus(), Math.min(rule.getPoints(),
                Math.min(rule.getDailyCap() - state.counter(ActivityType.DIVERSITY_BONUS.name()),
                        config.getDailyMax() - state.dailyPoints)));
        if (bonus <= 0) {
            return 0;
        }
        state.increaseCounter(ActivityType.DIVERSITY_BONUS.name(), bonus);
        state.dailyPoints += bonus;
        state.weeklyPoints += bonus;
        return bonus;
    }

    private ActivityPeriod currentPeriod(ActivityConfig config, Instant instant) {
        ZonedDateTime local = instant.atZone(config.getZoneId());
        LocalDate day = local.toLocalDate();
        if (local.getHour() < config.getDailyResetHour()) {
            day = day.minusDays(1);
        }
        DayOfWeek resetDay = config.getWeeklyResetDay();
        LocalDate weekStart = day.with(TemporalAdjusters.previousOrSame(resetDay));
        return new ActivityPeriod(day.toString(), weekStart.toString());
    }

    private static final class ActivityPeriod {
        private final String dayKey;
        private final String weekKey;

        private ActivityPeriod(String dayKey, String weekKey) {
            this.dayKey = dayKey;
            this.weekKey = weekKey;
        }
    }
}
