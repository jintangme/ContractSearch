package org.scse.cloudagent.tool;

import org.scse.cloudagent.domain.SideEffect;

import java.time.Duration;

public record ToolDefinition(
        String name,
        String description,
        String inputSchema,
        SideEffect sideEffect,
        Duration timeout
) {
    public ToolDefinition {
        if (name == null || !name.matches("^[a-z][a-z0-9_]*\\.v[1-9][0-9]*$")) {
            throw new IllegalArgumentException("Tool name must use name.vN versioning");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Tool timeout must be positive");
        }
    }
}
