package dev.gatewaylens;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** User-supplied policy adjustments for built-in GatewayLens rules. */
public final class PolicyConfig {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> RULE_IDS = Set.of(
            "GL000", "GL001", "GL002", "GL003", "GL004", "GL005", "GL006", "GL007", "GL008");
    private static final Set<String> ALLOWED_METHODS = Set.of(
            "GET", "PUT", "POST", "DELETE", "PATCH", "OPTIONS", "HEAD", "TRACE");
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "disabledRules", "severityOverrides", "ignoreOperations");

    private final Set<String> disabledRules;
    private final Map<String, Severity> severityOverrides;
    private final Set<String> ignoredOperations;

    private PolicyConfig(Set<String> disabledRules, Map<String, Severity> severityOverrides,
                         Set<String> ignoredOperations) {
        this.disabledRules = Set.copyOf(disabledRules);
        this.severityOverrides = Map.copyOf(severityOverrides);
        this.ignoredOperations = Set.copyOf(ignoredOperations);
    }

    public static PolicyConfig defaults() {
        return new PolicyConfig(Set.of(), Map.of(), Set.of());
    }

    /** Loads and validates a JSON policy file. Missing optional fields use empty defaults. */
    public static PolicyConfig load(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("Policy file does not exist or is not a regular file: " + path);
        }
        JsonNode root = JSON.readTree(path.toFile());
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Policy file must contain a JSON object.");
        }

        Iterator<String> fieldNames = root.fieldNames();
        while (fieldNames.hasNext()) {
            String field = fieldNames.next();
            if (!ALLOWED_FIELDS.contains(field)) {
                throw new IllegalArgumentException("Unknown policy field '" + field + "'. Allowed fields: "
                        + String.join(", ", ALLOWED_FIELDS) + ".");
            }
        }

        Set<String> disabledRules = readRuleArray(root, "disabledRules");
        Map<String, Severity> overrides = readOverrides(root.get("severityOverrides"));
        Set<String> ignoredOperations = readOperations(root.get("ignoreOperations"));
        return new PolicyConfig(disabledRules, overrides, ignoredOperations);
    }

    /** Applies operation ignores, disabled rules, and severity overrides to findings. */
    List<Finding> apply(List<Finding> findings) {
        List<Finding> adjusted = new ArrayList<>();
        for (Finding finding : findings) {
            if (disabledRules.contains(finding.ruleId()) || ignoredOperations.contains(finding.location())) {
                continue;
            }
            Severity override = severityOverrides.get(finding.ruleId());
            if (override == null || override == finding.severity()) {
                adjusted.add(finding);
            } else {
                adjusted.add(new Finding(finding.ruleId(), override, finding.location(),
                        finding.message(), finding.recommendation()));
            }
        }
        return adjusted;
    }

    private static Set<String> readRuleArray(JsonNode root, String field) {
        JsonNode array = root.get(field);
        if (array == null) {
            return Set.of();
        }
        if (!array.isArray()) {
            throw new IllegalArgumentException("Policy field '" + field + "' must be an array of rule IDs.");
        }
        Set<String> values = new LinkedHashSet<>();
        for (JsonNode item : array) {
            if (!item.isTextual() || item.asText().isBlank()) {
                throw new IllegalArgumentException("Every entry in '" + field + "' must be a non-blank rule ID.");
            }
            String ruleId = item.asText().trim().toUpperCase(Locale.ROOT);
            validateRuleId(ruleId);
            values.add(ruleId);
        }
        return values;
    }

    private static Map<String, Severity> readOverrides(JsonNode node) {
        if (node == null) {
            return Map.of();
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException("Policy field 'severityOverrides' must be an object of rule IDs to severities.");
        }
        Map<String, Severity> overrides = new TreeMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String ruleId = entry.getKey().trim().toUpperCase(Locale.ROOT);
            validateRuleId(ruleId);
            if (!entry.getValue().isTextual()) {
                throw new IllegalArgumentException("Severity override for " + ruleId + " must be ERROR, WARNING, or INFO.");
            }
            try {
                overrides.put(ruleId, Severity.valueOf(entry.getValue().asText().trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Invalid severity for " + ruleId + ": '"
                        + entry.getValue().asText() + "'. Use ERROR, WARNING, or INFO.");
            }
        }
        return overrides;
    }

    private static Set<String> readOperations(JsonNode node) {
        if (node == null) {
            return Set.of();
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("Policy field 'ignoreOperations' must be an array of 'METHOD /path' strings.");
        }
        Set<String> operations = new HashSet<>();
        for (JsonNode item : node) {
            if (!item.isTextual()) {
                throw new IllegalArgumentException("Every entry in 'ignoreOperations' must be a 'METHOD /path' string.");
            }
            String value = item.asText().trim();
            String[] parts = value.split("\\s+", 2);
            if (parts.length != 2 || !ALLOWED_METHODS.contains(parts[0].toUpperCase(Locale.ROOT))
                    || !parts[1].startsWith("/")) {
                throw new IllegalArgumentException("Invalid ignored operation '" + value
                        + "'. Use an HTTP method and an absolute path, for example 'GET /health'.");
            }
            operations.add(parts[0].toUpperCase(Locale.ROOT) + " " + parts[1]);
        }
        return operations;
    }

    private static void validateRuleId(String ruleId) {
        if (!RULE_IDS.contains(ruleId)) {
            throw new IllegalArgumentException("Unknown rule ID '" + ruleId + "'. Known rules: "
                    + String.join(", ", RULE_IDS) + ".");
        }
    }
}
