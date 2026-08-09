# cloud-agent-service

Java-native orchestration control plane for ContractSearch Agentic RAG.

Phase 1 intentionally implements only an in-memory mock Tool loop. Search, contract retrieval and compilation adapters are introduced in Phase 1A. Existing AI Chat remains unchanged.

## Safe defaults

```text
AGENT_ENABLED=false
AGENT_ROUTE_ENABLED=false
AGENT_MIGRATIONS_ENABLED=false
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
