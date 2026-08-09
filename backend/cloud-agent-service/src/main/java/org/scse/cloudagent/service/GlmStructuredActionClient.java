package org.scse.cloudagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.scse.cloudagent.config.AgentProperties;
import org.scse.cloudagent.tool.ToolDefinition;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class GlmStructuredActionClient {
    private final ObjectMapper objectMapper;
    private final AgentProperties properties;
    private final GlmHttpTransport transport;

    public GlmStructuredActionClient(ObjectMapper objectMapper, AgentProperties properties, GlmHttpTransport transport) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.transport = transport;
    }

    public JsonNode complete(String systemPrompt, String userPrompt, List<ToolDefinition> tools)
            throws IOException, InterruptedException {
        if (properties.provider().apiKey().isBlank()) {
            throw new IllegalStateException("ZHIPU_API_KEY is required for a live provider call");
        }
        Map<String, Object> payload = buildPayload(systemPrompt, userPrompt, tools);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(stripTrailingSlash(properties.provider().baseUrl()) + "/chat/completions"))
                .timeout(Duration.ofMillis(properties.provider().timeoutMs()))
                .header("Authorization", "Bearer " + properties.provider().apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
        GlmHttpTransport.Response response = transport.send(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GLM provider returned HTTP " + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    Map<String, Object> buildPayload(String systemPrompt, String userPrompt, List<ToolDefinition> tools) {
        return Map.of(
                "model", properties.model(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)),
                "tools", tools.stream().map(this::toProviderTool).toList(),
                "tool_choice", "auto",
                "temperature", 0.1,
                "thinking", Map.of("type", properties.provider().thinkingEnabled() ? "enabled" : "disabled")
        );
    }

    private Map<String, Object> toProviderTool(ToolDefinition definition) {
        try {
            return Map.of("type", "function", "function", Map.of(
                    "name", providerSafeName(definition.name()),
                    "description", definition.description(),
                    "parameters", objectMapper.readTree(definition.inputSchema())));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid schema for tool " + definition.name(), exception);
        }
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    static String providerSafeName(String internalName) {
        return internalName.replace(".", "__");
    }
}
