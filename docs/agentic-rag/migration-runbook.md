# Agent database migrations

Agent migrations add run, evidence, knowledge and ingestion tables. They are
separate from the business [bootstrap schema](../../database/bootstrap/001-schema.sql).
The migration runner uses Flyway with `baselineOnMigrate=true` and baseline
version `0`; it applies every migration in the service's `db/migration` directory.

## Local demo

The [Compose Agent workflow](../development/agent-mode.md) enables migrations
against its dedicated demo database. Repeated startup checks for pending
migrations without reapplying completed files. The root `.env` value of
`AGENT_MIGRATIONS_ENABLED` does not override that Compose setting.

## Existing database

Before changing an existing installation:

1. Test the migrations on a disposable empty database and a schema-only copy of
   the target database. Confirm that a second migration run has no pending work.
2. Check the conversation, draft and knowledge tables, then run application API
   and tool-adapter checks against the disposable database.
3. Back up the target database. Enable migrations for one configured Agent
   instance and wait for it to become healthy.
4. Disable migration execution on other replicas and enable the required Agent
   features independently.

Host-run migration configuration:

```dotenv
AGENT_MIGRATIONS_ENABLED=true
AGENT_DB_URL=jdbc:postgresql://127.0.0.1:5432/<validated-target>
AGENT_DB_USERNAME=<migration-role>
AGENT_DB_PASSWORD=<secret>
```

The migration role needs the required schema privileges. Applying migrations
alone does not populate a knowledge corpus or enable public Agent routes.
Follow the [knowledge ingestion guide](knowledge-ingestion-runbook.md) to import
sources. If conversation tables have already been provisioned, the business
service can use `LEGACY_CONVERSATION_DDL_ENABLED=false` to skip its conversation
startup DDL.

## Integration check

The migration test creates and drops a dedicated test schema. Run it only on a
disposable database, from the repository root:

```bash
AGENT_MIGRATION_TEST_URL='jdbc:postgresql://127.0.0.1:<port>/<test-database>' \
AGENT_MIGRATION_TEST_USERNAME='<test-user>' \
AGENT_MIGRATION_TEST_PASSWORD='<test-password>' \
mvn -f backend/pom.xml -pl cloud-agent-service -Dtest=PostgresMigrationIntegrationTest test
```

`AGENT_MIGRATION_TEST_MODE=live-clone` additionally checks compatibility with
conversation and draft tables copied from that database's `public` schema.
No production records are needed.

## Rollback

Disable `AGENT_ROUTE_ENABLED` and `AGENT_ENABLED` before rolling back the
service binary. Additive Agent and knowledge tables can remain in place while
the feature is disabled. Data removal or restoration requires a separate,
reviewed database operation; disabling the feature does not drop tables.
