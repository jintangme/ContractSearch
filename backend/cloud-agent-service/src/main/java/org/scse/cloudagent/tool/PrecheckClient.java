package org.scse.cloudagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.scse.cloudagent.config.AgentProperties;
import org.scse.cloudagent.domain.AgentErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PrecheckClient {
    private static final int MAX_SOURCE_CHARS = 700_000;
    private static final int MAX_RESPONSE_CHARS = 1_000_000;
    private final ObjectMapper objectMapper;
    private final AgentProperties properties;
    private final HttpClient httpClient;

    @Autowired
    public PrecheckClient(ObjectMapper objectMapper, AgentProperties properties) {
        this(objectMapper, properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.dependencies().contractTimeoutMs())).build());
    }

    PrecheckClient(ObjectMapper objectMapper, AgentProperties properties, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.httpClient = httpClient;
    }

    public Map<String, Object> precheck(String contractName, String sourceCode, String model, long userId) {
        if (sourceCode == null || sourceCode.isBlank()) {
            throw new InvalidToolArgumentsException("The selected contract has no source code to precheck");
        }
        if (sourceCode.length() > MAX_SOURCE_CHARS) {
            throw new InvalidToolArgumentsException("Contract source exceeds the precheck size limit");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractName", contractName);
        payload.put("sourceCode", sourceCode);
        payload.put("inputMode", "agent");
        payload.put("fileName", safeFileName(contractName));
        payload.put("model", model);
        payload.put("saveRecord", false);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.dependencies().contractServiceBaseUrl() + "/api/contracts/check"))
                    .timeout(Duration.ofMillis(properties.provider().timeoutMs()))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("X-User-Id", Long.toString(userId))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.body().length() > MAX_RESPONSE_CHARS) {
                throw new AgentToolException(AgentErrorCode.INTERNAL_ERROR, "Precheck response exceeded the size limit");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AgentToolException(AgentErrorCode.INTERNAL_ERROR,
                        "Precheck service returned HTTP " + response.statusCode());
            }
            JsonNode envelope = objectMapper.readTree(response.body());
            if (!envelope.path("status").asBoolean(false) || !envelope.path("data").isObject()) {
                throw new AgentToolException(AgentErrorCode.INTERNAL_ERROR, "Precheck service rejected the request");
            }
            return normalize(envelope.path("data"));
        } catch (AgentToolException exception) {
            throw exception;
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new AgentToolException(AgentErrorCode.TOOL_TIMEOUT, "Precheck request timed out", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AgentToolException(AgentErrorCode.TOOL_TIMEOUT, "Precheck request was interrupted", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw new AgentToolException(AgentErrorCode.INTERNAL_ERROR, "Precheck request failed", exception);
        }
    }

    private Map<String, Object> normalize(JsonNode data) {
        Map<String, Object> result = new LinkedHashMap<>();
        copy(result, data, "validSolidity");
        copy(result, data, "structurallyComplete");
        copy(result, data, "riskLevel");
        copy(result, data, "recommendation");
        copyText(result, data, "summary", 4000);
        copyText(result, data, "aiSummary", 6000);
        copyText(result, data, "conclusion", 4000);
        copy(result, data, "contractType");
        copy(result, data, "complexityLevel");
        copy(result, data, "riskScores");
        copy(result, data, "provider");
        copy(result, data, "modelUsed");
        result.put("saved", false);
        result.put("issues", boundedStrings(data.path("issues"), 20, 1000));
        result.put("suggestions", boundedStrings(data.path("aiSuggestions"), 20, 1000));
        result.put("keyFocus", boundedStrings(data.path("keyFocus"), 20, 500));
        return Map.copyOf(result);
    }

    private List<String> boundedStrings(JsonNode values, int maxItems, int maxChars) {
        List<String> result = new ArrayList<>();
        if (!values.isArray()) return List.of();
        for (JsonNode value : values) {
            if (result.size() >= maxItems) break;
            String text = value.asText();
            result.add(text.length() <= maxChars ? text : text.substring(0, maxChars));
        }
        return List.copyOf(result);
    }

    private void copy(Map<String, Object> target, JsonNode source, String field) {
        JsonNode value = source.get(field);
        if (value != null && !value.isNull()) target.put(field, objectMapper.convertValue(value, Object.class));
    }

    private void copyText(Map<String, Object> target, JsonNode source, String field, int maxChars) {
        String value = source.path(field).asText();
        if (!value.isBlank()) target.put(field, value.length() <= maxChars ? value : value.substring(0, maxChars));
    }

    private String safeFileName(String contractName) {
        String safe = contractName == null ? "Contract" : contractName.replaceAll("[^A-Za-z0-9_]", "_");
        return (safe.isBlank() ? "Contract" : safe) + ".sol";
    }
}
