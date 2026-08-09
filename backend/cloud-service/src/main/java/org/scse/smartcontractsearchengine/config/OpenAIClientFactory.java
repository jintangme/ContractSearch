package org.scse.smartcontractsearchengine.config;

import com.openai.client.OpenAIClient;
import org.scse.smartcontractsearchengine.common.enums.OpenAiClientType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OpenAIClientFactory {

    // Spring 自动注入所有 OpenAIClient 类型的 Bean
    @Autowired
    private Map<String, OpenAIClient> clientMap;

    public OpenAIClient getClient(OpenAiClientType clientEnum) {
        return clientMap.get(clientEnum.getBeanName());
    }

}
