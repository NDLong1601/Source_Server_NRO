package nro.models.activity;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

/**
 * Lightweight regression test for Phase 1; it needs only the built server jar.
 */
public final class ActivityCoreTest {

    private ActivityCoreTest() {
    }

    public static void main(String[] args) {
        ActivityConfig config = new ActivityConfig(true, false, false, false, true,
                ZoneId.of("Asia/Ho_Chi_Minh"), 0, DayOfWeek.MONDAY,
                100, 80, 4, 10, 5_000L, 1L);
        ActivityService service = ActivityService.gI();

        ActivityState dailyState = new ActivityState();
        dailyState.dayKey = "2026-08-31";
        dailyState.weekKey = "2026-08-31";
        dailyState.dailyPoints = 80;
        require(service.ensureCurrentPeriod(dailyState, config, Instant.parse("2026-09-01T00:01:00Z")),
                "daily reset must report a change");
        require(dailyState.dailyPoints == 0, "daily reset must clear points");
        require(dailyState.qualifiedDays == 1 && dailyState.qualifiedDayKeys.contains("2026-08-31"),
                "a qualified day must be recorded once before reset");

        ActivityState weeklyState = new ActivityState();
        weeklyState.dayKey = "2026-09-06";
        weeklyState.weekKey = "2026-08-31";
        weeklyState.dailyPoints = 80;
        weeklyState.weeklyPoints = 90;
        weeklyState.qualifiedDayKeys.add("2026-09-05");
        service.ensureCurrentPeriod(weeklyState, config, Instant.parse("2026-09-07T00:01:00Z"));
        require(weeklyState.dailyPoints == 0 && weeklyState.weeklyPoints == 0,
                "weekly reset must clear daily and weekly points");
        require(weeklyState.qualifiedDays == 0 && weeklyState.qualifiedDayKeys.isEmpty(),
                "weekly reset must clear qualified days");
        require("2026-09-07".equals(weeklyState.dayKey) && "2026-09-07".equals(weeklyState.weekKey),
                "weekly reset must use the current day and Monday key");

        ActivityState recovered = ActivityRepository.read("{invalid-json", 17);
        require(recovered.dailyPoints == 17, "invalid JSON must fall back to legacy mirror");

        ActivityState restored = ActivityRepository.read(ActivityRepository.write(weeklyState), 0);
        require(restored.dayKey.equals(weeklyState.dayKey) && restored.weekKey.equals(weeklyState.weekKey),
                "persisted state must round-trip");

        ActivityConfig playConfig = new ActivityConfig(true, false, false, false, true,
                ZoneId.of("Asia/Ho_Chi_Minh"), 0, DayOfWeek.MONDAY,
                100, 80, 4, 10, 5_000L, 1L);
        ActivityState uniqueState = new ActivityState();
        require(service.awardToState(uniqueState, playConfig, ActivityType.PVP_WIN, 5, "pvp:1").isAwarded(),
                "first unique PvP win must award points");
        require(service.awardToState(uniqueState, playConfig, ActivityType.PVP_WIN, 5, "pvp:1").reason
                == ActivityResult.Reason.DUPLICATE_EVENT, "same dedupe key must not award twice");
        require(service.awardToState(uniqueState, playConfig, ActivityType.PVP_WIN, 5, "pvp:2").isAwarded(),
                "second unique PvP win must award points");
        require(service.awardToState(uniqueState, playConfig, ActivityType.PVP_WIN, 5, "pvp:3").reason
                == ActivityResult.Reason.SOURCE_CAP_REACHED, "source cap must be enforced");

        ActivityConfig tightDailyConfig = new ActivityConfig(true, false, false, false, true,
                ZoneId.of("Asia/Ho_Chi_Minh"), 0, DayOfWeek.MONDAY,
                30, 20, 4, 10, 5_000L, 1L);
        ActivityState diversityState = new ActivityState();
        service.awardToState(diversityState, tightDailyConfig, ActivityType.DAILY_LOGIN, 5, "login:today");
        service.awardToState(diversityState, tightDailyConfig, ActivityType.FISH_CATCH, 2, null);
        service.awardToState(diversityState, tightDailyConfig, ActivityType.SIDE_TASK_EASY, 5, null);
        ActivityResult diversity = service.awardToState(diversityState, tightDailyConfig, ActivityType.PVP_WIN, 5, "pvp:diversity");
        require(diversity.awarded == 15 && diversityState.dailyPoints == 27,
                "fourth distinct category must apply the diversity bonus exactly once");
        require(service.awardToState(diversityState, tightDailyConfig, ActivityType.PEA_HARVEST, 5, null).awarded == 3,
                "award must be trimmed by the daily cap");
        require(service.awardToState(diversityState, tightDailyConfig, ActivityType.FISH_CATCH, 2, null).reason
                == ActivityResult.Reason.DAILY_CAP_REACHED, "daily cap must reject further awards");

        ActivityConfig disabledConfig = new ActivityConfig(false, false, false, false, true,
                ZoneId.of("Asia/Ho_Chi_Minh"), 0, DayOfWeek.MONDAY,
                100, 80, 4, 10, 5_000L, 1L);
        require(service.awardToState(new ActivityState(), disabledConfig, ActivityType.FISH_CATCH, 2, null).reason
                == ActivityResult.Reason.FEATURE_DISABLED, "disabled feature must fail closed");

        ActivityConfig shadowConfig = new ActivityConfig(true, true, false, false, true,
                ZoneId.of("Asia/Ho_Chi_Minh"), 0, DayOfWeek.MONDAY,
                100, 80, 4, 10, 5_000L, 2L);
        ActivityState shadowState = new ActivityState();
        ActivityResult shadowAward = service.awardToState(shadowState, shadowConfig,
                ActivityType.FISH_CATCH, 2, "fish:shadow-1");
        require(shadowAward.reason == ActivityResult.Reason.SHADOW_AWARDED
                && shadowAward.awarded == 2 && shadowState.dailyPoints == 2,
                "shadow mode must calculate and persist points without a reward engine");

        Map<ActivityType, ActivitySourceRule> sourceRules = ActivityConfig.defaultSourceRules();
        sourceRules.put(ActivityType.FISH_CATCH, new ActivitySourceRule(false, ActivityCategory.LIFE, 2, 10, null, 0));
        ActivityConfig disabledSourceConfig = new ActivityConfig(true, false, false, false, true,
                ZoneId.of("Asia/Ho_Chi_Minh"), 0, DayOfWeek.MONDAY,
                100, 80, 4, 10, 5_000L, 3L, ActivityConfig.defaultDailyTiers(),
                ActivityConfig.defaultWeeklyTiers(), sourceRules);
        require(service.awardToState(new ActivityState(), disabledSourceConfig, ActivityType.FISH_CATCH, 2, null).reason
                == ActivityResult.Reason.SOURCE_DISABLED, "disabled source must reject awards at runtime");

        sourceRules = ActivityConfig.defaultSourceRules();
        sourceRules.put(ActivityType.FISH_CATCH, new ActivitySourceRule(true, ActivityCategory.LIFE, 3, 3, null, 0));
        ActivityConfig overriddenSourceConfig = new ActivityConfig(true, false, false, false, true,
                ZoneId.of("Asia/Ho_Chi_Minh"), 0, DayOfWeek.MONDAY,
                100, 80, 4, 10, 5_000L, 3L, ActivityConfig.defaultDailyTiers(),
                ActivityConfig.defaultWeeklyTiers(), sourceRules);
        require(service.awardToState(new ActivityState(), overriddenSourceConfig, ActivityType.FISH_CATCH, 2, null).awarded == 3,
                "published source point override must win over hook defaults");

        System.out.println("ACTIVITY_CORE_TEST_OK");
    }

    private static void require(boolean expression, String message) {
        if (!expression) {
            throw new AssertionError(message);
        }
    }
}
