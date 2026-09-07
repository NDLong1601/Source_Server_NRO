package nro.models.ledger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import nro.models.data.LocalManager;
import nro.models.utils.Logger;

/**
 * SEC-05: Canonical JDBC implementation for the durable VND transaction ledger and delivery outbox.
 */
public class JdbcMoneyLedgerRepository implements MoneyLedgerRepository {

    @FunctionalInterface
    public interface ConnectionFactory { Connection open() throws SQLException; }
    private final ConnectionFactory connections;
    public JdbcMoneyLedgerRepository() { this(LocalManager::getConnection); }
    public JdbcMoneyLedgerRepository(ConnectionFactory connections) { this.connections = connections; }

    @Override
    public int readBalance(int accountId) {
        try (Connection con = connections.open();
             PreparedStatement ps = con.prepareStatement("SELECT vnd FROM account WHERE id = ?")) {
            ps.setInt(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Account missing");
                return rs.getInt(1);
            }
        } catch (SQLException ex) { throw new IllegalStateException("VND balance unavailable", ex); }
    }

    @Override
    public DebitResult recordDebitAndPendingOutbox(
        String purchaseKey,
        int accountId,
        long playerId,
        VndProductType productType,
        int amount,
        String payloadFingerprint,
        String frozenPayloadJson,
        String policyVersion
    ) {
        if (accountId <= 0 || playerId <= 0 || amount <= 0 || productType == null
            || (!productType.isVip() && productType != VndProductType.TRADE_GOLD && productType != VndProductType.TRADE_GEM)
            || (productType.isVip() ? amount != productType.getFixedCost()
                : amount < MoneyLedgerService.MIN_CONVERT_VND || amount > MoneyLedgerService.MAX_CONVERT_VND)) {
            return new DebitResult(DebitStatus.FAILED, 0, 0, purchaseKey, null);
        }
        OutboxRecord existing = findOutboxRecord(purchaseKey);
        if (existing != null) {
            if (existing.accountId() != accountId || existing.playerId() != playerId
                || !existing.payloadFingerprint().equals(payloadFingerprint)) {
                return new DebitResult(DebitStatus.CONFLICTING_PAYLOAD, 0, 0, purchaseKey, existing.status());
            }
            if ("DELIVERED".equalsIgnoreCase(existing.status())) {
                return new DebitResult(DebitStatus.ALREADY_COMMITTED, 0, 0, purchaseKey, existing.status());
            }
            return new DebitResult(DebitStatus.ALREADY_PENDING, 0, 0, purchaseKey, existing.status());
        }

        Connection con = null;
        boolean commitAttempted = false;
        try {
            con = connections.open();
            con.setAutoCommit(false);

            // 1. Lock and inspect account balance
            int balanceBefore;
            try (PreparedStatement psLock = con.prepareStatement("SELECT vnd,active FROM account WHERE id = ? FOR UPDATE")) {
                psLock.setInt(1, accountId);
                try (ResultSet rs = psLock.executeQuery()) {
                    if (!rs.next()) {
                        con.rollback();
                        return new DebitResult(DebitStatus.FAILED, 0, 0, purchaseKey, null);
                    }
                    balanceBefore = rs.getInt("vnd");
                    if (productType == VndProductType.TRADE_GOLD && !rs.getBoolean("active")) {
                        con.rollback(); return new DebitResult(DebitStatus.FAILED,0,0,purchaseKey,null);
                    }
                }
            }

            if (balanceBefore < 0 || balanceBefore < amount) {
                con.rollback();
                return new DebitResult(DebitStatus.INSUFFICIENT_BALANCE, balanceBefore, balanceBefore, purchaseKey, null);
            }

            int balanceAfter = balanceBefore - amount;

            // Accounts created after cutover get their opening baseline while
            // holding the same account lock as their first monetary movement.
            try (PreparedStatement baseline = con.prepareStatement("INSERT INTO vnd_transaction_ledger(purchase_key,account_id,player_id,transaction_type,amount,balance_before,balance_after,policy_version) SELECT ?,?,0,'BASELINE_OPENING',?,0,?,'SEC-05-BASELINE' WHERE NOT EXISTS (SELECT 1 FROM vnd_transaction_ledger WHERE account_id=?)")) {
                baseline.setString(1,"baseline_account_"+accountId);
                baseline.setInt(2,accountId);
                baseline.setInt(3,balanceBefore);
                baseline.setInt(4,balanceBefore);
                baseline.setInt(5,accountId);
                baseline.executeUpdate();
            }

            // Account lock serializes admission across independent connections.
            try (PreparedStatement owner = con.prepareStatement("SELECT data_vip FROM player WHERE id=? AND account_id=? FOR UPDATE")) {
                owner.setLong(1, playerId);
                owner.setInt(2, accountId);
                try (ResultSet row = owner.executeQuery()) {
                    if (!row.next()) { con.rollback(); return new DebitResult(DebitStatus.FAILED,0,0,purchaseKey,null); }
                    if (productType.isVip()) {
                        if (!MoneyLedgerService.isVipSeasonActive()) { con.rollback(); return new DebitResult(DebitStatus.FAILED,0,0,purchaseKey,null); }
                        int savedCount = 0;
                        Object raw = org.json.simple.JSONValue.parse(row.getString(1));
                        if (raw instanceof org.json.simple.JSONArray vip && vip.size() >= 8) savedCount = Integer.parseInt(vip.get(7).toString());
                        try (PreparedStatement count = con.prepareStatement("SELECT COUNT(*), COALESCE(SUM(status='PENDING_DELIVERY'),0) FROM vnd_delivery_outbox WHERE player_id=? AND product_type LIKE 'VIP%' AND created_at>=?")) {
                            count.setLong(1, playerId);
                            count.setTimestamp(2, Timestamp.valueOf(MoneyLedgerService.getVipSeasonStartDate()));
                            try (ResultSet counts = count.executeQuery()) {
                                counts.next();
                                if (Math.max(counts.getInt(1), savedCount + counts.getInt(2)) >= 4) {
                                    con.rollback(); return new DebitResult(DebitStatus.VIP_LIMIT_REACHED,0,0,purchaseKey,null);
                                }
                            }
                        }
                    }
                }
            }

            // 2. Conditional debit
            try (PreparedStatement psDebit = con.prepareStatement("UPDATE account SET vnd = ? WHERE id = ? AND vnd = ?")) {
                psDebit.setInt(1, balanceAfter);
                psDebit.setInt(2, accountId);
                psDebit.setInt(3, balanceBefore);
                int rows = psDebit.executeUpdate();
                if (rows != 1) {
                    con.rollback();
                    return new DebitResult(DebitStatus.FAILED, balanceBefore, balanceBefore, purchaseKey, null);
                }
            }

            // 3. Immutable ledger record
            try (PreparedStatement psLedger = con.prepareStatement(
                "INSERT INTO vnd_transaction_ledger (purchase_key, account_id, player_id, transaction_type, amount, balance_before, balance_after, policy_version, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())")) {
                psLedger.setString(1, purchaseKey);
                psLedger.setInt(2, accountId);
                psLedger.setLong(3, playerId);
                psLedger.setString(4, productType.getLedgerTransactionType());
                psLedger.setInt(5, amount);
                psLedger.setInt(6, balanceBefore);
                psLedger.setInt(7, balanceAfter);
                psLedger.setString(8, policyVersion);
                psLedger.executeUpdate();
            }

            // 4. Pending delivery outbox record
            try (PreparedStatement psOutbox = con.prepareStatement(
                "INSERT INTO vnd_delivery_outbox (purchase_key, account_id, player_id, product_type, amount, payload_fingerprint, frozen_entitlement_json, entitlement_version, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, 1, 'PENDING_DELIVERY', NOW())")) {
                psOutbox.setString(1, purchaseKey);
                psOutbox.setInt(2, accountId);
                psOutbox.setLong(3, playerId);
                psOutbox.setString(4, productType.name());
                psOutbox.setInt(5, amount);
                psOutbox.setString(6, payloadFingerprint);
                psOutbox.setString(7, frozenPayloadJson);
                psOutbox.executeUpdate();
            }

            commitAttempted = true;
            con.commit();
            return new DebitResult(DebitStatus.SUCCESS, balanceBefore, balanceAfter, purchaseKey, "PENDING_DELIVERY");
        } catch (SQLIntegrityConstraintViolationException dup) {
            safeRollback(con);
            OutboxRecord concurrentRecord = findOutboxRecord(purchaseKey);
            if (concurrentRecord != null) {
                if (concurrentRecord.accountId() != accountId || concurrentRecord.playerId() != playerId
                    || !concurrentRecord.payloadFingerprint().equals(payloadFingerprint)) {
                    return new DebitResult(DebitStatus.CONFLICTING_PAYLOAD, 0, 0, purchaseKey, concurrentRecord.status());
                }
                if ("DELIVERED".equalsIgnoreCase(concurrentRecord.status())) {
                    return new DebitResult(DebitStatus.ALREADY_COMMITTED, 0, 0, purchaseKey, concurrentRecord.status());
                }
                return new DebitResult(DebitStatus.ALREADY_PENDING, 0, 0, purchaseKey, concurrentRecord.status());
            }
            return new DebitResult(DebitStatus.FAILED, 0, 0, purchaseKey, null);
        } catch (Exception e) {
            safeRollback(con);
            Logger.logException(JdbcMoneyLedgerRepository.class, e, "[SEC-05] Error during recordDebitAndPendingOutbox key=" + purchaseKey);
            if (commitAttempted) {
                try {
                    OutboxRecord committed = findOutboxRecord(purchaseKey);
                    if (committed != null && committed.accountId() == accountId && committed.playerId() == playerId
                        && committed.payloadFingerprint().equals(payloadFingerprint)) {
                        return new DebitResult("DELIVERED".equals(committed.status()) ? DebitStatus.ALREADY_COMMITTED : DebitStatus.ALREADY_PENDING,0,0,purchaseKey,committed.status());
                    }
                } catch (RuntimeException unresolved) { /* Keep the purchase indeterminate. */ }
                return new DebitResult(DebitStatus.UNKNOWN,0,0,purchaseKey,null);
            }
            return new DebitResult(DebitStatus.FAILED, 0, 0, purchaseKey, null);
        } finally {
            safeClose(con);
        }
    }

