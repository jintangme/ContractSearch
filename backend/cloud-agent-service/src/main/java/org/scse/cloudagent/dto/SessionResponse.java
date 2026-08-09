package org.scse.cloudagent.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        long userId,
        String title,
        Map<String, Object> workspaceContext,
        Instant createdAt
) {
}
