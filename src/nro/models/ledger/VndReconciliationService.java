package nro.models.ledger;

import java.util.ArrayList;
import java.util.List;
import nro.models.utils.Logger;

/**
 * SEC-05: Bounded, read-only reconciliation and audit service.
 * Compares opening baseline + credits - debits against account.vnd to detect and report drift.
 * Never silently rewrites balances, deletes entries, or modifies data.
 */
public class VndReconciliationService {

    private final MoneyLedgerRepository repository;

    public VndReconciliationService(MoneyLedgerRepository repository) {
        this.repository = repository;
    }

    public static VndReconciliationService createDefault() {
        return new VndReconciliationService(MoneyLedgerService.gI().getRepository());
    }

    public record ReconciliationReport(
        int accountsAudited,
        int accountsWithDrift,
        int accountsWithPending,
        List<MoneyLedgerRepository.AccountAudit> driftedAccounts,
        List<String> warnings
    ) {
        public boolean isHealthy() {
            return accountsWithDrift == 0;
        }

        public void printSummary() {
            System.out.println("=== SEC-05 VND LEDGER RECONCILIATION REPORT ===");
            System.out.println("Accounts Audited: " + accountsAudited);
            System.out.println("Accounts with Drift: " + accountsWithDrift);
            System.out.println("Accounts with Pending Deliveries: " + accountsWithPending);
            if (accountsWithDrift > 0) {
                System.out.println("DRIFT DETECTED IN THE FOLLOWING ACCOUNTS:");
                for (MoneyLedgerRepository.AccountAudit a : driftedAccounts) {
                    System.out.println("  AccountId=" + a.accountId()
                        + " | Current=" + a.currentVnd()
                        + " | Baseline=" + a.baselineVnd()
                        + " | Credits=" + a.totalCredits()
                        + " | Debits=" + a.totalDebits()
                        + " | Expected=" + a.expectedVnd()
                        + " | Drift=" + a.drift());
                }
            } else {
                System.out.println("All audited accounts match ledger expectations exactly.");
            }
            if (!warnings.isEmpty()) {
                System.out.println("Warnings:");
                for (String w : warnings) {
                    System.out.println("  - " + w);
                }
            }
            System.out.println("===============================================");
        }
    }

    public ReconciliationReport runAudit(int maxAccounts) {
        List<MoneyLedgerRepository.AccountAudit> audits = repository.auditAccountsWithActivity(maxAccounts);
        List<MoneyLedgerRepository.AccountAudit> drifted = new ArrayList<>();
        int pendingCount = 0;
        List<String> warnings = new ArrayList<>();

        for (MoneyLedgerRepository.AccountAudit audit : audits) {
            if (audit.hasDrift()) {
                drifted.add(audit);
                Logger.error("[SEC-05 Reconciliation] Drift detected for accountId=" + audit.accountId());
            }
            if (audit.pendingDeliveries() > 0) {
                pendingCount++;
                warnings.add("Account " + audit.accountId() + " has " + audit.pendingDeliveries() + " pending outbox deliveries.");
            }
        }

        warnings.add("Note: Direct writes by external payment/top-up processors bypassing MoneyLedgerService will register as drift.");

        return new ReconciliationReport(audits.size(), drifted.size(), pendingCount, drifted, warnings);
    }

    public MoneyLedgerRepository.AccountAudit auditSingleAccount(int accountId) {
        return repository.auditAccount(accountId);
    }
}
