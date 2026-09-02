package nro.models.services;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import nro.models.consts.ConstMob;
import nro.models.consts.ConstNpc;
import nro.models.consts.ConstPlayer;
import nro.models.map.ItemMap;
import nro.models.map.WayPoint;
import nro.models.map.service.ChangeMapService;
import nro.models.map.service.ItemMapService;
import nro.models.map.service.MapService;
import nro.models.mob.Mob;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.skill.Skill;
import nro.models.task.SubTaskMain;
import nro.models.task.TaskMain;

/**
 * Online delegation for early main-quest combat. Task counters are never
 * changed here: mobs, map items and NPCs use their normal server callbacks.
 */
public class AutoQuestService {

    private static final long DURATION_MS = 30 * 60 * 1000L;
    private static final long ACTION_INTERVAL_MS = 900L;
    private static final int TASK_2_CHICKEN_ITEM_ID = 73;

    private static AutoQuestService instance;

    private enum Action {
        NONE,
        TRAIN,
        KILL,
        COLLECT_CHICKEN,
        TALK_NPC
    }

    public static AutoQuestService gI() {
        if (instance == null) {
            instance = new AutoQuestService();
        }
        return instance;
    }

    private AutoQuestService() {
    }

    public boolean isActive(Player player) {
        return player != null && player.autoQuestEndTime > System.currentTimeMillis();
    }

    public boolean activate(Player player) {
        if (player == null || getAction(player) == Action.NONE) {
            if (player != null) {
                Service.gI().sendThongBao(player,
                        "Ủy thác hiện hỗ trợ di chuyển, train sức mạnh, đánh quái, nhặt gà và báo cáo NPC của nhóm nhiệm vụ đầu.");
            }
            return false;
        }
        player.autoQuestEndTime = System.currentTimeMillis() + DURATION_MS;
        player.autoQuestLastActionTime = 0;
        player.autoQuestTargetMapId = -1;
        player.autoQuestRouteFailureCount = 0;
        player.autoQuestStatus = "";
        setClientAutoTraining(player, false);
        Service.gI().sendThongBao(player,
                "Đã bật Ủy Thác Nhiệm Vụ trong 30 phút. Hệ thống chỉ đi waypoint và tàu liên hành tinh miễn phí.");
        return true;
    }

    public void deactivate(Player player, String message) {
        if (player == null) {
            return;
        }
        player.autoQuestEndTime = 0;
        player.autoQuestLastActionTime = 0;
        player.autoQuestTargetMapId = -1;
        player.autoQuestRouteFailureCount = 0;
        player.autoQuestStatus = "";
        setClientAutoTraining(player, false);
        if (message != null && !message.isEmpty()) {
            Service.gI().sendThongBao(player, message);
        }
    }

    public void update(Player player) {
        if (!isActive(player)) {
            if (player != null && player.autoQuestEndTime > 0) {
                deactivate(player, "Thời gian Ủy Thác Nhiệm Vụ đã kết thúc.");
            }
            return;
        }
        if (!player.isPl() || player.zone == null || player.zone.map == null) {
            return;
        }
        if (player.isDie()) {
            deactivate(player, "Ủy Thác Nhiệm Vụ đã dừng vì nhân vật đã gục.");
            return;
        }
        if (player.pvp != null) {
            deactivate(player, "Ủy Thác Nhiệm Vụ đã dừng vì nhân vật đang trong PvP.");
            return;
        }

        long now = System.currentTimeMillis();
        if (now - player.autoQuestLastActionTime < ACTION_INTERVAL_MS) {
            return;
        }
        player.autoQuestLastActionTime = now;

        Action action = getAction(player);
        int targetMapId = getTargetMapId(player, action);
        // The normal auto-training feature owns movement and target choice.
        // It must run only after delegation has reached the selected training
        // map, never while waypoint or space-ship travel is in progress.
        if (action != Action.TRAIN || player.zone.map.mapId != targetMapId) {
            setClientAutoTraining(player, false);
        }
        if (action == Action.NONE) {
            return;
        }
        if (!isAllowedMap(targetMapId)) {
            deactivate(player, "Không có tuyến di chuyển an toàn cho bước nhiệm vụ hiện tại.");
            return;
        }
        if (!moveToTargetMap(player, targetMapId)) {
            return;
        }

        switch (action) {
            case TRAIN -> trainForPower(player);
            case KILL -> {
                notifyStatus(player, "Đang đánh quái nhiệm vụ tại " + mapName(player.zone.map.mapId) + ".");
                attackRequiredMob(player);
            }
            case COLLECT_CHICKEN -> {
                notifyStatus(player, "Đang thu thập vật phẩm nhiệm vụ tại " + mapName(player.zone.map.mapId) + ".");
                collectChickenOrAttack(player);
            }
            case TALK_NPC -> {
                notifyStatus(player, "Đang báo cáo NPC tại " + mapName(player.zone.map.mapId) + ".");
                reportToNpc(player);
            }
            default -> { }
        }
    }

