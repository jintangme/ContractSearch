package org.scse.cloudagent.knowledge;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeIngestionRequestTest {

    @Test
    void allowsCheckpointResumeWithoutExplicitAfterId() {
        UUID jobId = UUID.randomUUID();

        KnowledgeIngestionRequest request = new KnowledgeIngestionRequest(
                KnowledgeIngestionMode.APPLY, null, 200, true, jobId);

        assertThat(request.afterId()).isNull();
        assertThat(request.jobId()).isEqualTo(jobId);
    }
}
