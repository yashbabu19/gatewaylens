package dev.gatewaylens;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyConfigTest {
    private final PolicyChecker checker = new PolicyChecker();

    @Test
    void customPolicyDisablesRulesOverridesSeverityAndIgnoresAnOperation() throws Exception {
        PolicyConfig policy = PolicyConfig.load(Path.of("examples", "gatewaylens-policy.json"));
        CheckReport report = checker.check(Path.of("examples", "legacy-api.yaml"), policy);

        assertFalse(report.findings().stream().anyMatch(f -> f.ruleId().equals("GL006")),
                "GL006 should be removed because the example policy disables it");
        assertFalse(report.findings().stream().anyMatch(f -> f.location().equals("POST /inventory")),
                "Ignored operations should produce no findings");
        assertTrue(report.findings().stream().anyMatch(f -> f.ruleId().equals("GL005")
                        && f.severity() == Severity.ERROR),
                "GL005 should use the configured ERROR severity");
        assertTrue(report.findings().stream().anyMatch(f -> f.ruleId().equals("GL001")
                        && f.severity() == Severity.INFO),
                "GL001 should use the configured INFO severity");
    }

    @Test
    void unknownRuleIdsAreRejected(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("policy.json");
        Files.writeString(file, "{\"disabledRules\":[\"GL999\"]}");
        assertThrows(IllegalArgumentException.class, () -> PolicyConfig.load(file));
    }

    @Test
    void invalidSeverityOverridesAreRejected(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("policy.json");
        Files.writeString(file, "{\"severityOverrides\":{\"GL005\":\"CRITICAL\"}}");
        assertThrows(IllegalArgumentException.class, () -> PolicyConfig.load(file));
    }

    @Test
    void unknownConfigurationFieldsAreRejected(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("policy.json");
        Files.writeString(file, "{\"severityOverides\":{}}");
        assertThrows(IllegalArgumentException.class, () -> PolicyConfig.load(file));
    }

    @Test
    void invalidIgnoredOperationIsRejected(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("policy.json");
        Files.writeString(file, "{\"ignoreOperations\":[\"HEALTH /health\"]}");
        assertThrows(IllegalArgumentException.class, () -> PolicyConfig.load(file));
    }

    @Test
    void defaultPolicyLeavesFindingsUnchanged() throws Exception {
        CheckReport defaultReport = checker.check(Path.of("examples", "legacy-api.yaml"));
        CheckReport explicitDefaultReport = checker.check(Path.of("examples", "legacy-api.yaml"), PolicyConfig.defaults());
        assertEquals(defaultReport.findings(), explicitDefaultReport.findings());
    }
}