    private Action getAction(Player player) {
        TaskMain task = getCurrentTask(player);
        if (task == null) {
            return Action.NONE;
        }
        return switch (task.id) {
            case 1 -> task.index == 0 ? Action.KILL : task.index == 1 ? Action.TALK_NPC : Action.NONE;
            case 2 -> task.index == 0 ? Action.COLLECT_CHICKEN : task.index == 1 ? Action.TALK_NPC : Action.NONE;
            case 4, 5, 6 -> task.index >= 0 && task.index <= 2 ? Action.KILL
                    : task.index == 3 ? Action.TALK_NPC : Action.NONE;
            case 7 -> task.index == 0 ? Action.TRAIN : task.index == 1 ? Action.KILL
                    : (task.index == 2 || task.index == 3) ? Action.TALK_NPC : Action.NONE;
            case 10 -> task.index == 0 ? Action.TRAIN : task.index == 1 ? Action.KILL
                    : task.index == 2 ? Action.TALK_NPC : Action.NONE;
            default -> Action.NONE;
        };
    }

    private TaskMain getCurrentTask(Player player) {
        if (player == null || player.playerTask == null || player.playerTask.taskMain == null) {
            return null;
        }
        TaskMain task = player.playerTask.taskMain;
        return task.subTasks != null && task.index >= 0 && task.index < task.subTasks.size() ? task : null;
    }

    private int getTargetMapId(Player player, Action action) {
        TaskMain task = getCurrentTask(player);
        if (task == null) {
            return -1;
        }
        if (action == Action.TRAIN) {
            return task.id == 7 ? getTask7MapId(player.gender)
                    : task.id == 10 ? getTask10MapId(player.gender) : -1;
        }
        if (action == Action.KILL) {
            return switch (task.id) {
                case 1 -> getVillageMapId(player.gender);
                case 2 -> getTask2MapId(player.gender);
                case 4, 5, 6 -> getMotherMobMapId(getRequiredMobIds(player));
                case 7 -> getTask7MapId(player.gender);
                case 10 -> getTask10MapId(player.gender);
                default -> -1;
            };
        }
        return task.subTasks.get(task.index).mapId;
    }

    private int getVillageMapId(byte gender) {
        return switch (gender) {
            case ConstPlayer.TRAI_DAT -> 0;
            case ConstPlayer.NAMEC -> 7;
            default -> 14;
        };
    }

    private int getTask2MapId(byte gender) {
        return switch (gender) {
            case ConstPlayer.TRAI_DAT -> 1;
            case ConstPlayer.NAMEC -> 8;
            default -> 15;
        };
    }

    private int getMotherMobMapId(int[] mobIds) {
        if (mobIds.length != 1) {
            return -1;
        }
        return switch (mobIds[0]) {
            case ConstMob.KHUNG_LONG_ME -> 2;
            case ConstMob.LON_LOI_ME -> 9;
            case ConstMob.QUY_DAT_ME -> 16;
            default -> -1;
        };
    }

    private int getTask7MapId(byte gender) {
        return switch (gender) {
            case ConstPlayer.TRAI_DAT -> 3;
            case ConstPlayer.NAMEC -> 11;
            default -> 17;
        };
    }

    private int getTask10MapId(byte gender) {
        return switch (gender) {
            case ConstPlayer.TRAI_DAT -> 5;
            case ConstPlayer.NAMEC -> 13;
            default -> 20;
        };
    }

