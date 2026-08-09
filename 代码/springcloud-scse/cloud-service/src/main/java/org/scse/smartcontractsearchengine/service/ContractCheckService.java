package org.scse.smartcontractsearchengine.service;

import org.scse.smartcontractsearchengine.model.vo.ContractCheckResponse;

public interface ContractCheckService {

    ContractCheckResponse checkContract(String contractName, String sourceCode, String inputMode, String fileName, String model);
}
