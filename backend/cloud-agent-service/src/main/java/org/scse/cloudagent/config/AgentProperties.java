package org.scse.cloudagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "agent")
public record AgentProperties(
        boolean enabled,
        String internalToken,
        String model,
        Provider provider,
        Migrations migrations,
        MiniLoop miniLoop,
        Dependencies dependencies,
        Knowledge knowledge
) {
    @ConstructorBinding
    public AgentProperties {
        internalToken = internalToken == null ? "" : internalToken;
        model = model == null || model.isBlank() ? "glm-4.5-air" : model;
        provider = provider == null
                ? new Provider("https://open.bigmodel.cn/api/paas/v4", "", 60000, false)
                : provider;
        migrations = migrations == null ? new Migrations(false, "", "", "") : migrations;
        miniLoop = miniLoop == null ? new MiniLoop(false, 6, 4, 2) : miniLoop;
        dependencies = dependencies == null
                ? new Dependencies("http://127.0.0.1:18081", "http://127.0.0.1:19090", 15000, 45000)
                : dependencies;
        knowledge = knowledge == null
                ? new Knowledge(false, "", "", "", "contractsearch", "phase2-dev", 50)
                : knowledge;
    }

    public AgentProperties(boolean enabled,
                           String internalToken,
                           String model,
                           Provider provider,
                           Migrations migrations) {
        this(enabled, internalToken, model, provider, migrations, null, null, null);
    }

    public AgentProperties(boolean enabled,
                           String internalToken,
                           String model,
                           Provider provider,
                           Migrations migrations,
                           MiniLoop miniLoop,
                           Dependencies dependencies) {
        this(enabled, internalToken, model, provider, migrations, miniLoop, dependencies, null);
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

    public record MiniLoop(boolean enabled, int maxSteps, int maxToolCalls, int maxRetrievalRounds) {
        public MiniLoop {
            maxSteps = maxSteps <= 0 ? 6 : Math.min(maxSteps, 6);
            maxToolCalls = maxToolCalls <= 0 ? 4 : Math.min(maxToolCalls, 4);
            maxRetrievalRounds = maxRetrievalRounds <= 0 ? 2 : Math.min(maxRetrievalRounds, 2);
        }
    }

    public record Dependencies(
            String contractServiceBaseUrl,
            String runtimeBaseUrl,
            int contractTimeoutMs,
            int compileTimeoutMs
    ) {
        public Dependencies {
            contractServiceBaseUrl = normalize(contractServiceBaseUrl, "http://127.0.0.1:18081");
            runtimeBaseUrl = normalize(runtimeBaseUrl, "http://127.0.0.1:19090");
            contractTimeoutMs = contractTimeoutMs <= 0 ? 15000 : Math.min(contractTimeoutMs, 30000);
            compileTimeoutMs = compileTimeoutMs <= 0 ? 45000 : Math.min(compileTimeoutMs, 60000);
        }

        private static String normalize(String value, String fallback) {
            String normalized = value == null || value.isBlank() ? fallback : value.trim();
            return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
        }
    }

    public record Knowledge(
            boolean enabled,
            String databaseUrl,
            String databaseUsername,
            String databasePassword,
            String corpusName,
            String corpusVersion,
            int maxBatchSize
    ) {
        public Knowledge {
            databaseUrl = databaseUrl == null ? "" : databaseUrl;
            databaseUsername = databaseUsername == null ? "" : databaseUsername;
            databasePassword = databasePassword == null ? "" : databasePassword;
            corpusName = corpusName == null || corpusName.isBlank() ? "contractsearch" : corpusName.trim();
            corpusVersion = corpusVersion == null || corpusVersion.isBlank() ? "phase2-dev" : corpusVersion.trim();
            maxBatchSize = maxBatchSize <= 0 ? 50 : Math.min(maxBatchSize, 200);
        }
    }
}
