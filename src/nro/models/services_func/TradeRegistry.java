package nro.models.services_func;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import nro.models.player.Player;

/**
 * SEC-03: Thread-safe atomic registry for active Trade sessions.
 * Guarantees each player participates in at most one active trade,
 * conditional unregistration by trade identity, and single-visit timer traversal.
 */
public class TradeRegistry {

    private static final TradeRegistry INSTANCE = new TradeRegistry();

    public static TradeRegistry gI() {
        return INSTANCE;
    }

    private final Map<Long, Trade> activeTradesByPlayerId = new ConcurrentHashMap<>();
    private final Map<String, Trade> distinctTrades = new ConcurrentHashMap<>();
    private final Object registryLock = new Object();

    TradeRegistry() {
    }

    /**
     * Atomically registers both participants for a new Trade session.
     * Rejects self-trade and participants with an already active trade.
     *
     * @param trade Trade session to register
     * @return true if successfully registered, false otherwise
     */
    public boolean register(Trade trade) {
        if (trade == null || trade.getPlayer1() == null || trade.getPlayer2() == null) {
            return false;
        }
        long p1Id = trade.getPlayer1().id;
        long p2Id = trade.getPlayer2().id;

        if (p1Id == p2Id) {
            return false; // Self-trade forbidden
        }

        synchronized (registryLock) {
            if (activeTradesByPlayerId.containsKey(p1Id) || activeTradesByPlayerId.containsKey(p2Id)) {
                return false;
            }
            activeTradesByPlayerId.put(p1Id, trade);
            activeTradesByPlayerId.put(p2Id, trade);
            distinctTrades.put(trade.getTradeId(), trade);
            return true;
        }
    }

    /**
     * Looks up the active Trade for a player.
     */
    public Trade getTrade(Player player) {
        if (player == null) {
            return null;
        }
        return activeTradesByPlayerId.get(player.id);
    }

    public Trade getTrade(long playerId) {
        return activeTradesByPlayerId.get(playerId);
    }

    /**
     * Checks if a player is currently in an active trade.
     */
    public boolean hasActiveTrade(Player player) {
        if (player == null) {
            return false;
        }
        return activeTradesByPlayerId.containsKey(player.id);
    }

    /**
     * Atomically unregisters a Trade session if and only if the entries
     * currently point to this exact trade instance.
     */
    public boolean unregister(Trade trade) {
        if (trade == null) {
            return false;
        }
        long p1Id = trade.getPlayer1() != null ? trade.getPlayer1().id : -1;
        long p2Id = trade.getPlayer2() != null ? trade.getPlayer2().id : -1;
        String tradeId = trade.getTradeId();

        synchronized (registryLock) {
            boolean removed = false;
            if (p1Id != -1 && activeTradesByPlayerId.get(p1Id) == trade) {
                activeTradesByPlayerId.remove(p1Id);
                removed = true;
            }
            if (p2Id != -1 && activeTradesByPlayerId.get(p2Id) == trade) {
                activeTradesByPlayerId.remove(p2Id);
                removed = true;
            }
            if (tradeId != null) {
                distinctTrades.remove(tradeId, trade);
            }
            return removed;
        }
    }

    /**
     * Returns a snapshot of distinct active trades for timer processing.
     * Each trade is represented exactly once.
     */
    public List<Trade> getDistinctTradesSnapshot() {
        return new ArrayList<>(distinctTrades.values());
    }

    public int activeTradeCount() {
        return distinctTrades.size();
    }

    public void clearForTest() {
        synchronized (registryLock) {
            activeTradesByPlayerId.clear();
            distinctTrades.clear();
        }
    }
}
