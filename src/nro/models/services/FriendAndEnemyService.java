package nro.models.services;

import nro.models.consts.ConstNpc;
import nro.models.player.Enemy;
import nro.models.player.Friend;
import nro.models.player.Player;
import nro.models.matches.PVPService;
import nro.models.network.Message;
import nro.models.server.Client;
import nro.models.map.service.MapService;
import nro.models.map.service.NpcService;
import nro.models.services.Service;
import nro.models.services.TaskService;
import nro.models.map.service.ChangeMapService;
import nro.models.utils.Logger;
import nro.models.utils.Util;
import java.io.IOException;

/**
 *
 * @author By Mr Blue
 * 
 */

public class FriendAndEnemyService {

    private static final byte OPEN_LIST = 0;

    private static final byte MAKE_FRIEND = 1;
    private static final byte REMOVE_FRIEND = 2;

    private static final byte REVENGE = 1;
    private static final byte REMOVE_ENEMY = 2;

    private static FriendAndEnemyService i;

    public static FriendAndEnemyService gI() {
        if (i == null) {
            i = new FriendAndEnemyService();
        }
        return i;
    }

    public void controllerFriend(Player player, Message msg) {
        try {
            byte action = msg.reader().readByte();
            switch (action) {
                case OPEN_LIST:
                    openListFriend(player);
                    break;
                case MAKE_FRIEND:
                    makeFriend(player, msg.reader().readInt());
                    break;
                case REMOVE_FRIEND:
                    removeFriend(player, msg.reader().readInt());
                    break;
            }
        } catch (IOException ex) {

        }
    }

    public void controllerEnemy(Player player, Message msg) {
        try {
            byte action = msg.reader().readByte();
            switch (action) {
                case OPEN_LIST:
                    openListEnemy(player);
                    break;
                case REVENGE:
//                    if (true) {
//                        Service.gI().sendThongBao(player, "Không thể thực hiện");
//                        break;
//                    }
                    int id = msg.reader().readInt();
                    boolean flag = false;
                    for (Enemy e : player.enemies) {
                        if (e.id == id) {
                            flag = true;
                            break;
                        }
                    }
                    if (flag) {
                        PVPService.gI().openSelectRevenge(player, id);
                    } else {
                        Service.gI().sendThongBao(player, "Không thể thực hiện");
                    }
                    break;
                case REMOVE_ENEMY:
                    removeEnemy(player, msg.reader().readInt());
                    break;
            }
        } catch (IOException ex) {

        }
    }

    private void reloadFriend(Player player) {
        for (Friend f : player.friends) {
            Player pl = null;
            if ((pl = Client.gI().getPlayerByUser(f.id)) != null || (pl = Client.gI().getPlayer(f.name)) != null) {
                try {
                    f.power = pl.nPoint.power;
                    f.head = pl.getHead();
                    f.body = pl.getBody();
                    f.leg = pl.getLeg();
                    f.bag = (byte) pl.getFlagBag();
                } catch (Exception e) {
                }
                f.online = true;
            } else {
                f.online = false;
            }
        }
    }

    private void reloadEnemy(Player player) {
        for (Enemy e : player.enemies) {
            Player pl = null;
            if ((pl = Client.gI().getPlayerByUser(e.id)) != null || (pl = Client.gI().getPlayer(e.name)) != null) {
                try {
                    e.power = pl.nPoint.power;
                    e.head = pl.getHead();
                    e.body = pl.getBody();
                    e.leg = pl.getLeg();
                    e.bag = (byte) pl.getFlagBag();
                } catch (Exception ex) {
                }
                e.online = true;
            } else {
                e.online = false;
            }
        }
    }

    private void openListFriend(Player player) {
        reloadFriend(player);
        Message msg;
        try {
            msg = new Message(-80);
            msg.writer().writeByte(OPEN_LIST);
            msg.writer().writeByte(player.friends.size());
            for (Friend f : player.friends) {
                msg.writer().writeInt(f.id);
                msg.writer().writeShort(f.head);
                msg.writer().writeShort(-1);
                msg.writer().writeShort(f.body);
                msg.writer().writeShort(f.leg);
                msg.writer().writeByte(f.bag);
                msg.writer().writeUTF(f.name);
                msg.writer().writeBoolean(Client.gI().getPlayer((int) f.id) != null);
                msg.writer().writeUTF(Util.numberToMoney(f.power));
            }
            player.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            Logger.logException(FriendAndEnemyService.class, e);
        }
    }

    private void openListEnemy(Player player) {
        reloadEnemy(player);
        Message msg;
        try {
            msg = new Message(-99);
            msg.writer().writeByte(OPEN_LIST);
            msg.writer().writeByte(player.enemies.size());
            for (Enemy e : player.enemies) {
                msg.writer().writeInt(e.id);
                msg.writer().writeShort(e.head);
                msg.writer().writeShort(-1);
                msg.writer().writeShort(e.body);
                msg.writer().writeShort(e.leg);
                msg.writer().writeShort(e.bag);
                msg.writer().writeUTF(e.name);
                msg.writer().writeUTF(Util.numberToMoney(e.power));
                msg.writer().writeBoolean(Client.gI().getPlayer((int) e.id) != null);
            }
            player.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            Logger.logException(FriendAndEnemyService.class, e);
        }
    }

