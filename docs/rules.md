# Built-in rules

GatewayLens rules evaluate the parsed OpenAPI document. The first version aims to flag omissions that make onboarding reviews harder; it deliberately avoids claiming it can prove runtime behavior.

| ID | Default severity | Rule | Notes |
|---|---|---|---|
| GL000 | ERROR | Parse failure | Includes a short parser detail when available. |
| GL001 | WARNING | Missing operation ID | Stable operation IDs improve generated clients and tooling. Uniqueness checking is planned. |
| GL002 | WARNING | Missing summary and description | One of the two fields is enough to pass this baseline check. |
| GL003 | ERROR | Missing responses | Every operation should document expected outcomes. |
| GL004 | WARNING | Missing 2xx response | Accepts status codes `200`–`299` and `2XX`. |
| GL005 | WARNING | No declared security requirement | Checks the operation-level requirement first, then inherited root-level security. It does not exercise auth at runtime. |
| GL006 | INFO | Missing tags | A discoverability hint, not a release blocker by default. |
| GL007 | ERROR | Missing paths | An API document with no paths has nothing to check. |

## Public endpoint convention

An operation can include `x-gatewaylens-public: true` to declare that it is intentionally public. This suppresses only GL005. It does not disable any other checks and is not part of the OpenAPI specification. If the operation explicitly sets `security: []`, it overrides root security for this checker, consistent with OpenAPI's operation-level override behavior.

## Severity and exit behavior

The CLI's `--fail-on` threshold controls the process exit code, not the severity stored in a finding:

- `error`: fail if at least one ERROR is present.
- `warning`: fail if at least one ERROR or WARNING is present.
- `info`: fail if any finding is present.
- `never`: always return success after producing a report, unless command or IO handling fails.

Rule IDs should remain stable. If a rule's semantics materially change, document the change and update its tests.