    @Override
    public OutboxRecord findOutboxRecord(String purchaseKey) {
        String sql = "SELECT id, purchase_key, account_id, player_id, product_type, amount, payload_fingerprint, frozen_entitlement_json, status, error_code, retry_count, created_at FROM vnd_delivery_outbox WHERE purchase_key = ?";
        try (Connection con = connections.open();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, purchaseKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp ts = rs.getTimestamp("created_at");
                    long createdAt = ts != null ? ts.getTime() : 0L;
                    VndProductType pt;
                    try {
                        pt = VndProductType.valueOf(rs.getString("product_type"));
                    } catch (Exception ex) {
                        throw new IllegalStateException("Unknown persisted VND product", ex);
                    }
                    return new OutboxRecord(
                        rs.getLong("id"),
                        rs.getString("purchase_key"),
                        rs.getInt("account_id"),
                        rs.getLong("player_id"),
                        pt,
                        rs.getInt("amount"),
                        rs.getString("payload_fingerprint"),
                        rs.getString("frozen_entitlement_json"),
                        rs.getString("status"),
                        rs.getString("error_code"),
                        rs.getInt("retry_count"),
                        createdAt
                    );
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("VND outbox read failed", e);
        }
        return null;
    }

    @Override
    public List<OutboxRecord> findPendingDeliveriesForPlayer(long playerId) {
        List<OutboxRecord> list = new ArrayList<>();
        String sql = "SELECT id, purchase_key, account_id, player_id, product_type, amount, payload_fingerprint, frozen_entitlement_json, status, error_code, retry_count, created_at FROM vnd_delivery_outbox WHERE player_id = ? AND status = 'PENDING_DELIVERY' ORDER BY id ASC LIMIT 100";
        try (Connection con = connections.open();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("created_at");
                    long createdAt = ts != null ? ts.getTime() : 0L;
                    VndProductType pt;
                    try {
                        pt = VndProductType.valueOf(rs.getString("product_type"));
                    } catch (Exception ex) {
                        throw new IllegalStateException("Unknown persisted VND product", ex);
                    }
                    list.add(new OutboxRecord(
                        rs.getLong("id"),
                        rs.getString("purchase_key"),
                        rs.getInt("account_id"),
                        rs.getLong("player_id"),
                        pt,
                        rs.getInt("amount"),
                        rs.getString("payload_fingerprint"),
                        rs.getString("frozen_entitlement_json"),
                        rs.getString("status"),
                        rs.getString("error_code"),
                        rs.getInt("retry_count"),
                        createdAt
                    ));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Pending VND deliveries unavailable", e);
        }
        return list;
    }

