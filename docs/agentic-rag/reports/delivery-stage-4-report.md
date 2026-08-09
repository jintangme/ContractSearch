# Delivery Stage 4 Report: Existing AI Chat Integration

> Status: READY_FOR_SMOKE
> Date: 2026-08-09

## Outcome

The existing `/conversation` page is now the Agent workspace; no separate Agent page was added. Agent mode is the default and ordinary AI Chat remains one click away. The page submits authenticated Agent runs through Gateway, displays completed Tool steps, Evidence trust levels and IDs, and preserves an explicit ordinary-chat fallback when Agent is unavailable or fails.

## Implementation

- Added an `Agent / 普通对话` mode switch to the existing conversation header.
- Agent mode uses the existing JWT-aware `agentApi`, a 300-second bounded request timeout, one Agent session per current UI conversation, and fixed `glm-4.5-air` labeling.
- Completed runs display decision/Tool/retrieval counts, readable Tool names, expandable Evidence, trust badges, and terminal failure information.
- Failed Agent messages expose `切换普通对话重试`; fallback does not happen silently and therefore does not unexpectedly double model cost.
- Contract attachments preserve `contractId`. Contract detail provides `交给 Agent 验证`; Search provides `让 Agent 继续检索与验证`; precheck results provide `在 Agent 对话中继续`.
- Detail routing preselects Agent mode, attaches the contract and pre-fills a verification task without executing it automatically.
- Backend Workspace Context is now sanitized to an allowlist and passed to every Supervisor decision. `selectedContractId` is a routing reference, not Evidence, and directs Get/Compile/Precheck to the exact selected contract.
- Prompt version is `delivery-stage4-workspace-v1`.
- Raw local/workspace file attachments remain available in ordinary Chat only. Agent mode currently accepts catalog contract references; it does not pretend it can verify an unregistered local file.

## Minimal verification

- Frontend production build passed: 809 modules transformed.
- Focused Agent tests passed: 11 tests, 0 failures.
- Browser verification used the existing signed-in local session without sending a model request:
  - `/conversation` shows Agent mode and `glm-4.5-air`.
  - `/contract/378` shows `交给 Agent 验证`.
  - Clicking it opens `/conversation`, attaches `Wrapped_Ether.sol`, and pre-fills the verification task.
  - Switching to ordinary Chat restores the normal model selector.
  - Browser console contained no error or warning entries.
- No additional GLM call was made for this implementation checkpoint.

## Manual test checklist

1. Restart only `cloud-agent-service` with the existing Stage 3 environment variables; Gateway, cloud-service, runtimes and database may remain running.
2. If Vite is already running, allow hot reload; otherwise restart `frontend` with `npm run dev`.
3. Open a contract detail and click `交给 Agent 验证`.
4. Confirm `/conversation` opens in Agent mode with the contract chip and pre-filled prompt.
5. Click Send once. Require `Agent 任务完成`, at least one completed Tool step, expandable Evidence, and a cited final answer.
6. Start a new conversation, switch to `普通对话`, send one short question, and confirm the existing conversation/history flow is unchanged.
7. Optional failure check: stop Agent Service, submit in Agent mode, and confirm the UI offers the ordinary-chat retry without losing the user task. Restart Agent immediately afterward.

## Restart and rollback

- Restart required: `cloud-agent-service`.
- Conditional restart: frontend only if Vite hot reload is not active.
- No restart required: Gateway, cloud-service, Nacos, PostgreSQL/ParadeDB, contract runtime or Lean runtime.
- UI rollback: switch to `普通对话`; existing Chat APIs and history remain unchanged.
- Deployment rollback: disable `AGENT_ROUTE_ENABLED` or `AGENT_ENABLED`; the conversation page will expose a clear Agent failure and ordinary-chat fallback.
