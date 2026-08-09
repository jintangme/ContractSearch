# Service Map and Ownership

> Verified against the local processes and source on 2026-08-09.

## Request topology

```text
Browser (:5173 in local development)
  ├─ /api/* -> Vite proxy / deployment Nginx -> Gateway :18080
  │                                      -> smart-contract-search-engine :18081
  ├─ contract runtime direct -> :19090
  └─ Lean runtime direct     -> :19100

Gateway/Nacos
  ├─ cloud-gateway                  registered
  └─ smart-contract-search-engine   registered

Data
  └─ ParadeDB/PostgreSQL :5432
```

The production Nginx configuration is not present in the repository. Phase 1 must not assume that the current Vite development topology is the production topology.

## Ownership matrix

| Capability | Current owner | Future Agent integration |
|---|---|---|
| Authentication/JWT | Gateway + `cloud-service` auth implementation | Gateway authenticates; Agent receives signed/trusted context |
| Contract CRUD/detail | `cloud-service` | typed internal tool facade; no duplicated repositories |
| Semantic/code search | `cloud-service` | `search_contracts.v1` adapter, then evidence-ready retrieval |
| AI Chat/precheck/explain | `cloud-service` | provider/client reuse; old UI remains fallback |
| Draft/favorite/history | `cloud-service` | user-scoped Workspace tools; writes require proposal/policy |
| Agent state/evidence | Phase 1 in-memory mock implemented, persistence disabled | `cloud-agent-service` owns durable implementation |
| Solidity compile/deploy/call | `contract-runtime-service` | sandbox tools after version/isolation hardening |
| Lean checking | `lean-runtime-service` | bounded proof tool after environment pinning |
| Contract discovery/import | `scse-discover` | offline ingestion source; not an online Agent tool |

## Phase 1 service addition (implemented, default off)

```text
Gateway :18080
  -> /api/agent/**
  -> cloud-agent-service :18082
       ├─ OpenFeign -> smart-contract-search-engine
       ├─ typed HTTP -> contract runtime
       └─ typed HTTP -> Lean runtime (disabled in Phase 1A)
```

`cloud-agent-service` owns orchestration only. Search, contracts, users and runtime execution remain with their current owners.

Activation requires both `AGENT_ROUTE_ENABLED=true` in Gateway and `AGENT_ENABLED=true` in Agent Service, with the same non-committed `AGENT_INTERNAL_TOKEN`. Database migration is an independent `AGENT_MIGRATIONS_ENABLED` flag and remains off in the running system.

## Local restart dependency order

When a later phase changes services, restart in this order:

1. PostgreSQL/ParadeDB and Nacos only if their configuration changed.
2. `contract-runtime-service` / `lean-runtime-service` if their source or environment changed.
3. `cloud-service`, then `cloud-agent-service`.
4. `cloud-gateway` after route/config changes.
5. Vue/Vite after `.env`, API client, router or UI changes.

Phase 1 code changes require `cloud-service` and `cloud-gateway` restarts only when the new build is deployed. The new `cloud-agent-service` is not part of the currently running stack until explicitly started.
