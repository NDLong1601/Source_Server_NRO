package tools.tests;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import nro.models.ledger.JdbcMoneyLedgerRepository;
import nro.models.ledger.VndReconciliationRequest;
import nro.models.ledger.VndReconciliationStatus;

/** Exercises the real JDBC paging orchestrator with lazy synthetic result sets.
 * Does NOT establish MySQL transaction, isolation, or locking guarantees. */
public final class Sec07JdbcPagingRegressionTest {
    private static int assertions;
    private static final Timestamp TIME = new Timestamp(1_000);
    private static final String FP = "a".repeat(64);

    public static void main(String[] args) {
        Fixture capped = new Fixture(1_000_002, 0, false);
        var partial = new JdbcMoneyLedgerRepository(capped::connection).readVndSnapshot(request(false));
        check(!partial.coverageComplete(), "default cap is incomplete");
        check(partial.nextCursor() == 1 && partial.unresolvedOwnerIds().contains(1L), "unresolved owner does not rewind cursor");
        check(partial.owners().get(0).evaluated().expectedBalance() == null, "partial JDBC ledger sum is unknown");
        check(!partial.owners().get(0).evaluated().statuses().contains(VndReconciliationStatus.BALANCE_DRIFT), "partial JDBC ledger has no invented drift");

        Fixture full = new Fixture(1_000_002, 0, false);
        var complete = new JdbcMoneyLedgerRepository(full::connection).readVndSnapshot(request(true));
        check(complete.coverageComplete() && complete.owners().get(0).evaluated().isOk(), "full-owner finishes beyond the old cap");
        check(full.ledgerRows == 1_000_002 && full.ledgerPages > 1000, "actual page cursors neither skip nor duplicate rows");
        check(full.connections == 1 && full.rollbacks == 1 && full.closed, "one connection/snapshot is closed after scan");
        check(full.repeatableRead && full.manualCommit, "snapshot setup retained");

        Fixture outbox = new Fixture(1, 1_000_001, false);
        var outboxes = new JdbcMoneyLedgerRepository(outbox::connection).readVndSnapshot(request(true));
        check(outboxes.coverageComplete() && outbox.outboxRows == 1_000_001, "outbox scan passes old cap");
        check(outboxes.owners().get(0).evaluated().statuses().contains(VndReconciliationStatus.ORPHAN_OUTBOX), "orphan findings preserved through pages");
        check(outboxes.owners().get(0).evaluated().findings().size() <= 2048, "findings memory remains bounded");

        Fixture oldCapture = new Fixture(1, 0, true);
        var missing = new JdbcMoneyLedgerRepository(oldCapture::connection).readVndSnapshot(request(true));
        check(missing.owners().get(0).evaluated().statuses().contains(VndReconciliationStatus.MISSING_BASELINE), "NULL sequence still fails closed");
        Fixture repaired = new Fixture(1, 0, false);
        check(new JdbcMoneyLedgerRepository(repaired::connection).readVndSnapshot(request(true))
                .owners().get(0).evaluated().isOk(), "baseline with durable sequence is recognized");
        Fixture expired = new Fixture(2, 0, false);
        expired.expireOnFirstPage = true;
        var timeout = new JdbcMoneyLedgerRepository(expired::connection).readVndSnapshot(
                new VndReconciliationRequest("deadline", 0, 1, "VND", 100, 30, 1000, true, 1));
        check(!timeout.coverageComplete() && timeout.unresolvedOwnerIds().contains(1L), "expired scan remains unresolved");
        check(timeout.owners().get(0).evaluated().expectedBalance() == null, "expired scan does not publish partial balance");
        check(!timeout.owners().get(0).evaluated().statuses().contains(VndReconciliationStatus.MISSING_BASELINE), "expired scan cannot assert unread baseline missing");
        check(expired.closed && expired.rollbacks == 1, "expired snapshot is released");
        System.out.println("SEC-07 JDBC PAGING TESTS PASSED; assertions=" + assertions);
    }

    private static VndReconciliationRequest request(boolean full) {
        return new VndReconciliationRequest("jdbc-test", 0, 1, "VND", 100, 30, 1000, full, 300);
    }

    private static final class Fixture {
        final long ledgerCount, outboxCount;
        final boolean nullSequence;
        int connections, rollbacks, ledgerPages;
        long ledgerRows, outboxRows;
        boolean repeatableRead, manualCommit, closed, expireOnFirstPage;

        Fixture(long ledgerCount, long outboxCount, boolean nullSequence) {
            this.ledgerCount = ledgerCount;
            this.outboxCount = outboxCount;
            this.nullSequence = nullSequence;
        }

        Connection connection() {
            connections++;
            return proxy(Connection.class, (method, args) -> switch (method) {
                case "setTransactionIsolation" -> { repeatableRead = ((Integer) args[0]) == Connection.TRANSACTION_REPEATABLE_READ; yield null; }
                case "setAutoCommit" -> { manualCommit = !((Boolean) args[0]); yield null; }
                case "prepareStatement" -> statement((String) args[0]);
                case "rollback" -> { rollbacks++; yield null; }
                case "close" -> { closed = true; yield null; }
                default -> throw new AssertionError("Unexpected Connection call: " + method);
            });
        }

