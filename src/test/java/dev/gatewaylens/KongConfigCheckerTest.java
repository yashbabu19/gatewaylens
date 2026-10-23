package dev.gatewaylens;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KongConfigCheckerTest {
    private final KongConfigChecker checker = new KongConfigChecker();

    @Test
    void validDeclarativeConfigPassesChecks() {
        CheckReport report = checker.check(Path.of("examples", "kong-declarative.yaml"));
        assertEquals(0, report.findingCount(), () -> report.findings().toString());
    }

    @Test
    void invalidDeclarativeConfigReportsActionableFindings() {
        CheckReport report = checker.check(Path.of("examples", "kong-invalid.yaml"));
        Set<String> rules = report.findings().stream().map(Finding::ruleId).collect(Collectors.toSet());
        assertTrue(rules.containsAll(Set.of("KG001", "KG003", "KG004", "KG005", "KG006", "KG007", "KG008", "KG009")),
                () -> report.findings().toString());
    }

    @Test
    void parserErrorsBecomeStructuredFindings(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("broken.yaml");
        Files.writeString(file, "services: [\n - this: is: not: valid");
        CheckReport report = checker.check(file);
        assertTrue(report.findings().stream().anyMatch(f -> f.ruleId().equals("KG000")));
    }

    @Test
    void cliCanSelectKongConfigAndJsonOutput() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exitCode = GatewayLens.run(new String[]{
                        "examples/kong-declarative.yaml", "--type", "kong", "--format", "json"},
                new PrintStream(stdout), new PrintStream(stderr));
        assertEquals(0, exitCode, stderr.toString(StandardCharsets.UTF_8));
        assertTrue(stdout.toString(StandardCharsets.UTF_8).contains("\"findingCount\" : 0"),
                stdout.toString(StandardCharsets.UTF_8));
    }

    @Test
    void policyConfigCannotAccidentallyBeAppliedToKongMode(@TempDir Path tempDir) throws Exception {
        Path config = tempDir.resolve("policy.json");
        Files.writeString(config, "{}");
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exitCode = GatewayLens.run(new String[]{
                        "examples/kong-declarative.yaml", "--type", "kong", "--config", config.toString()},
                new PrintStream(new ByteArrayOutputStream()), new PrintStream(stderr));
        assertEquals(2, exitCode);
        assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("currently applies only to OpenAPI"));
    }
}
