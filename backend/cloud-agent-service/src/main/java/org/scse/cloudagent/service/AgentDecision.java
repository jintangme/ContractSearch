package org.scse.cloudagent.service;

import java.util.List;
import java.util.UUID;
import java.util.Map;

public record AgentDecision(
        Type type,
        String toolName,
        String argumentsJson,
        String answer,
        List<UUID> evidenceIds,
        String rawStructuredOutput,
        Map<String, Object> providerMetadata
) {
    public AgentDecision {
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        rawStructuredOutput = rawStructuredOutput == null ? "" : rawStructuredOutput;
        providerMetadata = providerMetadata == null ? Map.of() : Map.copyOf(providerMetadata);
    }

    public static AgentDecision toolCall(String toolName, String argumentsJson, String rawStructuredOutput) {
        return new AgentDecision(Type.TOOL_CALL, toolName, argumentsJson, null, List.of(), rawStructuredOutput, Map.of());
    }

    public static AgentDecision finalAnswer(String answer, List<UUID> evidenceIds, String rawStructuredOutput) {
        return new AgentDecision(Type.FINAL_ANSWER, null, null, answer, evidenceIds, rawStructuredOutput, Map.of());
    }

    public AgentDecision withProviderMetadata(Map<String, Object> metadata) {
        return new AgentDecision(type, toolName, argumentsJson, answer, evidenceIds, rawStructuredOutput, metadata);
    }

    public enum Type {
        TOOL_CALL,
        FINAL_ANSWER
    }
}
