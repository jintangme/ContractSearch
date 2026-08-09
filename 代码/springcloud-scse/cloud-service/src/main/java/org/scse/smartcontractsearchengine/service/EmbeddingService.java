package org.scse.smartcontractsearchengine.service;

import org.scse.smartcontractsearchengine.common.enums.OpenAiClientType;

import java.util.List;

public interface EmbeddingService {

    float[] getEmbedding(String text);

    float[] getEmbedding(String text, OpenAiClientType clientEnum);

    List<float[]> getEmbeddings(List<String>texts, OpenAiClientType clientEnum);
}
