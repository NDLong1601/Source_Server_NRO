package nro.models.services_dungeon;

import nro.models.map.phoban.SnakeWay;
import nro.models.clan.Clan;
import nro.models.clan.ClanMember;
import java.util.ArrayList;
import java.util.List;
import nro.models.map.Zone;
import nro.models.player.Player;
import nro.models.services.Service;
import nro.models.map.service.ChangeMapService;
import nro.models.utils.TimeUtil;
import nro.models.utils.Util;

/**
 *
 * @author By Mr Blue
 * 
 */

public class SnakeWayService {

    private static SnakeWayService instance;

    public static SnakeWayService gI() {
        if (instance == null) {
            instance = new SnakeWayService();
        }
        return instance;
    }

    public List<SnakeWay> conDuongRanDocs;

    private SnakeWayService() {
        this.conDuongRanDocs = new ArrayList<>();
        for (int i = 0; i < SnakeWay.AVAILABLE; i++) {
            this.conDuongRanDocs.add(new SnakeWay(i));
        }
    }

    public void addMapConDuongRanDoc(int id, Zone zone) {
        this.conDuongRanDocs.get(id).getZones().add(zone);
    }

    public void openConDuongRanDoc(Player player, byte level) {
        Clan clan = player.clan;
        if (clan != null) {
            ClanMember cm = clan.getClanMember((int) player.id);
            if (cm != null) {
                if (player.clanMember.getNumDateFromJoinTimeToToday() < 2) {
                    return;
                }
                if (!player.joinCDRD && !Util.isTimeDifferenceGreaterThanNDays(player.lastTimeJoinCDRD, 7)) {
                    Service.gI().sendThongBao(player, "Vui lòng đợi " + TimeUtil.getDateLeft(player.lastTimeJoinCDRD, 7 * 24 * 60 * 60) + " nữa");
                    return;
                } else if (!player.joinCDRD) {
                    player.talkToThuongDe = false;
                    player.talkToThanMeo = false;
                }
                if (player.clan.ConDuongRanDoc == null) {
                    if (level >= 1 && level <= 110) {
                        SnakeWay conDuongRanDoc = null;
                        for (SnakeWay cdrd : this.conDuongRanDocs) {
                            if (!cdrd.isOpened) {
                                conDuongRanDoc = cdrd;
                                break;
                            }
                        }
                        if (conDuongRanDoc != null) {
                            conDuongRanDoc.openConDuongRanDoc(player, player.clan, level);
                        } else {
                            Service.gI().sendThongBao(player, "Con đường rắn độc đã đầy, hãy quay lại sau 30 phút");
                            return;
                        }
                    } else {
                        Service.gI().sendThongBao(player, "Không thể thực hiện");
                        return;
                    }
                }
                if (player.clan.ConDuongRanDoc != null) {
                    player.joinCDRD = true;
                    player.lastTimeJoinCDRD = player.clan.lastTimeOpenConDuongRanDoc;
                    ChangeMapService.gI().changeMap(player, 143, -1, 1055 + (Util.nextInt(-10, 10)), 0);
                }
            }
        }
    }
}