    /**
     * Moves only through ordinary waypoints. Inter-planet travel is available
     * solely between the three public space stations and does not spend gold,
     * gems, capsules, or any inventory item.
     */
    private boolean moveToTargetMap(Player player, int targetMapId) {
        int currentMapId = player.zone.map.mapId;
        if (player.autoQuestTargetMapId != targetMapId) {
            player.autoQuestTargetMapId = targetMapId;
            player.autoQuestStatus = "";
        }
        if (currentMapId == targetMapId) {
            player.autoQuestRouteFailureCount = 0;
            return true;
        }

        int nextMapId = findNextMapHop(currentMapId, targetMapId);
        if (nextMapId < 0) {
            deactivate(player, "Ủy Thác Nhiệm Vụ đã dừng: không tìm được tuyến waypoint miễn phí tới map nhiệm vụ.");
            return false;
        }

        notifyStatus(player, "Đang di chuyển tới " + mapName(targetMapId) + ".");
        if (isSpaceStation(currentMapId) && isSpaceStation(nextMapId)) {
            return takeFreeSpaceShip(player, nextMapId);
        }

        WayPoint waypoint = findWaypointToMap(player, nextMapId);
        if (waypoint == null) {
            registerRouteFailure(player, "Không tìm thấy cổng waypoint kế tiếp.");
            return false;
        }
        int x = (waypoint.minX + waypoint.maxX) / 2;
        int y = (waypoint.minY + waypoint.maxY) / 2;
        PlayerService.gI().playerMove(player, x, y);
        ChangeMapService.gI().changeMapWaypoint(player);
        if (player.zone == null || player.zone.map.mapId == currentMapId) {
            registerRouteFailure(player, "Không thể qua waypoint lúc này.");
        } else {
            player.autoQuestRouteFailureCount = 0;
        }
        return false;
    }

    private boolean takeFreeSpaceShip(Player player, int destinationStationId) {
        Npc stationNpc = findStationNpc(player);
        if (stationNpc == null) {
            registerRouteFailure(player, "Không tìm thấy NPC trạm tàu vũ trụ.");
            return false;
        }
        PlayerService.gI().playerMove(player, stationNpc.cx, stationNpc.cy);
        int currentMapId = player.zone.map.mapId;
        ChangeMapService.gI().changeMapBySpaceShip(player, destinationStationId, -1, -1);
        if (player.zone == null || player.zone.map.mapId == currentMapId) {
            registerRouteFailure(player, "Tàu vũ trụ chưa thể khởi hành lúc này.");
        } else {
            player.autoQuestRouteFailureCount = 0;
        }
        return false;
    }

    private Npc findStationNpc(Player player) {
        int expectedNpcId = switch (player.zone.map.mapId) {
            case 24 -> ConstNpc.DR_DRIEF;
            case 25 -> ConstNpc.CARGO;
            case 26 -> ConstNpc.CUI;
            default -> -1;
        };
        for (Npc npc : new ArrayList<>(player.zone.map.npcs)) {
            if (npc != null && npc.tempId == expectedNpcId) {
                return npc;
            }
        }
        return null;
    }

    private int findNextMapHop(int currentMapId, int targetMapId) {
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        java.util.Map<Integer, Integer> previous = new HashMap<>();
        Set<Integer> visited = new HashSet<>();
        queue.add(currentMapId);
        visited.add(currentMapId);

        while (!queue.isEmpty()) {
            int mapId = queue.removeFirst();
            if (mapId == targetMapId) {
                break;
            }
            for (int adjacentMapId : getAdjacentMaps(mapId)) {
                if (visited.add(adjacentMapId)) {
                    previous.put(adjacentMapId, mapId);
                    queue.addLast(adjacentMapId);
                }
            }
        }
        if (!visited.contains(targetMapId)) {
            return -1;
        }
        int hop = targetMapId;
        while (previous.containsKey(hop) && previous.get(hop) != currentMapId) {
            hop = previous.get(hop);
        }
        return hop;
    }

    private List<Integer> getAdjacentMaps(int mapId) {
        List<Integer> adjacent = new ArrayList<>();
        nro.models.map.Map map = MapService.gI().getMapById(mapId);
        if (map != null && map.wayPoints != null) {
            for (WayPoint waypoint : map.wayPoints) {
                if (waypoint != null && isAllowedMap(waypoint.goMap)) {
                    adjacent.add(waypoint.goMap);
                }
            }
        }
        if (isSpaceStation(mapId)) {
            for (int stationMapId : new int[]{24, 25, 26}) {
                if (stationMapId != mapId) {
                    adjacent.add(stationMapId);
                }
            }
        }
        return adjacent;
    }

    private WayPoint findWaypointToMap(Player player, int mapId) {
        for (WayPoint waypoint : player.zone.map.wayPoints) {
            if (waypoint != null && waypoint.goMap == mapId) {
                return waypoint;
            }
        }
        return null;
    }

    private boolean isSpaceStation(int mapId) {
        return mapId == 24 || mapId == 25 || mapId == 26;
    }

