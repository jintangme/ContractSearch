package org.scse.cloudagent.service;

import org.scse.cloudagent.domain.AgentErrorCode;
import org.scse.cloudagent.domain.AgentEvent;
import org.scse.cloudagent.domain.AgentRunState;
import org.scse.cloudagent.domain.EvidenceItem;

import java.util.List;

public record MiniLoopResult(
        AgentRunState state,
        String answer,
        AgentErrorCode errorCode,
        String failureMessage,
        int steps,
        int toolCalls,
        int retrievalRounds,
        List<AgentEvent> events,
        List<EvidenceItem> evidence
) {
}
