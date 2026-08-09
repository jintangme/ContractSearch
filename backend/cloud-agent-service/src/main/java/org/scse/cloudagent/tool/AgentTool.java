package org.scse.cloudagent.tool;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public interface AgentTool {
    ToolDefinition definition();

    default Map<String, Object> invoke(JsonNode arguments) {
        throw new UnsupportedOperationException("Context-aware execution is required for " + definition().name());
    }

    default ToolExecutionResult execute(JsonNode arguments, ToolExecutionContext context) {
        return new ToolExecutionResult(invoke(arguments), java.util.List.of());
    }
}
