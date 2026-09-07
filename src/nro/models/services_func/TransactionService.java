package nro.models.services_func;

import nro.models.data.LocalManager;
import nro.models.database.PlayerDAO;
import nro.models.player.Player;
import nro.models.network.Message;
import nro.models.server.Client;
import nro.models.server.Maintenance;
import nro.models.services.Service;
import nro.models.utils.Functions;
import nro.models.utils.Logger;
import nro.models.utils.TimeUtil;
import nro.models.utils.Util;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import nro.models.Bot.Bot;
import nro.models.server.ServerManager;

public class TransactionService implements Runnable {

    private static final int TIME_DELAY_TRADE = 10000;

    private static final byte SEND_INVITE_TRADE = 0;
    private static final byte ACCEPT_TRADE = 1;
    private static final byte ADD_ITEM_TRADE = 2;
    private static final byte CANCEL_TRADE = 3;
    private static final byte REMOVE_ITEM_TRADE = 4;
    private static final byte LOCK_TRADE = 5;
    private static final byte ACCEPT = 7;

    private static volatile TransactionService instance;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private TransactionService() {
    }

    public static TransactionService gI() {
        TransactionService localRef = instance;
        if (localRef == null) {
            synchronized (TransactionService.class) {
                localRef = instance;
                if (localRef == null) {
                    instance = localRef = new TransactionService();
                    localRef.startTimer();
                }
            }
        }
        return localRef;
    }

