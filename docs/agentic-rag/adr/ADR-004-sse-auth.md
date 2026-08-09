# ADR-004: Agent SSE Authentication and Resume

- Status: Proposed; implementation details must be validated in Phase 1
- Date: 2026-08-09

## Context

The existing frontend stores a bearer token and Axios attaches it to API requests. Native browser `EventSource` cannot set an Authorization header. Agent runs need streaming, reconnect and strict per-user isolation.

## Decision

Use authenticated run creation over normal HTTP. For event streaming, prefer `fetch` streaming with the bearer header. If an EventSource-compatible fallback is required, issue a short-lived, single-run stream ticket; never place the long-lived JWT in a query string.

Persist monotonically increasing event sequence numbers. Resume uses `after`/cursor and rechecks run ownership on every connection. Gateway removes external `X-User-Id` and injects trusted identity; internal direct ports must not be public.

## Consequences

- Frontend needs an AbortController-based stream client.
- Nginx/Gateway must disable buffering and support heartbeat/idle timeouts.
- Reconnect behavior is testable and does not replay completed side effects.

## Rollback

Disable streaming and expose polling for run status/events while preserving the same ownership checks.

