package org.scse.cloudagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import org.scse.cloudagent.domain.SideEffect;
import org.scse.cloudagent.domain.TrustLevel;
import org.scse.cloudagent.knowledge.KnowledgeRetrievalResponse;
import org.scse.cloudagent.knowledge.KnowledgeRetrievalService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "agent.knowledge", name = "enabled", havingValue = "true")
public class RetrieveKnowledgeTool implements AgentTool {
    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "retrieve_knowledge.v1",
            "Hybrid retrieval over existing contract semantic/code search and function-level Knowledge chunks. Prefer concise English Solidity symbols or security terms in query rewrites.",
            """
                    {"type":"object","additionalProperties":false,"required":["query"],"properties":{"query":{"type":"string","minLength":2,"maxLength":500},"limit":{"type":"integer","minimum":1,"maximum":10}}}
                    """.trim(),
            SideEffect.READ,
            Duration.ofSeconds(20)
    );
    private final KnowledgeRetrievalService retrievalService;

    public RetrieveKnowledgeTool(KnowledgeRetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolExecutionResult execute(JsonNode arguments, ToolExecutionContext context) {
        ToolArgumentRules.rejectUnknown(arguments, "query", "limit");
        JsonNode queryNode = arguments.get("query");
        if (queryNode == null || !queryNode.isTextual()) {
            throw new InvalidToolArgumentsException("query is required");
        }
        String query = queryNode.asText().trim();
        if (query.length() < 2 || query.length() > 500) {
            throw new InvalidToolArgumentsException("query length is outside the allowed range");
        }
        JsonNode limitNode = arguments.get("limit");
        if (limitNode != null && !limitNode.isIntegralNumber()) {
            throw new InvalidToolArgumentsException("limit must be an integer");
        }
        int limit = limitNode == null ? 5 : limitNode.asInt();
        if (limit < 1 || limit > 10) {
            throw new InvalidToolArgumentsException("limit must be between 1 and 10");
        }

        KnowledgeRetrievalResponse response = retrievalService.retrieve(query, limit, context.userId());
        List<EvidenceDraft> evidence = evidence(response.results(), query);
        return new ToolExecutionResult(Map.of(
                "query", query,
                "count", response.results().size(),
                "degradedChannels", response.degradedChannels(),
                "results", response.results()
        ), evidence);
    }

    @SuppressWarnings("unchecked")
    private List<EvidenceDraft> evidence(List<Map<String, Object>> results, String query) {
        List<EvidenceDraft> evidence = new ArrayList<>();
        for (Map<String, Object> result : results) {
            Object contractId = result.get("contractId");
            if (contractId == null) continue;
            String contractName = text(result.get("contractName"), "Contract " + contractId);
            List<Map<String, Object>> chunks = result.get("chunks") instanceof List<?> list
                    ? (List<Map<String, Object>>) (List<?>) list : List.of();
            if (chunks.isEmpty()) {
                evidence.add(new EvidenceDraft("KNOWLEDGE_CONTRACT", "contract:" + contractId,
                        contractName, truncate(text(result.get("description"), "Contract retrieval match"), 2000),
                        TrustLevel.COMMUNITY,
                        Map.of("contractId", contractId, "query", query,
                                "channels", result.getOrDefault("channelRanks", Map.of()),
                                "contentBoundary", "UNTRUSTED")));
                continue;
            }
            for (Map<String, Object> chunk : chunks) {
                String chunkId = text(chunk.get("chunkId"), "unknown");
                Map<String, Object> metadata = new java.util.LinkedHashMap<>();
                metadata.put("contractId", contractId);
                metadata.put("chunkId", chunkId);
                metadata.put("query", query);
                copy(metadata, chunk, "filePath", "lineStart", "lineEnd", "contentHash", "symbol");
                metadata.put("channels", result.getOrDefault("channelRanks", Map.of()));
                metadata.put("contentBoundary", "UNTRUSTED");
                evidence.add(new EvidenceDraft("KNOWLEDGE_CHUNK", "chunk:" + chunkId,
                        contractName + " / " + text(chunk.get("symbol"), text(chunk.get("kind"), "chunk")),
                        truncate(text(chunk.get("excerpt"), "Knowledge chunk match"), 3000),
                        trust(chunk.get("trustLevel")), Map.copyOf(metadata)));
            }
        }
        return List.copyOf(evidence);
    }

    private void copy(Map<String, Object> target, Map<String, Object> source, String... fields) {
        for (String field : fields) {
            Object value = source.get(field);
            if (value != null) target.put(field, value);
        }
    }

    private TrustLevel trust(Object value) {
        try {
            return TrustLevel.valueOf(text(value, "COMMUNITY"));
        } catch (IllegalArgumentException ignored) {
            return TrustLevel.COMMUNITY;
        }
    }

    private String text(Object value, String fallback) {
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
