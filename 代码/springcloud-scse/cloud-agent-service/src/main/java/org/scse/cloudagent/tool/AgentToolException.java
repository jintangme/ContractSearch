package org.scse.cloudagent.tool;

import org.scse.cloudagent.domain.AgentErrorCode;

public class AgentToolException extends RuntimeException {
    private final AgentErrorCode errorCode;

    public AgentToolException(AgentErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AgentToolException(AgentErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public AgentErrorCode errorCode() {
        return errorCode;
    }
}