    @Override
    public boolean markDeliveredWithPlayerState(
        String purchaseKey,
        long playerId,
        String itemsBagJson,
        String dataInventoryJson,
        String dataVipJson,
        String petJson,
        String dataTaskBadgesJson,
        int eventPoint,
        String dataEventJson
    ) {
        Connection con = null;
        try {
            con = connections.open();
            con.setAutoCommit(false);

            // Claim the entitlement before touching player state; a losing worker
            // must never publish its speculative snapshot.
            try (PreparedStatement claim = con.prepareStatement("SELECT player_id,status FROM vnd_delivery_outbox WHERE purchase_key=? FOR UPDATE")) {
                claim.setString(1, purchaseKey);
                try (ResultSet row = claim.executeQuery()) {
                    if (!row.next() || row.getLong(1) != playerId || !"PENDING_DELIVERY".equals(row.getString(2))) {
                        con.rollback(); return false;
                    }
                }
            }

            // 1. Update player table
            String sqlPlayer = "UPDATE player SET items_bag = ?, data_inventory = ?, data_vip = ?, pet = COALESCE(?,pet), dataTaskBadges = ?, event_point = ?, data_event = ? WHERE id = ?";
            try (PreparedStatement psPlayer = con.prepareStatement(sqlPlayer)) {
                psPlayer.setString(1, itemsBagJson);
                psPlayer.setString(2, dataInventoryJson);
                psPlayer.setString(3, dataVipJson);
                psPlayer.setString(4, petJson);
                psPlayer.setString(5, dataTaskBadgesJson);
                psPlayer.setInt(6, eventPoint);
                psPlayer.setString(7, dataEventJson);
                psPlayer.setLong(8, playerId);
                int pRows = psPlayer.executeUpdate();
                if (pRows != 1) {
                    con.rollback();
                    return false;
                }
            }

            // 2. Mark outbox entry as DELIVERED
            String sqlOutbox = "UPDATE vnd_delivery_outbox SET status = 'DELIVERED', delivered_at = NOW(), error_code = NULL WHERE purchase_key = ? AND status = 'PENDING_DELIVERY'";
            try (PreparedStatement psOutbox = con.prepareStatement(sqlOutbox)) {
                psOutbox.setString(1, purchaseKey);
                int oRows = psOutbox.executeUpdate();
                if (oRows != 1) {
                    con.rollback();
                    return false;
                }
            }

            con.commit();
            return true;
        } catch (Exception e) {
            safeRollback(con);
            Logger.logException(JdbcMoneyLedgerRepository.class, e, "[SEC-05] Error in markDeliveredWithPlayerState key=" + purchaseKey);
            return false;
        } finally {
            safeClose(con);
        }
    }

