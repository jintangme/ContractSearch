package org.scse.cloudagent.service;

import org.scse.cloudagent.domain.AgentRun;
import org.scse.cloudagent.dto.SessionResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "agent.persistence", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopAgentRunArchive implements AgentRunArchive {
    @Override
    public java.util.List<SessionResponse> findSessions(long userId) {
        return java.util.List.of();
    }

    @Override
    public java.util.List<AgentRun> findRuns(long userId, java.util.UUID sessionId) {
        return java.util.List.of();
    }

    @Override
    public void saveSession(SessionResponse session) {
    }

    @Override
    public void saveRun(AgentRun run) {
    }

    @Override
    public void deleteSession(long userId, java.util.UUID sessionId) {
    }
}
