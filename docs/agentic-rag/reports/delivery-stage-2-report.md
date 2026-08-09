# Delivery Stage 2 Report: RAG Data Online

> Status: COMPLETE  
> Next: Delivery Stage 3 — Iterative Agent Answer Loop  
> Date: 2026-08-09

## Outcome

Delivery Stage 2 is online as an additive, non-active Knowledge corpus. All 1887 public-library contracts (`uploader_id IS NULL`) have a persisted ingestion outcome. The existing Search API, Search UI and their indexes were not changed.

The Agent now has `retrieve_knowledge.v1`, which combines the existing semantic and code search channels with a new chunk-text channel. Fusion uses fixed RRF (`K=60`), one score contribution per contract per channel, contract-level collapse and up to three evidence chunks per result. Every chunk carries source location, trust and integrity metadata.

## Database result

- Applied Flyway V1–V4 to the validated local `public` schema after backup.
- Corpus: `contractsearch / delivery-stage2-v1`.
- Corpus remains `BUILDING`, not `ACTIVE`; retrieval requires the explicit configured version.
- Ingestion job: `0962e955-5a33-46ad-ab42-ab22c88fe371`, checkpoint `4478`, terminal status `PARTIAL` because AST fallback is recorded as a diagnostic outcome.
- Target contracts / ingestion items / aliases: `1887 / 1887 / 1887`.
- Canonical normalized sources: `1213`; 674 normalized duplicates are preserved as aliases and `SKIPPED` outcomes.
- Documents / chunks / chunk search rows: `6394 / 95937 / 95937`.
- Outcomes: `SUCCEEDED 134`, `PARTIAL 1078`, `SKIPPED 674`, `FAILED 1`.
- Diagnostics: `AST_FALLBACK 1700`, `SOURCE_INVALID 3`; old pragma/compiler incompatibility uses the approved lexical fallback path.
- Validation: orphan documents `0`, orphan chunks `0`, missing search rows `0`, duplicate canonical identities `0`.

## Backup and rollback anchor

- Backup file: `backups/contractsearch_pre_stage2_20260809_1445.dump` (ignored by Git).
- Size: approximately 25 MB.
- SHA-256: `d2177dd6c40cf7b048164add55d0dcebec87fcea02c294f704f9b26e9788168e`.

Normal rollback does not restore the dump: set `AGENT_KNOWLEDGE_ENABLED=false`, restart `cloud-agent-service`, and the Agent falls back to `search_contracts.v1`. V4 is additive and the old Search remains untouched. Use the dump only for an explicitly approved database restore.

## Implemented scope

- Added V4 `knowledge_chunk_search` with a generated `simple` `tsvector`, GIN index and corpus/contract index.
- Ingestion writes exactly one search row per canonical chunk; `VALIDATE` checks search-row parity.
- Added `JdbcKnowledgeRetrievalRepository` with bounded tokenization and parameterized `to_tsquery` execution.
- Added `KnowledgeRetrievalService` with independently degradable semantic, code and chunk-text channels.
- Added fixed RRF, per-channel contract collapse, deterministic tie breaking and bounded Top-K.
- Added `retrieve_knowledge.v1` Evidence production with `contractId`, `chunkId`, file/line, `contentHash`, `trustLevel`, channel ranks and an explicit untrusted-content boundary.
- Added a trusted, private retrieval smoke endpoint; no Gateway/public route was added.
- Registered the tool in the existing multi-round Agent loop and exposed only tools actually present under current feature flags.
- Fixed checkpoint resume deserialization when `afterId` is omitted.

## Minimal verification

- Focused migration, retrieval and ingestion-request tests pass.
- Full ingestion completed in 10 checkpoint batches.
- `VALIDATE` passed all referential and search-row checks.
- Three real queries ran with no degraded channels:
  - `ERC20 token transfer approve allowance`
  - `mint onlyOwner access control role`
  - `reentrancy nonReentrant call value`
- Each query returned contract candidates; the chunk-text channel returned a chunk with file, line span, hash and `COMMUNITY` trust metadata.
- Three returned chunks were checked directly against Knowledge documents. Their contract IDs, file paths, line spans, content excerpts and SHA-256 metadata were present and consistent.
- A focused unit test proves chunk Evidence is still returned when both catalog semantic/code calls fail.

This checkpoint deliberately did not run a large model evaluation, create chunk embeddings, alter the current Search page or activate the corpus.

## Manual test checklist

1. Start `cloud-agent-service` with Knowledge enabled and the exact corpus name/version; keep it private or loopback-only.
2. Call the internal retrieval endpoint with the trusted token and `X-User-Id`.
3. Run the three queries above and confirm `degradedChannels` is empty under normal conditions.
4. Expand one result and verify `chunkId`, file, `lineStart/lineEnd`, `contentHash` and `trustLevel` exist.
5. Stop or misconfigure only the existing catalog-search dependency and confirm chunk results still return with `contract_semantic` / `contract_code` listed as degraded.
6. Run an Agent task after Delivery Stage 3 and confirm a `retrieve_knowledge.v1` Tool step creates `KNOWLEDGE_CHUNK` Evidence.
7. Confirm the original Search page and `/api/contracts/search` behavior are unchanged.

## Services to restart

- Required to load this code: `cloud-agent-service`.
- Required only for future AST ingestion/rebuild: `contract-runtime-service`.
- No restart required: Vue frontend, Nginx, Gateway, `cloud-service`, PostgreSQL/ParadeDB, Nacos and `lean-runtime-service`.

The temporary validation instances on ports `18083` and `19091` are not production dependencies and should be stopped after verification.

## Known boundaries accepted for delivery

- The corpus is dominated by old compiler pragmas and some Vyper-like sources; many records therefore have deterministic FILE/lexical chunks rather than AST function chunks.
- One invalid canonical source has a `FAILED` outcome; it does not prevent the other 1886 contract outcomes or corpus validation.
- Canonical deduplication intentionally stores 1213 source bodies for 1887 aliases.
- Trust is `COMMUNITY` for the imported catalog. Retrieval rank does not upgrade trust.
- Chunk search currently extracts Latin/code tokens. Chinese user questions rely on the existing semantic channel or an Agent query rewrite to Solidity symbols/security terms.
- Lean verification is unchanged and remains outside this stage.
