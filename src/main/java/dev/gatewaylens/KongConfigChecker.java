package dev.gatewaylens;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Static checks for Kong Gateway declarative YAML/JSON configuration. */
public final class KongConfigChecker {
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final List<String> SUPPORTED_FORMAT_VERSIONS = List.of("1.1", "2.1", "3.0");
    private static final List<String> ROUTE_MATCH_FIELDS = List.of(
            "paths", "hosts", "methods", "snis", "headers", "sources", "destinations");

    public CheckReport check(Path input) {
        List<Finding> findings = new ArrayList<>();
        final JsonNode root;
        try {
            root = YAML.readTree(input.toFile());
        } catch (IOException | RuntimeException exception) {
            findings.add(new Finding("KG000", Severity.ERROR, input.toString(),
                    "Could not read or parse the Kong declarative configuration.",
                    "Confirm the file exists and contains valid YAML or JSON."));
            return report(input, findings);
        }

        if (root == null || !root.isObject()) {
            findings.add(new Finding("KG000", Severity.ERROR, input.toString(),
                    "Kong declarative configuration must be a top-level object.",
                    "Use a YAML or JSON object containing _format_version and entity collections."));
            return report(input, findings);
        }

        String formatVersion = text(root.get("_format_version"));
        if (isBlank(formatVersion)) {
            findings.add(new Finding("KG001", Severity.ERROR, "_format_version",
                    "Kong declarative configuration is missing _format_version.",
                    "Declare the configuration format version, for example _format_version: \"3.0\"."));
        } else if (!SUPPORTED_FORMAT_VERSIONS.contains(formatVersion)) {
            findings.add(new Finding("KG002", Severity.ERROR, "_format_version",
                    "Unsupported declarative configuration format version '" + formatVersion + "'.",
                    "Use a format version supported by this GatewayLens rule set: "
                            + String.join(", ", SUPPORTED_FORMAT_VERSIONS) + "."));
        }

        JsonNode servicesNode = root.get("services");
        List<JsonNode> services = entityList(servicesNode, "services", input, findings);
        Set<String> serviceKeys = new HashSet<>();
        Map<String, List<String>> serviceNames = new TreeMap<>();
        for (int i = 0; i < services.size(); i++) {
            JsonNode service = services.get(i);
            String location = "services[" + i + "]";
            if (!service.isObject()) {
                findings.add(new Finding("KG000", Severity.ERROR, location,
                        "Service entry must be an object.", "Represent each service as a YAML mapping."));
                continue;
            }
            String name = text(service.get("name"));
            String id = text(service.get("id"));
            if (isBlank(name)) {
                findings.add(new Finding("KG003", Severity.ERROR, location,
                        "Service is missing a name.", "Give every service a stable, descriptive name."));
            } else {
                serviceNames.computeIfAbsent(name, ignored -> new ArrayList<>()).add(location);
                serviceKeys.add(name);
            }
            if (!isBlank(id)) {
                serviceKeys.add(id);
            }
            if (isBlank(text(service.get("url"))) && isBlank(text(service.get("host")))) {
                findings.add(new Finding("KG004", Severity.ERROR, location,
                        "Service has no upstream target; neither url nor host is declared.",
                        "Declare an upstream url or a host (with any required protocol/port settings)."));
            }
        }
        addDuplicateFindings(serviceNames, "KG008", "Service name", findings);

        List<RouteRecord> routes = new ArrayList<>();
        for (int i = 0; i < services.size(); i++) {
            JsonNode service = services.get(i);
            JsonNode nestedRoutes = service != null && service.isObject() ? service.get("routes") : null;
            List<JsonNode> nested = entityList(nestedRoutes, "services[" + i + "].routes", input, findings);
            for (int j = 0; j < nested.size(); j++) {
                routes.add(new RouteRecord(nested.get(j), "services[" + i + "].routes[" + j + "]", true));
            }
        }

        List<JsonNode> rootRoutes = entityList(root.get("routes"), "routes", input, findings);
        for (int i = 0; i < rootRoutes.size(); i++) {
            routes.add(new RouteRecord(rootRoutes.get(i), "routes[" + i + "]", false));
        }

        Map<String, List<String>> routeNames = new TreeMap<>();
        for (RouteRecord record : routes) {
            JsonNode route = record.route();
            String location = record.location();
            if (route == null || !route.isObject()) {
                findings.add(new Finding("KG000", Severity.ERROR, location,
                        "Route entry must be an object.", "Represent each route as a YAML mapping."));
                continue;
            }
            String name = text(route.get("name"));
            if (isBlank(name)) {
                findings.add(new Finding("KG005", Severity.ERROR, location,
                        "Route is missing a name.", "Give each route a stable, descriptive name."));
            } else {
                routeNames.computeIfAbsent(name, ignored -> new ArrayList<>()).add(location);
            }
            if (!hasMatchCriteria(route)) {
                findings.add(new Finding("KG006", Severity.ERROR, location,
                        "Route has no request-matching criteria.",
                        "Declare at least one match field such as paths, hosts, methods, snis, headers, sources, destinations, or expression."));
            }
            if (!record.nestedUnderService()) {
                checkRootRouteService(route, location, serviceKeys, findings);
            }
        }
        addDuplicateFindings(routeNames, "KG009", "Route name", findings);
        return report(input, findings);
    }

