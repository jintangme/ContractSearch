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
public class CompileRuntimeClient {
    static final int MAX_SOURCE_CHARS = 700_000;
    private static final int MAX_RESPONSE_CHARS = 3_000_000;
    private final ObjectMapper objectMapper;
    private final AgentProperties properties;
    private final HttpClient httpClient;

    @Autowired
    public CompileRuntimeClient(ObjectMapper objectMapper, AgentProperties properties) {
        this(objectMapper, properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.dependencies().compileTimeoutMs()))
                .build());
    }

    CompileRuntimeClient(ObjectMapper objectMapper, AgentProperties properties, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.httpClient = httpClient;
    }

    public Map<String, Object> compile(String sourceCode, String fileName, String compilerVersion) {
        return compile(sourceCode, fileName, compilerVersion, properties.dependencies().compileTimeoutMs());
    }

    public Map<String, Object> compile(String sourceCode, String fileName, String compilerVersion, long remainingMillis) {
        if (sourceCode == null || sourceCode.isBlank()) {
            throw new InvalidToolArgumentsException("The selected contract has no Solidity source code");
        }
        if (sourceCode.length() > MAX_SOURCE_CHARS) {
            throw new InvalidToolArgumentsException("Contract source exceeds the compile tool size limit");
        }
        if (remainingMillis <= 0) throw new AgentToolException(AgentErrorCode.TOOL_TIMEOUT, "No task time remains for compilation");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceCode", sourceCode);
        payload.put("fileName", fileName);
        if (compilerVersion != null && !compilerVersion.isBlank()) payload.put("compilerVersion", compilerVersion);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.dependencies().runtimeBaseUrl() + "/api/runtime/compile"))
                    .timeout(Duration.ofMillis(Math.min(properties.dependencies().compileTimeoutMs(), remainingMillis)))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.body().length() > MAX_RESPONSE_CHARS) {
                throw new AgentToolException(AgentErrorCode.INTERNAL_ERROR, "Compile response exceeded the size limit");
            }
            if (response.statusCode() != 200 && response.statusCode() != 400) {
                throw new AgentToolException(AgentErrorCode.INTERNAL_ERROR,
                        "Compile runtime returned HTTP " + response.statusCode());
            }
            return normalize(objectMapper.readTree(response.body()));
        } catch (AgentToolException exception) {
            throw exception;
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new AgentToolException(AgentErrorCode.TOOL_TIMEOUT, "Compile runtime timed out", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AgentToolException(AgentErrorCode.TOOL_TIMEOUT, "Compile runtime was interrupted", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw new AgentToolException(AgentErrorCode.INTERNAL_ERROR, "Compile runtime request failed", exception);
        }
    }

    private Map<String, Object> normalize(JsonNode root) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", root.path("success").asBoolean(false));
        copyText(root, result, "contractName");
        copyText(root, result, "compilerVersion");
        copyText(root, result, "selectedCompilerVersion");
        result.put("versionMismatch", root.path("versionMismatch").asBoolean(false));
        List<String> contractNames = new ArrayList<>();
        for (JsonNode contract : root.path("contracts")) {
            if (contractNames.size() >= 20) break;
            String name = contract.path("contractName").asText();
            if (!name.isBlank()) contractNames.add(name);
        }
        result.put("contracts", List.copyOf(contractNames));
        result.put("errors", diagnostics(root.path("errors")));
        result.put("warnings", diagnostics(root.path("warnings")));
        return Map.copyOf(result);
    }

    private List<Map<String, Object>> diagnostics(JsonNode items) {
        List<Map<String, Object>> diagnostics = new ArrayList<>();
        for (JsonNode item : items) {
            if (diagnostics.size() >= 20) break;
            Map<String, Object> diagnostic = new LinkedHashMap<>();
            copyText(item, diagnostic, "severity");
            String message = item.path("message").asText(item.path("formattedMessage").asText("Compiler diagnostic"));
            diagnostic.put("message", truncate(message, 2000));
            if (item.has("line") && item.get("line").canConvertToInt()) diagnostic.put("line", item.get("line").asInt());
            if (item.has("column") && item.get("column").canConvertToInt()) diagnostic.put("column", item.get("column").asInt());
            diagnostics.add(Map.copyOf(diagnostic));
        }
        return List.copyOf(diagnostics);
    }

    private void copyText(JsonNode source, Map<String, Object> target, String field) {
        String value = source.path(field).asText();
        if (!value.isBlank()) target.put(field, truncate(value, 300));
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
