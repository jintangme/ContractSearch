package org.scse.cloudagent.service;

import org.scse.cloudagent.domain.EvidenceItem;
import org.scse.cloudagent.tool.ToolDefinition;

import java.util.List;
import java.util.Map;

public record AgentDecisionRequest(
        String task,
        Map<String, Object> workspaceContext,
        List<String> observations,
        List<EvidenceItem> evidence,
        List<ToolDefinition> tools,
        int step,
        int remainingToolCalls,
        int remainingRetrievalRounds
) {
    public AgentDecisionRequest {
        workspaceContext = workspaceContext == null ? Map.of() : Map.copyOf(workspaceContext);
        observations = observations == null ? List.of() : List.copyOf(observations);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        tools = tools == null ? List.of() : List.copyOf(tools);
    }

    public AgentDecisionRequest(String task, List<String> observations, List<EvidenceItem> evidence,
                                List<ToolDefinition> tools, int step, int remainingToolCalls,
                                int remainingRetrievalRounds) {
        this(task, Map.of(), observations, evidence, tools, step, remainingToolCalls, remainingRetrievalRounds);
    }
}
