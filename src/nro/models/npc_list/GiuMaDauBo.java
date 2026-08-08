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
import nro.models.services.Service;
import nro.models.shop.ShopService;
import nro.models.utils.Util;
import org.json.simple.JSONArray;

/**
 *
 * @author By Mr Blue
 *
 */
public class GiuMaDauBo extends Npc {

    public GiuMaDauBo(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    @Override
    public void openBaseMenu(Player player) {
        if (canOpenNpc(player)) {
            this.createOtherMenu(player, ConstNpc.BASE_MENU, "Ngươi đang muốn tìm mảnh vỡ và mảnh hồn bông tai Porata trong truyền thuyết, ta sẽ đưa ngươi đến đó ?",
                    "Khiêu chiến\nBoss", "Điểm danh\n+1 Capsule\nBang", "OK", "Cửa Hàng\nBang hội","Từ chối");
        }
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (canOpenNpc(player)) {
            switch (select) {
                case 0 -> {
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
                    Service.gI().sendThongBao(player, "Gấu Tướng Cướp đã xuất hiện!");
                }

                case 1 -> {
                    if (player.clan == null) {
                        Service.gI().sendThongBao(player, "Bạn cần gia nhập bang hội để điểm danh.");
                    } else if (player.event.luotNhanCapsuleBang == 0) {
                        Service.gI().sendThongBao(player, "Bạn đã nhận Capsule Bang hôm nay rồi.");
                    } else {
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
                        JSONArray checkNhanQuaData = new JSONArray();
                        checkNhanQuaData.add(0);
                        Service.gI().sendThongBao(player, "Bạn đã điểm danh và nhận được 1 Capsule Bang.");
                        for (ClanMember cm : player.clan.getMembers()) {
                            Player pl = Client.gI().getPlayer(cm.id);
                            if (pl != null) {
                                ClanService.gI().sendMyClan(pl);
                            }
                        }
                    }
                }

                case 2 -> {
                    if (player.nPoint.power < 40_000_000_000L) {
                        Service.gI().sendThongBao(player, "KHÔNG ĐỦ SỨC MẠNH");
                    } else {
                        player.type = 5;
                        player.maxTime = 5;
                        Service.gI().Transport(player);
                    }
                }
                case 3 -> {
                    ShopService.gI().opendShop(player, "SHOP_CLAN", false);
                }
                default -> {
                }
            }
        }
    }
}