    private void makeFriend(Player player, int playerId) {
        boolean madeFriend = false;
        for (Friend friend : player.friends) {
            if (friend.id == playerId) {
                Service.gI().sendThongBao(player, "Đã có trong danh sách bạn bè");
                madeFriend = true;
                break;
            }
        }
        if (!madeFriend) {
            Player pl = Client.gI().getPlayer(playerId);
            if (pl != null) {
                String npcSay;
                if (player.friends.size() >= 5) {
                    npcSay = "Bạn có muốn kết bạn với " + pl.name + " với phí là 5 ngọc ?";
                } else {
                    npcSay = "Bạn có muốn kết bạn với " + pl.name + " ?";
                }
                NpcService.gI().createMenuConMeo(player, ConstNpc.MAKE_FRIEND, -1, npcSay, new String[]{"Đồng ý", "Từ chối"}, playerId);
            }
        }
    }

    private void removeFriend(Player player, int playerId) {
        for (int i = 0; i < player.friends.size(); i++) {
            if (player.friends.get(i).id == playerId) {
                Service.gI().sendThongBao(player, "Đã xóa thành công "
                        + player.friends.get(i).name + " khỏi danh sách bạn");
                Message msg;
                try {
                    msg = new Message(-80);
                    msg.writer().writeByte(REMOVE_FRIEND);
                    msg.writer().writeInt((int) player.friends.get(i).id);
                    player.sendMessage(msg);
                    msg.cleanup();
                } catch (Exception e) {
                }
                player.friends.remove(i);
                break;
            }
        }
    }

    private void removeEnemy(Player player, int playerId) {
        for (int i = 0; i < player.enemies.size(); i++) {
            if (player.enemies.get(i).id == playerId) {
                player.enemies.remove(i);
                break;
            }
        }
        openListEnemy(player);
    }

    public void chatPrivate(Player player, Message msg) {
        if (Util.canDoWithTime(player.idMark.getLastTimeChatPrivate(), 5000)) {
            player.idMark.setLastTimeChatPrivate(System.currentTimeMillis());
            try {
                int playerId = msg.reader().readInt();
                String text = msg.reader().readUTF();
                Player pl = Client.gI().getPlayer(playerId);
                if (pl != null) {
                    Service.gI().chatPrivate(player, pl, text);
                }
            } catch (Exception e) {
            }
        }
    }

    public void acceptMakeFriend(Player player, int playerId) {
        Player pl = Client.gI().getPlayer(playerId);
        if (pl != null) {
            Friend friend = new Friend();
            friend.id = (int) pl.id;
            friend.name = pl.name;
            friend.power = pl.nPoint.power;
            friend.head = pl.getHead();
            friend.body = pl.getBody();
            friend.leg = pl.getLeg();
            friend.bag = (byte) pl.getFlagBag();
            player.friends.add(friend);
            Service.gI().sendThongBao(player, "Kết bạn thành công");
            Service.gI().chatPrivate(player, pl, player.name + " vừa mới kết bạn với " + pl.name);
        } else {
            Service.gI().sendThongBao(player, "Không tìm thấy hoặc đang Offline, vui lòng thử lại sau");
        }
    }

    public void goToPlayerWithYardrat(Player player, Message msg) {
        try {
            int playerId = msg.reader().readInt();
            Player pl = Client.gI().getPlayer(playerId);
            if (pl != null) {
                if (player.isAdmin() || player.nPoint.teleport) {
                    if (!pl.itemTime.isUseAnDanh || player.isAdmin()) {
                        if ((player.isAdmin() || !pl.zone.isFullPlayer()) && !MapService.gI().isMapOffline(pl.zone.map.mapId) && !MapService.gI().isMapBlackBallWar(pl.zone.map.mapId) && !MapService.gI().isMapPhoBan(pl.zone.map.mapId) && !MapService.gI().isMapMaBu(pl.zone.map.mapId)) {
                            ChangeMapService.gI().changeMapYardrat(player, ChangeMapService.gI().checkMapCanJoin(player, pl.zone), pl.location.x + Util.nextInt(-5, 5), pl.location.y);
                        } else {
                            Service.gI().sendThongBao(player, "Không thể thực hiện");
                        }
                    } else {
                        Service.gI().sendThongBao(player, "Không thể thực hiện");
                    }
                } else {
                    Service.gI().sendThongBao(player, "Yêu cầu trang bị có khả năng dịch chuyển tức thời");
                }
            }
        } catch (IOException ex) {

        }
    }

    public void addEnemy(Player player, Player enemy) {
        boolean hadEnemy = false;
        for (Enemy ene : player.enemies) {
            if (ene.id == ene.id) {
                hadEnemy = true;
            }
        }
        if (!hadEnemy) {
            Enemy e = new Enemy();
            e.id = (int) enemy.id;
            e.name = enemy.name;
            e.power = enemy.nPoint.power;
            e.head = enemy.getHead();
            e.body = enemy.getBody();
            e.leg = enemy.getLeg();
            e.bag = (byte) enemy.getFlagBag();
            player.enemies.add(e);
        }
    }
}
