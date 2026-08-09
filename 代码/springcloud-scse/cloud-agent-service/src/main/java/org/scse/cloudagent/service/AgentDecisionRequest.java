package org.scse.cloudagent.service;

import org.scse.cloudagent.domain.EvidenceItem;
import org.scse.cloudagent.tool.ToolDefinition;

import java.util.List;

public record AgentDecisionRequest(
        String task,
        List<String> observations,
        List<EvidenceItem> evidence,
        List<ToolDefinition> tools,
        int step,
        int remainingToolCalls,
        int remainingRetrievalRounds
) {
    public AgentDecisionRequest {
        observations = observations == null ? List.of() : List.copyOf(observations);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        tools = tools == null ? List.of() : List.copyOf(tools);
    }
}
