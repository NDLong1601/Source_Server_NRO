package nro.models.services;

import java.util.List;
import nro.models.database.PKHistoryDAO;
import nro.models.network.Message;
import nro.models.player.Player;
import nro.models.utils.Logger;

/** Sends a compact list of a player's latest completed gold challenges to the client. */
public final class PKHistoryService {

    public static final byte ACTION_HISTORY = 43;
    private static PKHistoryService instance;

    private PKHistoryService() {
    }

    public static PKHistoryService gI() {
        if (instance == null) {
            instance = new PKHistoryService();
        }
        return instance;
    }

    public void sendHistory(Player player) {
        if (player == null) {
            return;
        }
        Message message = null;
        try {
            List<PKHistoryDAO.Entry> entries = PKHistoryDAO.findRecent(player.id);
            message = new Message(127);
            message.writer().writeByte(ACTION_HISTORY);
            message.writer().writeByte(entries.size());
            for (PKHistoryDAO.Entry entry : entries) {
                message.writer().writeUTF(entry.opponentName);
                message.writer().writeBoolean(entry.won);
                message.writer().writeInt((int) entry.completedAt);
            }
            message.writer().flush();
            player.sendMessage(message);
        } catch (Exception exception) {
            Logger.logException(PKHistoryService.class, exception, "Không thể gửi lịch sử PK");
        } finally {
            if (message != null) {
                message.cleanup();
            }
        }
    }
}
