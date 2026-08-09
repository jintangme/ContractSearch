package org.scse.cloudagent.domain;

import java.time.Instant;
import java.util.Map;

public record AgentEvent(long sequence, String type, Instant createdAt, Map<String, Object> data) {
}
