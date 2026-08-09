package org.scse.smartcontractsearchengine.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationDdlCompatibilityTest {
    @Test
    void compatibilityFlagDisablesStartupDdl() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        ConversationServiceImpl service = new ConversationServiceImpl(null, null, null, null, jdbcTemplate);
        ReflectionTestUtils.setField(service, "legacyConversationDdlEnabled", false);

        service.ensureConversationTables();

        assertThat(jdbcTemplate.executionCount).isZero();
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private int executionCount;

        @Override
        public void execute(String sql) {
            executionCount++;
        }
    }
}
