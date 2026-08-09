# cloud-agent-service

Java-native orchestration control plane for ContractSearch Agentic RAG.

Phase 1 keeps an in-memory mock Tool loop. Phase 1A adds a default-off, bounded real loop with `search_contracts.v1`, `get_contract.v1` and `compile_contract.v1`. Existing AI Chat remains unchanged.

## Safe defaults

```text
AGENT_ENABLED=false
AGENT_ROUTE_ENABLED=false
AGENT_MIGRATIONS_ENABLED=false
AGENT_MINI_LOOP_ENABLED=false
AGENT_MODEL=glm-4.5-air
```

The API cannot be enabled without an `AGENT_INTERNAL_TOKEN` of at least 24 characters. Gateway removes any caller-supplied internal token and injects its configured value. Direct runtime ports must remain private.

## Local mock startup

Use the same random internal token for Gateway and Agent Service. Do not commit it.

```bash
AGENT_ENABLED=true \
AGENT_INTERNAL_TOKEN='<random-24+-character-token>' \
AGENT_MIGRATIONS_ENABLED=false \
NACOS_DISCOVERY_ENABLED=false \
mvn -pl cloud-agent-service spring-boot:run
```

Default port: `18082`.

Health endpoints:

```text
GET /actuator/health
GET /actuator/metrics
GET /actuator/prometheus
```

Agent endpoints are documented in `docs/agentic-rag/openapi/agent-api-v1.yaml`.

## Database migration

Do not enable migrations against the running database until the disposable fresh and live-clone tests pass. Follow `docs/agentic-rag/migration-runbook.md`.

## Tests

```bash
mvn -pl cloud-agent-service -am test
node scripts/agentic-rag/run-eval.mjs --check
node scripts/agentic-rag/probe-runtime-contracts.mjs
```

The live GLM probe requires `ZHIPU_API_KEY` in the current process:

```bash
node scripts/agentic-rag/probe-glm-contract.mjs
```

Probe output is sanitized and never stores the API key.

## Phase 1A mini loop

Prerequisites: `cloud-service` on port 18081, `contract-runtime-service` on port 19090, and `ZHIPU_API_KEY` exported in the same process that starts Agent Service.

```bash
AGENT_ENABLED=true \
AGENT_MINI_LOOP_ENABLED=true \
AGENT_INTERNAL_TOKEN='<random-24+-character-token>' \
AGENT_MIGRATIONS_ENABLED=false \
NACOS_DISCOVERY_ENABLED=false \
mvn -pl cloud-agent-service spring-boot:run
```

The loop is capped at 6 model steps, 4 Tool calls and 2 search rounds. Dependency URLs are fixed to allowlisted local/service hosts. The model cannot supply URLs, deploy or call a contract, write user data, use Lean, access shell/files, or invoke unregistered tools.

Run the real local Tool adapter integration test without a model call:

```bash
RUN_AGENT_TOOL_INTEGRATION=true mvn -pl cloud-agent-service -Dtest=AgentToolsLiveIntegrationTest test
```
