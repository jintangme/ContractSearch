package org.scse.cloudagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent")
public record AgentProperties(
        boolean enabled,
        String internalToken,
        String model,
        Provider provider,
        Migrations migrations
) {
    public AgentProperties {
        internalToken = internalToken == null ? "" : internalToken;
        model = model == null || model.isBlank() ? "glm-4.5-air" : model;
        provider = provider == null
                ? new Provider("https://open.bigmodel.cn/api/paas/v4", "", 60000, false)
                : provider;
        migrations = migrations == null ? new Migrations(false, "", "", "") : migrations;
    }

    public record Provider(String baseUrl, String apiKey, int timeoutMs, boolean thinkingEnabled) {
        public Provider {
            baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://open.bigmodel.cn/api/paas/v4" : baseUrl;
            apiKey = apiKey == null ? "" : apiKey;
            timeoutMs = timeoutMs <= 0 ? 60000 : timeoutMs;
        }
    }

    public record Migrations(boolean enabled, String url, String username, String password) {
        public Migrations {
            url = url == null ? "" : url;
            username = username == null ? "" : username;
            password = password == null ? "" : password;
        }
    }
}
