package org.scse.cloudagent.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentStartupValidatorTest {
    @Test
    void refusesMiniLoopWithoutProviderKey() {
        AgentProperties properties = properties(
                new AgentProperties.Provider("https://open.bigmodel.cn/api/paas/v4", "", 1000, false),
                new AgentProperties.Dependencies("http://127.0.0.1:18081", "http://127.0.0.1:19090", 1000, 1000));

        assertThatThrownBy(() -> new AgentStartupValidator(properties).validate())
                .hasMessageContaining("ZHIPU_API_KEY");
    }

    @Test
    void refusesDependencyUrlOutsideAllowlist() {
        AgentProperties properties = properties(
                new AgentProperties.Provider("https://open.bigmodel.cn/api/paas/v4", "test-key", 1000, false),
                new AgentProperties.Dependencies("http://example.com", "http://127.0.0.1:19090", 1000, 1000));

        assertThatThrownBy(() -> new AgentStartupValidator(properties).validate())
                .hasMessageContaining("allowlist");
    }

    private AgentProperties properties(AgentProperties.Provider provider, AgentProperties.Dependencies dependencies) {
        return new AgentProperties(true, "phase-one-a-test-token-123456789", "glm-4.5-air", provider,
                new AgentProperties.Migrations(false, "", "", ""),
                new AgentProperties.MiniLoop(true, 6, 4, 2), dependencies);
    }
}
