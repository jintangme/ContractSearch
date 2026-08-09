package org.scse.cloudagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

public record CreateRunRequest(
        UUID sessionId,
        @NotBlank @Size(max = 12000) String task,
        Map<String, Object> workspaceContext
) {
}
