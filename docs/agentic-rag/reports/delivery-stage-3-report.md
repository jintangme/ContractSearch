# Delivery Stage 3 Report: Iterative Agent Answer Loop

> Status: READY_FOR_SMOKE  
> Next: restart updated services, run one real GLM smoke, then Delivery Stage 4  
> Date: 2026-08-09

## Outcome

ContractSearch now has the requested iterative Agent mechanism in `cloud-agent-service`: GLM chooses a bounded Tool action, observes real output as Evidence, then decides again whether to retrieve more information, validate a candidate, complete with citations, or explicitly abstain. This is not a single RAG request and does not require AgentScope or a Python sidecar.

The fixed Tool allowlist is `retrieve_knowledge.v1`, `search_contracts.v1`, `get_contract.v1`, `compile_contract.v1`, and `precheck_contract.v1`. Lean remains outside the default loop because Solidity-to-Lean translation is not yet a trustworthy automatic verification path.

## Implementation

- Supervisor decisions now contain auditable `reasonSummary`, `evidenceGap`, `citedEvidenceIds`, and `stopReason`; raw model output and private chain-of-thought are not persisted.
- The model is called again after each Tool observation. Identical Tool + arguments repetition is rejected with `LOOP_DETECTED`.
- `COMPLETE` requires real Evidence IDs from the current run. `ABSTAIN` is an explicit successful terminal state for insufficient evidence.
- The default Tool budget is 5 so one task can Search/Retrieve, Get, Compile, and Precheck. Once exhausted, the next model turn is terminal-only and must answer from existing Evidence or abstain instead of discarding a useful run as `BUDGET_EXCEEDED`.
- `precheck_contract.v1` reads the selected contract and calls the existing precheck service with `saveRecord=false`; Agent-internal checks do not create user precheck-history records.
- V6 additively extends Agent audit fields and indexes. Terminal sessions, runs, events, Evidence, and proposals can be persisted transactionally when `AGENT_PERSISTENCE_ENABLED=true`.
- Persistence remains default-off and validates its database URL/username before startup.
- Existing standalone AI Chat, Search, Contract Lab, precheck behavior, and old APIs remain compatible.

## Minimal verification

- Focused reactor build: `BUILD SUCCESS`.
- Tests: 23 passed, 0 failed, covering V1-V6 migration contracts, structured GLM actions, four-Tool iterative loop, loop detection, invented Evidence rejection, explicit abstention, Tool adapters, and old conversation DDL compatibility.
- Deterministic full loop completed five decision steps and four Tool observations: Retrieve -> Get -> Compile -> Precheck -> cited final answer.
- V6 was applied successfully to the local PostgreSQL/ParadeDB schema.
- Persistence smoke run `090badaf-b12c-475f-b42d-81d96072eaaa` archived a COMPLETE run with prompt version `delivery-stage3-supervisor-v2`, 5 events, 1 Evidence item, and 1 proposal.
- No paid GLM smoke was made from this shell because `ZHIPU_API_KEY` is not exported in the current Codex process. The existing processes were not terminated by this stage; the final health recheck found ports 18081, 18082, 19090, and 18083 no longer listening, so the local backend stack must be started before manual smoke.
- The first owner-run live smoke proved four observation/redecision cycles and collected 8 Evidence items, but attempted Knowledge retrieval as a fifth Tool and ended with `BUDGET_EXCEEDED`. The default was therefore raised to 5 bounded Tool calls, retrieval tools are hidden after their own budget is exhausted, and a terminal-only final turn was added. The focused regression for this correction passed 12 tests with 0 failures; one Agent restart and one repeat of the same smoke remain.

## Manual test checklist

1. Restart `cloud-service`, then require `GET http://127.0.0.1:18081/actuator/health` to return `UP`.
2. Restart `cloud-agent-service` with the same trusted internal token, `ZHIPU_API_KEY`, mini loop and Knowledge enabled. Enable persistence with the V6 database configuration if audit records are required.
3. Require `GET http://127.0.0.1:18082/actuator/health` to return `UP`.
4. Submit one representative task: `搜索一个包含 ERC20 实现的合约，读取源码，验证能否编译，并做一次安全预审后给出有证据的结论`.
5. Inspect run events and confirm at least one `model.action` occurs after a `tool.completed`; the final response must cite returned Evidence IDs or explicitly abstain.
6. If persistence is enabled, confirm the terminal run, event, and Evidence rows exist in the Agent tables.
7. Confirm the standalone precheck-history list did not gain a record from the Agent-internal precheck.

## Services and restart scope

- Restart required: `cloud-service` (for `saveRecord=false`) and `cloud-agent-service` (for the loop, Tool, prompt, gate, and persistence changes).
- No restart required: Vue frontend, Nginx, Gateway, PostgreSQL/ParadeDB, Nacos, `contract-runtime-service`, or `lean-runtime-service`, provided they are already healthy.
- Database action: V6 is already applied locally; do not re-run a manual migration. Flyway will recognize it as applied.

## Configuration additions

```text
AGENT_PERSISTENCE_ENABLED=true
AGENT_PERSISTENCE_DB_URL=<same PostgreSQL JDBC URL>
AGENT_PERSISTENCE_DB_USERNAME=<database user>
AGENT_PERSISTENCE_DB_PASSWORD=<database password>
```

The existing `AGENT_ENABLED`, `AGENT_MINI_LOOP_ENABLED`, `AGENT_KNOWLEDGE_ENABLED`, `AGENT_INTERNAL_TOKEN`, `ZHIPU_API_KEY`, dependency URLs, and Knowledge database/corpus settings are still required for the real loop.

## Risk and rollback

- Disable `AGENT_PERSISTENCE_ENABLED` to return to in-memory terminal records.
- Disable `AGENT_MINI_LOOP_ENABLED` to return to the mock path without changing the old AI Chat.
- If the new precheck adapter causes an issue, remove `precheck_contract.v1` from the Tool allowlist; standalone precheck remains available.
- V6 is additive. Normal rollback is feature-flag rollback, not dropping columns or restoring the database.
