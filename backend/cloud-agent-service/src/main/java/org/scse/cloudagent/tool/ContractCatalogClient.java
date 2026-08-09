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
public class ContractCatalogClient {
    private static final int MAX_RESPONSE_CHARS = 2_000_000;
    private final ObjectMapper objectMapper;
    private final AgentProperties properties;
    private final HttpClient httpClient;

    @Autowired
    public ContractCatalogClient(ObjectMapper objectMapper, AgentProperties properties) {
        this(objectMapper, properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.dependencies().contractTimeoutMs()))
                .build());
    }

    ContractCatalogClient(ObjectMapper objectMapper, AgentProperties properties, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.httpClient = httpClient;
    }

    public List<Map<String, Object>> search(String query, String mode, int limit, long userId) {
        Map<String, Object> payload = Map.of("input", query, "searchMode", mode, "limit", limit);
        JsonNode data = send("POST", "/api/search", payload, userId).path("data");
        if (!data.isArray()) {
            throw new AgentToolException(AgentErrorCode.INTERNAL_ERROR, "Contract search returned an invalid data shape");
        }
        List<Map<String, Object>> results = new ArrayList<>();
        for (JsonNode item : data) {
            if (results.size() >= limit) break;
            Map<String, Object> normalized = new LinkedHashMap<>();
            putIfPresent(normalized, "id", item.get("id"));
            putIfPresent(normalized, "contractName", item.get("contractName"));
            putIfPresent(normalized, "category", item.get("category"));
            putTextIfPresent(normalized, "description", item.get("description"), 4000);
            putIfPresent(normalized, "compilerVersion", item.get("compilerVersion"));
            results.add(Map.copyOf(normalized));
        }
        return List.copyOf(results);
    }

    public Map<String, Object> getContract(long contractId, long userId) {
        JsonNode data = send("GET", "/api/contracts/" + contractId + "?regenerateAbi=false", null, userId).path("data");
        if (!data.isObject()) {
            throw new AgentToolException(AgentErrorCode.RUN_NOT_FOUND, "Contract was not found");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        putIfPresent(result, "id", data.get("id"));
        putIfPresent(result, "address", data.get("address"));
        putIfPresent(result, "contractName", data.get("contractName"));
        putIfPresent(result, "sourceCode", data.get("sourceCode"));
        putIfPresent(result, "category", data.get("category"));
        putIfPresent(result, "description", data.get("description"));
        putIfPresent(result, "compilerVersion", data.get("compilerVersion"));
        return Map.copyOf(result);
    }

    private JsonNode send(String method, String path, Object payload, long userId) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(properties.dependencies().contractServiceBaseUrl() + path))
                    .timeout(Duration.ofMillis(properties.dependencies().contractTimeoutMs()))
                    .header("Accept", "application/json")
                    .header("X-User-Id", Long.toString(userId));
            if ("POST".equals(method)) {
                builder.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8));
            } else {
                builder.GET();
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.body().length() > MAX_RESPONSE_CHARS) {
                throw new AgentToolException(AgentErrorCode.INTERNAL_ERROR, "Contract service response exceeded the size limit");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AgentToolException(AgentErrorCode.INTERNAL_ERROR,
                        "Contract service returned HTTP " + response.statusCode());
            }
            JsonNode envelope = objectMapper.readTree(response.body());
            if (!envelope.path("status").asBoolean(false)) {
                throw new AgentToolException(AgentErrorCode.INTERNAL_ERROR,
                        "Contract service rejected the request: " + safeMessage(envelope.path("message").asText()));
            }
            return envelope;
        } catch (AgentToolException exception) {
            throw exception;
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new AgentToolException(AgentErrorCode.TOOL_TIMEOUT, "Contract service request timed out", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AgentToolException(AgentErrorCode.TOOL_TIMEOUT, "Contract service request was interrupted", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw new AgentToolException(AgentErrorCode.INTERNAL_ERROR, "Contract service request failed", exception);
        }
    }

    private void putIfPresent(Map<String, Object> target, String field, JsonNode value) {
        if (value == null || value.isNull()) return;
        target.put(field, objectMapper.convertValue(value, Object.class));
    }

    private void putTextIfPresent(Map<String, Object> target, String field, JsonNode value, int maxLength) {
        if (value == null || value.isNull()) return;
        String text = value.asText();
        target.put(field, text.length() <= maxLength ? text : text.substring(0, maxLength));
    }

    private String safeMessage(String value) {
        if (value == null || value.isBlank()) return "unknown error";
        return value.length() <= 300 ? value : value.substring(0, 300);
    }
}
