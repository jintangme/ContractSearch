package org.scse.cloudagent.service;

public class AgentResourceNotFoundException extends RuntimeException {
    public AgentResourceNotFoundException(String message) {
        super(message);
    }
}
