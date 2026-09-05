package nro.models.npc_list;

import nro.models.clan.Clan;
import nro.models.clan.ClanMember;
import nro.models.consts.ConstMob;
import nro.models.consts.ConstNpc;
import nro.models.map.Zone;
import nro.models.mob.Mob;
import nro.models.mob_bigboss.GauTuongCuop;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.server.Client;
import nro.models.services.ClanService;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.Service;
import nro.models.item.Item;
import nro.models.clan.ClanShopService;
import nro.models.utils.Util;
import nro.models.activity.ActivityService;
import nro.models.activity.ActivityType;

/**
 *
 * @author By Mr Blue
 *
 */
public class GiuMaDauBo extends Npc {

    public GiuMaDauBo(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    private boolean shouldShowClanCheckIn(Player player) {
        return player.clan == null || player.event.luotNhanCapsuleBang != 0;
    }

    @Override
    public void openBaseMenu(Player player) {
        if (canOpenNpc(player)) {
            java.util.ArrayList<String> menus = new java.util.ArrayList<>();
            menus.add("Khiêu chiến\nBoss");
            if (shouldShowClanCheckIn(player)) {
                menus.add("Điểm danh\n+1 Capsule\n+1 Phiếu quà");
            }
            menus.add("Đến\nTây Thánh địa");
            menus.add("Từ chối");
            this.createOtherMenu(player, ConstNpc.BASE_MENU,
                    "Ngươi đang muốn tìm mảnh vỡ và mảnh hồn bông tai Porata trong truyền thuyết, ta sẽ đưa ngươi đến đó ?",
                    menus.toArray(String[]::new));
        }
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (!canOpenNpc(player)) {
            return;
        }
        if (select == 0) {
            challengeGauTuongCuop(player);
            return;
        }

        boolean showCheckIn = shouldShowClanCheckIn(player);
        if (showCheckIn && select == 1) {
            checkInClan(player);
            return;
        }

        int westHolyLandIndex = showCheckIn ? 2 : 1;
        if (select == westHolyLandIndex) {
            if (player.nPoint.power < 40_000_000_000L) {
                Service.gI().sendThongBao(player, "KHÔNG ĐỦ SỨC MẠNH");
            } else {
                player.type = 5;
                player.maxTime = 5;
                Service.gI().Transport(player);
            }
        }
    }

    private void challengeGauTuongCuop(Player player) {
        Zone zone = player.zone;
        if (zone == null) {
            return;
        }

        if (player.clan == null) {
            Service.gI().sendThongBao(player, "Bạn không có trong bang hội!");
            return;
        }
        Clan clan = player.clan;
        ClanMember member = clan.members.stream()
                .filter(m -> m.name.equals(player.name))
                .findFirst()
                .orElse(null);

        if (member == null || member.role != Clan.LEADER) {
            Service.gI().sendThongBao(player, "Chỉ bang chủ mới có quyền gọi Gấu Tướng Cướp!");
            return;
        }

        if (!Util.isAfterMidnight(clan.lastTimeOpenGauTuongCuop)) {
            Service.gI().sendThongBao(player, "Bang hội đã truy nã Gấu Tướng Cướp hôm nay, hẹn gặp lại ngày mai.");
            return;
        }

        long membersInZone = clan.membersInGame.stream()
                .filter(p -> p.zone != null && p.zone.equals(zone))
                .count();

        if (membersInZone < 3) {
            Service.gI().sendThongBao(player, "Cần ít nhất 3 thành viên bang hội!");
            return;
        }

        // Tạo Gấu Tướng Cướp
        Mob mobBase = new Mob();
        mobBase.tempId = ConstMob.GAU_TUONG_CUOP;
        mobBase.level = 1;
        mobBase.location = new nro.models.player.Location();
        mobBase.location.x = player.location.x + Util.nextInt(-30, 30);
        mobBase.location.y = player.location.y;
        mobBase.zone = zone;
        mobBase.point.setHpFull(2_000_000_000);
        mobBase.point.sethp(mobBase.point.getHpFull());
        mobBase.pDame = (byte) 50;
        mobBase.pTiemNang = 0;
        mobBase.setTiemNang();
        GauTuongCuop gauTuong = new GauTuongCuop(mobBase);
        zone.mobs.add(gauTuong);
        for (Player p : zone.getPlayers()) {
            Service.gI().loadMob(p, gauTuong);
        }
        clan.lastTimeOpenGauTuongCuop = System.currentTimeMillis();
        clan.update();
        Service.gI().sendThongBao(player, "Gấu Tướng Cướp đã xuất hiện!");
    }

    private void checkInClan(Player player) {
        if (player.clan == null) {
            Service.gI().sendThongBao(player, "Bạn cần gia nhập bang hội để điểm danh.");
            return;
        }
        if (player.event.luotNhanCapsuleBang == 0) {
            return;
        }

        Item giftTicket = ItemService.gI().createNewItem((short) ClanShopService.CLAN_GIFT_TICKET_ITEM_ID);
        giftTicket.itemOptions.add(new Item.ItemOption(30, 0));
        if (!InventoryService.gI().addItemBag(player, giftTicket)) {
            Service.gI().sendThongBao(player, "Hành trang đã đầy, cần chỗ trống để nhận Phiếu quà bang.");
            return;
        }

        player.lastClanCheckIn = System.currentTimeMillis();
        player.clan.capsuleClan += 1;

        for (ClanMember cm : player.clan.getMembers()) {
            if (cm.id == player.id) {
                cm.memberPoint += 1;
                cm.clanPoint += 1;
                break;
            }
        }
        player.event.luotNhanCapsuleBang = 0;
        InventoryService.gI().sendItemBags(player);
        // This is after all state mutations, so opening/retrying the NPC menu
        // cannot create an Activity event unless the clan check-in succeeded.
        ActivityService.gI().awardUnique(player, ActivityType.CLAN_CHECKIN, "clan-checkin");
        Service.gI().sendThongBao(player, "Bạn đã điểm danh và nhận được 1 Capsule Bang cùng 1 Phiếu quà bang.");
        for (ClanMember cm : player.clan.getMembers()) {
            Player pl = Client.gI().getPlayer(cm.id);
            if (pl != null) {
                ClanService.gI().sendMyClan(pl);
            }
        }
        // Refresh the menu immediately so the daily check-in button disappears.
        openBaseMenu(player);
    }
}
