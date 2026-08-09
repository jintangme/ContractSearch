package org.scse.cloudagent.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AgentRun(
        UUID id,
        UUID sessionId,
        long userId,
        String task,
        String model,
        String traceId,
        AgentRunState state,
        String answer,
        AgentErrorCode errorCode,
        String failureMessage,
        int steps,
        int toolCalls,
        int retrievalRounds,
        Instant createdAt,
        Instant updatedAt,
        List<AgentEvent> events,
        List<EvidenceItem> evidence,
        List<Proposal> proposals
) {
}
