# Phase 1A Report: Minimal Agentic RAG Validation

> Status: COMPLETE — minimal validation accepted by project owner
> Decision: GO_TO_PHASE_2_WITH_DEFERRED_EVALUATION
> Date: 2026-08-09

## Outcome

The first real, bounded Agentic RAG loop is implemented in `cloud-agent-service` and remains default-off. It can ask `glm-4.5-air` for one structured action at a time, execute only three allowlisted Tools, feed normalized observations back across an explicit untrusted-data boundary, and finish only with known Evidence IDs. A deterministic substitute provider proves the complete state loop. The three production adapters also passed a live local `cloud-service -> PostgreSQL/ParadeDB -> contract-runtime-service` integration test against the current contract catalog.

The project owner subsequently completed a real `glm-4.5-air` Agent Run from the local terminal and confirmed success. On 2026-08-09 the owner accepted this real run, the live Search/Get/Compile chain and the critical automated tests as the Phase 1A minimum gate. Phase 2 may start. The 30-case A/B/C comparison is explicitly deferred to Phase 9 to avoid unnecessary model-call cost; therefore this decision is an engineering readiness decision, not a measured claim that Agentic RAG already outperforms Direct GLM or Naive RAG.

## Modified scope

- Extended `AgentProperties` with a default-off `mini-loop` profile and fixed dependency endpoints.
- Added hard budgets: at most 6 model steps, 4 Tool calls and 2 search rounds. Budget exhaustion always reaches `FAILED/BUDGET_EXCEEDED`.
- Added `search_contracts.v1`, `get_contract.v1` and `compile_contract.v1` typed Tools with versioned JSON Schema, strict field/type checks, size limits, timeouts, error mapping and deterministic side-effect policy.
- Added fixed HTTP adapters for existing Search/Contract APIs and the isolated compile runtime. The model cannot provide a URL or path.
- Added a backward-compatible `regenerateAbi=false` read-only detail mode in `cloud-service`; the existing endpoint still defaults to `true`, while Agent calls cannot trigger lazy ABI compilation or database updates.
- Made compile accept only `contractId`; Agent Service fetches the source itself. Deploy, call, Lean, shell, file access, persistence and arbitrary network access are not registered.
- Added `StructuredActionProvider` plus the `glm-4.5-air` OpenAI-compatible implementation. Provider Tool IDs map from `name__vN` back to internal `name.vN`.
- Added exactly one structured-output repair attempt. A second parse failure is terminal `PROVIDER_ERROR`.
- Added `phase1a-supervisor-v1`, raw structured output capture, provider call count, latency, finish reason and usage capture when present.
- Added an explicit `UNTRUSTED_TOOL_OUTPUT` boundary. Contract text and compiler diagnostics are evidence, never instructions; closing-tag injection is escaped.
- Normalized search matches, contract source and compiler diagnostics into Evidence. Contract-library content is `COMMUNITY`; compiler facts are `TOOL_GENERATED`.
- Added final-answer grounding checks: at least one Evidence ID is required and every cited ID must belong to the current run.
- Extended Agent run output/OpenAPI with `errorCode`, `failureMessage`, `steps`, `toolCalls` and `retrievalRounds`.
- Bounded model context: search descriptions are truncated, source returned to the model is capped at 120,000 characters, Evidence excerpts at 6,000, diagnostics at 20 items, and bytecode/ABI are excluded from compiler observations.

The `cloud-service` detail endpoint gained one optional query parameter with its old behavior preserved by default. No Gateway public route shape, PostgreSQL table, Vue route, Contract Lab behavior or runtime endpoint was otherwise changed.

## Verification

- Agent module: 27 tests, 0 failures; 2 environment-gated tests skipped in the generic run.
- New deterministic coverage: three-Tool happy path, evidence citation, unknown evidence rejection, retrieval budget, Tool policy, strict argument types, extra-field rejection, provider-safe names, one-repair limit and prompt-injection boundary.
- Read-only detail coverage proves `regenerateAbi=false` invokes neither the ABI compiler nor `updateById`.
- Real local Tool integration: 1 test, 0 failures; about 45 seconds on the first cold compiler path and 1.3 seconds against the final updated temporary `cloud-service` on port 18083.
- Live chain exercised: code search on the current catalog, contract detail retrieval and sandbox compilation.
- Live observation contract contains compile status/errors/warnings and Evidence, but not bytecode or ABI.
- The final live test used `regenerateAbi=false` on the updated service. The temporary port 18083 service was stopped cleanly; the user's existing port 18081 process was not restarted.
- No database migration was enabled and no write Tool was executed.

