# Contributing

Thanks for helping improve GatewayLens.

## Development setup

1. Install Java 21 and Maven 3.9+.
2. Fork or clone the repository.
3. Run `mvn clean verify` before making changes.
4. Add or update a fixture and focused test for every rule change.

## Pull request checklist

- Explain the problem and the false-positive/false-negative trade-off.
- Include a test for both the expected finding and the passing case where practical.
- Keep finding IDs stable and findings deterministic.
- Do not add credentials, private API specifications, real customer data, or employer-confidential configuration.
- Update README/rule documentation when user-facing behavior changes.

## Style

Keep rules small and explainable. Prefer actionable recommendations over generic advice. GatewayLens is currently a static document checker; do not describe it as a runtime security scanner.
