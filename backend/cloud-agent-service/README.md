# Agent service

Java orchestration for ContractSearch Agentic RAG. The Agent selects tools from
the current evidence and may finish early. The server validates actions,
enforces budgets and binds generated code to actual tool receipts.

## Setup

Use the repository's [Agent mode guide](../../docs/development/agent-mode.md)
for the Compose workflow, model configuration and demo knowledge setup. Agent
features are disabled by default. The service requires a shared
`AGENT_INTERNAL_TOKEN` of at least 24 characters when enabled; the gateway
injects this token and removes any caller-supplied value.

The default model is `glm-4.5-air` at the Zhipu endpoint. Configure
`AGENT_MODEL`, `ZHIPU_BASE_URL` and `ZHIPU_API_KEY` in your ignored environment.
An alternative endpoint must support the structured chat/tool request format
used by the provider client, including its `thinking` parameter.

Default port: `18082`. Health and metrics:

```text
GET /actuator/health
GET /actuator/metrics
GET /actuator/prometheus
```

See the [API contract](../../docs/agentic-rag/openapi/agent-api-v1.yaml) and
[database migration guide](../../docs/agentic-rag/migration-runbook.md).

## Tools

| Tool | Purpose |
|---|---|
| `search_contracts.v1` | Search catalog contracts |
| `retrieve_knowledge.v1` | Retrieve source chunks and reference knowledge |
| `get_contract.v1` | Read catalog source and metadata |
| `compile_contract.v1` | Compile an existing catalog contract |
| `compile_source.v1` | Compile exact supplied source without behavior checks |
| `precheck_contract.v1` | Run a read-only source precheck |
| `plan_reuse.v1` | Freeze checks for a fully read source; optionally validate the first candidate |
| `validate_candidate.v1` | Compile and execute a candidate against the frozen checks |
| `review_candidate.v1` | Request an optional advisory review of a checked candidate and its checks |

The model cannot choose arbitrary service URLs, run shell commands, access
files, invoke Lean, publish contracts or modify user records. Candidate execution
uses a fresh local EVM; it does not deploy to the user's Contract Lab session or
a public chain. Precheck uses `saveRecord=false`.

## Control and receipts

Default limits are six model calls/decisions, five tool calls and two retrieval
rounds, configured by `AGENT_MAX_STEPS`, `AGENT_MAX_TOOL_CALLS` and
`AGENT_MAX_RETRIEVAL_ROUNDS`. Server hard caps are six, eight and three,
respectively. Model retries and advisory review consume the same model budget,
with capacity reserved for the final answer.

Tools become available when their prerequisites are met. A source must be fully
read before freezing a reuse plan; validation requires a plan; advisory review
requires a checked candidate. These steps are optional for tasks that do not
need them. Candidate limits and execution timeouts also bound work within a run.

The server pins source and plan hashes before checks execute. Candidate revisions
preserve existing checks, and an identical source/plan pair reuses its receipt.
Compilation receipts separately record the exact source and selected compiler.
Final delivery preserves these distinctions: compilation only, declared behavior
checks, unresolved checks or an unexecuted draft. Model review is advice, not a
correctness certificate.

Retrieved text and tool content are treated as untrusted data. Responses cite
collected evidence or state what remains unsupported. Passing Agent-authored
checks does not establish correctness outside those checks.

## Persistence and tests

Apply the migrations before setting `AGENT_PERSISTENCE_ENABLED=true`. Configure
`AGENT_PERSISTENCE_DB_URL`, `AGENT_PERSISTENCE_DB_USERNAME` and
`AGENT_PERSISTENCE_DB_PASSWORD`, or their `AGENT_DB_*` fallbacks. Persistence
retains terminal runs, events, evidence and proposals; interrupted active runs
are not resumable.

From the repository root:

```bash
mvn -f backend/pom.xml -pl cloud-agent-service -am test
```

With the business service and Solidity runtime running on their configured
local URLs, the tool adapter integration test makes no model calls:

```bash
RUN_AGENT_TOOL_INTEGRATION=true mvn -f backend/pom.xml -pl cloud-agent-service -Dtest=AgentToolsLiveIntegrationTest test
```
