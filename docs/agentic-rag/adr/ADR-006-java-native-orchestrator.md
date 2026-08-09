# ADR-006: Java-native Agent Orchestrator

- Status: Accepted
- Date: 2026-08-09

## Context

ContractSearch already uses Spring Cloud Gateway, Spring Boot, Nacos, OpenFeign and Java-owned user/search/conversation capabilities. Introducing AgentScope as the primary orchestrator would add a second service framework, duplicate state ownership and require a new internal authentication boundary before the value of Agentic RAG is established.

## Decision

Implement the MVP orchestrator as the Java 17 module `cloud-agent-service`. It owns state transitions, budgets, Tool policy, Evidence and Proposal records. Existing services remain the owners of search, contracts and user data. Runtime execution remains in the isolated Node services.

AgentScope is not a production dependency. After Phase 1A produces Java baseline results, a time-boxed sidecar comparison is allowed only with the same 30 fixtures, model profile, tools and budgets. The sidecar may not connect directly to PostgreSQL or register shell/file/python tools.

## Consequences

- One state/evidence source of truth and consistent operational conventions.
- The first implementation has less framework-provided agent abstraction, so state transitions and Tool policy are explicit domain code.
- AgentScope remains a measurable alternative instead of an architectural prerequisite.

## Rollback

Disable the Gateway route and `AGENT_ENABLED`; remove the module from the Maven reactor. Existing AI Chat and Search are unaffected.
