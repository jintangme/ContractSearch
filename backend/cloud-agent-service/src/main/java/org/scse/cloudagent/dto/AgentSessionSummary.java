package org.scse.cloudagent.dto;

import java.time.Instant;
import java.util.UUID;

public record AgentSessionSummary(
        UUID id,
        String title,
        Instant createdAt,
        Instant updatedAt,
        long runCount
) {
}
