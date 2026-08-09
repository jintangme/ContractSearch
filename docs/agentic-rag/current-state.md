# ContractSearch Current State

> Baseline date: 2026-08-09 (Asia/Shanghai)  
> Scope: Phase 0, read-only repository and local runtime audit

## Repository boundary

The effective project root is `计算机设计大赛2.0/`, because the Java/Vue applications live under `代码/` while the Solidity and Lean runtimes are sibling directories.

```text
计算机设计大赛2.0/
├── 代码/
│   ├── springcloud-scse/       Java 17 Maven reactor
│   ├── scse_front_end/         Vue 3 + Vite
│   ├── scse-discover/          Python/FastAPI import utility
│   └── pg18.sql                database dump baseline
├── contract-runtime-service/   Node/Express Solidity runtime
├── lean-runtime-service/       Node/Express Lean checker
└── plan.md
```

No Git worktree was detected at the project root or its main child projects. Creating a repository and baseline commit requires explicit user approval and remains a Phase 0 blocker.

## Build and runtime versions

| Component | Observed version/state |
|---|---|
| Java | OpenJDK 17.0.18 |
| Maven | 3.9.15 |
| Spring Boot | 3.5.13 |
| Spring Cloud | 2025.0.0 |
| Spring Cloud Alibaba | 2025.0.0.0 |
| Node.js / npm | 22.23.2 / 10.9.8 |
| Vue | 3.4.x |
| Vite | 5.x |
| PostgreSQL | 18.3 |
| ParadeDB `pg_search` | 0.23.0 |
| `pgvector` | 0.8.1 |
| installed `solc` | 0.8.35 (package range and README still imply 0.8.30) |
| ethers / Ganache | 6.16.0 / 7.9.2 |
| host Lean | 4.30.0 |
| Lean runtime probe | attempted to download 4.32.2 and timed out after 10 seconds |

## Running local topology

Observed listeners and probes:

| Port | Process/component | Probe result |
|---:|---|---|
| 5173 | Vue/Vite | HTTP 200 |
| 5432 | ParadeDB Docker container | PostgreSQL query succeeded |
| 8848 | Nacos 3.0.3 Docker container | HTTP 200 |
| 18080 | Spring Cloud Gateway | protected request returned 401 as expected |
| 18081 | `smart-contract-search-engine` | HTTP and OpenAPI available |
| 19090 | contract runtime | compile probe passed |
| 19100 | Lean runtime | endpoint reachable; proof probe timed out |
| 18000 | discovery service | not running |
| 18083/18084 | user/auth placeholder modules | not running and not registered |

Nacos reported two services: `cloud-gateway` and `smart-contract-search-engine`.

## Existing AI and search behavior

- Existing AI Chat uses the Zhipu OpenAI-compatible endpoint and defaults to `glm-5.1`.
- `AiModelRegistry` already allowlists `glm-4.5-air`.
- A live minimal call using `glm-4.5-air` returned `OK`, with provider/model fields correctly reporting `zhipu` and `glm-4.5-air`.
- Existing semantic search combines description BM25 and description vector search with RRF.
- Existing code search uses source-code BM25 only; code vector search is commented out.
- Search ignores the request `limit` and may return the hard-coded maximum of 100 results.
- `ContractCardVo` does not expose the computed RRF score in the observed API response.
- Semantic smoke results require a quality evaluation: `SimpleStoragePlus` ranked first for two unrelated English queries.

## Authentication and trust boundary

- Gateway verifies JWT, removes a caller-supplied `X-User-Id`, and injects its own user ID.
- Backend accepts `X-User-Id` directly and creates an authenticated Spring Security context from it.
- Therefore port 18081 must not be publicly reachable. A caller that can reach it can spoof a user ID.
- Local frontend currently calls the runtime services directly on ports 19090 and 19100.
- Gateway and backend CORS are broad; the two Node services also enable broad CORS.

## Existing persistence behavior

- Core tables include `smart_contract`, `user_contract_record`, `ai_chat_session`, `ai_chat_message`, and `ai_chat_attachment`.
- No Flyway or Liquibase configuration was found.
- Conversation tables are also created from application startup code, which makes schema evolution difficult to audit.

## Critical Phase 1 prerequisites

1. User-approved Git root and recoverable baseline commit.
2. Revoke and rotate the third-party API keys currently committed in `scse-discover.yaml`; replace them with environment/secret references.
3. Pin Solidity and Lean runtime versions. Lean must not download a toolchain during a request.
4. Make backend/runtime ports private or require service authentication before exposing Agent tools.
5. Decide and document the local/prod Nginx topology; no Nginx configuration is present in this repository.
6. Introduce versioned database migrations before adding Agent tables.

