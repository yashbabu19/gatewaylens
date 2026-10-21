# Built-in rules

GatewayLens rules evaluate the parsed OpenAPI document. The first version aims to flag omissions that make onboarding reviews harder; it deliberately avoids claiming it can prove runtime behavior.

| ID | Default severity | Rule | Notes |
|---|---|---|---|
| GL000 | ERROR | Parse failure | Includes a short parser detail when available. |
| GL001 | WARNING | Missing operation ID | Stable operation IDs improve generated clients and tooling. |
| GL002 | WARNING | Missing summary and description | One of the two fields is enough to pass this baseline check. |
| GL003 | ERROR | Missing responses | Every operation should document expected outcomes. |
| GL004 | WARNING | Missing 2xx response | Accepts status codes `200`–`299` and `2XX`. |
| GL005 | WARNING | No declared security requirement | Checks the operation-level requirement first, then inherited root-level security. It does not exercise auth at runtime. |
| GL006 | INFO | Missing tags | A discoverability hint, not a release blocker by default. |
| GL007 | ERROR | Missing paths | An API document with no paths has nothing to check. |
| GL008 | ERROR | Duplicate operation ID | Reports each operation involved in a collision. OpenAPI operation IDs should uniquely identify operations across the API. |

## Public endpoint convention

An operation can include `x-gatewaylens-public: true` to declare that it is intentionally public. This suppresses only GL005. It does not disable any other checks and is not part of the OpenAPI specification. If the operation explicitly sets `security: []`, it overrides root security for this checker, consistent with OpenAPI's operation-level override behavior.

## Duplicate operation IDs

GL008 groups non-blank `operationId` values across all HTTP operations in the document. When an ID is used more than once, GatewayLens emits an ERROR for each operation in the conflict and lists the other affected operation locations. Missing or blank IDs remain the responsibility of GL001. IDs are compared as declared, using exact case-sensitive string matching.

## Severity and exit behavior

The CLI's `--fail-on` threshold controls the process exit code, not the severity stored in a finding:

- `error`: fail if at least one ERROR is present.
- `warning`: fail if at least one ERROR or WARNING is present.
- `info`: fail if any finding is present.
- `never`: always return success after producing a report, unless command or IO handling fails.

Rule IDs should remain stable. If a rule's semantics materially change, document the change and update its tests.
