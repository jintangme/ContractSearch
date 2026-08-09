# Phase 0 Report: Repository Discovery and Fact Baseline

> Date: 2026-08-09  
> Result: COMPLETE
> Business code changed: Discovery credential-loading hardening only

## Outcome

The repository, running local services, live database, OpenAPI surface, model path, search baseline and runtime behavior were audited. The project now has a sanitized Git baseline, discovery credentials are outside Git/Nacos, and Lean `4.31.0` is the confirmed target. Phase 1 may start without enabling Lean as an Agent Tool.

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
- `docs/agentic-rag/eval/phase0-smoke-queries.json`
- `docs/agentic-rag/eval/phase0-search-baseline.json`
- `scripts/agentic-rag/run-phase0-search-baseline.mjs`
- `.gitignore`
- `scse-discover/app/main.py`
- `scse-discover/app/services/task_service.py`
- `scse-discover/tests/test_secret_config.py`
- discovery config/examples/docs and Python requirements

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
| Search smoke baseline | PASS; 24/24 requests completed, average 685 ms |

## Build verification

| Command/scope | Result |
|---|---|
| Maven reactor tests | PASS; 4 Spring context tests, 0 failures/errors, all 5 reactor projects successful |
| Vue production build | PASS; output written to `/private/tmp`, project `dist/` not modified |
| Runtime JavaScript syntax | PASS for both Node entry points |
| Baseline JSON parse | PASS |
| Discovery secret-loading tests | PASS; 3 tests |
| Staged known-secret scan | PASS; no known exposed key values and no real `.env` tracked |

Non-blocking warnings recorded for later cleanup: Spring Cloud Gateway uses temporarily migrated property keys; Nacos test logging targets a home-directory log path; the backend reports multiple SLF4J providers and no Jakarta Validation provider; the Vue build has a roughly 2.56 MB Monaco/editor chunk. These warnings predate Phase 0 and no source fix was made in this documentation-only stage.

## Important findings

1. Discovery credentials were present in the root config, `.env`, an instruction file and a cached Nacos snapshot. Tracked/Nacos copies are now sanitized; the local `.env` remains ignored. Provider-side rotation is still recommended if any old copy was shared.
2. Backend direct access trusts `X-User-Id`, so port 18081 is a privileged internal boundary.
3. Runtime versions drift from documentation/package intent; Lean is currently unusable and request-time downloads violate reproducibility.
4. Search request `limit` is ignored and RRF score is not exposed. All 12 semantic baseline queries returned `SimpleStoragePlus` as Top-1; 20/24 total queries returned 100 rows despite requesting 10.
5. Corpus duplication and embedding gaps are large enough that Phase 2 must rebuild a versioned shadow corpus rather than reuse rows blindly.
6. No production Nginx configuration or deployment/backup runbook was found.

## Exit Gate assessment

| Gate | Status |
|---|---|
| Facts have evidence or are explicit blockers | PASS |
| No production/database writes | PASS |
| Adapter ownership is documented | PASS |
| Recoverable version-control baseline | PASS — root initialized and sanitized baseline committed |
| Lean current/target/fix is unambiguous | PASS — target 4.31.0 confirmed; runtime pin/health enforcement required before enabling the Tool |
| Secret handling safe for new work | PASS — Git/Nacos use environment references; old provider keys require owner rotation only if previously shared |

Phase 0 is recorded as `COMPLETE`.

## Manual test checklist

- [ ] Open `http://localhost:5173` and confirm login, Browse, Search, Conversation, Contract Check and Contract Lab pages load.
- [ ] Log in and run one semantic search; confirm results appear and note that more than requested may be returned.
- [ ] In Contract Lab, compile the default Solidity example; confirm compiler version shown is 0.8.35 in the current installation.
- [ ] In Proof Workbench, check `example : True := by trivial`; confirm the current failure/timeout before any Lean fix.
- [ ] Open Nacos and confirm only `cloud-gateway` and `smart-contract-search-engine` are registered.
- [ ] Confirm ports 18081, 19090 and 19100 are not exposed outside the trusted local/private network.
- [ ] Install the updated discovery requirements under Python 3.11, then verify `/health` with `DUNE_API_KEY` and `ETHERSCAN_API_KEY` supplied locally.
- [ ] If an old project copy was shared, rotate the Dune/Etherscan keys before triggering discovery.

## Restart requirements

No currently running service requires restart: `scse-discover` was not running. Before its next start, install the updated Python requirements. Nacos configuration was updated dynamically and does not require a Nacos restart.

## Database migration

None. All database operations were read-only.

## Rollback

Use the baseline commit to inspect/revert later changes. To undo discovery hardening, restore its previous code/config only from a secure local copy; do not recommit plaintext credentials. No database rollback is required.

## Remaining environment input

Provide or identify the real Nginx/deployment/backup configuration if it exists outside this folder. Its absence is documented and does not block building the feature-flagged local Phase 1 skeleton.
