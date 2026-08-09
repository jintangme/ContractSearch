package org.scse.cloudagent.service;

import org.scse.cloudagent.domain.AgentRun;
import org.scse.cloudagent.dto.SessionResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "agent.persistence", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopAgentRunArchive implements AgentRunArchive {
    @Override
    public void saveSession(SessionResponse session) {
    }

    @Override
    public void saveRun(AgentRun run) {
    }
}
