package org.scse.cloudagent.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Set;

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
        if (properties.miniLoop().enabled()) {
            validateFixedDependency("AGENT_CONTRACT_SERVICE_URL", properties.dependencies().contractServiceBaseUrl(),
                    Set.of("127.0.0.1", "localhost", "cloud-service"));
            validateFixedDependency("AGENT_RUNTIME_SERVICE_URL", properties.dependencies().runtimeBaseUrl(),
                    Set.of("127.0.0.1", "localhost", "contract-runtime-service"));
        }
        if (properties.enabled() && properties.miniLoop().enabled() && properties.provider().apiKey().isBlank()) {
            throw new IllegalStateException("ZHIPU_API_KEY is required when AGENT_MINI_LOOP_ENABLED=true");
        }
    }

    private void validateFixedDependency(String name, String value, Set<String> allowedHosts) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(name + " must be a valid fixed HTTP(S) base URL", exception);
        }
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getHost() == null || !allowedHosts.contains(uri.getHost())
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalStateException(name + " points outside the Agent dependency allowlist");
        }
    }
}
