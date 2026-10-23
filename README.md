# GatewayLens 🔎

**A developer-first linter for OpenAPI contracts and API-gateway onboarding readiness.**

GatewayLens checks API descriptions before they enter a gateway onboarding or deployment workflow. It highlights contract gaps and operations with no declared security requirement, and it can statically inspect Kong declarative configuration for common onboarding mistakes. Reports are available as terminal-friendly text or JSON for CI pipelines.

> **Project status:** early MVP. This is a static configuration checker, not an API gateway, runtime security control, or replacement for review against the installed Kong version. Passing its checks does not prove an API or gateway is secure or that Kong will accept every possible configuration.

## Why build this?

Gateway onboarding repeats the same questions: Are operations documented? Are success and failure responses declared? Is security behavior intentional? Do routes have request-matching criteria and point at a declared service? Can a reviewer get a consistent report before a change moves forward?

GatewayLens provides deterministic checks that can run locally or in CI. OpenAPI checks and Kong declarative-config checks are separate modes.

## OpenAPI checks

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

Security checks are declarative: they detect whether a security requirement is specified in OpenAPI, not whether authentication is enforced at runtime. `x-gatewaylens-public: true` is an opt-out convention for GL005, not an OpenAPI standard. GL008 reports each operation participating in a duplicate `operationId` collision.

## Kong declarative configuration checks

Run with `--type kong`. The Kong mode reads declarative YAML or JSON and applies these initial checks:

| Rule | Default severity | What it checks |
|---|---|---|
| `KG000` | Error | Input cannot be parsed or an entity collection/entry has an invalid basic shape |
| `KG001` | Error | Missing `_format_version` |
| `KG002` | Error | Format version is outside this rule set's supported values (`1.1`, `2.1`, `3.0`) |
| `KG003` | Error | A service is missing its name |
| `KG004` | Error | A service declares neither `url` nor `host` as an upstream target |
| `KG005` | Error | A route is missing its name |
| `KG006` | Error | A route has no recognized request-matching criterion |
| `KG007` | Error | A top-level route has no declared service association or references an undeclared service |
| `KG008` | Error | Duplicate service names |
| `KG009` | Error | Duplicate route names |

These are opinionated static checks, not full validation against Kong's schema: custom entities/plugins and additional version-specific semantics may require Kong or decK itself to validate. Nested routes under a service inherit that association; top-level routes must declare a service reference.

## Requirements

- Java 21+
- Maven 3.9+
- Internet access on the first build so Maven can download dependencies

## Run it

```bash
# Run the test suite and package the executable JAR
mvn clean verify

# Check an OpenAPI contract (default mode)
java -jar target/gatewaylens.jar examples/orders-api.yaml

# Apply custom OpenAPI policy settings
java -jar target/gatewaylens.jar examples/legacy-api.yaml --config examples/gatewaylens-policy.json

# Check a Kong declarative configuration
java -jar target/gatewaylens.jar examples/kong-declarative.yaml --type kong

# Check an intentionally invalid Kong config (findings expected)
java -jar target/gatewaylens.jar examples/kong-invalid.yaml --type kong --fail-on never

# Emit machine-readable output
java -jar target/gatewaylens.jar examples/kong-invalid.yaml --type kong --format json --fail-on never
```

## Configurable OpenAPI policies

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

- `disabledRules` suppresses findings for specified rule IDs.
- `severityOverrides` changes reported severity to `ERROR`, `WARNING`, or `INFO`.
- `ignoreOperations` suppresses findings for exact `METHOD /path` entries.

All fields are optional. Unknown fields/rules, invalid severities, and malformed operation locations result in a configuration error (exit code `2`). OpenAPI policy configuration cannot currently be combined with `--type kong`.

## Exit codes

- `0`: no findings reached the selected failure threshold.
- `1`: findings reached the selected threshold.
- `2`: command usage, input file, configuration, or report-writing error.

`--fail-on` accepts `error` (default), `warning`, `info`, or `never`.

## Project layout

```text
src/main/java/dev/gatewaylens/  # CLI, policy config, OpenAPI and Kong checkers
src/test/java/dev/gatewaylens/  # automated tests
src/test/resources/              # OpenAPI test fixtures
examples/                        # demo OpenAPI/Kong specs and sample policy config
.github/workflows/ci.yml         # Java CI workflow
docs/rules.md                   # rule details and design choices
```

## Roadmap

- [x] Parse OpenAPI 3.x YAML/JSON using Swagger Parser
- [x] Deterministic findings with rule IDs, severity, locations, and remediation
- [x] Text/JSON output and configurable failure thresholds
- [x] Duplicate `operationId` detection (`GL008`)
- [x] JSON policy configuration for OpenAPI rules
- [x] Initial Kong declarative YAML/JSON static checks
- [ ] Validate against versioned Kong schemas / decK
- [ ] Add plugin-specific security and traffic-policy checks
- [ ] Add SARIF output for code-scanning workflows
- [ ] Add GitHub Actions examples for external API repositories
- [ ] Document threat model, limitations, and rule versioning

## References

- [Kong declarative configuration](https://docs.konghq.com/gateway/latest/admin-api/declarative-configuration/)
- [Kong DB-less declarative configuration](https://docs.konghq.com/gateway/latest/production/deployment-topologies/db-less-and-declarative-config/)
- [Kong services and routes](https://docs.konghq.com/gateway/latest/get-started/services-and-routes/)
- [OpenAPI Specification](https://github.com/OAI/OpenAPI-Specification)
- [Swagger Parser](https://github.com/swagger-api/swagger-parser)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). For a first contribution, add a focused rule test and discuss false-positive trade-offs in the pull request.

## License

MIT. See [LICENSE](LICENSE).
