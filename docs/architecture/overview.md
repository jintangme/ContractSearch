# System architecture

ContractSearch connects source discovery, contract inspection, assisted editing
and local validation. The browser presents these tools as a reuse workspace;
Java services own application state and Agent control, while dedicated runtimes
handle Solidity execution and optional Lean checks.

## Services

| Component | Responsibility | Default host port |
|---|---|---:|
| Vue / Vite frontend | Search, contract detail, conversations, precheck and workspace | 5173 |
| Nginx demo | Static frontend and reverse proxy | 8080 |
| `cloud-gateway` | JWT validation, trusted identity headers and routing | 18080 |
| `cloud-service` | Catalog, search, accounts, favorites, drafts and precheck | 18081 |
| `cloud-agent-service` | Agentic RAG, knowledge retrieval, tool control and run persistence | 18082 |
| `contract-runtime-service` | Solidity parsing/compilation and local EVM execution | 19090 |
| `lean-runtime-service` | Optional checking of supplied Lean programs | 19100 |
| `scse-discover` | Optional Dune/Etherscan discovery and file-based import | 18000 |

Host services bind to loopback by default. Container services communicate over
the Compose network. Authentication and profile APIs live in `cloud-service`;
the gateway removes caller-supplied identity headers before forwarding requests.

## Retrieval

Source ingestion normalizes Solidity and multi-file source formats, deduplicates
source bodies, and records provenance. AST extraction produces searchable
declarations; unsupported sources retain lexical/file fallback metadata.

Retrieval combines description semantic search, source BM25, PostgreSQL chunk
text search and pinned reference material with reciprocal rank fusion. Results
retain source locations and trust labels. Description vectors use 768 dimensions;
the public demo contains no precomputed embeddings. Code search can run without
model credentials.

## Agentic RAG

The Agent chooses its next action from the evidence already collected and can
finish as soon as the task is supported. Retrieval, source reading, compilation,
precheck and candidate validation are available capabilities, not a mandatory
sequence for every question. The server enforces tool permissions, argument
schemas, evidence references and per-run budgets.

For a contract modification, the Agent can read a catalog source, propose a
candidate together with executable behavior checks, and freeze those checks
before execution. The runtime compiles the candidate and runs each scenario in
a fresh local EVM. Source and plan hashes bind the returned receipts to the
tested candidate. Candidate revisions preserve the frozen checks; repeated
identical checks reuse their existing receipt. An optional model review can
examine a discrepancy between the task, code and authored checks.

Compilation, declared behavior checks and advisory review have separate result
labels. Agent-authored checks establish only the behaviors they exercise, and
model review adds no execution evidence. See the [Agent service guide](../../backend/cloud-agent-service/README.md)
for the nine tools and budget settings.

Runs execute asynchronously; the frontend polls progress and displays tool
activity and supporting sources. Optional persistence retains terminal runs,
events, evidence and proposals. Active execution is in memory and does not
resume automatically after a service restart.

## Storage and runtimes

The [database bootstrap](../../database/bootstrap/001-schema.sql) creates the
business schema and search indexes. The [demo seed](../../database/bootstrap/002-demo.sql)
adds three original sample contracts. Agent migrations add knowledge and run
tables; use the [migration guide](../agentic-rag/migration-runbook.md) for existing
databases. Discovery imports require a shared directory visible to both services
and a separate internal token.

The Solidity runtime bundles compiler 0.8.35 and can download selected additional
compiler versions. Contract Lab uses a shared, transient local chain; Agent
candidate checks use separate fresh EVM instances. Neither path publishes a
contract to a public blockchain. Lean checking is a separate optional workflow
and is outside the Agent tool allowlist. It checks the supplied Lean model and
does not establish equivalence with Solidity source.

## Further documentation

- [Local quickstart](../development/quickstart.md)
- [Agent mode setup](../development/agent-mode.md)
- [Agent API contract](../agentic-rag/openapi/agent-api-v1.yaml)
- [Knowledge ingestion](../agentic-rag/knowledge-ingestion-runbook.md)
- [Data sources and licenses](../reference/data-sources.md)
