# ADR-002: Initial Embedding Dimension

- Status: Accepted for the first shadow corpus
- Date: 2026-08-09

## Context

Both live embedding columns are `vector(768)`. The current DashScope path uses `text-embedding-v4` and manually truncates provider output to 768. Most live rows are missing one or both embeddings.

## Decision

Keep 768 dimensions for the first Agent Knowledge shadow index. Do not alter existing vector columns in place. Store embedding provider, model, requested dimension, normalization method and code version with each corpus version.

Test an explicit provider-side 768 dimension request against current manual truncation before changing behavior. Evaluate 1024 or other dimensions only in isolated columns/tables with a fixed retrieval set.

## Consequences

- Preserves compatibility and limits Phase 2 scope.
- Does not assert that manual truncation is optimal.
- Requires a versioned re-embedding job because live vector coverage is incomplete.

## Rollback

Switch the active corpus/index pointer to the previous version; existing search indexes remain untouched.

