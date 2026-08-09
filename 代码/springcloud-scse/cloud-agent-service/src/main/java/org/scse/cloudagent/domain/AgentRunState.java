package org.scse.cloudagent.domain;

public enum AgentRunState {
    RECEIVED,
    PLAN,
    TOOL_CALL,
    OBSERVE,
    SYNTHESIZE,
    COMPLETE,
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETE || this == FAILED;
    }
}
