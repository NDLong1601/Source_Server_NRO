package nro.models.npc_list;

import nro.models.consts.ConstNpc;
import nro.models.map.service.ChangeMapService;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.services.KanaoQuestService;
import nro.models.shop.ShopService;

public class Kanao extends Npc {

    public Kanao(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    @Override
    public void openBaseMenu(Player player) {
        if (!canOpenNpc(player)) {
            return;
        }
        if (this.mapId == 0 || this.mapId == 187) {
            String travelOption = this.mapId == 0 ? "Vào\nVô Hạn Thành" : "Về\nLàng Aru";
            this.createOtherMenu(player, ConstNpc.BASE_MENU,
                    "Xin chào chiến binh! Tôi có nhiệm vụ tại Vô Hạn Thành dành cho bạn.",
                    "Cửa Hàng", "Nhiệm vụ", "Nhận\nthưởng", travelOption, "Đóng");
        } else {
            this.createOtherMenu(player, ConstNpc.BASE_MENU,
                    "Xin chào chiến binh! Tôi là Kanao Tsuyuri.", "Đóng");
        }
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (!canOpenNpc(player)) {
            return;
        }

        if (player.idMark.isBaseMenu()) {
            switch (select) {
                case 0 -> ShopService.gI().opendShop(player, "KANAO", true);
                case 1 -> showQuest(player);
                case 2 -> KanaoQuestService.gI().claimReward(player);
                case 3 -> travel(player);
                default -> {
                }
            }
        }
    }

    private void showQuest(Player player) {
        KanaoQuestService questService = KanaoQuestService.gI();
        if (!questService.hasActiveQuest(player)) {
            questService.assignRandomQuest(player);
        }
        this.createOtherMenu(player, ConstNpc.KANAO_QUEST_MENU,
                questService.getQuestDescription(player), "Đóng");
    }

    private void travel(Player player) {
        if (this.mapId == 0) {
            ChangeMapService.gI().changeMapNonSpaceship(player, 187, 112, 360);
        } else if (this.mapId == 187) {
            ChangeMapService.gI().changeMapNonSpaceship(player, 0, 560, 432);
        }
    }
}
