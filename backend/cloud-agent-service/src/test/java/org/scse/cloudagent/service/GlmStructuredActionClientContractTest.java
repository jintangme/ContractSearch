package org.scse.cloudagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.scse.cloudagent.config.AgentProperties;
import org.scse.cloudagent.tool.MockEchoTool;

import java.net.http.HttpRequest;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GlmStructuredActionClientContractTest {
    @Test
    void sendsOpenAiCompatibleToolContractAndParsesToolCall() throws Exception {
        AtomicReference<HttpRequest> capturedRequest = new AtomicReference<>();
        GlmHttpTransport transport = request -> {
            capturedRequest.set(request);
            return new GlmHttpTransport.Response(200, """
                {"id":"mock","choices":[{"message":{"role":"assistant","tool_calls":[{"id":"call-1","type":"function","function":{"name":"mock_echo__v1","arguments":"{\\\"message\\\":\\\"ok\\\"}"}}]}}]}
                """);
        };

        AgentProperties properties = new AgentProperties(
                true,
                "phase-one-test-token-1234567890",
                "glm-4.5-air",
                new AgentProperties.Provider("http://provider.invalid", "test-key", 2000, false),
                new AgentProperties.Migrations(false, "", "", "")
        );
        ObjectMapper objectMapper = new ObjectMapper();
        GlmStructuredActionClient client = new GlmStructuredActionClient(objectMapper, properties, transport);
        var payload = objectMapper.valueToTree(client.buildPayload("system", "use the tool",
                java.util.List.of(new MockEchoTool().definition())));
        var parsedResponse = client.complete("system", "use the tool", java.util.List.of(new MockEchoTool().definition()));

        HttpRequest sentRequest = capturedRequest.get();
        assertThat(sentRequest.headers().firstValue("Authorization")).contains("Bearer test-key");
        assertThat(sentRequest.uri().toString()).isEqualTo("http://provider.invalid/chat/completions");
        assertThat(payload.get("model").asText()).isEqualTo("glm-4.5-air");
        assertThat(payload.at("/tools/0/function/name").asText()).isEqualTo("mock_echo__v1");
        assertThat(payload.at("/thinking/type").asText()).isEqualTo("disabled");
        assertThat(parsedResponse.at("/choices/0/message/tool_calls/0/function/name").asText()).isEqualTo("mock_echo__v1");
    }
}
