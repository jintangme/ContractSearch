# Phase 2 Report: Knowledge Ingestion

> **Historical record — 2026-08-09.** Archived on 2026-10-09.
> This describes that checkpoint, not the current release or current verification status.
> Historical test results and limitations are retained; private run identifiers, backup
> identifiers and host-specific observations have been omitted or generalized.
> See [current project status](../../../status.md) before following any operational steps.

> Checkpoint status: IN_PROGRESS — Phase 2A/2B foundations, AST and resumable writer verified
> Decision: CONTINUE_TO_PHASE_2C_EMBEDDING_AND_SHADOW_INDEX
> Date: 2026-08-09

## Checkpoint outcome

Phase 2A establishes an additive, inactive Knowledge Layer foundation without changing current Search, AI Chat, Contract Lab or Agent Tool behavior. It adds versioned corpus/source/document/chunk/job/error storage contracts and a pure dry-run pipeline that recognizes the source shapes observed in contract libraries, normalizes them reproducibly, computes SHA-256 identities and creates deterministic Solidity declaration chunks with source spans.

Phase 2B adds a pinned `solc 0.8.35` Standard JSON AST endpoint, AST-aware declaration metadata, public-corpus dedup aliases and a transactional ingestion writer with stored checkpoints and per-contract idempotency ledger. It also adds bounded `DRY_RUN`, `APPLY`, resume and `VALIDATE` operations through a direct-service-only internal endpoint.

The V1–V3 scripts and writer were exercised in an automatically deleted PostgreSQL schema. This checkpoint intentionally does not apply the migration to the running `public` schema, ingest all contracts, call DashScope, build indexes or switch an active corpus. The existing catalog and indexes remain the production source of truth.

## Modified scope

- Added Flyway `V2__knowledge_layer_foundations.sql` with `knowledge_corpus_version`, `knowledge_source`, `knowledge_document`, `knowledge_chunk`, `knowledge_ingestion_job` and `knowledge_ingestion_error`.
- Added explicit corpus status, source trust level, visibility/owner, parser outcome, source hashes, chunk spans and resumable job counters.
- Added exact-source, normalized-source and canonical multi-file manifest SHA-256 identities. File order does not change a manifest hash.
- Added `SoliditySourceRouter` support for plain Solidity, Etherscan double-brace Standard JSON, Solidity Standard JSON, multi-file source maps and explicit partial/invalid outcomes.
- Added line-ending/trailing-whitespace normalization while retaining raw content and both hashes.
- Added `SolidityChunker`, which masks comments and string literals before deterministic function/modifier/constructor/receive/fallback boundary extraction, while preserving exact content, file, line and character spans.
- Added `KnowledgeIngestionPlanner.dryRun`; partial/invalid sources receive an outcome but are not chunked.
- Added `/api/runtime/parse` with a pinned compiler, multi-file input, bounded request size and compact declaration-only output.
- Added AST ID/type, owning contract, visibility, mutability, virtual flag, parameter/return types and modifiers to chunk metadata.
- Added V3 `knowledge_source_alias` and `knowledge_ingestion_item`; normalized duplicates share one canonical source while preserving each catalog contract mapping.
- Added transactional `JdbcKnowledgeIngestionStore`, persistent checkpoint resume and exact-job replay suppression.
- Added an internal ingestion endpoint outside the Gateway `/api/agent/**` route. It is default-off and still requires the trusted internal token.
- Public corpus loading excludes user-uploaded contracts by requiring `uploader_id IS NULL`.

## Important boundary

The lexical parser remains a deterministic fallback and is never labeled as AST output. A fixed `solc` AST success is required for AST metadata; compiler incompatibility or runtime failure produces an explicit `AST_FALLBACK` ingestion outcome.

No AgentScope dependency was added. The current Java-native service already owns the relevant state, policies and Tool boundary; Knowledge ingestion is deterministic infrastructure and gains no demonstrated benefit from a Python Agent framework.

## Verification

- `cloud-agent-service` compiled successfully; nine focused Java tests passed.
- The runtime AST test passed and a temporary runtime HTTP smoke returned the pinned compiler, two declarations and exact spans; the temporary service was then stopped.
- V1–V3 migrated successfully in a guarded disposable schema; the second Flyway run was a no-op and the schema was deleted automatically.
- The PostgreSQL writer test proved two identical catalog sources collapse to one canonical source/document/chunk and replaying the same job adds zero processed items.
- No model or Embedding API calls were made.
- No migration or corpus write was executed against the running `public` schema; database writes were confined to the auto-deleted test schema.
- No full reactor or 1887-contract test was run, following the project owner's request to keep validation minimal.

## Manual test checklist

This checkpoint does not expose a new public endpoint. Manual validation is therefore deployment-oriented:

1. Keep `AGENT_MIGRATIONS_ENABLED=false` and confirm the current Search and Agent run still work; this proves the sidecar code is inactive.
2. Before applying V2/V3 to the real database, run Flyway once against a disposable schema/database and confirm all `knowledge_*` tables are created.
3. Run Flyway a second time against that disposable target and confirm there is no pending migration or duplicate object.
4. Restart the runtime and confirm `/api/runtime/parse` reports `compilerVersion=0.8.35`.
5. Follow `docs/agentic-rag/knowledge-ingestion-runbook.md` for a three-contract AST dry-run and inspect file/line spans.
6. Check that a `PRIVATE` or `WORKSPACE` `knowledge_source` without `owner_user_id` is rejected, while a `PUBLIC` source is allowed.
7. Do not import the full corpus and do not enable any Knowledge retrieval flag yet.

## Services to restart

- For source-code-only validation: no currently running service needs to restart.
- Restart `contract-runtime-service` to load the new AST endpoint and pinned package declaration.
- Restart `cloud-agent-service` to load AST/client/writer/internal endpoint code.
- To deliberately apply V2/V3 later: start one `cloud-agent-service` instance with migration variables enabled against a previously validated target, then restart it with migrations disabled.
- Do not restart Gateway, Vue, `cloud-service`, PostgreSQL/ParadeDB, Nacos or `lean-runtime-service` for this checkpoint.

## Risks and next checkpoint

- `solc 0.8.35` cannot parse every historical pragma; older-version compiler bundles/allowlist are still needed before full-corpus coverage can pass.
- `raw_content` and `normalized_content` require retention/access controls before user-private data ingestion.
- The writer is intentionally single-ingestor during Phase 2; normalized canonical selection is not designed for concurrent ingestion replicas.
- The internal endpoint has no Gateway route by design and must remain private/loopback-accessible.

Phase 2C should run a bounded corpus-format/pragma profile, add approved cached compiler versions, then implement versioned DashScope 768-dimensional batching/cache and BM25/vector shadow indexes. It must begin with a small sample and measure failure/cost before full ingestion.

## Rollback

- Leave `AGENT_MIGRATIONS_ENABLED=false`; no runtime behavior changes.
- Revert the Phase 2B code/migration commit if the migration has not been applied.
- If V2/V3 was applied, roll back the service binary and leave the additive tables in place. Do not drop them automatically.
- Existing Search continues on its old tables/indexes because no active corpus pointer or route was introduced.
