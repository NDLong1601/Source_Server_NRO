package nro.models.services_func;

import nro.models.services_func.TradeCommitPlan.ImmutableTradeData;

/**
 * Persistence boundary for the trade audit ledger.
 * Implementations must never throw into the trade state machine.
 */
public interface TradeLedgerSink {

    /** Persist the COMMITTING intent. Returning false prevents inventory mutation. */
    boolean begin(ImmutableTradeData data);

    /** Mark a previously persisted intent COMMITTED after inventory mutation. */
    boolean committed(ImmutableTradeData data);

    /** Best-effort persistence for terminal trades which did not commit. */
    boolean terminal(ImmutableTradeData data, String status, String resultCode);
}
