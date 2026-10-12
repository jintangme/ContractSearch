package org.scse.cloudagent.tool;

import java.util.UUID;

public record ToolExecutionContext(long userId, UUID runId, AgentWorkspace workspace, String task, ModelCallBudget modelBudget,
                                   long remainingMillis) {
    public ToolExecutionContext(long userId, UUID runId, AgentWorkspace workspace, String task, ModelCallBudget modelBudget) {
        this(userId, runId, workspace, task, modelBudget, Long.MAX_VALUE);
    }
    public ToolExecutionContext(long userId, UUID runId, AgentWorkspace workspace) { this(userId, runId, workspace, "", new ModelCallBudget(0)); }
    public ToolExecutionContext(long userId, UUID runId) { this(userId, runId, new AgentWorkspace()); }
    public ToolExecutionContext {
        if (workspace == null) workspace = new AgentWorkspace();
        if (task == null) task = "";
        if (modelBudget == null) modelBudget = new ModelCallBudget(0);
    }
}
