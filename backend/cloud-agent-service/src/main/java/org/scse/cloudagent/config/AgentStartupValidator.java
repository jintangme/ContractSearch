package org.scse.cloudagent.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Set;

@Component
public class AgentStartupValidator {
    private final AgentProperties properties;
    private final AgentPersistenceProperties persistenceProperties;

    public AgentStartupValidator(AgentProperties properties, AgentPersistenceProperties persistenceProperties) {
        this.properties = properties;
        this.persistenceProperties = persistenceProperties;
    }

    @PostConstruct
    void validate() {
        if ((properties.enabled() || properties.knowledge().enabled())
                && properties.internalToken().length() < 24) {
            throw new IllegalStateException("AGENT_INTERNAL_TOKEN must contain at least 24 characters when Agent or Knowledge APIs are enabled");
        }
        if (properties.migrations().enabled()
                && (properties.migrations().url().isBlank() || properties.migrations().username().isBlank())) {
            throw new IllegalStateException("Agent migration URL and username are required when migrations are enabled");
        }
        if (properties.knowledge().enabled()
                && (properties.knowledge().databaseUrl().isBlank()
                || properties.knowledge().databaseUsername().isBlank())) {
            throw new IllegalStateException("Knowledge database URL and username are required when Knowledge ingestion is enabled");
        }
        if (persistenceProperties.enabled()
                && (persistenceProperties.url().isBlank() || persistenceProperties.username().isBlank())) {
            throw new IllegalStateException("Agent persistence URL and username are required when persistence is enabled");
        }
        if (properties.miniLoop().enabled()) {
            validateFixedDependency("AGENT_CONTRACT_SERVICE_URL", properties.dependencies().contractServiceBaseUrl(),
                    Set.of("127.0.0.1", "localhost", "cloud-service", "service"));
            validateFixedDependency("AGENT_RUNTIME_SERVICE_URL", properties.dependencies().runtimeBaseUrl(),
                    Set.of("127.0.0.1", "localhost", "contract-runtime-service", "runtime"));
        }
        if (properties.knowledge().enabled()) {
            validateFixedDependency("AGENT_RUNTIME_SERVICE_URL", properties.dependencies().runtimeBaseUrl(),
                    Set.of("127.0.0.1", "localhost", "contract-runtime-service", "runtime"));
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
