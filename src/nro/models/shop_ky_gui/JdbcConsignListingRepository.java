package nro.models.shop_ky_gui;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import nro.models.data.LocalManager;
import nro.models.item.Item;
import nro.models.player.InventoryPersistenceSnapshot;
import nro.models.utils.Logger;

/**
 * JDBC persistence for the consignment state machine. Every lifecycle change
 * and the corresponding player wallet/bag snapshot commit on one connection.
 */
public class JdbcConsignListingRepository implements ConsignListingRepository {

    private static final int MAX_SAFE_WIRE_ID = Short.MAX_VALUE;

    @FunctionalInterface
    interface ConnectionProvider {
        Connection get() throws SQLException;
    }

    @FunctionalInterface
    private interface TransactionWork {
        ConsignPersistenceResult execute(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    private interface CommitVerifier {
        ConsignPersistenceResult verify();
    }

    private final ConnectionProvider connections;

    public JdbcConsignListingRepository() {
        this(LocalManager::getConnection);
    }

    JdbcConsignListingRepository(ConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    @Override
    public ConsignItem findById(int id) {
        String sql = "SELECT `id`, `player_id`, `tab`, `item_id`, `gold`, `gem`, `quantity`, "
                + "`itemOption`, `isUpTop`, `isBuy`, `status`, `buyer_id`, `version`, `sold_at` "
                + "FROM `shop_ky_gui` WHERE `id` = ?";
        try (Connection con = connections.get();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            Logger.logException(JdbcConsignListingRepository.class, e);
            return null;
        }
    }

    @Override
    public List<ConsignItem> loadAllActiveAndSold() {
        List<ConsignItem> list = new ArrayList<>();
        String sql = "SELECT `id`, `player_id`, `tab`, `item_id`, `gold`, `gem`, `quantity`, "
                + "`itemOption`, `isUpTop`, `isBuy`, `status`, `buyer_id`, `version`, `sold_at` "
                + "FROM `shop_ky_gui` WHERE `status` IN ('ACTIVE', 'SOLD') "
                + "ORDER BY `isUpTop` DESC, `id` ASC";
        try (Connection con = connections.get();
             PreparedStatement ps = con.prepareStatement(sql);
            ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    ConsignItem item = mapRow(rs);
                    if (item != null) {
                        list.add(item);
                    }
                } catch (SQLException invalidRow) {
                    Logger.logException(JdbcConsignListingRepository.class, invalidRow);
                }
            }
        } catch (SQLException e) {
            Logger.logException(JdbcConsignListingRepository.class, e);
        }
        return list;
    }

    @Override
    public ConsignPersistenceResult insertListing(ConsignItem item,
                                                   InventoryPersistenceSnapshot sellerState) {
        AtomicInteger allocatedId = new AtomicInteger(-1);
        return executeTransaction(connection -> {
            int listingId = allocateListingId(connection);
            if (listingId < 1) {
                return ConsignPersistenceResult.of(ConsignPersistenceResult.Status.ID_EXHAUSTED);
            }
            insertListingRow(connection, item, listingId);
            persistPlayerState(connection, sellerState);
            allocatedId.set(listingId);
            return ConsignPersistenceResult.committed(listingId);
        }, () -> verifyCreatedListing(allocatedId.get(), item.player_sell, sellerState));
    }

    @Override
    public ConsignPersistenceResult transitionToClaimed(int listingId, int currentVersion,
                                                         InventoryPersistenceSnapshot sellerState) {
        String sql = "UPDATE `shop_ky_gui` SET `status` = 'CLAIMED', `version` = `version` + 1 "
                + "WHERE `id` = ? AND `status` = 'SOLD' AND `version` = ?";
        return transitionWithPlayerState(sql, listingId, currentVersion, "CLAIMED", 0L, sellerState);
    }

    @Override
    public ConsignPersistenceResult transitionToCancelled(int listingId, int currentVersion,
                                                           InventoryPersistenceSnapshot sellerState) {
        String sql = "UPDATE `shop_ky_gui` SET `status` = 'CANCELLED', `version` = `version` + 1 "
                + "WHERE `id` = ? AND `status` = 'ACTIVE' AND `version` = ?";
        return transitionWithPlayerState(sql, listingId, currentVersion, "CANCELLED", 0L, sellerState);
    }