    @Override
    public void recordOutboxFailure(String purchaseKey, String errorCode) {
        String sql = "UPDATE vnd_delivery_outbox SET error_code = ?, retry_count = retry_count + 1 WHERE purchase_key = ?";
        try (Connection con = connections.open();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, errorCode);
            ps.setString(2, purchaseKey);
            ps.executeUpdate();
        } catch (Exception e) {
            Logger.logException(JdbcMoneyLedgerRepository.class, e, "[SEC-05] Error updating outbox failure key=" + purchaseKey);
        }
    }

    @Override
    public AccountAudit auditAccount(int accountId) {
        int currentVnd = 0;
        int baselineVnd = 0;
        long totalCredits = 0;
        long totalDebits = 0;
        int pendingDeliveries = 0;

        try (Connection con = connections.open()) {
            con.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            con.setAutoCommit(false);
            // Current VND
            try (PreparedStatement ps = con.prepareStatement("SELECT vnd FROM account WHERE id = ?")) {
                ps.setInt(1, accountId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        currentVnd = rs.getInt("vnd");
                    }
                }
            }

            // Baseline
            try (PreparedStatement ps = con.prepareStatement("SELECT amount FROM vnd_transaction_ledger WHERE account_id = ? AND transaction_type = 'BASELINE_OPENING'")) {
                ps.setInt(1, accountId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new SQLException("Opening baseline missing");
                    baselineVnd = rs.getInt("amount");
                }
            }

            // Credits
            try (PreparedStatement ps = con.prepareStatement("SELECT COALESCE(SUM(amount), 0) FROM vnd_transaction_ledger WHERE account_id = ? AND transaction_type LIKE 'CREDIT%'")) {
                ps.setInt(1, accountId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        totalCredits = rs.getLong(1);
                    }
                }
            }

            // Debits
            try (PreparedStatement ps = con.prepareStatement("SELECT COALESCE(SUM(amount), 0) FROM vnd_transaction_ledger WHERE account_id = ? AND transaction_type LIKE 'DEBIT%'")) {
                ps.setInt(1, accountId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        totalDebits = rs.getLong(1);
                    }
                }
            }

            // Pending deliveries
            try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM vnd_delivery_outbox WHERE account_id = ? AND status = 'PENDING_DELIVERY'")) {
                ps.setInt(1, accountId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        pendingDeliveries = rs.getInt(1);
                    }
                }
            }
            con.rollback(); // End the consistent read-only snapshot.
        } catch (Exception e) {
            throw new IllegalStateException("VND audit unavailable", e);
        }

        long expectedVnd = (long) baselineVnd + totalCredits - totalDebits;
        long drift = (long) currentVnd - expectedVnd;

        return new AccountAudit(accountId, currentVnd, baselineVnd, totalCredits, totalDebits, expectedVnd, drift, pendingDeliveries);
    }

    @Override
    public List<AccountAudit> auditAccountsWithActivity(int limit) {
        return auditAccountsAfter(0,limit);
    }

    public List<AccountAudit> auditAccountsAfter(int afterAccountId, int limit) {
        List<AccountAudit> audits = new ArrayList<>();
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("Audit batch must be 1..1000");
        String sql = "SELECT id AS account_id FROM account WHERE id>? ORDER BY id LIMIT ?";
        try (Connection con = connections.open();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, afterAccountId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int accId = rs.getInt("account_id");
                    if (accId > 0) {
                        audits.add(auditAccount(accId));
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("VND audit unavailable", e);
        }
        return audits;
    }

    private void safeRollback(Connection con) {
        if (con != null) {
            try {
                con.rollback();
            } catch (SQLException ignored) {}
        }
    }

    private void safeClose(Connection con) {
        if (con != null) {
            try {
                con.close();
            } catch (SQLException ignored) {}
        }
    }
}
