# Architecture

## Purpose and service boundaries

ContractSearch 2.0 supports contract reuse through source retrieval, evidence
inspection and local experiments. Java owns business APIs and Agent control;
Node owns Solidity execution tools; Python discovery is optional and offline.

| Component | Responsibility | Default host port |
|---|---|---:|
| Vue / Vite | Search, detail, conversation, precheck and workspace UI | 5173 development |
| Nginx demo | Static frontend and local reverse proxy | 8080 |
| cloud-gateway | JWT validation, identity-header sanitization, routing | 18080 |
| cloud-service | Contracts, search, profiles, favorites, drafts, precheck | 18081 |
| cloud-agent-service | Bounded loop, knowledge retrieval, evidence and terminal archive | 18082 |
| contract-runtime-service | solc AST/compilation, shared Ganache deployment/calls | 19090 |
| lean-runtime-service | Optional local Lean program checking | 19100 |
| scse-discover | Dune/Etherscan discovery and shared-file import callback | 18000 |

Host services default to loopback. Compose explicitly binds internal services
inside its own network; only the documented loopback ports are published.
Standalone auth/user modules are reserved and excluded from the default Maven
reactor. Actual authentication and profile behavior live in cloud-service.

## Retrieval and knowledge

1. Normalize plain Solidity, Etherscan Standard JSON and multi-file source.
2. Deduplicate source bodies while retaining contract provenance.
3. Parse AST declarations with the runtime; record lexical/file fallback when
   the pinned compiler cannot parse a source.
4. Store versioned sources, documents, chunks, aliases and ingestion outcomes.
5. Retrieve from description semantic search, source BM25, chunk text search
   and pinned authoritative references.
6. Fuse channel ranks with RRF and return evidence locations and trust labels.

Description vectors use 768 dimensions. The chunk channel uses PostgreSQL text
search; the existence of an embedding column does not mean every chunk has an
embedding. Code-mode search is BM25. Pure Chinese questions depend on the
semantic path or a suitable query rewrite because chunk token extraction is
focused on Latin/code tokens.

## Agent control

The model chooses a structured action after each observation. Tools are limited
to retrieve, search, read, compile and read-only precheck. Budgets, argument
validation, repeated-call control and evidence ID validation are enforced in
Java. Retrieved source, reference text and user input are wrapped as untrusted
data, not granted tool authority.

Runs execute asynchronously in a bounded thread pool. The UI polls snapshots
and cancels stale requests on navigation. Source cards preserve distinct
locations, prioritize cited evidence and support expanding every available
source. Optional database persistence archives terminal state; interrupted
execution is not resumable and active state is not a distributed queue.

## Data initialization

`database/bootstrap/001-schema.sql` provides the fresh business schema and
required extensions/indexes. `002-demo.sql` inserts three original samples and
a group, without user accounts or embeddings. Agent V1–V6 migrations add the
knowledge and audit structures. Some legacy business startup DDL still exists;
unifying all schema evolution remains follow-up work.

Discovery writes Parquet locally and sends its path to cloud-service. These
processes must share the configured import directory. A network callback alone
does not transfer the file. The callback is unavailable through Gateway and
requires a separate token plus constrained real-path validation.

## Execution boundaries

The runtime bundles Solidity 0.8.35. AST extraction always uses this bundled
compiler, while workspace compilation can select a known version for the
source pragma or an explicit compiler choice. The remote version map contains
only 0.4.26, 0.5.17, 0.6.12, 0.7.6 and 0.8.20; first use requires a network
download, and arbitrary exact pragma versions are not guaranteed to work.
Ganache runs the Shanghai hardfork. Compilers at 0.8.20 or newer explicitly
target Shanghai; older compilers retain their default EVM target. The workspace
compiles a single source unit, so historical multi-file sources can require
additional preparation. The EVM is shared and transient, not isolated by user.

Lean is separate and excluded from both the Agent tool list and demo reverse
proxy. Rejecting obvious unfinished proofs improves result labeling; it is not
a sandbox and does not establish Solidity equivalence.

Precheck combines heuristics and optional GLM assistance. Citation existence
and compilation success must never be presented as security guarantees.

## Design records

See the [Agent ADRs](../agentic-rag/adr/ADR-001-service-boundary.md),
[API contract](../agentic-rag/openapi/agent-api-v1.yaml),
[current status](../status.md) and [historical archive](../archive/README.md).
