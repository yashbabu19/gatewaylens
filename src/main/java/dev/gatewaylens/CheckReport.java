package dev.gatewaylens;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Complete report for one OpenAPI document. */
public record CheckReport(
        String file,
        int findingCount,
        Map<Severity, Long> summary,
        List<Finding> findings) {

    public static CheckReport of(String file, List<Finding> findings) {
        Map<Severity, Long> counts = new EnumMap<>(Severity.class);
        for (Severity severity : Severity.values()) {
            counts.put(severity, findings.stream().filter(f -> f.severity() == severity).count());
        }
        return new CheckReport(file, findings.size(), counts, List.copyOf(findings));
    }
}
