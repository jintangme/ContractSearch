package org.scse.smartcontractsearchengine.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OpenAiClientType {

    REMOTE("AliyunEmbeddingClient", "text-embedding-v4"),
    LOCAL("LocalEmbeddingClient", System.getenv().getOrDefault("LOCAL_EMBEDDING_MODEL", "Qwen/Qwen3-Embedding-4B")),

    ZHIPU("ZhiPuAIClient", "glm-5.1"),
    ZHIPU_FAST("ZhiPuAIClient", "glm-4.7-flash");

    private final String beanName;
    private final String modelName;
}
