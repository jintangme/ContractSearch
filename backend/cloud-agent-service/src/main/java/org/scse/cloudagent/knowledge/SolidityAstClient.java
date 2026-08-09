package org.scse.cloudagent.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.scse.cloudagent.config.AgentProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
public class SolidityAstClient {
    private static final int MAX_RESPONSE_CHARS = 5_000_000;
    private final ObjectMapper objectMapper;
    private final AgentProperties properties;
    private final HttpClient httpClient;

    @Autowired
    public SolidityAstClient(ObjectMapper objectMapper, AgentProperties properties) {
        this(objectMapper, properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.dependencies().compileTimeoutMs()))
                .build());
    }

    SolidityAstClient(ObjectMapper objectMapper, AgentProperties properties, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.httpClient = httpClient;
    }

    public AstParseResult parse(List<NormalizedSourceFile> files) {
        Map<String, String> sources = new LinkedHashMap<>();
        for (NormalizedSourceFile file : files) {
            sources.put(file.path(), file.normalizedContent());
        }
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of("sources", sources));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.dependencies().runtimeBaseUrl() + "/api/runtime/parse"))
                    .timeout(Duration.ofMillis(properties.dependencies().compileTimeoutMs()))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.body().length() > MAX_RESPONSE_CHARS) {
                return failed("AST_RESPONSE_TOO_LARGE");
            }
            if (response.statusCode() != 200 && response.statusCode() != 400) {
                return failed("AST_RUNTIME_HTTP_" + response.statusCode());
            }
            return normalize(objectMapper.readTree(response.body()));
        } catch (java.net.http.HttpTimeoutException exception) {
            return failed("AST_RUNTIME_TIMEOUT");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failed("AST_RUNTIME_INTERRUPTED");
        } catch (IOException | IllegalArgumentException exception) {
            return failed("AST_RUNTIME_UNAVAILABLE");
        }
    }

    private AstParseResult normalize(JsonNode root) {
        List<AstDeclaration> declarations = new ArrayList<>();
        for (JsonNode item : root.path("declarations")) {
            declarations.add(new AstDeclaration(
                    item.path("astId").asLong(),
                    item.path("nodeType").asText(),
                    item.path("kind").asText(),
                    item.path("name").asText(),
                    nullableText(item, "contractName"),
                    nullableText(item, "visibility"),
                    nullableText(item, "stateMutability"),
                    item.path("virtual").asBoolean(false),
                    textList(item.path("parameterTypes")),
                    textList(item.path("returnTypes")),
                    textList(item.path("modifiers")),
                    item.path("filePath").asText(),
                    item.path("startChar").asInt(),
                    item.path("endChar").asInt(),
                    item.path("lineStart").asInt(),
                    item.path("lineEnd").asInt(),
                    item.path("signature").asText(),
                    item.path("content").asText()
            ));
        }
        List<String> diagnostics = new ArrayList<>();
        collectDiagnostics(root.path("errors"), diagnostics);
        collectDiagnostics(root.path("warnings"), diagnostics);
        return new AstParseResult(root.path("success").asBoolean(false),
                root.path("parser").asText("solc-standard-json-ast"),
                root.path("compilerVersion").asText("unknown"), declarations, diagnostics);
    }

    private void collectDiagnostics(JsonNode items, List<String> target) {
        for (JsonNode item : items) {
            if (target.size() >= 20) return;
            target.add(item.path("severity").asText("error") + ": "
                    + truncate(item.path("message").asText("Solidity parser diagnostic"), 1000));
        }
    }

    private List<String> textList(JsonNode node) {
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) values.add(item.asText());
        return values;
    }

    private String nullableText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private AstParseResult failed(String diagnostic) {
        return new AstParseResult(false, "solc-standard-json-ast", "unknown", List.of(), List.of(diagnostic));
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
