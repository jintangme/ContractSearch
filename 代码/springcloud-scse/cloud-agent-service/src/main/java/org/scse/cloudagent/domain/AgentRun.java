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
        Instant createdAt,
        Instant updatedAt,
        List<AgentEvent> events,
        List<EvidenceItem> evidence,
        List<Proposal> proposals
) {
}
