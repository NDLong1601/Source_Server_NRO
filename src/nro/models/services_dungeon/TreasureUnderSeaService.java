package nro.models.services_dungeon;

import nro.models.map.phoban.BanDoKhoBau;
import nro.models.item.Item;
import java.util.ArrayList;
import java.util.List;
import nro.models.map.Zone;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.Service;
import nro.models.utils.Util;

/**
 *
 * @author By Mr Blue
 * 
 */

public class TreasureUnderSeaService {

    private static TreasureUnderSeaService instance;

    public static TreasureUnderSeaService gI() {
        if (instance == null) {
            instance = new TreasureUnderSeaService();
        }
        return instance;
    }

    public List<BanDoKhoBau> banDoKhoBaus;

    private TreasureUnderSeaService() {
        this.banDoKhoBaus = new ArrayList<>();
        for (int i = 0; i < BanDoKhoBau.AVAILABLE; i++) {
            this.banDoKhoBaus.add(new BanDoKhoBau(i));
        }
    }

    public void addMapBanDoKhoBau(int id, Zone zone) {
        this.banDoKhoBaus.get(id).getZones().add(zone);
    }

    public void openBanDoKhoBau(Player player, byte level) {
        if (level >= 1 && level <= 110) {
            if (player.clan != null && player.clan.BanDoKhoBau == null) {
                Item item = InventoryService.gI().findItemBag(player, 611);
                if (item != null && item.quantity > 0) {
                    BanDoKhoBau banDoKhoBau = null;
                    for (BanDoKhoBau bdkb : this.banDoKhoBaus) {
                        if (!bdkb.isOpened) {
                            banDoKhoBau = bdkb;
                            break;
                        }
                    }
                    if (banDoKhoBau != null) {
                        if (Util.isAfterMidnight(player.lastTimeJoinBDKB)) {
                            player.timesPerDayBDKB = 1;
                        } else if (player.lastTimeJoinBDKB != player.clan.lastTimeOpenBanDoKhoBau) {
                            if (player.timesPerDayBDKB >= 3) {
                                Service.gI().sendThongBao(player, "Bạn đã vào hang kho báu 3 lần trong hôm nay, hẹn gặp lại ngày mai");
                                return;
                            }
                        }

                        InventoryService.gI().subQuantityItemsBag(player, item, 1);
                        InventoryService.gI().sendItemBags(player);
                        banDoKhoBau.openBanDoKhoBau(player, player.clan, level);
                    } else {
                        Service.gI().sendThongBao(player, "Hang kho báu đã đầy, hãy quay lại sau 30 phút");
                    }
                } else {
                    Service.gI().sendThongBao(player, "Không tìm thấy bản đồ kho báu");
                }
            } else {
                Service.gI().sendThongBao(player, "Không thể thực hiện");
            }
        }
    }
}
