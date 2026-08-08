package nro.models.npc_list;

import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import nro.models.map.phoban.BanDoKhoBau;
import nro.models.services_dungeon.TreasureUnderSeaService;
import nro.models.npc.Npc;
import static nro.models.npc.NpcFactory.PLAYERID_OBJECT;
import nro.models.player.Player;
import nro.models.services.ItemService;
import nro.models.services.InventoryService;
import nro.models.map.service.NpcService;
import nro.models.services.RewardService;
import nro.models.services.Service;
import nro.models.shop.ShopService;
import nro.models.services.TaskService;
import nro.models.map.service.ChangeMapService;
import nro.models.services_func.Input;
import nro.models.services.PlayerService;
import nro.models.skill.Skill;
import nro.models.utils.Logger;
import nro.models.utils.SkillUtil;
import nro.models.utils.TimeUtil;
import nro.models.utils.Util;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QuyLaoKame extends Npc {

    private static class RewardItem {

        int itemId;
        int quantity;

        public RewardItem(int itemId, int quantity) {
            this.itemId = itemId;
            this.quantity = quantity;
        }
    }

    private static class KOLQuestData {

        int questType;
        int itemId;
        int requiredQuantity;

        List<RewardItem> rewards;
        String description;

        public KOLQuestData(int questType, int itemId, int requiredQuantity, List<RewardItem> rewards, String description) {
            this.questType = questType;
            this.itemId = itemId;
            this.requiredQuantity = requiredQuantity;
            this.rewards = rewards;
            this.description = description;
        }
    }

    private static final Map<Integer, KOLQuestData> KOL_QUESTS = new HashMap<>();

    static {
        KOL_QUESTS.put(1, new KOLQuestData(ConstNpc.KOL_QUEST_TYPE_ITEM_COLLECTION, 1778, 100, Arrays.asList(new RewardItem(1821, 5)), "Nhiệm vụ 1:\nThu thập 100 cuốn chả giò (Quái doanh trại)"));
        KOL_QUESTS.put(2, new KOLQuestData(ConstNpc.KOL_QUEST_TYPE_ITEM_COLLECTION, 1824, 10, Arrays.asList(new RewardItem(1592, 5), new RewardItem(1757, 5)), "Nhiệm vụ 2:\nThu thập 10 chai cuke 2 lít (Boss doanh trại)"));
        KOL_QUESTS.put(3, new KOLQuestData(ConstNpc.KOL_QUEST_TYPE_DUNGEON_COMPLETION, -1, 20, Arrays.asList(new RewardItem(1360, 1)), "Nhiệm vụ 3:\nHoàn thành phó bản Destron Gas cấp 70 trên 20 lần"));
        KOL_QUESTS.put(4, new KOLQuestData(ConstNpc.KOL_QUEST_TYPE_PVP_WINS, -1, 10, Arrays.asList(new RewardItem(1654, 1)), "Nhiệm vụ 4:\nĐánh bại 10 người trong đại hội võ thuật"));
        KOL_QUESTS.put(5, new KOLQuestData(ConstNpc.KOL_QUEST_TYPE_DAILY_QUEST_COMPLETION, -1, 30, Arrays.asList(new RewardItem(1822, 10)), "Nhiệm vụ 5:\nHoàn thành 30 nhiệm vụ siêu khó hàng ngày"));
        KOL_QUESTS.put(6, new KOLQuestData(ConstNpc.KOL_QUEST_TYPE_BOSS_DEFEAT_PARTICIPATION, -1, 5, Arrays.asList(new RewardItem(1797, 1), new RewardItem(1592, 5), new RewardItem(1757, 5)), "Nhiệm vụ 6:\nTham gia hạ gục boss baby 5 lần"));
        KOL_QUESTS.put(7, new KOLQuestData(ConstNpc.KOL_QUEST_TYPE_MONSTER_KILL_COUNT, -1, 100000, Arrays.asList(new RewardItem(1592, 10), new RewardItem(664, 9999), new RewardItem(1757, 5)), "Nhiệm vụ 7:\nHạ 100.000 quái (dùng tự động luyện tập)"));
    }

    private static final Map<Integer, KOLQuestData> KOL_VIP_QUESTS = new HashMap<>();

    static {
        KOL_VIP_QUESTS.put(1, new KOLQuestData(ConstNpc.KOL_QUEST_TYPE_ITEM_COLLECTION, 1778, 100, Arrays.asList(new RewardItem(1821, 10)), "Nhiệm vụ 1:\nThu thập 100 cuốn chả giò (Quái doanh trại)"));
        KOL_VIP_QUESTS.put(2, new KOLQuestData(ConstNpc.KOL_QUEST_TYPE_ITEM_COLLECTION, 1824, 10, Arrays.asList(new RewardItem(1592, 10), new RewardItem(1757, 10)), "Nhiệm vụ 2:\nThu thập 10 chai cuke 2 lít (Boss doanh trại)"));
        KOL_VIP_QUESTS.put(3, new KOLQuestData(ConstNpc.KOL_QUEST_TYPE_DUNGEON_COMPLETION, -1, 20, Arrays.asList(new RewardItem(1360, 1)), "Nhiệm vụ 3:\nHoàn thành phó bản Destron Gas cấp 70 trên 20 lần"));
        KOL_VIP_QUESTS.put(4, new KOLQuestData(ConstNpc.KOL_QUEST_TYPE_PVP_WINS, -1, 10, Arrays.asList(new RewardItem(1654, 1)), "Nhiệm vụ 4:\nĐánh bại 10 người trong đại hội võ thuật"));
        KOL_VIP_QUESTS.put(5, new KOLQuestData(ConstNpc.KOL_QUEST_TYPE_DAILY_QUEST_COMPLETION, -1, 30, Arrays.asList(new RewardItem(1822, 20)), "Nhiệm vụ 5:\nHoàn thành 30 nhiệm vụ siêu khó hàng ngày"));
        KOL_VIP_QUESTS.put(6, new KOLQuestData(ConstNpc.KOL_QUEST_TYPE_BOSS_DEFEAT_PARTICIPATION, -1, 5, Arrays.asList(new RewardItem(1797, 1), new RewardItem(1592, 10), new RewardItem(1757, 10)), "Nhiệm vụ 6:\nTham gia hạ gục boss baby 5 lần"));
        KOL_VIP_QUESTS.put(7, new KOLQuestData(ConstNpc.KOL_QUEST_TYPE_MONSTER_KILL_COUNT, -1, 100000, Arrays.asList(new RewardItem(1592, 20), new RewardItem(1757, 10)), "Nhiệm vụ 7:\nHạ 100.000 quái (dùng tự động luyện tập)"));
    }

    public QuyLaoKame(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    @Override
    public void openBaseMenu(Player player) {
        Item ruacon = InventoryService.gI().findItemBag(player, 874);
        if (canOpenNpc(player)) {
            ArrayList<String> menu = new ArrayList<>();
            if (!player.canReward) {
                menu.add("Nói\nchuyện");
                menu.add("Đổi điểm\nsự kiện\n[" + player.event.getEventPoint() + "]");
                menu.add("Nhận quà\nKOL");
                menu.add("Nhận quà\nKOL VIP");
                if (ruacon != null && ruacon.quantity >= 1) {
                    menu.add("Giao\nRùa con");
                }
            } else {
                menu.add("Giao\nLân con");
            }
            String[] menus = menu.toArray(String[]::new);
            if (!TaskService.gI().checkDoneTaskTalkNpc(player, this)) {
                this.createOtherMenu(player, ConstNpc.BASE_MENU, "Con muốn hỏi gì nào?", menus);
            }
        }
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (!canOpenNpc(player)) {
            return;
        }

        if (player.canReward) {
            RewardService.gI().rewardLancon(player);
            return;
        }

        switch (player.idMark.getIndexMenu()) {
            case ConstNpc.BASE_MENU:
                handleBaseMenu(player, select);
                break;
            case 12:
                handleMenu12(player, select);
                break;
            case 0:
                handleMenu0(player, select);
                break;
            case 4:
                handleMenu4(player, select);
                break;
            case ConstNpc.MENU_OPENED_DBKB:
                handleMenuOpenedDBKB(player, select);
                break;
            case ConstNpc.MENU_OPEN_DBKB:
                handleMenuOpenDBKB(player, select);
                break;
            case ConstNpc.MENU_ACCEPT_GO_TO_BDKB:
                handleMenuAcceptGoToBDKB(player, select);
                break;
            case ConstNpc.KOL_QUEST_MENU:
                handleKOLQuestRewardConfirm(player, select, false);
                break;
            case ConstNpc.KOL_VIP_REWARD_MENU:
                handleKOLQuestRewardConfirm(player, select, true);
                break;
        }
    }

    private void handleBaseMenu(Player player, int select) {
        switch (select) {
            case 0:
                handleTalk(player);
                break;
            case 1:
                ShopService.gI().opendShop(player, "SHOP_DOI_DIEM", false);
                break;
            case 2:
                handleKOLQuest(player, false);
                break;
            case 3:
                handleKOLQuest(player, true);
                break;
            case 4:
                handleTradeRuacon(player);
                break;
        }
    }

    private void handleTalk(Player player) {
        if (player.LearnSkill.Time != -1 && player.LearnSkill.Time <= System.currentTimeMillis()) {
            player.LearnSkill.Time = -1;
            try {
                var curSkill = SkillUtil.createSkill(SkillUtil.getTempSkillSkillByItemID(player.LearnSkill.ItemTemplateSkillId), SkillUtil.getSkillByItemID(player, player.LearnSkill.ItemTemplateSkillId).point);
                player.BoughtSkill.add((int) player.LearnSkill.ItemTemplateSkillId);
                SkillUtil.setSkill(player, curSkill);
                var msg = Service.gI().messageSubCommand((byte) 62);
                msg.writer().writeShort(curSkill.skillId);
                player.sendMessage(msg);
                msg.cleanup();
                PlayerService.gI().sendInfoHpMpMoney(player);
            } catch (Exception e) {
                Logger.log(e.toString());
            }
        }

        ArrayList<String> menu = new ArrayList<>();
        menu.add("Nhiệm vụ");
        menu.add("Học\nKỹ năng");
        if (player.clan != null) {
            menu.add("Về khu\nvực bang");
            menu.add("Kho báu\ndưới biển");
            if (player.clan.isLeader(player)) {
                menu.add("Giải tán\nBang hội");
            }
        }
        this.createOtherMenu(player, 0, "Chào con, ta rất vui khi gặp con\nCon muốn làm gì nào ?", menu.toArray(new String[0]));
    }

    private void handleTradeRuacon(Player player) {
        Item ruacon = InventoryService.gI().findItemBag(player, 874);
        if (ruacon != null && ruacon.quantity >= 1) {
            this.createOtherMenu(player, 1, "Cảm ơn cậu đã cứu con rùa của ta\nĐể cảm ơn ta sẽ tặng cậu món quà.", "Nhận quà", "Đóng");
        }
    }

    private void learnSkill(Player player) {
        try {
            String[] subName = ItemService.gI().getTemplate(player.LearnSkill.ItemTemplateSkillId).name.split("");
            byte level = Byte.parseByte(subName[subName.length - 1]);
            Skill curSkill = SkillUtil.createSkill(SkillUtil.getTempSkillSkillByItemID(player.LearnSkill.ItemTemplateSkillId), level);
            player.BoughtSkill.add((int) player.LearnSkill.ItemTemplateSkillId);
            SkillUtil.setSkill(player, curSkill);
            var msg = Service.gI().messageSubCommand((byte) 62);
            msg.writer().writeShort(curSkill.skillId);
            player.sendMessage(msg);
            msg.cleanup();
            PlayerService.gI().sendInfoHpMpMoney(player);
        } catch (Exception e) {
            Logger.log(e.toString());
        }
    }

    private void handleMenu12(Player player, int select) {
        switch (select) {
            case 0 -> {
                var time = player.LearnSkill.Time - System.currentTimeMillis();
                var ngoc = 5;
                if (time / 600_000 >= 2) {
                    ngoc += time / 600_000;
                }
                if (player.inventory.gem < ngoc) {
                    Service.gI().sendThongBao(player, "Bạn không có đủ ngọc");
                    return;
                }
                player.inventory.subGem(ngoc);
                player.LearnSkill.Time = -1;
                learnSkill(player);
            }
            case 1 ->
                createOtherMenu(player, 13, "Con có muốn huỷ học kỹ năng này và nhận lại 50% số tiềm năng không ?", "Ok", "Đóng");
        }
    }

    private void handleMenu0(Player player, int select) {
        switch (select) {
            case 0: // Nhiệm vụ
                NpcService.gI().createTutorial(player, tempId, avartar, player.playerTask.taskMain.subTasks.get(player.playerTask.taskMain.index).name);
                break;
            case 1: // Học Kỹ năng
                if (player.LearnSkill.Time != -1) {
                    handleSkillLearning(player);
                } else {
                    ShopService.gI().opendShop(player, "QUY_LAO", false);
                }
                break;
            case 2: // Về khu vực bang
                handleClanMapChange(player);
                break;
            default:
                if (player.clan != null) {
                    if (select == 3) {
                        handleTreasureMap(player);
                    } else if (player.clan.isLeader(player) && select == 4) {
                        handleClanDissolution(player);
                    }
                }
                break;
        }
    }

    private void handleSkillLearning(Player player) {
        if (player.LearnSkill.Time != -1) {
            var ngoc = 5;
            var time = player.LearnSkill.Time - System.currentTimeMillis();
            if (time / 600_000 >= 2) {
                ngoc += time / 600_000;
            }
            String[] subName = ItemService.gI().getTemplate(player.LearnSkill.ItemTemplateSkillId).name.split("");
            byte level = Byte.parseByte(subName[subName.length - 1]);
            createOtherMenu(player, 12,
                    "Con đang học kỹ năng\n" + SkillUtil.findSkillTemplate(SkillUtil.getTempSkillSkillByItemID(player.LearnSkill.ItemTemplateSkillId)).name
                    + " cấp " + level + "\nThời gian còn lại " + TimeUtil.getTime(time),
                    "Học Cấp tốc " + ngoc + " ngọc", "Huỷ", "Bỏ qua");
        } else {
            ShopService.gI().opendShop(player, "QUY_LAO", false);
        }
    }

    private void handleClanMapChange(Player player) {
        if (player.clan != null) {
            ChangeMapService.gI().changeMapNonSpaceship(player, 153, Util.nextInt(100, 200), 432);
        } else {
            Service.gI().sendThongBao(player, "Bạn cần có bang hội để thực hiện chức năng này.");
        }
    }

    private void handleClanDissolution(Player player) {
        if (player.clan != null && player.clan.isLeader(player)) {
            createOtherMenu(player, 4, "Con có chắc muốn giải tán bang hội không?", "Đồng ý", "Từ chối");
        }
    }

    private void handleTreasureMap(Player player) {
        if (player.clan != null && player.clan.BanDoKhoBau != null) {
            this.createOtherMenu(player, ConstNpc.MENU_OPENED_DBKB, "Bang hội con đang ở hang kho báu cấp " + player.clan.BanDoKhoBau.level + "\ncon có muốn đi cùng họ không?", "Top\nBang hội", "Thành tích\nBang", "Đồng ý", "Từ chối");
        } else {
            this.createOtherMenu(player, ConstNpc.MENU_OPEN_DBKB, "Đây là bản đồ kho báu hải tặc tí hon\nCác con cứ yên tâm lên đường\nỞ đây có ta lo\nNhớ chọn cấp độ vừa sức mình nhé", "Top\nBang hội", "Thành tích\nBang", "Chọn\ncấp độ", "Từ chối");
        }
    }

    private void handleMenu4(Player player, int select) {
        if (player.clan != null && player.clan.isLeader(player) && select == 0) {
            Input.gI().createFormGiaiTanBangHoi(player);
        }
    }

    private void handleMenuOpenedDBKB(Player player, int select) {
        if (select == 0) {
            Service.gI().showTopClanBDKB(player);
        } else if (select == 1) {
            Service.gI().showMyTopClanBDKB(player);
        } else if (select == 2) {
            if (player.isAdmin() || player.nPoint.power >= BanDoKhoBau.POWER_CAN_GO_TO_DBKB) {
                if (player.clan.BanDoKhoBau != null) {
                    ChangeMapService.gI().goToDBKB(player);
                } else {
                    this.npcChat(player, "Bang hội của con hiện không có hang kho báu đang hoạt động.");
                }
            } else {
                this.npcChat(player, "Yêu cầu sức mạnh lớn hơn " + Util.numberToMoney(BanDoKhoBau.POWER_CAN_GO_TO_DBKB));
            }
        }
    }

    private void handleMenuOpenDBKB(Player player, int select) {
        if (select == 0) {
            Service.gI().showTopClanBDKB(player);
        } else if (select == 1) {
            Service.gI().showTopClanBDKB(player);
        } else if (select == 2) {
            if (player.clan == null) {
                Service.gI().sendThongBao(player, "Hãy vào bang hội trước");
                return;
            }
            if (player.isAdmin() || player.nPoint.power >= BanDoKhoBau.POWER_CAN_GO_TO_DBKB) {
                Input.gI().createFormChooseLevelBDKB(player);
            } else {
                this.npcChat(player, "Yêu cầu sức mạnh lớn hơn " + Util.numberToMoney(BanDoKhoBau.POWER_CAN_GO_TO_DBKB));
            }
        }
    }

    private void handleMenuAcceptGoToBDKB(Player player, int select) {
        if (select == 0) {
            TreasureUnderSeaService.gI().openBanDoKhoBau(player, Byte.parseByte(String.valueOf(PLAYERID_OBJECT.get(player.id))));
        }
    }

    private void handleKOLQuest(Player player, boolean isVIP) {
        int currentStage;
        Map<Integer, KOLQuestData> questsMap;
        int menuType;

        if (isVIP) {
            currentStage = player.kolVIPQuestStage;
            questsMap = KOL_VIP_QUESTS;
            menuType = ConstNpc.KOL_VIP_REWARD_MENU;
        } else {
            currentStage = player.kolQuestStage;
            questsMap = KOL_QUESTS;
            menuType = ConstNpc.KOL_QUEST_MENU;
        }

        if (currentStage < 1) {
            currentStage = 1;
            if (isVIP) {
                player.kolVIPQuestStage = 1;
            } else {
                player.kolQuestStage = 1;
            }
        }

        KOLQuestData questData = questsMap.get(currentStage);

        if (questData == null) {
            this.createOtherMenu(player, menuType, "Con đã hoàn thành tất cả nhiệm vụ " + (isVIP ? "KOL VIP" : "KOL") + " rồi! Chúc mừng con!", "Đóng");
            return;
        }

        long currentProgress = 0;
        switch (questData.questType) {
            case ConstNpc.KOL_QUEST_TYPE_ITEM_COLLECTION:
                Item requiredItem = InventoryService.gI().findItemBag(player, questData.itemId);
                currentProgress = requiredItem != null ? requiredItem.quantity : 0;
                break;
            case ConstNpc.KOL_QUEST_TYPE_DUNGEON_COMPLETION:
                currentProgress = player.destronGas70CompletionCount;
                break;
            case ConstNpc.KOL_QUEST_TYPE_PVP_WINS:
                currentProgress = player.martialArtsTournamentWins;
                break;
            case ConstNpc.KOL_QUEST_TYPE_DAILY_QUEST_COMPLETION:
                currentProgress = player.dailySuperHardQuestCompletionCount;
                break;
            case ConstNpc.KOL_QUEST_TYPE_BOSS_DEFEAT_PARTICIPATION:
                currentProgress = player.bossBabyDefeatParticipationCount;
                break;
            case ConstNpc.KOL_QUEST_TYPE_MONSTER_KILL_COUNT:
                currentProgress = player.monsterKillCountAutoTrain;
                break;
            default:
                Service.gI().sendThongBao(player, "Lỗi: Loại nhiệm vụ không xác định.");
                return;
        }

        int percent = (int) ((currentProgress * 100.0) / questData.requiredQuantity);
        if (percent > 100) {
            percent = 100;
        }

        String rewardDetails = formatRewardDetails(questData.rewards);

        String npcText = questData.description + "\nPhần thưởng: " + rewardDetails + "\nHoàn thành: "
                + currentProgress + "/" + questData.requiredQuantity + " (" + percent + "%)";

        if (currentProgress >= questData.requiredQuantity) {
            this.createOtherMenu(player, menuType, npcText, "Nhận thưởng", "Đóng");
        } else {
            this.createOtherMenu(player, menuType, npcText, "Đóng");
        }
    }

    private void handleKOLQuestRewardConfirm(Player player, int select, boolean isVIP) {
        if (select == 0) {
            int currentStage;
            Map<Integer, KOLQuestData> questsMap;

            if (isVIP) {
                Item vipTicket = InventoryService.gI().findItemBag(player, 1825);
                if (vipTicket == null || vipTicket.quantity < 1) {
                    Service.gI().sendThongBao(player, "Bạn cần có Vé Nhiệm Vụ VIP để nhận thưởng KOL VIP.");
                    openBaseMenu(player);
                    return;
                }
                currentStage = player.kolVIPQuestStage;
                questsMap = KOL_VIP_QUESTS;
            } else {
                currentStage = player.kolQuestStage;
                questsMap = KOL_QUESTS;
            }

            if (currentStage < 1) {
                currentStage = 1;
                if (isVIP) {
                    player.kolVIPQuestStage = 1;
                } else {
                    player.kolQuestStage = 1;
                }
            }

            KOLQuestData questData = questsMap.get(currentStage);

            if (questData == null) {
                Service.gI().sendThongBao(player, "Không tìm thấy nhiệm vụ hiện tại.");
                return;
            }

            long currentProgress = 0;
            switch (questData.questType) {
                case ConstNpc.KOL_QUEST_TYPE_ITEM_COLLECTION:
                    Item requiredItem = InventoryService.gI().findItemBag(player, questData.itemId);
                    currentProgress = requiredItem != null ? requiredItem.quantity : 0;
                    break;
                case ConstNpc.KOL_QUEST_TYPE_DUNGEON_COMPLETION:
                    currentProgress = player.destronGas70CompletionCount;
                    break;
                case ConstNpc.KOL_QUEST_TYPE_PVP_WINS:
                    currentProgress = player.martialArtsTournamentWins;
                    break;
                case ConstNpc.KOL_QUEST_TYPE_DAILY_QUEST_COMPLETION:
                    currentProgress = player.dailySuperHardQuestCompletionCount;
                    break;
                case ConstNpc.KOL_QUEST_TYPE_BOSS_DEFEAT_PARTICIPATION:
                    currentProgress = player.bossBabyDefeatParticipationCount;
                    break;
                case ConstNpc.KOL_QUEST_TYPE_MONSTER_KILL_COUNT:
                    currentProgress = player.monsterKillCountAutoTrain;
                    break;
                default:
                    Service.gI().sendThongBao(player, "Lỗi: Loại nhiệm vụ không xác định.");
                    return;
            }

            if (currentProgress >= questData.requiredQuantity) {
                if (questData.questType == ConstNpc.KOL_QUEST_TYPE_ITEM_COLLECTION) {
                    Item requiredItem = InventoryService.gI().findItemBag(player, questData.itemId);
                    if (requiredItem != null) {
                        InventoryService.gI().subQuantityItemsBag(player, requiredItem, questData.requiredQuantity);
                    }
                }

                for (RewardItem rewardData : questData.rewards) {
                    Item rewardItem = ItemService.gI().createNewItem((short) rewardData.itemId);
                    rewardItem.quantity = rewardData.quantity;

                    List<Item.ItemOption> options = getPetBackpackOptions(rewardData.itemId);
                    if (options != null) {
                        rewardItem.itemOptions.addAll(options);
                    }

                    InventoryService.gI().addItemBag(player, rewardItem);
                }
                InventoryService.gI().sendItemBags(player);

                Service.gI().sendThongBao(player, "Bạn đã nhận phần thưởng nhiệm vụ " + (isVIP ? "KOL VIP" : "KOL") + " cấp " + currentStage + "!");

                if (isVIP) {
                    player.kolVIPQuestStage++;
                } else {
                    player.kolQuestStage++;
                }

                openBaseMenu(player);

            } else {
                Service.gI().sendThongBao(player, "Bạn không đủ điều kiện hoàn thành nhiệm vụ!");
                openBaseMenu(player);
            }
        } else {
            openBaseMenu(player);
        }
    }

    private List<Item.ItemOption> getPetBackpackOptions(int itemId) {
        List<Item.ItemOption> options = new ArrayList<>();
        options.add(new Item.ItemOption(73, 0));

        switch (itemId) {
            case 1360:
                options.add(new Item.ItemOption(77, 13));
                options.add(new Item.ItemOption(103, 13));
                options.add(new Item.ItemOption(50, 13));
                options.add(new Item.ItemOption(101, 20));
                options.add(new Item.ItemOption(30, 1));
                options.add(new Item.ItemOption(93, 90));
                break;
            case 1654:
                options.add(new Item.ItemOption(50, 16));
                options.add(new Item.ItemOption(77, 15));
                options.add(new Item.ItemOption(103, 15));
                options.add(new Item.ItemOption(106, 0));
                options.add(new Item.ItemOption(30, 1));
                options.add(new Item.ItemOption(93, 30));
                break;
            case 1797:
                options.add(new Item.ItemOption(50, 25));
                options.add(new Item.ItemOption(103, 30));
                options.add(new Item.ItemOption(30, 1));
                options.add(new Item.ItemOption(93, 90));
                break;
            default:
                return null;
        }
        return options;
    }

    private String formatRewardDetails(List<RewardItem> rewards) {
        StringBuilder details = new StringBuilder();
        for (int i = 0; i < rewards.size(); i++) {
            RewardItem rewardData = rewards.get(i);
            String rewardName = ItemService.gI().getTemplate(rewardData.itemId).name;
            details.append(rewardData.quantity).append(" ").append(rewardName);
            if (i < rewards.size() - 1) {
                details.append(", ");
            }
        }
        return details.toString();
    }
}
