package org.scse.cloudagent.tool;

import java.util.UUID;

public record ToolExecutionContext(long userId, UUID runId) {
}
