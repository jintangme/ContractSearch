package org.scse.smartcontractsearchengine.service;

import org.scse.smartcontractsearchengine.dto.AiExplainResponse;

public interface AiExplainService {

    AiExplainResponse explainContract(Long id, String mode, String model);
}