    private boolean isAllowedMap(int mapId) {
        if (mapId < 0 || mapId > 44 || MapService.gI().isMapBlackBallWar(mapId)
                || MapService.gI().isMapPhoBan(mapId) || MapService.gI().isMapMaBu(mapId)) {
            return false;
        }
        return MapService.gI().getMapById(mapId) != null;
    }

    private void registerRouteFailure(Player player, String reason) {
        player.autoQuestRouteFailureCount++;
        if (player.autoQuestRouteFailureCount >= 3) {
            deactivate(player, "Ủy Thác Nhiệm Vụ đã dừng: " + reason);
        }
    }

    private void notifyStatus(Player player, String status) {
        if (!status.equals(player.autoQuestStatus)) {
            player.autoQuestStatus = status;
            Service.gI().sendThongBao(player, status);
        }
    }

    private String mapName(int mapId) {
        nro.models.map.Map map = MapService.gI().getMapById(mapId);
        return map == null ? "map nhiệm vụ" : map.mapName;
    }

    private void collectChickenOrAttack(Player player) {
        if (InventoryService.gI().getCountEmptyBag(player) <= 0) {
            deactivate(player, "Ủy Thác Nhiệm Vụ đã dừng vì hành trang đã đầy.");
            return;
        }
        for (ItemMap itemMap : new ArrayList<>(player.zone.items)) {
            if (itemMap != null && !itemMap.isPickedUp && itemMap.itemTemplate != null
                    && itemMap.itemTemplate.id == TASK_2_CHICKEN_ITEM_ID
                    && (itemMap.playerId == player.id || itemMap.playerId == -1)) {
                PlayerService.gI().playerMove(player, itemMap.x, itemMap.y);
                ItemMapService.gI().pickItem(player, itemMap.itemMapId, false);
                return;
            }
        }
        attackRequiredMob(player);
    }

    private void trainForPower(Player player) {
        long requiredPower = getRequiredPower(player);
        int[] trainingMobIds = getTrainingMobIds(player);
        if (requiredPower <= 0 || trainingMobIds.length == 0 || player.nPoint == null) {
            deactivate(player, "Không tìm được cấu hình train an toàn cho nhiệm vụ hiện tại.");
            return;
        }
        if (player.nPoint.power >= requiredPower) {
            // Use the canonical task service rather than changing the task
            // index directly. This also handles characters who already met
            // the requirement before activating delegation.
            TaskService.gI().checkDoneTaskPower(player, player.nPoint.power);
            return;
        }
        notifyStatus(player, "Đang train tại " + mapName(player.zone.map.mapId)
                + " đến " + requiredPower + " sức mạnh.");
        // Use the established client-side Tự động luyện tập behaviour here.
        // Unlike a server teleport to a mob coordinate, it chooses nearby
        // targets and moves/attacks with the same visible behaviour players
        // already use for normal training.
        setClientAutoTraining(player, true);
    }

    private void setClientAutoTraining(Player player, boolean enabled) {
        if (player == null || player.autoQuestClientAutoPlay == enabled) {
            return;
        }
        player.autoQuestClientAutoPlay = enabled;
        ItemTimeService.gI().sendCanAutoPlay(player);
    }

    private long getRequiredPower(Player player) {
        TaskMain task = getCurrentTask(player);
        if (task == null) {
            return 0;
        }
        return switch (task.id) {
            case 7 -> 16_000L;
            case 10 -> 200_000L;
            default -> 0;
        };
    }

    private int[] getTrainingMobIds(Player player) {
        TaskMain task = getCurrentTask(player);
        if (task == null) {
            return new int[0];
        }
        return switch (task.id) {
            case 7 -> new int[]{getTask7MobId(player.gender)};
            case 10 -> new int[]{getTask10MobId(player.gender)};
            default -> new int[0];
        };
    }

    private void attackRequiredMob(Player player) {
        int[] requiredMobIds = getRequiredMobIds(player);
        if (requiredMobIds.length == 0) {
            return;
        }
        attackMobIds(player, requiredMobIds);
    }

    private void attackMobIds(Player player, int[] requiredMobIds) {
        Mob target = findMob(player, requiredMobIds);
        if (target == null) {
            return;
        }
        Skill skill = findAttackSkill(player);
        if (skill == null) {
            return;
        }
        player.playerSkill.skillSelect = skill;
        if (!SkillService.gI().canUseSkillWithMana(player) || !SkillService.gI().canUseSkillWithCooldown(player)) {
            return;
        }
        PlayerService.gI().playerMove(player, target.location.x, target.location.y);
        SkillService.gI().useSkillAttack(player, null, target);
    }

