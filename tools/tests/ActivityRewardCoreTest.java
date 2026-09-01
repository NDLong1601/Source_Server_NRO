package nro.models.activity;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.List;

/** Pure regression coverage for tier identity, eligibility and claim masks. */
public final class ActivityRewardCoreTest {

    private ActivityRewardCoreTest() {
    }

    public static void main(String[] args) {
        ActivityTier daily20 = new ActivityTier("DAILY_20", 3, true, 20, 0,
                List.of(new ActivityReward(ActivityReward.Kind.GOLD, 0, 10_000, 10_000,
                        3, false, false, false, List.of(), 0, 0)));
        ActivityTier weekly500 = new ActivityTier("WEEKLY_500", 7, true, 500, 5,
                List.of(new ActivityReward(ActivityReward.Kind.ITEM, 457, 1, 1,
                        3, true, false, true, List.of(new ActivityRewardOption(30, 0, 0)), 7, 7)));
        ActivityConfig config = new ActivityConfig(true, false, true, false, true,
                ZoneId.of("Asia/Ho_Chi_Minh"), 0, DayOfWeek.MONDAY,
                100, 80, 4, 10, 5_000L, 3L, List.of(daily20), List.of(weekly500));
        ActivityState state = new ActivityState();
        state.dailyPoints = 20;
        state.weeklyPoints = 500;
        state.qualifiedDays = 5;

        ActivityRewardService.TierStatus daily = ActivityRewardService.evaluate(state,
                ActivityPeriodType.DAILY, config.getDailyTiers().get(0));
        require(daily.isEligible() && !daily.isClaimed(), "daily threshold must become eligible exactly at the threshold");
        state.dailyClaimMask |= 1L << daily20.getClaimBit();
        require(ActivityRewardService.evaluate(state, ActivityPeriodType.DAILY, daily20).isClaimed(),
                "a fixed claim bit must block repeat claims regardless of tier order");

        ActivityRewardService.TierStatus weekly = ActivityRewardService.evaluate(state,
                ActivityPeriodType.WEEKLY, weekly500);
        require(weekly.isEligible(), "weekly tier must require both weekly points and qualified days");
        state.qualifiedDays = 4;
        require(!ActivityRewardService.evaluate(state, ActivityPeriodType.WEEKLY, weekly500).isEligible(),
                "weekly tier must reject a player below its qualified-day requirement");
        require(ActivityRewardService.describeRewards(weekly500).contains("vật phẩm #457 x1"),
                "item reward description must remain independent from client protocol");
        require(new ActivityReward(ActivityReward.Kind.ITEM, 1, 1, 1, 3,
                false, false, false, List.of(), 0, 0).requiresBagSlot(),
                "item rewards must reserve bag capacity");
        require(!new ActivityReward(ActivityReward.Kind.GOLD, 0, 1, 1, 3,
                false, false, false, List.of(), 0, 0).requiresBagSlot(),
                "currency rewards must not consume a bag slot");

        System.out.println("ACTIVITY_REWARD_CORE_TEST_OK");
    }

    private static void require(boolean expression, String message) {
        if (!expression) {
            throw new AssertionError(message);
        }
    }
}
