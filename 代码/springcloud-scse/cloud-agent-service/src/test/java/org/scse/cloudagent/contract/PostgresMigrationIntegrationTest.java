package org.scse.cloudagent.contract;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;
import java.sql.Connection;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "AGENT_MIGRATION_TEST_URL", matches = ".+")
class PostgresMigrationIntegrationTest {
    @Test
    void migratesAndRemainsIdempotent() throws Exception {
        String url = System.getenv("AGENT_MIGRATION_TEST_URL");
        String username = System.getenv("AGENT_MIGRATION_TEST_USERNAME");
        String password = System.getenv("AGENT_MIGRATION_TEST_PASSWORD");
        String schema = System.getenv().getOrDefault("AGENT_MIGRATION_TEST_SCHEMA", "agent_phase1_test_fresh");
        String mode = System.getenv().getOrDefault("AGENT_MIGRATION_TEST_MODE", "fresh");
        if (!schema.matches("^agent_phase1_test_[a-z0-9_]+$")) {
            throw new IllegalArgumentException("Test schema must start with agent_phase1_test_");
        }
        prepareSchema(url, username, password, schema, mode);
        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(url, username, password)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .baselineOnMigrate(true)
                    .baselineVersion(MigrationVersion.fromVersion("0"))
                    .locations("classpath:db/migration")
                    .load();

            assertThat(flyway.migrate().migrationsExecuted).isGreaterThanOrEqualTo(1);
            assertThat(flyway.migrate().migrationsExecuted).isZero();

            try (var connection = DriverManager.getConnection(url, username, password);
                 var statement = connection.createStatement();
                 var result = statement.executeQuery("""
                         SELECT table_name FROM information_schema.tables
                         WHERE table_schema = '%s'
                           AND table_name IN ('agent_session','agent_run','agent_event','agent_evidence','agent_proposal')
                         """.formatted(schema))) {
                java.util.Set<String> tables = new java.util.HashSet<>();
                while (result.next()) tables.add(result.getString(1));
                assertThat(tables).isEqualTo(Set.of("agent_session", "agent_run", "agent_event", "agent_evidence", "agent_proposal"));
            }
            assertLegacyColumns(url, username, password, schema);
        } finally {
            dropSchema(url, username, password, schema);
        }
    }

    private void prepareSchema(String url, String username, String password, String schema, String mode) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, username, password);
             var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            statement.execute("CREATE SCHEMA " + schema);
            if ("live-clone".equals(mode)) {
                for (String table : Set.of("ai_chat_session", "ai_chat_message", "ai_chat_attachment", "user_contract_record")) {
                    statement.execute("CREATE TABLE " + schema + "." + table
                            + " (LIKE public." + table + " INCLUDING ALL)");
                }
            }
        }
    }

    private void dropSchema(String url, String username, String password, String schema) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, username, password);
             var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private void assertLegacyColumns(String url, String username, String password, String schema) throws Exception {
        assertColumns(url, username, password, schema, "ai_chat_session",
                Set.of("id", "user_id", "title", "model", "created_at", "updated_at"));
        assertColumns(url, username, password, schema, "ai_chat_message",
                Set.of("id", "session_id", "role", "content", "model", "requested_model", "model_used", "created_at"));
        assertColumns(url, username, password, schema, "ai_chat_attachment",
                Set.of("id", "message_id", "file_name", "file_type", "source_type", "content_text", "created_at"));
        assertColumns(url, username, password, schema, "user_contract_record",
                Set.of("id", "user_id", "contract_name", "source_code", "precheck_conclusion", "risk_level",
                        "issue_count", "issues", "status", "created_at", "updated_at"));
    }

    private void assertColumns(String url, String username, String password, String schema,
                               String table, Set<String> expectedColumns) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, username, password);
             var statement = connection.prepareStatement("""
                     SELECT column_name FROM information_schema.columns
                     WHERE table_schema = ? AND table_name = ?
                     """)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            try (var result = statement.executeQuery()) {
                java.util.Set<String> columns = new java.util.HashSet<>();
                while (result.next()) columns.add(result.getString(1));
                assertThat(columns).containsAll(expectedColumns);
            }
        }
    }
}
