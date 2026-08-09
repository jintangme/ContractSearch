# ADR-007: Agent MVP Model Profile

- Status: Accepted
- Date: 2026-08-09

## Context

The existing Chat model registry allows several GLM models and currently defaults to `glm-5.1`. The Agentic RAG architecture needs a fixed, inexpensive model during protocol and value validation so model changes do not invalidate A/B/C comparisons.

## Decision

The Phase 1/1A Agent profile is fixed to `glm-4.5-air`. This does not change the existing Chat default. Temperature, thinking mode, timeouts, Tool registry version, prompts, corpus snapshot and budgets must be recorded with every evaluation run.

Internal Tool names use `name.vN`. Because OpenAI-compatible function-name constraints are narrower, the provider adapter maps `name.vN` to `name__vN` on request and reverses the mapping when parsing Tool Calls.

A stronger model may be tested only as a new named profile against the same frozen fixtures. Promotion requires improved hard-gate compliance and measured task/evidence quality without unacceptable latency or cost regression.

## Validation

`scripts/agentic-rag/probe-glm-contract.mjs` probes synchronous and streaming Tool Calling, thinking toggles, invalid-parameter rejection and client timeout behavior. The saved report is sanitized and never contains credentials.

## Rollback

Disable the Agent feature. Do not silently fall back to the existing Chat default because that would make evaluation results incomparable.
