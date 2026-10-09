# Phase 0 Data Profile

> **Historical record — 2026-08-09.** Archived on 2026-10-09.
> This describes that checkpoint, not the current release or current verification status.
> Historical test results and limitations are retained; private run identifiers, backup
> identifiers and host-specific observations have been omitted or generalized.
> See [current project status](../../status.md) before following any operational steps.

> Source: read-only queries against the checkpoint database on 2026-08-09.

## Database and extensions

| Item | Value |
|---|---|
| PostgreSQL | 18.3 |
| `pg_search` | 0.23.0 |
| `vector` | 0.8.1 |
| Live `smart_contract` rows | 1889 |
| Dump baseline rows | 1887 |
| Embedding dimensions | `vector(768)` for code and description |

At this checkpoint, the live database had two more contracts than its dump. These historical counts do not describe the current demo dataset or a newly initialized database.

## Completeness and duplication

| Metric | Count | Ratio (of 1889) |
|---|---:|---:|
| Empty source | 0 | 0.00% |
| Empty description | 23 | 1.22% |
| Missing code embedding | 755 | 39.97% |
| Missing description embedding | 1255 | 66.44% |
| SPDX marker present | 513 | 27.16% |
| Solidity pragma present | 1856 | 98.25% |
| Duplicate source rows beyond first copy | 663 | 35.10% |
| Duplicate source hash groups | 97 | — |
| ABI present | 1889 | 100.00% |
| ABI status `generated` | 2 | 0.11% |
| ABI status missing | 1887 | 99.89% |
| Compiler version recorded | 2 | 0.11% |

Duplicate rows must be grouped by content hash during ingestion, while retaining separate contract/address provenance records.

## Source shape

| Shape | Count |
|---|---:|
| Plain Solidity | 1412 |
| Etherscan double-brace Standard JSON | 416 |
| JSON object | 28 |
| Unknown/other | 33 |

The Phase 2 parser router must handle these classes separately. Treating all `source_code` values as one Solidity file will lose file names, imports and line provenance.

## Source sizes

| Metric | Characters |
|---|---:|
| Minimum | 163 |
| Mean | 35,660 |
| Maximum | 1,180,887 |

The maximum source is too large for direct model context or a single embedding. Size limits and AST/file-aware chunking are mandatory.

## Existing indexes

- `idx_smart_contract_bm25`: BM25 over `source_code::pdb.source_code` and `description::pdb.jieba`.
- `idx_smart_contract_code_hnsw`: cosine HNSW over `code_embedding`.
- `idx_smart_contract_desc_hnsw`: cosine HNSW over `description_embedding`.
- Primary key and unique address B-tree indexes.

## Data quality implications

1. Retain 768 dimensions for the first shadow index; do not mutate existing vector columns.
2. Build corpus versions from the live database using a consistent snapshot.
3. Record ingestion outcomes for every row, including partial/invalid sources.
4. Separate document identity from deployment/address provenance to avoid losing 663 duplicate deployments.
5. Do not claim vector coverage over the full corpus until missing embeddings are rebuilt.
6. Keep user drafts/history outside the public corpus and apply user ownership filters before retrieval.
7. ABI presence does not prove reproducible compilation: only two rows currently record a generated status/compiler version.

## Profiling still required in Phase 2

- Exact Solidity version distribution after parsing all source shapes.
- Import resolvability and compiler success by source class.
- License normalization beyond the SPDX marker heuristic.
- AST function/modifier coverage and file/line round-trip validation.
- Content-level near-duplicate clusters, not only exact MD5 groups.
