package org.scse.cloudagent.service;

public interface StructuredActionProvider {
    AgentDecision decide(AgentDecisionRequest request);
}
