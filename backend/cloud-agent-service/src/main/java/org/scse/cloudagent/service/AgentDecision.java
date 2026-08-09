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
        String reasonSummary,
        String evidenceGap,
        String stopReason,
        String rawStructuredOutput,
        Map<String, Object> providerMetadata
) {
    public AgentDecision {
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        reasonSummary = normalize(reasonSummary);
        evidenceGap = normalize(evidenceGap);
        stopReason = normalize(stopReason);
        rawStructuredOutput = rawStructuredOutput == null ? "" : rawStructuredOutput;
        providerMetadata = providerMetadata == null ? Map.of() : Map.copyOf(providerMetadata);
    }

    public static AgentDecision toolCall(String toolName, String argumentsJson, String rawStructuredOutput) {
        return toolCall(toolName, argumentsJson, "Call " + toolName,
                "A fresh tool observation is required", rawStructuredOutput);
    }

    public static AgentDecision toolCall(String toolName, String argumentsJson, String reasonSummary,
                                         String evidenceGap, String rawStructuredOutput) {
        return new AgentDecision(Type.TOOL_CALL, toolName, argumentsJson, null, List.of(),
                reasonSummary, evidenceGap, "", rawStructuredOutput, Map.of());
    }

    public static AgentDecision finalAnswer(String answer, List<UUID> evidenceIds, String rawStructuredOutput) {
        return finalAnswer(answer, evidenceIds, "Evidence is sufficient for the answer", "", "ANSWERED",
                rawStructuredOutput);
    }

    public static AgentDecision finalAnswer(String answer, List<UUID> evidenceIds, String reasonSummary,
                                            String evidenceGap, String stopReason, String rawStructuredOutput) {
        return new AgentDecision(Type.FINAL_ANSWER, null, null, answer, evidenceIds,
                reasonSummary, evidenceGap, stopReason, rawStructuredOutput, Map.of());
    }

    public static AgentDecision abstain(String answer, List<UUID> evidenceIds, String reasonSummary,
                                        String evidenceGap, String rawStructuredOutput) {
        return new AgentDecision(Type.ABSTAIN, null, null, answer, evidenceIds,
                reasonSummary, evidenceGap, "EVIDENCE_INSUFFICIENT", rawStructuredOutput, Map.of());
    }

    public AgentDecision withProviderMetadata(Map<String, Object> metadata) {
        return new AgentDecision(type, toolName, argumentsJson, answer, evidenceIds,
                reasonSummary, evidenceGap, stopReason, rawStructuredOutput, metadata);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public enum Type {
        TOOL_CALL,
        FINAL_ANSWER,
        ABSTAIN
    }
}
