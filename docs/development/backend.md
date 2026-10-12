# Backend development

The default Maven reactor builds three Java 17 services:

| Module | Role | Default bind |
| --- | --- | --- |
| `cloud-gateway` | Validates JWTs, establishes the user header, forwards public API requests | `127.0.0.1:18080` |
| `cloud-service` | Contracts, search, identity, favorites, conversations, precheck | `127.0.0.1:18081` |
| `cloud-agent-service` | Bounded agent loop, tool adapters, knowledge ingestion, evidence and audit | `127.0.0.1:18082` |

`cloud-auth-service` and `cloud-user-service` are optional scaffolds excluded from the normal build. Identity and user features live in `cloud-service`.

The main business entry point is `org.scse.cloudservice.CloudServiceApplication`; it scans the `org.scse.smartcontractsearchengine` business packages and MyBatis mappers. There is one application entry point per active module.

## Clean database setup

Use the disposable local ParadeDB database from the [quickstart](quickstart.md).
Compose runs the bootstrap files automatically when its database volume is
first initialized. To reapply them to that demo database after `db` is healthy,
run from the repository root:

```bash
docker compose exec -T db psql -U contractsearch -d contractsearch_demo -v ON_ERROR_STOP=1 < database/bootstrap/001-schema.sql
docker compose exec -T db psql -U contractsearch -d contractsearch_demo -v ON_ERROR_STOP=1 < database/bootstrap/002-demo.sql
```

The bootstrap schema contains all core business tables, `pg_search`/`vector` extensions, the source/description BM25 index, and 768-dimensional vector indexes. It was verified on PostgreSQL 18.3, pg_search 0.23.0 and pgvector 0.8.1, including repeated initialization and English source/Chinese description queries. It contains no production records or user accounts. The demo file provides three original Solidity examples (SPDX `UNLICENSED`, pending the project license decision) with ABIs checked against solc 0.8.35 and explicit `demo:*` identifiers; these identifiers are not Ethereum addresses. Register your own local test user through the UI. The examples contain no embeddings: browsing and code search work without model credentials; semantic search needs matching document and query embeddings.

The SQL is intended for clean installations and can be reapplied to the same demo database without duplicating its data. It is not a migration tool for an unknown existing database. Do not apply it to a production database or replace it with a private SQL dump.

Agent migrations remain in `cloud-agent-service/src/main/resources/db/migration/`. V1–V6 add agent/knowledge tables and are compatible with the bootstrap conversation/draft tables. For host-run services, enable `AGENT_MIGRATIONS_ENABLED` for one configured agent instance, then turn it off after successful provisioning. The Compose demo sets this flag to `true` in its Agent service and checks the additive migrations at startup; changing the root `.env` flag does not override that Compose setting. See the [migration runbook](../agentic-rag/migration-runbook.md).

For the original demo corpus, start the Agent with `AGENT_KNOWLEDGE_ENABLED=true` and migrations applied; `AGENT_ENABLED` and `AGENT_MINI_LOOP_ENABLED` can remain false. With the Agent and contract runtime running locally:

```bash
python3 scripts/with-env.py python3 scripts/seed-demo-knowledge.py
```

The script checks the original source hashes before writing, follows ingestion checkpoints, and verifies that all knowledge chunks have search rows without orphans or duplicate identities. It refuses a catalog that differs from the three original examples, permits only a loopback Agent URL, and makes no model calls. Repeated runs preserve the three sources and six AST chunks. Use a new corpus version when intentionally changing the example source; knowledge corpus versions are immutable.

Some business services run additive startup DDL; the bootstrap already contains those columns. `LEGACY_CONVERSATION_DDL_ENABLED=false` disables conversation startup DDL after provisioning.

## Network and configuration boundaries

Nacos discovery and configuration are disabled by default. Enable `NACOS_DISCOVERY_ENABLED` and/or `NACOS_CONFIG_ENABLED` explicitly if using Nacos. Gateway defaults to the local business service; override `CONTRACT_SERVICE_GATEWAY_URI` for containers or discovery.

`GATEWAY_HOST`, `CONTRACT_SERVICE_HOST`, and `AGENT_SERVICE_HOST` default to loopback. A container needs `0.0.0.0` to accept requests on its internal network; publish only the gateway or reverse proxy, not the business or agent service ports. The business service trusts the gateway-provided `X-User-Id`, so it must remain on a private network. The gateway strips external identity and internal-token headers before forwarding, and injects identity only after validating a JWT.

The optional local embedding client is configured with `LOCAL_EMBEDDING_BASE_URL` (default `http://127.0.0.1:8000/v1/`) and `LOCAL_EMBEDDING_MODEL` (default `Qwen/Qwen3-Embedding-4B`). Supply the model ID served by your own endpoint; the repository does not assume a personal filesystem path. Corpus ingestion and query encoding must use compatible embedding models and dimensions.

### Offline discovery import

The callback `POST /api/contracts/process` is an internal file import operation. The gateway rejects it regardless of a caller's JWT. Point the discovery service directly at the private business service and configure:

| Setting | Requirement |
| --- | --- |
| `CONTRACT_IMPORT_TOKEN` | Shared random token with at least 24 characters; not a user JWT |
| `CONTRACT_IMPORT_ROOT` | Directory containing the Parquet files, as visible to `cloud-service` |
| `X-Contract-Import-Token` | Request header carrying the shared token |

Import is disabled when the token/root is unset or the token is too short. Authentication precedes filesystem resolution. Only existing `.parquet` files whose resolved path stays inside the configured root are accepted; traversal, remote URLs, and symlink escapes are rejected. Mount the same directory at the same absolute path in discovery and business containers. Keep that directory writable only by trusted ingestion operators. No raw import token is logged.

## Tests

From `backend/`:

```bash
# Unit/controller/agent tests, no real database required:
mvn test
```

The gateway tests cover spoofed identity, invalid JWTs, authenticated identity replacement, and blocked imports. Callback tests verify authentication before processing, disabled defaults, accepted files, traversal/symlink rejection, missing files, and remote-URI rejection. Test modules use the subclass Mockito mock maker, avoiding dynamic JVM agent attachment.

`CloudServiceApplicationTests` is disabled unless `RUN_BUSINESS_DB_INTEGRATION=true`. It loads the full business application and therefore executes legacy startup DDL. Run it only against an explicitly configured disposable database:

```bash
RUN_BUSINESS_DB_INTEGRATION=true \
SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:<test-port>/<test-database>' \
POSTGRESQL_DB_USERNAME='<test-user>' POSTGRESQL_DB_PASSWORD='<test-password>' \
mvn -pl cloud-service -Dtest=CloudServiceApplicationTests test
```

Do not point integration tests at a working or production database. Agent migration and live-tool integration tests are opt-in via `AGENT_MIGRATION_TEST_URL` and `RUN_AGENT_TOOL_INTEGRATION`; see the agent README for their requirements.
