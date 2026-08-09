# Phase 2 Report: Knowledge Ingestion

> Status: IN_PROGRESS — Phase 2A Knowledge Foundations verified
> Decision: CONTINUE_TO_PHASE_2B
> Date: 2026-08-09

## Checkpoint outcome

Phase 2A establishes an additive, inactive Knowledge Layer foundation without changing current Search, AI Chat, Contract Lab or Agent Tool behavior. It adds versioned corpus/source/document/chunk/job/error storage contracts and a pure dry-run pipeline that recognizes the source shapes observed in contract libraries, normalizes them reproducibly, computes SHA-256 identities and creates deterministic Solidity declaration chunks with source spans.

This checkpoint intentionally does not apply the migration to the running database, ingest all contracts, call DashScope, build indexes or switch an active corpus. The existing catalog and indexes remain the production source of truth.

## Modified scope

- Added Flyway `V2__knowledge_layer_foundations.sql` with `knowledge_corpus_version`, `knowledge_source`, `knowledge_document`, `knowledge_chunk`, `knowledge_ingestion_job` and `knowledge_ingestion_error`.
- Added explicit corpus status, source trust level, visibility/owner, parser outcome, source hashes, chunk spans and resumable job counters.
- Added exact-source, normalized-source and canonical multi-file manifest SHA-256 identities. File order does not change a manifest hash.
- Added `SoliditySourceRouter` support for plain Solidity, Etherscan double-brace Standard JSON, Solidity Standard JSON, multi-file source maps and explicit partial/invalid outcomes.
- Added line-ending/trailing-whitespace normalization while retaining raw content and both hashes.
- Added `SolidityChunker`, which masks comments and string literals before deterministic function/modifier/constructor/receive/fallback boundary extraction, while preserving exact content, file, line and character spans.
- Added `KnowledgeIngestionPlanner.dryRun`; partial/invalid sources receive an outcome but are not chunked.

## Important boundary

The Phase 2A chunker is a deterministic lexical fallback, not the final AST-aware chunker. It must not be advertised as AST verification. Phase 2B must obtain fixed-solc Standard JSON AST, associate declarations with AST IDs/types and retain this fallback only for explainable partial outcomes.

No AgentScope dependency was added. The current Java-native service already owns the relevant state, policies and Tool boundary; Knowledge ingestion is deterministic infrastructure and gains no demonstrated benefit from a Python Agent framework.

## Verification

- `cloud-agent-service` compiled successfully.
- Four focused tests passed: V1/V2 additive migration contracts, supported source routing/normalization/hash stability and deterministic declaration chunk/line mapping.
- No model or Embedding API calls were made.
- No database migration or corpus write was executed.
- No full reactor or 1887-contract test was run, following the project owner's request to keep validation minimal.

## Manual test checklist

This checkpoint does not expose a new public endpoint. Manual validation is therefore deployment-oriented:

1. Keep `AGENT_MIGRATIONS_ENABLED=false` and confirm the current Search and Agent run still work; this proves the sidecar code is inactive.
2. Before applying V2 to the real database, run Flyway once against a disposable schema/database and confirm the six empty `knowledge_*` tables are created.
3. Run Flyway a second time against that disposable target and confirm there is no pending migration or duplicate object.
4. Check that a `PRIVATE` or `WORKSPACE` `knowledge_source` without `owner_user_id` is rejected, while a `PUBLIC` source is allowed.
5. Do not import the 1887 contracts and do not enable any Knowledge retrieval flag during Phase 2A.

## Services to restart

- For source-code-only validation: no currently running service needs to restart.
- To load the new dry-run Java classes in a deployed build: restart only `cloud-agent-service`.
- To deliberately apply V2 later: start one `cloud-agent-service` instance with migration variables enabled against a previously validated target, then restart it with migrations disabled.
- Do not restart Gateway, Vue, `cloud-service`, PostgreSQL/ParadeDB, Nacos, `contract-runtime-service` or `lean-runtime-service` for this checkpoint.

## Risks and next checkpoint

- Lexical parsing cannot fully resolve Solidity grammar, nested source mappings, inheritance or AST identity.
- `raw_content` and `normalized_content` require retention/access controls before user-private data ingestion.
- Hash columns make dedup decisions possible, but the idempotent writer/checkpoint transaction is not implemented yet.
- V2 SQL has a static additive contract test; a disposable PostgreSQL migration smoke remains required before first activation.

Phase 2B should add the fixed-solc AST adapter, metadata extraction and an idempotent DB writer with dry-run/resume/validate commands. It should validate a very small local sample first and still avoid DashScope until ingestion outcomes are stable.

## Rollback

- Leave `AGENT_MIGRATIONS_ENABLED=false`; no runtime behavior changes.
- Revert the Phase 2A code/migration commit if the migration has not been applied.
- If V2 was applied, roll back the service binary and leave the empty additive tables in place. Do not drop them automatically.
- Existing Search continues on its old tables/indexes because no active corpus pointer or route was introduced.
