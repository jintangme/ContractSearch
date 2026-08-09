package org.scse.cloudagent.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class AgentStartupValidator {
    private final AgentProperties properties;

    public AgentStartupValidator(AgentProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void validate() {
        if (properties.enabled() && properties.internalToken().length() < 24) {
            throw new IllegalStateException("AGENT_INTERNAL_TOKEN must contain at least 24 characters when AGENT_ENABLED=true");
        }
        if (properties.migrations().enabled()
                && (properties.migrations().url().isBlank() || properties.migrations().username().isBlank())) {
            throw new IllegalStateException("Agent migration URL and username are required when migrations are enabled");
        }
    }
}
