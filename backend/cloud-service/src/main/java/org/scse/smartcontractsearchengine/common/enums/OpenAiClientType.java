package org.scse.smartcontractsearchengine.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OpenAiClientType {

    REMOTE("AliyunEmbeddingClient", "text-embedding-v4"),
    LOCAL("LocalEmbeddingClient", "/home/chidanta/models/Qwen3-Embedding-4B"),

    ZHIPU("ZhiPuAIClient", "glm-5.1"),
    ZHIPU_FAST("ZhiPuAIClient", "glm-4.7-flash");

    private final String beanName;
    private final String modelName;
}
