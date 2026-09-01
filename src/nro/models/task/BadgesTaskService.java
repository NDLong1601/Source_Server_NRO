package nro.models.task;

import nro.models.player.Player;
import nro.models.player_badges.BadgesData;
import nro.models.player_badges.BagesTemplate;
import nro.models.player_badges.BadgesService;
import nro.models.server.Manager;

public class BadgesTaskService {

    public static void createAndResetTask(Player player) {
        player.dataTaskBadges.clear();
        for (BadgesTaskTemplate BTT : Manager.TASKS_BADGES_TEMPLATE) {
            BadgesTask data = new BadgesTask();
            data.id = BTT.id;
            data.count = 0;
            data.countMax = BTT.count;
            data.idBadgesReward = BTT.idbadgesReward;
            player.dataTaskBadges.add(data);
        }
    }

    public static void updateDoneTask(Player player) {
        for (BadgesTask data : player.dataTaskBadges) {
            if (data.isDone()) {
                int rewardId = normalizeRewardId(data);
                if (BadgesService.hasBadge(player, rewardId)) {
                    continue;
                }
                if (TaskConfig.isCustomRewardEnabled("badges", data.id)
                        && !TaskRewardService.grant(player, "badges", data.id)) {
                    continue;
                }
                if (BadgesService.grantBadge(player, rewardId,
                        BadgesService.DEFAULT_BADGE_DURATION_DAYS)) {
                    data.count = 0;
                }
            }
        }
    }

    public static void updateCountBagesTask(Player player, int id, int amount) {
        if (player == null || player.dataTaskBadges == null || amount <= 0) {
            return;
        }
        for (BadgesTask data : player.dataTaskBadges) {
            if (data.id == id) {
                data.count += amount;
                if (data.count > data.countMax) {
                    data.count = data.countMax;
                }
                break;
            }
        }
    }

    public static void setCountBadgesTask(Player player, int id, int count) {
        if (player == null || player.dataTaskBadges == null) {
            return;
        }
        for (BadgesTask data : player.dataTaskBadges) {
            if (data.id == id) {
                data.count = Math.max(0, Math.min(count, data.countMax));
                return;
            }
        }
    }

    public static int sendPercenBadgesTask(Player player, int idBadgesReward) {
        for (BadgesTask data : player.dataTaskBadges) {
            if (normalizeRewardId(data) == idBadgesReward) {
                if (data.getPercentProcess() > 0) {
                    return data.getPercentProcess();
                } else {
                    return 0;
                }
            }
        }
        return 0;
    }

    private static int normalizeRewardId(BadgesTask data) {
        int normalizedId = BagesTemplate.normalizeEffectId(data.idBadgesReward);
        if (normalizedId != data.idBadgesReward) {
            data.idBadgesReward = normalizedId;
        }
        return normalizedId;
    }

    public static int sendDay(Player player, int id) {
        for (BadgesData data : player.dataBadges) {
            if (data.idBadGes == id) {
                long timeDifference = data.timeofUseBadges - System.currentTimeMillis();
                if (timeDifference <= 0) {
                    return 0;
                }
                return (int) Math.ceil(timeDifference / (24D * 60 * 60 * 1000));
            }
        }
        return 0;
    }

}