## Deferred to Phase 9 Hardening

1. Run `probe-glm-contract.mjs` from the same process that has `ZHIPU_API_KEY`.
2. Start Agent Service with `AGENT_MINI_LOOP_ENABLED=true` and execute representative real runs through the trusted Gateway path.
3. Calibrate the 30 seed cases with concrete catalog IDs/source fixtures for get-only and compile-error cases. The current seed tasks are a schema/harness set and are not yet a fully executable gold dataset.
4. Run A `DIRECT_GLM`, B `NAIVE_RAG` and C `AGENTIC_RAG` with the same model parameters and corpus snapshot.
5. Human-grade Tool selection, task completion, key-claim coverage and unsupported claims; generate latency/token/failure taxonomy.
6. Confirm the Phase 1A Gate and replace this `ITERATE` decision with evidence-backed `GO` or `NO_GO`.

## Manual test checklist

### Default-off regression

- Keep `AGENT_ENABLED=false`, `AGENT_ROUTE_ENABLED=false`, `AGENT_MINI_LOOP_ENABLED=false`.
- Confirm existing Search, AI Chat, Contract Lab and favorites work exactly as before.
- Confirm `/api/agent/v1/runs` is unavailable through Gateway.

### Real Tool chain without model

- Ensure ports 18081 and 19090 are running.
- Run `RUN_AGENT_TOOL_INTEGRATION=true mvn -pl cloud-agent-service -Dtest=AgentToolsLiveIntegrationTest test`.
- Expect one passing test; no source, bytecode, ABI or credential should appear in output.

### Controlled real Agent run

- Export `ZHIPU_API_KEY` in the same terminal used to start Agent Service.
- Use a random internal token of at least 24 characters and configure the identical token in Gateway.
- Enable Agent and mini loop, but keep migrations disabled.
- Ask for a code-search task, a candidate-detail task and a compile-verification task.
- Verify terminal `COMPLETE` or explicit `FAILED`, counters never exceed 6/4/2, ordered events contain Tool call IDs, and final Evidence IDs all exist in `/evidence`.
- Test an unknown Tool instruction, an extra argument such as `url`, source-comment prompt injection and repeated search. Expect policy denial, argument rejection, ignored embedded instructions and `BUDGET_EXCEEDED` respectively.
- Verify deploy/call/Lean/favorite/draft/write actions are never offered.

## Services to restart

No user service was restarted for this implementation; the currently running stack remains intact.

For a controlled deployment:

1. Rebuild and start `cloud-agent-service` to load this code.
2. Restart `cloud-gateway` only when deliberately enabling the Agent route and configuring the matching internal token.
3. Restart `cloud-service` once to load the new read-only detail parameter before enabling the mini loop. Existing clients retain the old default behavior.
4. Do not restart PostgreSQL/ParadeDB, Nacos or `contract-runtime-service`.
5. Vue does not need a restart because Phase 1A adds no UI route.
6. `lean-runtime-service` stays disabled and does not need a restart.

## Risks and boundaries

- Runs and events remain in memory; process restart loses them.
- `POST /runs` executes synchronously in this spike; later Agent Core work must move long runs to a recoverable async executor and stream events while executing.
- Contract-library content is not trusted as instructions even though it comes from the local database.
- The direct dependency ports must remain private. Agent API access still requires the Gateway-injected internal token.
- A successful compile proves only compiler acceptance in the sandbox, not security, deployability or semantic correctness.
- AgentScope remains intentionally absent. There is no evidence yet that a Python sidecar improves this bounded Java loop.

## Rollback

- Set `AGENT_MINI_LOOP_ENABLED=false`; the deterministic Phase 1 mock remains available only if `AGENT_ENABLED=true`.
- Set `AGENT_ROUTE_ENABLED=false` and `AGENT_ENABLED=false` to remove all Agent reachability.
- Stop `cloud-agent-service` if required. Existing Chat, Search and Contract Lab continue independently.
- Revert the Phase 1A code commit if code rollback is needed. This checkpoint adds no database migration and requires no data rollback.
