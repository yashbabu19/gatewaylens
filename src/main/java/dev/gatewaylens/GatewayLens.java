package dev.gatewaylens;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Command-line entry point for GatewayLens. */
public final class GatewayLens {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private GatewayLens() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0 || contains(args, "--help") || contains(args, "-h")) {
            printUsage(out);
            return args.length == 0 ? 2 : 0;
        }

        Path file = null;
        Path configFile = null;
        String format = "text";
        String failOn = "error";
        List<String> values = new ArrayList<>(List.of(args));
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            if (value.equals("--format")) {
                if (i + 1 >= values.size()) {
                    err.println("Missing value for --format.");
                    printUsage(err);
                    return 2;
                }
                format = values.get(++i).toLowerCase(Locale.ROOT);
            } else if (value.equals("--fail-on")) {
                if (i + 1 >= values.size()) {
                    err.println("Missing value for --fail-on.");
                    printUsage(err);
                    return 2;
                }
                failOn = values.get(++i).toLowerCase(Locale.ROOT);
            } else if (value.equals("--config")) {
                if (i + 1 >= values.size()) {
                    err.println("Missing value for --config.");
                    printUsage(err);
                    return 2;
                }
                configFile = Path.of(values.get(++i));
            } else if (value.startsWith("--")) {
                err.println("Unknown option: " + value);
                printUsage(err);
                return 2;
            } else if (file == null) {
                file = Path.of(value);
            } else {
                err.println("Unexpected extra argument: " + value);
                printUsage(err);
                return 2;
            }
        }

        if (file == null || !List.of("text", "json").contains(format)
                || !List.of("error", "warning", "info", "never").contains(failOn)) {
            err.println("Provide one input file, --format text|json, --fail-on error|warning|info|never, and optionally --config policy.json.");
            printUsage(err);
            return 2;
        }
        if (!Files.isRegularFile(file)) {
            err.println("Input file does not exist or is not a regular file: " + file);
            return 2;
        }

        final PolicyConfig policy;
        try {
            policy = configFile == null ? PolicyConfig.defaults() : PolicyConfig.load(configFile);
        } catch (IOException | IllegalArgumentException exception) {
            err.println("Could not load policy configuration: " + exception.getMessage());
            return 2;
        }

        CheckReport report = new PolicyChecker().check(file, policy);
        try {
            if (format.equals("json")) {
                out.println(JSON.writeValueAsString(report));
            } else {
                printText(report, out);
            }
        } catch (Exception exception) {
            err.println("Could not write the report: " + exception.getMessage());
            return 2;
        }
        return shouldFail(report, failOn) ? 1 : 0;
    }

    private static boolean shouldFail(CheckReport report, String failOn) {
        return switch (failOn) {
            case "error" -> report.findings().stream().anyMatch(f -> f.severity() == Severity.ERROR);
            case "warning" -> report.findings().stream().anyMatch(f -> f.severity() == Severity.ERROR || f.severity() == Severity.WARNING);
            case "info" -> !report.findings().isEmpty();
            default -> false;
        };
    }

    private static void printText(CheckReport report, PrintStream out) {
        out.printf("GatewayLens report — %s%n", report.file());
        out.printf("Findings: %d (ERROR: %d, WARNING: %d, INFO: %d)%n%n",
                report.findingCount(),
                report.summary().getOrDefault(Severity.ERROR, 0L),
                report.summary().getOrDefault(Severity.WARNING, 0L),
                report.summary().getOrDefault(Severity.INFO, 0L));
        if (report.findings().isEmpty()) {
            out.println("No findings. The document passed the current GatewayLens checks.");
            return;
        }
        for (Finding finding : report.findings()) {
            out.printf("[%s] %s — %s%n", finding.severity(), finding.ruleId(), finding.location());
            out.printf("  %s%n  Recommendation: %s%n%n", finding.message(), finding.recommendation());
        }
    }

    private static boolean contains(String[] values, String sought) {
        for (String value : values) {
            if (sought.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static void printUsage(PrintStream out) {
        out.println("GatewayLens — check an OpenAPI contract before gateway onboarding");
        out.println("Usage: java -jar target/gatewaylens.jar <openapi.yaml|openapi.json> [options]");
        out.println("Options:");
        out.println("  --config <policy.json>      Optional policy configuration file");
        out.println("  --format text|json          Report format (default: text)");
        out.println("  --fail-on error|warning|info|never  Failure threshold (default: error)");
        out.println("  --help                       Show this help");
    }
}
