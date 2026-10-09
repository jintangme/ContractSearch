# Delivery Stage 2.5 Report: Authoritative Knowledge Base

> **Historical record — 2026-08-09.** Archived on 2026-10-09.
> This describes that checkpoint, not the current release or current verification status.
> Historical test results and limitations are retained; private run identifiers, backup
> identifiers and host-specific observations have been omitted or generalized.
> See [current project status](../../../status.md) before following any operational steps.

> Checkpoint status: COMPLETE
> Next: Delivery Stage 3 — Iterative Agent Answer Loop  
> Date: 2026-08-09

## Outcome

ContractSearch now has a real, reproducible authoritative Knowledge pack alongside the 1887-contract community corpus. The pack contains 18 pinned documents and 313 searchable chunks. Retrieval keeps official reference evidence and reusable contract candidates in the same answer instead of allowing the larger contract corpus to drown out standards and security guidance.

## Sources and provenance

- 8 ERC standards: ERC-20, 165, 721, 1155, 2612, 4626, 1167 and 1967.
- EIP-712 typed structured data.
- Solidity 0.8.35 security considerations and common patterns.
- 6 OpenZeppelin 5.7.0 contracts: Ownable, AccessControl, ReentrancyGuard, Pausable, ERC20 and ERC1967Proxy.
- OWASP Smart Contract Top 10.

All entries store the original raw URL, immutable 40-character commit, declared license, fetch time and normalized SHA-256. The complete source lockfile is `docs/agentic-rag/knowledge-sources-v1.json`.

## Database result

- Added: 18 sources / 18 documents / 313 chunks.
- Total: 1231 sources / 6412 documents / 96250 chunks / 96250 search rows.
- Trust distribution for new sources: 18 `AUTHORITATIVE`.
- Validation: orphan documents `0`, orphan chunks `0`, missing search rows `0`, duplicate source identities `0`.
- Corpus remains explicitly configured as `contractsearch / delivery-stage2-v1`; the old Search index is unchanged.

## Implementation

- V5 permits reference search rows without an existing contract ID and adds bounded lookup indexes.
- A trusted internal ingestion endpoint validates allowlist, immutable revision, content size/format and server-side hash.
- Heading-aware chunking preserves line/character spans and stable content hashes.
- The standard-library sync script is dependency-free, supports dry-run and performs idempotent batches.
- Retrieval now has independently bounded authoritative-reference and contract-chunk text channels.
- Source-key exact matching and distinct-token coverage improve standard/library selection.
- `retrieve_knowledge.v1` emits `AUTHORITATIVE_REFERENCE` Evidence with URL, source kind, version reference, line span and content hash.

## Minimal verification

- Focused migration/chunker/retrieval/request tests pass.
- A repeated 18-source import inserted no duplicate rows and refreshed search projections.
- `VALIDATE` passed all four integrity checks.
- Real query results, with no degraded channels:
  - ERC20 query: `ERC-20 Token Standard` plus LinkToken/TetherToken/contract chunks.
  - Mint authorization query: `OpenZeppelin AccessControl v5.7.0` plus role-guarded mint contracts.
  - Reentrancy query: `OpenZeppelin ReentrancyGuard v5.7.0` plus existing contract code; Solidity security guidance is also indexed.

No GLM or embedding calls were made for this checkpoint.

## Backup and rollback

A private, Git-ignored database backup was recorded before this checkpoint.
Backup filenames, hashes and local restore identifiers are not published.

Normal rollback is to disable `AGENT_KNOWLEDGE_ENABLED` and restart only `cloud-agent-service`. The migration and records are additive; do not restore the dump unless physical removal is explicitly required.

## Manual checklist

1. Restart `cloud-agent-service` with Knowledge enabled and the same corpus name/version.
2. Call `/actuator/health` and require `UP`.
3. Run the three acceptance queries through the internal retrieval endpoint.
4. Confirm each response contains an `AUTHORITATIVE` source and at least one contract result.
5. Inspect one reference result for `sourceUrl`, `sourceRef`, `chunkId`, line span, content hash and trust level.
6. Confirm the original semantic/code Search page still works unchanged.

## Services

- Restart required: `cloud-agent-service` only.
- No restart required: Vue, Nginx, Gateway, `cloud-service`, PostgreSQL/ParadeDB, Nacos, `contract-runtime-service` and `lean-runtime-service`.
- The temporary validation instance was not a project dependency and was stopped after verification.