    private void startTimer() {
        if (running.compareAndSet(false, true)) {
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "TransactionService-Timer");
                t.setDaemon(true);
                return t;
            }).submit(this);
        }
    }

    public void controller(Player pl, Message msg) {
        Trade trade = null;
        try {
            byte action = msg.reader().readByte();
            int playerId = -1;
            Player plMap = null;
            trade = TradeRegistry.gI().getTrade(pl);
            if (Maintenance.isRunning && action != CANCEL_TRADE) {
                if (trade != null) {
                    trade.cancelFromSystem("SERVER_MAINTENANCE");
                }
                Service.gI().sendThongBao(pl, "Máy chủ đang bảo trì, không thể giao dịch");
                return;
            }
            if (pl.baovetaikhoan) {
                Service.gI().sendThongBao(pl, "Chức năng bảo vệ đã được bật. Bạn vui lòng kiểm tra lại");
                return;
            }
            if (action == SEND_INVITE_TRADE) {
                pl.idMark.setTransactionWP(false);
                pl.idMark.setTransactionWVP(false);
            }
            switch (action) {
                case SEND_INVITE_TRADE:
                case ACCEPT_TRADE:
                    if (!pl.getSession().actived) {
                        Service.gI().sendThongBao(pl,
                                "Truy Cập: " + ServerManager.DOMAIN + "\n Để Mở Thành Viên");
                        return;
                    }
                    playerId = msg.reader().readInt();
                    plMap = pl.zone.getPlayerInMap(playerId);
                    if (plMap != null && plMap.isPl()) {
                        if (plMap.tradeWVP) {
                            return;
                        }
                        trade = TradeRegistry.gI().getTrade(pl);
                        if (trade == null) {
                            trade = TradeRegistry.gI().getTrade(plMap);
                        }
                        if (trade == null) {
                            if (action == SEND_INVITE_TRADE) {
                                if (Util.canDoWithTime(pl.idMark.getLastTimeTrade(), TIME_DELAY_TRADE)
                                        && Util.canDoWithTime(plMap.idMark.getLastTimeTrade(), TIME_DELAY_TRADE)) {
                                    boolean checkLogout1 = false;
                                    boolean checkLogout2 = false;
                                    try (Connection con = LocalManager.getConnection()) {
                                        checkLogout1 = PlayerDAO.checkLogout(con, pl);
                                        checkLogout2 = PlayerDAO.checkLogout(con, plMap);
                                    } catch (Exception ignored) {
                                    }
                                    if (checkLogout1) {
                                        Client.gI().kickSession(pl.getSession());
                                        break;
                                    }
                                    if (checkLogout2) {
                                        Client.gI().kickSession(plMap.getSession());
                                        break;
                                    }
                                    pl.idMark.setLastTimeTrade(System.currentTimeMillis());
                                    pl.idMark.setPlayerTradeId((int) plMap.id);
                                    sendInviteTrade(pl, plMap);
                                } else {
                                    Service.gI().sendThongBao(pl, "Thử lại sau "
                                            + TimeUtil.getTimeLeft(Math.max(pl.idMark.getLastTimeTrade(),
                                                    plMap.idMark.getLastTimeTrade()), TIME_DELAY_TRADE / 1000));
                                }
                            } else {
                                if (plMap.idMark.getPlayerTradeId() == pl.id) {
                                    try {
                                        trade = new Trade(pl, plMap);
                                        trade.openTabTrade();
                                    } catch (IllegalArgumentException | IllegalStateException ex) {
                                        pl.idMark.setPlayerTradeId(-1);
                                        if (plMap.idMark.getPlayerTradeId() == pl.id) {
                                            plMap.idMark.setPlayerTradeId(-1);
                                        }
                                        Service.gI().sendThongBao(pl, "Không thể mở giao dịch này");
                                    }
                                }
                            }
                        } else {
                            Service.gI().sendThongBao(pl, "Không thể thực hiện");
                        }
                    }
                    break;
                case ADD_ITEM_TRADE:
                    if (trade != null) {
                        byte index = msg.reader().readByte();
                        int quantity = msg.reader().readInt();
                        if (quantity < 0) {
                            Service.gI().sendThongBao(pl, "Không thể thực hiện");
                            trade.cancelTrade(pl);
                            break;
                        }
                        if (index != -1 && quantity == 0) {
                            quantity = 1;
                        }
                        if (index != -1 && quantity > Trade.QUANLITY_MAX) {
                            Service.gI().sendThongBao(pl, "Đã quá giới hạn giao dịch...");
                            trade.cancelTrade(pl);
                            break;
                        }
                        if (index == -1 && (quantity > Trade.MAX_GOLD_TRADE_PER_TIME
                                || pl.inventory == null || quantity > pl.inventory.gold)) {
                            Service.gI().sendThongBao(pl, "Số vàng giao dịch không hợp lệ hoặc vượt quá số dư");
                            trade.cancelTrade(pl);
                            break;
                        }
                        trade.addItemTrade(pl, index, quantity);
                    }
                    break;
                case CANCEL_TRADE:
                    if (trade != null) {
                        trade.cancelTrade(pl);
                    }
                    break;
                case REMOVE_ITEM_TRADE:
                    if (trade != null) {
                        byte index = msg.reader().readByte();
                        trade.removeOfferItem(pl, index);
                    }
                    break;
                case LOCK_TRADE:
                    if (Maintenance.isRunning) {
                        if (trade != null) trade.cancelTrade(pl);
                        break;
                    }
                    if (trade != null) {
                        trade.lockTran(pl);
                    }
                    break;
                case ACCEPT:
                    if (Maintenance.isRunning) {
                        if (trade != null) trade.cancelTrade(pl);
                        break;
                    }
                    if (trade != null) {
                        trade.acceptTrade(pl);
                    }
                    break;
                default:
                    if (trade != null) {
                        trade.cancelFromSystem("UNSUPPORTED_ACTION_" + action);
                    }
                    break;
            }
        } catch (Exception e) {
            Logger.logException(this.getClass(), e);
            if (trade != null) {
                trade.cancelFromSystem("MALFORMED_PACKET");
            }
        }
    }

    /**
     * Mời giao dịch
     */
    private void sendInviteTrade(Player plInvite, Player plReceive) {
        if (plReceive.isBot) {
            ((Bot) plReceive).shop.activeTraDe(plInvite);
        }
        Message msg = null;
        try {
            msg = new Message(-86);
            msg.writer().writeByte(0);
            msg.writer().writeInt((int) plInvite.id);
            plReceive.sendMessage(msg);
        } catch (Exception ignored) {
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    /**
     * Hủy giao dịch
     */
    public void cancelTrade(Player player) {
        Trade trade = TradeRegistry.gI().getTrade(player);
        if (trade != null) {
            trade.cancelTrade(player);
        }
    }

    public boolean check(Player player) {
        return TradeRegistry.gI().hasActiveTrade(player);
    }

    static void processTimerTick(boolean maintenanceRunning) {
        List<Trade> trades = TradeRegistry.gI().getDistinctTradesSnapshot();
        for (Trade trade : trades) {
            try {
                if (maintenanceRunning) {
                    trade.cancelFromSystem("SERVER_MAINTENANCE");
                } else {
                    trade.update();
                }
            } catch (Exception e) {
                Logger.logException(TransactionService.class, e);
            }
        }
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                long st = System.currentTimeMillis();
                processTimerTick(Maintenance.isRunning);
                Functions.sleep(Math.max(300 - (System.currentTimeMillis() - st), 10));
            } catch (Exception e) {
                Logger.logException(TransactionService.class, e);
            }
        }
    }
}
