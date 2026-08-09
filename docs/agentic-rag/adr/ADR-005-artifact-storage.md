# ADR-005: Artifact Storage

- Status: Accepted for MVP
- Date: 2026-08-09

## Context

Compiler output, model traces, large source bundles and proof diagnostics can exceed reasonable relational row sizes. No object store is currently part of the deployment.

## Decision

Store small structured Agent state, evidence metadata, hashes and bounded text excerpts in PostgreSQL. Store oversized artifacts on a configured local filesystem path outside the web root, referenced by an opaque artifact ID and content hash.

Artifact reads go through an authenticated service endpoint with ownership/trust checks. Never expose host paths. Define size, retention and redaction rules before enabling runtime artifacts for users. Do not add S3/MinIO in MVP without measured need.

## Consequences

- Keeps MVP infrastructure small.
- Requires backups to include the artifact directory and database metadata consistently.
- Multi-instance deployment later requires shared/object storage or affinity.

## Rollback

Disable artifact-producing tools and retain bounded inline evidence. Database state remains valid even if an optional artifact is unavailable.

