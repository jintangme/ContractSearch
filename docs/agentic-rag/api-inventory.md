# API Inventory and Agent Reuse Map

> Generated from the running Spring OpenAPI document plus runtime source inspection. The Spring service exposes 33 paths and 42 operations.

## P0 read and analysis candidates

| Existing API | Auth/current behavior | Proposed Tool |
|---|---|---|
| `POST /api/search` | authenticated POST | `search_contracts.v1` |
| `GET /api/contracts/{id}` | public GET, optional user context | `get_contract.v1` |
| `POST /api/contracts/check` | public POST | `security_precheck.v1` |
| `GET /api/groups` | public GET | retrieval filter metadata |
| `GET /api/groups/{groupId}/contracts` | public GET | retrieval filter metadata |
| `POST :19090/api/runtime/compile` | direct, no service auth | `compile_contract.v1` after hardening |
| `POST :19100/api/lean/check` | direct, no service auth | `lean_check.v1`, disabled until version pinning |

## Ephemeral execution APIs

| API | Current concern | Earliest phase |
|---|---|---|
| `POST :19090/api/runtime/deploy` | shared Ganache and signer; in-memory ownership | Phase 4 after isolation |
| `POST :19090/api/runtime/call` | caller role cannot be selected safely | Phase 4 after isolation |

## User-scoped context and write APIs

| API group | Operations | Agent policy |
|---|---|---|
| Conversation | chat, list/get/delete sessions, parse attachment | read context only first; delete never autonomous |
| Drafts | create/list/get/delete | create only through approved proposal; delete forbidden |
| Submissions | create/list/get/delete | explicit user approval required |
| Favorites/folders | add/remove/list/create/rename/delete | reads allowed; mutations require explicit user action |
| Profile/avatar/password | read/update/delete | not Agent tools |
| Contract uploads | create/list/delete | proposal/approval required; delete forbidden |

## Existing general AI APIs

- `POST /api/ai/chat`: synchronous, public, 60-second client timeout.
- `GET /api/contracts/{id}/ai-explain`: public GET but may invoke a paid model and can update missing ABI indirectly through contract detail retrieval.
- `POST /api/contracts/check`: synchronous GLM precheck.
- `POST /api/conversation/chat`: authenticated and persistent.

These endpoints remain legacy fallbacks. Agent orchestration must not be implemented by recursively calling untyped AI Chat.

## Contract mismatches discovered

1. `POST /api/search` accepts `limit`, but the service currently returns up to a hard-coded 100 items.
2. Search computes RRF score internally, but the observed card response does not expose it.
3. The Gateway allows public `GET /api/search`, while the implemented search operation is POST and is therefore protected.
4. Direct backend access accepts a caller-provided `X-User-Id`; backend port isolation is a security requirement.
5. Runtime root paths return 404 and there are no `/health` endpoints.
6. The discovery callback `/api/contracts/process` is public and must receive service authentication before external ingestion is expanded.

## Phase 1A allowlist

Only these Tool contracts are allowed in the minimal Agentic RAG validation:

```text
search_contracts.v1  -> POST /api/search
get_contract.v1      -> GET /api/contracts/{id}
compile_contract.v1  -> POST /api/runtime/compile
```

No persistent write, deploy, call, Lean, shell, filesystem or arbitrary URL tool is allowed.

