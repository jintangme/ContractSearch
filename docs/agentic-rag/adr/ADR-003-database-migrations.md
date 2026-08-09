# ADR-003: Versioned Database Migrations

- Status: Accepted for Phase 1
- Date: 2026-08-09

## Context

No Flyway/Liquibase setup exists, and conversation tables can be created from `@PostConstruct`. Agent state and Evidence add several related tables that require deterministic ordering and rollback planning.

## Decision

Introduce Flyway in Phase 1. Baseline the existing live schema without recreating existing objects. Use expand/contract migrations: add new tables/columns/indexes first, deploy compatible code, migrate/backfill, and remove legacy behavior only in a later release.

For the current non-empty database, use Flyway baseline version `0`; the first managed migration is `V1__agent_foundations.sql`. V1 must use idempotent, validated definitions for existing conversation and user-contract tables before adding Agent/Evidence objects. A fresh-database test and a clone-of-live migration test are required before touching the running database.

Keep the current startup DDL behind a compatibility flag for one release, verify schema equivalence, then remove it.

## Consequences

- Schema history becomes reviewable and reproducible.
- Initial baseline requires careful comparison of the live database and `pg18.sql`.
- Destructive down migrations are not the default rollback mechanism.

## Rollback

Roll back the service and feature flags while retaining additive tables. Use corpus/index pointers for data rollback.
