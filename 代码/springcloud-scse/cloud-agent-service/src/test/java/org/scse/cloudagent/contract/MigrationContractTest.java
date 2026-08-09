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
}
