package org.scse.cloudagent.dto;

import org.scse.cloudagent.domain.AgentErrorCode;

import java.time.Instant;

public record ApiError(AgentErrorCode code, String message, String traceId, Instant timestamp) {
}
