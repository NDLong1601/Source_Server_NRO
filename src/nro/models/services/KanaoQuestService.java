package nro.models.services;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import nro.models.map.Map;
import nro.models.map.service.MapService;
import nro.models.mob.Mob;
import nro.models.player.Player;
import nro.models.player_system.Template;
import nro.models.server.Manager;
import nro.models.task.TaskConfig;
import nro.models.task.TaskRewardService;
import nro.models.utils.Util;

/** Handles Kanao's repeatable Infinity Castle monster-hunting quest. */
public final class KanaoQuestService {

    private static final int[] DEFAULT_MAP_IDS = {187, 188, 189, 190};
    private static final int REWARD_CONFIG_ID = 0;
    private static KanaoQuestService instance;

    private KanaoQuestService() {
    }

    public static KanaoQuestService gI() {
        if (instance == null) {
            instance = new KanaoQuestService();
        }
        return instance;
    }

    public boolean hasActiveQuest(Player player) {
        return player != null
                && player.kanaoQuestMapId >= 0
                && player.kanaoQuestMobId >= 0
                && player.kanaoQuestRequiredCount > 0;
    }

    public boolean assignRandomQuest(Player player) {
        if (player == null || hasActiveQuest(player)) {
            return false;
        }

        List<QuestTarget> availableTargets = getAvailableTargets();
        if (availableTargets.isEmpty()) {
            Service.gI().sendThongBao(player,
                    "Không thể tạo nhiệm vụ Kanao vì map 187-190 chưa có quái hợp lệ.");
            return false;
        }

        QuestTarget target = availableTargets.get(Util.nextInt(0, availableTargets.size() - 1));
        int minKills = TaskConfig.getInt("task.kanao.killCountMin", 10, 1, 100_000);
        int maxKills = TaskConfig.getInt("task.kanao.killCountMax", 20, minKills, 100_000);
        player.kanaoQuestMapId = target.mapId;
        player.kanaoQuestMobId = target.mobId;
        player.kanaoQuestCount = 0;
        player.kanaoQuestRequiredCount = Util.nextInt(minKills, maxKills);
        return true;
    }

    public void checkDoneTaskKillMob(Player player, Mob mob) {
        if (player == null || mob == null || player.isBoss || player.isBot || player.isPet
                || !hasActiveQuest(player) || mob.zone == null || mob.zone.map == null
                || mob.zone.map.mapId != player.kanaoQuestMapId
                || mob.tempId != player.kanaoQuestMobId
                || player.kanaoQuestCount >= player.kanaoQuestRequiredCount) {
            return;
        }

        player.kanaoQuestCount++;
        if (player.kanaoQuestCount >= player.kanaoQuestRequiredCount) {
            Service.gI().sendThongBao(player,
                    "Đã hoàn thành nhiệm vụ Kanao! Hãy gặp Kanao ở map 0 hoặc 187 và chọn Nhận thưởng.");
        } else {
            Service.gI().sendThongBao(player, "Nhiệm vụ Kanao: "
                    + player.kanaoQuestCount + "/" + player.kanaoQuestRequiredCount + ".");
        }
    }

    public boolean claimReward(Player player) {
        if (!hasActiveQuest(player)) {
            Service.gI().sendThongBao(player, "Bạn chưa nhận nhiệm vụ từ Kanao.");
            return false;
        }
        if (player.kanaoQuestCount < player.kanaoQuestRequiredCount) {
            Service.gI().sendThongBao(player, "Bạn chưa hoàn thành nhiệm vụ Kanao ("
                    + player.kanaoQuestCount + "/" + player.kanaoQuestRequiredCount + ").");
            return false;
        }

        synchronized (player) {
            if (!hasActiveQuest(player) || player.kanaoQuestCount < player.kanaoQuestRequiredCount) {
                return false;
            }
            if (!TaskRewardService.grant(player, "kanao", REWARD_CONFIG_ID)) {
                return false;
            }
            resetQuest(player);
        }
        Service.gI().sendThongBao(player,
                "Bạn đã nhận thưởng nhiệm vụ Kanao. Hãy chọn Nhiệm vụ để nhận mục tiêu mới.");
        return true;
    }

    public String getQuestDescription(Player player) {
        if (!hasActiveQuest(player)) {
            return "Bạn chưa có nhiệm vụ Vô Hạn Thành.\nHãy chọn Nhiệm vụ để nhận một mục tiêu ngẫu nhiên.";
        }
        int progress = Math.min(player.kanaoQuestCount, player.kanaoQuestRequiredCount);
        return "Nhiệm vụ Vô Hạn Thành\nTiêu diệt " + player.kanaoQuestRequiredCount + " "
                + getMobName(player.kanaoQuestMobId) + " tại " + getMapName(player.kanaoQuestMapId)
                + ".\nTiến độ: " + progress + "/" + player.kanaoQuestRequiredCount;
    }

    private List<QuestTarget> getAvailableTargets() {
        List<QuestTarget> targets = new ArrayList<>();
        int[] mapIds = TaskConfig.getIntArray("task.kanao.mapIds", DEFAULT_MAP_IDS);
        for (int mapId : mapIds) {
            Map map = MapService.gI().getMapById(mapId);
            if (map == null || map.zones == null || map.zones.isEmpty()) {
                continue;
            }
            Set<Integer> mobIds = new LinkedHashSet<>();
            for (Mob mob : map.zones.get(0).mobs) {
                if (mob != null && Manager.getMobTemplateByTemp(mob.tempId) != null) {
                    mobIds.add(mob.tempId);
                }
            }
            for (int mobId : mobIds) {
                targets.add(new QuestTarget(mapId, mobId));
            }
        }
        return targets;
    }

    private String getMobName(int mobId) {
        Template.MobTemplate mobTemplate = Manager.getMobTemplateByTemp(mobId);
        return mobTemplate != null ? mobTemplate.name : "quái " + mobId;
    }

    private String getMapName(int mapId) {
        Map map = MapService.gI().getMapById(mapId);
        return map != null ? map.mapName : "map " + mapId;
    }

    private void resetQuest(Player player) {
        player.kanaoQuestMapId = -1;
        player.kanaoQuestMobId = -1;
        player.kanaoQuestCount = 0;
        player.kanaoQuestRequiredCount = 0;
    }

    private static final class QuestTarget {

        private final int mapId;
        private final int mobId;

        private QuestTarget(int mapId, int mobId) {
            this.mapId = mapId;
            this.mobId = mobId;
        }
    }
}
