# Phase 0 Report: Repository Discovery and Fact Baseline

> Date: 2026-08-09  
> Result: IMPLEMENTED_WITH_BLOCKERS  
> Business code changed: No

## Outcome

The repository, running local services, live database, OpenAPI surface, model path, search smoke baseline and runtime behavior were audited. The target architecture in `plan.md` is compatible with the current system, but Phase 1 must not start until the recoverability and secret-management blockers below are resolved.

## Files added

- `docs/agentic-rag/current-state.md`
- `docs/agentic-rag/service-map.md`
- `docs/agentic-rag/data-profile.md`
- `docs/agentic-rag/api-inventory.md`
- `docs/agentic-rag/eval/phase0-smoke-baseline.json`
- `docs/agentic-rag/adr/ADR-001-service-boundary.md`
- `docs/agentic-rag/adr/ADR-002-embedding-dimension.md`
- `docs/agentic-rag/adr/ADR-003-database-migrations.md`
- `docs/agentic-rag/adr/ADR-004-sse-auth.md`
- `docs/agentic-rag/adr/ADR-005-artifact-storage.md`
- `docs/agentic-rag/reports/phase-0-report.md`

## Dynamic verification results

| Check | Result |
|---|---|
| Vue/Vite :5173 | PASS, HTTP 200 |
| Nacos :8848 | PASS; two registered Spring services |
| Gateway protected route | PASS, anonymous request rejected with 401 |
| Spring OpenAPI | PASS; 33 paths / 42 operations |
| PostgreSQL query | PASS; 18.3, `pg_search 0.23.0`, `vector 0.8.1` |
| Live corpus count | 1889, two above dump baseline |
| Solidity compile | PASS; actual compiler 0.8.35 |
| `glm-4.5-air` basic call | PASS; correct requested/used model returned |
| Lean proof check | FAIL; request attempted 4.32.2 download and timed out |
| Discovery service :18000 | NOT RUNNING; not required for online app |

## Build verification

| Command/scope | Result |
|---|---|
| Maven reactor tests | PASS; 4 Spring context tests, 0 failures/errors, all 5 reactor projects successful |
| Vue production build | PASS; output written to `/private/tmp`, project `dist/` not modified |
| Runtime JavaScript syntax | PASS for both Node entry points |
| Baseline JSON parse | PASS |

Non-blocking warnings recorded for later cleanup: Spring Cloud Gateway uses temporarily migrated property keys; Nacos test logging targets a home-directory log path; the backend reports multiple SLF4J providers and no Jakarta Validation provider; the Vue build has a roughly 2.56 MB Monaco/editor chunk. These warnings predate Phase 0 and no source fix was made in this documentation-only stage.

## Important findings

1. `scse-discover.yaml` contains plaintext third-party API credentials. Values are intentionally not reproduced in any Phase 0 deliverable. Revoke/rotate them and replace the file values with environment references.
2. No Git worktree exists, so the plan's per-phase rollback guarantee is not yet achievable.
3. Backend direct access trusts `X-User-Id`, so port 18081 is a privileged internal boundary.
4. Runtime versions drift from documentation/package intent; Lean is currently unusable and request-time downloads violate reproducibility.
5. Search request `limit` is ignored, RRF score is not exposed, and two semantic smoke queries showed a suspicious shared top result.
6. Corpus duplication and embedding gaps are large enough that Phase 2 must rebuild a versioned shadow corpus rather than reuse rows blindly.
7. No production Nginx configuration or deployment/backup runbook was found.

## Exit Gate assessment

| Gate | Status |
|---|---|
| Facts have evidence or are explicit blockers | PASS |
| No production/database writes | PASS |
| Adapter ownership is documented | PASS |
| Recoverable version-control baseline | BLOCKED — user approval required |
| Lean current/target/fix is unambiguous | PARTIAL — discrepancy proven; pinning implementation belongs before Lean Tool enablement |
| Secret handling safe for new work | BLOCKED — exposed discovery credentials must be rotated |

Phase 0 is therefore recorded as `IMPLEMENTED_WITH_BLOCKERS`, not `COMPLETE`.

## Manual test checklist

- [ ] Open `http://localhost:5173` and confirm login, Browse, Search, Conversation, Contract Check and Contract Lab pages load.
- [ ] Log in and run one semantic search; confirm results appear and note that more than requested may be returned.
- [ ] In Contract Lab, compile the default Solidity example; confirm compiler version shown is 0.8.35 in the current installation.
- [ ] In Proof Workbench, check `example : True := by trivial`; confirm the current failure/timeout before any Lean fix.
- [ ] Open Nacos and confirm only `cloud-gateway` and `smart-contract-search-engine` are registered.
- [ ] Confirm ports 18081, 19090 and 19100 are not exposed outside the trusted local/private network.
- [ ] After key rotation, verify discovery import using newly supplied environment variables.

## Restart requirements

None. This phase adds documentation and baseline data only. Existing services were probed but not modified or restarted.

## Database migration

None. All database operations were read-only.

## Rollback

Remove the new `docs/agentic-rag/` files and revert the Phase 0 status row in `plan.md`. No runtime or database state needs rollback.

## Required user decisions before Phase 1

1. Approve the Git root (`计算机设计大赛2.0/` is recommended) and creation of a baseline repository/commit, or provide the intended existing remote/worktree.
2. Rotate the exposed Dune/Etherscan keys and confirm whether Codex may replace `scse-discover.yaml` values with environment placeholders.
3. Confirm whether local development should pin Lean to the plan target 4.31.0 or adopt a different target after team review.
4. Provide or identify the real Nginx/deployment/backup configuration if it exists outside this folder.
