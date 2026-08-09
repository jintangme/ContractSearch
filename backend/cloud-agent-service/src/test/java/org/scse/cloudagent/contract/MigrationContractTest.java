package org.scse.cloudagent.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationContractTest {
    @Test
    void firstMigrationCoversLegacyAndAgentFoundations() throws IOException {
        try (var input = getClass().getResourceAsStream("/db/migration/V1__agent_foundations.sql")) {
            assertThat(input).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
            assertThat(sql).contains("create table if not exists ai_chat_session");
            assertThat(sql).contains("create table if not exists user_contract_record");
            assertThat(sql).contains("create table if not exists agent_run");
            assertThat(sql).contains("create table if not exists agent_event");
            assertThat(sql).contains("create table if not exists agent_evidence");
            assertThat(sql).contains("create table if not exists agent_proposal");
            assertThat(sql).doesNotContain("drop table");
        }
    }

    @Test
    void secondMigrationIsAdditiveAndCoversKnowledgeFoundations() throws IOException {
        try (var input = getClass().getResourceAsStream("/db/migration/V2__knowledge_layer_foundations.sql")) {
            assertThat(input).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
            assertThat(sql).contains("create table if not exists knowledge_corpus_version");
            assertThat(sql).contains("create table if not exists knowledge_source");
            assertThat(sql).contains("create table if not exists knowledge_document");
            assertThat(sql).contains("create table if not exists knowledge_chunk");
            assertThat(sql).contains("create table if not exists knowledge_ingestion_job");
            assertThat(sql).contains("create table if not exists knowledge_ingestion_error");
            assertThat(sql).contains("existing_contract_id");
            assertThat(sql).contains("trust_level");
            assertThat(sql).contains("visibility");
            assertThat(sql).doesNotContain("drop table");
            assertThat(sql).doesNotContain("alter table");
        }
    }

    @Test
    void thirdMigrationAddsAliasDedupAndIdempotentJobLedger() throws IOException {
        try (var input = getClass().getResourceAsStream("/db/migration/V3__knowledge_ingestion_items.sql")) {
            assertThat(input).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
            assertThat(sql).contains("create table if not exists knowledge_source_alias");
            assertThat(sql).contains("create table if not exists knowledge_ingestion_item");
            assertThat(sql).contains("unique(corpus_version_id, existing_contract_id)");
            assertThat(sql).contains("unique(job_id, existing_contract_id)");
            assertThat(sql).doesNotContain("drop table");
            assertThat(sql).doesNotContain("alter table");
        }
    }

    @Test
    void fourthMigrationAddsAnAdditiveChunkTextIndex() throws IOException {
        try (var input = getClass().getResourceAsStream("/db/migration/V4__knowledge_chunk_search.sql")) {
            assertThat(input).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
            assertThat(sql).contains("create table if not exists knowledge_chunk_search");
            assertThat(sql).contains("tsvector generated always");
            assertThat(sql).contains("using gin(search_vector)");
            assertThat(sql).doesNotContain("drop table");
            assertThat(sql).doesNotContain("alter table");
        }
    }

    @Test
    void fifthMigrationAllowsAuthoritativeNonContractKnowledge() throws IOException {
        try (var input = getClass().getResourceAsStream("/db/migration/V5__authoritative_knowledge.sql")) {
            assertThat(input).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
            assertThat(sql).contains("alter column existing_contract_id drop not null");
            assertThat(sql).contains("source_kind, trust_level");
            assertThat(sql).doesNotContain("drop table");
            assertThat(sql).doesNotContain("drop column");
        }
    }
}
