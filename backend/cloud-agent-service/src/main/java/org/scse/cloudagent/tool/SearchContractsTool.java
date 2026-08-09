package org.scse.cloudagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import org.scse.cloudagent.domain.SideEffect;
import org.scse.cloudagent.domain.TrustLevel;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class SearchContractsTool implements AgentTool {
    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "search_contracts.v1",
            "Search the existing ContractSearch catalog with semantic hybrid search or Solidity code text search.",
            """
                    {"type":"object","additionalProperties":false,"required":["query"],"properties":{"query":{"type":"string","minLength":2,"maxLength":500},"mode":{"type":"string","enum":["semantic","code"]},"limit":{"type":"integer","minimum":1,"maximum":10}}}
                    """.trim(),
            SideEffect.READ,
            Duration.ofSeconds(15)
    );
    private final ContractCatalogClient client;

    public SearchContractsTool(ContractCatalogClient client) {
        this.client = client;
    }

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolExecutionResult execute(JsonNode arguments, ToolExecutionContext context) {
        ToolArgumentRules.rejectUnknown(arguments, "query", "mode", "limit");
        String query = requiredText(arguments, "query", 2, 500);
        String mode = optionalText(arguments, "mode", "semantic");
        if (!"semantic".equals(mode) && !"code".equals(mode)) {
            throw new InvalidToolArgumentsException("mode must be semantic or code");
        }
        JsonNode limitNode = arguments.get("limit");
        if (limitNode != null && !limitNode.isIntegralNumber()) {
            throw new InvalidToolArgumentsException("limit must be an integer");
        }
        int limit = limitNode == null ? 5 : limitNode.asInt();
        if (limit < 1 || limit > 10) {
            throw new InvalidToolArgumentsException("limit must be between 1 and 10");
        }
        List<Map<String, Object>> results = client.search(query, mode, limit, context.userId());
        List<EvidenceDraft> evidence = new ArrayList<>();
        for (Map<String, Object> result : results) {
            Object id = result.get("id");
            if (id == null) continue;
            String name = stringValue(result.get("contractName"), "Contract " + id);
            String excerpt = truncate(stringValue(result.get("description"), "Catalog search match"), 2000);
            evidence.add(new EvidenceDraft("CONTRACT_SEARCH", "contract:" + id, name, excerpt,
                    TrustLevel.COMMUNITY,
                    Map.of("contractId", id, "query", query, "mode", mode, "contentBoundary", "UNTRUSTED")));
        }
        return new ToolExecutionResult(Map.of(
                "query", query,
                "mode", mode,
                "count", results.size(),
                "results", results
        ), evidence);
    }

    private String requiredText(JsonNode arguments, String name, int min, int max) {
        JsonNode value = arguments.get(name);
        if (value == null || !value.isTextual()) throw new InvalidToolArgumentsException(name + " is required");
        String text = value.asText().trim();
        if (text.length() < min || text.length() > max) {
            throw new InvalidToolArgumentsException(name + " length is outside the allowed range");
        }
        return text;
    }

    private String optionalText(JsonNode arguments, String name, String fallback) {
        JsonNode value = arguments.get(name);
        if (value == null || value.isNull()) return fallback;
        if (!value.isTextual()) throw new InvalidToolArgumentsException(name + " must be a string");
        return value.asText().trim();
    }

    private String stringValue(Object value, String fallback) {
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
