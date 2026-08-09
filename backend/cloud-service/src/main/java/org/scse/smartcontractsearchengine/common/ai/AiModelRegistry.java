package org.scse.smartcontractsearchengine.common.ai;

import java.util.Set;

public final class AiModelRegistry {
    public static final String PROVIDER = "zhipu";
    public static final String DEFAULT_MODEL = "glm-5.1";
    public static final Set<String> ALLOWED_MODELS = Set.of(
            "glm-5.1",
            "glm-5-turbo",
            "glm-5",
            "glm-4.7",
            "glm-4.7-flash",
            "glm-4.7-flashx",
            "glm-4.6",
            "glm-4.5-air",
            "glm-4.5-airx",
            "glm-4.5-flash",
            "glm-4-flash-250414",
            "glm-4-flashx-250414"
    );

    private AiModelRegistry() {
    }

    public static String resolveModel(String requestedModel) {
        if (requestedModel == null || requestedModel.isBlank()) {
            return DEFAULT_MODEL;
        }
        String model = requestedModel.trim();
        if (!ALLOWED_MODELS.contains(model)) {
            throw new IllegalArgumentException("不支持的 AI 模型：" + model);
        }
        return model;
    }
}
