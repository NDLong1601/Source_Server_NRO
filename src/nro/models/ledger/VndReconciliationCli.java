package nro.models.ledger;

import java.io.PrintStream;

/**
 * Read-only SEC-07 CLI. It does not insert run records, update findings, or
 * invoke delivery/recovery code.
 */
public final class VndReconciliationCli {

    private VndReconciliationCli() {
    }

    @FunctionalInterface
    public interface AuditExecutor {
        VndReconciliationRunReport execute(VndReconciliationRequest request);
    }

    public static void main(String[] args) {
        int exitCode = run(args, null, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    /**
     * CLI process seam. The production path passes {@code null} and creates
     * the JDBC service with diagnostics redirected during bootstrap. Tests can
     * inject a pure executor and capture both streams without touching a DB.
     */
    public static int run(String[] args, AuditExecutor injectedExecutor,
            PrintStream stdout, PrintStream stderr) {
        try {
            Options options = Options.parse(args);
            if (options.help) {
                stdout.println(Options.usage());
                return 0;
            }
            VndReconciliationRequest request = options.request();
            VndReconciliationRunReport report;
            try {
                report = injectedExecutor == null
                        ? executeWithDiagnosticsOnStderr(request)
                        : injectedExecutor.execute(request);
            } catch (RuntimeException | LinkageError bootstrapFailure) {
                report = VndReconciliationRunReport.unavailable(request, System.currentTimeMillis(),
                        "CLI_BOOTSTRAP_UNAVAILABLE");
                stderr.println("SEC-07 reconciliation unavailable: "
                        + bootstrapFailure.getClass().getSimpleName());
            }
            stdout.println("json".equals(options.format) ? report.toJson() : report.toCsv());
            stderr.println(report.summaryLine());
            return report.exitCode();
        } catch (IllegalArgumentException invalid) {
            stderr.println("SEC-07 invalid arguments: " + invalid.getMessage());
            stderr.println(Options.usage());
            return 64;
        }
    }

    private static VndReconciliationRunReport executeWithDiagnosticsOnStderr(
            VndReconciliationRequest request) {
        // Logger's static initializer replaces System.out using FileDescriptor.out.
        // Initialize it before installing the CLI-only redirect, otherwise its
        // first diagnostic silently undoes the redirect during DB bootstrap.
        nro.models.utils.Logger.log("");
        PrintStream reportStream = System.out;
        try {
            // LocalManager and the legacy Logger write bootstrap diagnostics to
            // stdout. Redirect only during bootstrap/audit and restore before
            // serializing the machine-readable report.
            System.setOut(System.err);
            return new VndReconciliationService(
                    MoneyLedgerService.gI().getRepository()).runSec07(request);
        } finally {
            System.setOut(reportStream);
        }
    }

    private static final class Options {
        private String format = "json";
        private boolean help;
        private long accountId = -1L;
        private long after = 0L;
        private int limit = 100;
        private int timeout = 30;
        private int runTimeout = 300;
        private boolean fullOwner;
        private long staleMinutes = 24L * 60L;

        private VndReconciliationRequest request() {
            long staleMillis;
            try {
                staleMillis = Math.multiplyExact(Math.multiplyExact(staleMinutes, 60L), 1_000L);
            } catch (ArithmeticException overflow) {
                throw new IllegalArgumentException("--stale-minutes is too large");
            }
            return new VndReconciliationRequest(
                    java.util.UUID.randomUUID().toString().replace("-", ""), after, accountId,
                    "VND", limit, timeout, staleMillis, fullOwner, runTimeout);
        }

        private static Options parse(String[] args) {
            Options options = new Options();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--help", "-h" -> options.help = true;
                    case "--format" -> options.format = next(args, ++i, arg).toLowerCase(java.util.Locale.ROOT);
                    case "--account-id" -> options.accountId = parseLong(next(args, ++i, arg), arg);
                    case "--after-owner-id" -> options.after = parseLong(next(args, ++i, arg), arg);
                    case "--limit" -> options.limit = parseInt(next(args, ++i, arg), arg);
                    case "--timeout-seconds" -> options.timeout = parseInt(next(args, ++i, arg), arg);
                    case "--run-timeout-seconds" -> options.runTimeout = parseInt(next(args, ++i, arg), arg);
                    case "--full-owner" -> options.fullOwner = true;
                    case "--stale-minutes" -> options.staleMinutes = parseLong(next(args, ++i, arg), arg);
                    case "--currency" -> {
                        if (!"VND".equalsIgnoreCase(next(args, ++i, arg))) {
                            throw new IllegalArgumentException("only --currency VND is supported");
                        }
                    }
                    default -> throw new IllegalArgumentException("unknown argument " + arg);
                }
            }
            if (!options.help && !"json".equals(options.format) && !"csv".equals(options.format)) {
                throw new IllegalArgumentException("format must be json or csv");
            }
            return options;
        }

        private static String next(String[] args, int index, String option) {
            if (index >= args.length) throw new IllegalArgumentException(option + " requires a value");
            return args[index];
        }

        private static long parseLong(String value, String option) {
            try { return Long.parseLong(value); }
            catch (NumberFormatException e) { throw new IllegalArgumentException(option + " must be an integer"); }
        }

        private static int parseInt(String value, String option) {
            try { return Integer.parseInt(value); }
            catch (NumberFormatException e) { throw new IllegalArgumentException(option + " must fit a 32-bit integer"); }
        }

        private static String usage() {
            return "Usage: java nro.models.ledger.VndReconciliationCli [--account-id N] "
                    + "[--after-owner-id N] [--limit 1..1000] [--timeout-seconds 1..120] "
                    + "[--stale-minutes N] [--format json|csv] [--currency VND] "
                    + "[--full-owner --account-id N] [--run-timeout-seconds 1..3600]";
        }
    }
}
