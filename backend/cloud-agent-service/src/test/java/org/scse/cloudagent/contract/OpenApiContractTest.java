package org.scse.cloudagent.contract;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiContractTest {
    @Test
    void openApiAndFrontendClientCoverPhaseOneResources() throws Exception {
        Path module = Path.of(System.getProperty("user.dir"));
        Path repository = module.resolve("../../..").normalize();
        String openApi = Files.readString(repository.resolve("docs/agentic-rag/openapi/agent-api-v1.yaml"));
        String frontend = Files.readString(module.resolve("../../scse_front_end/src/api/index.js").normalize());

        assertThat(openApi).contains("/sessions:", "/runs:", "/runs/{runId}/events:",
                "/runs/{runId}/evidence:", "/runs/{runId}/proposals:");
        assertThat(openApi).contains("RECEIVED, PLAN, TOOL_CALL, OBSERVE, SYNTHESIZE, COMPLETE, FAILED");
        assertThat(frontend).contains("export const agentApi", "streamEvents", "Authorization: `Bearer ${token}`");
        assertThat(frontend).doesNotContain("EventSource(");
    }
}
