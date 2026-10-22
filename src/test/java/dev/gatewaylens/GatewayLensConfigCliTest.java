package dev.gatewaylens;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayLensConfigCliTest {
    @Test
    void cliLoadsCustomPolicyAndUsesOverriddenSeverity() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = GatewayLens.run(new String[]{
                        "examples/legacy-api.yaml",
                        "--config", "examples/gatewaylens-policy.json",
                        "--format", "json",
                        "--fail-on", "never"},
                new PrintStream(stdout), new PrintStream(stderr));

        assertEquals(0, exitCode, stderr.toString(StandardCharsets.UTF_8));
        String report = stdout.toString(StandardCharsets.UTF_8);
        assertTrue(report.contains("GL005"), report);
        assertTrue(report.contains("ERROR"), report);
        assertTrue(report.contains("POST /inventory") == false, report);
    }

    @Test
    void cliReturnsUsageErrorForInvalidPolicy(@TempDir Path tempDir) throws Exception {
        Path invalidPolicy = tempDir.resolve("invalid-policy.json");
        Files.writeString(invalidPolicy, "{\"severityOverrides\":{\"GL005\":\"CRITICAL\"}}");
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = GatewayLens.run(new String[]{
                        "examples/legacy-api.yaml", "--config", invalidPolicy.toString()},
                new PrintStream(stdout), new PrintStream(stderr));

        assertEquals(2, exitCode);
        assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("Could not load policy configuration"));
    }
}