    @Override
    public ConsignPersistenceResult updateUpTop(int listingId, int currentVersion, int isUpTop,
                                                InventoryPersistenceSnapshot sellerState) {
        String sql = "UPDATE `shop_ky_gui` SET `isUpTop` = ?, `version` = `version` + 1 "
                + "WHERE `id` = ? AND `status` = 'ACTIVE' AND `version` = ?";
        return executeTransaction(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, isUpTop);
                ps.setInt(2, listingId);
                ps.setInt(3, currentVersion);
                if (ps.executeUpdate() != 1) {
                    return ConsignPersistenceResult.of(ConsignPersistenceResult.Status.CONFLICT);
                }
            }
            persistPlayerState(connection, sellerState);
            return ConsignPersistenceResult.committed(listingId);
        }, () -> verifyUpTopCommit(listingId, currentVersion + 1, isUpTop, sellerState));
    }

    @Override
    public ConsignPersistenceResult executeAtomicPurchase(int listingId, int currentVersion, long buyerId,
                                                           Timestamp soldAt, long sellerId, byte priceType,
                                                           int price, short itemId, int quantity,
                                                           String optionsJson, long buyerGoldBefore,
                                                           long buyerGoldAfter, int buyerGemBefore,
                                                           int buyerGemAfter,
                                                           InventoryPersistenceSnapshot buyerState) {
        String updateSql = "UPDATE `shop_ky_gui` SET `status` = 'SOLD', `isBuy` = 1, `buyer_id` = ?, "
                + "`version` = `version` + 1, `sold_at` = ? "
                + "WHERE `id` = ? AND `status` = 'ACTIVE' AND `version` = ?";
        String ledgerSql = "INSERT INTO `consign_purchase_ledger` (`listing_id`, `seller_id`, `buyer_id`, "
                + "`price_type`, `price`, `item_id`, `quantity`, `item_options_json`, `status`, "
                + "`buyer_gold_before`, `buyer_gold_after`, `buyer_gem_before`, `buyer_gem_after`) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'SUCCESS', ?, ?, ?, ?)";

        return executeTransaction(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(updateSql)) {
                ps.setLong(1, buyerId);
                ps.setTimestamp(2, soldAt != null ? soldAt : new Timestamp(System.currentTimeMillis()));
                ps.setInt(3, listingId);
                ps.setInt(4, currentVersion);
                if (ps.executeUpdate() != 1) {
                    return ConsignPersistenceResult.of(ConsignPersistenceResult.Status.CONFLICT);
                }
            }
            persistPlayerState(connection, buyerState);
            try (PreparedStatement ps = connection.prepareStatement(ledgerSql)) {
                ps.setInt(1, listingId);
                ps.setLong(2, sellerId);
                ps.setLong(3, buyerId);
                ps.setByte(4, priceType);
                ps.setInt(5, price);
                ps.setInt(6, itemId);
                ps.setInt(7, quantity);
                ps.setString(8, optionsJson);
                ps.setLong(9, buyerGoldBefore);
                ps.setLong(10, buyerGoldAfter);
                ps.setInt(11, buyerGemBefore);
                ps.setInt(12, buyerGemAfter);
                if (ps.executeUpdate() != 1) {
                    throw new SQLException("Purchase ledger insert affected an unexpected row count");
                }
            }
            return ConsignPersistenceResult.committed(listingId);
        }, () -> verifyPurchaseCommit(listingId, currentVersion + 1, buyerId, buyerState));
    }

    private ConsignPersistenceResult transitionWithPlayerState(String sql, int listingId, int currentVersion,
                                                                String expectedStatus, long expectedBuyerId,
                                                                InventoryPersistenceSnapshot playerState) {
        return executeTransaction(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, listingId);
                ps.setInt(2, currentVersion);
                if (ps.executeUpdate() != 1) {
                    return ConsignPersistenceResult.of(ConsignPersistenceResult.Status.CONFLICT);
                }
            }
            persistPlayerState(connection, playerState);
            return ConsignPersistenceResult.committed(listingId);
        }, () -> verifyLifecycleCommit(listingId, currentVersion + 1, expectedStatus,
                expectedBuyerId, playerState));
    }

    private ConsignPersistenceResult executeTransaction(TransactionWork work, CommitVerifier verifier) {
        Connection connection = null;
        boolean commitAttempted = false;
        try {
            connection = connections.get();
            connection.setAutoCommit(false);
            ConsignPersistenceResult result = work.execute(connection);
            if (!result.isCommitted()) {
                connection.rollback();
                return result;
            }
            commitAttempted = true;
            connection.commit();
            return result;
        } catch (SQLException e) {
            Logger.logException(JdbcConsignListingRepository.class, e);
            rollbackQuietly(connection);
            if (commitAttempted) {
                closeQuietly(connection);
                connection = null;
                return verifier.verify();
            }
            if (e.getSQLState() != null && e.getSQLState().startsWith("23")) {
                return ConsignPersistenceResult.of(ConsignPersistenceResult.Status.CONFLICT);
            }
            return ConsignPersistenceResult.of(ConsignPersistenceResult.Status.FAILED);
        } finally {
            closeQuietly(connection);
        }
    }

    private int allocateListingId(Connection connection) throws SQLException {
        long nextId;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT `next_id` FROM `consign_listing_sequence` WHERE `singleton_id` = 1 FOR UPDATE");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new SQLException("Consignment ID sequence is not initialized");
            }
            nextId = rs.getLong(1);
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COALESCE(MAX(`id`), 0) FROM `shop_ky_gui`");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new SQLException("Could not inspect current consignment IDs");
            }
            nextId = Math.max(nextId, rs.getLong(1) + 1L);
        }
        if (nextId < 1 || nextId > MAX_SAFE_WIRE_ID) {
            return -1;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE `consign_listing_sequence` SET `next_id` = ? WHERE `singleton_id` = 1")) {
            ps.setLong(1, nextId + 1L);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Could not advance consignment ID sequence");
            }
        }
        return (int) nextId;
    }

    private void insertListingRow(Connection connection, ConsignItem item, int listingId) throws SQLException {
        String sql = "INSERT INTO `shop_ky_gui` (`id`, `player_id`, `tab`, `item_id`, `gold`, `gem`, "
                + "`quantity`, `itemOption`, `isUpTop`, `isBuy`, `status`, `buyer_id`, `version`) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, listingId);
            ps.setInt(2, item.player_sell);
            ps.setByte(3, item.tab);
            ps.setShort(4, item.itemId);
            ps.setInt(5, item.goldSell);
            ps.setInt(6, item.gemSell);
            ps.setInt(7, item.quantity);
            ps.setString(8, ConsignItemOptionsCodec.encode(item.options));
            ps.setInt(9, item.isUpTop);
            ps.setInt(10, item.isBuy ? 1 : 0);
            ps.setString(11, item.getStatus().name());
            ps.setInt(12, item.getVersion());
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Listing insert affected an unexpected row count");
            }
        }
    }

    private void persistPlayerState(Connection connection, InventoryPersistenceSnapshot state) throws SQLException {
        if (state == null) {
            throw new SQLException("Missing player inventory snapshot");
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE `player` SET `data_inventory` = ?, `items_bag` = ? WHERE `id` = ?")) {
            ps.setString(1, state.getDataInventoryJson());
            ps.setString(2, state.getItemsBagJson());
            ps.setLong(3, state.getPlayerId());
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Player inventory persistence affected an unexpected row count");
            }
        }
    }

    private ConsignPersistenceResult verifyPurchaseCommit(int listingId, int expectedVersion, long buyerId,
                                                           InventoryPersistenceSnapshot state) {
        String sql = "SELECT s.`status`, s.`version`, s.`buyer_id`, p.`data_inventory`, p.`items_bag`, "
                + "l.`listing_id` FROM `shop_ky_gui` s JOIN `player` p ON p.`id` = ? "
                + "JOIN `consign_purchase_ledger` l ON l.`listing_id` = s.`id` WHERE s.`id` = ?";
        try (Connection con = connections.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, state.getPlayerId());
            ps.setInt(2, listingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()
                        && "SOLD".equalsIgnoreCase(rs.getString(1))
                        && rs.getInt(2) == expectedVersion
                        && rs.getLong(3) == buyerId
                        && playerStateMatches(rs.getString(4), rs.getString(5), state)) {
                    return ConsignPersistenceResult.committed(listingId);
                }
                return ConsignPersistenceResult.of(ConsignPersistenceResult.Status.UNKNOWN);
            }
        } catch (SQLException e) {
            Logger.logException(JdbcConsignListingRepository.class, e);
            return ConsignPersistenceResult.of(ConsignPersistenceResult.Status.UNKNOWN);
        }
    }

    private ConsignPersistenceResult verifyLifecycleCommit(int listingId, int expectedVersion,
                                                            String expectedStatus, long expectedBuyerId,
                                                            InventoryPersistenceSnapshot state) {
        String sql = "SELECT s.`status`, s.`version`, s.`buyer_id`, p.`data_inventory`, p.`items_bag` "
                + "FROM `shop_ky_gui` s JOIN `player` p ON p.`id` = ? WHERE s.`id` = ?";
        try (Connection con = connections.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, state.getPlayerId());
            ps.setInt(2, listingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()
                        && expectedStatus.equalsIgnoreCase(rs.getString(1))
                        && rs.getInt(2) == expectedVersion
                        && (expectedBuyerId == 0L || rs.getLong(3) == expectedBuyerId)
                        && playerStateMatches(rs.getString(4), rs.getString(5), state)) {
                    return ConsignPersistenceResult.committed(listingId);
                }
                return ConsignPersistenceResult.of(ConsignPersistenceResult.Status.UNKNOWN);
            }
        } catch (SQLException e) {
            Logger.logException(JdbcConsignListingRepository.class, e);
            return ConsignPersistenceResult.of(ConsignPersistenceResult.Status.UNKNOWN);
        }
    }

    private ConsignPersistenceResult verifyCreatedListing(int listingId, long sellerId,
                                                           InventoryPersistenceSnapshot state) {
        if (listingId < 1) {
            return ConsignPersistenceResult.of(ConsignPersistenceResult.Status.UNKNOWN);
        }
        String sql = "SELECT s.`status`, s.`version`, s.`player_id`, p.`data_inventory`, p.`items_bag` "
                + "FROM `shop_ky_gui` s JOIN `player` p ON p.`id` = ? WHERE s.`id` = ?";
        try (Connection con = connections.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, state.getPlayerId());
            ps.setInt(2, listingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()
                        && "ACTIVE".equalsIgnoreCase(rs.getString(1))
                        && rs.getInt(2) == 1
                        && rs.getLong(3) == sellerId
                        && playerStateMatches(rs.getString(4), rs.getString(5), state)) {
                    return ConsignPersistenceResult.committed(listingId);
                }
                return ConsignPersistenceResult.of(ConsignPersistenceResult.Status.UNKNOWN);
            }
        } catch (SQLException e) {
            Logger.logException(JdbcConsignListingRepository.class, e);
            return ConsignPersistenceResult.of(ConsignPersistenceResult.Status.UNKNOWN);
        }
    }

    private ConsignPersistenceResult verifyUpTopCommit(int listingId, int expectedVersion, int isUpTop,
                                                        InventoryPersistenceSnapshot state) {
        String sql = "SELECT s.`status`, s.`version`, s.`isUpTop`, p.`data_inventory`, p.`items_bag` "
                + "FROM `shop_ky_gui` s JOIN `player` p ON p.`id` = ? WHERE s.`id` = ?";
        try (Connection con = connections.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, state.getPlayerId());
            ps.setInt(2, listingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()
                        && "ACTIVE".equalsIgnoreCase(rs.getString(1))
                        && rs.getInt(2) == expectedVersion
                        && rs.getInt(3) == isUpTop
                        && playerStateMatches(rs.getString(4), rs.getString(5), state)) {
                    return ConsignPersistenceResult.committed(listingId);
                }
                return ConsignPersistenceResult.of(ConsignPersistenceResult.Status.UNKNOWN);
            }
        } catch (SQLException e) {
            Logger.logException(JdbcConsignListingRepository.class, e);
            return ConsignPersistenceResult.of(ConsignPersistenceResult.Status.UNKNOWN);
        }
    }

    private boolean playerStateMatches(String inventoryJson, String bagJson,
                                       InventoryPersistenceSnapshot expected) {
        return Objects.equals(inventoryJson, expected.getDataInventoryJson())
                && Objects.equals(bagJson, expected.getItemsBagJson());
    }

    private void rollbackQuietly(Connection connection) {
        if (connection != null) {
            try {
                connection.rollback();
            } catch (SQLException rollbackError) {
                Logger.logException(JdbcConsignListingRepository.class, rollbackError);
            }
        }
    }

    private void closeQuietly(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException closeError) {
                Logger.logException(JdbcConsignListingRepository.class, closeError);
            }
        }
    }

    private ConsignItem mapRow(ResultSet rs) throws SQLException {
        ConsignListingStatus status = ConsignListingStatus.fromString(rs.getString("status"));
        if (status == ConsignListingStatus.ACTIVE && rs.getBoolean("isBuy")) {
            status = ConsignListingStatus.SOLD;
        }
        return new ConsignItem(
                rs.getInt("id"),
                rs.getShort("item_id"),
                rs.getInt("player_id"),
                rs.getByte("tab"),
                rs.getInt("gold"),
                rs.getInt("gem"),
                rs.getInt("quantity"),
                rs.getByte("isUpTop"),
                parseItemOptions(rs.getString("itemOption")),
                status,
                rs.getLong("buyer_id"),
                Math.max(1, rs.getInt("version")),
                rs.getTimestamp("sold_at"));
    }

    private List<Item.ItemOption> parseItemOptions(String json) throws SQLException {
        try {
            return ConsignItemOptionsCodec.decode(json);
        } catch (RuntimeException e) {
            throw new SQLException("Invalid consignment itemOption JSON", e);
        }
    }
}
