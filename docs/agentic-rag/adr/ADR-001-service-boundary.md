# ADR-001: Agent Service Boundary

- Status: Accepted for Phase 1
- Date: 2026-08-09

## Context

The current control plane is Java/Spring Cloud. Search, contracts, users, conversations and AI features are already implemented in `cloud-service`; Solidity and Lean execution are separate Node services.

## Decision

Add one Java 17 Spring Boot Maven module, `cloud-agent-service`, to the existing reactor. It owns run state, orchestration, Tool policy, budgets and evidence. It calls existing business capabilities through typed internal APIs and does not duplicate repositories or business rules.

Do not make AgentScope a production dependency for MVP. An AgentScope/FastAPI sidecar may be evaluated only after Phase 1A against the same tools, fixtures and budgets.

## Consequences

- Reuses Gateway, Nacos, OpenFeign and the existing security/data conventions.
- Adds one deployable Java service and one Gateway route.
- Requires explicit service-to-service authentication because the backend currently trusts `X-User-Id`.
- The optional Python experiment remains removable and cannot become a second source of run/evidence truth.

## Rollback

Remove/disable the `/api/agent/**` route and service registration. Existing Chat/Search remain unchanged.

