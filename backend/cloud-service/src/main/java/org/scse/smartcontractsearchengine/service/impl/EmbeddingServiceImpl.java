package org.scse.smartcontractsearchengine.service.impl;

import com.openai.client.OpenAIClient;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.Embedding;
import com.openai.models.embeddings.EmbeddingCreateParams;
import org.scse.smartcontractsearchengine.common.enums.OpenAiClientType;
import org.scse.smartcontractsearchengine.config.OpenAIClientFactory;
import org.scse.smartcontractsearchengine.exception.EmbeddingException;
import org.scse.smartcontractsearchengine.service.EmbeddingService;
import org.scse.smartcontractsearchengine.util.EmbeddingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingServiceImpl.class);

    @Autowired
    private OpenAIClientFactory clientFactory;

    @Override
    public float[] getEmbedding(String text) {
        return getEmbeddings(List.of(text), OpenAiClientType.REMOTE).get(0);
    }

    @Override
    public float[] getEmbedding(String text, OpenAiClientType clientEnum) {
        return getEmbeddings(List.of(text), clientEnum).get(0);
    }

    @Override
    public List<float[]> getEmbeddings(List<String> texts, OpenAiClientType clientEnum) {
        if (clientEnum != OpenAiClientType.REMOTE && clientEnum != OpenAiClientType.LOCAL){
            throw new EmbeddingException("不支持的 OpenAiClientType：" + clientEnum);
        }
        String modelName = clientEnum.getModelName();

        EmbeddingCreateParams params = EmbeddingCreateParams.builder()
                .model(modelName)
                .input(EmbeddingCreateParams.Input.ofArrayOfStrings(texts))
                // .dimensions(768)
                .build();

        OpenAIClient client = clientFactory.getClient(clientEnum);

        try {
            CreateEmbeddingResponse response = client.embeddings().create(params);
            // logger.debug("成功生成嵌入向量，文本长度：{}", text.length());
            List<Embedding> embeddings = response.data();
            // hnsw 不支持 2560 维度，vllm 对 Qwen3embedding 的 MRL 识别有 bug 无法使用 dimensions 参数，需要手动截断至 768 维度
            return EmbeddingUtil.toLowerDimension(EmbeddingUtil.embedding2float(embeddings));
        } catch (Exception e) {
            logger.error("生成嵌入向量失败", e);
            throw new EmbeddingException("生成嵌入向量失败：" + e.getMessage(), e);
        }
    }
}
