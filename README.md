# GatewayLens 🔎

**A developer-first linter for OpenAPI contracts and API-gateway onboarding readiness.**

GatewayLens checks API descriptions before they enter a gateway onboarding or deployment workflow. It highlights contract gaps and operations with no declared security requirement, then produces actionable findings in terminal-friendly text or JSON for CI pipelines.

> **Project status:** early MVP. This is a static OpenAPI checker, not an API gateway, runtime security control, or replacement for security review. Passing these checks does not prove an API is secure.

## Why build this?

Gateway onboarding often repeats the same questions: Are operations documented? Are success and failure responses declared? Is security behavior intentional? Can a reviewer get a consistent, machine-readable report before a change moves forward?

GatewayLens starts with a small, deterministic set of checks that can run locally or in CI. The plan is to add gateway-specific policy packs after the core checker and tests are solid.

## Current checks

| Rule | Default severity | What it checks |
|---|---|---|
| `GL000` | Error | The OpenAPI document could not be parsed |
| `GL001` | Warning | An operation is missing `operationId` |
| `GL002` | Warning | An operation has neither `summary` nor `description` |
| `GL003` | Error | An operation has no response definitions |
| `GL004` | Warning | An operation has no documented 2xx success response |
| `GL005` | Warning | An operation has no effective security requirement and is not explicitly marked public |
| `GL006` | Info | An operation has no tags |
| `GL007` | Error | The document contains no paths |
| `GL008` | Error | An `operationId` is used by more than one operation |

Security checks are intentionally declarative: GatewayLens detects whether a security requirement is declared in the OpenAPI document; it does **not** test authentication enforcement at runtime. An endpoint intended to be public can opt out of `GL005` using the explicit vendor extension `x-gatewaylens-public: true`. This is a GatewayLens convention, not an OpenAPI standard.

`GL008` reports each operation participating in a duplicate `operationId` collision and identifies the other conflicting locations. Operation IDs are compared using exact, case-sensitive string matching.

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

# Check an intentionally incomplete API (expected to report findings)
java -jar target/gatewaylens.jar examples/legacy-api.yaml

# Emit machine-readable output for automation
java -jar target/gatewaylens.jar examples/legacy-api.yaml --format json

# Fail the command if any warning or error is found
java -jar target/gatewaylens.jar examples/legacy-api.yaml --fail-on warning

# Apply custom policy settings from a JSON file
java -jar target/gatewaylens.jar examples/legacy-api.yaml --config examples/gatewaylens-policy.json
```

## Configurable policies

GatewayLens can load a JSON configuration with `--config`. With no config argument, the existing built-in behavior is unchanged.

```json
{
  "disabledRules": ["GL006"],
  "severityOverrides": {
    "GL005": "ERROR",
    "GL001": "INFO"
  },
  "ignoreOperations": ["POST /inventory"]
}
```

- **`disabledRules`** removes findings produced by those rule IDs.
- **`severityOverrides`** changes a rule's reported severity to `ERROR`, `WARNING`, or `INFO`; the CLI `--fail-on` threshold then uses that adjusted severity.
- **`ignoreOperations`** suppresses all findings for exact `METHOD /path` locations, such as `GET /health`. HTTP methods are case-insensitive in the config; paths match exactly and remain case-sensitive.

All fields are optional. Unknown fields, unknown rule IDs, invalid severity names, or malformed operation locations cause a configuration error and exit code `2` instead of silently ignoring typos. Rule IDs are validated against the supported built-in rules (`GL000`–`GL008`). Disabled rules take precedence over severity overrides, and operation ignores suppress findings regardless of rule.

A sample configuration is provided at [`examples/gatewaylens-policy.json`](examples/gatewaylens-policy.json). Use it to see how a team can reduce informational noise while making missing declared security requirements release-blocking.

## Exit codes

- `0`: no findings reached the selected failure threshold.
- `1`: findings reached the selected threshold.
- `2`: command usage, input file, policy config, or report-writing error.

`--fail-on` accepts `error` (default), `warning`, `info`, or `never`.

## Try duplicate `operationId` detection

```bash
java -jar target/gatewaylens.jar src/test/resources/duplicate-operationid-openapi.yaml --fail-on never
```

This fixture intentionally uses `listOrders` for two different operations. GatewayLens should report `GL008` against both `GET /orders` and `GET /orders/{orderId}`.

## Project layout

```text
src/main/java/dev/gatewaylens/  # CLI, policy config, checker, report model
src/test/java/dev/gatewaylens/  # automated tests
src/test/resources/              # valid and intentionally failing OpenAPI fixtures
examples/                        # demo specifications and sample policy config
.github/workflows/ci.yml         # Java CI workflow
docs/rules.md                   # rule details and design choices
```

## Roadmap

- [x] Parse OpenAPI 3.x YAML/JSON using Swagger Parser
- [x] Emit deterministic findings with IDs, severity, location, and recommendations
- [x] Support text and JSON output plus configurable failure thresholds
- [x] Unit tests and valid/invalid fixtures
- [x] Detect duplicate `operationId` values (`GL008`)
- [x] Load optional JSON policy config with disabled rules, severity overrides, and operation ignores
- [ ] Add a SARIF report format for code-scanning workflows
- [ ] Add Kong-specific configuration checks (separate from OpenAPI contract checks)
- [ ] Add a GitHub Actions usage example against a sample API repository
- [ ] Document threat model, limitations, and rule versioning

## Design principles

- **Fail clearly:** each finding has a stable rule ID, severity, location, and remediation.
- **Deterministic:** the same document and policy should produce the same finding order.
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
