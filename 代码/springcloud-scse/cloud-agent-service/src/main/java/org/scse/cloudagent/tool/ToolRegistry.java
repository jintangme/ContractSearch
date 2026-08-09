package org.scse.cloudagent.tool;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ToolRegistry {
    private final Map<String, AgentTool> tools;

    public ToolRegistry(List<AgentTool> registeredTools) {
        Map<String, AgentTool> indexed = new LinkedHashMap<>();
        for (AgentTool tool : registeredTools) {
            AgentTool previous = indexed.put(tool.definition().name(), tool);
            if (previous != null) {
                throw new IllegalStateException("Duplicate tool: " + tool.definition().name());
            }
        }
        this.tools = Map.copyOf(indexed);
    }

    public AgentTool require(String name) {
        AgentTool tool = tools.get(name);
        if (tool == null) {
            throw new UnknownToolException(name);
        }
        return tool;
    }

    public Collection<ToolDefinition> definitions() {
        return tools.values().stream().map(AgentTool::definition).toList();
    }

    public List<ToolDefinition> definitions(Collection<String> names) {
        return names.stream().sorted().map(this::require).map(AgentTool::definition).toList();
    }
}
