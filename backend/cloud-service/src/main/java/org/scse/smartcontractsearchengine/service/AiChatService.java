package org.scse.smartcontractsearchengine.service;

import org.scse.smartcontractsearchengine.model.dto.AiChatRequest;
import org.scse.smartcontractsearchengine.model.dto.AiChatResponse;

public interface AiChatService {
    AiChatResponse chat(AiChatRequest request);
}
