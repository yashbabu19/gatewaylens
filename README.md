# GatewayLens 🔎

**A developer-first linter for OpenAPI contracts and API-gateway onboarding readiness.**

GatewayLens checks API descriptions before they enter a gateway onboarding or deployment workflow. It highlights contract gaps and operations with no declared security requirement, then produces actionable findings in terminal-friendly text or JSON for CI pipelines.

> **Project status:** early MVP. This is a static OpenAPI checker, not an API gateway, runtime security control, or replacement for security review. Passing these checks does not prove an API is secure.

## Why build this?

Gateway onboarding often repeats the same questions: Are operations documented? Are success and failure responses declared? Is security behavior intentional? Can a reviewer get a consistent, machine-readable report before a change moves forward?

GatewayLens starts with a small, deterministic set of checks that can run locally or in CI. The plan is to add gateway-specific policy packs after the core checker and tests are solid.

## Current checks

| Rule | Severity | What it checks |
|---|---|---|
| `GL000` | Error | The OpenAPI document could not be parsed |
| `GL001` | Warning | An operation is missing `operationId` |
| `GL002` | Warning | An operation has neither `summary` nor `description` |
| `GL003` | Error | An operation has no response definitions |
| `GL004` | Warning | An operation has no documented 2xx success response |
| `GL005` | Warning | An operation has no effective security requirement and is not explicitly marked public |
| `GL006` | Info | An operation has no tags |
| `GL007` | Error | The document contains no paths |

Security checks are intentionally declarative: GatewayLens detects whether a security requirement is declared in the OpenAPI document; it does **not** test authentication enforcement at runtime. An endpoint intended to be public can opt out of `GL005` using the explicit vendor extension `x-gatewaylens-public: true`. This is a GatewayLens convention, not an OpenAPI standard.

## Requirements

- Java 21+
- Maven 3.9+
- Internet access on the first build so Maven can download dependencies

## Run it

```bash
# Run the test suite and package the executable JAR
mvn clean verify

# Check the well-described sample API (expected to pass the current rules)
java -jar target/gatewaylens.jar examples/orders-api.yaml

# Check an intentionally incomplete API (expected to report findings and return a non-zero status)
java -jar target/gatewaylens.jar examples/legacy-api.yaml

# Emit machine-readable output for automation
java -jar target/gatewaylens.jar examples/legacy-api.yaml --format json

# Fail the command if any warning or error is found
java -jar target/gatewaylens.jar examples/legacy-api.yaml --fail-on warning
```

### Exit codes

- `0`: no findings reached the selected failure threshold.
- `1`: findings reached the selected threshold.
- `2`: command usage, input file, or report-writing error.

`--fail-on` accepts `error` (default), `warning`, `info`, or `never`.

## Example output

```text
GatewayLens report — examples/legacy-api.yaml
Findings: ... (ERROR: ..., WARNING: ..., INFO: ...)

[WARNING] GL001 — GET /inventory
  Operation is missing operationId.
  Recommendation: Add a stable, unique operationId for code generation, tracing, and tooling.
```

Counts depend on the rules and the exact sample file version; use `--format json` for stable structured consumption.

## Project layout

```text
src/main/java/dev/gatewaylens/  # CLI, policy checker, and report model
src/test/java/dev/gatewaylens/  # automated tests
src/test/resources/              # valid and intentionally public API fixtures
examples/                        # demo specifications
.github/workflows/ci.yml         # Java CI workflow
docs/rules.md                   # rule details and design choices
```

## Roadmap

- [x] Parse OpenAPI 3.x YAML/JSON using Swagger Parser
- [x] Emit deterministic findings with IDs, severity, location, and recommendations
- [x] Support text and JSON output plus configurable failure thresholds
- [x] Unit tests and valid/invalid fixtures
- [ ] Detect duplicate `operationId` values
- [ ] Add a configurable policy file with allowlists and severity overrides
- [ ] Add a SARIF report format for code-scanning workflows
- [ ] Add Kong-specific configuration checks (separate from OpenAPI contract checks)
- [ ] Add a GitHub Actions usage example against a sample API repository
- [ ] Document threat model, limitations, and rule versioning

## Design principles

- **Fail clearly:** each finding has a stable rule ID, severity, location, and remediation.
- **Deterministic:** the same document should produce the same finding order.
- **Explicit public APIs:** public routes should be marked intentionally, rather than silently exempted.
- **Test the rules:** positive, negative, and edge cases belong in tests.
- **Safe examples:** fixtures use synthetic paths and data only.

## References

- [OpenAPI Specification](https://github.com/OAI/OpenAPI-Specification)
- [Swagger Parser](https://github.com/swagger-api/swagger-parser)
- [Kong Gateway](https://github.com/Kong/kong)
- [Spring Cloud Gateway](https://github.com/spring-cloud/spring-cloud-gateway)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). For a first contribution, add a rule with a focused test fixture and explain false-positive trade-offs in the pull request.

## License

MIT. See [LICENSE](LICENSE).
