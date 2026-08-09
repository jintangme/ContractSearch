package org.scse.cloudagent.state;

import org.scse.cloudagent.domain.AgentRunState;
import org.scse.cloudagent.domain.AgentSignal;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class AgentStateMachine {
    private final Map<AgentRunState, Map<AgentSignal, AgentRunState>> transitions = new EnumMap<>(AgentRunState.class);

    public AgentStateMachine() {
        allow(AgentRunState.RECEIVED, AgentSignal.START_PLAN, AgentRunState.PLAN);
        allow(AgentRunState.PLAN, AgentSignal.REQUEST_TOOL, AgentRunState.TOOL_CALL);
        allow(AgentRunState.PLAN, AgentSignal.START_SYNTHESIS, AgentRunState.SYNTHESIZE);
        allow(AgentRunState.TOOL_CALL, AgentSignal.RECORD_OBSERVATION, AgentRunState.OBSERVE);
        allow(AgentRunState.OBSERVE, AgentSignal.RETRY_TOOL, AgentRunState.TOOL_CALL);
        allow(AgentRunState.OBSERVE, AgentSignal.START_SYNTHESIS, AgentRunState.SYNTHESIZE);
        allow(AgentRunState.SYNTHESIZE, AgentSignal.COMPLETE, AgentRunState.COMPLETE);
        for (AgentRunState state : AgentRunState.values()) {
            if (!state.isTerminal()) {
                allow(state, AgentSignal.FAIL, AgentRunState.FAILED);
            }
        }
    }

    public AgentRunState transition(AgentRunState current, AgentSignal signal) {
        AgentRunState next = transitions.getOrDefault(current, Map.of()).get(signal);
        if (next == null) {
            throw new InvalidStateTransitionException(current, signal);
        }
        return next;
    }

    private void allow(AgentRunState from, AgentSignal signal, AgentRunState to) {
        transitions.computeIfAbsent(from, ignored -> new EnumMap<>(AgentSignal.class)).put(signal, to);
    }
}
