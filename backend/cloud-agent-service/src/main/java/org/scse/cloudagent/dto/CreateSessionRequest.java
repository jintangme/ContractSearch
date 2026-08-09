package org.scse.cloudagent.dto;

import jakarta.validation.constraints.Size;

import java.util.Map;

public record CreateSessionRequest(
        @Size(max = 120) String title,
        Map<String, Object> workspaceContext
) {
}
