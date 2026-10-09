# Phase 0 Environment Baseline

> **Historical record — 2026-08-09.** Archived on 2026-10-09.
> This describes that checkpoint, not the current release or current verification status.
> Historical test results and limitations are retained; private run identifiers, backup
> identifiers and host-specific observations have been omitted or generalized.
> See [current project status](../../status.md) before following any operational steps.

> Baseline date: 2026-08-09 (Asia/Shanghai)  
> Scope: Phase 0 baseline, updated after the 2026-08-09 root-layout cleanup

## Repository boundary at the checkpoint

Runnable services were consolidated directly under the repository root. Local
database dumps, archives, credentials, generated data, dependencies and build
output were excluded from version control. Historical local folder names and
machine paths are omitted here.

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

## Historical local service observations

The frontend, database, service discovery, Gateway, main backend and contract
runtime responded to the recorded probes. The Gateway rejected anonymous
protected requests with HTTP 401. The Lean endpoint was reachable, but its proof
probe timed out. Discovery and placeholder auth/user modules were not running.

Nacos reported the Gateway and main backend services. Individual host listener
assignments and machine-specific process observations are omitted. These were
observations at the checkpoint, not a current availability claim.

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
- Therefore direct backend access must remain private. A caller that can reach it can spoof a user ID.
- The frontend at this checkpoint called the local runtime services directly.
- Gateway and backend CORS are broad; the two Node services also enable broad CORS.

## Existing persistence behavior

- Core tables include `smart_contract`, `user_contract_record`, `ai_chat_session`, `ai_chat_message`, and `ai_chat_attachment`.
- No Flyway or Liquibase configuration was found.
- Conversation tables are also created from application startup code, which makes schema evolution difficult to audit.

## Critical Phase 1 prerequisites

1. Pin Solidity and Lean runtime versions. The user confirmed Lean `4.31.0`; Lean must not download a toolchain during a request.
2. Make backend/runtime ports private or require service authentication before exposing Agent tools.
3. Decide and document the local/prod Nginx topology; no Nginx configuration is present in this repository.
4. Introduce versioned database migrations before adding Agent tables.

Discovery credentials have been removed from Git/Nacos configuration and are now loaded from `DUNE_API_KEY` and `ETHERSCAN_API_KEY`. If an earlier copy of the folder was shared, the old provider-side credentials still need revocation/rotation by their owner.
