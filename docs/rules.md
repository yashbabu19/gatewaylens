# GatewayLens checks

GatewayLens runs static checks over parsed OpenAPI documents (`GLxxx`) or Kong declarative YAML/JSON (`KGxxx`). It does not replace validation with Kong/decK or runtime security testing.

## OpenAPI rules

| ID | Default severity | Rule | Notes |
|---|---|---|---|
| GL000 | ERROR | Parse failure | Includes parser detail when available. |
| GL001 | WARNING | Missing operation ID | Stable operation IDs improve generated clients and tooling. |
| GL002 | WARNING | Missing summary and description | Either field is enough for this baseline check. |
| GL003 | ERROR | Missing responses | Every operation should document expected outcomes. |
| GL004 | WARNING | Missing 2xx response | Accepts status codes `200`–`299` and `2XX`. |
| GL005 | WARNING | No declared security requirement | Checks operation-level security first, then inherited root security; does not exercise auth at runtime. |
| GL006 | INFO | Missing tags | Discoverability hint, not a release blocker by default. |
| GL007 | ERROR | Missing paths | An API document with no paths has nothing to check. |
| GL008 | ERROR | Duplicate operation ID | Reports each operation involved in a collision. |

`x-gatewaylens-public: true` explicitly opts an operation out of GL005. Operation-level `security: []` overrides root security for this checker. GL008 compares declared non-blank IDs exactly, case-sensitively, and reports every operation using a repeated value.

## Kong declarative rules

Select this checker with `--type kong`. The current implementation recognizes these standard core entity shapes but is **not a complete Kong schema validator**. Custom plugin entities and version-specific constraints must also be validated with Kong Gateway or decK.

| ID | Default severity | Rule | Notes |
|---|---|---|---|
| KG000 | ERROR | Parse / basic structure failure | Invalid YAML/JSON, non-object root, non-array core collection, or non-object entity entry. |
| KG001 | ERROR | Missing `_format_version` | The declarative document should declare its format version. |
| KG002 | ERROR | Unsupported format version | This rule set currently recognizes `1.1`, `2.1`, and `3.0`; confirm support with your deployed Kong version. |
| KG003 | ERROR | Service missing name | Services should have a stable descriptive name. |
| KG004 | ERROR | Service missing upstream target | Requires `url` or `host`; further protocol/port validation is out of scope. |
| KG005 | ERROR | Route missing name | Routes should have a stable descriptive name. |
| KG006 | ERROR | Route has no recognized match criterion | Accepts non-empty paths, hosts, methods, SNIs, headers, sources, destinations, or a non-empty expression. |
| KG007 | ERROR | Top-level route service association missing or unresolved | Top-level routes must reference a service by name or ID; nested service routes are treated as associated with their parent. |
| KG008 | ERROR | Duplicate service name | Reports every service participating in a name collision. |
| KG009 | ERROR | Duplicate route name | Reports every route participating in a name collision, including nested and top-level routes. |

Kong's declarative format stores entities such as services and routes as collections. Services represent upstream applications; routes define request-matching criteria and normally associate with a service. GatewayLens checks common onboarding mistakes but does not prove Kong will accept a config.

## Official Kong schema validation

For validation against static schemas and entity relationships supported by decK, run:

```bash
deck file validate examples/kong-declarative.yaml
```

This is an offline check and does not contact a live gateway. GatewayLens and decK serve different purposes: GatewayLens reports opinionated onboarding checks; decK checks its supported declarative state schema and relationships. CI pins a decK version to keep validation reproducible.

## OpenAPI policy configuration

Use `--config path/to/policy.json` with OpenAPI mode to apply `disabledRules`, `severityOverrides`, and `ignoreOperations`. The config is not currently available in Kong mode; CLI exits with `2` if `--config` is provided alongside `--type kong`.

## Exit behavior

The CLI's `--fail-on` threshold controls the exit code, not the severity stored in each finding:

- `error`: exit `1` if any ERROR exists.
- `warning`: exit `1` if any ERROR or WARNING exists.
- `info`: exit `1` if any finding exists.
- `never`: return success after writing the report unless command/config/IO handling fails.

Exit code `2` indicates invalid CLI arguments, an invalid/missing input file, invalid policy config, or report-writing failure. Rule IDs are intended to remain stable as users build automation around them.
