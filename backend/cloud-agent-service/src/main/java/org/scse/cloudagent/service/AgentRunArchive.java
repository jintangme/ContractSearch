package org.scse.cloudagent.service;

import org.scse.cloudagent.domain.AgentRun;
import org.scse.cloudagent.dto.SessionResponse;

public interface AgentRunArchive {
    java.util.List<SessionResponse> findSessions(long userId);

    java.util.List<AgentRun> findRuns(long userId, java.util.UUID sessionId);

    void saveSession(SessionResponse session);

    void saveRun(AgentRun run);

    void deleteSession(long userId, java.util.UUID sessionId);
}
