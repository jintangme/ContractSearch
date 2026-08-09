package org.scse.cloudagent.domain;

public enum AgentSignal {
    START_PLAN,
    REQUEST_TOOL,
    RECORD_OBSERVATION,
    RETRY_TOOL,
    START_SYNTHESIS,
    COMPLETE,
    FAIL
}
