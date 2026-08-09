# cloud-agent-service

Java-native orchestration control plane for ContractSearch Agentic RAG.

The bounded iterative loop uses GLM to reassess the task after every Tool observation. It can retrieve the curated knowledge base, search/read contracts, compile in the isolated runtime, and run a read-only AI precheck. A final answer must cite collected Evidence or explicitly abstain.

## Safe defaults

```text
AGENT_ENABLED=false
AGENT_ROUTE_ENABLED=false
AGENT_MIGRATIONS_ENABLED=false
AGENT_MINI_LOOP_ENABLED=false
AGENT_PERSISTENCE_ENABLED=false
AGENT_MODEL=glm-4.5-air
```

The API cannot be enabled without an `AGENT_INTERNAL_TOKEN` of at least 24 characters. Gateway removes any caller-supplied internal token and injects its configured value. Direct runtime ports must remain private.

## Local startup

Use the same random internal token for Gateway and Agent Service. Do not commit it.

```bash
AGENT_ENABLED=true \
AGENT_MINI_LOOP_ENABLED=true \
AGENT_INTERNAL_TOKEN='<random-24+-character-token>' \
ZHIPU_API_KEY='<configured-locally>' \
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

## Iterative Agent loop

Prerequisites: `cloud-service` on port 18081, `contract-runtime-service` on port 19090, and `ZHIPU_API_KEY` exported in the same process that starts Agent Service.

```bash
AGENT_ENABLED=true \
AGENT_MINI_LOOP_ENABLED=true \
AGENT_INTERNAL_TOKEN='<random-24+-character-token>' \
AGENT_MIGRATIONS_ENABLED=false \
NACOS_DISCOVERY_ENABLED=false \
mvn -pl cloud-agent-service spring-boot:run
```

The loop is capped at 6 model steps, 5 Tool calls and 2 retrieval rounds. When the Tool budget is exhausted, the final model turn receives no Tool definitions and must complete with current Evidence or abstain. Its allowlist is:

- `retrieve_knowledge.v1`
- `search_contracts.v1`
- `get_contract.v1`
- `compile_contract.v1`
- `precheck_contract.v1`

Dependency URLs are fixed to allowlisted local/service hosts. The model cannot supply URLs, deploy or call a contract, write user data, use Lean, access shell/files, or invoke unregistered tools. An identical Tool invocation is rejected as a loop. The precheck adapter sets `saveRecord=false`, so Agent validation does not pollute the user's standalone precheck history.

## Audit persistence

After applying migrations through `V6`, enable terminal run/event/evidence/proposal persistence with:

```bash
AGENT_PERSISTENCE_ENABLED=true \
AGENT_PERSISTENCE_DB_URL='jdbc:postgresql://127.0.0.1:5432/<database>' \
AGENT_PERSISTENCE_DB_USERNAME='<username>' \
AGENT_PERSISTENCE_DB_PASSWORD='<password>' \
...
```

Persistence is default-off. When enabled, URL and username are validated at startup. Provider raw responses and secrets are never stored; `model.action` records only the action type, concise reason, evidence gap, cited Evidence IDs, stop reason, and provider name.

Run the real local Tool adapter integration test without a model call:

```bash
RUN_AGENT_TOOL_INTEGRATION=true mvn -pl cloud-agent-service -Dtest=AgentToolsLiveIntegrationTest test
```
