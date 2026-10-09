package org.scse.smartcontractsearchengine.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAIConfig {

    @Bean(name = "AliyunEmbeddingClient")
    public OpenAIClient aliyunEmbeddingClient() {
        return OpenAIOkHttpClient.builder()
                .apiKey(apiKeyOrEmpty("DASHSCOPE_API_KEY"))
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .build();
    }

    @Bean(name = "LocalEmbeddingClient")
    public OpenAIClient localEmbeddingClient() {
        return OpenAIOkHttpClient.builder()
                .apiKey("EMPTY")
                .baseUrl(System.getenv().getOrDefault("LOCAL_EMBEDDING_BASE_URL", "http://127.0.0.1:8000/v1/"))
                .build();
    }

    @Bean(name = "ZhiPuAIClient")
    public OpenAIClient zhiPuAIClient() {
        return OpenAIOkHttpClient.builder()
                .apiKey(apiKeyOrEmpty("ZHIPU_API_KEY"))
                .baseUrl("https://open.bigmodel.cn/api/paas/v4/")
                .build();
    }

    private String apiKeyOrEmpty(String envName) {
        String value = System.getenv(envName);
        return value == null || value.isBlank() ? "EMPTY" : value;
    }
}
