package nro.models.npc_list;import nro.models.consts.ConstNpc;
import nro.models.managers.SuperRankManager;
import nro.models.matches.dai_hoi_vo_thuat.SuperRankService;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.map.service.NpcService;
import nro.models.map.service.ChangeMapService;

public class TrongTai extends Npc {

    public TrongTai(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    @Override
    public void openBaseMenu(Player player) {
        if (!canOpenNpc(player)) return;

        if (mapId == 113) {
            if (SuperRankManager.gI().awaiting(player)) {
                int ordinal = SuperRankManager.gI().ordinal(player.id);
                createOtherMenu(player, ConstNpc.BASE_MENU, "Vui lòng chờ, số thứ tự của bạn là " + ordinal, "OK", "Về\nĐại Hội\nVõ Thuật");
            } else {
                String ticketMsg = player.superRank.ticket > 0 ? "Miễn phí\nCòn " + player.superRank.ticket + " vé" : "Thi đấu";
                createOtherMenu(player, ConstNpc.BASE_MENU,
                        "Đại hội võ thuật Siêu Hạng\ndiễn ra 24/7 kể cả ngày lễ và chủ nhật\nHãy thi đấu ngay để khẳng định đẳng cấp của mình nhé",
                        "Top 100\nCao Thủ", "Hướng\ndẫn\nthêm", ticketMsg, "Ưu tiên\nđấu ngay", "Về\nĐại Hội\nVõ Thuật");
            }
        } else {
            super.openBaseMenu(player);
        }
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (!canOpenNpc(player) || !player.idMark.isBaseMenu()) return;

        if (mapId == 113) {
            if (SuperRankManager.gI().awaiting(player)) {
                if (select == 1) {
                    ChangeMapService.gI().changeMapNonSpaceship(player, 52, player.location.x, 336);
                }
                return;
            }

            switch (select) {
                case 0 -> SuperRankService.gI().topList(player, 0);
                case 1 -> NpcService.gI().createTutorial(player, tempId, avartar, ConstNpc.THONG_TIN_SIEU_HANG);
                case 2 -> SuperRankService.gI().topList(player, 1);
                case 3 -> SuperRankService.gI().topList(player, 2);
                case 4 -> ChangeMapService.gI().changeMapNonSpaceship(player, 52, player.location.x, 336);
            }
        }
    }
}