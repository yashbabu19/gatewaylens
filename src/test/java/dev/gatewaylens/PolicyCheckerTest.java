package dev.gatewaylens;

import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyCheckerTest {
    private final PolicyChecker checker = new PolicyChecker();

    @Test
    void cleanDocumentPassesCurrentChecks() throws Exception {
        CheckReport report = checker.check(resource("valid-openapi.yaml"));
        assertEquals(0, report.findingCount(), () -> report.findings().toString());
    }

    @Test
    void publicEndpointCanBeExplicitlyDocumentedWithoutAuthentication() throws Exception {
        CheckReport report = checker.check(resource("public-openapi.yaml"));
        assertFalse(report.findings().stream().anyMatch(f -> f.ruleId().equals("GL005")));
    }

    @Test
    void operationLevelEmptySecurityOverridesGlobalSecurity() throws Exception {
        CheckReport report = checker.check(resource("insecure-override-openapi.yaml"));
        assertTrue(report.findings().stream().anyMatch(f -> f.ruleId().equals("GL005")));
    }

    @Test
    void missingContractAndSecurityDetailsAreReported() throws Exception {
        CheckReport report = checker.check(Path.of("examples", "legacy-api.yaml"));
        Set<String> rules = report.findings().stream().map(Finding::ruleId).collect(Collectors.toSet());
        assertTrue(rules.contains("GL001"));
        assertTrue(rules.contains("GL002"));
        assertTrue(rules.contains("GL005"));
        assertTrue(rules.contains("GL006"));
        assertTrue(rules.contains("GL004"));
    }

    @Test
    void missingResponsesAreAnError() throws Exception {
        CheckReport report = checker.check(Path.of("examples", "legacy-api.yaml"));
        assertTrue(report.findings().stream().anyMatch(f ->
                f.ruleId().equals("GL003") && f.severity() == Severity.ERROR));
    }

    private static Path resource(String name) throws URISyntaxException {
        return Path.of(PolicyCheckerTest.class.getClassLoader().getResource(name).toURI());
    }
}
