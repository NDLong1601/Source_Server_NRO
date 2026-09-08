package nro.models.ledger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import nro.models.data.LocalManager;
import nro.models.utils.Logger;

/**
 * SEC-05/SEC-07 JDBC implementation for the durable VND transaction ledger,
 * delivery outbox, and read-only reconciliation snapshot.
 */
public class JdbcMoneyLedgerRepository implements MoneyLedgerRepository, VndReconciliationDataSource {

    private static final int MOVEMENT_PAGE_SIZE = 1_000;
    private static final long MAX_MOVEMENTS_PER_OWNER_RUN = 1_000_000L;
    // Shared budget keeps a large owner from consuming an unbounded batch.
    private static final long MAX_MOVEMENTS_PER_RUN = 10_000_000L;

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
                : amount < MoneyLedgerService.MIN_CONVERT_VND || amount > MoneyLedgerService.MAX_CONVERT_VND)
            || !isSafeOperationKey(purchaseKey) || !isSha256Fingerprint(payloadFingerprint)
            || frozenPayloadJson == null || frozenPayloadJson.length() > 16_000_000) {
            return new DebitResult(DebitStatus.FAILED, 0, 0, purchaseKey, null);
        }
        OutboxRecord existing = findOutboxRecord(purchaseKey);
        if (existing != null) {
            if (existing.accountId() != accountId || existing.playerId() != playerId
                || !java.util.Objects.equals(existing.payloadFingerprint(), payloadFingerprint)) {
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

            // A baseline must be captured at an approved cutover. Deriving it
            // from today's balance here would hide legacy drift.
            try (PreparedStatement baseline = timed(con,
                    "SELECT COUNT(*) FROM vnd_transaction_ledger "
                    + "WHERE account_id=? AND transaction_type='BASELINE_OPENING' "
                    + "AND owner_type='ACCOUNT' AND currency='VND' "
                    + "AND schema_version='SEC-07-BASELINE' "
                    + "AND operation_id<>'' AND business_key<>'' AND leg_index=0 "
                    + "AND amount>=0 AND balance_before=0 AND balance_after=amount "
                    + "AND requested_amount=amount AND applied_delta=amount "
                    + "AND durable_version>0 AND `source` IS NOT NULL AND committed_at IS NOT NULL", 30)) {
                baseline.setInt(1, accountId);
                try (ResultSet baselineRow = baseline.executeQuery()) {
                    if (!baselineRow.next() || baselineRow.getLong(1) != 1L) {
                        con.rollback();
                        return new DebitResult(DebitStatus.BASELINE_MISSING, balanceBefore,
                                balanceBefore, purchaseKey, null);
                    }
                }
            }

            if (balanceBefore < 0 || balanceBefore < amount) {
                con.rollback();
                return new DebitResult(DebitStatus.INSUFFICIENT_BALANCE, balanceBefore, balanceBefore, purchaseKey, null);
            }

            int balanceAfter = balanceBefore - amount;

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
            long ledgerId;
            try (PreparedStatement psLedger = con.prepareStatement(
                "INSERT INTO vnd_transaction_ledger (purchase_key, account_id, player_id, transaction_type, amount, "
                + "balance_before, balance_after, policy_version, created_at, operation_id, business_key, owner_type, "
                + "currency, requested_amount, applied_delta, leg_index, durable_version, `source`, payload_fingerprint, "
                + "committed_at, schema_version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), ?, ?, 'ACCOUNT', 'VND', ?, ?, 0, NULL, ?, ?, NOW(), 'SEC-07-V1')",
                Statement.RETURN_GENERATED_KEYS)) {
                psLedger.setString(1, purchaseKey);
                psLedger.setInt(2, accountId);
                psLedger.setLong(3, playerId);
                psLedger.setString(4, productType.getLedgerTransactionType());
                psLedger.setInt(5, amount);
                psLedger.setInt(6, balanceBefore);
                psLedger.setInt(7, balanceAfter);
                psLedger.setString(8, policyVersion);
                psLedger.setString(9, purchaseKey);
                psLedger.setString(10, purchaseKey);
                psLedger.setInt(11, amount);
                psLedger.setInt(12, -amount);
                psLedger.setString(13, productType.getLedgerTransactionType());
                psLedger.setString(14, payloadFingerprint);
                if (psLedger.executeUpdate() != 1) {
                    throw new SQLException("VND ledger insert affected an unexpected row count");
                }
                try (ResultSet keys = psLedger.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("VND ledger insert did not return a durable sequence");
                    }
                    ledgerId = keys.getLong(1);
                }
            }
            try (PreparedStatement sequence = con.prepareStatement(
                    "UPDATE vnd_transaction_ledger SET durable_version=? WHERE id=?")) {
                sequence.setLong(1, ledgerId);
                sequence.setLong(2, ledgerId);
                if (sequence.executeUpdate() != 1) {
                    throw new SQLException("VND ledger durable sequence update affected an unexpected row count");
                }
            }

            // 4. Pending delivery outbox record
            try (PreparedStatement psOutbox = con.prepareStatement(
                "INSERT INTO vnd_delivery_outbox (purchase_key, account_id, player_id, product_type, amount, "
                + "payload_fingerprint, frozen_entitlement_json, entitlement_version, status, created_at, "
                + "operation_id, business_key, owner_type, currency, requested_amount, schema_version) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, 1, 'PENDING_DELIVERY', NOW(), ?, ?, 'ACCOUNT', 'VND', ?, 'SEC-07-V1')")) {
                psOutbox.setString(1, purchaseKey);
                psOutbox.setInt(2, accountId);
                psOutbox.setLong(3, playerId);
                psOutbox.setString(4, productType.name());
                psOutbox.setInt(5, amount);
                psOutbox.setString(6, payloadFingerprint);
                psOutbox.setString(7, frozenPayloadJson);
                psOutbox.setString(8, purchaseKey);
                psOutbox.setString(9, purchaseKey);
                psOutbox.setInt(10, amount);
                if (psOutbox.executeUpdate() != 1) {
                    throw new SQLException("VND outbox insert affected an unexpected row count");
                }
            }

            commitAttempted = true;
            con.commit();
            return new DebitResult(DebitStatus.SUCCESS, balanceBefore, balanceAfter, purchaseKey, "PENDING_DELIVERY");
        } catch (SQLIntegrityConstraintViolationException dup) {
            safeRollback(con);
            OutboxRecord concurrentRecord = findOutboxRecord(purchaseKey);
            if (concurrentRecord != null) {
                if (concurrentRecord.accountId() != accountId || concurrentRecord.playerId() != playerId
                    || !java.util.Objects.equals(concurrentRecord.payloadFingerprint(), payloadFingerprint)) {
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
                        && java.util.Objects.equals(committed.payloadFingerprint(), payloadFingerprint)) {
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

    /**
     * Reads the VND ledger, outbox, and account balance from one
     * REPEATABLE_READ connection. This method is deliberately read-only and
     * bounded; it never creates a baseline or changes a finding.
     */
    @Override
    public ReconciliationSnapshot readVndSnapshot(VndReconciliationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("SEC-07 request is required");
        }
        try (Connection con = connections.open()) {
            con.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            con.setAutoCommit(false);
            ensureSec07Columns(con, request.queryTimeoutSeconds());

            long cutoffLedgerId;
            long cutoffAtMillis;
            try (PreparedStatement ps = timed(con,
                    "SELECT COALESCE(MAX(id),0), CURRENT_TIMESTAMP FROM vnd_transaction_ledger",
                    request.queryTimeoutSeconds());
                    ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("SEC-07 cutoff query returned no row");
                }
                cutoffLedgerId = rs.getLong(1);
                Timestamp cutoff = rs.getTimestamp(2);
                cutoffAtMillis = cutoff == null ? System.currentTimeMillis() : cutoff.getTime();
            }

            List<Long> ownerIds = new ArrayList<>();
            boolean hasMoreOwners = false;
            if (request.ownerId() > 0L) {
                try (PreparedStatement ps = timed(con,
                        "SELECT id FROM account WHERE id=?", request.queryTimeoutSeconds())) {
                    ps.setLong(1, request.ownerId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            ownerIds.add(rs.getLong(1));
                        }
                    }
                }
                if (ownerIds.isEmpty()) {
                    ownerIds.add(request.ownerId());
                }
            } else {
                try (PreparedStatement ps = timed(con,
                        "SELECT id FROM account WHERE id>? ORDER BY id LIMIT ?",
                        request.queryTimeoutSeconds())) {
                    ps.setLong(1, request.afterOwnerId());
                    ps.setInt(2, request.ownerBatchSize() + 1);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            ownerIds.add(rs.getLong(1));
                        }
                    }
                }
                if (ownerIds.size() > request.ownerBatchSize()) {
                    hasMoreOwners = true;
                    ownerIds = new ArrayList<>(ownerIds.subList(0, request.ownerBatchSize()));
                }
            }

            List<OwnerSnapshot> owners = new ArrayList<>();
            List<Long> unresolvedOwnerIds = new ArrayList<>();
            MovementWorkBudget workBudget = new MovementWorkBudget(request.fullOwnerScan()
                    ? Long.MAX_VALUE : MAX_MOVEMENTS_PER_RUN,
                    request.fullOwnerScan() ? Long.MAX_VALUE : MAX_MOVEMENTS_PER_OWNER_RUN,
                    request.runTimeoutSeconds());
            long nextCursor = request.afterOwnerId();
            for (int ownerIndex = 0; ownerIndex < ownerIds.size(); ownerIndex++) {
                if (!workBudget.hasRemaining()) {
                    hasMoreOwners = true;
                    break;
                }
                Long ownerId = ownerIds.get(ownerIndex);
                if (ownerId == null || ownerId <= 0L) {
                    continue;
                }
                OwnerSnapshot owner = readOwnerSnapshot(con, ownerId, cutoffLedgerId, cutoffAtMillis,
                        request.queryTimeoutSeconds(), request.stalePendingAfterMillis(), workBudget);
                owners.add(owner);
                if (owner.unavailableCode() != null || !owner.coverageComplete()) {
                    unresolvedOwnerIds.add(ownerId);
                }
                nextCursor = Math.max(nextCursor, ownerId);
            }
            // The main owner cursor always progresses. Unresolved owners are
            // explicit in the report and are retried with --account-id so a
            // large/broken owner cannot starve later owners forever.
            con.rollback();
            boolean coverageComplete = !hasMoreOwners && unresolvedOwnerIds.isEmpty()
                    && owners.stream().allMatch(owner -> owner.unavailableCode() == null && owner.coverageComplete());
            return new ReconciliationSnapshot(cutoffLedgerId, cutoffAtMillis, owners,
                    nextCursor, hasMoreOwners, coverageComplete, unresolvedOwnerIds);
        } catch (SQLException ex) {
            throw new IllegalStateException("SEC-07 VND snapshot unavailable", ex);
        }
    }

    private OwnerSnapshot readOwnerSnapshot(Connection con, long ownerId, long cutoffLedgerId,
            long cutoffAtMillis, int timeoutSeconds, long stalePendingAfterMillis,
            MovementWorkBudget workBudget) {
        try {
            long persistedBalance;
            try (PreparedStatement ps = timed(con, "SELECT vnd FROM account WHERE id=?",
                    workBudget.queryTimeoutSeconds(timeoutSeconds))) {
                ps.setLong(1, ownerId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return OwnerSnapshot.unavailable(ownerId, "OWNER_NOT_FOUND");
                    }
                    if (rs.getObject(1) == null) {
                        return OwnerSnapshot.unavailable(ownerId, "OWNER_BALANCE_NULL");
                    }
                    persistedBalance = rs.getLong(1);
                }
            }

            VndReconciliationCalculator.StreamingAccumulator audit =
                    new VndReconciliationCalculator.StreamingAccumulator(ownerId, persistedBalance,
                            System.currentTimeMillis(), stalePendingAfterMillis);
            markDuplicateKeys(con, ownerId, cutoffLedgerId, cutoffAtMillis, timeoutSeconds, audit, workBudget);
            boolean ledgerComplete = readLedgerPages(con, ownerId, cutoffLedgerId, timeoutSeconds, audit, workBudget);
            boolean complete = ledgerComplete;
            if (complete) {
                complete = readOutboxPages(con, ownerId, cutoffAtMillis, cutoffLedgerId,
                        timeoutSeconds, audit, workBudget);
            }
            if (!complete) {
                audit.markWorkLimitReached(ledgerComplete);
            }
            return new OwnerSnapshot(ownerId, persistedBalance, List.of(), List.of(), complete, null,
                    audit.finish());
        } catch (SQLException ex) {
            // Keep this owner visible as UNAVAILABLE and let the batch continue.
            return OwnerSnapshot.unavailable(ownerId, "OWNER_QUERY_FAILED");
        }
    }

    private boolean readLedgerPages(Connection con, long ownerId, long cutoffLedgerId,
            int timeoutSeconds, VndReconciliationCalculator.StreamingAccumulator audit,
            MovementWorkBudget workBudget) throws SQLException {
        final String sequence = "CASE WHEN l.durable_version IS NULL OR l.durable_version<=0 "
                + "THEN l.id ELSE l.durable_version END";
        String sql = "SELECT l.id, l.account_id, l.owner_type, l.currency, l.operation_id, l.business_key, "
                + "l.transaction_type, l.amount, l.requested_amount, l.applied_delta, l.balance_before, "
                + "l.balance_after, l.leg_index, l.durable_version, l.payload_fingerprint, l.schema_version, "
                + "l.`source`, l.committed_at, o.id AS o_id, o.account_id AS o_account_id, "
                + "o.owner_type AS o_owner_type, o.currency AS o_currency, o.operation_id AS o_operation_id, "
                + "o.business_key AS o_business_key, o.product_type AS o_product_type, o.amount AS o_amount, "
                + "o.payload_fingerprint AS o_payload_fingerprint, o.status AS o_status, "
                + "o.retry_count AS o_retry_count, o.created_at AS o_created_at, o.delivered_at AS o_delivered_at "
                + "FROM vnd_transaction_ledger l LEFT JOIN vnd_delivery_outbox o "
                + "ON o.account_id=l.account_id AND o.operation_id=l.operation_id "
                + "WHERE l.account_id=? AND l.id<=? AND (" + sequence + ">? OR (" + sequence
                + "=? AND l.id>?)) ORDER BY " + sequence + ", l.id LIMIT ?";
        long afterSequence = 0L;
        long afterRowId = 0L;
        long processed = 0L;
        while (processed < workBudget.ownerLimit && workBudget.hasRemaining()) {
            int pageSize = (int) Math.min(MOVEMENT_PAGE_SIZE,
                    Math.min(workBudget.ownerLimit - processed, workBudget.remaining()));
            int rows = 0;
            long pageLastSequence = afterSequence;
            long pageLastRowId = afterRowId;
            try (PreparedStatement ps = timed(con, sql, workBudget.queryTimeoutSeconds(timeoutSeconds))) {
                ps.setLong(1, ownerId);
                ps.setLong(2, cutoffLedgerId);
                ps.setLong(3, afterSequence);
                ps.setLong(4, afterSequence);
                ps.setLong(5, afterRowId);
                ps.setInt(6, pageSize);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        LedgerMovement movement = readLedgerMovement(rs);
                        OutboxMovement matchingOutbox = rs.getObject("o_id") == null
                                ? null : readJoinedOutbox(rs);
                        if (!workBudget.consume()) {
                            return false;
                        }
                        audit.acceptLedger(movement, matchingOutbox);
                        rows++;
                        processed++;
                        pageLastSequence = movement.durableSequence();
                        pageLastRowId = movement.rowId();
                    }
                }
            }
            if (rows < pageSize) return true;
            afterSequence = pageLastSequence;
            afterRowId = pageLastRowId;
        }
        return false;
    }

    private boolean readOutboxPages(Connection con, long ownerId, long cutoffAtMillis,
            long cutoffLedgerId, int timeoutSeconds,
            VndReconciliationCalculator.StreamingAccumulator audit,
            MovementWorkBudget workBudget) throws SQLException {
        String sql = "SELECT o.id, o.account_id, o.owner_type, o.currency, o.operation_id, o.business_key, "
                + "o.product_type, o.amount, o.payload_fingerprint, o.status, o.retry_count, "
                + "o.created_at, o.delivered_at, l.id AS linked_ledger_id "
                + "FROM vnd_delivery_outbox o LEFT JOIN vnd_transaction_ledger l "
                + "ON l.account_id=o.account_id AND l.operation_id=o.operation_id AND l.id<=? "
                + "WHERE o.account_id=? AND o.created_at<=? AND o.id>? ORDER BY o.id ASC LIMIT ?";
        long afterId = 0L;
        long processed = 0L;
        while (processed < workBudget.ownerLimit && workBudget.hasRemaining()) {
            int pageSize = (int) Math.min(MOVEMENT_PAGE_SIZE,
                    Math.min(workBudget.ownerLimit - processed, workBudget.remaining()));
            int rows = 0;
            long pageLastId = afterId;
            try (PreparedStatement ps = timed(con, sql, workBudget.queryTimeoutSeconds(timeoutSeconds))) {
                ps.setLong(1, cutoffLedgerId);
                ps.setLong(2, ownerId);
                ps.setTimestamp(3, new Timestamp(cutoffAtMillis));
                ps.setLong(4, afterId);
                ps.setInt(5, pageSize);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        OutboxMovement outbox = readOutboxMovement(rs);
                        if (!workBudget.consume()) {
                            return false;
                        }
                        audit.acceptOutbox(outbox, rs.getObject("linked_ledger_id") != null);
                        rows++;
                        processed++;
                        pageLastId = outbox.rowId();
                    }
                }
            }
            if (rows < pageSize) return true;
            afterId = pageLastId;
        }
        return false;
    }

    private void markDuplicateKeys(Connection con, long ownerId, long cutoffLedgerId,
            long cutoffAtMillis, int timeoutSeconds,
            VndReconciliationCalculator.StreamingAccumulator audit, MovementWorkBudget workBudget) throws SQLException {
        String ledger = "SELECT COUNT(*) FROM (SELECT operation_id, leg_index FROM vnd_transaction_ledger "
                + "WHERE account_id=? AND id<=? GROUP BY operation_id,leg_index HAVING COUNT(*)>1 LIMIT 1) d";
        try (PreparedStatement ps = timed(con, ledger, workBudget.queryTimeoutSeconds(timeoutSeconds))) {
            ps.setLong(1, ownerId);
            ps.setLong(2, cutoffLedgerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) audit.markDuplicateLedgerOperation();
            }
        }
        String outbox = "SELECT COUNT(*) FROM (SELECT operation_id FROM vnd_delivery_outbox "
                + "WHERE account_id=? AND created_at<=? GROUP BY operation_id HAVING COUNT(*)>1 LIMIT 1) d";
        try (PreparedStatement ps = timed(con, outbox, workBudget.queryTimeoutSeconds(timeoutSeconds))) {
            ps.setLong(1, ownerId);
            ps.setTimestamp(2, new Timestamp(cutoffAtMillis));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) audit.markDuplicateOutboxOperation();
            }
        }
    }

    private static final class MovementWorkBudget {
        private long remaining;
        private final long ownerLimit;
        private final long startedNanos = System.nanoTime();
        private final long durationNanos;

        private MovementWorkBudget(long maximum, long ownerLimit, int seconds) {
            remaining = maximum;
            this.ownerLimit = ownerLimit;
            durationNanos = java.util.concurrent.TimeUnit.SECONDS.toNanos(seconds);
        }

        private boolean hasRemaining() {
            return remaining > 0L && timeRemainingNanos() > 0L;
        }

        private long timeRemainingNanos() {
            return durationNanos - (System.nanoTime() - startedNanos);
        }

        private int queryTimeoutSeconds(int configured) throws SQLException {
            long nanos = timeRemainingNanos();
            if (nanos <= 0L) throw new SQLException("SEC-07 scan deadline reached");
            return (int) Math.min(configured, (nanos + 999_999_999L) / 1_000_000_000L);
        }

        private long remaining() {
            return remaining;
        }

        private boolean consume() {
            if (!hasRemaining()) {
                return false;
            }
            remaining--;
            return true;
        }
    }

    private LedgerMovement readLedgerMovement(ResultSet rs) throws SQLException {
        long rowId = rs.getLong("id");
        long durableSequence = rs.getLong("durable_version");
        boolean hasDurableSequence = !rs.wasNull() && durableSequence > 0L;
        if (!hasDurableSequence) durableSequence = rowId;
        String transactionType = rs.getString("transaction_type");
        String schemaVersion = rs.getString("schema_version");
        String source = rs.getString("source");
        long committedAtMillis = timestampMillis(rs.getTimestamp("committed_at"));
        String fingerprint = rs.getString("payload_fingerprint");
        return new LedgerMovement(rowId, rs.getLong("account_id"), rs.getString("owner_type"),
                rs.getString("currency"), rs.getString("operation_id"), rs.getString("business_key"),
                transactionType, rs.getLong("amount"), nullableLong(rs, "requested_amount"),
                nullableLong(rs, "applied_delta"), nullableLong(rs, "balance_before"),
                nullableLong(rs, "balance_after"), rs.getInt("leg_index"), durableSequence,
                fingerprint, schemaVersion, source, committedAtMillis,
                hasDurableSequence && schemaVersion != null && schemaVersion.startsWith("SEC-07")
                        && source != null && !source.isBlank() && committedAtMillis > 0L
                        && ("BASELINE_OPENING".equals(transactionType)
                        || fingerprint != null && fingerprint.matches("[0-9a-fA-F]{64}")));
    }

    private OutboxMovement readJoinedOutbox(ResultSet rs) throws SQLException {
        return new OutboxMovement(rs.getLong("o_id"), rs.getLong("o_account_id"),
                rs.getString("o_owner_type"), rs.getString("o_currency"),
                rs.getString("o_operation_id"), rs.getString("o_business_key"),
                rs.getString("o_product_type"), rs.getLong("o_amount"),
                rs.getString("o_payload_fingerprint"), rs.getString("o_status"),
                rs.getInt("o_retry_count"), timestampMillis(rs.getTimestamp("o_created_at")),
                timestampMillis(rs.getTimestamp("o_delivered_at")));
    }

    private OutboxMovement readOutboxMovement(ResultSet rs) throws SQLException {
        return new OutboxMovement(rs.getLong("id"), rs.getLong("account_id"),
                rs.getString("owner_type"), rs.getString("currency"),
                rs.getString("operation_id"), rs.getString("business_key"),
                rs.getString("product_type"), rs.getLong("amount"),
                rs.getString("payload_fingerprint"), rs.getString("status"),
                rs.getInt("retry_count"), timestampMillis(rs.getTimestamp("created_at")),
                timestampMillis(rs.getTimestamp("delivered_at")));
    }

    private void ensureSec07Columns(Connection con, int timeoutSeconds) throws SQLException {
        String[] required = {
            "operation_id", "business_key", "owner_type", "currency", "requested_amount",
            "applied_delta", "leg_index", "durable_version", "payload_fingerprint", "schema_version",
            "source", "committed_at"
        };
        String sql = "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() "
                + "AND TABLE_NAME=? AND COLUMN_NAME IN (?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = timed(con, sql, timeoutSeconds)) {
            ps.setString(1, "vnd_transaction_ledger");
            for (int i = 0; i < required.length; i++) {
                ps.setString(i + 2, required[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getInt(1) != required.length) {
                    throw new SQLException("SEC-07 migration is not applied to vnd_transaction_ledger");
                }
            }
        }
        String outboxSql = "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() "
                + "AND TABLE_NAME=? AND COLUMN_NAME IN (?,?,?,?,?,?)";
        String[] outboxRequired = {"operation_id", "business_key", "owner_type", "currency", "requested_amount", "schema_version"};
        try (PreparedStatement ps = timed(con, outboxSql, timeoutSeconds)) {
            ps.setString(1, "vnd_delivery_outbox");
            for (int i = 0; i < outboxRequired.length; i++) {
                ps.setString(i + 2, outboxRequired[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getInt(1) != outboxRequired.length) {
                    throw new SQLException("SEC-07 migration is not applied to vnd_delivery_outbox");
                }
            }
        }
    }

    private PreparedStatement timed(Connection con, String sql, int timeoutSeconds) throws SQLException {
        PreparedStatement statement = con.prepareStatement(sql);
        statement.setQueryTimeout(timeoutSeconds);
        return statement;
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static long timestampMillis(Timestamp timestamp) {
        return timestamp == null ? 0L : timestamp.getTime();
    }

    private static boolean isSafeOperationKey(String value) {
        return value != null && value.matches("[A-Za-z0-9._:-]{1,128}");
    }

    private static boolean isSha256Fingerprint(String value) {
        return value != null && value.matches("[0-9a-fA-F]{64}");
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
