# Knowledge Ingestion Runbook

## Current boundary

This runbook covers Phase 2B only. Ingestion is disabled by default, processes imported public-library contracts only (`uploader_id IS NULL`), writes to a non-active corpus version and never calls DashScope. The internal endpoint is deliberately outside Gateway's `/api/agent/**` route and must be called directly on the private Agent Service port.

## Required deployment order

1. Back up the target database and record its identifier.
2. Apply V1–V3 with one migration-enabled Agent Service instance, then stop it.
3. Restart `contract-runtime-service` and verify `POST /api/runtime/parse` reports the pinned compiler version.
4. Restart `cloud-agent-service` with migrations disabled and Knowledge ingestion enabled.
5. Run a small `DRY_RUN`, review parser outcomes and chunk spans, then use `APPLY` only for an approved non-active corpus version.
6. Resume with the returned `jobId`; the stored checkpoint is authoritative.
7. Run `VALIDATE`. Do not publish or route Search to the corpus in Phase 2B.

## Environment

```text
AGENT_ENABLED=true
AGENT_INTERNAL_TOKEN=<at-least-24-random-characters>
AGENT_KNOWLEDGE_ENABLED=true
AGENT_KNOWLEDGE_DB_URL=jdbc:postgresql://127.0.0.1:5432/<validated-target>
AGENT_KNOWLEDGE_DB_USERNAME=<restricted-role>
AGENT_KNOWLEDGE_DB_PASSWORD=<secret>
AGENT_KNOWLEDGE_CORPUS_NAME=contractsearch
AGENT_KNOWLEDGE_CORPUS_VERSION=phase2-shadow-v1
AGENT_KNOWLEDGE_MAX_BATCH_SIZE=50
AGENT_MIGRATIONS_ENABLED=false
```

Use a corpus version that has never been published. The database role needs read access to `smart_contract` and write access only to `knowledge_*` and Flyway history where applicable.

## Internal requests

Endpoint:

```text
POST http://127.0.0.1:18082/internal/agent/v1/knowledge/ingestions
```

Headers:

```text
Content-Type: application/json
X-Agent-Internal-Token: <same trusted token used at startup>
```

Small AST dry-run:

```json
{"mode":"DRY_RUN","afterId":0,"limit":3,"useAst":true}
```

Apply the reviewed batch:

```json
{"mode":"APPLY","afterId":0,"limit":3,"useAst":true}
```

Resume using the returned job ID. `afterId` may be omitted because the persisted checkpoint is used:

```json
{"mode":"APPLY","jobId":"<job-id>","limit":50,"useAst":true}
```

Validate the configured corpus:

```json
{"mode":"VALIDATE"}
```

Validation must report zero orphan documents, zero orphan chunks and zero duplicate canonical source identities. Repeating the same job/batch must create no additional ingestion items, sources, documents or chunks.

## Stop and rollback conditions

Stop ingestion if AST fallback/error rate is unexpectedly high, the runtime compiler version changes, private/user-uploaded contracts appear, validation reports orphans/duplicates, or existing Search latency changes.

To roll back, disable `AGENT_KNOWLEDGE_ENABLED` and restart only `cloud-agent-service`. The corpus remains inactive. Do not drop tables automatically. Delete/archive a failed corpus only by exact corpus ID after review; existing Search remains on the legacy index.