        PreparedStatement statement(String sql) {
            Map<Integer, Object> parameters = new HashMap<>();
            return proxy(PreparedStatement.class, (method, args) -> {
                if (method.equals("setQueryTimeout")) { check((Integer) args[0] > 0, "query timeout bounded"); return null; }
                if (method.startsWith("set")) { parameters.put((Integer) args[0], args[1]); return null; }
                if (method.equals("close")) return null;
                if (!method.equals("executeQuery")) throw new AssertionError("Audit attempted non-query call: " + method);
                if (sql.contains("information_schema.COLUMNS")) return scalar("vnd_transaction_ledger".equals(parameters.get(1)) ? 12L : 6L);
                if (sql.contains("COALESCE(MAX(id)")) return rows(1, 1, (id, column) -> column.equals(1) ? ledgerCount : TIME);
                if (sql.startsWith("SELECT id FROM account")) return scalar(1L);
                if (sql.startsWith("SELECT vnd")) return scalar(ledgerCount - 1);
                if (sql.startsWith("SELECT COUNT(*)")) return scalar(0L);
                if (sql.contains("FROM vnd_transaction_ledger l LEFT JOIN")) {
                    if (expireOnFirstPage && ledgerPages == 0) {
                        try { Thread.sleep(1100); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new SQLException("test interrupted", e); }
                    }
                    long after = ((Number) parameters.get(5)).longValue();
                    check(after == ledgerRows, "ledger cursor equals last consumed ID");
                    int limit = (Integer) parameters.get(6);
                    ledgerPages++;
                    return rows(after + 1, Math.min(ledgerCount, after + limit), (id, column) -> {
                        if (column.equals("id")) ledgerRows++;
                        return ledgerValue(id, column.toString());
                    });
                }
                if (sql.contains("FROM vnd_delivery_outbox o LEFT JOIN")) {
                    long after = ((Number) parameters.get(4)).longValue();
                    check(after == outboxRows, "outbox cursor equals last consumed ID");
                    int limit = (Integer) parameters.get(5);
                    return rows(after + 1, Math.min(outboxCount, after + limit), (id, column) -> {
                        if (column.equals("id")) outboxRows++;
                        return outboxValue(id, column.toString());
                    });
                }
                throw new AssertionError("Unexpected audit SQL shape");
            });
        }

        Object ledgerValue(long id, String column) {
            return switch (column) {
                case "id" -> id;
                case "account_id" -> 1L;
                case "owner_type" -> "ACCOUNT";
                case "currency" -> "VND";
                case "operation_id", "business_key" -> "op-" + id;
                case "transaction_type", "source" -> id == 1 ? "BASELINE_OPENING" : "CREDIT_ADMIN";
                case "amount", "requested_amount", "applied_delta" -> id == 1 ? 0L : 1L;
                case "balance_before" -> id == 1 ? 0L : id - 2;
                case "balance_after" -> id - 1;
                case "leg_index" -> 0;
                case "durable_version" -> nullSequence ? null : id;
                case "payload_fingerprint" -> id == 1 ? null : FP;
                case "schema_version" -> id == 1 ? "SEC-07-BASELINE" : "SEC-07-V1";
                case "committed_at" -> TIME;
                case "o_id" -> null;
                default -> throw new AssertionError("Unknown ledger column: " + column);
            };
        }

        Object outboxValue(long id, String column) {
            return switch (column) {
                case "id" -> id;
                case "account_id" -> 1L;
                case "owner_type" -> "ACCOUNT";
                case "currency" -> "VND";
                case "operation_id", "business_key" -> "orphan-" + id;
                case "product_type" -> "TRADE_GOLD";
                case "amount" -> 10L;
                case "payload_fingerprint" -> FP;
                case "status" -> "DELIVERED";
                case "retry_count" -> 0;
                case "created_at", "delivered_at" -> TIME;
                case "linked_ledger_id" -> null;
                default -> throw new AssertionError("Unknown outbox column: " + column);
            };
        }
    }

    private static ResultSet scalar(long value) { return rows(1, 1, (id, column) -> value); }

    private static ResultSet rows(long first, long last, BiFunction<Long, Object, Object> values) {
        long[] row = { first - 1 };
        boolean[] wasNull = { false };
        return proxy(ResultSet.class, (method, args) -> {
            if (method.equals("next")) return ++row[0] <= last;
            if (method.equals("close")) return null;
            if (method.equals("wasNull")) return wasNull[0];
            Object value = values.apply(row[0], args[0]);
            wasNull[0] = value == null;
            return switch (method) {
                case "getObject", "getTimestamp" -> value;
                case "getString" -> value == null ? null : value.toString();
                case "getInt" -> value == null ? 0 : ((Number) value).intValue();
                case "getLong" -> value == null ? 0L : ((Number) value).longValue();
                default -> throw new AssertionError("Unknown ResultSet call: " + method);
            };
        });
    }

    @FunctionalInterface private interface Invocation { Object call(String method, Object[] args) throws SQLException; }
    private static <T> T proxy(Class<T> type, Invocation call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (object, method, args) -> call.call(method.getName(), args)));
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
