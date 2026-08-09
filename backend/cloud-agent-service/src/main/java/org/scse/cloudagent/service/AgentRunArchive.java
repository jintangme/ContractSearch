package org.scse.cloudagent.service;

import org.scse.cloudagent.domain.AgentRun;
import org.scse.cloudagent.dto.SessionResponse;

public interface AgentRunArchive {
    void saveSession(SessionResponse session);

    void saveRun(AgentRun run);
}
