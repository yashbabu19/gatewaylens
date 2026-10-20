package dev.gatewaylens;

/** A deterministic, machine-readable policy or contract finding. */
public record Finding(
        String ruleId,
        Severity severity,
        String location,
        String message,
        String recommendation) {
}