    private static List<JsonNode> entityList(JsonNode node, String location, Path input, List<Finding> findings) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            findings.add(new Finding("KG000", Severity.ERROR, location,
                    "Entity collection must be an array.",
                    "Represent " + location + " as a YAML sequence of objects."));
            return List.of();
        }
        List<JsonNode> values = new ArrayList<>();
        node.forEach(values::add);
        return values;
    }

    private static boolean hasMatchCriteria(JsonNode route) {
        for (String field : ROUTE_MATCH_FIELDS) {
            JsonNode value = route.get(field);
            if (value != null && !value.isNull()) {
                if ((value.isArray() || value.isObject()) && !value.isEmpty()) {
                    return true;
                }
                if (value.isTextual() && !value.asText().isBlank()) {
                    return true;
                }
            }
        }
        return !isBlank(text(route.get("expression")));
    }

    private static void checkRootRouteService(JsonNode route, String location,
                                              Set<String> serviceKeys, List<Finding> findings) {
        JsonNode service = route.get("service");
        if (service == null || service.isNull()) {
            findings.add(new Finding("KG007", Severity.ERROR, location,
                    "Top-level route has no service association.",
                    "Set route.service to a service name or ID, or nest the route under its service."));
            return;
        }
        String reference = service.isTextual() ? service.asText()
                : service.isObject() ? firstNonBlank(text(service.get("name")), text(service.get("id"))) : null;
        if (isBlank(reference) || !serviceKeys.contains(reference)) {
            findings.add(new Finding("KG007", Severity.ERROR, location,
                    "Top-level route references a service that is missing or not declared in this file.",
                    "Reference an existing service by its declared name or ID."));
        }
    }

    private static void addDuplicateFindings(Map<String, List<String>> locationsByName,
                                             String ruleId, String entity, List<Finding> findings) {
        for (Map.Entry<String, List<String>> entry : locationsByName.entrySet()) {
            List<String> locations = entry.getValue();
            if (locations.size() < 2) {
                continue;
            }
            for (String location : locations) {
                List<String> others = locations.stream().filter(other -> !other.equals(location)).toList();
                findings.add(new Finding(ruleId, Severity.ERROR, location,
                        entity + " '" + entry.getKey() + "' is duplicated at " + String.join(", ", others) + ".",
                        "Use unique names for each " + entity.toLowerCase() + " within the declarative configuration."));
            }
        }
    }

    private static CheckReport report(Path input, List<Finding> findings) {
        findings.sort(Comparator.comparing(Finding::location)
                .thenComparing(Finding::ruleId)
                .thenComparing(f -> f.severity().name()));
        return CheckReport.of(input.toString(), findings);
    }

    private static String text(JsonNode node) {
        return node != null && node.isValueNode() ? node.asText() : null;
    }

    private static String firstNonBlank(String first, String second) {
        return !isBlank(first) ? first : second;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record RouteRecord(JsonNode route, String location, boolean nestedUnderService) {
    }
}
