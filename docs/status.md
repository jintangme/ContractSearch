# Current project status

Reviewed on **2026-10-09** for the 2.0 public demonstration preparation.

## Supported demonstration

Basic mode provides registration, browsing, BM25 source search, contract detail
and a local compile/deploy/call workspace using three original sample contracts.
It needs no model key and does not load the private historical corpus.

Agent mode is separately configured. Its control loop, tools and persistence
have code and automated coverage; live paid-model quality was not re-evaluated
during this preparation. The demo knowledge corpus can be ingested without a
model call.

## Verified scope

See [validation commands and results](evaluation/README.md). Test reports,
raw logs, account credentials and browser state remain local. Screenshots show
only the synthetic demo.

CI configuration is included; a workflow file is not itself proof of a
successful GitHub Actions run. Check the workflow status for the relevant commit.

## Remaining limitations

- Repository-wide licensing remains undecided; existing snippet license markers
  need source-specific review.
- Production hosting, multi-tenant runtime isolation and interrupted Agent-run
  recovery are outside the local demo.
- Semantic retrieval requires embeddings and provider configuration.
- Model quality comparisons require a human-reviewed dataset and live runs;
  historical mock harness output is not such a comparison.
- Business startup DDL remains alongside explicit bootstrap/Agent migrations.
- Lean is optional, local, and not a Solidity security proof.
- The minimal Java container does not install the business service's legacy
  Node-based ABI helper. Included examples have ABI; Lab and Agent compilation
  use the separate runtime.
- Git history may retain old author metadata and removed content; current-file
  checks do not rewrite history.

[Version evolution](version-history.md) · [Archived records](archive/README.md)
