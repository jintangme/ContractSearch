# Phase 1 Report: Foundations, Contracts and Evaluation Harness

> **Historical record — 2026-08-09.** Archived on 2026-10-09.
> This describes that checkpoint, not the current release or current verification status.
> Historical test results and limitations are retained; private run identifiers, backup
> identifiers and host-specific observations have been omitted or generalized.
> See [current project status](../../../status.md) before following any operational steps.

> Checkpoint status: COMPLETE — minimal live validation accepted; full protocol matrix deferred to Phase 9
> Date: 2026-08-09

## Outcome

The Java-native Agent foundation is implemented and default-off. `cloud-agent-service` starts behind the Gateway, reports healthy, rejects direct untrusted Agent requests, and completes a deterministic mock Tool loop with ordered SSE, Evidence and a reviewable Proposal. Existing Chat, Search, Contract Lab and runtime behavior remain unchanged.

The Codex process could not inherit the `ZHIPU_API_KEY` stored in another terminal or IDE configuration, so no secret-recovery attempt was made. The project owner subsequently started the service in the correctly configured terminal and confirmed a real `glm-4.5-air` Agent Run succeeded. On 2026-08-09 the owner accepted this as the minimal live validation and deferred the streaming/thinking/invalid-parameter protocol matrix to Phase 9 to avoid unnecessary model-call cost.

## Implemented scope

- Added the Maven module `cloud-agent-service` with Nacos discovery/config support, OpenFeign, Actuator, Micrometer/Prometheus, validation and SpringDoc.
- Added default-off flags for Agent API, Gateway route and Flyway migration. Enabling Agent/Gateway requires the same internal token with at least 24 characters.
- Added the state machine `RECEIVED -> PLAN -> TOOL_CALL -> OBSERVE -> SYNTHESIZE -> COMPLETE`, bounded retry transition and terminal failure transition.
- Added versioned Tool contracts, JSON-object validation, unknown Tool rejection, `SideEffect`, error codes and Trust levels.
- Added in-memory Session/Run/Event/Evidence/Proposal models and a deterministic `mock_echo.v1` Tool.
- Added run/session/evidence/proposal REST contracts and bearer-compatible `fetch` SSE with sequence resume.
- Added trace ID propagation, key-value structured log context, run counters, Actuator health/metrics and Prometheus output.
- Added an OpenAI-compatible structured-action client fixed to `glm-4.5-air`; internal `name.vN` Tool IDs are mapped to provider-safe `name__vN` names.
- Added Flyway 13.2.0, baseline version 0 and additive `V1__agent_foundations.sql`. V1 creates legacy conversation/user-record tables when absent and adds Agent foundation tables. It does not touch `smart_contract` or embedding columns.
- Kept legacy conversation startup DDL for one compatibility release behind `LEGACY_CONVERSATION_DDL_ENABLED`.
- Added a 30-case seed fixture and deterministic A/B/C runner producing 90 JSONL rows. Output is explicitly marked harness-only, not a model-quality result.
- Added ADR-006 (Java orchestrator) and ADR-007 (`glm-4.5-air` Agent profile). AgentScope remains an optional post-Phase-1A comparison only.

## Migration validation

No migration was applied to the live `public` schema.

Two disposable schemas were created and automatically removed:

1. Fresh disposable schema: V1 applied once; the second migrate executed zero migrations.
2. Disposable schema with cloned legacy tables: the four relevant live legacy table structures were copied, Flyway baselined the non-empty schema at version 0, V1 applied once, and the second migrate executed zero migrations.

Both ran against PostgreSQL 18.3. Flyway 11.8.2 initially reported that PostgreSQL 18 was outside its verified range, so it was replaced with current stable Flyway 13.2.0 and both tests were rerun without that compatibility warning.

## Verification results

- Full Maven reactor: BUILD SUCCESS across Gateway, Service, Auth, User and Agent modules.
- Full reactor tests: 18 passed; the environment-gated PostgreSQL integration test was skipped in the generic run and passed separately in both fresh and live-clone modes.
- Agent module unit/contract tests: state transitions, terminal-state rejection, unknown Tool, invalid JSON, feature-off 404, trusted-token rejection, ownership isolation, OpenAPI/frontend compatibility, migration content and GLM mocked transport all passed.
- Live Agent smoke: health `UP`, missing internal token `401`, mock run `COMPLETE`, five SSE frames, one Evidence item, one pending Proposal, and trace ID round trip.
- Vue production build: passed. Existing Monaco chunk-size warning remains.
- Eval harness: 30 fixtures × 3 variants = 90 JSONL rows, deterministic mock adapter.
- Solidity runtime contract: valid compile `200`, solc `0.8.35`, one compiled contract; invalid compile `400` with structured error.
- Lean runtime contract: returned `LEAN_TIMEOUT` after about 10 seconds; target 4.31.0 remains unverified and Lean stays disabled as an Agent Tool.
- GLM mocked contract: request shape, model, thinking flag, provider-safe Tool name, authorization header and structured Tool Call response parsing passed.
- GLM live contract: `BLOCKED_MISSING_ZHIPU_API_KEY`.

## Known limitations

- Phase 1 storage is intentionally in-memory. V1 persistence tables exist only as a migration contract and have not been activated.
- The mock loop is deterministic and does not call Search, contract detail or compile. Those typed adapters belong to Phase 1A.
- The 30 seed cases have not been human graded; they are the first subset of the planned 120-case golden set.
- The production Nginx configuration is still outside the repository. SSE proxy buffering and idle timeout must be validated before deployment.
- Existing Gateway renamed-property, SLF4J provider, Jakarta validation and frontend Monaco warnings remain outside this change.
- Lean runtime is not version-pinned to 4.31.0 and currently times out.

## Deferred protocol audit

Expose `ZHIPU_API_KEY` to the terminal process without writing it to Git, then run:

```bash
node scripts/agentic-rag/probe-glm-contract.mjs
```

The resulting report must show the actual synchronous Tool Call, streaming deltas, invalid-parameter behavior, client timeout enforcement and thinking toggle behavior. If any field is incompatible, preserve the report as a fixture and adapt the provider client before Phase 1A.

## Restart and activation

No temporary test service remains running. The current user stack was not restarted.

When deploying this checkpoint:

1. Restart `cloud-service` to load the legacy DDL compatibility flag; its default behavior remains unchanged.
2. Start `cloud-agent-service` only for a controlled mock test. Keep migrations disabled.
3. Restart `cloud-gateway` only if enabling the route; keep `AGENT_ROUTE_ENABLED=false` until the Agent internal token is configured.
4. Restart/rebuild Vue only when using the new API client; no Agent UI route is added in Phase 1.

Nacos, PostgreSQL, contract runtime and Lean runtime do not need a restart for this checkpoint.

## Rollback

- Set `AGENT_ROUTE_ENABLED=false`, `AGENT_ENABLED=false` and `AGENT_MIGRATIONS_ENABLED=false`.
- Stop/remove `cloud-agent-service` and revert the Gateway route configuration.
- Revert the frontend Agent API client if necessary.
- Leave additive Agent tables in place if V1 is eventually deployed; do not drop them automatically.
- Keep `LEGACY_CONVERSATION_DDL_ENABLED=true` to retain the old startup behavior.
