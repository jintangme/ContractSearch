package org.scse.cloudagent.tool;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public interface AgentTool {
    ToolDefinition definition();

    Map<String, Object> invoke(JsonNode arguments);
}
