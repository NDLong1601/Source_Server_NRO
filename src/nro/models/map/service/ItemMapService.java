package nro.models.map.service;

import nro.models.map.ItemMap;
import nro.models.player.Player;
import nro.models.network.Message;
import nro.models.services.Service;
import nro.models.utils.Logger;
import nro.models.utils.Util;

public class ItemMapService {

    private static ItemMapService instance;

    public static ItemMapService gI() {
        if (instance == null) {
            instance = new ItemMapService();
        }
        return instance;
    }

    public void pickItem(Player player, int itemMapId, boolean isThuHut) {
        if (player != null && player.zone != null && player.idMark != null) {
            if (isThuHut || player.isBot || Util.canDoWithTime(player.idMark.getLastTimePickItem(), 1000)) {
                player.zone.pickItem(player, itemMapId);
                player.idMark.setLastTimePickItem(System.currentTimeMillis());
            }
        }
    }

    public void removeItemMapAndSendClient(ItemMap itemMap) {
        sendItemMapDisappear(itemMap);
        removeItemMap(itemMap);
    }

    public void sendItemMapDisappear(ItemMap itemMap) {
        Message msg;
        try {
            msg = new Message(-21);
            msg.writer().writeShort(itemMap.itemMapId);
            Service.gI().sendMessAllPlayerInMap(itemMap.zone, msg);
            msg.cleanup();
        } catch (Exception e) {
            Logger.logException(ItemMapService.class, e);
        }
    }

    public void removeItemMap(ItemMap itemMap) {
        itemMap.zone.removeItemMap(itemMap);
        itemMap.dispose();
    }

    public boolean findItemMapByPlayer(Player player, int tempId) {
        for (ItemMap it : player.zone.items) {
            if (it.playerId == player.id && it.itemTemplate.id == tempId) {
                return true;
            }
        }
        return false;
    }

    public boolean isBlackBall(int tempId) {
        return tempId >= 372 && tempId <= 378;
    }

    public boolean isNamecBall(int tempId) {
        return tempId >= 353 && tempId <= 360;
    }

    public boolean isNamecBallStone(int tempId) {
        return tempId == 362;
    }
}