    private int[] getRequiredMobIds(Player player) {
        TaskMain task = getCurrentTask(player);
        if (task == null) {
            return new int[0];
        }
        return switch (task.id) {
            case 1 -> task.index == 0 ? new int[]{ConstMob.MOC_NHAN} : new int[0];
            case 2 -> task.index == 0 ? new int[]{getTask2MobId(player.gender)} : new int[0];
            case 4 -> switch (task.index) {
                case 0 -> new int[]{ConstMob.KHUNG_LONG_ME};
                case 1 -> new int[]{ConstMob.LON_LOI_ME};
                case 2 -> new int[]{ConstMob.QUY_DAT_ME};
                default -> new int[0];
            };
            case 5 -> switch (task.index) {
                case 0 -> new int[]{ConstMob.LON_LOI_ME};
                case 1 -> new int[]{ConstMob.KHUNG_LONG_ME};
                case 2 -> new int[]{ConstMob.QUY_DAT_ME};
                default -> new int[0];
            };
            case 6 -> switch (task.index) {
                case 0 -> new int[]{ConstMob.QUY_DAT_ME};
                case 1 -> new int[]{ConstMob.KHUNG_LONG_ME};
                case 2 -> new int[]{ConstMob.LON_LOI_ME};
                default -> new int[0];
            };
            case 7 -> task.index == 1 ? new int[]{getTask7MobId(player.gender)} : new int[0];
            case 10 -> task.index == 1
                    ? new int[]{getTask10MobId(player.gender)}
                    : new int[0];
            default -> new int[0];
        };
    }

    private int getTask2MobId(byte gender) {
        return switch (gender) {
            case ConstPlayer.TRAI_DAT -> ConstMob.KHUNG_LONG;
            case ConstPlayer.NAMEC -> ConstMob.LON_LOI;
            default -> ConstMob.QUY_DAT;
        };
    }

    private int getTask7MobId(byte gender) {
        return switch (gender) {
            case ConstPlayer.TRAI_DAT -> ConstMob.THAN_LAN_BAY;
            case ConstPlayer.NAMEC -> ConstMob.PHI_LONG;
            default -> ConstMob.QUY_BAY;
        };
    }

    private int getTask10MobId(byte gender) {
        return switch (gender) {
            case ConstPlayer.TRAI_DAT -> ConstMob.OC_MUON_HON;
            case ConstPlayer.NAMEC -> ConstMob.OC_SEN;
            default -> ConstMob.HEO_XAYDA_ME;
        };
    }

    private Mob findMob(Player player, int[] requiredMobIds) {
        for (Mob mob : new ArrayList<>(player.zone.mobs)) {
            if (mob == null || mob.isDie()) {
                continue;
            }
            for (int requiredMobId : requiredMobIds) {
                if (mob.tempId == requiredMobId) {
                    return mob;
                }
            }
        }
        return null;
    }

    private Skill findAttackSkill(Player player) {
        if (player.playerSkill == null || player.playerSkill.skills == null || player.effectSkill == null
                || player.effectSkill.isHaveEffectSkill()) {
            return null;
        }
        for (Skill skill : player.playerSkill.skills) {
            if (skill != null && skill.template != null && isDirectAttackSkill(skill.template.id)) {
                player.playerSkill.skillSelect = skill;
                if (SkillService.gI().canUseSkillWithMana(player)
                        && SkillService.gI().canUseSkillWithCooldown(player)) {
                    return skill;
                }
            }
        }
        return null;
    }

    private boolean isDirectAttackSkill(int skillId) {
        return switch (skillId) {
            case Skill.KAMEJOKO, Skill.MASENKO, Skill.ANTOMIC, Skill.DRAGON,
                    Skill.DEMON, Skill.GALICK, Skill.LIEN_HOAN -> true;
            default -> false;
        };
    }

    private void reportToNpc(Player player) {
        TaskMain task = getCurrentTask(player);
        if (task == null) {
            return;
        }
        SubTaskMain subTask = task.subTasks.get(task.index);
        if (subTask.npcId < 0) {
            return;
        }
        for (Npc npc : new ArrayList<>(player.zone.map.npcs)) {
            if (npc != null && npc.tempId == subTask.npcId) {
                PlayerService.gI().playerMove(player, npc.cx, npc.cy);
                TaskService.gI().checkDoneTaskTalkNpc(player, npc);
                return;
            }
        }
    }
}
