package org.scse.cloudagent.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record Proposal(
        UUID id,
        String kind,
        String status,
        String summary,
        Map<String, Object> payload,
        Instant createdAt
) {
}
