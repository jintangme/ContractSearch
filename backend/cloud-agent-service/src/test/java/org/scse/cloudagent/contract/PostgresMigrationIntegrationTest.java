package org.scse.cloudagent.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.scse.cloudagent.config.AgentProperties;
import org.scse.cloudagent.knowledge.ContractSourceRecord;
import org.scse.cloudagent.knowledge.JdbcKnowledgeIngestionStore;
import org.scse.cloudagent.knowledge.KnowledgeIngestionPlanner;
import org.scse.cloudagent.knowledge.PlannedKnowledgeContract;
import org.scse.cloudagent.knowledge.SolidityAstChunker;
import org.scse.cloudagent.knowledge.SolidityChunker;
import org.scse.cloudagent.knowledge.SoliditySourceRouter;

import java.sql.DriverManager;
import java.sql.Connection;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
            assertKnowledgeTables(url, username, password, schema);
            assertKnowledgeWriterIsIdempotent(url, username, password, schema);
        } finally {
            dropSchema(url, username, password, schema);
        }
    }

    private void assertKnowledgeTables(String url, String username, String password, String schema) throws Exception {
        try (var connection = DriverManager.getConnection(url, username, password);
             var statement = connection.prepareStatement("""
                     SELECT table_name FROM information_schema.tables
                     WHERE table_schema = ? AND table_name LIKE 'knowledge_%'
                     """)) {
            statement.setString(1, schema);
            try (var result = statement.executeQuery()) {
                java.util.Set<String> tables = new java.util.HashSet<>();
                while (result.next()) tables.add(result.getString(1));
                assertThat(tables).contains("knowledge_corpus_version", "knowledge_source",
                        "knowledge_source_alias", "knowledge_document", "knowledge_chunk",
                        "knowledge_ingestion_job", "knowledge_ingestion_item", "knowledge_ingestion_error");
            }
        }
    }

    private void assertKnowledgeWriterIsIdempotent(String url, String username,
                                                   String password, String schema) throws Exception {
        String schemaUrl = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
        AgentProperties properties = new AgentProperties(false, "", "glm-4.5-air",
                new AgentProperties.Provider("http://provider.invalid", "", 1000, false),
                new AgentProperties.Migrations(false, "", "", ""),
                new AgentProperties.MiniLoop(false, 6, 4, 2),
                new AgentProperties.Dependencies("http://127.0.0.1:18081", "http://127.0.0.1:19090", 1000, 1000),
                new AgentProperties.Knowledge(true, schemaUrl, username, password,
                        "contractsearch-test", "v1", 10));
        ObjectMapper mapper = new ObjectMapper();
        JdbcKnowledgeIngestionStore store = new JdbcKnowledgeIngestionStore(properties, mapper);
        SoliditySourceRouter router = new SoliditySourceRouter(mapper);
        KnowledgeIngestionPlanner planner = new KnowledgeIngestionPlanner(router,
                new SolidityChunker(), null, new SolidityAstChunker());
        String source = "pragma solidity ^0.8.20; contract A { function value() public pure returns(uint){return 1;} }";
        var first = new PlannedKnowledgeContract(new ContractSourceRecord(1, "A", source),
                planner.dryRun("contract:1", 1L, source));
        var duplicate = new PlannedKnowledgeContract(new ContractSourceRecord(2, "A-copy", source),
                planner.dryRun("contract:2", 2L, source));

        var written = store.persist(null, java.util.List.of(first, duplicate), 2, false);
        var repeated = store.persist(written.jobId(), java.util.List.of(first, duplicate), 2, false);
        var validation = store.validateCorpus();

        assertThat(written.sourceCount()).isEqualTo(2);
        assertThat(repeated.sourceCount()).isZero();
        assertThat(validation.sources()).isEqualTo(1);
        assertThat(validation.documents()).isEqualTo(1);
        assertThat(validation.chunks()).isEqualTo(1);
        assertThat(validation.searchRows()).isEqualTo(1);
        assertThat(validation.missingChunkSearchRows()).isZero();
        assertThat(validation.valid()).isTrue();

        String changed = source.replace("return 1", "return 2");
        var mutated = new PlannedKnowledgeContract(new ContractSourceRecord(1, "A", changed),
                planner.dryRun("contract:1", 1L, changed));
        assertThatThrownBy(() -> store.persist(null, java.util.List.of(mutated), 1, false))
                .hasMessageContaining("immutable corpus version");
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
