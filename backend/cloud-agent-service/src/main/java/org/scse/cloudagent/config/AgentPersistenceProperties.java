package org.scse.cloudagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "agent.persistence")
public record AgentPersistenceProperties(boolean enabled, String url, String username, String password) {
    @ConstructorBinding
    public AgentPersistenceProperties {
        url = url == null ? "" : url.trim();
        username = username == null ? "" : username;
        password = password == null ? "" : password;
    }
}
