# Knowledge ingestion

The ingestion service indexes public catalog sources (`uploader_id IS NULL`)
into a configured, immutable knowledge corpus. It normalizes and deduplicates
source, records provenance, extracts AST or fallback chunks, and maintains
PostgreSQL text-search rows. Ingestion makes no model or embedding calls.

## Prerequisites

Apply the [Agent migrations](migration-runbook.md), start the Solidity runtime,
and configure the Agent's database and internal token. For the supplied demo,
use the [Agent setup guide](../development/agent-mode.md) and its seed script.
For another catalog, use a distinct corpus version:

```dotenv
AGENT_ENABLED=false
AGENT_INTERNAL_TOKEN=<at-least-24-random-characters>
AGENT_KNOWLEDGE_ENABLED=true
AGENT_KNOWLEDGE_DB_URL=jdbc:postgresql://127.0.0.1:5432/<validated-target>
AGENT_KNOWLEDGE_DB_USERNAME=<restricted-role>
AGENT_KNOWLEDGE_DB_PASSWORD=<secret>
AGENT_KNOWLEDGE_CORPUS_NAME=contractsearch
AGENT_KNOWLEDGE_CORPUS_VERSION=catalog-v1
AGENT_KNOWLEDGE_MAX_BATCH_SIZE=50
AGENT_MIGRATIONS_ENABLED=false
```

The database role needs catalog read access and knowledge-table write access.
Use a new corpus version when source contents change; existing source identities
cannot be overwritten inside an immutable version. Retrieval selects the corpus
name and version configured on the Agent service.

## Import and validate

Call the private Agent port directly; these endpoints are not exposed through
the gateway. Each request needs `Content-Type: application/json` and
`X-Agent-Internal-Token` matching the service configuration.

```text
POST http://127.0.0.1:18082/internal/agent/v1/knowledge/ingestions
```

Start with a small dry run and inspect the parser outcomes and chunk spans:

```json
{"mode":"DRY_RUN","afterId":0,"limit":3,"useAst":true}
```

Apply the reviewed batch:

```json
{"mode":"APPLY","afterId":0,"limit":3,"useAst":true}
```

Resume using the returned job ID; the persisted checkpoint supplies `afterId`:

```json
{"mode":"APPLY","jobId":"<job-id>","limit":50,"useAst":true}
```

Validate the configured corpus:

```json
{"mode":"VALIDATE"}
```

Require zero orphan documents/chunks, missing search rows and duplicate
canonical source identities. Repeating the same batch should not duplicate
sources, documents or chunks. Investigate unexpected parser fallback, failed
items or integrity errors before importing more data.

## Inspect retrieval

Use the same internal-token header and an authenticated user's `X-User-Id`:

```text
POST http://127.0.0.1:18082/internal/agent/v1/knowledge/retrieval
```

```json
{"query":"mint onlyOwner access control role","limit":5}
```

Results include fused channel ranks and source/chunk provenance. The Agent
uses this retrieval service through `retrieve_knowledge.v1`. Missing catalog
embeddings can degrade the semantic channel while text channels remain usable.

To disable knowledge ingestion and retrieval, set `AGENT_KNOWLEDGE_ENABLED=false`
and restart the Agent. This retains the stored corpus; it does not delete data
or change the catalog's standalone search indexes.
