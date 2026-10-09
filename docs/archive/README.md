# Historical records

These documents preserve engineering checkpoints from **2026-08-09**. They were
archived on **2026-10-09** to separate development history from the current public
project guide. Start with [current project status](../status.md) and the repository
README for supported setup and current validation.

Historical statuses, test counts, skipped checks, failed probes and deferred
evaluation remain as originally recorded. They are not evidence that today's
checkout or a public deployment has passed the same checks. Private run/job IDs,
backup filenames and hashes, local paths and machine-specific listener details
have been removed or generalized. Historical rollback and startup instructions
are retained for context and should not be used as the current deployment guide.

| Record | What it preserves |
|---|---|
| [Phase 0 environment baseline](agentic-rag/current-state.md) | Runtime versions, original service and security boundaries, and known gaps before Agent work. |
| [Phase 0 data profile](agentic-rag/data-profile.md) | Historical corpus size, completeness, duplication, source formats and embedding coverage. |
| [Phase 0 report](agentic-rag/reports/phase-0-report.md) | Initial discovery, build and search smoke checks. |
| [Phase 1 report](agentic-rag/reports/phase-1-report.md) | Java Agent foundation, protocol contracts and a deterministic evaluation harness. |
| [Phase 1A report](agentic-rag/reports/phase-1a-report.md) | Bounded Search/Get/Compile loop and the limits of the initial real-model validation. |
| [Phase 2 report](agentic-rag/reports/phase-2-report.md) | Source normalization, AST parsing and resumable ingestion foundations. |
| [Delivery Stage 2](agentic-rag/reports/delivery-stage-2-report.md) | Historical contract-corpus ingestion and chunk-text retrieval. |
| [Delivery Stage 2.5](agentic-rag/reports/delivery-stage-2-5-report.md) | Versioned authoritative sources and provenance. |
| [Delivery Stage 3](agentic-rag/reports/delivery-stage-3-report.md) | Iterative real-tool loop, evidence persistence and a recorded model run. |
| [Delivery Stage 4](agentic-rag/reports/delivery-stage-4-report.md) | Integration into the conversation UI; the checkpoint was marked `READY_FOR_SMOKE`. |

The API specification, schemas, ADRs, evaluation fixtures and operational runbooks
remain under [`docs/agentic-rag/`](../agentic-rag/). Archiving these reports does not
change their interfaces or convert the mock harness into a model-quality result.
