package nro.models.npc_list;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import nro.models.activity.ActivityClaimResult;
import nro.models.activity.ActivityConfig;
import nro.models.activity.ActivityConfigService;
import nro.models.activity.ActivityPeriodType;
import nro.models.activity.ActivityRewardService;
import nro.models.activity.ActivityRewardService.TierStatus;
import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import nro.models.task.TaskConfig;
import nro.models.services.AchievementService;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.PlayerService;
import nro.models.services.Service;
import nro.models.services.TaskService;
import nro.models.services_func.Input;

/**
 *
 * @author By Mr Blue
 *
 */
public class BoMong extends Npc {

    public BoMong(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    @Override
    public void openBaseMenu(Player player) {
        if (!TaskService.gI().checkDoneTaskTalkNpc(player, this)) {
            if (canOpenNpc(player)) {
                if (this.mapId == 47 || this.mapId == 84) {
                    this.createOtherMenu(player, ConstNpc.BASE_MENU,
                            "Ngươi muốn có thêm ngọc thì chịu khó làm vài nhiệm vụ sẽ được ngọc thưởng",
                            "Nhiệm vụ\nhàng ngày", "Nhiệm vụ\nthành tích", "Nạp Ngọc", "Điểm danh",
                            "Năng động\nNhận quà", "Từ chối");
                }
            }
        }
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (canOpenNpc(player)) {
            if (this.mapId == 47 || this.mapId == 84) {
                if (player.idMark.isBaseMenu()) {
                    switch (select) {
                        case 0 -> {
                            if (player.playerTask.sideTask.template != null) {
                                String npcSay = "Nhiệm vụ hiện tại: " + player.playerTask.sideTask.getName() + " ("
                                        + player.playerTask.sideTask.getLevel() + ")"
                                        + "\nHiện tại đã hoàn thành: " + player.playerTask.sideTask.count + "/"
                                        + player.playerTask.sideTask.maxCount + " ("
                                        + player.playerTask.sideTask.getPercentProcess() + "%)\nSố nhiệm vụ còn lại trong ngày: "
                                        + player.playerTask.sideTask.leftTask + "/" + TaskConfig.getMaxSideTask();
                                this.createOtherMenu(player, ConstNpc.MENU_OPTION_PAY_SIDE_TASK,
                                        npcSay, "Trả nhiệm\nvụ", "Hủy nhiệm\nvụ");
                            } else {
                                this.createOtherMenu(player, ConstNpc.MENU_OPTION_LEVEL_SIDE_TASK,
                                        "Tôi có vài nhiệm vụ theo cấp bậc, "
                                        + "sức cậu có thể làm được cái nào?",
                                        "Dễ", "Bình thường", "Khó", "Từ chối");
                            }
                        }
                        case 1 -> {
                            AchievementService.gI().openAchievementUI(player);
                        }
                        case 2 -> {
                            Input.gI().createFormTradeGem(player);
                        }
                        case 3 -> {
                            if (player.lastCheckIn != null) {
                                LocalDate last = player.lastCheckIn.toLocalDate();
                                LocalDate today = LocalDate.now();
                                if (last.isEqual(today)) {
                                    Service.gI().sendThongBao(player, "Bạn đã điểm danh hôm nay rồi!");
                                    return;
                                }
                            }
                            player.lastCheckIn = LocalDateTime.now();
                            player.inventory.gem += 10000;
                            Item item457 = ItemService.gI().createNewItem((short) 457);
                            item457.quantity = 100;
                            item457.itemOptions.add(new Item.ItemOption(30, 0));
                            InventoryService.gI().addItemBag(player, item457);
                            PlayerService.gI().sendInfoHpMpMoney(player);
                            InventoryService.gI().sendItemBags(player);

                            Service.gI().sendThongBao(player, "Điểm danh thành công! Bạn nhận được 10.000 ngọc và 100 thỏi vàng.");
                        }
                        case 4 -> openActivityOverview(player);

                    }
                } else if (player.idMark.getIndexMenu() == ConstNpc.MENU_OPTION_LEVEL_SIDE_TASK) {
                    switch (select) {
                        case 0, 1, 2 ->
                            TaskService.gI().changeSideTask(player, (byte) select);
                    }
                } else if (player.idMark.getIndexMenu() == ConstNpc.MENU_OPTION_PAY_SIDE_TASK) {
                    switch (select) {
                        case 0 ->
                            TaskService.gI().paySideTask(player);
                        case 1 ->
                            TaskService.gI().removeSideTask(player);
                    }
                } else if (player.idMark.getIndexMenu() == ConstNpc.MENU_ACTIVITY_OVERVIEW) {
                    if (select == 0) {
                        openActivityTiers(player, ActivityPeriodType.DAILY);
                    } else if (select == 1) {
                        openActivityTiers(player, ActivityPeriodType.WEEKLY);
                    }
                } else if (player.idMark.getIndexMenu() == ConstNpc.MENU_ACTIVITY_DAILY_TIER) {
                    claimActivityTier(player, ActivityPeriodType.DAILY, select);
                } else if (player.idMark.getIndexMenu() == ConstNpc.MENU_ACTIVITY_WEEKLY_TIER) {
                    claimActivityTier(player, ActivityPeriodType.WEEKLY, select);
                }
            }
        }
    }

    private void openActivityOverview(Player player) {
        ActivityConfig config = ActivityConfigService.gI().getCurrent();
        List<TierStatus> daily = ActivityRewardService.gI().getTierStatuses(player, ActivityPeriodType.DAILY);
        List<TierStatus> weekly = ActivityRewardService.gI().getTierStatuses(player, ActivityPeriodType.WEEKLY);
        int dailyPoints = daily.isEmpty() ? 0 : daily.get(0).getCurrentPoints();
        int weeklyPoints = weekly.isEmpty() ? 0 : weekly.get(0).getCurrentPoints();
        int qualifiedDays = weekly.isEmpty() ? 0 : weekly.get(0).getQualifiedDays();
        String status = (!config.isEnabled()) ? "\nNăng động đang tạm khóa."
                : (!config.isRewardsEnabled() || config.isShadowMode())
                        ? "\nQuà đang được khóa trong giai đoạn theo dõi."
                        : "\nBạn có thể nhận các mốc đủ điều kiện.";
        this.createOtherMenu(player, ConstNpc.MENU_ACTIVITY_OVERVIEW,
                "Năng động hôm nay: " + dailyPoints + "/" + config.getDailyMax()
                + "\nNăng động tuần: " + weeklyPoints
                + "\nNgày đạt chuẩn: " + qualifiedDays + "/7" + status,
                "Mốc ngày", "Mốc tuần", "Đóng");
    }

    private void openActivityTiers(Player player, ActivityPeriodType period) {
        List<TierStatus> statuses = ActivityRewardService.gI().getTierStatuses(player, period);
        List<String> menus = new ArrayList<>();
        StringBuilder text = new StringBuilder(period == ActivityPeriodType.DAILY
                ? "Chọn mốc ngày để xem trạng thái hoặc nhận quà:"
                : "Chọn mốc tuần để xem trạng thái hoặc nhận quà:");
        for (TierStatus status : statuses) {
            String prefix = status.isClaimed() ? "[Đã nhận] "
                    : status.isEligible() ? "[Nhận] " : "[Chưa đủ] ";
            menus.add(prefix + status.getTier().getThreshold() + " điểm");
            text.append("\n").append(status.getTier().getThreshold()).append(" điểm: ")
                    .append(ActivityRewardService.describeRewards(status.getTier()));
            if (period == ActivityPeriodType.WEEKLY && status.getTier().getMinQualifiedDays() > 0) {
                text.append(" (cần ").append(status.getTier().getMinQualifiedDays()).append(" ngày đạt chuẩn)");
            }
        }
        menus.add("Đóng");
        int menuId = period == ActivityPeriodType.DAILY
                ? ConstNpc.MENU_ACTIVITY_DAILY_TIER : ConstNpc.MENU_ACTIVITY_WEEKLY_TIER;
        this.createOtherMenu(player, menuId, text.toString(), menus.toArray(String[]::new));
    }

    private void claimActivityTier(Player player, ActivityPeriodType period, int select) {
        List<TierStatus> statuses = ActivityRewardService.gI().getTierStatuses(player, period);
        if (select < 0 || select >= statuses.size()) {
            return;
        }
        ActivityClaimResult result = period == ActivityPeriodType.DAILY
                ? ActivityRewardService.gI().claimDaily(player, statuses.get(select).getTier().getId())
                : ActivityRewardService.gI().claimWeekly(player, statuses.get(select).getTier().getId());
        Service.gI().sendThongBao(player, result.getMessage());
        openActivityTiers(player, period);
    }
}
