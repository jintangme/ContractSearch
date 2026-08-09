package org.scse.cloudagent.state;

import org.junit.jupiter.api.Test;
import org.scse.cloudagent.domain.AgentRunState;
import org.scse.cloudagent.domain.AgentSignal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentStateMachineTest {
    private final AgentStateMachine machine = new AgentStateMachine();

    @Test
    void supportsMockHappyPath() {
        AgentRunState state = AgentRunState.RECEIVED;
        state = machine.transition(state, AgentSignal.START_PLAN);
        state = machine.transition(state, AgentSignal.REQUEST_TOOL);
        state = machine.transition(state, AgentSignal.RECORD_OBSERVATION);
        state = machine.transition(state, AgentSignal.START_SYNTHESIS);
        state = machine.transition(state, AgentSignal.COMPLETE);

        assertThat(state).isEqualTo(AgentRunState.COMPLETE);
    }

    @Test
    void supportsBoundedToolRetryTransition() {
        AgentRunState state = machine.transition(AgentRunState.OBSERVE, AgentSignal.RETRY_TOOL);
        assertThat(state).isEqualTo(AgentRunState.TOOL_CALL);
    }

    @Test
    void rejectsUnauthorizedTransition() {
        assertThatThrownBy(() -> machine.transition(AgentRunState.RECEIVED, AgentSignal.COMPLETE))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void terminalStatesCannotTransition() {
        assertThatThrownBy(() -> machine.transition(AgentRunState.COMPLETE, AgentSignal.FAIL))
                .isInstanceOf(InvalidStateTransitionException.class);
    }
}
