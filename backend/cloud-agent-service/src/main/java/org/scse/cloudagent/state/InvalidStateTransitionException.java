package org.scse.cloudagent.state;

import org.scse.cloudagent.domain.AgentRunState;
import org.scse.cloudagent.domain.AgentSignal;

public class InvalidStateTransitionException extends RuntimeException {
    public InvalidStateTransitionException(AgentRunState state, AgentSignal signal) {
        super("Transition is not allowed: " + state + " + " + signal);
    }
}
