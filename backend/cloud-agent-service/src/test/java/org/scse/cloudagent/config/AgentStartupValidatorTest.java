package org.scse.cloudagent.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class AgentStartupValidatorTest {
    @Test
    void acceptsFixedComposeServiceAliases() {
        AgentProperties properties = properties(
                new AgentProperties.Provider("https://open.bigmodel.cn/api/paas/v4", "test-key", 1000, false),
                new AgentProperties.Dependencies("http://service:18081", "http://runtime:19090", 1000, 1000));
        assertThatCode(() -> validator(properties).validate()).doesNotThrowAnyException();
    }

    @Test
    void refusesEnabledKnowledgeApiWithoutTrustedToken() {
        AgentProperties properties = new AgentProperties(false, "", "glm-4.5-air",
                new AgentProperties.Provider("https://open.bigmodel.cn/api/paas/v4", "", 1000, false),
                new AgentProperties.Migrations(false, "", "", ""),
                new AgentProperties.MiniLoop(false, 6, 4, 2),
                new AgentProperties.Dependencies("http://127.0.0.1:18081", "http://127.0.0.1:19090", 1000, 1000),
                new AgentProperties.Knowledge(true, "jdbc:postgresql://127.0.0.1/test", "user", "password",
                        "contractsearch", "test", 10));

        assertThatThrownBy(() -> validator(properties).validate())
                .hasMessageContaining("AGENT_INTERNAL_TOKEN");
    }

    @Test
    void refusesMiniLoopWithoutProviderKey() {
        AgentProperties properties = properties(
                new AgentProperties.Provider("https://open.bigmodel.cn/api/paas/v4", "", 1000, false),
                new AgentProperties.Dependencies("http://127.0.0.1:18081", "http://127.0.0.1:19090", 1000, 1000));

        assertThatThrownBy(() -> validator(properties).validate())
                .hasMessageContaining("ZHIPU_API_KEY");
    }

    @Test
    void refusesDependencyUrlOutsideAllowlist() {
        AgentProperties properties = properties(
                new AgentProperties.Provider("https://open.bigmodel.cn/api/paas/v4", "test-key", 1000, false),
                new AgentProperties.Dependencies("http://example.com", "http://127.0.0.1:19090", 1000, 1000));

        assertThatThrownBy(() -> validator(properties).validate())
                .hasMessageContaining("allowlist");
    }

    @Test
    void refusesEnabledPersistenceWithoutDatabaseIdentity() {
        AgentProperties properties = properties(
                new AgentProperties.Provider("https://open.bigmodel.cn/api/paas/v4", "test-key", 1000, false),
                new AgentProperties.Dependencies("http://127.0.0.1:18081", "http://127.0.0.1:19090", 1000, 1000));

        assertThatThrownBy(() -> new AgentStartupValidator(properties,
                new AgentPersistenceProperties(true, "", "", "")).validate())
                .hasMessageContaining("persistence URL and username");
    }

    private AgentStartupValidator validator(AgentProperties properties) {
        return new AgentStartupValidator(properties, new AgentPersistenceProperties(false, "", "", ""));
    }

    private AgentProperties properties(AgentProperties.Provider provider, AgentProperties.Dependencies dependencies) {
        return new AgentProperties(true, "phase-one-a-test-token-123456789", "glm-4.5-air", provider,
                new AgentProperties.Migrations(false, "", "", ""),
                new AgentProperties.MiniLoop(true, 6, 4, 2), dependencies);
    }
}
