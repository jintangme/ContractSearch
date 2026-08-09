# Agent Foundations Migration Runbook

## Safety defaults

- `AGENT_MIGRATIONS_ENABLED=false` by default.
- `AGENT_ENABLED=false` and `AGENT_ROUTE_ENABLED=false` by default.
- V1 is additive and must not alter `smart_contract` or its embedding columns.
- Never test V1 first against the running `mydatabase` database.

## Required pre-production sequence

1. Create an empty disposable PostgreSQL database and run V1 twice. The second run must report no pending migration.
2. Create a disposable database from a schema-only dump of the running database.
3. Configure Flyway with `baselineOnMigrate=true` and `baselineVersion=0`, then run V1.
4. Compare `ai_chat_*` and `user_contract_record` columns/indexes with the live schema.
5. Verify old Search, Chat, Contract Lab and authentication smoke tests against the disposable clone.
6. Back up the target database and record the backup identifier.
7. Enable migrations for one `cloud-agent-service` instance, wait for health, then disable migration execution on additional replicas.

## Activation variables

```text
AGENT_MIGRATIONS_ENABLED=true
AGENT_DB_URL=jdbc:postgresql://127.0.0.1:5432/<validated-target>
AGENT_DB_USERNAME=<migration-role>
AGENT_DB_PASSWORD=<secret>
```

The migration role should own only the relevant schema and should not be the application superuser.

## Compatibility window

Keep `LEGACY_CONVERSATION_DDL_ENABLED=true` for one release. After schema equivalence and rolling-deployment tests pass, set it to `false`. Delete the legacy `@PostConstruct` DDL only in a later contract migration.

## Rollback

Disable `AGENT_ROUTE_ENABLED` and `AGENT_ENABLED`, then roll back the service binary. V1 tables are retained because they are additive and unused while the feature is off. Do not automatically drop evidence or proposal tables.
