package dev.gatewaylens;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Runs the built-in static checks against an OpenAPI 3 document. */
public final class PolicyChecker {
    public static final String PUBLIC_EXTENSION = "x-gatewaylens-public";

    public CheckReport check(Path input) {
        return check(input, PolicyConfig.defaults());
    }

    public CheckReport check(Path input, PolicyConfig policy) {
        List<Finding> findings = new ArrayList<>();
        final SwaggerParseResult parsed;
        try {
            parsed = new OpenAPIV3Parser().readLocation(input.toAbsolutePath().toString(), null, null);
        } catch (RuntimeException exception) {
            findings.add(new Finding(
                    "GL000", Severity.ERROR, input.toString(),
                    "Could not read or parse the OpenAPI document: " + safeMessage(exception),
                    "Confirm the file exists and contains a valid OpenAPI 3.0 or 3.1 document."));
            return createReport(input, findings, policy);
        }

        if (parsed == null || parsed.getOpenAPI() == null) {
            String detail = "";
            if (parsed != null && parsed.getMessages() != null && !parsed.getMessages().isEmpty()) {
                detail = " Parser details: " + String.join("; ", parsed.getMessages().stream().limit(3).toList());
            }
            findings.add(new Finding(
                    "GL000", Severity.ERROR, input.toString(),
                    "Could not parse the OpenAPI document." + detail,
                    "Fix the parser errors and rerun GatewayLens."));
            return createReport(input, findings, policy);
        }

        inspect(parsed.getOpenAPI(), findings);
        return createReport(input, findings, policy);
    }

    private CheckReport createReport(Path input, List<Finding> findings, PolicyConfig policy) {
        List<Finding> adjusted = policy.apply(findings);
        adjusted.sort(Comparator.comparing(Finding::location)
                .thenComparing(Finding::ruleId)
                .thenComparing(f -> f.severity().name()));
        return CheckReport.of(input.toString(), adjusted);
    }

    private void inspect(OpenAPI api, List<Finding> findings) {
        if (api.getPaths() == null || api.getPaths().isEmpty()) {
            findings.add(new Finding("GL007", Severity.ERROR, "paths",
                    "The document does not declare any API paths.",
                    "Add at least one documented endpoint under paths."));
            return;
        }

        List<String> paths = api.getPaths().keySet().stream().sorted().toList();
        checkDuplicateOperationIds(api, paths, findings);

        for (String path : paths) {
            PathItem pathItem = api.getPaths().get(path);
            if (pathItem == null || pathItem.readOperationsMap() == null) {
                continue;
            }
            pathItem.readOperationsMap().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing((PathItem.HttpMethod method) -> method.name())))
                    .forEach(entry -> inspectOperation(api, entry.getKey(), path, entry.getValue(), findings));
        }
    }

    /** GL008: operationId values must uniquely identify operations across the document. */
    private void checkDuplicateOperationIds(OpenAPI api, List<String> paths, List<Finding> findings) {
        Map<String, List<String>> locationsByOperationId = new TreeMap<>();
        for (String path : paths) {
            PathItem pathItem = api.getPaths().get(path);
            if (pathItem == null || pathItem.readOperationsMap() == null) {
                continue;
            }
            pathItem.readOperationsMap().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing((PathItem.HttpMethod method) -> method.name())))
                    .forEach(entry -> {
                        Operation operation = entry.getValue();
                        if (operation == null || isBlank(operation.getOperationId())) {
                            return;
                        }
                        String location = entry.getKey().name() + " " + path;
                        locationsByOperationId.computeIfAbsent(operation.getOperationId(), ignored -> new ArrayList<>())
                                .add(location);
                    });
        }

        for (Map.Entry<String, List<String>> entry : locationsByOperationId.entrySet()) {
            List<String> locations = entry.getValue();
            if (locations.size() < 2) {
                continue;
            }
            for (String location : locations) {
                List<String> otherLocations = locations.stream()
                        .filter(other -> !other.equals(location))
                        .toList();
                findings.add(new Finding(
                        "GL008", Severity.ERROR, location,
                        "Operation ID '" + entry.getKey() + "' is duplicated; it is also used by "
                                + String.join(", ", otherLocations) + ".",
                        "Assign a unique operationId to every operation. Keep IDs stable after publishing clients or tooling that depend on them."));
            }
        }
    }

    private void inspectOperation(OpenAPI api, PathItem.HttpMethod method, String path,
                                  Operation operation, List<Finding> findings) {
        String location = method.name() + " " + path;

        if (isBlank(operation.getOperationId())) {
            findings.add(new Finding("GL001", Severity.WARNING, location,
                    "Operation is missing operationId.",
                    "Add a stable, unique operationId for code generation, tracing, and tooling."));
        }

        if (isBlank(operation.getSummary()) && isBlank(operation.getDescription())) {
            findings.add(new Finding("GL002", Severity.WARNING, location,
                    "Operation has neither a summary nor a description.",
                    "Describe the operation's purpose, expected inputs, and important behavior."));
        }

        if (operation.getResponses() == null || operation.getResponses().isEmpty()) {
            findings.add(new Finding("GL003", Severity.ERROR, location,
                    "Operation does not define any responses.",
                    "Document expected success and failure responses, including relevant status codes."));
        } else if (operation.getResponses().keySet().stream().noneMatch(PolicyChecker::isSuccessResponse)) {
            findings.add(new Finding("GL004", Severity.WARNING, location,
                    "Operation does not declare a 2xx success response.",
                    "Document at least one successful 2xx response (for example, 200 or 204)."));
        }

        if (!isExplicitlyPublic(operation) && !hasEffectiveSecurity(api, operation)) {
            findings.add(new Finding("GL005", Severity.WARNING, location,
                    "No security requirement is declared for this operation.",
                    "Declare an operation-level or global security requirement, or explicitly mark an intentionally public endpoint with "
                            + PUBLIC_EXTENSION + ": true."));
        }

        if (operation.getTags() == null || operation.getTags().isEmpty()) {
            findings.add(new Finding("GL006", Severity.INFO, location,
                    "Operation has no tags.",
                    "Group related operations with tags to make API documentation easier to navigate."));
        }
    }

    private static boolean hasEffectiveSecurity(OpenAPI api, Operation operation) {
        // A present operation-level security list overrides the root-level list, including an empty list.
        if (operation.getSecurity() != null) {
            return !operation.getSecurity().isEmpty();
        }
        return api.getSecurity() != null && !api.getSecurity().isEmpty();
    }

    private static boolean isExplicitlyPublic(Operation operation) {
        if (operation.getExtensions() == null) {
            return false;
        }
        Object value = operation.getExtensions().get(PUBLIC_EXTENSION);
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static boolean isSuccessResponse(String code) {
        return code != null && (code.matches("2[0-9]{2}") || "2XX".equalsIgnoreCase(code));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
