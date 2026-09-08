package tools.tests;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import nro.models.ledger.VndReconciliationCalculator;
import nro.models.ledger.VndReconciliationCli;
import nro.models.ledger.VndReconciliationDataSource;
import nro.models.ledger.VndReconciliationRequest;
import nro.models.ledger.VndReconciliationRunReport;
import nro.models.ledger.VndReconciliationStatus;

/**
 * SEC-07 CLI stream/exit regression. The injected executor keeps this test
 * away from the configured database and still exercises the real CLI seam.
 */
public final class Sec07CliRegressionTest {

    private static int assertions;

    private Sec07CliRegressionTest() {
    }

    public static void main(String[] args) {
        testMachineReadableOutputAndExitCodes();
        testFullOwnerOptions();
        System.out.println("SEC-07 CLI TESTS PASSED; assertions=" + assertions);
    }

    private static void testFullOwnerOptions() {
        PrintStream sink = new PrintStream(new ByteArrayOutputStream());
        int valid = VndReconciliationCli.run(new String[]{"--account-id", "42", "--full-owner",
                "--run-timeout-seconds", "600"}, request -> {
                    check(request.fullOwnerScan() && request.ownerId() == 42, "full scan targets one explicit owner");
                    equalsInt(600, request.runTimeoutSeconds(), "scan deadline is passed through");
                    return cleanReport();
                }, sink, sink);
        equalsInt(0, valid, "full-owner arguments accepted");
        for (String[] args : List.of(new String[]{"--full-owner"},
                new String[]{"--account-id", "1", "--full-owner", "--after-owner-id", "1"},
                new String[]{"--run-timeout-seconds", "0"},
                new String[]{"--run-timeout-seconds", "3601"})) {
            equalsInt(64, VndReconciliationCli.run(args, request -> {
                throw new AssertionError("invalid options must not execute audit");
            }, sink, sink), "invalid full-scan/deadline arguments rejected before audit");
        }
    }

    private static void testMachineReadableOutputAndExitCodes() {
        VndReconciliationRunReport clean = cleanReport();

        ByteArrayOutputStream jsonBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream jsonErrors = new ByteArrayOutputStream();
        int jsonExit = VndReconciliationCli.run(new String[]{"--format", "json"},
                request -> clean, new PrintStream(jsonBytes), new PrintStream(jsonErrors));
        equalsInt(0, jsonExit, "CLI clean JSON exit code");
        String json = jsonBytes.toString();
        check(json.startsWith("{") && json.trim().endsWith("}") && !json.contains("\033"),
                "CLI JSON stdout contains only machine-readable report");
        check(jsonErrors.toString().contains("SEC-07 run=run-cli-clean"),
                "CLI summary is written to stderr");

        ByteArrayOutputStream csvBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream csvErrors = new ByteArrayOutputStream();
        int csvExit = VndReconciliationCli.run(new String[]{"--format", "csv"},
                request -> clean, new PrintStream(csvBytes), new PrintStream(csvErrors));
        equalsInt(0, csvExit, "CLI clean CSV exit code");
        check(csvBytes.toString().startsWith("run_id,duration_ms,overall_status"),
                "CLI CSV stdout starts with the report header");
        check(!csvBytes.toString().contains("SEC-07 run="),
                "CLI CSV stdout has no diagnostic summary");

        ByteArrayOutputStream unavailableBytes = new ByteArrayOutputStream();
        int unavailableExit = VndReconciliationCli.run(new String[]{"--format", "json"},
                request -> VndReconciliationRunReport.unavailable(request, 1L, "TEST_UNAVAILABLE"),
                new PrintStream(unavailableBytes), new PrintStream(new ByteArrayOutputStream()));
        equalsInt(3, unavailableExit, "CLI unavailable report exit code");
        check(unavailableBytes.toString().contains("\"overallStatus\":\"UNAVAILABLE\""),
                "CLI unavailable still emits a machine-readable report");

        ByteArrayOutputStream bootstrapBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream bootstrapErrors = new ByteArrayOutputStream();
        int bootstrapExit = VndReconciliationCli.run(new String[]{"--format", "json"},
                request -> { throw new IllegalStateException("secret-config-value"); },
                new PrintStream(bootstrapBytes), new PrintStream(bootstrapErrors));
        equalsInt(3, bootstrapExit, "CLI bootstrap failure exit code");
        check(bootstrapBytes.toString().contains("\"overallStatus\":\"UNAVAILABLE\""),
                "CLI bootstrap failure still emits unavailable JSON");
        check(!bootstrapErrors.toString().contains("secret-config-value"),
                "CLI bootstrap diagnostics do not expose exception details");

        ByteArrayOutputStream invalidBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream invalidErrors = new ByteArrayOutputStream();
        int invalidExit = VndReconciliationCli.run(new String[]{"--format", "xml"},
                request -> clean, new PrintStream(invalidBytes), new PrintStream(invalidErrors));
        equalsInt(64, invalidExit, "CLI invalid argument exit code");
        check(invalidBytes.size() == 0 && invalidErrors.toString().contains("invalid arguments"),
                "CLI invalid arguments do not pollute report stdout");

        ByteArrayOutputStream helpBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream helpErrors = new ByteArrayOutputStream();
        int helpExit = VndReconciliationCli.run(new String[]{"--help"},
                request -> { throw new AssertionError("--help must not execute audit"); },
                new PrintStream(helpBytes), new PrintStream(helpErrors));
        equalsInt(0, helpExit, "CLI help exit code");
        check(helpBytes.toString().startsWith("Usage: java nro.models.ledger.VndReconciliationCli"),
                "CLI help does not require bootstrap");
        check(helpErrors.size() == 0, "CLI help has no diagnostic noise");
    }

    private static VndReconciliationRunReport cleanReport() {
        VndReconciliationDataSource.LedgerMovement baseline = new VndReconciliationDataSource.LedgerMovement(
                1L, 30L, "ACCOUNT", "VND", "base-30", "base-30", "BASELINE_OPENING",
                0L, 0L, 0L, 0L, 0L, 0, 1L, null, "SEC-07-BASELINE",
                "BASELINE_OPENING", 1_000L, true);
        VndReconciliationCalculator.OwnerReconciliation owner =
                VndReconciliationCalculator.evaluate(
                        new VndReconciliationDataSource.OwnerSnapshot(
                                30L, 0L, List.of(baseline), List.of(), true, null),
                        1_000L, 100L);
        return new VndReconciliationRunReport(
                "run-cli-clean", 1L, 2L, 1L, 1L, "REPEATABLE_READ_OWNER_SNAPSHOT", 0L, 30L,
                false, true, List.of(owner), Map.of(VndReconciliationStatus.OK, 1), "");
    }

    private static void equalsInt(int expected, int actual, String message) {
        check(expected == actual, message + " expected=" + expected + " actual=" + actual);
    }

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError("FAILED: " + message);
    }
}
